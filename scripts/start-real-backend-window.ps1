param(
    [int]$Port = 8080
)

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path -Parent $scriptDir

try {
    $host.UI.RawUI.WindowTitle = "Pogun Backend Real"
} catch {
}

Set-Location $repoRoot

Remove-Item Env:FIREBASE_AUTH_EMULATOR_HOST -ErrorAction SilentlyContinue
Remove-Item Env:APP_FIREBASE_AUTH_EMULATOR_HOST -ErrorAction SilentlyContinue

$env:SPRING_PROFILES_ACTIVE = "prod"
$env:APP_FIREBASE_AUTH_MODE = "PRODUCTION"
$env:APP_FIREBASE_AUTH_ALLOW_EMULATOR = "false"
$env:PORT = "$Port"

# Overrides for testing with real .env
# REDIS_HOST will be loaded from .env due to 'prod' profile

& ".\gradlew.bat" bootRun
