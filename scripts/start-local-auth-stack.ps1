param(
    [string]$ProjectId = "",
    [string]$Email = "tester@local.dev",
    [string]$Password = "Test1234!",
    [int]$AuthPort = 9099,
    [int]$BackendPort = 8080,
    [int]$UiPort = 4000,
    [int]$TimeoutSeconds = 90
)

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path -Parent $scriptDir
$localDir = Join-Path $repoRoot ".local"
New-Item -ItemType Directory -Force -Path $localDir | Out-Null

function Get-DotEnvValue {
    param(
        [string]$FilePath,
        [string]$Key
    )

    if (-not (Test-Path $FilePath)) {
        return $null
    }

    $line = Select-String -Path $FilePath -Pattern "^$Key=" | Select-Object -First 1
    if (-not $line) {
        return $null
    }

    return ($line.Line -split '=', 2)[1]
}

function Resolve-DefaultProjectId {
    $envFile = Join-Path $repoRoot ".env"

    $dotenvProjectId = Get-DotEnvValue -FilePath $envFile -Key "FIREBASE_PROJECT_ID"
    if ($dotenvProjectId) {
        return $dotenvProjectId.Trim()
    }

    $dotenvGcloudProject = Get-DotEnvValue -FilePath $envFile -Key "GCLOUD_PROJECT"
    if ($dotenvGcloudProject) {
        return $dotenvGcloudProject.Trim()
    }

    $dotenvBase64 = Get-DotEnvValue -FilePath $envFile -Key "FIREBASE_KEY_BASE64"
    if ($dotenvBase64) {
        try {
            $jsonText = [System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String($dotenvBase64.Trim()))
            $serviceAccount = $jsonText | ConvertFrom-Json
            if ($serviceAccount.project_id) {
                return [string]$serviceAccount.project_id
            }
        } catch {
        }
    }

    return "pogun-local"
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
        [int]$TimeoutSec
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        if (Test-TcpPort -Port $Port) {
            return
        }
        Start-Sleep -Seconds 1
    }

    throw "$Name did not become ready on port $Port within $TimeoutSec seconds."
}

function Start-DetachedPowerShell {
    param(
        [string]$Title,
        [string]$CommandText
    )

    $powershellExe = Join-Path $env:WINDIR "System32\\WindowsPowerShell\\v1.0\\powershell.exe"
    $fullCommand = "`$host.UI.RawUI.WindowTitle = '$Title'; $CommandText"

    Start-Process -FilePath $powershellExe -WorkingDirectory $repoRoot -ArgumentList @(
        "-NoExit",
        "-Command",
        $fullCommand
    ) | Out-Null
}

function Get-HttpErrorText {
    param([System.Management.Automation.ErrorRecord]$ErrorRecord)

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

function Ensure-EmulatorUserAndGetToken {
    $authBody = @{
        email = $Email
        password = $Password
        returnSecureToken = $true
    }

    try {
        Invoke-AuthEmulatorRequest `
            -Path "/identitytoolkit.googleapis.com/v1/accounts:signUp?key=fake-api-key" `
            -Body $authBody | Out-Null
    } catch {
        $errorText = Get-HttpErrorText -ErrorRecord $_
        if ($errorText -notmatch "EMAIL_EXISTS") {
            throw
        }
    }

    $response = Invoke-AuthEmulatorRequest `
        -Path "/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=fake-api-key" `
        -Body $authBody

    return $response.idToken
}

$firebaseExe = Get-ExecutablePath -Names @("firebase.cmd", "firebase")

if ([string]::IsNullOrWhiteSpace($ProjectId)) {
    $ProjectId = Resolve-DefaultProjectId
}

if (-not (Test-TcpPort -Port $AuthPort)) {
    $emulatorCommand = "& `"$firebaseExe`" emulators:start --only auth --project $ProjectId"
    Start-DetachedPowerShell -Title "Pogun Auth Emulator" -CommandText $emulatorCommand
}

Wait-TcpPort -Port $AuthPort -Name "Firebase Auth Emulator" -TimeoutSec $TimeoutSeconds

if (-not (Test-TcpPort -Port $BackendPort)) {
    $backendCommand = @(
        "`$env:FIREBASE_AUTH_EMULATOR_HOST = '127.0.0.1:$AuthPort'",
        "`$env:FIREBASE_ALLOW_AUTH_EMULATOR = 'true'",
        "`$env:GCLOUD_PROJECT = '$ProjectId'",
        "`$env:FIREBASE_PROJECT_ID = '$ProjectId'",
        "& '.\\gradlew.bat' bootRun"
    ) -join "; "

    Start-DetachedPowerShell -Title "Pogun Backend Local" -CommandText $backendCommand
}

Wait-TcpPort -Port $BackendPort -Name "Spring Boot backend" -TimeoutSec $TimeoutSeconds

$idToken = Ensure-EmulatorUserAndGetToken

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

$tokenPath = Join-Path $localDir "emulator-firebase-id-token.txt"
$authHeaderPath = Join-Path $localDir "emulator-authorization-header.txt"
$loginBodyPath = Join-Path $localDir "emulator-login-request.json"

[System.IO.File]::WriteAllText($tokenPath, $idToken, [System.Text.UTF8Encoding]::new($false))
[System.IO.File]::WriteAllText($authHeaderPath, "Authorization: Bearer $idToken", [System.Text.UTF8Encoding]::new($false))
[System.IO.File]::WriteAllText($loginBodyPath, "{`n  `"firebaseIdToken`": `"$idToken`"`n}", [System.Text.UTF8Encoding]::new($false))

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
Write-Host "Login test example:" -ForegroundColor Yellow
Write-Host "POST http://localhost:$BackendPort/api/auth/login"
Write-Host (Get-Content -Raw $loginBodyPath)
