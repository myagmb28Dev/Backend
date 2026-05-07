param(
    [ValidateSet("local", "real")]
    [string]$Mode = "local",
    [string]$ProjectId = "pogun-local",
    [string]$Email = "playwright-user1@local.dev",
    [string]$Password = "Test1234!",
    [int]$AuthPort = 9099,
    [int]$BackendPort = 8081,
    [int]$UiPort = 4000,
    [int]$TimeoutSeconds = 180,
    [string]$SpringProfile = "",
    [switch]$ElevateGradle,
    [switch]$NoDocker,
    [string[]]$DockerServices = @("redis"),
    [switch]$NoAutoOpen,
    [switch]$NoKeepAlive
)

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path -Parent $scriptDir
. (Join-Path $scriptDir "lib\PogunScriptCommon.ps1")

$localDir = Join-Path $repoRoot ".local"
New-Item -ItemType Directory -Force -Path $localDir | Out-Null

function Get-ExecutablePath {
    param([string[]]$Names)
    foreach ($name in $Names) {
        $cmd = Get-Command $name -ErrorAction SilentlyContinue
        if ($cmd) {
            return $cmd.Source
        }
    }
    throw "Required command not found: $($Names -join ', ')"
}

function Import-EnvFile {
    param([string]$Path)
    if (-not (Test-Path $Path)) {
        return
    }
    Get-Content $Path | ForEach-Object {
        $line = $_.Trim()
        if ([string]::IsNullOrWhiteSpace($line)) { return }
        if ($line.StartsWith("#")) { return }
        $idx = $line.IndexOf("=")
        if ($idx -lt 1) { return }
        $key = $line.Substring(0, $idx).Trim()
        $value = $line.Substring($idx + 1)
        if ([string]::IsNullOrWhiteSpace($key)) { return }
        [Environment]::SetEnvironmentVariable($key, $value, "Process")
    }
}

function Resolve-DockerComposeCommand {
    $docker = Get-Command docker -ErrorAction SilentlyContinue
    if ($docker) {
        try {
            & $docker.Source compose version *> $null
            if ($LASTEXITCODE -eq 0) {
                return @($docker.Source, "compose")
            }
        } catch {
        }
    }
    $dockerCompose = Get-Command docker-compose -ErrorAction SilentlyContinue
    if ($dockerCompose) {
        return @($dockerCompose.Source)
    }
    throw "Docker Compose command not found. Install Docker Desktop or docker-compose."
}

function Ensure-ExistingContainerStarted {
    param([string]$ContainerName)
    $docker = Get-Command docker -ErrorAction SilentlyContinue
    if (-not $docker) {
        return $false
    }
    $nameFilter = "^/$ContainerName$"
    $existingId = (& $docker.Source ps -a --filter "name=$nameFilter" --format "{{.ID}}" | Select-Object -First 1)
    if ([string]::IsNullOrWhiteSpace($existingId)) {
        return $false
    }
    $runningId = (& $docker.Source ps --filter "name=$nameFilter" --format "{{.ID}}" | Select-Object -First 1)
    if (-not [string]::IsNullOrWhiteSpace($runningId)) {
        Write-Host "Reusing running container: $ContainerName"
        return $true
    }
    Write-Host "Starting existing container: $ContainerName"
    & $docker.Source start $ContainerName | Out-Null
    return $LASTEXITCODE -eq 0
}

function Start-DockerServices {
    param(
        [string[]]$Services,
        [string]$ComposeFilePath,
        [string]$EnvFilePath,
        [int]$TimeoutSec
    )

    if (-not $Services -or $Services.Count -eq 0) {
        return
    }

    $composeCmd = Resolve-DockerComposeCommand
    $serviceArgs = @()
    foreach ($svc in $Services) {
        if (-not [string]::IsNullOrWhiteSpace($svc)) {
            $serviceArgs += $svc.Trim()
        }
    }
    if ($serviceArgs -contains "redis") {
        if (Ensure-ExistingContainerStarted -ContainerName "pogun-redis") {
            $serviceArgs = @($serviceArgs | Where-Object { $_ -ne "redis" })
        }
    }
    if ($serviceArgs.Count -eq 0) {
        if ($Services -contains "redis") {
            Wait-TcpPort -Port 6379 -Name "Docker Redis" -TimeoutSec $TimeoutSec
        }
        return
    }

    Write-Host "Starting Docker services: $($serviceArgs -join ', ')"
    $upArgs = @()
    if ($composeCmd.Count -eq 2) {
        $upArgs += $composeCmd[1]
    }
    $upArgs += @("-f", $ComposeFilePath)
    if (-not [string]::IsNullOrWhiteSpace($EnvFilePath) -and (Test-Path $EnvFilePath)) {
        $upArgs += @("--env-file", $EnvFilePath)
    }
    $upArgs += @("up", "-d")
    $upArgs += $serviceArgs
    & $composeCmd[0] @upArgs
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose up failed with exit code $LASTEXITCODE"
    }

    if ($serviceArgs -contains "redis") {
        Wait-TcpPort -Port 6379 -Name "Docker Redis" -TimeoutSec $TimeoutSec
    }
}

