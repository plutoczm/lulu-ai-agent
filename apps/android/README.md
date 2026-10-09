# Android Mobile Assistant

`apps/android` is the single Android client for WeChat and QQ coaching.

## Identity model

One real person can own multiple chat accounts:

- `PersonProfile`: shared long-term relationship memory and relationship metadata.
- `ContactAccount`: one concrete WeChat/QQ account with its own recent-message thread.
- `conversationId`: recent-message thread scope for the concrete account.
- `personId`: long-term memory scope shared across that person's accounts.
- `accountId`: provenance of the concrete source account.

Account linking and reassignment are explicit user actions. LULU never infers that two accounts belong to the same person from nickname, writing style, or model guesses.

## Project-local Android toolchain

The Windows Android toolchain is intentionally installed under the repository only:

```text
.tools/android/
├─ studio/android-studio/        # Android Studio Rabbit 1 / 2026.2.1
├─ jdk-17/                      # Temurin JDK 17
├─ gradle/gradle-8.9/           # AGP 8.7 compatible Gradle
├─ sdk/
│  ├─ cmdline-tools/latest/
│  ├─ platform-tools/           # adb 37.0.1
│  ├─ platforms/android-35/
│  └─ build-tools/34.0.0/
├─ cache/                       # Gradle, Android user state, adb keys
└─ studio-user/                 # Android Studio config/system/plugins/log
```

`.tools/` is gitignored. No global Android SDK, Gradle, adb, or Android Studio installation is required.

### Build APK

From the repository root:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\dev\build-android.ps1
```

Output:

```text
apps/android/app/build/outputs/apk/debug/app-debug.apk
```

Run Android Lint with the same isolated toolchain:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\dev\build-android.ps1 -Task ':app:lintDebug'
```

### Start project-local Android Studio

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\dev\start-android-studio.ps1
```

The launcher redirects Android Studio configuration, system cache, plugins, logs, SDK state, Gradle cache, and adb home into `.tools/android/`.

### Use project-local adb

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\dev\adb-android.ps1 devices -l
powershell -ExecutionPolicy Bypass -File .\scripts\dev\adb-android.ps1 install -r .\apps\android\app\build\outputs\apk\debug\app-debug.apk
```

## Verified build and real-device performance

The debug APK has been built with `:app:assembleDebug`, aligned successfully, and verified with Android `apksigner` using APK Signature Scheme v2.

`lintDebug` completes successfully with zero errors.

The app has also been installed and exercised on a OnePlus Ace 3 Pro (PJX110, Android 16 / API 36). The verified Android fast path is:

```text
Person recent facts from PostgreSQL
+ current Account recent thread
+ current WeChat/QQ screen captured through Accessibility/OCR
+ voice transcription when available
→ FAST model compact A/B/C response
→ overlay immediately
→ raw observations enter the durable sync pipeline
→ long-term memory extraction continues asynchronously
```

Measured real-device latency after optimization:

- First measured quick run: 3.347 s end-to-end on the phone.
- Warm measured quick run after the redesigned UI: 2.309 s end-to-end.
- WeChat OCR fallback run: 2.404 s tap-to-A/B/C.
- In that OCR run, current-screen capture cost 388 ms total: Accessibility probe 9 ms, screenshot 15 ms, on-device Chinese OCR 288 ms, and OCR post-processing 7 ms.
- Backend warm timing: 1.804 s total; qwen-plus model call 1.793 s, recent-memory read 1 ms, thread read 3 ms, persistence scheduling 5 ms.

The previous approximately 30-second path blocked the response on local Ollama long-term-memory extraction. That work is now outside the user-facing critical path.

## Continuous sync and storage

LULU does not require the user to reply through LULU in order to keep conversation history useful.

- `LuluNotificationListenerService` ingests incoming WeChat/QQ notifications after explicit notification-access permission.
- `LuluAccessibilityService` passively reconciles already-visible chat bubbles when the user naturally uses WeChat/QQ. Composer-only typing events are filtered so unsent drafts are not treated as history.
- An explicit tap on the floating “噜” performs a stronger current-screen reconciliation and on-device Chinese OCR before requesting A/B/C. Voice durations are captured as placeholders; UI transcription is attempted only when the separate fallback toggle is explicitly enabled.
- `ChatSyncOutbox` stores observations in a phone-local SQLite outbox before delivery. Backend/network downtime therefore does not discard captured messages; pending rows are retried when LULU, Accessibility, or the notification listener reconnects.
- Stable source keys plus backend cross-source reconciliation merge notification and screen observations of the same message.
- `VoiceIngestionQueue` persists voice-notification work separately from the normal message outbox. Jobs can be `pending`, `waiting_audio`, or `failed`; waiting for an accessible audio source does not consume ASR retry attempts.
- `VoiceAudioSourceAdapter` is the background-first boundary. The first adapter, `NotificationAttachmentVoiceSource`, only reads an audio `content://` URI already exposed by Android through the notification. It does not depend on Root, Hook, or private app storage.
- `VoiceAudioSupport` performs bounded file validation, format detection, container checks, and sample-rate probing before upload. The backend `/api/ai/voice/transcribe` endpoint uses DashScope `paraformer-realtime-v2`.
- A successful transcript carries `replacesSourceKey`, so the exact notification `voice` placeholder is enriched in place. The UI “transcribe to text” path is a separate explicit fallback, off by default and only allowed during a user-invoked “噜” flow.

Android SharedPreferences store the Person/Account mapping, active account, optional bound chat title, backend URL, and login session cookie. The phone-local SQLite outbox stores pending chat sync rows, while a separate SQLite voice queue stores pending, waiting-audio, and failed background-ASR jobs.

The backend PostgreSQL table `relationship_thread_message` retains synchronized raw history for each concrete Account. Prompt builders remain bounded independently, so retaining full history does not mean sending all history to the LLM. Long-term compact relationship facts and embeddings are stored in the backend `relationship_memory` PGVector table.

In the current development environment the backend database runs on the Windows machine in Docker. If the backend is later deployed to a Linux server, these server-side records move with that PostgreSQL/PGVector deployment.

## Privacy boundary

The default path does not hook WeChat/QQ, decrypt private app databases, continuously monitor the clipboard, infer account identity automatically, or send messages automatically. Notification access and Accessibility remain explicit user-granted permissions.

Background ASR is enabled by default but only consumes audio that Android has explicitly made accessible to LULU, such as a notification attachment URI. It does not scan WeChat/QQ private storage. When no accessible audio exists, the durable job remains `waiting_audio`. The optional UI transcription fallback is disabled by default and only runs after an explicit user toggle plus a user-invoked “噜” action.
