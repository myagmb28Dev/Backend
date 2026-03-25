param(
    [int]$Port = 8080
)

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path -Parent $scriptDir

Remove-Item Env:FIREBASE_AUTH_EMULATOR_HOST -ErrorAction SilentlyContinue
Remove-Item Env:FIREBASE_ALLOW_AUTH_EMULATOR -ErrorAction SilentlyContinue

Write-Host "Starting backend in real Firebase mode..." -ForegroundColor Green
Write-Host "Auth Emulator host override has been cleared for this session." -ForegroundColor Yellow
Write-Host "Backend port: $Port"
Write-Host ""
Write-Host "This mode expects a real Firebase ID token from the same project as your service account." -ForegroundColor Yellow
Write-Host ""

& "$repoRoot\\gradlew.bat" bootRun
