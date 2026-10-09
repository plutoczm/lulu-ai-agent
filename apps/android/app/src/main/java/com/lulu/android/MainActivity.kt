package com.lulu.android

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val bg = Color.rgb(250, 247, 242)
    private val card = Color.WHITE
    private val ink = Color.rgb(62, 49, 39)
    private val muted = Color.rgb(133, 122, 112)
    private val gold = Color.rgb(218, 154, 82)
    private val goldDark = Color.rgb(184, 118, 48)
    private val border = Color.rgb(238, 226, 214)
    private val softGold = Color.rgb(255, 248, 238)
    private val softGreen = Color.rgb(239, 247, 243)
    private val green = Color.rgb(83, 132, 105)
    private val softRose = Color.rgb(253, 242, 239)
    private val red = Color.rgb(177, 83, 72)

    private lateinit var connectionChip: TextView
    private lateinit var personTitle: TextView
    private lateinit var accountTitle: TextView
    private lateinit var memoryStatus: TextView
    private lateinit var helperStatus: TextView
    private lateinit var primaryButton: TextView
    private lateinit var syncPermissionStatus: TextView
    private lateinit var backgroundVoiceButton: TextView
    private lateinit var voiceUiFallbackButton: TextView

    private var selectedPersonId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = bg
        window.navigationBarColor = bg
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(bg)
        }
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(36))
        }
        scroll.addView(page)
        setContentView(scroll)

        addHero(page)
        addIdentityCard(page)
        addPrimaryAction(page)
        addHowToCard(page)
        addManagementCard(page)
        addConnectionCard(page)
        addFooter(page)

        selectedPersonId =
            IdentityStore.activeIdentity(this)?.person?.id
                ?: IdentityStore.persons(this).firstOrNull()?.id

        refreshDashboard(loadRemoteStatus = true)
    }

    override fun onResume() {
        super.onResume()
        ChatSyncCoordinator.flushPending(this)
        VoiceIngestionCoordinator.flushPending(this)
        if (::personTitle.isInitialized) {
            refreshDashboard(loadRemoteStatus = true)
        }
        if (::syncPermissionStatus.isInitialized) {
            refreshSyncPermissionStatus()
        }
    }

    private fun addHero(page: LinearLayout) {
        val hero = cardContainer(
            fill = Color.rgb(255, 252, 247),
            radius = 24,
            padding = 18
        ).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                    Color.rgb(255, 251, 245),
                    Color.rgb(255, 246, 235)
                )
            ).apply {
                cornerRadius = dp(24).toFloat()
                setStroke(dp(1), Color.rgb(245, 228, 207))
            }
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val mark = ImageView(this).apply {
            setImageResource(R.drawable.lulu_mark)
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = rounded(Color.rgb(247, 227, 201), 18)
            clipToOutline = true
            outlineProvider = ViewOutlineProvider.BACKGROUND
        }
        top.addView(
            mark,
            LinearLayout.LayoutParams(dp(58), dp(58))
        )

        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, 0, 0)
        }
        brand.addView(TextView(this).apply {
            text = "噜噜"
            textSize = 26f
            setTextColor(ink)
            setTypeface(typeface, Typeface.BOLD)
        })
        brand.addView(TextView(this).apply {
            text = "手机聊天回复助手"
            textSize = 13f
            setTextColor(muted)
            setPadding(0, dp(2), 0, 0)
        })
        top.addView(
            brand,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        connectionChip = chip("快回复", softGreen, green)
        top.addView(connectionChip)

        hero.addView(top)
        hero.addView(TextView(this).apply {
            text = "直接停留在微信 / QQ 聊天界面，点悬浮“噜”，几秒拿到 A / B / C。无需先复制聊天内容，也不会替你发送。"
            textSize = 14f
            setTextColor(Color.rgb(104, 91, 80))
            setLineSpacing(0f, 1.18f)
            setPadding(0, dp(16), 0, 0)
        })

        val chips = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(14), 0, 0)
        }
        chips.addView(chip("人物记忆共享", softGold, goldDark))
        chips.addView(space(dp(8), 1))
        chips.addView(chip("账号上下文隔离", softGreen, green))
        hero.addView(chips)

        addBlock(page, hero, top = 0)
    }

    private fun addIdentityCard(page: LinearLayout) {
        val box = cardContainer()

        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        head.addView(TextView(this).apply {
            text = "当前聊天身份"
            textSize = 14f
            setTextColor(muted)
            setTypeface(typeface, Typeface.BOLD)
        }, LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1f
        ))
        head.addView(smallAction("切换账号") {
            showAccountChooser()
        })
        box.addView(head)

        personTitle = TextView(this).apply {
            textSize = 25f
            setTextColor(ink)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(14), 0, 0)
        }
        box.addView(personTitle)

        accountTitle = TextView(this).apply {
            textSize = 15f
            setTextColor(goldDark)
            setPadding(0, dp(7), 0, 0)
        }
        box.addView(accountTitle)

        memoryStatus = TextView(this).apply {
            textSize = 13f
            setTextColor(muted)
            setPadding(0, dp(12), 0, 0)
        }
        box.addView(memoryStatus)

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(16), 0, 0)
        }
        actions.addView(
            secondaryButton("切换人物") {
                showPersonChooser()
            },
            weighted()
        )
        actions.addView(space(dp(10), 1))
        actions.addView(
            secondaryButton("编辑当前账号") {
                val account = IdentityStore.activeAccount(this@MainActivity)
                if (account == null) {
                    toast("当前还没有账号")
                } else {
                    showAccountEditor(account)
                }
            },
            weighted()
        )
        box.addView(actions)

        addBlock(page, box)
    }

    private fun addPrimaryAction(page: LinearLayout) {
        val box = cardContainer(
            fill = Color.rgb(255, 249, 240),
            radius = 24,
            padding = 18
        )

        box.addView(TextView(this).apply {
            text = "准备好后，回微信 / QQ 使用"
            textSize = 13f
            setTextColor(muted)
        })

        primaryButton = TextView(this).apply {
            text = "开启悬浮助手"
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(16), dp(17), dp(16), dp(17))
            background = gradientButton()
            elevation = dp(5).toFloat()
            setOnClickListener { startOverlayAssistant() }
        }
        box.addView(
            primaryButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(12)
            }
        )

        helperStatus = TextView(this).apply {
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(muted)
            setPadding(0, dp(12), 0, 0)
        }
        box.addView(helperStatus)

        addBlock(page, box)
    }

    private fun addHowToCard(page: LinearLayout) {
        val box = cardContainer()
        box.addView(sectionTitle("怎么用"))

        box.addView(stepRow(
            "1",
            "照常使用微信或 QQ",
            "通知会增量收集对方新消息；你正常打开聊天时，噜噜低频补齐双方已显示的消息气泡。"
        ))
        box.addView(stepDivider())
        box.addView(stepRow(
            "2",
            "需要建议时点右侧悬浮“噜”",
            "先强制补齐当前屏幕、自动处理可见语音转文字，再结合数据库历史和人物记忆生成 A/B/C。"
        ))
        box.addView(stepDivider())
        box.addView(stepRow(
            "3",
            "选择 A / B / C",
            "点击优先直接填入当前聊天输入框；不会自动发送。"
        ))

        addBlock(page, box)
    }

    private fun addManagementCard(page: LinearLayout) {
        val box = cardContainer()
        box.addView(sectionTitle("人物与账号"))

        val row1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(12), 0, 0)
        }
        row1.addView(
            secondaryButton("编辑人物") {
                val person = currentPerson()
                if (person == null) showPersonEditor(null)
                else showPersonEditor(person)
            },
            weighted()
        )
        row1.addView(space(dp(10), 1))
        row1.addView(
            secondaryButton("新建人物") {
                showPersonEditor(null)
            },
            weighted()
        )
        box.addView(row1)

        val row2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10), 0, 0)
        }
        row2.addView(
            secondaryButton("添加微信 / QQ") {
                showAccountEditor(null)
            },
            weighted()
        )
        row2.addView(space(dp(10), 1))
        row2.addView(
            secondaryButton("改绑账号") {
                showReassignAccountDialog()
            },
            weighted()
        )
        box.addView(row2)

        val row3 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10), 0, 0)
        }
        row3.addView(
            secondaryButton("导入历史聊天") {
                showHistoryImportDialog()
            },
            weighted()
        )
        row3.addView(space(dp(10), 1))
        row3.addView(
            secondaryButton("记忆管理") {
                showMemoryManagement()
            },
            weighted()
        )
        box.addView(row3)

        syncPermissionStatus = TextView(this).apply {
            textSize = 12f
            setTextColor(muted)
            setPadding(0, dp(14), 0, dp(6))
        }
        box.addView(syncPermissionStatus)
        refreshSyncPermissionStatus()

        val row4 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        row4.addView(
            secondaryButton("通知消息同步") {
                startActivity(
                    Intent(
                        Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
                    )
                )
            },
            weighted()
        )
        row4.addView(space(dp(10), 1))
        row4.addView(
            secondaryButton("前台聊天同步") {
                startActivity(
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                )
            },
            weighted()
        )
        box.addView(row4)

        backgroundVoiceButton =
            secondaryButton(backgroundVoiceButtonLabel()) {
                val enabled =
                    !LuluPrefs.backgroundVoiceEnabled(this)
                LuluPrefs.setBackgroundVoiceEnabled(this, enabled)
                backgroundVoiceButton.text =
                    backgroundVoiceButtonLabel()

                if (enabled) {
                    val retried = VoiceIngestionQueue.retryFailed(this)
                    VoiceIngestionCoordinator.flushPending(this)
                    toast(
                        if (retried > 0) {
                            "后台 ASR 已开启；已恢复 $retried 条失败任务"
                        } else {
                            "后台 ASR 已开启；收到可访问音频后自动转写"
                        }
                    )
                } else {
                    toast("后台 ASR 已关闭；文字同步不受影响")
                }
                refreshSyncPermissionStatus()
            }
        box.addView(
            backgroundVoiceButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(10)
            }
        )

        voiceUiFallbackButton =
            secondaryButton(voiceUiFallbackButtonLabel()) {
                val enabled =
                    !LuluPrefs.voiceUiFallbackEnabled(this)
                LuluPrefs.setVoiceUiFallbackEnabled(this, enabled)
                voiceUiFallbackButton.text =
                    voiceUiFallbackButtonLabel()
                toast(
                    if (enabled) {
                        "微信 UI 转文字 fallback 已开启；仅点“噜”时使用"
                    } else {
                        "微信 UI 转文字 fallback 已关闭"
                    }
                )
                refreshSyncPermissionStatus()
            }
        box.addView(
            voiceUiFallbackButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(8)
            }
        )

        addBlock(page, box)
    }

    private fun addConnectionCard(page: LinearLayout) {
        val box = cardContainer(
            fill = Color.rgb(248, 249, 247)
        )
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val text = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        text.addView(TextView(this).apply {
            this.text = "后端连接"
            textSize = 14f
            setTextColor(ink)
            setTypeface(typeface, Typeface.BOLD)
        })
        text.addView(TextView(this).apply {
            val baseUrl = LuluPrefs.baseUrl(this@MainActivity)
            this.text = if (baseUrl.isBlank()) {
                "尚未设置"
            } else {
                baseUrl
            }
            textSize = 12f
            setTextColor(muted)
            setPadding(0, dp(4), 0, 0)
        })
        row.addView(
            text,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        )
        row.addView(smallAction("连接设置") {
            showLoginDialog()
        })
        box.addView(row)

        addBlock(page, box)
    }

    private fun addFooter(page: LinearLayout) {
        page.addView(TextView(this).apply {
            text = "隐私边界：不 Hook、不读取聊天数据库、不自动发送；后台 ASR 优先，UI fallback 需显式开启。"
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(165, 154, 144))
            setPadding(dp(8), dp(8), dp(8), 0)
        })
    }

    private fun refreshDashboard(loadRemoteStatus: Boolean) {
        val person = currentPerson()
        val identity = currentIdentity()

        connectionChip.text =
            if (LuluPrefs.cookie(this).isNotBlank()) "已连接"
            else "未登录"
        connectionChip.setTextColor(
            if (LuluPrefs.cookie(this).isNotBlank()) green else goldDark
        )

        if (person == null) {
            personTitle.text = "先建立一个现实人物"
            accountTitle.text = "人物可以绑定多个微信 / QQ 账号"
            memoryStatus.text = "长期记忆会按现实人物共享"
            helperStatus.text = "先创建人物和账号，再开启悬浮助手"
            primaryButton.alpha = 0.65f
            return
        }

        personTitle.text = person.displayName
        if (identity == null) {
            accountTitle.text = "还没有绑定微信 / QQ 账号"
            memoryStatus.text =
                person.relationshipStage + " · " + person.goal
            helperStatus.text = "给这个人物添加至少一个账号"
            primaryButton.alpha = 0.65f
            return
        }

        accountTitle.text =
            identity.account.platformLabel +
                " · " +
                identity.account.alias +
                if (identity.account.accountLabel.isBlank()) {
                    ""
                } else {
                    " · " + identity.account.accountLabel
                }

        memoryStatus.text =
            person.relationshipStage +
                " · " +
                person.goal +
                " · 正在读取记忆状态…"
        helperStatus.text =
            when {
                !Settings.canDrawOverlays(this) ->
                    "首次使用需要授权悬浮窗"
                !LuluAccessibilityService.isEnabled(this) ->
                    "还需开启“噜噜聊天读取”无障碍权限"
                else ->
                    "已就绪 · 打开微信 / QQ 聊天后直接点“噜”"
            }
        primaryButton.alpha = 1f

        if (loadRemoteStatus) {
            loadThreadStatus(identity)
        }
    }

    private fun currentPerson(): PersonProfile? {
        selectedPersonId?.let { id ->
            IdentityStore.person(this, id)?.let { return it }
        }
        return IdentityStore.activeIdentity(this)?.person
            ?: IdentityStore.persons(this).firstOrNull()
    }

    private fun currentIdentity(): ActiveIdentity? {
        val active = IdentityStore.activeIdentity(this) ?: return null
        val selected = selectedPersonId
        return if (selected == null || selected == active.person.id) {
            active
        } else {
            null
        }
    }

    private fun showLoginDialog() {
        val wrapper = dialogForm()
        val base = formInput(
            "后端地址",
            LuluPrefs.baseUrl(this).ifBlank {
                "http://127.0.0.1:8123"
            },
            InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_VARIATION_URI
        )
        val email = formInput(
            "噜噜账号邮箱",
            "",
            InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        )
        val password = formInput(
            "密码",
            "",
            InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_VARIATION_PASSWORD
        )
        wrapper.addView(base)
        wrapper.addView(formGap())
        wrapper.addView(email)
        wrapper.addView(formGap())
        wrapper.addView(password)

        val dialog = AlertDialog.Builder(this)
            .setTitle("后端连接")
            .setMessage("地址只需设置一次。账号密码只用于本次登录。")
            .setView(wrapper)
            .setPositiveButton("登录", null)
            .setNegativeButton("取消", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener {
                    val url = base.text.toString().trim().trimEnd('/')
                    if (url.isBlank() ||
                        email.text.isBlank() ||
                        password.text.isBlank()
                    ) {
                        toast("请填写地址、邮箱和密码")
                        return@setOnClickListener
                    }

                    dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                        .isEnabled = false
                    Thread {
                        runCatching {
                            CoachApi.login(
                                url,
                                email.text.toString(),
                                password.text.toString()
                            )
                        }.onSuccess { cookie ->
                            LuluPrefs.setBaseUrl(this, url)
                            LuluPrefs.setCookie(this, cookie)
                            runOnUiThread {
                                toast("连接成功")
                                dialog.dismiss()
                                refreshDashboard(loadRemoteStatus = true)
                            }
                        }.onFailure { error ->
                            runOnUiThread {
                                dialog.getButton(
                                    AlertDialog.BUTTON_POSITIVE
                                ).isEnabled = true
                                toast(error.message ?: "登录失败")
                            }
                        }
                    }.start()
                }
        }
        dialog.show()
    }

    private fun showPersonChooser() {
        val persons = IdentityStore.persons(this)
        if (persons.isEmpty()) {
            showPersonEditor(null)
            return
        }

        val labels = persons.map { person ->
            val count =
                IdentityStore.accountsForPerson(this, person.id).size
            person.displayName + " · " + count + " 个账号"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("选择现实人物")
            .setItems(labels) { _, which ->
                val person = persons[which]
                selectedPersonId = person.id
                IdentityStore.accountsForPerson(this, person.id)
                    .firstOrNull()
                    ?.let {
                        IdentityStore.setActiveAccount(this, it.id)
                    }
                refreshDashboard(loadRemoteStatus = true)
            }
            .setPositiveButton("新建人物") { _, _ ->
                showPersonEditor(null)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showPersonEditor(person: PersonProfile?) {
        val wrapper = dialogForm()
        val name = formInput(
            "人物名称，例如 小王",
            person?.displayName.orEmpty()
        )
        val stage = formInput(
            "关系阶段，例如 暧昧 / 恋爱 / 朋友",
            person?.relationshipStage.orEmpty()
        )
        val goal = formInput(
            "长期目标，例如 自然推进关系",
            person?.goal ?: "自然推进关系"
        )
        val style = formInput(
            "你的回复风格",
            person?.userStyle ?: "短句、自然、不油腻"
        )

        wrapper.addView(name)
        wrapper.addView(formGap())
        wrapper.addView(stage)
        wrapper.addView(formGap())
        wrapper.addView(goal)
        wrapper.addView(formGap())
        wrapper.addView(style)

        AlertDialog.Builder(this)
            .setTitle(if (person == null) "新建现实人物" else "编辑人物资料")
            .setView(wrapper)
            .setPositiveButton("保存") { _, _ ->
                val displayName = name.text.toString().trim()
                if (displayName.isBlank()) {
                    toast("人物名称不能为空")
                    return@setPositiveButton
                }

                val saved = if (person == null) {
                    IdentityStore.createPerson(
                        displayName,
                        stage.text.toString(),
                        goal.text.toString(),
                        style.text.toString()
                    )
                } else {
                    person.copy(
                        displayName = displayName,
                        relationshipStage =
                            stage.text.toString()
                                .ifBlank { "未提供" },
                        goal =
                            goal.text.toString()
                                .ifBlank { "自动判断" },
                        userStyle =
                            style.text.toString()
                                .ifBlank { "短句、自然、不油腻" }
                    )
                }
                IdentityStore.upsertPerson(this, saved)
                selectedPersonId = saved.id
                refreshDashboard(loadRemoteStatus = false)
                toast("人物资料已保存")
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showAccountChooser() {
        val accounts = IdentityStore.accounts(this)
        if (accounts.isEmpty()) {
            showAccountEditor(null)
            return
        }

        val labels = accounts.map { account ->
            val person =
                IdentityStore.person(this, account.personId)
            (person?.displayName ?: "未知人物") +
                "｜" +
                account.displayName
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("切换微信 / QQ 账号")
            .setItems(labels) { _, which ->
                val account = accounts[which]
                IdentityStore.setActiveAccount(this, account.id)
                selectedPersonId = account.personId
                refreshDashboard(loadRemoteStatus = true)
            }
            .setPositiveButton("添加账号") { _, _ ->
                showAccountEditor(null)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showAccountEditor(account: ContactAccount?) {
        val person = if (account == null) {
            currentPerson()
        } else {
            IdentityStore.person(this, account.personId)
        }
        if (person == null) {
            toast("请先建立人物")
            return
        }

        val wrapper = dialogForm()
        val spinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf("微信", "QQ")
            )
            setSelection(if (account?.platform == "qq") 1 else 0)
        }
        val alias = formInput(
            "该账号的备注 / 昵称",
            account?.alias.orEmpty()
        )
        val label = formInput(
            "账号标签，例如 大号 / 小号 / 工作号",
            account?.accountLabel.orEmpty()
        )
        val chatTitle = formInput(
            "聊天标题（可留空，首次点“噜”自动绑定）",
            account?.chatTitle.orEmpty()
        )

        wrapper.addView(TextView(this).apply {
            text = "归属人物：" + person.displayName
            textSize = 13f
            setTextColor(muted)
            setPadding(0, 0, 0, dp(8))
        })
        wrapper.addView(spinner)
        wrapper.addView(formGap())
        wrapper.addView(alias)
        wrapper.addView(formGap())
        wrapper.addView(label)
        wrapper.addView(formGap())
        wrapper.addView(chatTitle)

        AlertDialog.Builder(this)
            .setTitle(if (account == null) "添加微信 / QQ 账号" else "编辑当前账号")
            .setView(wrapper)
            .setPositiveButton("保存") { _, _ ->
                val aliasText = alias.text.toString().trim()
                if (aliasText.isBlank()) {
                    toast("账号备注不能为空")
                    return@setPositiveButton
                }

                val platform =
                    if (spinner.selectedItemPosition == 1) "qq"
                    else "wechat"

                val titleText = chatTitle.text
                    .toString()
                    .trim()
                val saved = if (account == null) {
                    IdentityStore.createAccount(
                        person.id,
                        platform,
                        aliasText,
                        label.text.toString()
                    ).copy(
                        chatTitle = titleText
                    )
                } else {
                    account.copy(
                        platform = platform,
                        alias = aliasText,
                        accountLabel =
                            label.text.toString().trim(),
                        chatTitle = titleText
                    )
                }
                IdentityStore.upsertAccount(this, saved)
                selectedPersonId = saved.personId
                refreshDashboard(loadRemoteStatus = true)
                toast("账号已保存")
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showReassignAccountDialog() {
        val identity = IdentityStore.activeIdentity(this)
        if (identity == null) {
            toast("请先选择要改绑的账号")
            return
        }
        val candidates = IdentityStore.persons(this)
            .filter { it.id != identity.person.id }
        if (candidates.isEmpty()) {
            toast("至少还需要另一个人物")
            return
        }
        if (LuluPrefs.cookie(this).isBlank()) {
            toast("请先登录，确保服务器记忆一起迁移")
            return
        }

        AlertDialog.Builder(this)
            .setTitle("改绑 " + identity.account.displayName)
            .setMessage("只迁移这个账号来源的长期事实，账号自己的最近聊天不变。")
            .setItems(
                candidates.map { it.displayName }.toTypedArray()
            ) { _, which ->
                val target = candidates[which]
                helperStatus.text = "正在迁移账号记忆归属…"
                Thread {
                    runCatching {
                        CoachApi.reassignAccount(
                            LuluPrefs.baseUrl(this),
                            LuluPrefs.cookie(this),
                            identity.account,
                            identity.person.id,
                            target.id
                        )
                    }.onSuccess { result ->
                        IdentityStore.reassignAccountLocal(
                            this,
                            identity.account.id,
                            target.id
                        )
                        selectedPersonId = target.id
                        runOnUiThread {
                            toast(
                                "改绑完成，迁移 " +
                                    result.movedMemoryFacts +
                                    " 条长期记忆"
                            )
                            refreshDashboard(loadRemoteStatus = true)
                        }
                    }.onFailure { error ->
                        runOnUiThread {
                            toast(error.message ?: "改绑失败")
                            refreshDashboard(loadRemoteStatus = true)
                        }
                    }
                }.start()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showHistoryImportDialog() {
        val identity = currentIdentity()
        if (identity == null) {
            toast("请先选择人物和账号")
            return
        }
        if (LuluPrefs.cookie(this).isBlank()) {
            toast("请先登录噜噜")
            return
        }

        val input = EditText(this).apply {
            hint = "每行一条，例如：\n我：周末有空吗\n对方：周六可能可以"
            inputType =
                InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                    InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            minLines = 8
            maxLines = 14
            gravity = Gravity.TOP
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(Color.WHITE, 14, border)
        }
        val wrapper = FrameLayout(this).apply {
            setPadding(dp(12), 0, dp(12), 0)
            addView(
                input,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }

        AlertDialog.Builder(this)
            .setTitle("导入 " + identity.account.displayName + " 历史")
            .setMessage(
                "长期事实归到人物 " +
                    identity.person.displayName +
                    "；原始消息只留在当前账号最近 50 条。"
            )
            .setView(wrapper)
            .setPositiveButton("开始导入") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isBlank()) {
                    toast("没有可导入的聊天内容")
                } else {
                    importHistory(identity, text)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun importHistory(
        identity: ActiveIdentity,
        historyText: String
    ) {
        helperStatus.text = "正在整理长期关系记忆…"
        Thread {
            runCatching {
                CoachApi.importHistory(
                    LuluPrefs.baseUrl(this),
                    LuluPrefs.cookie(this),
                    identity.person,
                    identity.account,
                    historyText
                )
            }.onSuccess { info ->
                runOnUiThread {
                    toast(
                        "导入完成，新增长期记忆 " +
                            info.importedFacts +
                            " 条"
                    )
                    refreshDashboard(loadRemoteStatus = true)
                }
            }.onFailure { error ->
                runOnUiThread {
                    toast(error.message ?: "历史导入失败")
                    refreshDashboard(loadRemoteStatus = true)
                }
            }
        }.start()
    }

    private fun showMemoryManagement() {
        val identity = currentIdentity()
        val person = currentPerson()
        if (person == null) {
            toast("还没有人物")
            return
        }

        val actions = arrayOf(
            "清空当前账号最近聊天",
            "清空人物长期记忆"
        )
        AlertDialog.Builder(this)
            .setTitle("记忆管理")
            .setMessage(
                "人物长期记忆跨账号共享；最近聊天只属于当前账号。"
            )
            .setItems(actions) { _, which ->
                if (which == 0) {
                    if (identity == null) {
                        toast("当前人物还没有账号")
                    } else {
                        confirmClearAccount(identity)
                    }
                } else {
                    confirmClearPersonMemory(
                        person,
                        identity?.account
                    )
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun confirmClearAccount(identity: ActiveIdentity) {
        AlertDialog.Builder(this)
            .setTitle("清空当前账号最近聊天？")
            .setMessage(
                "只删除 " +
                    identity.account.displayName +
                    " 的最近原文，不删除人物长期记忆。"
            )
            .setPositiveButton("清空") { _, _ ->
                Thread {
                    runCatching {
                        CoachApi.clearAccount(
                            LuluPrefs.baseUrl(this),
                            LuluPrefs.cookie(this),
                            identity.person,
                            identity.account
                        )
                    }.onSuccess {
                        runOnUiThread {
                            toast("当前账号最近聊天已清空")
                            refreshDashboard(loadRemoteStatus = true)
                        }
                    }.onFailure { error ->
                        runOnUiThread {
                            toast(error.message ?: "清空失败")
                        }
                    }
                }.start()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun confirmClearPersonMemory(
        person: PersonProfile,
        activeAccount: ContactAccount?
    ) {
        AlertDialog.Builder(this)
            .setTitle("清空 " + person.displayName + " 的长期记忆？")
            .setMessage(
                "会删除这个现实人物跨微信 / QQ 账号共享的长期事实，各账号最近聊天仍保留。"
            )
            .setPositiveButton("清空") { _, _ ->
                Thread {
                    runCatching {
                        CoachApi.clearPersonMemory(
                            LuluPrefs.baseUrl(this),
                            LuluPrefs.cookie(this),
                            person,
                            activeAccount
                        )
                    }.onSuccess {
                        runOnUiThread {
                            toast("人物长期记忆已清空")
                            refreshDashboard(loadRemoteStatus = true)
                        }
                    }.onFailure { error ->
                        runOnUiThread {
                            toast(error.message ?: "清空失败")
                        }
                    }
                }.start()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun startOverlayAssistant() {
        val identity = currentIdentity()
        if (identity == null) {
            toast("请先给当前人物绑定并选择一个账号")
            return
        }
        if (LuluPrefs.cookie(this).isBlank()) {
            toast("请先连接并登录后端")
            showLoginDialog()
            return
        }

        if (!Settings.canDrawOverlays(this)) {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + packageName)
                )
            )
            return
        }

        if (!LuluAccessibilityService.isEnabled(this)) {
            toast("请开启“噜噜聊天读取”；它会低频同步已显示的聊天气泡，并在点“噜”时读取当前页")
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }

        requestNotificationPermissionIfNeeded()

        val service = Intent(
            this,
            CoachOverlayService::class.java
        ).apply {
            action = CoachOverlayService.ACTION_START_TRIGGER
        }
        startForegroundService(service)

        helperStatus.text =
            "已开启 · 回微信 / QQ 当前聊天，直接点右侧悬浮“噜”"
        toast("悬浮助手已开启；不需要再复制聊天内容")
    }

    private fun notificationListenerEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver,
            "enabled_notification_listeners"
        ).orEmpty()
        return enabled.contains(packageName)
    }

    private fun backgroundVoiceButtonLabel(): String =
        if (LuluPrefs.backgroundVoiceEnabled(this)) {
            "后台 ASR · 已开启"
        } else {
            "后台 ASR · 点击开启"
        }

    private fun voiceUiFallbackButtonLabel(): String =
        if (LuluPrefs.voiceUiFallbackEnabled(this)) {
            "微信 UI 转文字 fallback · 已开启"
        } else {
            "微信 UI 转文字 fallback · 默认关闭"
        }

    private fun refreshSyncPermissionStatus() {
        if (!::syncPermissionStatus.isInitialized) return
        val incoming = notificationListenerEnabled()
        val foreground = LuluAccessibilityService.isEnabled(this)
        val pending = ChatSyncOutbox.count(this)
        val backgroundVoice =
            LuluPrefs.backgroundVoiceEnabled(this)
        val uiFallback =
            LuluPrefs.voiceUiFallbackEnabled(this)
        val voicePending = VoiceIngestionQueue.count(this)
        val voiceFailed = VoiceIngestionQueue.failedCount(this)

        if (::backgroundVoiceButton.isInitialized) {
            backgroundVoiceButton.text =
                backgroundVoiceButtonLabel()
        }
        if (::voiceUiFallbackButton.isInitialized) {
            voiceUiFallbackButton.text =
                voiceUiFallbackButtonLabel()
        }

        syncPermissionStatus.text =
            "消息同步 · 通知 " +
                (if (incoming) "已开启" else "未开启") +
                " · 前台气泡 " +
                (if (foreground) "已开启" else "未开启") +
                " · 后台 ASR " +
                (if (backgroundVoice) "已开启" else "关闭") +
                " · UI fallback " +
                (if (uiFallback) "开" else "关") +
                if (pending > 0) {
                    " · 待补传 $pending 条"
                } else {
                    " · 已同步"
                } +
                if (voicePending > 0) {
                    " · 语音待处理 $voicePending"
                } else {
                    ""
                } +
                if (voiceFailed > 0) {
                    " · 语音失败 $voiceFailed"
                } else {
                    ""
                }
        syncPermissionStatus.setTextColor(
            if (incoming &&
                foreground &&
                pending == 0 &&
                voicePending == 0 &&
                voiceFailed == 0
            ) {
                green
            } else {
                goldDark
            }
        )
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                3102
            )
        }
    }

    private fun loadThreadStatus(identity: ActiveIdentity) {
        val baseUrl = LuluPrefs.baseUrl(this)
        val cookie = LuluPrefs.cookie(this)
        if (baseUrl.isBlank() || cookie.isBlank()) {
            memoryStatus.text =
                identity.person.relationshipStage +
                    " · 尚未连接后端"
            return
        }

        Thread {
            runCatching {
                CoachApi.threadStatus(
                    baseUrl,
                    cookie,
                    identity.person,
                    identity.account
                )
            }.onSuccess { info ->
                runOnUiThread {
                    val current = currentIdentity()
                    if (current?.account?.id ==
                        identity.account.id
                    ) {
                        memoryStatus.text =
                            "已同步 " +
                                info.recentMessages +
                                " 条消息 · 长期记忆 " +
                                info.memoryFacts +
                                " 条 · " +
                                identity.person.relationshipStage
                        connectionChip.text = "已连接"
                        connectionChip.setTextColor(green)
                    }
                }
            }.onFailure {
                runOnUiThread {
                    memoryStatus.text =
                        identity.person.relationshipStage +
                            " · 后端暂不可用"
                    connectionChip.text = "待连接"
                    connectionChip.setTextColor(goldDark)
                }
            }
        }.start()
    }

    private fun cardContainer(
        fill: Int = card,
        radius: Int = 22,
        padding: Int = 16
    ): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                dp(padding),
                dp(padding),
                dp(padding),
                dp(padding)
            )
            background = rounded(fill, radius, border)
            elevation = dp(1).toFloat()
        }

    private fun sectionTitle(text: String): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 17f
            setTextColor(ink)
            setTypeface(typeface, Typeface.BOLD)
        }

    private fun stepRow(
        number: String,
        title: String,
        subtitle: String
    ): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            setPadding(0, dp(12), 0, dp(12))

            addView(TextView(this@MainActivity).apply {
                text = number
                textSize = 13f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                setTypeface(typeface, Typeface.BOLD)
                background = rounded(gold, 11)
            }, LinearLayout.LayoutParams(dp(34), dp(34)))

            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), 0, 0, 0)
                addView(TextView(this@MainActivity).apply {
                    text = title
                    textSize = 14f
                    setTextColor(ink)
                    setTypeface(typeface, Typeface.BOLD)
                })
                addView(TextView(this@MainActivity).apply {
                    text = subtitle
                    textSize = 12f
                    setTextColor(muted)
                    setLineSpacing(0f, 1.15f)
                    setPadding(0, dp(4), 0, 0)
                })
            }, LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            ))
        }

    private fun stepDivider(): View =
        View(this).apply {
            setBackgroundColor(Color.rgb(245, 237, 229))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(1)
            ).apply {
                leftMargin = dp(46)
            }
        }

    private fun secondaryButton(
        text: String,
        onClick: () -> Unit
    ): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(ink)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(10), dp(12), dp(10), dp(12))
            background = rounded(
                Color.rgb(253, 250, 247),
                14,
                border
            )
            setOnClickListener { onClick() }
        }

    private fun smallAction(
        text: String,
        onClick: () -> Unit
    ): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(goldDark)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = rounded(softGold, 12)
            setOnClickListener { onClick() }
        }

    private fun chip(
        text: String,
        fill: Int,
        textColor: Int
    ): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(textColor)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = rounded(fill, 99)
        }

    private fun formInput(
        hintText: String,
        value: String,
        type: Int = InputType.TYPE_CLASS_TEXT
    ): EditText =
        EditText(this).apply {
            hint = hintText
            setText(value)
            inputType = type
            textSize = 14f
            setTextColor(ink)
            setHintTextColor(Color.rgb(172, 160, 149))
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = rounded(Color.WHITE, 12, border)
        }

    private fun dialogForm(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(4))
        }

    private fun formGap(): Space = space(1, dp(10))

    private fun gradientButton(): GradientDrawable =
        GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(
                Color.rgb(224, 167, 100),
                Color.rgb(211, 143, 66)
            )
        ).apply {
            cornerRadius = dp(17).toFloat()
        }

    private fun rounded(
        fill: Int,
        radius: Int,
        stroke: Int? = null
    ): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(radius).toFloat()
            if (stroke != null) {
                setStroke(dp(1), stroke)
            }
        }

    private fun addBlock(
        page: LinearLayout,
        view: View,
        top: Int = 14
    ) {
        page.addView(
            view,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(top)
            }
        )
    }

    private fun weighted(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1f
        )

    private fun space(width: Int, height: Int): Space =
        Space(this).apply {
            layoutParams = LinearLayout.LayoutParams(width, height)
        }

    private fun toast(text: String) {
        Toast.makeText(
            this,
            text,
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
