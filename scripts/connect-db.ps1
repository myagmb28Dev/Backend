param(
    [ValidateSet("local", "prod")]
    [string]$Mode = "local"
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$envFile = if ($Mode -eq "local") { ".env.local" } else { ".env" }
$envPath = Join-Path $repoRoot $envFile

if (-not (Test-Path $envPath)) {
    throw "Env file not found: $envPath"
}

if (-not (Get-Command psql -ErrorAction SilentlyContinue)) {
    $candidatePaths = @(
        "C:\\Program Files\\PostgreSQL\\18\\bin\\psql.exe",
        "C:\\Program Files\\PostgreSQL\\17\\bin\\psql.exe",
        "C:\\Program Files\\PostgreSQL\\16\\bin\\psql.exe",
        "C:\\Program Files\\PostgreSQL\\15\\bin\\psql.exe",
        "C:\\Program Files\\PostgreSQL\\14\\bin\\psql.exe"
    )
    $resolved = $candidatePaths | Where-Object { Test-Path $_ } | Select-Object -First 1
    if ($resolved) {
        $env:Path = "$([System.IO.Path]::GetDirectoryName($resolved));$env:Path"
    }
}

if (-not (Get-Command psql -ErrorAction SilentlyContinue)) {
    throw "psql is not installed or not on PATH. Install PostgreSQL client or add psql.exe to PATH."
}

$kv = @{}
Get-Content $envPath | ForEach-Object {
    $line = $_.Trim()
    if ([string]::IsNullOrWhiteSpace($line)) { return }
    if ($line.StartsWith("#")) { return }
    $idx = $line.IndexOf("=")
    if ($idx -lt 1) { return }
    $key = $line.Substring(0, $idx).Trim()
    $value = $line.Substring($idx + 1).Trim()
    if (-not [string]::IsNullOrWhiteSpace($key)) {
        $kv[$key] = $value
    }
}

$dbUrl = $kv["DB_URL"]
$dbUser = $kv["DB_USERNAME"]
$dbPass = $kv["DB_PASSWORD"]

if ([string]::IsNullOrWhiteSpace($dbUrl) -or [string]::IsNullOrWhiteSpace($dbUser) -or [string]::IsNullOrWhiteSpace($dbPass)) {
    throw "DB_URL/DB_USERNAME/DB_PASSWORD missing in $envFile"
}

if ($dbUrl -notmatch '^jdbc:postgresql://([^/:?]+)(?::(\d+))?/([^?]+)') {
    throw "Unsupported DB_URL format: $dbUrl"
}

$host = $Matches[1]
$port = if ($Matches[2]) { $Matches[2] } else { "5432" }
$database = $Matches[3]

$env:PGHOST = $host
$env:PGPORT = $port
$env:PGDATABASE = $database
$env:PGUSER = $dbUser
$env:PGPASSWORD = $dbPass

Write-Host "Connecting with ${envFile} -> ${host}:${port}/${database}"
& psql