function Test-TcpPort {
    param([int]$Port)

    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $async = $client.BeginConnect("127.0.0.1", $Port, $null, $null)
        if (-not $async.AsyncWaitHandle.WaitOne(500, $false)) {
            return $false
        }
        $client.EndConnect($async) | Out-Null
        return $true
    } catch {
        return $false
    } finally {
        $client.Close()
    }
}

function Wait-TcpPort {
    param(
        [int]$Port,
        [string]$Name,
        [int]$TimeoutSec,
        [System.Diagnostics.Process]$Process = $null
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        if (Test-TcpPort -Port $Port) {
            return
        }
        if ($Process -and $Process.HasExited) {
            throw "$Name process exited before port $Port became ready. Check the visible $Name window."
        }
        Start-Sleep -Seconds 1
    }
    throw "$Name did not become ready on port $Port within $TimeoutSec seconds."
}

function Wait-BackendHealth {
    param(
        [int]$Port,
        [int]$TimeoutSec,
        [System.Diagnostics.Process]$Process = $null
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        try {
            $response = Invoke-RestMethod -Method Get -Uri "http://127.0.0.1:$Port/health" -TimeoutSec 3
            if ($response.status -eq 200 -or $response.ok -eq $true) {
                return
            }
        } catch {
        }
        if ($Process -and $Process.HasExited) {
            throw "Spring Boot backend process exited before health became ready. Check the visible backend window."
        }
        Start-Sleep -Seconds 2
    }
    throw "Spring Boot backend health did not become ready on port $Port within $TimeoutSec seconds."
}

function Ensure-PortReadyWithRetry {
    param(
        [int]$Port,
        [string]$Name,
        [int]$TimeoutSec,
        [scriptblock]$StartAction,
        [int]$MaxAttempts = 2
    )

    for ($attempt = 1; $attempt -le $MaxAttempts; $attempt++) {
        $startedProcess = $null
        if (-not (Test-TcpPort -Port $Port)) {
            $startedProcess = & $StartAction
        }
        try {
            Wait-TcpPort -Port $Port -Name $Name -TimeoutSec $TimeoutSec -Process $startedProcess
            return
        } catch {
            if ($attempt -ge $MaxAttempts) {
                throw
            }
            Write-Warning "$Name startup attempt $attempt failed. Retrying..."
        }
    }
}

function Start-DetachedPowerShell {
    param(
        [string]$Title,
        [string]$CommandText,
        [switch]$RunAsAdmin
    )

    $powershellExe = Join-Path $env:WINDIR "System32\\WindowsPowerShell\\v1.0\\powershell.exe"
    $fullCommand = "`$host.UI.RawUI.WindowTitle = '$Title'; `$OutputEncoding = [Console]::OutputEncoding = [System.Text.UTF8Encoding]::new(); chcp 65001 > `$null; $CommandText"
    $startParams = @{
        FilePath = $powershellExe
        WorkingDirectory = $repoRoot
        WindowStyle = "Hidden"
        PassThru = $true
        ArgumentList = @(
        "-NoProfile",
        "-ExecutionPolicy", "Bypass",
        "-Command",
        $fullCommand
        )
    }
    if ($RunAsAdmin) {
        $startParams["Verb"] = "RunAs"
    }
    return Start-Process @startParams
}

