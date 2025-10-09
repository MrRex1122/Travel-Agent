# Requires: Windows PowerShell 5+ or PowerShell 7+
# Run from project root or anywhere: 
#   powershell -ExecutionPolicy Bypass -File scripts\windows\dev-check.ps1

Write-Host "[Dev Check] Starting environment diagnostics..." -ForegroundColor Cyan

function Test-Command {
  param([string]$Name)
  $null = Get-Command $Name -ErrorAction SilentlyContinue
  return $?
}

# 1) Docker CLI present?
if (-not (Test-Command docker)) {
  Write-Host "[ERROR] 'docker' CLI not found in PATH." -ForegroundColor Red
  Write-Host "        Install/Start Docker Desktop and reopen PowerShell."
} else {
  Write-Host "[OK] docker CLI available: $(docker --version)" -ForegroundColor Green
  # 1a) Engine running?
  try {
    $info = docker info 2>$null
    if ($LASTEXITCODE -ne 0 -or -not $info) {
      Write-Host "[ERROR] Docker engine is not running (cannot connect)." -ForegroundColor Red
      Write-Host "        Start Docker Desktop and wait until status is 'Running'."
    } else {
      Write-Host "[OK] Docker engine is running." -ForegroundColor Green
    }
  } catch {
    Write-Host "[ERROR] Docker engine check failed: $($_.Exception.Message)" -ForegroundColor Red
  }

  # 1b) docker compose available?
  if (-not (Test-Command "docker")) {
    Write-Host "[WARN] Could not verify 'docker compose' subcommand." -ForegroundColor Yellow
  } else {
    try {
      $dcv = docker compose version 2>$null
      if ($LASTEXITCODE -eq 0) {
        Write-Host "[OK] docker compose available: $dcv" -ForegroundColor Green
      } else {
        Write-Host "[WARN] 'docker compose' not available. Make sure Docker Desktop uses new Compose V2." -ForegroundColor Yellow
      }
    } catch {
      Write-Host "[WARN] 'docker compose' check failed: $($_.Exception.Message)" -ForegroundColor Yellow
    }
  }
}

# 2) Ollama CLI present?
if (-not (Test-Command ollama)) {
  Write-Host "[WARN] 'ollama' CLI not found. Install from https://ollama.com and add to PATH." -ForegroundColor Yellow
} else {
  Write-Host "[OK] ollama CLI available: $(ollama --version)" -ForegroundColor Green
}

# 3) Port 11434 usage (Ollama default)
try {
  $conns = Get-NetTCPConnection -LocalPort 11434 -State Listen -ErrorAction SilentlyContinue
  if ($conns) {
    $pids = $conns | Select-Object -ExpandProperty OwningProcess -Unique
    foreach ($pid in $pids) {
      try { $proc = Get-Process -Id $pid -ErrorAction SilentlyContinue } catch {}
      if ($proc) {
        Write-Host "[INFO] Port 11434 is in use by PID=$($proc.Id) Name=$($proc.ProcessName)." -ForegroundColor Cyan
      } else {
        Write-Host "[INFO] Port 11434 is in use by PID=$pid (process info unavailable)." -ForegroundColor Cyan
      }
    }
    Write-Host "[NOTE] If you saw 'Only one usage of each socket address' from 'ollama serve', an Ollama server is already running. Do NOT start a second one." -ForegroundColor DarkCyan
  } else {
    Write-Host "[OK] Port 11434 is free." -ForegroundColor Green
  }
} catch {
  Write-Host "[WARN] Could not check port 11434: $($_.Exception.Message)" -ForegroundColor Yellow
}

Write-Host "\nNext steps:" -ForegroundColor Cyan
Write-Host "1) If Docker engine was not running: Start Docker Desktop, then reopen PowerShell." 
Write-Host "2) Build JARs: mvn -q -DskipTests package"
Write-Host "3) Start stack: docker compose up --build -d"
Write-Host "4) Health: http://localhost:18081/actuator/health (booking), http://localhost:18082/actuator/health (payment), http://localhost:18090/actuator/health (assistant)"
Write-Host "5) If assistant can’t reach Ollama in Docker, ensure OLLAMA_BASE_URL is set to http://host.docker.internal:11434 (already in docker-compose)."

Write-Host "\n[Dev Check] Finished." -ForegroundColor Cyan
