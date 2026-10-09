# 噜噜 Android 手机聊天助手

## Person → Account → Thread

Android 端不再把每个微信 / QQ 账号当成独立的现实人物。

一个 PersonProfile 代表现实中的同一个人，保存关系阶段、长期目标和回复风格。一个 Person 可以绑定多个 ContactAccount，例如微信大号、微信小号、QQ 旧号。

长期关系记忆使用 personId 作为作用域，因此同一个人的多个账号共享这些事实。最近原始聊天使用每个账号自己的 conversationId，因此大小号的局部聊天上下文不会混在一起。accountId 只用于标记长期事实来自哪个具体账号。

账号绑定与改绑只能由用户显式操作。系统不会根据昵称、头像、说话风格或 LLM 推理自动判断两个账号是不是同一个人。

## 改绑

如果用户把一个已有账号从人物 A 改绑到人物 B，Android 会先调用后端 account/reassign workflow。后端会把这个账号来源的长期事实迁移到新 personId，同时保留该账号自己的最近消息 Thread。后端成功后，手机才更新本地映射。

## 存储位置

手机本地 SharedPreferences 保存：Person/Account 映射、当前账号、后端地址和登录 Session。

后端 PostgreSQL 的 relationship_thread_message 保存每个账号已经同步到的原始聊天历史，不再因为 Prompt 上下文上限而删除较早消息。表内同时记录 chat_id、person_id、account_id、platform、sender、message_text、message_time、content_type、source、source_key、observed_at 和 fingerprint。模型每轮仍只读取有界的最近上下文，因此“完整存储”和“有限 Prompt”是分离的。

后端 PGVector 的 relationship_memory 保存压缩后的长期关系事实和向量。长期事实按 personId 共享，并记录 source_account_id 和 source_platform 作为来源。

当前开发环境中，这两类服务端数据都存放在 Windows 本机 Docker 容器 lulu-ai-agent-pgvector 所使用的 PostgreSQL 数据卷中。以后如果 LULU Backend / PostgreSQL 部署到 Linux 服务器，服务端聊天数据也会随数据库部署位置移动，不再固定存于 Windows。

## 持续同步

LULU 不要求用户必须使用 A/B/C 回复才能维持历史完整性。

1. NotificationListenerService 在用户显式开启通知访问后接收微信 / QQ 新通知。MessagingStyle 合并通知会逐条解析并分别生成稳定 source_key，找不到 MessagingStyle 时再退回 BIG_TEXT / TEXT，因此一次聚合多条消息也不会只保留最后一条。
2. AccessibilityService 在用户正常使用微信 / QQ 时监听窗口内容变化和滚动，经过 900 ms debounce 后对当前可见聊天做低频 reconcile。输入框区域的打字事件会被过滤，因此未发送草稿不会进入数据库。
3. 当前微信版本如果不暴露文字 AccessibilityNode，则自动使用系统当前屏幕截图 + 手机上的 ML Kit 中文 OCR。截图只用于当前设备本地识别。
4. 第一次在某账号下点“噜”时会把 OCR 识别的聊天标题绑定到该 Account；也可以在账号编辑页提前填写聊天标题。被动同步只有在标题唯一匹配一个已绑定账号时才写入，避免猜错联系人。
5. ChatSyncOutbox 先把观察到的消息写入手机本地 SQLite，再尝试发送 Backend。后端或网络暂时不可用时消息留在 Outbox，等 LULU、无障碍服务或通知监听重新连接后自动补传。
6. Backend 用 source_key / fingerprint 和 notification↔screen 跨来源 reconcile 去重；新插入的文字或 voice_transcript 会异步进入 Person 长期记忆提取。
7. 对被识别成 voice 的通知，`VoiceIngestionQueue` 保存独立持久任务。VoiceJob 记录 notification source key、Account、时间、可选 `audioUri/audioMimeType`、attempts、next_retry_at 和 last_error。没有可访问音频时进入 `waiting_audio`，不消耗 ASR 失败次数。
8. 后台主链路通过 `VoiceAudioSourceAdapter` 获取音频。目前第一实现 `NotificationAttachmentVoiceSource` 只读取 Android 已经通过通知暴露给 LULU 的音频 `content://` URI，不依赖微信 UI、Root、Hook 或私有目录。新的合法音频来源可以以后作为 adapter 增加，不需要修改 Coordinator。
9. `VoiceAudioSupport` 统一完成格式识别、大小限制、容器校验和采样率探测。拿到音频后上传 `/api/ai/voice/transcribe`，Backend 使用 DashScope Paraformer 转写。ASR 结果携带 `replacesSourceKey`，优先按原 notification source_key 原位把 voice 占位富化为 `voice_transcript`。
10. 可访问音频转写失败时才使用递增 backoff；没有音频句柄只是 `waiting_audio`。后台 ASR 与普通 ChatSyncOutbox 完全独立，不会阻塞文字同步。微信 UI“长按语音→转文字”仅作为默认关闭的显式 fallback：只有用户在 LULU 首页打开 fallback，并主动点“噜”时才允许执行 UI 操作。

因此即使用户完全手动在微信 / QQ 回复，文字对话仍会持续进入 Account Thread；语音至少会先以“语音 N 秒，等待转写”的占位进入上下文。如果系统通知提供可访问音频，LULU 会在后台自动 ASR 并原位富化；否则保留 waiting_audio，而不是偷偷操作微信。

