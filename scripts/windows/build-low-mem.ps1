# Low-memory sequential build for Windows
# Usage: Right-click -> Run with PowerShell, or:
#   powershell -ExecutionPolicy Bypass -File scripts\windows\build-low-mem.ps1

$ErrorActionPreference = 'Stop'

Write-Host "[low-mem] Setting low-memory Maven JVM options" -ForegroundColor Cyan
$env:MAVEN_OPTS = "-Xmx256m -XX:+UseSerialGC -XX:MaxMetaspaceSize=128m -XX:MaxDirectMemorySize=64m"

function Build-Module([string]$module) {
  Write-Host "[low-mem] Building module: $module" -ForegroundColor Yellow
  & mvn -q -DskipTests -pl $module -am package
}

# Build modules one-by-one to minimize peak memory usage
Build-Module "common"
Build-Module "booking-service"
Build-Module "profile-service"
Build-Module "payment-service"
Build-Module "assistant-service"

Write-Host "[low-mem] Build completed" -ForegroundColor Green
