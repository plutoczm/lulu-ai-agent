package com.lulu.android

import android.app.*
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
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
        const val EXTRA_CONTACT = "contact_name"
        private const val CHANNEL_ID = "lulu_reply_overlay"
        private const val NOTIFICATION_ID = 3101

        @Volatile
        private var currentOverlayBounds: Rect? = null

        fun visibleOverlayBounds(): Rect? =
            currentOverlayBounds?.let { Rect(it) }
    }

    private lateinit var windowManager: WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var overlayView: View? = null
    @Volatile
    private var requestInFlight = false

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
        val contact = intent?.getStringExtra(EXTRA_CONTACT)
            ?: IdentityStore.activeIdentity(this)?.displayName
            ?: "未选择人物 / 账号"

        if (a.isBlank() || b.isBlank() || c.isBlank()) {
            showTrigger()
            return START_STICKY
        }

        showOverlay(contact, a, b, c)
        return START_STICKY
    }

    private fun showTrigger() {
        removeOverlay()

        val active = IdentityStore.activeIdentity(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val contactLabel = TextView(this).apply {
            text = active?.displayName ?: "先建人物 / 账号"
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(82, 64, 48))
            setPadding(dp(8), dp(5), dp(8), dp(5))
            background = rounded(Color.rgb(255, 248, 238), 12f)
            setOnClickListener {
                val next = IdentityStore.cycleActiveAccount(this@CoachOverlayService)
                if (next == null) {
                    startActivity(
                        Intent(this@CoachOverlayService, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } else {
                    Toast.makeText(
                        this@CoachOverlayService,
                        "已切换到 ${next.displayName}",
                        Toast.LENGTH_SHORT
                    ).show()
                    showTrigger()
                }
            }
        }

        val trigger = TextView(this).apply {
            text = "噜"
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = rounded(Color.rgb(213, 154, 87), 24f)
            elevation = dp(10).toFloat()
            setOnClickListener {
                analyzeCurrentChat()
            }
            layoutParams = LinearLayout.LayoutParams(dp(52), dp(52)).apply {
                topMargin = dp(5)
            }
        }

        root.addView(contactLabel)
        root.addView(trigger)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.END or Gravity.BOTTOM
            x = dp(18)
            y = dp(150)
        }

        overlayView = root
        windowManager.addView(root, params)
        trackOverlayBounds(root)
    }

    private fun analyzeCurrentChat() {
        val tapStarted = SystemClock.elapsedRealtimeNanos()
        if (requestInFlight) {
            Toast.makeText(
                this,
                "噜噜正在生成上一轮回复",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val active = IdentityStore.activeIdentity(this)
        if (active == null) {
            Toast.makeText(
                this,
                "请先创建人物并绑定账号",
                Toast.LENGTH_SHORT
            ).show()
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
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
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }

        if (!LuluAccessibilityService.isEnabled(this)) {
            Toast.makeText(
                this,
                "首次使用请开启“噜噜聊天读取”无障碍权限",
                Toast.LENGTH_LONG
            ).show()
            startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }

        val bridge = LuluAccessibilityService.current
        if (bridge == null) {
            Toast.makeText(
                this,
                "无障碍服务正在连接，请回到聊天页后再点一次“噜”",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        requestInFlight = true
        removeOverlay()

        val captureStarted = SystemClock.elapsedRealtimeNanos()
        mainHandler.postDelayed({
            bridge.captureVisibleConversationAsync(
                autoTranscribeVoices =
                    LuluPrefs.voiceUiFallbackEnabled(this)
            ) { captureResult ->
                mainHandler.post {
                    captureResult.onSuccess { capture ->
                        val captureMs = elapsedMs(captureStarted)

                        val routeStarted =
                            SystemClock.elapsedRealtimeNanos()
                        val identity = resolveIdentityForPlatform(
                            active,
                            capture.platform
                        )
                        if (identity == null) {
                            requestInFlight = false
                            showTrigger()
                            return@onSuccess
                        }
                        val routeMs = elapsedMs(routeStarted)

                        capture.chatTitle
                            ?.takeIf { it.isNotBlank() }
                            ?.let { title ->
                                IdentityStore.bindChatTitle(
                                    this,
                                    identity.account.id,
                                    title
                                )
                            }

                        ChatSyncCoordinator.syncVisibleCaptureForIdentity(
                            this,
                            identity,
                            capture,
                            source = "coach_screen"
                        )

                        Toast.makeText(
                            this,
                            "已读取当前屏幕 ${capture.messages.size} 条可见消息，正在生成 A/B/C…",
                            Toast.LENGTH_SHORT
                        ).show()

                        Thread {
                            val httpStarted =
                                SystemClock.elapsedRealtimeNanos()
                            runCatching {
                                CoachApi.suggest(
                                    baseUrl,
                                    cookie,
                                    identity.person,
                                    identity.account,
                                    capture.asCoachText()
                                )
                            }.onSuccess { choices ->
                                val httpMs = elapsedMs(httpStarted)
                                mainHandler.post {
                                    requestInFlight = false
                                    val overlayStarted =
                                        SystemClock.elapsedRealtimeNanos()
                                    showOverlay(
                                        identity.displayName,
                                        choices.aggressive,
                                        choices.normal,
                                        choices.conservative
                                    )
                                    val overlayMs =
                                        elapsedMs(overlayStarted)
                                    Log.i(
                                        "LuluPerf",
                                        (
                                            "tap_to_overlay " +
                                                "platform=${capture.platform} " +
                                                "source=${capture.source} " +
                                                "capture=${captureMs}ms " +
                                                "route=${routeMs}ms " +
                                                "http=${httpMs}ms " +
                                                "overlay=${overlayMs}ms " +
                                                "total=${elapsedMs(tapStarted)}ms " +
                                                "messages=${capture.messages.size} " +
                                                "voiceMarkers=${capture.voiceMarkers}"
                                        )
                                    )
                                }
                            }.onFailure { error ->
                                val httpMs = elapsedMs(httpStarted)
                                Log.w(
                                    "LuluPerf",
                                    "tap_failed after=${elapsedMs(tapStarted)}ms http=${httpMs}ms",
                                    error
                                )
                                mainHandler.post {
                                    requestInFlight = false
                                    showTrigger()
                                    Toast.makeText(
                                        this,
                                        error.message ?: "生成回复失败",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }.start()
                    }.onFailure { error ->
                        requestInFlight = false
                        showTrigger()
                        Log.w(
                            "LuluPerf",
                            "capture_failed after=${elapsedMs(tapStarted)}ms",
                            error
                        )
                        Toast.makeText(
                            this,
                            error.message ?: "读取当前聊天失败",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }, 16L)
    }

    private fun resolveIdentityForPlatform(
        active: ActiveIdentity,
        platform: String
    ): ActiveIdentity? {
        if (active.account.platform == platform) {
            return active
        }

        val matches = IdentityStore.accountsForPerson(
            this,
            active.person.id
        ).filter { it.platform == platform }

        if (matches.size == 1) {
            val account = matches.first()
            IdentityStore.setActiveAccount(this, account.id)
            Toast.makeText(
                this,
                "已自动切换到 ${account.displayName}",
                Toast.LENGTH_SHORT
            ).show()
            return ActiveIdentity(active.person, account)
        }

        val platformLabel = if (platform == "qq") "QQ" else "微信"
        if (matches.isEmpty()) {
            Toast.makeText(
                this,
                "${active.person.displayName} 还没有绑定 $platformLabel 账号",
                Toast.LENGTH_LONG
            ).show()
        } else {
            Toast.makeText(
                this,
                "${active.person.displayName} 有多个 $platformLabel 账号，请点悬浮标签先选对大小号",
                Toast.LENGTH_LONG
            ).show()
        }
        return null
    }

    private fun showOverlay(
        contactName: String,
        a: String,
        b: String,
        c: String
    ) {
        removeOverlay()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = roundedStroke(
                Color.rgb(255, 253, 249),
                22f,
                Color.rgb(239, 224, 209)
            )
            elevation = dp(14).toFloat()
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val brand = TextView(this).apply {
            text = "噜"
            textSize = 17f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = rounded(Color.rgb(214, 147, 72), 15f)
        }
        header.addView(
            brand,
            LinearLayout.LayoutParams(dp(38), dp(38))
        )

        val titleBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), 0, 0, 0)
        }
        titleBlock.addView(TextView(this).apply {
            text = "噜噜 · 快回复"
            textSize = 15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(Color.rgb(66, 51, 39))
        })
        titleBlock.addView(TextView(this).apply {
            text = contactName
            textSize = 11f
            setTextColor(Color.rgb(137, 122, 109))
            setPadding(0, dp(2), 0, 0)
        })
        header.addView(
            titleBlock,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        val close = TextView(this).apply {
            text = "×"
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(100, 87, 76))
            background = rounded(Color.rgb(246, 240, 234), 13f)
            setOnClickListener { showTrigger() }
        }
        header.addView(
            close,
            LinearLayout.LayoutParams(dp(36), dp(36))
        )
        root.addView(header)

        root.addView(
            replyCard(
                "A｜激进",
                "更主动",
                a,
                Color.rgb(255, 247, 243),
                Color.rgb(220, 143, 111),
                false
            )
        )
        root.addView(
            replyCard(
                "B｜正常",
                "推荐",
                b,
                Color.rgb(255, 249, 239),
                Color.rgb(214, 154, 82),
                true
            )
        )
        root.addView(
            replyCard(
                "C｜保守",
                "更克制",
                c,
                Color.rgb(244, 250, 248),
                Color.rgb(99, 150, 126),
                false
            )
        )

        val note = TextView(this).apply {
            text = "结合人物长期记忆 + 当前可见聊天 · 点击优先填入输入框，不自动发送"
            textSize = 10.5f
            setTextColor(Color.rgb(145, 137, 128))
            gravity = Gravity.CENTER
            setPadding(0, dp(9), 0, 0)
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
            y = dp(46)
        }

        overlayView = root
        windowManager.addView(root, params)
        trackOverlayBounds(root)
    }

    private fun replyCard(
        label: String,
        hint: String,
        text: String,
        fill: Int,
        accent: Int,
        recommended: Boolean
    ): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(13))
            background = roundedStroke(
                fill,
                16f,
                if (recommended) accent else Color.rgb(239, 229, 219)
            )
            isClickable = true
            isFocusable = true
            elevation = if (recommended) dp(3).toFloat() else 0f
            setOnClickListener {
                copyReply(text, label.substringBefore("｜"))
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }

            val meta = LinearLayout(this@CoachOverlayService).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            meta.addView(TextView(this@CoachOverlayService).apply {
                this.text = label
                textSize = 13f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(accent)
            }, LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            ))
            meta.addView(TextView(this@CoachOverlayService).apply {
                this.text = hint
                textSize = 10f
                gravity = Gravity.CENTER
                setTextColor(accent)
                setPadding(dp(8), dp(4), dp(8), dp(4))
                background = rounded(
                    Color.argb(28, Color.red(accent), Color.green(accent), Color.blue(accent)),
                    10f
                )
            })
            addView(meta)

            addView(TextView(this@CoachOverlayService).apply {
                this.text = text
                textSize = if (recommended) 15f else 14f
                setTextColor(Color.rgb(62, 50, 40))
                setLineSpacing(0f, 1.12f)
                setPadding(0, dp(8), 0, 0)
            })
        }

    private fun copyReply(text: String, key: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("噜噜回复$key", text))

        val filled = LuluAccessibilityService.current
            ?.fillCurrentChatInput(text) == true

        Toast.makeText(
            this,
            if (filled) {
                "$key 已填入当前输入框，请确认后手动发送"
            } else {
                "$key 已复制，可在当前聊天输入框粘贴"
            },
            Toast.LENGTH_SHORT
        ).show()
        showTrigger()
    }

    private fun trackOverlayBounds(view: View) {
        view.post {
            if (overlayView !== view) {
                return@post
            }
            val location = IntArray(2)
            view.getLocationOnScreen(location)
            currentOverlayBounds = Rect(
                location[0],
                location[1],
                location[0] + view.width,
                location[1] + view.height
            )
        }
    }

    private fun removeOverlay() {
        currentOverlayBounds = null
        overlayView?.let { runCatching { windowManager.removeView(it) } }
        overlayView = null
    }

    private fun rounded(color: Int, radiusDp: Float) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp.toInt()).toFloat()
        }

    private fun roundedStroke(
        color: Int,
        radiusDp: Float,
        strokeColor: Int
    ) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp.toInt()).toFloat()
        setStroke(dp(1), strokeColor)
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
        val active = IdentityStore.activeIdentity(this)?.displayName
            ?: "未选择人物 / 账号"
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("噜噜回复建议")
            .setContentText("当前：$active · 点击悬浮“噜”生成 A/B/C")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(openApp)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        removeOverlay()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun elapsedMs(started: Long): Long =
        (SystemClock.elapsedRealtimeNanos() - started) / 1_000_000

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
