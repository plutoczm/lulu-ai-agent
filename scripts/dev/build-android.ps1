param([string]$Task = ":app:assembleDebug")

$ErrorActionPreference = "Stop"
$repo = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$tools = Join-Path $repo ".tools\android"
$androidProject = Join-Path $repo "apps\android"

$env:JAVA_HOME = Join-Path $tools "jdk-17"
$env:ANDROID_HOME = Join-Path $tools "sdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:ANDROID_USER_HOME = Join-Path $tools "cache\android-user"
$env:GRADLE_USER_HOME = Join-Path $tools "cache\gradle"
$gradle = Join-Path $tools "gradle\gradle-8.9\bin\gradle.bat"

$required = @(
    (Join-Path $env:JAVA_HOME "bin\java.exe"),
    $gradle,
    (Join-Path $env:ANDROID_HOME "platform-tools\adb.exe"),
    (Join-Path $env:ANDROID_HOME "platforms\android-35\android.jar"),
    (Join-Path $env:ANDROID_HOME "build-tools\34.0.0\aapt2.exe")
)
foreach ($path in $required) {
    if (-not (Test-Path -LiteralPath $path)) {
        throw "Missing project-local Android tool: $path"
    }
}

foreach ($dir in @($env:ANDROID_USER_HOME, $env:GRADLE_USER_HOME)) {
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
}
$env:Path = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$(Split-Path $gradle);$env:Path"

$sdkForProperties = $env:ANDROID_HOME.Replace("\", "/")
Set-Content -LiteralPath (Join-Path $androidProject "local.properties") -Value "sdk.dir=$sdkForProperties" -Encoding ASCII

Push-Location $androidProject
try {
    & $gradle $Task --no-daemon --console=plain
    if ($LASTEXITCODE -ne 0) {
        throw "Android Gradle build failed with exit code $LASTEXITCODE"
    }
} finally {
    Pop-Location
}
