$ErrorActionPreference = "SilentlyContinue"
$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$EnvFile = Join-Path $ProjectRoot ".env.local"

function Test-Http([string]$Url) {
    try {
        $code = (& curl.exe -sS -o NUL -w "%{http_code}" --max-time 3 $Url 2>$null).Trim()
        return ($code -match "^[23][0-9][0-9]$" -or $code -eq "401" -or $code -eq "403")
    } catch {
        return $false
    }
}

Write-Host "lulu-ai-agent status" -ForegroundColor Cyan
Write-Host "------------------"

$docker = Get-Command docker -ErrorAction SilentlyContinue
if ($docker) {
    $pg = (& $docker.Source ps --filter "name=lulu-ai-agent-pgvector" --format "{{.Status}}").Trim()
    Write-Host ("PGVector : " + $(if ($pg) { $pg } else { "stopped" }))
} else {
    Write-Host "PGVector : Docker CLI not found"
}

try {
    $tags = Invoke-RestMethod -Uri "http://127.0.0.1:11434/api/tags" -TimeoutSec 3
    $hasQwen = @($tags.models.name) -contains "qwen3:8b"
    Write-Host ("Ollama   : running; qwen3:8b=" + $hasQwen)
} catch {
    Write-Host "Ollama   : stopped"
}

Write-Host ("Backend  : " + $(if (Test-Http "http://127.0.0.1:8123/api/v3/api-docs") { "healthy" } else { "stopped/unhealthy" }))
Write-Host ("Frontend : " + $(if (Test-Http "http://127.0.0.1:3000/") { "healthy" } else { "stopped/unhealthy" }))

$qqEnabled = $false
if (Test-Path $EnvFile) {
    $qqLine = Get-Content $EnvFile |
        Where-Object { $_ -match '^QQ_BOT_ENABLED=' } |
        Select-Object -Last 1
    if ($qqLine) {
        $qqEnabled = ($qqLine.Split('=', 2)[1].Trim() -eq 'true')
    }
}

if ($qqEnabled) {
    try {
        $qq = Invoke-RestMethod -Uri "http://127.0.0.1:8131/health" -TimeoutSec 3
        $qqState = if ($qq.ready) { "ready" } else { "starting/unhealthy" }
        Write-Host ("QQ Bot    : " + $qqState + "; transport=" + $qq.transport)
    } catch {
        Write-Host "QQ Bot    : stopped/unhealthy"
    }
} else {
    Write-Host "QQ Bot    : disabled"
}
