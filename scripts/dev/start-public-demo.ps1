param(
    [switch]$NoBrowser
)

$ErrorActionPreference = "Stop"
$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$RunDir = Join-Path $ProjectRoot ".run"
$LogDir = Join-Path $RunDir "logs"
$FrontendRoot = Join-Path $ProjectRoot "apps\web"
$Cloudflared = Join-Path $ProjectRoot "tools\cloudflared\cloudflared.exe"
$PublicPort = 3002
$PublicLocalUrl = "http://127.0.0.1:$PublicPort/"
$PublicUrlFile = Join-Path $RunDir "public-url.txt"

New-Item -ItemType Directory -Force -Path $RunDir, $LogDir | Out-Null

function Write-Stage([string]$Message) {
    Write-Host ("[LULU public demo] " + $Message) -ForegroundColor Yellow
}

function Test-Http([string]$Url) {
    try {
        $code = (& curl.exe -sS -L -o NUL -w "%{http_code}" --max-time 6 $Url 2>$null).Trim()
        return ($code -match "^[23][0-9][0-9]$")
    } catch {
        return $false
    }
}

function Wait-Http([string]$Url, [int]$TimeoutSeconds) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if (Test-Http $Url) { return $true }
        Start-Sleep -Seconds 1
    }
    return $false
}

function Stop-ManagedProcess([string]$PidFile) {
    if (-not (Test-Path $PidFile)) { return }
    $savedPid = Get-Content $PidFile -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($savedPid -match "^\d+$") {
        $proc = Get-Process -Id ([int]$savedPid) -ErrorAction SilentlyContinue
        if ($proc) {
            & taskkill.exe /PID $savedPid /T /F *> $null
            Start-Sleep -Milliseconds 500
        }
    }
    Remove-Item $PidFile -Force -ErrorAction SilentlyContinue
}

Write-Stage "Ensuring the local Lulu stack is running..."
& (Join-Path $PSScriptRoot "start-dev.ps1") -NoBrowser
if ($LASTEXITCODE -ne 0) { throw "Local Lulu stack failed to start." }

$frontendPidFile = Join-Path $RunDir "public-frontend.pid"
Stop-ManagedProcess $frontendPidFile

$portOwner = Get-NetTCPConnection -State Listen -LocalPort $PublicPort -ErrorAction SilentlyContinue |
    Select-Object -First 1
if ($portOwner) {
    throw "Port $PublicPort is already in use by PID $($portOwner.OwningProcess)."
}

$npm = "D:\Anaconda3\envs\lulu-ai-agent\npm.cmd"
if (-not (Test-Path $npm)) { throw "npm.cmd was not found at $npm." }

$env:LULU_PUBLIC_DEMO = "true"
$env:VITE_PUBLIC_DEMO = "true"
$frontendOut = Join-Path $LogDir "public-frontend.out.log"
$frontendErr = Join-Path $LogDir "public-frontend.err.log"
Remove-Item $frontendOut, $frontendErr -Force -ErrorAction SilentlyContinue

Write-Stage "Starting read-only public frontend on port $PublicPort..."
$frontendCommand = "$npm run dev -- --host 127.0.0.1 --port $PublicPort --strictPort"
$frontend = Start-Process -FilePath "cmd.exe" -ArgumentList @("/d", "/s", "/c", $frontendCommand) -WorkingDirectory $FrontendRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput $frontendOut -RedirectStandardError $frontendErr
$frontend.Id | Set-Content $frontendPidFile

if (-not (Wait-Http $PublicLocalUrl 30)) {
    throw "Public demo frontend failed to start. See .run\logs\public-frontend.*.log"
}

if (-not (Test-Path $Cloudflared)) {
    throw "cloudflared.exe was not found at $Cloudflared."
}

$tunnelPidFile = Join-Path $RunDir "public-tunnel.pid"
Stop-ManagedProcess $tunnelPidFile
Remove-Item $PublicUrlFile -Force -ErrorAction SilentlyContinue

$tunnelOut = Join-Path $LogDir "cloudflared-public.out.log"
$tunnelErr = Join-Path $LogDir "cloudflared-public.err.log"
Remove-Item $tunnelOut, $tunnelErr -Force -ErrorAction SilentlyContinue

Write-Stage "Starting free Cloudflare Quick Tunnel..."
$tunnel = Start-Process -FilePath $Cloudflared -ArgumentList @(
    "tunnel",
    "--no-autoupdate",
    "--url", "http://127.0.0.1:$PublicPort",
    "--http-host-header", "127.0.0.1:$PublicPort"
) -WindowStyle Hidden -PassThru -RedirectStandardOutput $tunnelOut -RedirectStandardError $tunnelErr
$tunnel.Id | Set-Content $tunnelPidFile

$deadline = (Get-Date).AddSeconds(35)
$publicUrl = $null
while (-not $publicUrl -and (Get-Date) -lt $deadline) {
    Start-Sleep -Seconds 1
    $combined = ""
    if (Test-Path $tunnelOut) { $combined += (Get-Content $tunnelOut -Raw -ErrorAction SilentlyContinue) }
    if (Test-Path $tunnelErr) { $combined += (Get-Content $tunnelErr -Raw -ErrorAction SilentlyContinue) }
    $match = [regex]::Match($combined, "https://[a-z0-9-]+\.trycloudflare\.com")
    if ($match.Success) { $publicUrl = $match.Value }
}

if (-not $publicUrl) {
    throw "Cloudflare did not return a public URL. See .run\logs\cloudflared-public.*.log"
}

$publicUrl | Set-Content $PublicUrlFile
$desktop = [Environment]::GetFolderPath("Desktop")
if ($desktop) {
    $publicUrl | Set-Content (Join-Path $desktop "噜噜公网地址.txt") -Encoding UTF8
}
try { Set-Clipboard -Value $publicUrl } catch {}

Write-Stage "Waiting for the public URL to become reachable..."
$reachable = Wait-Http $publicUrl 20

Write-Host ""
Write-Host "LULU public demo is ready:" -ForegroundColor Green
Write-Host "  $publicUrl" -ForegroundColor Cyan
Write-Host ""
Write-Host "This is a temporary read-only demo URL." -ForegroundColor DarkGray
Write-Host "URL file: $PublicUrlFile" -ForegroundColor DarkGray
Write-Host "Stop script: scripts\dev\stop-public-demo.ps1" -ForegroundColor DarkGray
if (-not $reachable) {
    Write-Warning "The tunnel URL was created but did not answer the first health check yet. It may need a few more seconds."
}

if (-not $NoBrowser) {
    Start-Process $publicUrl | Out-Null
}
exit 0
