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

## Verified build

The debug APK has been built with `:app:assembleDebug`, aligned successfully, and verified with Android `apksigner` using APK Signature Scheme v2.

`lintDebug` also completes successfully. Current lint output has no errors; remaining warnings are UI/resource polish items.

## Storage location

Android local preferences store the Person/Account mapping, active account, backend URL, and login session cookie.

Recent raw chat messages are stored in the backend PostgreSQL table `relationship_thread_message`. Long-term compact relationship facts and embeddings are stored in the backend `relationship_memory` PGVector table.

In the current development environment the backend database runs on the Windows machine in Docker. If the backend is later deployed to a Linux server, these server-side records move with that PostgreSQL/PGVector deployment.

## Privacy boundary

The app does not hook WeChat/QQ, decrypt private app databases, continuously monitor the clipboard, infer account identity automatically, or send messages automatically.