function Stop-ProcessesListeningOnPort {
    param(
        [int]$Port,
        [string]$Name = "service"
    )

    $listeners = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
    if (-not $listeners) {
        return
    }

    $owningProcessIds = @($listeners | Select-Object -ExpandProperty OwningProcess -Unique)
    foreach ($procId in $owningProcessIds) {
        if (-not $procId) { continue }
        try {
            $proc = Get-Process -Id $procId -ErrorAction Stop
            Write-Host "Stopping existing $Name process on port ${Port}: $($proc.ProcessName) (PID $procId)"
            Stop-Process -Id $procId -Force -ErrorAction Stop
        } catch {
            Write-Warning "Failed to stop PID $procId on port ${Port}: $($_.Exception.Message)"
        }
    }

    $deadline = (Get-Date).AddSeconds(20)
    while ((Get-Date) -lt $deadline) {
        if (-not (Test-TcpPort -Port $Port)) {
            return
        }
        Start-Sleep -Milliseconds 300
    }
    throw "Port $Port is still in use after stopping existing $Name process(es)."
}

function Get-HttpErrorText {
    param([System.Management.Automation.ErrorRecord]$ErrorRecord)

    if ($ErrorRecord.ErrorDetails -and $ErrorRecord.ErrorDetails.Message) {
        return $ErrorRecord.ErrorDetails.Message
    }

    $response = $ErrorRecord.Exception.Response
    if ($response -and $response.GetResponseStream()) {
        $reader = New-Object System.IO.StreamReader($response.GetResponseStream())
        try {
            return $reader.ReadToEnd()
        } finally {
            $reader.Close()
        }
    }
    return $ErrorRecord.Exception.Message
}

function Invoke-AuthEmulatorRequest {
    param(
        [string]$Path,
        [hashtable]$Body
    )
    $uri = "http://127.0.0.1:$AuthPort$Path"
    return Invoke-RestMethod -Method Post -Uri $uri -ContentType "application/json" -Body ($Body | ConvertTo-Json)
}

function Invoke-BackendRequest {
    param(
        [string]$Path,
        [string]$Method = "Post",
        [object]$Body = $null,
        [string]$BearerToken = $null
    )

    $uri = "http://127.0.0.1:$BackendPort$Path"
    $headers = @{}
    if ($BearerToken) {
        $headers["Authorization"] = "Bearer $BearerToken"
    }
    $params = @{
        Method = $Method
        Uri = $uri
        Headers = $headers
    }
    if ($null -ne $Body) {
        $params["ContentType"] = "application/json"
        $params["Body"] = ($Body | ConvertTo-Json)
    }
    return Invoke-RestMethod @params
}

function Ensure-EmulatorUserAndGetToken {
    param(
        [string]$TargetEmail,
        [string]$TargetPassword
    )

    $authBody = @{
        email = $TargetEmail
        password = $TargetPassword
        returnSecureToken = $true
    }

    try {
        return Invoke-AuthEmulatorRequest -Path "/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=fake-api-key" -Body $authBody
    } catch {
        $errorText = Get-HttpErrorText -ErrorRecord $_
        if ($errorText -notmatch "EMAIL_NOT_FOUND") {
            throw
        }
    }

    try {
        Invoke-AuthEmulatorRequest -Path "/identitytoolkit.googleapis.com/v1/accounts:signUp?key=fake-api-key" -Body $authBody | Out-Null
    } catch {
        $errorText = Get-HttpErrorText -ErrorRecord $_
        if ($errorText -notmatch "EMAIL_EXISTS") {
            throw
        }
    }

    return Invoke-AuthEmulatorRequest -Path "/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=fake-api-key" -Body $authBody
}

function Ensure-EmulatorEmailVerified {
    param(
        [string]$TargetEmail,
        [string]$Token
    )

    $lookup = Invoke-AuthEmulatorRequest -Path "/identitytoolkit.googleapis.com/v1/accounts:lookup?key=fake-api-key" -Body @{ idToken = $Token }
    if ($lookup.users -and $lookup.users.Count -gt 0 -and $lookup.users[0].emailVerified -eq $true) {
        return
    }

    Invoke-AuthEmulatorRequest -Path "/identitytoolkit.googleapis.com/v1/accounts:sendOobCode?key=fake-api-key" -Body @{ requestType = "VERIFY_EMAIL"; idToken = $Token } | Out-Null
    $oobResponse = Invoke-RestMethod -Method Get -Uri "http://127.0.0.1:$AuthPort/emulator/v1/projects/$ProjectId/oobCodes"
    $oobCodes = @($oobResponse.oobCodes)
    $latestVerifyCode = $oobCodes |
        Where-Object { $_.email -eq $TargetEmail -and $_.requestType -eq "VERIFY_EMAIL" } |
        Select-Object -Last 1

    if (-not $latestVerifyCode -or -not $latestVerifyCode.oobLink) {
        throw "Failed to resolve VERIFY_EMAIL oobLink for $TargetEmail"
    }

    Invoke-WebRequest -Uri $latestVerifyCode.oobLink -UseBasicParsing | Out-Null
    $afterLookup = Invoke-AuthEmulatorRequest -Path "/identitytoolkit.googleapis.com/v1/accounts:lookup?key=fake-api-key" -Body @{ idToken = $Token }
    if (-not ($afterLookup.users -and $afterLookup.users.Count -gt 0 -and $afterLookup.users[0].emailVerified -eq $true)) {
        throw "Email verification did not complete for $TargetEmail"
    }
}

