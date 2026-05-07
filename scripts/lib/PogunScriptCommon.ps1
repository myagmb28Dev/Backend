function Resolve-PogunGradleUserHome {
    param([string]$RepoRoot)
    $base = $env:GRADLE_USER_HOME
    if ([string]::IsNullOrWhiteSpace($base)) {
        if ([string]::IsNullOrWhiteSpace($RepoRoot)) {
            $RepoRoot = (Get-Location).Path
        }
        $base = Join-Path $RepoRoot ".gradle-user"
    }
    if (-not (Test-Path $base)) {
        New-Item -ItemType Directory -Force -Path $base | Out-Null
    }
    return $base
}

function Set-PogunWindowTitle {
    param([string]$Title)
    try {
        $host.UI.RawUI.WindowTitle = $Title
    } catch {
    }
}
