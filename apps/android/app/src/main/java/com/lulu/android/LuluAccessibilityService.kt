package com.lulu.android

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions

data class VisibleChatCapture(
    val platform: String,
    val packageName: String,
    val messages: List<VisibleChatMessage>,
    val source: String = "accessibility",
    val voiceMarkers: Int = 0,
    val chatTitle: String? = null
) {
    fun asCoachText(): String =
        messages.joinToString("\n") {
            it.sender + "：" + it.text
        }
}

data class VisibleChatMessage(
    val sender: String,
    val text: String,
    val top: Int,
    val centerX: Int
)

class LuluAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var current: LuluAccessibilityService? = null
            private set

        private const val WECHAT_PACKAGE = "com.tencent.mm"
        private const val QQ_PACKAGE = "com.tencent.mobileqq"

        fun isEnabled(context: Context): Boolean {
            val manager = context.getSystemService(
                Context.ACCESSIBILITY_SERVICE
            ) as AccessibilityManager
            return manager
                .getEnabledAccessibilityServiceList(
                    AccessibilityServiceInfo.FEEDBACK_ALL_MASK
                )
                .any { info ->
                    val service = info.resolveInfo.serviceInfo
                    service.packageName == context.packageName &&
                        service.name == LuluAccessibilityService::class.java.name
                }
        }
    }

    private val textRecognizer: TextRecognizer by lazy {
        TextRecognition.getClient(
            ChineseTextRecognizerOptions.Builder().build()
        )
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastPassiveSyncAt = 0L

    private val passiveSyncRunnable: Runnable =
        object : Runnable {
            override fun run() {
                val now = SystemClock.elapsedRealtime()
                val remaining =
                    4_000L - (now - lastPassiveSyncAt)
                if (remaining > 0L) {
                    mainHandler.postDelayed(this, remaining)
                    return
                }
                lastPassiveSyncAt = now
                captureVisibleConversationAsync(
                    autoTranscribeVoices = false
                ) { result ->
                    result.onSuccess { capture ->
                        ChatSyncCoordinator.syncVisibleCapture(
                            this@LuluAccessibilityService,
                            capture,
                            source = "passive_screen"
                        )
                    }
                }
            }
        }

    private data class OcrLine(
        val text: String,
        val bounds: Rect
    )

    private data class VoiceAffordance(
        val bubbleBounds: Rect,
        val seconds: Int,
        val transcriptVisible: Boolean
    )

    private data class OcrParseResult(
        val messages: List<VisibleChatMessage>,
        val voiceMarkers: Int,
        val transcribeMenuButtons: List<Rect>,
        val untranscribedVoiceBubbles: List<Rect>,
        val chatTitle: String?
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        current = this
        ChatSyncCoordinator.flushPending(this)
        VoiceIngestionCoordinator.flushPending(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val currentEvent = event ?: return
        val packageName = currentEvent.packageName?.toString().orEmpty()
        if (packageName != WECHAT_PACKAGE &&
            packageName != QQ_PACKAGE
        ) {
            return
        }

        if (currentEvent.eventType ==
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            isComposerOnlyEvent(currentEvent)
        ) {
            return
        }

        when (currentEvent.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                // Debounce UI churn. The composer region is ignored so typing
                // drafts does not trigger OCR; committed chat bubbles do.
                mainHandler.removeCallbacks(passiveSyncRunnable)
                mainHandler.postDelayed(passiveSyncRunnable, 900L)
            }
        }
    }

    private fun isComposerOnlyEvent(
        event: AccessibilityEvent
    ): Boolean {
        val source = event.source ?: return false
        val bounds = Rect()
        source.getBoundsInScreen(bounds)
        val height = resources.displayMetrics.heightPixels
        return bounds.height() > 0 &&
            bounds.top >= (height * 0.78).toInt()
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (current === this) {
            current = null
        }
        runCatching { textRecognizer.close() }
        super.onDestroy()
    }

    fun captureVisibleConversationAsync(
        autoTranscribeVoices: Boolean = true,
        callback: (Result<VisibleChatCapture>) -> Unit
    ) {
        // Explicit coaching always uses OCR so visible voice-transcription
        // controls and the chat title are available deterministically.
        if (autoTranscribeVoices &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        ) {
            captureVisibleConversationFromScreenshot(
                callback,
                autoTranscribeVoices = true
            )
            return
        }

        val nodeAttempt = runCatching {
            captureVisibleConversationFromNodes()
        }
        if (nodeAttempt.isSuccess) {
            callback(nodeAttempt)
            return
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            callback(nodeAttempt)
            return
        }

        captureVisibleConversationFromScreenshot(
            callback,
            autoTranscribeVoices = false
        )
    }

    fun fillCurrentChatInput(text: String): Boolean {
        if (text.isBlank()) return false
        val root = rootInActiveWindow ?: return false
        val packageName = root.packageName?.toString().orEmpty()
        if (packageName != WECHAT_PACKAGE &&
            packageName != QQ_PACKAGE
        ) {
            return false
        }

        val candidates = mutableListOf<AccessibilityNodeInfo>()
        collectEditableNodes(root, candidates)
        val target = candidates.maxByOrNull { node ->
            val rect = Rect()
            node.getBoundsInScreen(rect)
            rect.bottom * 10_000L + rect.width()
        } ?: return false

        val args = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                text
            )
        }
        target.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        return target.performAction(
            AccessibilityNodeInfo.ACTION_SET_TEXT,
            args
        )
    }

    private fun captureVisibleConversationFromNodes(): VisibleChatCapture {
        val totalStarted = SystemClock.elapsedRealtimeNanos()
        val rootStarted = SystemClock.elapsedRealtimeNanos()
        val root = rootInActiveWindow
            ?: throw IllegalStateException(
                "没有拿到当前聊天界面，请回到微信或 QQ 聊天页后再点“噜”"
            )
        val rootMs = elapsedMs(rootStarted)

        val packageName = root.packageName?.toString().orEmpty()
        val platform = platformFor(packageName)

        val screen = Rect()
        root.getBoundsInScreen(screen)
        val width = if (screen.width() > 0) {
            screen.width()
        } else {
            resources.displayMetrics.widthPixels
        }
        val height = if (screen.height() > 0) {
            screen.height()
        } else {
            resources.displayMetrics.heightPixels
        }

        val raw = mutableListOf<VisibleChatMessage>()
        val traversalStarted = SystemClock.elapsedRealtimeNanos()
        collectTextNodes(root, raw, width, height)
        val traversalMs = elapsedMs(traversalStarted)

        val postStarted = SystemClock.elapsedRealtimeNanos()
        val messages = normalizeMessages(raw)
        val postMs = elapsedMs(postStarted)

        Log.i(
            "LuluPerf",
            "accessibility_capture platform=$platform " +
                "root=${rootMs}ms traversal=${traversalMs}ms " +
                "post=${postMs}ms total=${elapsedMs(totalStarted)}ms " +
                "raw=${raw.size} messages=${messages.size}"
        )

        if (messages.isEmpty()) {
            throw IllegalStateException(
                "当前微信/QQ没有暴露可读取文字节点，切换到本地OCR"
            )
        }

        return VisibleChatCapture(
            platform = platform,
            packageName = packageName,
            messages = messages,
            source = "accessibility",
            chatTitle = detectChatTitleFromNodes(
                root,
                width,
                height,
                platform
            )
        )
    }

    private fun captureVisibleConversationFromScreenshot(
        callback: (Result<VisibleChatCapture>) -> Unit,
        autoTranscribeVoices: Boolean,
        transcribeAttempts: Int = 0
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            callback(
                Result.failure(
                    IllegalStateException("当前系统版本不支持截图OCR")
                )
            )
            return
        }

        val totalStarted = SystemClock.elapsedRealtimeNanos()
        val packageName =
            rootInActiveWindow?.packageName?.toString().orEmpty()

        val platform = runCatching {
            platformFor(packageName)
        }.getOrElse { error ->
            callback(Result.failure(error))
            return
        }

        val screenshotStarted = SystemClock.elapsedRealtimeNanos()
        takeScreenshot(
            Display.DEFAULT_DISPLAY,
            mainExecutor,
            object : TakeScreenshotCallback {
                override fun onSuccess(
                    screenshot: ScreenshotResult
                ) {
                    val screenshotMs = elapsedMs(screenshotStarted)
                    val buffer = screenshot.hardwareBuffer
                    val wrapped = Bitmap.wrapHardwareBuffer(
                        buffer,
                        screenshot.colorSpace
                    )
                    val bitmap = wrapped?.copy(
                        Bitmap.Config.ARGB_8888,
                        false
                    )
                    buffer.close()

                    if (bitmap == null) {
                        callback(
                            Result.failure(
                                IllegalStateException("无法读取当前屏幕截图")
                            )
                        )
                        return
                    }

                    val ocrStarted = SystemClock.elapsedRealtimeNanos()
                    val image = InputImage.fromBitmap(bitmap, 0)
                    textRecognizer.process(image)
                        .addOnSuccessListener { visionText ->
                            val ocrMs = elapsedMs(ocrStarted)
                            val postStarted =
                                SystemClock.elapsedRealtimeNanos()
                            val parsed = parseOcrMessages(
                                visionText,
                                bitmap.width,
                                bitmap.height,
                                platform
                            )
                            val postMs = elapsedMs(postStarted)
                            bitmap.recycle()

                            Log.i(
                                "LuluPerf",
                                "ocr_capture platform=$platform " +
                                    "screenshot=${screenshotMs}ms " +
                                    "ocr=${ocrMs}ms post=${postMs}ms " +
                                    "total=${elapsedMs(totalStarted)}ms " +
                                    "messages=${parsed.messages.size} " +
                                    "voiceMarkers=${parsed.voiceMarkers}"
                            )

                            if (autoTranscribeVoices &&
                                transcribeAttempts < 8
                            ) {
                                val menuButton =
                                    parsed.transcribeMenuButtons.firstOrNull()
                                val voiceBubble =
                                    parsed.untranscribedVoiceBubbles.firstOrNull()

                                if (menuButton != null) {
                                    Log.i(
                                        "LuluPerf",
                                        "voice_ui_fallback action=tap_transcribe " +
                                            "attempt=${transcribeAttempts + 1}"
                                    )
                                    tapBounds(menuButton) { tapped ->
                                        if (!tapped) {
                                            callback(
                                                Result.success(
                                                    VisibleChatCapture(
                                                        platform = platform,
                                                        packageName = packageName,
                                                        messages = parsed.messages,
                                                        source = "ocr",
                                                        voiceMarkers =
                                                            parsed.voiceMarkers,
                                                        chatTitle =
                                                            parsed.chatTitle
                                                    )
                                                )
                                            )
                                        } else {
                                            mainHandler.postDelayed(
                                                {
                                                    captureVisibleConversationFromScreenshot(
                                                        callback,
                                                        autoTranscribeVoices = true,
                                                        transcribeAttempts =
                                                            transcribeAttempts + 1
                                                    )
                                                },
                                                900L
                                            )
                                        }
                                    }
                                    return@addOnSuccessListener
                                }

                                if (voiceBubble != null) {
                                    Log.i(
                                        "LuluPerf",
                                        "voice_ui_fallback action=long_press " +
                                            "attempt=${transcribeAttempts + 1}"
                                    )
                                    longPressBounds(voiceBubble) { pressed ->
                                        if (!pressed) {
                                            callback(
                                                Result.success(
                                                    VisibleChatCapture(
                                                        platform = platform,
                                                        packageName = packageName,
                                                        messages = parsed.messages,
                                                        source = "ocr",
                                                        voiceMarkers =
                                                            parsed.voiceMarkers,
                                                        chatTitle =
                                                            parsed.chatTitle
                                                    )
                                                )
                                            )
                                        } else {
                                            mainHandler.postDelayed(
                                                {
                                                    captureVisibleConversationFromScreenshot(
                                                        callback,
                                                        autoTranscribeVoices = true,
                                                        transcribeAttempts =
                                                            transcribeAttempts + 1
                                                    )
                                                },
                                                600L
                                            )
                                        }
                                    }
                                    return@addOnSuccessListener
                                }
                            }

                            if (parsed.messages.isEmpty()) {
                                callback(
                                    Result.failure(
                                        IllegalStateException(
                                            "当前屏幕没有识别到可用聊天文字"
                                        )
                                    )
                                )
                            } else {
                                callback(
                                    Result.success(
                                        VisibleChatCapture(
                                            platform = platform,
                                            packageName = packageName,
                                            messages = parsed.messages,
                                            source = "ocr",
                                            voiceMarkers = parsed.voiceMarkers,
                                            chatTitle = parsed.chatTitle
                                        )
                                    )
                                )
                            }
                        }
                        .addOnFailureListener { error ->
                            bitmap.recycle()
                            callback(Result.failure(error))
                        }
                }

                override fun onFailure(errorCode: Int) {
                    callback(
                        Result.failure(
                            IllegalStateException(
                                "系统截图失败，错误码 $errorCode"
                            )
                        )
                    )
                }
            }
        )
    }

    private fun parseOcrMessages(
        visionText: Text,
        screenWidth: Int,
        screenHeight: Int,
        platform: String
    ): OcrParseResult {
        data class VoiceMatch(
            val anchor: OcrLine,
            val seconds: Int?,
            val transcriptVisible: Boolean,
            val bubbleBounds: Rect,
            val fromTranscribeButton: Boolean
        )

        val overlayPadding =
            (12 * resources.displayMetrics.density).toInt()
        val overlayBounds = CoachOverlayService
            .visibleOverlayBounds()
            ?.let { bounds ->
                Rect(bounds).apply {
                    inset(-overlayPadding, -overlayPadding)
                }
            }
        var overlayExcluded = 0

        val allLines = mutableListOf<OcrLine>()
        for (block in visionText.textBlocks) {
            for (line in block.lines) {
                val bounds = line.boundingBox ?: continue
                if (overlayBounds != null &&
                    Rect.intersects(bounds, overlayBounds)
                ) {
                    overlayExcluded++
                    continue
                }
                val normalized = normalizeText(line.text)
                if (normalized.isBlank()) continue
                allLines += OcrLine(normalized, Rect(bounds))
            }
        }

        val chatTitle = detectChatTitle(
            allLines,
            screenWidth,
            screenHeight,
            platform
        )
        val lines = allLines.filter {
            isInsideConversationArea(
                it.bounds,
                screenWidth,
                screenHeight
            )
        }

        val transcribeLines = lines
            .filter { it.text.contains("转文字") }
        val transcribeMenuButtons = transcribeLines
            .map { Rect(it.bounds) }

        val durations = lines.mapNotNull { line ->
            val seconds = parseVoiceDuration(line.text)
                ?: return@mapNotNull null
            line to seconds
        }

        val yTolerance = (screenHeight * 0.045).toInt()
        val matchedButtons = mutableSetOf<OcrLine>()
        val voiceMatches = mutableListOf<VoiceMatch>()

        for ((duration, seconds) in durations) {
            val button = transcribeLines
                .map { candidate ->
                    candidate to kotlin.math.abs(
                        candidate.bounds.centerY() -
                            duration.bounds.centerY()
                    )
                }
                .filter { it.second <= yTolerance }
                .minByOrNull { it.second }
                ?.first

            if (button != null) {
                matchedButtons += button
            }

            val transcriptLimit =
                duration.bounds.bottom +
                    (screenHeight * 0.11).toInt()
            val transcriptVisible =
                button == null &&
                    lines.any { candidate ->
                        candidate !== duration &&
                            !candidate.text.contains("转文字") &&
                            parseVoiceDuration(candidate.text) == null &&
                            candidate.bounds.top > duration.bounds.bottom &&
                            candidate.bounds.top <= transcriptLimit &&
                            kotlin.math.abs(
                                candidate.bounds.centerX() -
                                    duration.bounds.centerX()
                            ) < screenWidth * 0.28 &&
                            isPotentialMessage(candidate.text)
                    }

            val horizontalLeft =
                (screenWidth * 0.08).toInt()
            val horizontalRight =
                (screenWidth * 0.14).toInt()
            val vertical =
                (screenHeight * 0.018).toInt()
            val bubble = Rect(
                (duration.bounds.left - horizontalLeft)
                    .coerceAtLeast(0),
                (duration.bounds.top - vertical)
                    .coerceAtLeast(0),
                (duration.bounds.right + horizontalRight)
                    .coerceAtMost(screenWidth),
                (duration.bounds.bottom + vertical)
                    .coerceAtMost(screenHeight)
            )

            voiceMatches += VoiceMatch(
                anchor = duration,
                seconds = seconds,
                transcriptVisible = transcriptVisible,
                bubbleBounds = bubble,
                fromTranscribeButton = false
            )
        }

        // On current WeChat builds ML Kit reliably sees the inline
        // "转文字" affordance even when it misses the tiny 2"/10" duration.
        // Treat the affordance itself as a deterministic voice marker.
        for (button in transcribeLines) {
            if (button in matchedButtons) continue
            val paddingX = (screenWidth * 0.22).toInt()
            val paddingY = (screenHeight * 0.018).toInt()
            voiceMatches += VoiceMatch(
                anchor = button,
                seconds = null,
                transcriptVisible = false,
                bubbleBounds = Rect(
                    (button.bounds.left - paddingX).coerceAtLeast(0),
                    (button.bounds.top - paddingY).coerceAtLeast(0),
                    button.bounds.right.coerceAtMost(screenWidth),
                    (button.bounds.bottom + paddingY)
                        .coerceAtMost(screenHeight)
                ),
                fromTranscribeButton = true
            )
        }

        val usedDurations = voiceMatches
            .filterNot { it.fromTranscribeButton }
            .map { it.anchor }
            .toSet()

        val untranscribedVoiceBubbles = voiceMatches
            .filter { voice ->
                !voice.transcriptVisible &&
                    senderFor(
                        voice.anchor.bounds,
                        screenWidth
                    ) == "对方"
            }
            .map { Rect(it.bubbleBounds) }

        val raw = mutableListOf<VisibleChatMessage>()
        for (voice in voiceMatches) {
            if (!voice.transcriptVisible) {
                val placeholder =
                    if (voice.seconds != null) {
                        "[语音消息 ${voice.seconds} 秒，内容尚未转写]"
                    } else {
                        "[语音消息，内容尚未转写]"
                    }
                raw += VisibleChatMessage(
                    sender = senderFor(
                        voice.anchor.bounds,
                        screenWidth
                    ),
                    text = placeholder,
                    top = voice.anchor.bounds.top,
                    centerX = voice.anchor.bounds.centerX()
                )
            }
        }

        for (line in lines) {
            if (line in usedDurations) continue
            if (line.text.contains("转文字")) continue
            if (!isPotentialMessage(line.text)) continue

            raw += VisibleChatMessage(
                sender = senderFor(
                    line.bounds,
                    screenWidth
                ),
                text = line.text,
                top = line.bounds.top,
                centerX = line.bounds.centerX()
            )
        }

        Log.i(
            "LuluPerf",
            "ocr_voice_detection menuButtons=${transcribeMenuButtons.size} " +
                "voiceMarkers=${voiceMatches.size} " +
                "untranscribed=${untranscribedVoiceBubbles.size} " +
                "overlayExcluded=$overlayExcluded"
        )

        return OcrParseResult(
            messages = normalizeMessages(raw),
            voiceMarkers = voiceMatches.size,
            transcribeMenuButtons = transcribeMenuButtons,
            untranscribedVoiceBubbles = untranscribedVoiceBubbles,
            chatTitle = chatTitle
        )
    }

    private fun detectChatTitle(
        lines: List<OcrLine>,
        screenWidth: Int,
        screenHeight: Int,
        platform: String
    ): String? {
        val minY = (screenHeight * 0.025).toInt()
        val maxY = (screenHeight * 0.13).toInt()
        val candidates = lines.filter { line ->
            val centerX = line.bounds.centerX()
            line.bounds.centerY() in minY..maxY &&
                centerX in
                    (screenWidth * 0.20).toInt()..
                    (screenWidth * 0.80).toInt() &&
                isLikelyChatTitle(line.text)
        }

        val boundMatches = candidates.mapNotNull { line ->
            val identity = IdentityStore.identityForChatTitle(
                this,
                platform,
                line.text
            ) ?: return@mapNotNull null
            identity.account.id to line
        }

        val uniqueAccounts = boundMatches
            .map { it.first }
            .distinct()
        if (uniqueAccounts.size == 1) {
            return boundMatches
                .filter { it.first == uniqueAccounts.first() }
                .minByOrNull { (_, line) ->
                    kotlin.math.abs(
                        line.bounds.centerX() -
                            screenWidth / 2
                    )
                }
                ?.second
                ?.text
        }
        if (uniqueAccounts.size > 1) {
            return null
        }

        return candidates
            .minByOrNull { line ->
                kotlin.math.abs(
                    line.bounds.centerX() -
                        screenWidth / 2
                )
            }
            ?.text
    }

    private fun detectChatTitleFromNodes(
        root: AccessibilityNodeInfo,
        screenWidth: Int,
        screenHeight: Int,
        platform: String
    ): String? {
        val candidates = mutableListOf<OcrLine>()

        fun walk(node: AccessibilityNodeInfo?) {
            if (node == null) return
            if (node.isVisibleToUser) {
                val text = normalizeText(
                    node.text?.toString().orEmpty().ifBlank {
                        node.contentDescription
                            ?.toString()
                            .orEmpty()
                    }
                )
                if (text.isNotBlank()) {
                    val bounds = Rect()
                    node.getBoundsInScreen(bounds)
                    candidates += OcrLine(text, bounds)
                }
            }
            for (index in 0 until node.childCount) {
                walk(node.getChild(index))
            }
        }

        walk(root)
        return detectChatTitle(
            candidates,
            screenWidth,
            screenHeight,
            platform
        )
    }

    private fun isLikelyChatTitle(text: String): Boolean {
        val value = text.trim()
        if (value.length !in 1..40) return false
        if (value in setOf(
                "微信",
                "QQ",
                "返回",
                "聊天信息",
                "更多",
                "搜索"
            )
        ) {
            return false
        }
        if (Regex("""^\d{1,2}:\d{2}$""").matches(value)) {
            return false
        }
        return true
    }

    private fun tapBounds(
        bounds: Rect,
        callback: (Boolean) -> Unit
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            callback(false)
            return
        }

        val path = Path().apply {
            moveTo(
                bounds.exactCenterX(),
                bounds.exactCenterY()
            )
        }
        val gesture = GestureDescription.Builder()
            .addStroke(
                GestureDescription.StrokeDescription(
                    path,
                    0L,
                    80L
                )
            )
            .build()

        val dispatched = dispatchGesture(
            gesture,
            object : GestureResultCallback() {
                override fun onCompleted(
                    gestureDescription: GestureDescription?
                ) {
                    callback(true)
                }

                override fun onCancelled(
                    gestureDescription: GestureDescription?
                ) {
                    callback(false)
                }
            },
            mainHandler
        )
        if (!dispatched) {
            callback(false)
        }
    }

    private fun longPressBounds(
        bounds: Rect,
        callback: (Boolean) -> Unit
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            callback(false)
            return
        }

        val path = Path().apply {
            moveTo(
                bounds.exactCenterX(),
                bounds.exactCenterY()
            )
        }
        val gesture = GestureDescription.Builder()
            .addStroke(
                GestureDescription.StrokeDescription(
                    path,
                    0L,
                    650L
                )
            )
            .build()

        val dispatched = dispatchGesture(
            gesture,
            object : GestureResultCallback() {
                override fun onCompleted(
                    gestureDescription: GestureDescription?
                ) {
                    callback(true)
                }

                override fun onCancelled(
                    gestureDescription: GestureDescription?
                ) {
                    callback(false)
                }
            },
            mainHandler
        )
        if (!dispatched) {
            callback(false)
        }
    }

    private fun collectTextNodes(
        node: AccessibilityNodeInfo?,
        out: MutableList<VisibleChatMessage>,
        screenWidth: Int,
        screenHeight: Int
    ) {
        if (node == null) return

        if (node.isVisibleToUser) {
            val visibleText = node.text?.toString().orEmpty()
                .ifBlank {
                    node.contentDescription?.toString().orEmpty()
                }
            val text = normalizeText(visibleText)
            if (isPotentialMessage(text)) {
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                if (isInsideConversationArea(
                        bounds,
                        screenWidth,
                        screenHeight
                    )
                ) {
                    out += VisibleChatMessage(
                        sender = senderFor(bounds, screenWidth),
                        text = text,
                        top = bounds.top,
                        centerX = bounds.centerX()
                    )
                }
            }
        }

        for (index in 0 until node.childCount) {
            collectTextNodes(
                node.getChild(index),
                out,
                screenWidth,
                screenHeight
            )
        }
    }

    private fun collectEditableNodes(
        node: AccessibilityNodeInfo?,
        out: MutableList<AccessibilityNodeInfo>
    ) {
        if (node == null) return
        if (node.isVisibleToUser &&
            (node.isEditable ||
                node.className?.toString()?.contains("EditText") == true)
        ) {
            out += node
        }
        for (index in 0 until node.childCount) {
            collectEditableNodes(node.getChild(index), out)
        }
    }

    private fun normalizeMessages(
        raw: List<VisibleChatMessage>
    ): List<VisibleChatMessage> =
        raw
            .distinctBy {
                listOf(
                    it.text,
                    it.top,
                    it.centerX / 12
                ).joinToString("|")
            }
            .sortedWith(
                compareBy<VisibleChatMessage> { it.top }
                    .thenBy { it.centerX }
            )
            .takeLast(12)

    private fun senderFor(
        bounds: Rect,
        screenWidth: Int
    ): String {
        val centerX = bounds.centerX()
        return when {
            centerX >= screenWidth * 0.56 -> "我"
            centerX <= screenWidth * 0.44 -> "对方"
            bounds.right >= screenWidth * 0.78 -> "我"
            else -> "对方"
        }
    }

    private fun platformFor(packageName: String): String =
        when (packageName) {
            WECHAT_PACKAGE -> "wechat"
            QQ_PACKAGE -> "qq"
            else -> throw IllegalStateException(
                "当前前台不是微信或 QQ，请在聊天界面里点“噜”"
            )
        }

    private fun isInsideConversationArea(
        bounds: Rect,
        width: Int,
        height: Int
    ): Boolean {
        if (bounds.width() <= 0 || bounds.height() <= 0) return false
        if (bounds.top < height * 0.10) return false
        if (bounds.bottom > height * 0.82) return false
        if (bounds.left <= 0 && bounds.right >= width) return false
        return true
    }

    private fun parseVoiceDuration(text: String): Int? {
        val match = Regex(
            """^(\d{1,3})\s*["”'′″秒sS]+$"""
        ).find(text) ?: return null
        val seconds = match.groupValues[1].toIntOrNull()
            ?: return null
        return seconds.takeIf { it in 1..180 }
    }

    private fun elapsedMs(started: Long): Long =
        (SystemClock.elapsedRealtimeNanos() - started) / 1_000_000

    private fun normalizeText(value: String): String =
        value
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(500)

    private fun isPotentialMessage(text: String): Boolean {
        if (text.isBlank()) return false
        if (text.length > 500) return false

        val ignored = setOf(
            "微信",
            "QQ",
            "发送",
            "转文字",
            "按住 说话",
            "切换到按住说话",
            "语音输入",
            "更多功能按钮",
            "返回",
            "聊天信息",
            "表情",
            "相册",
            "拍摄",
            "位置",
            "红包",
            "转账",
            "+"
        )
        if (text in ignored) return false

        if (Regex("^\\d{1,2}:\\d{2}$").matches(text)) return false
        if (Regex("^\\d{1,2}月\\d{1,2}日.*$").matches(text)) return false
        return true
    }
}
