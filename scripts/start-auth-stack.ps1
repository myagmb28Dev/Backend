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
    [string]$SpringProfile = ""
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
        [string]$CommandText
    )

    $powershellExe = Join-Path $env:WINDIR "System32\\WindowsPowerShell\\v1.0\\powershell.exe"
    $fullCommand = "`$host.UI.RawUI.WindowTitle = '$Title'; `$OutputEncoding = [Console]::OutputEncoding = [System.Text.UTF8Encoding]::new(); chcp 65001 > `$null; $CommandText"
    return Start-Process -FilePath $powershellExe -WorkingDirectory $repoRoot -WindowStyle Normal -PassThru -ArgumentList @(
        "-NoExit",
        "-Command",
        $fullCommand
    )
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
        $response = Invoke-AuthEmulatorRequest -Path "/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=fake-api-key" -Body $authBody
        return $response.idToken
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

    $response = Invoke-AuthEmulatorRequest -Path "/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=fake-api-key" -Body $authBody
    return $response.idToken
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
        [string]$TokenFileName,
        [string]$HeaderFileName,
        [string]$LoginBodyFileName
    )

    $tokenPath = Join-Path $localDir $TokenFileName
    $authHeaderPath = Join-Path $localDir $HeaderFileName
    $loginBodyPath = Join-Path $localDir $LoginBodyFileName

    [System.IO.File]::WriteAllText($tokenPath, $Token, [System.Text.UTF8Encoding]::new($false))
    [System.IO.File]::WriteAllText($authHeaderPath, "Authorization: Bearer $Token", [System.Text.UTF8Encoding]::new($false))
    [System.IO.File]::WriteAllText($loginBodyPath, "{`n  `"firebaseIdToken`": `"$Token`"`n}", [System.Text.UTF8Encoding]::new($false))
}

if ([string]::IsNullOrWhiteSpace($SpringProfile)) {
    if ($Mode -eq "local") {
        $SpringProfile = "local"
    } else {
        $SpringProfile = "prod"
    }
}

$gradleUserHome = Resolve-PogunGradleUserHome -RepoRoot $repoRoot

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

$backendProcess = $null
if (-not (Test-TcpPort -Port $BackendPort)) {
    $backendCommand = @(
        "`$env:PORT = '$BackendPort'",
        "`$env:SERVER_PORT = '$BackendPort'",
        "`$env:GRADLE_USER_HOME = '$gradleUserHome'",
        "`$env:SPRING_PROFILES_ACTIVE = '$SpringProfile'",
        "`$env:APP_FIREBASE_AUTH_MODE = 'PRODUCTION'",
        "`$env:APP_FIREBASE_AUTH_ALLOW_EMULATOR = 'true'",
        "`$env:APP_FIREBASE_AUTH_EMULATOR_PROJECT_ID = '$ProjectId'",
        "`$env:FIREBASE_AUTH_EMULATOR_HOST = '127.0.0.1:$AuthPort'",
        "`$env:FIREBASE_ALLOW_AUTH_EMULATOR = 'true'",
        "`$env:APP_ADMIN_WEBAUTHN_RP_ID = 'localhost'",
        "`$env:APP_ADMIN_WEBAUTHN_RP_NAME = 'Pogun Admin Local'",
        "`$env:APP_ADMIN_WEBAUTHN_ALLOWED_ORIGINS = 'http://localhost:$BackendPort,http://127.0.0.1:$BackendPort,http://localhost:8080,http://127.0.0.1:8080'",
        "& '.\\gradlew.bat' bootRun"
    ) -join "; "

    $backendProcess = Start-DetachedPowerShell -Title "Pogun Backend Local" -CommandText $backendCommand
}

Wait-BackendHealth -Port $BackendPort -TimeoutSec $TimeoutSeconds -Process $backendProcess

$idToken = Ensure-EmulatorUserAndGetToken -TargetEmail $Email -TargetPassword $Password
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

Write-TokenArtifacts -Token $idToken -TokenFileName "emulator-firebase-id-token.txt" -HeaderFileName "emulator-authorization-header.txt" -LoginBodyFileName "emulator-login-request.json"

foreach ($account in $playwrightAccounts) {
    $accountToken = Ensure-EmulatorUserAndGetToken -TargetEmail $account.Email -TargetPassword $account.Password
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
        -TokenFileName ("emulator-user{0}-firebase-id-token.txt" -f $account.Index) `
        -HeaderFileName ("emulator-user{0}-authorization-header.txt" -f $account.Index) `
        -LoginBodyFileName ("emulator-user{0}-login-request.json" -f $account.Index)
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
