param(
    [string[]]$Emails = @("playwright-user2@local.dev", "playwright-user3@local.dev")
)

# Parse .env.local to extract DB credentials
$envPath = Join-Path (Resolve-Path ".") ".env.local"
$envVars = @{}

Get-Content $envPath | ForEach-Object {
    $line = $_.Trim()
    if ([string]::IsNullOrWhiteSpace($line) -or $line.StartsWith("#")) { return }
    $parts = $line -split "=", 2
    if ($parts.Count -eq 2) {
        $envVars[$parts[0].Trim()] = $parts[1].Trim()
    }
}

$dbUrl = $envVars["DB_URL"]
$dbUser = $envVars["DB_USERNAME"]
$dbPass = $envVars["DB_PASSWORD"]

Write-Host "DB_URL: $dbUrl"
Write-Host "DB_USERNAME: $dbUser"
Write-Host ""

if ([string]::IsNullOrWhiteSpace($dbUrl) -or [string]::IsNullOrWhiteSpace($dbUser) -or [string]::IsNullOrWhiteSpace($dbPass)) {
    Write-Error "Missing DB credentials in .env.local"
    exit 1
}

# Now run the Java delete tool
Write-Host "Running JDBC deletion for: $($Emails -join ', ')"
Write-Host ""

# Use the existing JdbcDeleteUsersByEmail class
$emailsArg = ($Emails | ForEach-Object { "`"$_`"" }) -join " "
$envBlock = @"
`$env:DB_URL = "$dbUrl"
`$env:DB_USERNAME = "$dbUser"
`$env:DB_PASSWORD = "$dbPass"
`$env:CLASSPATH = (Resolve-Path ".").Path
& .\gradlew.bat compileJava -q 2>&1 | Out-Null
`$jarPath = Join-Path (Resolve-Path ".") "build\classes\java\main"
`$cp = Get-ChildItem $jarPath -Recurse -Include "*.jar" -ErrorAction SilentlyContinue | ForEach-Object { `$_.FullName } | Join-String -Separator ";"
if (-not `$cp) { `$cp = "$jarPath" }
& java -cp "`$cp;build/classes/java/main" com.example.pogun.tools.JdbcDeleteUsersByEmail $emailsArg
"@

powershell -NoProfile -Command $envBlock
