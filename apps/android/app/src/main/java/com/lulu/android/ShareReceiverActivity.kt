package com.lulu.android

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast

class ShareReceiverActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedText = when (intent.action) {
            Intent.ACTION_PROCESS_TEXT ->
                intent.getCharSequenceExtra(
                    Intent.EXTRA_PROCESS_TEXT
                )?.toString()
            Intent.ACTION_SEND ->
                intent.getCharSequenceExtra(
                    Intent.EXTRA_TEXT
                )?.toString()
            else -> null
        }?.trim().orEmpty()

        if (sharedText.isBlank()) {
            Toast.makeText(
                this,
                "没有收到可分析的聊天文字",
                Toast.LENGTH_SHORT
            ).show()
            finish()
            return
        }

        val identity = IdentityStore.activeIdentity(this)
        if (identity == null) {
            Toast.makeText(
                this,
                "请先在噜噜里创建人物并绑定至少一个账号",
                Toast.LENGTH_LONG
            ).show()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        val baseUrl = LuluPrefs.baseUrl(this)
        val cookie = LuluPrefs.cookie(this)
        if (baseUrl.isBlank() || cookie.isBlank()) {
            Toast.makeText(
                this,
                "请先打开噜噜完成登录",
                Toast.LENGTH_LONG
            ).show()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(
                this,
                "请先授予噜噜悬浮窗权限",
                Toast.LENGTH_LONG
            ).show()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        Toast.makeText(
            this,
            "正在结合 " + identity.displayName + " 的记忆生成 A/B/C…",
            Toast.LENGTH_SHORT
        ).show()

        Thread {
            runCatching {
                CoachApi.suggest(
                    baseUrl,
                    cookie,
                    identity.person,
                    identity.account,
                    sharedText
                )
            }.onSuccess { choices ->
                runOnUiThread {
                    val service = Intent(
                        this,
                        CoachOverlayService::class.java
                    ).apply {
                        action = CoachOverlayService.ACTION_SHOW_CHOICES
                        putExtra(
                            CoachOverlayService.EXTRA_A,
                            choices.aggressive
                        )
                        putExtra(
                            CoachOverlayService.EXTRA_B,
                            choices.normal
                        )
                        putExtra(
                            CoachOverlayService.EXTRA_C,
                            choices.conservative
                        )
                        putExtra(
                            CoachOverlayService.EXTRA_CONTACT,
                            identity.displayName
                        )
                    }
                    startForegroundService(service)
                    finish()
                }
            }.onFailure { error ->
                runOnUiThread {
                    Toast.makeText(
                        this,
                        error.message ?: "生成回复失败",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                }
            }
        }.start()
    }
}
