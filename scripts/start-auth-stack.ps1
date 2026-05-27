param(
    [ValidateSet("local", "real")]
    [string]$Mode = "local",
    [string]$ProjectId = "pogun-local",
    [string]$Email = "playwright-user1@local.dev",
    [string]$Password = "Test1234!",
    [switch]$PrepareTestUser,
    [int]$AuthPort = 9099,
    [int]$BackendPort = 8080,
    [int]$UiPort = 4000,
    [int]$TimeoutSeconds = 30,
    [int]$StartupTimeoutSec = 45,
    [string]$HealthPath = "/health",
    [string]$SpringProfile = "",
    [switch]$FailFast = $true,
    [switch]$ElevateGradle,
    [switch]$NoDocker,
    [string[]]$DockerServices = @("redis"),
    [switch]$NoAutoOpen,
    [switch]$NoKeepAlive = $true,
    [switch]$WaitForEnd
)

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path -Parent $scriptDir

function Set-PogunWindowTitle {
    param([string]$Title)
    try {
        $host.UI.RawUI.WindowTitle = $Title
    } catch {
    }
}

$localDir = Join-Path $repoRoot ".local"
New-Item -ItemType Directory -Force -Path $localDir | Out-Null
$lockFile = Join-Path $localDir "start-auth-stack.lock"

function Write-Step {
    param([string]$Name)
    Write-Host ("STEP=" + $Name)
}

if (Test-Path $lockFile) {
    throw "Another start-auth-stack run appears active: $lockFile"
}
New-Item -ItemType File -Force -Path $lockFile | Out-Null
trap {
    Remove-Item -LiteralPath $lockFile -Force -ErrorAction SilentlyContinue
    Write-Error ("start-auth-stack failed: " + $_.Exception.Message)
    throw $_
}

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
    if ($composeCmd -is [string]) {
        $composeCmd = @($composeCmd)
    }
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
        Start-Sleep -Milliseconds 500
    }
    throw "$Name did not become ready on port $Port within $TimeoutSec seconds."
}

