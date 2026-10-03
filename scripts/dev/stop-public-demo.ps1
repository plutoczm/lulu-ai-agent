$ErrorActionPreference = "SilentlyContinue"
$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$RunDir = Join-Path $ProjectRoot ".run"

function Stop-FromPidFile([string]$PidFile) {
    if (-not (Test-Path $PidFile)) { return }
    $savedPid = Get-Content $PidFile | Select-Object -First 1
    if ($savedPid -match "^\d+$") {
        $proc = Get-Process -Id ([int]$savedPid) -ErrorAction SilentlyContinue
        if ($proc) {
            & taskkill.exe /PID $savedPid /T /F *> $null
        }
    }
    Remove-Item $PidFile -Force -ErrorAction SilentlyContinue
}

Stop-FromPidFile (Join-Path $RunDir "public-tunnel.pid")
Stop-FromPidFile (Join-Path $RunDir "public-frontend.pid")
Remove-Item (Join-Path $RunDir "public-url.txt") -Force -ErrorAction SilentlyContinue

Write-Host "LULU public demo stopped." -ForegroundColor Yellow
