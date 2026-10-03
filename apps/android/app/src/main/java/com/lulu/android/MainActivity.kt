package com.lulu.android

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.widget.*
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "噜噜手机聊天助手"

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 56, 48, 48)
        }
        val titleView = TextView(this).apply {
            text = "噜噜 · 手机聊天回复助手"
            textSize = 24f
        }
        val note = TextView(this).apply {
            text = "在微信或 QQ 里选中/分享聊天文字给噜噜，生成 A/B/C 三条建议后会直接悬浮在当前聊天界面上方。点击某条只复制，不会自动发送。"
            textSize = 15f
            setPadding(0, 16, 0, 28)
        }
        val baseUrl = EditText(this).apply {
            hint = "后端地址，例如 http://192.168.1.10:8123"
            setText(LuluPrefs.baseUrl(this@MainActivity))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        val email = EditText(this).apply {
            hint = "噜噜账号邮箱"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }
        val password = EditText(this).apply {
            hint = "密码"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val loginButton = Button(this).apply { text = "保存地址并登录" }
        val overlayButton = Button(this).apply { text = "开启微信 / QQ 悬浮助手" }
        val status = TextView(this).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 20, 0, 0)
        }

        root.addView(titleView)
        root.addView(note)
        root.addView(baseUrl)
        root.addView(email)
        root.addView(password)
        root.addView(loginButton)
        root.addView(overlayButton)
        root.addView(status)
        setContentView(root)
        loginButton.setOnClickListener {
            val url = baseUrl.text.toString().trim().trimEnd('/')
            if (url.isBlank() || email.text.isBlank() || password.text.isBlank()) {
                Toast.makeText(this, "请填写后端地址、邮箱和密码", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            LuluPrefs.setBaseUrl(this, url)
            loginButton.isEnabled = false
            status.text = "正在登录…"
            Thread {
                runCatching {
                    CoachApi.login(url, email.text.toString(), password.text.toString())
                }.onSuccess { cookie ->
                    LuluPrefs.setCookie(this, cookie)
                    runOnUiThread {
                        loginButton.isEnabled = true
                        status.text = "登录成功。现在可从微信或 QQ 分享/选中文字给噜噜。"
                    }
                }.onFailure { error ->
                    runOnUiThread {
                        loginButton.isEnabled = true
                        status.text = error.message ?: "登录失败"
                    }
                }
            }.start()
        }

        overlayButton.setOnClickListener {
            if (Settings.canDrawOverlays(this)) {
                val service = Intent(this, CoachOverlayService::class.java).apply {
                    action = CoachOverlayService.ACTION_START_TRIGGER
                }
                startForegroundService(service)
                status.text = "悬浮助手已开启：在微信或 QQ 复制聊天内容后，点“噜”即可生成 A/B/C。"
            } else {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            }
        }
    }
}
