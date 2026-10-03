param(
    [switch]$StopInfrastructure
)

$ErrorActionPreference = "Continue"
$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$RunDir = Join-Path $ProjectRoot ".run"

function Write-Stage([string]$Message) {
    Write-Host ("[lulu-ai-agent] " + $Message) -ForegroundColor Cyan
}

function Get-ListeningPid([int]$Port) {
    $conn = Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($null -eq $conn) { return $null }
    return [int]$conn.OwningProcess
}

function Stop-ProcessTree([int]$ProcessId, [string]$Label) {
    $process = Get-Process -Id $ProcessId -ErrorAction SilentlyContinue
    if (-not $process) { return }
    Write-Stage "Stopping $Label (PID $ProcessId)..."
    & taskkill.exe /PID $ProcessId /T /F *> $null
}

function Stop-TrackedService(
        [string]$Label,
        [string]$PidFileName,
        [int]$Port) {
    $pidFile = Join-Path $RunDir $PidFileName
    if (Test-Path $pidFile) {
        $trackedPid = [int](Get-Content $pidFile -ErrorAction SilentlyContinue | Select-Object -First 1)
        if ($trackedPid) { Stop-ProcessTree $trackedPid $Label }
        Remove-Item $pidFile -Force -ErrorAction SilentlyContinue
        Start-Sleep -Milliseconds 500
    }

    $listenerPid = Get-ListeningPid $Port
    if ($listenerPid) {
        $proc = Get-CimInstance Win32_Process -Filter "ProcessId=$listenerPid" -ErrorAction SilentlyContinue
        $cmd = if ($proc) { [string]$proc.CommandLine } else { "" }
        if ($cmd -like "*$ProjectRoot*") {
            Stop-ProcessTree $listenerPid $Label
        } else {
            Write-Warning "$Label still owns port $Port via an untracked process (PID $listenerPid); it was not killed."
        }
    }
}
Write-Stage "Stopping application services..."
Stop-TrackedService "QQ Bot" "qqbot.pid" 8131
Stop-TrackedService "frontend" "frontend.pid" 3000
Stop-TrackedService "backend" "backend.pid" 8123

if ($StopInfrastructure) {
    Write-Stage "Stopping PGVector container..."
    $docker = Get-Command docker -ErrorAction SilentlyContinue
    if ($docker) {
        & $docker.Source stop lulu-ai-agent-pgvector *> $null
    }

    $ollamaPidFile = Join-Path $RunDir "ollama-started.pid"
    if (Test-Path $ollamaPidFile) {
        $ollamaPid = [int](Get-Content $ollamaPidFile -ErrorAction SilentlyContinue | Select-Object -First 1)
        if ($ollamaPid) {
            Stop-ProcessTree $ollamaPid "Ollama started by lulu-ai-agent"
        }
        Remove-Item $ollamaPidFile -Force -ErrorAction SilentlyContinue
    }
    Write-Stage "Infrastructure stop requested. Shared Ollama instances not started by this script are left untouched."
} else {
    Write-Stage "PGVector and Ollama were left running for faster next startup."
}

Write-Host ""
Write-Host "lulu-ai-agent application services are stopped." -ForegroundColor Green
exit 0
