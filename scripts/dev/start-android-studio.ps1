$ErrorActionPreference = "Stop"
$repo = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$tools = Join-Path $repo ".tools\android"
$studio = Join-Path $tools "studio\android-studio\bin\studio64.exe"
$androidProject = Join-Path $repo "apps\android"

if (-not (Test-Path -LiteralPath $studio)) {
    throw "Project-local Android Studio not found: $studio"
}

$env:ANDROID_HOME = Join-Path $tools "sdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:ANDROID_USER_HOME = Join-Path $tools "cache\android-user"
$env:GRADLE_USER_HOME = Join-Path $tools "cache\gradle"
$env:STUDIO_PROPERTIES = Join-Path $tools "studio-user\studio.properties"
$env:HOME = Join-Path $tools "cache\adb-profile"
$env:USERPROFILE = $env:HOME
$env:ADB_VENDOR_KEYS = Join-Path $env:ANDROID_USER_HOME "adbkey"

$dirs = @(
    (Join-Path $tools "studio-user\config"),
    (Join-Path $tools "studio-user\system"),
    (Join-Path $tools "studio-user\plugins"),
    (Join-Path $tools "studio-user\log"),
    $env:ANDROID_USER_HOME,
    $env:GRADLE_USER_HOME
)
foreach ($dir in $dirs) {
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
}

$sdkForProperties = $env:ANDROID_HOME.Replace("\", "/")
Set-Content -LiteralPath (Join-Path $androidProject "local.properties") -Value "sdk.dir=$sdkForProperties" -Encoding ASCII

Start-Process -FilePath $studio -ArgumentList @($androidProject)
