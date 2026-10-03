package com.lulu.android

import android.app.Activity
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast

class ClipboardCaptureActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请先授予噜噜悬浮窗权限", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val text = clipboard.primaryClip
            ?.getItemAt(0)
            ?.coerceToText(this)
            ?.toString()
            ?.trim()
            .orEmpty()
        if (text.isBlank()) {
            Toast.makeText(this, "请先在微信或 QQ 里复制需要分析的聊天内容", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val baseUrl = LuluPrefs.baseUrl(this)
        val cookie = LuluPrefs.cookie(this)
        if (baseUrl.isBlank() || cookie.isBlank()) {
            Toast.makeText(this, "请先打开噜噜完成登录", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        Toast.makeText(this, "噜噜正在生成 A/B/C 三条回复…", Toast.LENGTH_SHORT).show()
        Thread {
            runCatching {
                CoachApi.suggest(baseUrl, cookie, text)
            }.onSuccess { choices ->
                runOnUiThread {
                    val service = Intent(this, CoachOverlayService::class.java).apply {
                        action = CoachOverlayService.ACTION_SHOW_CHOICES
                        putExtra(CoachOverlayService.EXTRA_A, choices.aggressive)
                        putExtra(CoachOverlayService.EXTRA_B, choices.normal)
                        putExtra(CoachOverlayService.EXTRA_C, choices.conservative)
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
