param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$AdbArgs
)

$ErrorActionPreference = "Stop"
$repo = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$tools = Join-Path $repo ".tools\android"
$adb = Join-Path $tools "sdk\platform-tools\adb.exe"
$profile = Join-Path $tools "cache\adb-profile"
$androidUser = Join-Path $tools "cache\android-user"

if (-not (Test-Path -LiteralPath $adb)) {
    throw "Project-local adb not found: $adb"
}

New-Item -ItemType Directory -Force -Path $profile, $androidUser | Out-Null
$env:HOME = $profile
$env:USERPROFILE = $profile
$env:ANDROID_USER_HOME = $androidUser
$env:ANDROID_HOME = Join-Path $tools "sdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:ADB_VENDOR_KEYS = Join-Path $androidUser "adbkey"

& $adb @AdbArgs
exit $LASTEXITCODE
