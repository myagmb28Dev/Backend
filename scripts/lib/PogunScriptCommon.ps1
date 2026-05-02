function Set-PogunWindowTitle {
    param([string]$Title)
    try {
        $host.UI.RawUI.WindowTitle = $Title
    } catch {
    }
}

function Resolve-PogunGradleUserHome {
    param([string]$RepoRoot)

    $wrapperProperties = Join-Path $RepoRoot "gradle\wrapper\gradle-wrapper.properties"
    $distributionName = "gradle-8.14-bin"
    if (Test-Path $wrapperProperties) {
        $distributionUrlLine = Select-String -Path $wrapperProperties -Pattern "^distributionUrl=" | Select-Object -First 1
        if ($distributionUrlLine -and $distributionUrlLine.Line -match "([^/\\]+)\.zip$") {
            $distributionName = $Matches[1]
        }
    }

    $candidateHomes = @()
    if ($env:GRADLE_USER_HOME) {
        $candidateHomes += $env:GRADLE_USER_HOME
    }
    $candidateHomes += (Join-Path $env:USERPROFILE ".gradle")
    $usersRoot = Join-Path $env:SystemDrive "Users"
    if (Test-Path $usersRoot) {
        $candidateHomes += Get-ChildItem $usersRoot -Directory -ErrorAction SilentlyContinue |
            ForEach-Object { Join-Path $_.FullName ".gradle" }
    }

    $repoGradleHome = Join-Path $RepoRoot ".gradle"
    New-Item -ItemType Directory -Force -Path $repoGradleHome | Out-Null
    $repoDistributionRoot = Join-Path $repoGradleHome "wrapper\dists\$distributionName"

    foreach ($candidateHome in ($candidateHomes | Select-Object -Unique)) {
        $distributionRoot = Join-Path $candidateHome "wrapper\dists\$distributionName"
        if (-not (Test-Path $distributionRoot)) {
            continue
        }

        $installed = Get-ChildItem $distributionRoot -Directory -Recurse -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -match "^gradle-\d" } |
            Select-Object -First 1

        if (-not $installed) {
            continue
        }

        $lockProbe = Join-Path (Split-Path -Parent $installed.FullName) ".pogun-write-test"
        try {
            [System.IO.File]::WriteAllText($lockProbe, "ok", [System.Text.UTF8Encoding]::new($false))
            Remove-Item $lockProbe -Force -ErrorAction SilentlyContinue
            return $candidateHome
        } catch {
        }

        $hashDir = Split-Path -Parent $installed.FullName
        $relativeHashDir = $hashDir.Substring($distributionRoot.Length).TrimStart("\", "/")
        $targetHashDir = Join-Path $repoDistributionRoot $relativeHashDir
        New-Item -ItemType Directory -Force -Path $targetHashDir | Out-Null
        Copy-Item -Path $installed.FullName -Destination $targetHashDir -Recurse -Force
        New-Item -ItemType File -Force -Path (Join-Path $targetHashDir "$distributionName.zip.ok") | Out-Null
        return $repoGradleHome
    }

    if (Test-Path $repoDistributionRoot) {
        $repoInstalled = Get-ChildItem $repoDistributionRoot -Directory -Recurse -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -match "^gradle-\d" } |
            Select-Object -First 1
        if ($repoInstalled) {
            return $repoGradleHome
        }
    }

    return $repoGradleHome
}
