param(
    [switch]$NoBrowser
)

$ErrorActionPreference = "Stop"
$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$BackendRoot = Join-Path $ProjectRoot "services\backend"
$FrontendRoot = Join-Path $ProjectRoot "apps\web"
$QQRoot = Join-Path $ProjectRoot "services\qq-bot"
$ImageMcpRoot = Join-Path $ProjectRoot "services\image-search-mcp"
$RunDir = Join-Path $ProjectRoot ".run"
$LogDir = Join-Path $RunDir "logs"
$EnvFile = Join-Path $ProjectRoot ".env.local"
$BackendUrl = "http://127.0.0.1:8123/api/health"
$FrontendUrl = "http://127.0.0.1:3000/"
$QQBotHealthUrl = "http://127.0.0.1:8131/health"
$BrowserUrl = "http://127.0.0.1:3000/"

$env:LULU_PROJECT_ROOT = $ProjectRoot

New-Item -ItemType Directory -Force -Path $RunDir, $LogDir | Out-Null

function Write-Stage([string]$Message) {
    Write-Host ("[lulu-ai-agent] " + $Message) -ForegroundColor Cyan
}

function Read-DotEnv([string]$Path) {
    $result = @{}
    if (-not (Test-Path $Path)) { return $result }
    foreach ($raw in Get-Content $Path) {
        $line = $raw.Trim()
        if (-not $line -or $line.StartsWith("#") -or -not $line.Contains("=")) { continue }
        $parts = $line.Split("=", 2)
        $value = $parts[1].Trim()
        if (($value.StartsWith('"') -and $value.EndsWith('"')) -or
            ($value.StartsWith("'") -and $value.EndsWith("'"))) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        $result[$parts[0].Trim()] = $value
    }
    return $result
}
function Test-Http([string]$Url) {
    try {
        $code = (& curl.exe -sS -o NUL -w "%{http_code}" --max-time 3 $Url 2>$null).Trim()
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

function Test-QQBotReady {
    try {
        $status = Invoke-RestMethod -Uri $QQBotHealthUrl -TimeoutSec 3
        return ($status.enabled -eq $true -and $status.ready -eq $true)
    } catch {
        return $false
    }
}

function Get-ListeningPid([int]$Port) {
    $conn = Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($null -eq $conn) { return $null }
    return [int]$conn.OwningProcess
}

function Assert-PortFreeOrHealthy([int]$Port, [string]$HealthUrl, [string]$Name) {
    if (Test-Http $HealthUrl) { return $true }
    $pidOnPort = Get-ListeningPid $Port
    if ($pidOnPort) {
        $proc = Get-CimInstance Win32_Process -Filter "ProcessId=$pidOnPort" -ErrorAction SilentlyContinue
        $cmd = if ($proc) { [string]$proc.CommandLine } else { "" }
        if ($cmd -like "*$ProjectRoot*") {
            Write-Stage "$Name owns port $Port but is unhealthy; restarting it."
            & taskkill.exe /PID $pidOnPort /T /F *> $null
            Start-Sleep -Seconds 1
            return $false
        }
        throw "$Name cannot start: port $Port is occupied by unknown PID $pidOnPort, and health check failed."
    }
    return $false
}

$config = Read-DotEnv $EnvFile
Write-Stage "1/6 Checking Docker and PostgreSQL/PGVector..."
$docker = Get-Command docker -ErrorAction SilentlyContinue
if (-not $docker) {
    throw "Docker CLI was not found."
}

& $docker.Source info *> $null
if ($LASTEXITCODE -ne 0) {
    $dockerDesktopCandidates = @(
        "D:\Docker\Docker Desktop.exe",
        "$env:ProgramFiles\Docker\Docker\Docker Desktop.exe"
    )
    $dockerDesktop = $dockerDesktopCandidates |
        Where-Object { Test-Path $_ } |
        Select-Object -First 1
    if (-not $dockerDesktop) {
        throw "Docker Desktop is not running and its executable could not be found."
    }
    Write-Stage "Starting Docker Desktop..."
    Start-Process -FilePath $dockerDesktop | Out-Null
    $deadline = (Get-Date).AddSeconds(90)
    do {
        Start-Sleep -Seconds 2
        & $docker.Source info *> $null
        $dockerReady = ($LASTEXITCODE -eq 0)
    } while (-not $dockerReady -and (Get-Date) -lt $deadline)
    if (-not $dockerReady) { throw "Docker Desktop did not become ready within 90 seconds." }
}
$containerName = "lulu-ai-agent-pgvector"
$containerExists = (& $docker.Source ps -a --filter "name=^/$containerName$" --format "{{.Names}}").Trim()
if (-not $containerExists) {
    $pgUser = if ($config["POSTGRES_USER"]) { $config["POSTGRES_USER"] } else { "lulu_ai_agent" }
    $pgDb = if ($config["POSTGRES_DB"]) { $config["POSTGRES_DB"] } else { "lulu_ai_agent" }
    $pgPort = if ($config["POSTGRES_PORT"]) { $config["POSTGRES_PORT"] } else { "5433" }
    $pgPassword = $config["POSTGRES_PASSWORD"]
    if (-not $pgPassword) { throw "POSTGRES_PASSWORD is empty in .env.local." }

    Write-Stage "Creating PGVector container for the first time..."
    $env:POSTGRES_USER = $pgUser
    $env:POSTGRES_DB = $pgDb
    $env:POSTGRES_PASSWORD = $pgPassword
    & $docker.Source volume create lulu-ai-agent-pgvector-data *> $null

    $dockerArgs = @(
        "run", "-d", "--name", $containerName,
        "-e", "POSTGRES_USER",
        "-e", "POSTGRES_DB",
        "-e", "POSTGRES_PASSWORD",
        "-p", "127.0.0.1:${pgPort}:5432",
        "-v", "lulu-ai-agent-pgvector-data:/var/lib/postgresql/data",
        "pgvector/pgvector:pg16"
    )
    & $docker.Source @dockerArgs *> $null
    if ($LASTEXITCODE -ne 0) { throw "Failed to create PGVector container." }
}

$running = (& $docker.Source inspect -f "{{.State.Running}}" $containerName 2>$null).Trim()
if ($running -ne "true") {
    & $docker.Source start $containerName *> $null
    if ($LASTEXITCODE -ne 0) { throw "Failed to start $containerName." }
}
$pgReady = $false
$pgUserCheck = if ($config["POSTGRES_USER"]) { $config["POSTGRES_USER"] } else { "lulu_ai_agent" }
$pgDbCheck = if ($config["POSTGRES_DB"]) { $config["POSTGRES_DB"] } else { "lulu_ai_agent" }
for ($i = 0; $i -lt 30; $i++) {
    & $docker.Source exec $containerName pg_isready -U $pgUserCheck -d $pgDbCheck *> $null
    if ($LASTEXITCODE -eq 0) {
        $pgReady = $true
        break
    }
    Start-Sleep -Seconds 1
}
if (-not $pgReady) { throw "PGVector did not become ready." }
Write-Stage "PostgreSQL/PGVector is ready."

Write-Stage "2/6 Checking Ollama qwen3:8b..."
$ollama = Get-Command ollama -ErrorAction SilentlyContinue
if (-not $ollama) { throw "Ollama was not found." }

$ollamaModelsPath = $config["OLLAMA_MODELS"]
if ($ollamaModelsPath) {
    $env:OLLAMA_MODELS = $ollamaModelsPath
}

function Test-Ollama {
    try {
        $null = Invoke-RestMethod -Uri "http://127.0.0.1:11434/api/tags" -TimeoutSec 3
        return $true
    } catch {
        return $false
    }
}

function Start-ManagedOllama {
    Write-Stage "Starting Ollama service..."
    $process = Start-Process -FilePath $ollama.Source -ArgumentList "serve" -WindowStyle Hidden -PassThru
    $process.Id | Set-Content (Join-Path $RunDir "ollama-started.pid")
    $deadline = (Get-Date).AddSeconds(60)
    while (-not (Test-Ollama) -and (Get-Date) -lt $deadline) {
        Start-Sleep -Seconds 1
    }
    if (-not (Test-Ollama)) { throw "Ollama did not become ready." }
}

if (-not (Test-Ollama)) {
    Start-ManagedOllama
}

$ollamaTags = Invoke-RestMethod -Uri "http://127.0.0.1:11434/api/tags" -TimeoutSec 5
$modelNames = @($ollamaTags.models | ForEach-Object { $_.name })

if ($modelNames -notcontains "qwen3:8b" -and $ollamaModelsPath -and (Test-Path $ollamaModelsPath)) {
    $ollamaPid = Get-ListeningPid 11434
    if ($ollamaPid) {
        $proc = Get-CimInstance Win32_Process -Filter "ProcessId=$ollamaPid" -ErrorAction SilentlyContinue
        if ($proc -and ([string]$proc.CommandLine) -match "ollama") {
            Write-Stage "Restarting Ollama with configured model directory..."
            & taskkill.exe /PID $ollamaPid /T /F *> $null
            Start-Sleep -Seconds 2
            Start-ManagedOllama
            $ollamaTags = Invoke-RestMethod -Uri "http://127.0.0.1:11434/api/tags" -TimeoutSec 5
            $modelNames = @($ollamaTags.models | ForEach-Object { $_.name })
        }
    }
}

if ($modelNames -notcontains "qwen3:8b") {
    $hint = if ($ollamaModelsPath) { " Configured OLLAMA_MODELS=$ollamaModelsPath" } else { "" }
    throw "Required local model qwen3:8b is not available.$hint"
}
Write-Stage "Ollama qwen3:8b is ready."

if ($config["MCP_ENABLED"] -eq "true") {
    $imageMcpJar = Join-Path $ImageMcpRoot "target\lulu-image-search-mcp-server-0.0.1-SNAPSHOT.jar"
    if (-not (Test-Path $imageMcpJar)) {
        Write-Stage "Building image-search MCP service..."
        $mvnw = Join-Path $ProjectRoot "mvnw.cmd"
        $imageMcpPom = Join-Path $ImageMcpRoot "pom.xml"
        & $mvnw -f $imageMcpPom -DskipTests package
        if ($LASTEXITCODE -ne 0) { throw "Image-search MCP build failed." }
    }
}

Write-Stage "3/6 Checking Spring Boot backend..."
$backendHealthy = Assert-PortFreeOrHealthy 8123 $BackendUrl "Backend"
$backendPidFile = Join-Path $RunDir "backend.pid"

if ($backendHealthy) {
    $existingBackendPid = Get-ListeningPid 8123
    if ($existingBackendPid) { $existingBackendPid | Set-Content $backendPidFile }
    Write-Stage "Backend is already healthy; reusing it."
} else {
    $javaHome = "D:\Anaconda3\envs\lulu-ai-agent\Library"
    $mvnw = Join-Path $ProjectRoot "mvnw.cmd"
    $backendPom = Join-Path $BackendRoot "pom.xml"
    if (-not (Test-Path (Join-Path $javaHome "bin\java.exe"))) {
        throw "Java 21 environment was not found at $javaHome."
    }
    if (-not (Test-Path $mvnw)) { throw "mvnw.cmd was not found." }
    if (-not (Test-Path $backendPom)) { throw "Backend pom.xml was not found at $backendPom." }

    $env:JAVA_HOME = $javaHome
    $env:Path = "$javaHome\bin;$env:Path"
    $backendOut = Join-Path $LogDir "backend.out.log"
    $backendErr = Join-Path $LogDir "backend.err.log"
    Remove-Item $backendOut, $backendErr -Force -ErrorAction SilentlyContinue

    $backendCommand = "$mvnw -f `"$backendPom`" -DskipTests spring-boot:run"
    $backendProcess = Start-Process -FilePath "cmd.exe" -ArgumentList @("/d", "/s", "/c", $backendCommand) -WorkingDirectory $ProjectRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput $backendOut -RedirectStandardError $backendErr
    $backendProcess.Id | Set-Content $backendPidFile

    if (-not (Wait-Http $BackendUrl 75)) {
        throw "Backend failed its health check. See .run\logs\backend.*.log"
    }
    Write-Stage "Spring Boot backend is ready on port 8123."
}
Write-Stage "4/6 Checking Vite frontend..."
$frontendHealthy = Assert-PortFreeOrHealthy 3000 $FrontendUrl "Frontend"
$frontendPidFile = Join-Path $RunDir "frontend.pid"

if ($frontendHealthy) {
    $existingFrontendPid = Get-ListeningPid 3000
    if ($existingFrontendPid) { $existingFrontendPid | Set-Content $frontendPidFile }
    Write-Stage "Frontend is already healthy; reusing it."
} else {
    $npm = "D:\Anaconda3\envs\lulu-ai-agent\npm.cmd"
    if (-not (Test-Path $npm)) { throw "npm.cmd was not found at $npm." }

    $frontendOut = Join-Path $LogDir "frontend.out.log"
    $frontendErr = Join-Path $LogDir "frontend.err.log"
    Remove-Item $frontendOut, $frontendErr -Force -ErrorAction SilentlyContinue

    $frontendCommand = "$npm run dev -- --host 127.0.0.1"
    $frontendProcess = Start-Process -FilePath "cmd.exe" -ArgumentList @("/d", "/s", "/c", $frontendCommand) -WorkingDirectory $frontendRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput $frontendOut -RedirectStandardError $frontendErr
    $frontendProcess.Id | Set-Content $frontendPidFile

    if (-not (Wait-Http $FrontendUrl 45)) {
        throw "Frontend failed its health check. See .run\logs\frontend.*.log"
    }
    Write-Stage "Vite frontend is ready on port 3000."
}

Write-Stage "5/6 Checking QQ Bot channel..."
$qqEnabled = ($config["QQ_BOT_ENABLED"] -eq "true")
if ($qqEnabled) {
    if (-not $config["QQ_BOT_APP_ID"] -or -not $config["QQ_BOT_APP_SECRET"]) {
        throw "QQ_BOT_ENABLED=true but QQ_BOT_APP_ID / QQ_BOT_APP_SECRET is missing."
    }

    if (-not (Test-QQBotReady)) {
        $qqPortPid = Get-ListeningPid 8131
        if ($qqPortPid) {
            $proc = Get-CimInstance Win32_Process -Filter "ProcessId=$qqPortPid" -ErrorAction SilentlyContinue
            $cmd = if ($proc) { [string]$proc.CommandLine } else { "" }
            if ($cmd -like "*$QQRoot*") {
                & taskkill.exe /PID $qqPortPid /T /F *> $null
                Start-Sleep -Seconds 1
            } else {
                throw "QQ Bot health port 8131 is occupied by PID $qqPortPid."
            }
        }

        $node = "D:\Anaconda3\envs\lulu-ai-agent\node.exe"
        $npm = "D:\Anaconda3\envs\lulu-ai-agent\npm.cmd"
        if (-not (Test-Path $node)) { throw "Node.js was not found at $node." }

        if (-not (Test-Path (Join-Path $qqRoot "node_modules\@tencent-connect\qqbot-nodejs"))) {
            Write-Stage "Installing QQ official SDK dependencies..."
            Push-Location $qqRoot
            & $npm ci
            $npmExit = $LASTEXITCODE
            Pop-Location
            if ($npmExit -ne 0) { throw "QQ Bot npm ci failed." }
        }

        $qqOut = Join-Path $LogDir "qqbot.out.log"
        $qqErr = Join-Path $LogDir "qqbot.err.log"
        Remove-Item $qqOut, $qqErr -Force -ErrorAction SilentlyContinue

        $qqProcess = Start-Process -FilePath $node -ArgumentList @("src\index.js") -WorkingDirectory $qqRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput $qqOut -RedirectStandardError $qqErr
        $qqProcess.Id | Set-Content (Join-Path $RunDir "qqbot.pid")

        $deadline = (Get-Date).AddSeconds(45)
        while (-not (Test-QQBotReady) -and (Get-Date) -lt $deadline) {
            Start-Sleep -Seconds 1
        }
        if (-not (Test-QQBotReady)) {
            throw "QQ Bot failed to become ready. See .run\logs\qqbot.*.log"
        }
    }
    Write-Stage "QQ Bot channel is ready."
} else {
    Write-Stage "QQ Bot is disabled; skipping channel startup."
}

Write-Stage "6/6 All services are ready."
Write-Host ""
Write-Host "lulu-ai-agent is ready:" -ForegroundColor Green
Write-Host "  Frontend : $BrowserUrl"
Write-Host "  Backend  : http://127.0.0.1:8123/api"
Write-Host "  Logs     : $LogDir"
Write-Host ""

if (-not $NoBrowser) {
    Start-Process $BrowserUrl | Out-Null
}
exit 0
