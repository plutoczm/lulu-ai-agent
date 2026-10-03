package com.lulu.android

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*

class MainActivity : Activity() {

    private lateinit var activeIdentityView: TextView
    private lateinit var threadInfoView: TextView
    private lateinit var statusView: TextView

    private lateinit var personNameInput: EditText
    private lateinit var stageInput: EditText
    private lateinit var goalInput: EditText
    private lateinit var styleInput: EditText

    private lateinit var platformSpinner: Spinner
    private lateinit var accountAliasInput: EditText
    private lateinit var accountLabelInput: EditText

    private var editingPersonId: String? = null
    private var editingAccountId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "噜噜手机聊天助手"

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 56)
        }
        val scroll = ScrollView(this).apply {
            addView(content)
        }
        setContentView(scroll)

        content.addView(TextView(this).apply {
            text = "噜噜 · 手机聊天回复助手"
            textSize = 24f
        })
        content.addView(TextView(this).apply {
            text = "一个现实人物可以绑定多个微信 / QQ 账号。人物共享长期关系记忆；每个账号保留自己的最近聊天，不会把大小号的局部上下文混在一起。"
            textSize = 14f
            setPadding(0, 14, 0, 24)
        })

        addLoginSection(content)
        addPersonSection(content)
        addAccountSection(content)
        addOverlaySection(content)

        refreshActiveIdentity(loadRemoteStatus = true)
    }

    private fun addLoginSection(content: LinearLayout) {
        addSectionTitle(content, "1. 后端与登录")

        val baseUrl = EditText(this).apply {
            hint = "后端地址，例如 http://192.168.1.10:8123"
            setText(LuluPrefs.baseUrl(this@MainActivity))
            inputType =
                InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_VARIATION_URI
        }
        val email = EditText(this).apply {
            hint = "噜噜账号邮箱"
            inputType =
                InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }
        val password = EditText(this).apply {
            hint = "密码"
            inputType =
                InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val loginButton = Button(this).apply {
            text = "保存地址并登录"
        }

        content.addView(baseUrl)
        content.addView(email)
        content.addView(password)
        content.addView(loginButton)

        loginButton.setOnClickListener {
            val url = baseUrl.text.toString().trim().trimEnd('/')
            if (url.isBlank()
                || email.text.isBlank()
                || password.text.isBlank()) {
                toast("请填写后端地址、邮箱和密码")
                return@setOnClickListener
            }

            LuluPrefs.setBaseUrl(this, url)
            loginButton.isEnabled = false
            statusView.text = "正在登录…"

            Thread {
                runCatching {
                    CoachApi.login(
                        url,
                        email.text.toString(),
                        password.text.toString()
                    )
                }.onSuccess { cookie ->
                    LuluPrefs.setCookie(this, cookie)
                    runOnUiThread {
                        loginButton.isEnabled = true
                        statusView.text = "登录成功。"
                        refreshActiveIdentity(
                            loadRemoteStatus = true
                        )
                    }
                }.onFailure { error ->
                    runOnUiThread {
                        loginButton.isEnabled = true
                        statusView.text =
                            error.message ?: "登录失败"
                    }
                }
            }.start()
        }
    }

    private fun addPersonSection(content: LinearLayout) {
        addSectionTitle(content, "2. 现实人物（共享长期记忆）")

        activeIdentityView = TextView(this).apply {
            textSize = 17f
            setPadding(0, 4, 0, 8)
        }
        threadInfoView = TextView(this).apply {
            textSize = 13f
            setPadding(0, 0, 0, 12)
        }

        val choosePersonButton = Button(this).apply {
            text = "选择人物"
        }
        val newPersonButton = Button(this).apply {
            text = "新建人物"
        }

        personNameInput = EditText(this).apply {
            hint = "人物名称，例如 小王"
        }
        stageInput = EditText(this).apply {
            hint = "关系阶段，例如 暧昧 / 恋爱 / 朋友"
        }
        goalInput = EditText(this).apply {
            hint = "长期目标，例如 自然推进关系"
        }
        styleInput = EditText(this).apply {
            hint = "你的回复风格"
            setText("短句、自然、不油腻")
        }
        val savePersonButton = Button(this).apply {
            text = "保存人物资料"
        }

        content.addView(activeIdentityView)
        content.addView(threadInfoView)
        content.addView(choosePersonButton)
        content.addView(newPersonButton)
        content.addView(personNameInput)
        content.addView(stageInput)
        content.addView(goalInput)
        content.addView(styleInput)
        content.addView(savePersonButton)

        choosePersonButton.setOnClickListener {
            showPersonChooser()
        }
        newPersonButton.setOnClickListener {
            startNewPerson()
        }
        savePersonButton.setOnClickListener {
            savePerson()
        }
    }

    private fun addAccountSection(content: LinearLayout) {
        addSectionTitle(content, "3. 微信 / QQ 账号（各自最近聊天）")

        content.addView(TextView(this).apply {
            text = "同一个人的大号、小号、QQ 都添加到同一人物下面。账号标签可写“大号 / 小号 / 工作号”等。"
            textSize = 14f
            setPadding(0, 0, 0, 8)
        })

        val chooseAccountButton = Button(this).apply {
            text = "选择当前账号"
        }
        val newAccountButton = Button(this).apply {
            text = "给当前人物添加账号"
        }
        val reassignButton = Button(this).apply {
            text = "把当前账号改绑到其他人物"
        }

        platformSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf("微信", "QQ")
            )
        }
        accountAliasInput = EditText(this).apply {
            hint = "该账号在聊天里的备注 / 昵称"
        }
        accountLabelInput = EditText(this).apply {
            hint = "账号标签，例如 大号 / 小号 / 旧号"
        }
        val saveAccountButton = Button(this).apply {
            text = "保存账号"
        }

        val importHistoryButton = Button(this).apply {
            text = "给当前账号导入 / 补充历史聊天"
        }
        val clearAccountButton = Button(this).apply {
            text = "清空当前账号最近聊天"
        }
        val clearPersonMemoryButton = Button(this).apply {
            text = "清空当前人物长期记忆"
        }

        content.addView(chooseAccountButton)
        content.addView(newAccountButton)
        content.addView(reassignButton)
        content.addView(platformSpinner)
        content.addView(accountAliasInput)
        content.addView(accountLabelInput)
        content.addView(saveAccountButton)
        content.addView(importHistoryButton)
        content.addView(clearAccountButton)
        content.addView(clearPersonMemoryButton)

        chooseAccountButton.setOnClickListener {
            showAccountChooser()
        }
        newAccountButton.setOnClickListener {
            startNewAccount()
        }
        reassignButton.setOnClickListener {
            showReassignAccountDialog()
        }
        saveAccountButton.setOnClickListener {
            saveAccount()
        }
        importHistoryButton.setOnClickListener {
            showHistoryImportDialog()
        }
        clearAccountButton.setOnClickListener {
            confirmClearAccount()
        }
        clearPersonMemoryButton.setOnClickListener {
            confirmClearPersonMemory()
        }
    }

    private fun addOverlaySection(content: LinearLayout) {
        addSectionTitle(content, "4. 微信 / QQ 悬浮助手")

        content.addView(TextView(this).apply {
            text = "复制当前账号的新聊天 → 点悬浮“噜” → LULU 读取该人物的长期记忆 + 当前账号最近聊天 + 本轮复制内容 → 返回 A/B/C。"
            textSize = 14f
            setPadding(0, 6, 0, 10)
        })
        val overlayButton = Button(this).apply {
            text = "开启微信 / QQ 悬浮助手"
        }

        statusView = TextView(this).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 20, 0, 0)
        }

        content.addView(overlayButton)
        content.addView(statusView)

        overlayButton.setOnClickListener {
            if (IdentityStore.activeIdentity(this) == null) {
                toast("请先创建人物并绑定至少一个账号")
                return@setOnClickListener
            }

            if (Settings.canDrawOverlays(this)) {
                val service = Intent(
                    this,
                    CoachOverlayService::class.java
                ).apply {
                    action =
                        CoachOverlayService.ACTION_START_TRIGGER
                }
                startForegroundService(service)
                statusView.text =
                    "悬浮助手已开启。点击上方人物 / 账号标签可轮换账号。"
            } else {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + packageName)
                    )
                )
            }
        }
    }

    private fun addSectionTitle(
        root: LinearLayout,
        text: String
    ) {
        root.addView(TextView(this).apply {
            this.text = text
            textSize = 18f
            setPadding(0, 28, 0, 10)
        })
    }

    private fun showPersonChooser() {
        val persons = IdentityStore.persons(this)
        if (persons.isEmpty()) {
            startNewPerson()
            toast("还没有人物，请先新建")
            return
        }

        AlertDialog.Builder(this)
            .setTitle("选择现实人物")
            .setItems(
                persons.map { it.displayName }.toTypedArray()
            ) { _, which ->
                val selected = persons[which]
                editingPersonId = selected.id
                loadPersonIntoEditor(selected)

                val account =
                    IdentityStore.accountsForPerson(
                        this,
                        selected.id
                    ).firstOrNull()
                if (account != null) {
                    IdentityStore.setActiveAccount(
                        this,
                        account.id
                    )
                    editingAccountId = account.id
                    loadAccountIntoEditor(account)
                } else {
                    editingAccountId = null
                    clearAccountEditor()
                }
                refreshActiveIdentity(
                    loadRemoteStatus = true
                )
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showAccountChooser() {
        val accounts = IdentityStore.accounts(this)
        if (accounts.isEmpty()) {
            toast("还没有账号，请先给人物添加账号")
            return
        }

        val labels = accounts.map { account ->
            val person = IdentityStore.person(
                this,
                account.personId
            )
            (person?.displayName ?: "未知人物") +
                "｜" +
                account.displayName
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("选择当前聊天账号")
            .setItems(labels) { _, which ->
                val selected = accounts[which]
                IdentityStore.setActiveAccount(
                    this,
                    selected.id
                )
                editingAccountId = selected.id
                editingPersonId = selected.personId
                IdentityStore.person(
                    this,
                    selected.personId
                )?.let {
                    loadPersonIntoEditor(it)
                }
                loadAccountIntoEditor(selected)
                refreshActiveIdentity(
                    loadRemoteStatus = true
                )
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun startNewPerson() {
        editingPersonId = null
        editingAccountId = null
        personNameInput.setText("")
        stageInput.setText("")
        goalInput.setText("")
        styleInput.setText("短句、自然、不油腻")
        clearAccountEditor()
        statusView.text =
            "正在新建现实人物；保存后可为其添加多个微信 / QQ 账号。"
    }

    private fun savePerson() {
        val name = personNameInput.text.toString().trim()
        if (name.isBlank()) {
            toast("请填写人物名称")
            return
        }

        val existing = editingPersonId?.let {
            IdentityStore.person(this, it)
        }

        val profile = existing?.copy(
            displayName = name,
            relationshipStage = stageInput.text
                .toString()
                .ifBlank { "未提供" },
            goal = goalInput.text
                .toString()
                .ifBlank { "自动判断" },
            userStyle = styleInput.text
                .toString()
                .ifBlank { "短句、自然、不油腻" }
        ) ?: IdentityStore.createPerson(
            displayName = name,
            relationshipStage =
                stageInput.text.toString(),
            goal = goalInput.text.toString(),
            userStyle = styleInput.text.toString()
        )

        IdentityStore.upsertPerson(this, profile)
        editingPersonId = profile.id
        loadPersonIntoEditor(profile)
        statusView.text =
            "已保存人物 " + profile.displayName +
                "。现在可以为他/她添加大号、小号或 QQ。"
        refreshActiveIdentity(
            loadRemoteStatus = false
        )
    }

    private fun startNewAccount() {
        val person = selectedPerson()
        if (person == null) {
            toast("请先新建或选择人物")
            return
        }

        editingPersonId = person.id
        editingAccountId = null
        clearAccountEditor()
        statusView.text =
            "正在给 " + person.displayName +
                " 添加新的微信 / QQ 账号。"
    }

    private fun saveAccount() {
        val person = selectedPerson()
        if (person == null) {
            toast("请先选择人物")
            return
        }

        val alias = accountAliasInput.text.toString().trim()
        if (alias.isBlank()) {
            toast("请填写账号备注 / 昵称")
            return
        }

        val platform =
            if (platformSpinner.selectedItemPosition == 1) {
                "qq"
            } else {
                "wechat"
            }

        val existing = editingAccountId?.let { id ->
            IdentityStore.accounts(this)
                .firstOrNull { it.id == id }
        }

        val account = existing?.copy(
            platform = platform,
            alias = alias,
            accountLabel =
                accountLabelInput.text.toString().trim()
        ) ?: IdentityStore.createAccount(
            personId = person.id,
            platform = platform,
            alias = alias,
            accountLabel =
                accountLabelInput.text.toString()
        )

        IdentityStore.upsertAccount(this, account)
        editingAccountId = account.id
        editingPersonId = account.personId
        loadAccountIntoEditor(account)
        refreshActiveIdentity(
            loadRemoteStatus = true
        )
        toast(
            "已绑定 " + account.displayName +
                " → " + person.displayName
        )
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
            toast("至少还需要另一个人物才能改绑")
            return
        }

        if (LuluPrefs.baseUrl(this).isBlank()
            || LuluPrefs.cookie(this).isBlank()) {
            toast("请先登录，确保服务器端记忆一起迁移")
            return
        }

        AlertDialog.Builder(this)
            .setTitle(
                "把 " + identity.account.displayName +
                    " 改绑到哪个人物？"
            )
            .setMessage(
                "只迁移这个账号来源的长期事实；账号自己的最近聊天 Thread 不变。"
            )
            .setItems(
                candidates.map { it.displayName }.toTypedArray()
            ) { _, which ->
                reassignAccount(
                    identity,
                    candidates[which]
                )
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun reassignAccount(
        identity: ActiveIdentity,
        newPerson: PersonProfile
    ) {
        statusView.text =
            "正在迁移 " + identity.account.displayName +
                " 的长期记忆归属…"

        Thread {
            runCatching {
                CoachApi.reassignAccount(
                    LuluPrefs.baseUrl(this),
                    LuluPrefs.cookie(this),
                    identity.account,
                    identity.person.id,
                    newPerson.id
                )
            }.onSuccess { result ->
                IdentityStore.reassignAccountLocal(
                    this,
                    identity.account.id,
                    newPerson.id
                )
                runOnUiThread {
                    editingPersonId = newPerson.id
                    editingAccountId =
                        identity.account.id
                    loadPersonIntoEditor(newPerson)
                    IdentityStore.activeAccount(this)
                        ?.let {
                            loadAccountIntoEditor(it)
                        }
                    statusView.text =
                        "改绑完成：迁移长期记忆 " +
                            result.movedMemoryFacts +
                            " 条。"
                    refreshActiveIdentity(
                        loadRemoteStatus = true
                    )
                }
            }.onFailure { error ->
                runOnUiThread {
                    statusView.text =
                        error.message ?: "改绑失败"
                }
            }
        }.start()
    }

    private fun loadPersonIntoEditor(
        person: PersonProfile
    ) {
        editingPersonId = person.id
        personNameInput.setText(person.displayName)
        stageInput.setText(person.relationshipStage)
        goalInput.setText(person.goal)
        styleInput.setText(person.userStyle)
    }

    private fun loadAccountIntoEditor(
        account: ContactAccount
    ) {
        editingAccountId = account.id
        platformSpinner.setSelection(
            if (account.platform == "qq") 1 else 0
        )
        accountAliasInput.setText(account.alias)
        accountLabelInput.setText(account.accountLabel)
    }

    private fun clearAccountEditor() {
        platformSpinner.setSelection(0)
        accountAliasInput.setText("")
        accountLabelInput.setText("")
    }

    private fun selectedPerson(): PersonProfile? {
        val byEditor = editingPersonId?.let {
            IdentityStore.person(this, it)
        }
        if (byEditor != null) return byEditor

        val active = IdentityStore.activeIdentity(this)
        if (active != null) return active.person

        return IdentityStore.persons(this).firstOrNull()
    }

    private fun refreshActiveIdentity(
        loadRemoteStatus: Boolean
    ) {
        val identity = IdentityStore.activeIdentity(this)
        if (identity == null) {
            val person = selectedPerson()
            if (person == null) {
                activeIdentityView.text =
                    "当前：还没有人物"
                threadInfoView.text =
                    "先建立人物，再为其绑定微信 / QQ 账号。"
            } else {
                activeIdentityView.text =
                    "当前人物：" + person.displayName
                threadInfoView.text =
                    "还没有可用账号，请添加账号。"
            }
            return
        }

        activeIdentityView.text =
            "当前：" + identity.displayName
        threadInfoView.text =
            "人物共享长期记忆 · 当前账号 Thread：" +
                identity.account.threadId.take(28) +
                "…"

        if (editingPersonId == null) {
            loadPersonIntoEditor(identity.person)
        }
        if (editingAccountId == null) {
            loadAccountIntoEditor(identity.account)
        }

        if (loadRemoteStatus) {
            loadThreadStatus(identity)
        }
    }

    private fun loadThreadStatus(
        identity: ActiveIdentity
    ) {
        val baseUrl = LuluPrefs.baseUrl(this)
        val cookie = LuluPrefs.cookie(this)
        if (baseUrl.isBlank() || cookie.isBlank()) return

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
                    val current =
                        IdentityStore.activeIdentity(this)
                    if (current?.account?.id ==
                        identity.account.id) {
                        threadInfoView.text =
                            "当前账号最近原文 " +
                                info.recentMessages +
                                "/50 条 · 人物长期记忆 " +
                                info.memoryFacts +
                                " 条"
                    }
                }
            }
        }.start()
    }

    private fun showHistoryImportDialog() {
        val identity = IdentityStore.activeIdentity(this)
        if (identity == null) {
            toast("请先选择人物和账号")
            return
        }

        val baseUrl = LuluPrefs.baseUrl(this)
        val cookie = LuluPrefs.cookie(this)
        if (baseUrl.isBlank() || cookie.isBlank()) {
            toast("请先登录噜噜")
            return
        }

        val input = EditText(this).apply {
            hint =
                "每行一条，例如：\n我：周末有空吗\n对方：周六可能可以"
            inputType =
                InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                    InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            minLines = 8
            maxLines = 16
            gravity = Gravity.TOP
            setPadding(32, 24, 32, 24)
        }
        val container = FrameLayout(this).apply {
            setPadding(24, 0, 24, 0)
            addView(
                input,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }

        AlertDialog.Builder(this)
            .setTitle(
                "导入 " + identity.displayName +
                    " 的历史聊天"
            )
            .setMessage(
                "长期事实会归到人物 " +
                    identity.person.displayName +
                    "；原始消息只保留在当前账号最近 50 条。"
            )
            .setView(container)
            .setPositiveButton("开始导入") { _, _ ->
                val text =
                    input.text.toString().trim()
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
        statusView.text =
            "正在提取 " + identity.person.displayName +
                " 的长期关系记忆…"

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
                    statusView.text =
                        "历史导入完成：新增长期记忆 " +
                            info.importedFacts +
                            " 条；当前账号最近原文 " +
                            info.recentMessages +
                            "/50 条。"
                    threadInfoView.text =
                        "当前账号最近原文 " +
                            info.recentMessages +
                            "/50 条 · 人物长期记忆 " +
                            info.memoryFacts +
                            " 条"
                }
            }.onFailure { error ->
                runOnUiThread {
                    statusView.text =
                        error.message ?: "历史导入失败"
                }
            }
        }.start()
    }

    private fun confirmClearAccount() {
        val identity = IdentityStore.activeIdentity(this)
            ?: return

        AlertDialog.Builder(this)
            .setTitle(
                "清空 " + identity.account.displayName +
                    " 的最近聊天？"
            )
            .setMessage(
                "只删除这个账号保留的最近原文，不删除 " +
                    identity.person.displayName +
                    " 的长期记忆。"
            )
            .setPositiveButton("清空") { _, _ ->
                clearAccount(identity)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun clearAccount(
        identity: ActiveIdentity
    ) {
        Thread {
            runCatching {
                CoachApi.clearAccount(
                    LuluPrefs.baseUrl(this),
                    LuluPrefs.cookie(this),
                    identity.person,
                    identity.account
                )
            }.onSuccess { info ->
                runOnUiThread {
                    threadInfoView.text =
                        "当前账号最近原文 0/50 条 · 人物长期记忆 " +
                            info.memoryFacts +
                            " 条"
                    statusView.text =
                        "已清空当前账号最近聊天。"
                }
            }.onFailure { error ->
                runOnUiThread {
                    statusView.text =
                        error.message ?: "清空失败"
                }
            }
        }.start()
    }

    private fun confirmClearPersonMemory() {
        val person = selectedPerson() ?: return
        val activeAccount = IdentityStore.activeAccount(this)

        AlertDialog.Builder(this)
            .setTitle(
                "清空 " + person.displayName +
                    " 的长期记忆？"
            )
            .setMessage(
                "会删除这个现实人物跨微信 / QQ 账号共享的长期关系事实；各账号最近原文仍保留。"
            )
            .setPositiveButton("清空") { _, _ ->
                clearPersonMemory(
                    person,
                    activeAccount?.takeIf {
                        it.personId == person.id
                    }
                )
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun clearPersonMemory(
        person: PersonProfile,
        activeAccount: ContactAccount?
    ) {
        Thread {
            runCatching {
                CoachApi.clearPersonMemory(
                    LuluPrefs.baseUrl(this),
                    LuluPrefs.cookie(this),
                    person,
                    activeAccount
                )
            }.onSuccess { info ->
                runOnUiThread {
                    threadInfoView.text =
                        "当前账号最近原文 " +
                            info.recentMessages +
                            "/50 条 · 人物长期记忆 0 条"
                    statusView.text =
                        "已清空 " +
                            person.displayName +
                            " 的长期记忆。"
                }
            }.onFailure { error ->
                runOnUiThread {
                    statusView.text =
                        error.message ?: "清空失败"
                }
            }
        }.start()
    }

    private fun toast(text: String) {
        Toast.makeText(
            this,
            text,
            Toast.LENGTH_SHORT
        ).show()
    }
}