function Wait-BackendHealth {
    param(
        [int]$Port,
        [int]$TimeoutSec,
        [System.Diagnostics.Process]$Process = $null,
        [string]$LogPath = ""
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        try {
            $response = Invoke-RestMethod -Method Get -Uri "http://127.0.0.1:$Port$HealthPath" -TimeoutSec 3
            if ($response.status -eq 200 -or $response.ok -eq $true) {
                return
            }
        } catch {
        }
        if ($Process -and $Process.HasExited) {
            throw "Spring Boot backend process exited before health became ready. Check the visible backend window."
        }
        Start-Sleep -Seconds 1
    }
    $logHint = ""
    if (-not [string]::IsNullOrWhiteSpace($LogPath) -and (Test-Path $LogPath)) {
        $logHint = " Check log: $LogPath"
    }
    throw "Spring Boot backend health did not become ready on port $Port ($HealthPath) within $TimeoutSec seconds.$logHint"
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

function Preflight-Check {
    Write-Step "preflight"
    $checks = @(
        @{ Name = "java"; Cmd = { cmd /c "java -version >nul 2>&1" } },
        @{ Name = "gradle-wrapper"; Cmd = { cmd /c ".\gradlew.bat -v >nul 2>&1" } },
        @{ Name = "psql"; Cmd = { cmd /c "psql --version >nul 2>&1" } }
    )
    foreach ($check in $checks) {
        try {
            $global:LASTEXITCODE = 0
            & $check.Cmd
            if ($LASTEXITCODE -ne 0) {
                throw "$($check.Name) check failed"
            }
        } catch {
            if ($FailFast) {
                throw "Preflight failed: $($check.Name). $($_.Exception.Message)"
            }
            Write-Warning "Preflight warning: $($check.Name) failed."
        }
    }
}

function Ensure-GradleWrapperReady {
    param(
        [string]$RepoPath,
        [string]$LogPath
    )
    Push-Location $RepoPath
    try {
        Remove-Item Env:GRADLE_USER_HOME -ErrorAction SilentlyContinue
        & ".\gradlew.bat" --version *> $LogPath
        if ($LASTEXITCODE -ne 0) {
            throw "Gradle wrapper bootstrap failed. Check log: $LogPath"
        }
    } finally {
        Pop-Location
    }
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
    Write-Step "redis"
    $composePath = Join-Path $repoRoot "docker-compose.yml"
    $composeEnvPath = Join-Path $repoRoot ".env.local"
    if (-not (Test-Path $composeEnvPath)) {
        $composeEnvPath = Join-Path $repoRoot ".env"
    }
    if (Test-Path $composePath) {
        try {
            Start-DockerServices -Services $DockerServices -ComposeFilePath $composePath -EnvFilePath $composeEnvPath -TimeoutSec $TimeoutSeconds
        } catch {
            if (Test-TcpPort -Port 6379) {
                Write-Warning "Docker startup failed, but Redis port 6379 is already reachable. Continuing."
            } else {
                throw "Docker startup failed and Redis is not reachable on 6379. Run terminal as Administrator or pass -NoDocker when Redis is already running."
            }
        }
    } else {
        Write-Warning "docker-compose.yml not found at $composePath. Skipping docker startup."
    }
}

Preflight-Check

if ($Mode -eq "real") {
    Write-Step "backend_start"
    Set-PogunWindowTitle -Title "Pogun Backend Real"
    Set-Location $repoRoot
    Remove-Item Env:FIREBASE_AUTH_EMULATOR_HOST -ErrorAction SilentlyContinue
    Remove-Item Env:APP_FIREBASE_AUTH_EMULATOR_HOST -ErrorAction SilentlyContinue

    $env:SPRING_PROFILES_ACTIVE = $SpringProfile
    $env:APP_FIREBASE_AUTH_MODE = "PRODUCTION"
    $env:APP_FIREBASE_AUTH_ALLOW_EMULATOR = "false"
    $env:PORT = "$BackendPort"
    Remove-Item Env:GRADLE_USER_HOME -ErrorAction SilentlyContinue

    & ".\gradlew.bat" bootRun
    Remove-Item -LiteralPath $lockFile -Force -ErrorAction SilentlyContinue
    exit $LASTEXITCODE
}

$xdgConfigDir = Join-Path $localDir "xdg"
if ([string]::IsNullOrWhiteSpace($env:XDG_CONFIG_HOME)) {
    $env:XDG_CONFIG_HOME = $xdgConfigDir
}
New-Item -ItemType Directory -Force -Path $env:XDG_CONFIG_HOME | Out-Null

$firebaseExe = Get-ExecutablePath -Names @("firebase.cmd")

Ensure-PortReadyWithRetry -Port $AuthPort -Name "Firebase Auth Emulator" -TimeoutSec $TimeoutSeconds -StartAction {
    Write-Step "auth_emulator_start"
    $emulatorCommand = @(
        "`$env:XDG_CONFIG_HOME = '$($env:XDG_CONFIG_HOME)'",
        "& `"$firebaseExe`" emulators:start --only auth --project $ProjectId"
    ) -join "; "
    Start-DetachedPowerShell -Title "Pogun Auth Emulator" -CommandText $emulatorCommand
}

$gradleCheckLog = Join-Path $localDir "gradle-wrapper-check.log"
Ensure-GradleWrapperReady -RepoPath $repoRoot -LogPath $gradleCheckLog

Stop-ProcessesListeningOnPort -Port $BackendPort -Name "backend"

$backendCommand = @(
    "`$env:PORT = '$BackendPort'",
    "`$env:SERVER_PORT = '$BackendPort'",
    "Remove-Item Env:GRADLE_USER_HOME -ErrorAction SilentlyContinue",
    "`$env:SPRING_PROFILES_ACTIVE = '$SpringProfile'",
    "`$env:APP_FIREBASE_AUTH_MODE = 'PRODUCTION'",
    "`$env:APP_FIREBASE_AUTH_ALLOW_EMULATOR = 'true'",
    "`$env:APP_FIREBASE_AUTH_EMULATOR_PROJECT_ID = '$ProjectId'",
    "`$env:FIREBASE_ALLOW_AUTH_EMULATOR = 'true'",
    "`$env:SPRING_WEB_RESOURCES_STATIC_LOCATIONS = 'file:$($repoRoot.Replace('\', '/'))/src/main/resources/static/,classpath:/static/'",
    "`$env:SPRING_WEB_RESOURCES_CACHE_PERIOD = '0'",
    "`$env:SPRING_WEB_RESOURCES_CACHE_CACHECONTROL_NO_STORE = 'true'",
    "& '.\\gradlew.bat' bootRun *> '.\\.local\\backend-local.log'"
) -join "; "

$backendProcess = Start-DetachedPowerShell -Title "Pogun Backend Local" -CommandText $backendCommand -RunAsAdmin:$ElevateGradle

Write-Step "health_wait"
try {
    Wait-BackendHealth -Port $BackendPort -TimeoutSec $StartupTimeoutSec -Process $backendProcess -LogPath (Join-Path $localDir "backend-local.log")
} catch {
    # Gradle cold start can exceed the first window. Retry once with short grace.
    Wait-BackendHealth -Port $BackendPort -TimeoutSec 45 -Process $backendProcess -LogPath (Join-Path $localDir "backend-local.log")
}
Write-Step "ready"

Write-Host ""
Write-Host "Local auth emulator stack is ready." -ForegroundColor Green
Write-Host "Project       : $ProjectId"
Write-Host "Auth Emulator : http://127.0.0.1:$AuthPort"
Write-Host "Emulator UI   : http://127.0.0.1:$UiPort"
Write-Host "Backend       : http://localhost:$BackendPort"

if ($PrepareTestUser) {
    $authTokenResponse = Ensure-EmulatorUserAndGetToken -TargetEmail $Email -TargetPassword $Password
    $idToken = $authTokenResponse.idToken
    $refreshToken = $authTokenResponse.refreshToken
    Ensure-EmulatorEmailVerified -TargetEmail $Email -Token $idToken
    try {
        Ensure-BackendUserReady -Token $idToken | Out-Null
    } catch {
        Write-Warning "Backend bootstrap failed for $Email. Continuing without onboarding bootstrap."
    }
    Write-TokenArtifacts -Token $idToken -RefreshToken $refreshToken -TokenFileName "emulator-firebase-id-token.txt" -HeaderFileName "emulator-authorization-header.txt" -LoginBodyFileName "emulator-login-request.json" -RefreshTokenFileName "emulator-refresh-token.txt"

    $tokenPath = Join-Path $localDir "emulator-firebase-id-token.txt"
    $authHeaderPath = Join-Path $localDir "emulator-authorization-header.txt"
    $loginBodyPath = Join-Path $localDir "emulator-login-request.json"

    Write-Host ""
    Write-Host "Test user prepared." -ForegroundColor Yellow
    Write-Host "Email         : $Email"
    Write-Host "Token file    : $tokenPath"
    Write-Host "Header file   : $authHeaderPath"
    Write-Host "Login body    : $loginBodyPath"
    Write-Host ""
    Write-Host "These token files are emulator-only. Do not use them against real Firebase mode or deployed servers." -ForegroundColor Yellow
}

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

if ($WaitForEnd) {
    Write-Host ""
    Write-Host "Type 'end' and press Enter to stop backend on port $BackendPort." -ForegroundColor Cyan
    while ($true) {
        $command = Read-Host "command"
        if ($null -ne $command -and $command.Trim().ToLowerInvariant() -eq "end") {
            Stop-ProcessesListeningOnPort -Port $BackendPort -Name "backend"
            break
        }
    }
}

Remove-Item -LiteralPath $lockFile -Force -ErrorAction SilentlyContinue
