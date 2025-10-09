# Run from project root. This helper checks Docker & Ollama, builds jars, and starts Docker Compose.
# Usage:
#   powershell -ExecutionPolicy Bypass -File scripts\windows\compose-up.ps1

Write-Host "[compose-up] Starting..." -ForegroundColor Cyan

function Test-Command {
  param([string]$Name)
  $null = Get-Command $Name -ErrorAction SilentlyContinue
  return $?
}

# 1) Check Docker CLI
if (-not (Test-Command docker)) {
  Write-Host "[ERROR] 'docker' CLI not found in PATH. Install/Start Docker Desktop and reopen PowerShell." -ForegroundColor Red
  exit 1
}

# 2) Check Docker engine
$engineOk = $false
try {
  $info = docker info 2>$null
  if ($LASTEXITCODE -eq 0 -and $info) { $engineOk = $true }
} catch {}
if (-not $engineOk) {
  Write-Host "[ERROR] Docker engine is not running. Start Docker Desktop (status 'Running') and retry." -ForegroundColor Red
  exit 2
}

# 3) Optional: inform about Ollama default port
try {
  $conns = Get-NetTCPConnection -LocalPort 11434 -State Listen -ErrorAction SilentlyContinue
  if ($conns) {
    Write-Host "[INFO] Ollama default port 11434 is in use (likely Ollama is already running)." -ForegroundColor DarkCyan
  } else {
    Write-Host "[WARN] Port 11434 not listening. If you plan to use assistant-service with Ollama on host, run 'ollama serve' or adjust OLLAMA_BASE_URL." -ForegroundColor Yellow
  }
} catch {}

# 4) Build jars
Write-Host "[compose-up] Building project jars with Maven..." -ForegroundColor Cyan
if (-not (Test-Command mvn)) {
  Write-Host "[ERROR] 'mvn' not found. Install Maven 3.9+ or use Maven Wrapper if available." -ForegroundColor Red
  exit 3
}
& mvn -q -DskipTests package
if ($LASTEXITCODE -ne 0) {
  Write-Host "[ERROR] Maven build failed." -ForegroundColor Red
  exit 4
}

# 5) Start Docker compose
Write-Host "[compose-up] Starting Docker Compose (build + detach)..." -ForegroundColor Cyan
& docker compose up --build -d
if ($LASTEXITCODE -ne 0) {
  Write-Host "[ERROR] docker compose failed to start. Check Docker Desktop or run 'docker compose ps' and 'docker compose logs'." -ForegroundColor Red
  exit 5
}

# 6) Show status and health URLs
Write-Host "[compose-up] Stack started. Current services:" -ForegroundColor Green
& docker compose ps

Write-Host "\nHealth endpoints:" -ForegroundColor Cyan
Write-Host "- Booking:   http://localhost:18081/actuator/health"
Write-Host "- Payment:   http://localhost:18082/actuator/health"
Write-Host "- Assistant: http://localhost:18090/actuator/health"

Write-Host "\n[compose-up] Done." -ForegroundColor Cyan