function Ensure-BackendUserReady {
    param([string]$Token)

    $loginResponse = Invoke-BackendRequest -Path "/api/auth/login" -Method "Post" -Body @{ firebaseIdToken = $Token }
    $registrationStatus = [string]$loginResponse.data.registrationStatus
    $userId = $loginResponse.data.id
    if ($userId -or $registrationStatus -ne "PENDING_ONBOARDING") {
        return $loginResponse
    }

    Invoke-BackendRequest -Path "/api/auth/onboarding/complete" -Method "Post" -Body @{ x = 127.1086228; y = 37.4012191 } -BearerToken $Token | Out-Null
    return Invoke-BackendRequest -Path "/api/auth/login" -Method "Post" -Body @{ firebaseIdToken = $Token }
}

function Bootstrap-LocalAdmin {
    param([string]$TargetEmail)
    return Invoke-BackendRequest -Path "/api/admin/auth/local/login" -Method "Post" -Body @{
        localTestEmail = $TargetEmail
        forcePasskeyEnroll = $true
    }
}

function Write-TokenArtifacts {
    param(
        [string]$Token,
        [string]$RefreshToken,
        [string]$TokenFileName,
        [string]$HeaderFileName,
        [string]$LoginBodyFileName,
        [string]$RefreshTokenFileName
    )

    $tokenPath = Join-Path $localDir $TokenFileName
    $authHeaderPath = Join-Path $localDir $HeaderFileName
    $loginBodyPath = Join-Path $localDir $LoginBodyFileName
    $refreshTokenPath = Join-Path $localDir $RefreshTokenFileName

    [System.IO.File]::WriteAllText($tokenPath, $Token, [System.Text.UTF8Encoding]::new($false))
    [System.IO.File]::WriteAllText($authHeaderPath, "Authorization: Bearer $Token", [System.Text.UTF8Encoding]::new($false))
    [System.IO.File]::WriteAllText($loginBodyPath, "{`n  `"firebaseIdToken`": `"$Token`"`n}", [System.Text.UTF8Encoding]::new($false))
    [System.IO.File]::WriteAllText($refreshTokenPath, $RefreshToken, [System.Text.UTF8Encoding]::new($false))
}

if ([string]::IsNullOrWhiteSpace($SpringProfile)) {
    if ($Mode -eq "local") {
        $SpringProfile = "local"
    } else {
        $SpringProfile = "prod"
    }
}

$gradleUserHome = Resolve-PogunGradleUserHome -RepoRoot $repoRoot

if ($Mode -eq "local") {
    $localEnvPath = Join-Path $repoRoot ".env.local"
    $baseEnvPath = Join-Path $repoRoot ".env"
    if (Test-Path $localEnvPath) {
        Import-EnvFile -Path $localEnvPath
    } elseif (Test-Path $baseEnvPath) {
        Import-EnvFile -Path $baseEnvPath
    }
}

if (-not $NoDocker -and $Mode -eq "local") {
    $composePath = Join-Path $repoRoot "docker-compose.yml"
    $composeEnvPath = Join-Path $repoRoot ".env.local"
    if (-not (Test-Path $composeEnvPath)) {
        $composeEnvPath = Join-Path $repoRoot ".env"
    }
    if (Test-Path $composePath) {
        Start-DockerServices -Services $DockerServices -ComposeFilePath $composePath -EnvFilePath $composeEnvPath -TimeoutSec $TimeoutSeconds
    } else {
        Write-Warning "docker-compose.yml not found at $composePath. Skipping docker startup."
    }
}

if ($Mode -eq "real") {
    Set-PogunWindowTitle -Title "Pogun Backend Real"
    Set-Location $repoRoot
    Remove-Item Env:FIREBASE_AUTH_EMULATOR_HOST -ErrorAction SilentlyContinue
    Remove-Item Env:APP_FIREBASE_AUTH_EMULATOR_HOST -ErrorAction SilentlyContinue

    $env:SPRING_PROFILES_ACTIVE = $SpringProfile
    $env:APP_FIREBASE_AUTH_MODE = "PRODUCTION"
    $env:APP_FIREBASE_AUTH_ALLOW_EMULATOR = "false"
    $env:PORT = "$BackendPort"
    $env:GRADLE_USER_HOME = $gradleUserHome

    & ".\gradlew.bat" bootRun
    exit $LASTEXITCODE
}

$xdgConfigDir = Join-Path $localDir "xdg"
if ([string]::IsNullOrWhiteSpace($env:XDG_CONFIG_HOME)) {
    $env:XDG_CONFIG_HOME = $xdgConfigDir
}
New-Item -ItemType Directory -Force -Path $env:XDG_CONFIG_HOME | Out-Null

$firebaseExe = Get-ExecutablePath -Names @("firebase.cmd")

Ensure-PortReadyWithRetry -Port $AuthPort -Name "Firebase Auth Emulator" -TimeoutSec $TimeoutSeconds -StartAction {
    $emulatorCommand = @(
        "`$env:XDG_CONFIG_HOME = '$($env:XDG_CONFIG_HOME)'",
        "& `"$firebaseExe`" emulators:start --only auth --project $ProjectId"
    ) -join "; "
    Start-DetachedPowerShell -Title "Pogun Auth Emulator" -CommandText $emulatorCommand
}

Stop-ProcessesListeningOnPort -Port $BackendPort -Name "backend"

$backendCommand = @(
    "`$env:PORT = '$BackendPort'",
    "`$env:SERVER_PORT = '$BackendPort'",
    "`$env:GRADLE_USER_HOME = '$gradleUserHome'",
    "`$env:SPRING_PROFILES_ACTIVE = '$SpringProfile'",
    "`$env:APP_FIREBASE_AUTH_MODE = 'EMULATOR'",
    "`$env:APP_FIREBASE_AUTH_ALLOW_EMULATOR = 'true'",
    "`$env:APP_FIREBASE_AUTH_EMULATOR_PROJECT_ID = '$ProjectId'",
    "`$env:FIREBASE_PROJECT_ID = '$ProjectId'",
    "`$env:GCLOUD_PROJECT = '$ProjectId'",
    "`$env:FIREBASE_AUTH_EMULATOR_HOST = '127.0.0.1:$AuthPort'",
    "`$env:FIREBASE_ALLOW_AUTH_EMULATOR = 'true'",
    "`$env:SPRING_WEB_RESOURCES_STATIC_LOCATIONS = 'file:$($repoRoot.Replace('\', '/'))/src/main/resources/static/,classpath:/static/'",
    "`$env:SPRING_WEB_RESOURCES_CACHE_PERIOD = '0'",
    "`$env:SPRING_WEB_RESOURCES_CACHE_CACHECONTROL_NO_STORE = 'true'",
    "`$env:APP_ADMIN_WEBAUTHN_RP_ID = 'localhost'",
    "`$env:APP_ADMIN_WEBAUTHN_RP_NAME = 'Pogun Admin Local'",
    "`$env:APP_ADMIN_WEBAUTHN_ALLOWED_ORIGINS = 'http://localhost:$BackendPort,http://127.0.0.1:$BackendPort,http://localhost:8080,http://127.0.0.1:8080'",
    "& '.\\gradlew.bat' bootRun"
) -join "; "

$backendProcess = Start-DetachedPowerShell -Title "Pogun Backend Local" -CommandText $backendCommand -RunAsAdmin:$ElevateGradle

Wait-BackendHealth -Port $BackendPort -TimeoutSec $TimeoutSeconds -Process $backendProcess

$authTokenResponse = Ensure-EmulatorUserAndGetToken -TargetEmail $Email -TargetPassword $Password
$idToken = $authTokenResponse.idToken
$refreshToken = $authTokenResponse.refreshToken
Ensure-EmulatorEmailVerified -TargetEmail $Email -Token $idToken

$playwrightAccounts = @(
    @{ Index = 1; Email = "playwright-user1@local.dev"; Password = "Test1234!" },
    @{ Index = 2; Email = "playwright-user2@local.dev"; Password = "Test1234!" },
    @{ Index = 3; Email = "playwright-user3@local.dev"; Password = "Test1234!" },
    @{ Index = 4; Email = "playwright-user4@local.dev"; Password = "Test1234!" }
)

$legacyFiles = @(
    (Join-Path $localDir "firebase-id-token.txt"),
    (Join-Path $localDir "authorization-header.txt"),
    (Join-Path $localDir "login-request.json")
)
foreach ($legacyFile in $legacyFiles) {
    if (Test-Path $legacyFile) {
        Remove-Item $legacyFile -Force
    }
}

Write-TokenArtifacts -Token $idToken -RefreshToken $refreshToken -TokenFileName "emulator-firebase-id-token.txt" -HeaderFileName "emulator-authorization-header.txt" -LoginBodyFileName "emulator-login-request.json" -RefreshTokenFileName "emulator-refresh-token.txt"

foreach ($account in $playwrightAccounts) {
    $accountTokenResponse = Ensure-EmulatorUserAndGetToken -TargetEmail $account.Email -TargetPassword $account.Password
    $accountToken = $accountTokenResponse.idToken
    $accountRefreshToken = $accountTokenResponse.refreshToken
    if ($account.Index -eq 1) {
        Ensure-EmulatorEmailVerified -TargetEmail $account.Email -Token $accountToken
    }
    try {
        Ensure-BackendUserReady -Token $accountToken | Out-Null
    } catch {
        Write-Warning "Backend bootstrap failed for $($account.Email). Continuing without onboarding bootstrap."
    }
    if ($account.Index -eq 1) {
        try {
            Bootstrap-LocalAdmin -TargetEmail $account.Email | Out-Null
        } catch {
            Write-Warning "Local admin bootstrap failed for $($account.Email). Continuing without admin bootstrap."
        }
    }
    Write-TokenArtifacts `
        -Token $accountToken `
        -RefreshToken $accountRefreshToken `
        -TokenFileName ("emulator-user{0}-firebase-id-token.txt" -f $account.Index) `
        -HeaderFileName ("emulator-user{0}-authorization-header.txt" -f $account.Index) `
        -LoginBodyFileName ("emulator-user{0}-login-request.json" -f $account.Index) `
        -RefreshTokenFileName ("emulator-user{0}-refresh-token.txt" -f $account.Index)
}

$tokenPath = Join-Path $localDir "emulator-firebase-id-token.txt"
$authHeaderPath = Join-Path $localDir "emulator-authorization-header.txt"
$loginBodyPath = Join-Path $localDir "emulator-login-request.json"

Write-Host ""
Write-Host "Local auth emulator stack is ready." -ForegroundColor Green
Write-Host "These token files are emulator-only. Do not use them against real Firebase mode or deployed servers." -ForegroundColor Yellow
Write-Host "Project       : $ProjectId"
Write-Host "Auth Emulator : http://127.0.0.1:$AuthPort"
Write-Host "Emulator UI   : http://127.0.0.1:$UiPort"
Write-Host "Backend       : http://localhost:$BackendPort"
Write-Host ""
Write-Host "Email         : $Email"
Write-Host "Token file    : $tokenPath"
Write-Host "Header file   : $authHeaderPath"
Write-Host "Login body    : $loginBodyPath"
Write-Host ""
Write-Host "Login payload values are stored in the local files above and are not echoed to the terminal." -ForegroundColor Yellow

if (-not $NoAutoOpen) {
    try {
        Start-Process "http://localhost:$BackendPort/Full_Compact.html" | Out-Null
        Write-Host "Opened: http://localhost:$BackendPort/Full_Compact.html"
    } catch {
        Write-Warning "Failed to auto-open browser: $($_.Exception.Message)"
    }
}

if (-not $NoKeepAlive) {
    Write-Host ""
    Write-Host "Launcher is now monitoring. Press Ctrl+C to stop this terminal watcher." -ForegroundColor Cyan
    Write-Host "Note: Auth/Backend child processes keep running in their own windows." -ForegroundColor DarkGray
    while ($true) {
        Start-Sleep -Seconds 5
        $authReady = Test-TcpPort -Port $AuthPort
        $backendReady = Test-TcpPort -Port $BackendPort
        if (-not $authReady -or -not $backendReady) {
            $down = @()
            if (-not $authReady) { $down += "Auth:$AuthPort" }
            if (-not $backendReady) { $down += "Backend:$BackendPort" }
            Write-Warning ("Detected down service(s): " + ($down -join ", "))
        }
    }
}
