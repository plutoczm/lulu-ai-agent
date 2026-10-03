package com.lulu.android

import android.app.*
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*

class CoachOverlayService : Service() {

    companion object {
        const val ACTION_START_TRIGGER = "com.lulu.android.START_TRIGGER"
        const val ACTION_SHOW_CHOICES = "com.lulu.android.SHOW_CHOICES"
        const val EXTRA_A = "reply_a"
        const val EXTRA_B = "reply_b"
        const val EXTRA_C = "reply_c"
        private const val CHANNEL_ID = "lulu_reply_overlay"
        private const val NOTIFICATION_ID = 3101
    }

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        ensureNotificationChannel()
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())

        if (intent?.action == ACTION_START_TRIGGER) {
            showTrigger()
            return START_STICKY
        }

        val a = intent?.getStringExtra(EXTRA_A).orEmpty()
        val b = intent?.getStringExtra(EXTRA_B).orEmpty()
        val c = intent?.getStringExtra(EXTRA_C).orEmpty()
        if (a.isBlank() || b.isBlank() || c.isBlank()) {
            showTrigger()
            return START_STICKY
        }

        showOverlay(a, b, c)
        return START_STICKY
    }

    private fun showTrigger() {
        overlayView?.let { runCatching { windowManager.removeView(it) } }

        val trigger = TextView(this).apply {
            text = "噜"
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = rounded(Color.rgb(213, 154, 87), 24f)
            elevation = dp(10).toFloat()
            setOnClickListener {
                val capture = Intent(this@CoachOverlayService, ClipboardCaptureActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(capture)
            }
        }

        val params = WindowManager.LayoutParams(
            dp(52),
            dp(52),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.END or Gravity.BOTTOM
            x = dp(18)
            y = dp(150)
        }

        overlayView = trigger
        windowManager.addView(trigger, params)
    }

    private fun showOverlay(a: String, b: String, c: String) {
        overlayView?.let { runCatching { windowManager.removeView(it) } }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(14))
            background = rounded(Color.rgb(255, 252, 247), 18f)
            elevation = dp(12).toFloat()
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(this).apply {
            text = "噜噜回复建议 · 点击即复制"
            textSize = 15f
            setTextColor(Color.rgb(82, 64, 48))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val close = Button(this).apply {
            text = "×"
            textSize = 18f
            minWidth = 0
            minimumWidth = 0
            setOnClickListener { showTrigger() }
        }
        header.addView(title)
        header.addView(close)
        root.addView(header)

        root.addView(replyCard("A｜激进", a, Color.rgb(255, 246, 242)))
        root.addView(replyCard("B｜正常", b, Color.rgb(255, 249, 239)))
        root.addView(replyCard("C｜保守", c, Color.rgb(244, 250, 249)))

        val note = TextView(this).apply {
            text = "只复制到剪贴板，不会替你发送。"
            textSize = 11f
            setTextColor(Color.rgb(130, 130, 130))
            setPadding(0, dp(6), 0, 0)
        }
        root.addView(note)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
            x = 0
            y = dp(72)
        }

        overlayView = root
        windowManager.addView(root, params)
    }

    private fun replyCard(label: String, text: String, color: Int): TextView =
        TextView(this).apply {
            this.text = "$label\n$text"
            textSize = 14f
            setTextColor(Color.rgb(65, 52, 42))
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(color, 14f)
            isClickable = true
            isFocusable = true
            setOnClickListener { copyReply(text, label.substringBefore("｜")) }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
        }
    private fun copyReply(text: String, key: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("噜噜回复$key", text))
        Toast.makeText(this, "$key 已复制，可回到输入框粘贴", Toast.LENGTH_SHORT).show()
        showTrigger()
    }

    private fun rounded(color: Int, radiusDp: Float) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp.toInt()).toFloat()
        }

    private fun ensureNotificationChannel() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "噜噜回复浮层",
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("噜噜回复建议")
            .setContentText("A/B/C 三条建议正在当前聊天界面上方显示")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(openApp)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        overlayView?.let { runCatching { windowManager.removeView(it) } }
        overlayView = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
