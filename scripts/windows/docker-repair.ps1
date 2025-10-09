# Windows Docker Desktop repair helper
# Usage (from project root or anywhere):
#   powershell -ExecutionPolicy Bypass -File scripts\windows\docker-repair.ps1

Write-Host "[docker-repair] Starting Docker Desktop repair..." -ForegroundColor Cyan

function Test-Command {
  param([string]$Name)
  $null = Get-Command $Name -ErrorAction SilentlyContinue
  return $?
}

# 0) Basic checks
if (-not (Test-Command docker)) {
  Write-Host "[ERROR] 'docker' CLI not found in PATH. Install Docker Desktop: https://www.docker.com/products/docker-desktop/" -ForegroundColor Red
  exit 1
}

# 1) Try to gracefully stop Docker Desktop and backend
Write-Host "[Step 1] Stopping Docker Desktop and backend..." -ForegroundColor Cyan
try {
  $dd = Get-Process -Name "Docker Desktop" -ErrorAction SilentlyContinue
  if ($dd) { Stop-Process -Id $dd.Id -Force -ErrorAction SilentlyContinue }
} catch {}
try {
  $backend = Get-Process -Name "com.docker.backend" -ErrorAction SilentlyContinue
  if ($backend) { Stop-Process -Id $backend.Id -Force -ErrorAction SilentlyContinue }
} catch {}

Start-Sleep -Seconds 2

# 2) Shutdown WSL and restart LxssManager service
Write-Host "[Step 2] Shutting down WSL and restarting LxssManager..." -ForegroundColor Cyan
try { wsl --shutdown } catch {}
try {
  $svc = Get-Service -Name LxssManager -ErrorAction SilentlyContinue
  if ($svc) {
    if ($svc.Status -eq 'Running') { Restart-Service -Name LxssManager -Force -ErrorAction SilentlyContinue }
    else { Start-Service -Name LxssManager -ErrorAction SilentlyContinue }
  }
} catch {}

Start-Sleep -Seconds 2

# 3) Start Docker Desktop (user scope)
Write-Host "[Step 3] Starting Docker Desktop..." -ForegroundColor Cyan
$dockerDesktopPath = "$Env:ProgramFiles\\Docker\\Docker\\Docker Desktop.exe"
if (-not (Test-Path $dockerDesktopPath)) {
  # Sometimes installed under user profile
  $dockerDesktopPath = "$Env:LocalAppData\\Docker\\Docker\\Docker Desktop.exe"
}
if (-not (Test-Path $dockerDesktopPath)) {
  Write-Host "[ERROR] Docker Desktop executable not found. Reinstall Docker Desktop." -ForegroundColor Red
  exit 2
}

Start-Process -FilePath $dockerDesktopPath | Out-Null

# 4) Wait for engine to come up and respond to 'docker info'
Write-Host "[Step 4] Waiting for Docker engine to become ready (up to 90s)..." -ForegroundColor Cyan
$maxWait = 90
$ok = $false
for ($i = 0; $i -lt $maxWait; $i += 5) {
  Start-Sleep -Seconds 5
  try {
    $info = docker info 2>$null
    if ($LASTEXITCODE -eq 0 -and $info) { $ok = $true; break }
  } catch {}
}

if (-not $ok) {
  Write-Host "[ERROR] Docker engine did not become ready." -ForegroundColor Red
  Write-Host "Next steps:" -ForegroundColor Yellow
  Write-Host "- Open Docker Desktop UI and check for errors (Settings -> Troubleshoot)."
  Write-Host "- Ensure Windows features are enabled (run in Admin PowerShell):" -ForegroundColor Yellow
  Write-Host "  dism /online /enable-feature /featurename:VirtualMachinePlatform /all /norestart"
  Write-Host "  dism /online /enable-feature /featurename:Microsoft-Windows-Subsystem-Linux /all /norestart"
  Write-Host "- Reboot the PC after enabling features."
  Write-Host "- In Docker Desktop -> Settings -> Resources -> WSL integration: enable your default distro."
  Write-Host "- Try: 'wsl --status' and 'wsl --shutdown' then start Docker Desktop again."
  Write-Host "- If still failing, use Docker Desktop Troubleshoot -> Reset to factory defaults (last resort)."
  exit 3
}

Write-Host "[SUCCESS] Docker engine is running: $(docker --version)" -ForegroundColor Green
Write-Host "You can now run your stack:" -ForegroundColor Cyan
Write-Host "  mvn -q -DskipTests package"
Write-Host "  docker compose up --build -d"
Write-Host "Verify with: docker compose ps"