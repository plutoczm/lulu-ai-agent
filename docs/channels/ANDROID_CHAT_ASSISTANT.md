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

后端 PostgreSQL 的 relationship_thread_message 保存每个账号最近最多 50 条原始消息。表内同时记录 chat_id、person_id、account_id、platform、sender、message_text、message_time。

后端 PGVector 的 relationship_memory 保存压缩后的长期关系事实和向量。长期事实按 personId 共享，并记录 source_account_id 和 source_platform 作为来源。

当前开发环境中，这两类服务端数据都存放在 Windows 本机 Docker 容器 lulu-ai-agent-pgvector 所使用的 PostgreSQL 数据卷中。以后如果 LULU Backend / PostgreSQL 部署到 Linux 服务器，服务端聊天数据也会随数据库部署位置移动，不再固定存于 Windows。

## 生成回复

当前账号复制的新聊天 → 读取同一 Person 的长期记忆 → 读取当前 Account 最近最多 30 条消息 → 加上本轮复制内容 → 生成 A/B/C → 本轮消息写回当前 Account Thread → 新的稳定事实写入 Person 长期记忆。

## 隐私边界

不 Hook 微信 / QQ，不 Root，不解密私有数据库，不后台持续读取完整聊天记录，不持续监听剪贴板，不自动发送消息。

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

当前还没有连接 Android 真机，因此 APK 已完成真实编译与静态产物验证，但尚未完成真机安装、微信悬浮窗和 QQ 悬浮窗的端到端设备验证。