## 生成回复

Android 使用低延迟 quick workflow：

点悬浮“噜” → OCR/Accessibility 强制 reconcile 当前页面（默认只识别语音占位，不操作微信 UI）→ PostgreSQL 读取同一 Person 最近最多 4 条稳定事实 → 读取当前 Account 最近最多 12 条消息 → FAST 路由 qwen-plus 只生成 A/B/C → 立即返回悬浮层 → 点击 A/B/C 优先填入当前输入框，但不会自动发送。若用户显式开启 UI fallback，点“噜”时才允许尝试“长按语音→转文字→重新 OCR”。

Web 深度分析仍保留完整结构化输出和语义记忆检索，因此没有为了 Android 速度牺牲 Web 的分析能力。

这条链路在 OnePlus Ace 3 Pro（PJX110，Android 16 / API 36）上实测已经从约 30 秒降低到 2.309–3.347 秒。微信不暴露文本节点时的 OCR fallback 真机实测为 2.404 秒点“噜”到 A/B/C，其中当前屏幕读取约 388 ms：Accessibility probe 9 ms、系统截图 15 ms、本地中文 OCR 288 ms、后处理 7 ms。后端 warm run 实测总耗时 1.804 秒，其中 qwen-plus 模型调用 1.793 秒、人物记忆读取 1 ms、账号 Thread 读取 3 ms、持久化调度 5 ms。长期记忆提取即使需要数秒到数十秒，也已经完全退出用户等待路径。

## 隐私边界

默认模式仍然不 Hook 微信 / QQ、不解密私有数据库、不持续监听剪贴板、不自动发送消息。通知访问与 Accessibility 都需要用户显式授权；Accessibility 只处理微信 / QQ 的前台 UI 事件和当前可见内容，并过滤输入框草稿。

后台 ASR 默认开启，但只处理系统已经明确授予 LULU 访问能力的音频来源；当前实现优先使用通知携带的音频 URI，不扫描微信 / QQ 私有目录。UI 转文字 fallback 默认关闭，只有用户显式开启且主动点“噜”时才工作。手机 `ChatSyncOutbox` 保存待补传文字/屏幕观察，`VoiceIngestionQueue` 保存 pending / waiting_audio / failed 语音 workflow 状态。

## 项目内 Android 工具链与验证

Android Studio、JDK 17、Gradle 8.9、Android SDK、Platform-Tools/adb、API 35 和 Build Tools 34.0.0 均安装在仓库的 `.tools/android/` 下，并由 `.gitignore` 排除，不依赖系统全局安装。

真实 Debug APK 已通过 `:app:assembleDebug` 构建，输出为：

`apps/android/app/build/outputs/apk/debug/app-debug.apk`

构建产物已通过 `aapt` 元数据检查、`zipalign` 对齐检查和 `apksigner` v2 签名验证。`lintDebug` 也能成功完成，目前无 Lint Error，剩余警告主要是 programmatic UI 的硬编码中文字符串以及应用图标/数据提取规则等后续 UI 打磨项。

项目根目录可直接执行：

`powershell -ExecutionPolicy Bypass -File .\scripts\dev\build-android.ps1`

Android Studio 使用：

`powershell -ExecutionPolicy Bypass -File .\scripts\dev\start-android-studio.ps1`

项目内 adb 使用：

`powershell -ExecutionPolicy Bypass -File .\scripts\dev\adb-android.ps1 devices -l`

## Ace 3 Pro 真机验证

已在 OnePlus Ace 3 Pro（PJX110，Android 16 / API 36）完成：

- USB ADB 连接、APK 覆盖安装与 Activity 启动。
- `adb reverse tcp:8123 tcp:8123` 手机到 Windows Backend 链路。
- 登录 Session、悬浮窗权限和前台 `CoachOverlayService`。
- 微信测试账号和 QQ 测试账号分别生成 A/B/C。
- 点击 B 后只复制文本并收起为悬浮“噜”，没有自动发送。
- 同一 Person 下，微信 Thread 与 QQ Thread 的最近消息计数相互隔离，同时共享人物长期记忆。
- 新版首页和新版 A/B/C 底部浮层均已通过真机截图检查。
- 微信 Accessibility 文本节点为空时，本地截图 + ML Kit 中文 OCR fallback 已在真机跑通，点“噜”到 A/B/C 为 2.404 秒。
- NotificationListener、被动前台同步、SQLite Outbox、screen:v3 幂等键、45 秒短窗口 screen reconcile 和悬浮层 OCR 排除均已完成真机验证；同屏重复观察只 merge，数据库不再持续增长。
- 当前微信页面的语音气泡已实测为“扬声器图标 + 2\"/10\"/5\"/21\"秒数”。新版 OCR 直接把这些秒数识别为 voice marker，并记录“语音 N 秒，内容尚未转写”的占位，而不是普通文本。
- 后台语音主链路已改为 VoiceAudioSourceAdapter + waiting_audio：通知如果携带可访问 audio URI 就直接后台 ASR；当前 Ace 3 Pro 的公开 MediaStore 没有索引这些微信语音，因此没有可访问音频时会保持 waiting_audio，不伪装成已完成转写。
- 微信 UI“转文字”仅保留为默认关闭的显式 fallback；真机已确认当前微信长按语音菜单包含“转文字”，fallback 只有用户主动开启且点“噜”时才允许使用。
