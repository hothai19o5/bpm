# User-run verification only. Does not connect to or remove the dev stack.
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repoPath = Split-Path -Parent $PSScriptRoot
$previousStage = $env:P16_RESTART_STAGE
$previousId = $env:P16_RESTART_ID
$runId = [guid]::NewGuid().ToString()
$reportPath = Join-Path $repoPath "target/p16-restart/$runId"

function Invoke-RestartCompose {
    param([string[]]$ComposeArguments)
    & docker compose -p bpm-p16-restart -f compose.restart.yaml @ComposeArguments
    if ($LASTEXITCODE -ne 0) {
        throw "P1.6 Docker Compose failed (exit $LASTEXITCODE): $($ComposeArguments -join ' ')"
    }
}

function Invoke-RestartStage {
    param([string]$Stage)
    $env:P16_RESTART_STAGE = $Stage
    $stagePath = Join-Path $reportPath $Stage
    New-Item -ItemType Directory -Force -Path $stagePath | Out-Null
    $stagePassed = $false
    try {
        # Only tests is attached; the already-running PostgreSQL stays up when it exits.
        Invoke-RestartCompose -ComposeArguments @('up', '--no-deps', '--force-recreate', '--abort-on-container-exit', '--exit-code-from', 'tests', 'tests')
        $stagePassed = $true
    } finally {
        & docker compose -p bpm-p16-restart -f compose.restart.yaml logs --no-color |
            Out-File -Encoding utf8 (Join-Path $stagePath 'compose.log')
        & docker compose -p bpm-p16-restart -f compose.restart.yaml cp tests:/workspace/target/surefire-reports $stagePath
        if ($LASTEXITCODE -ne 0) {
            if ($stagePassed) {
                throw "P1.6 $Stage passed but its Surefire reports could not be collected"
            }
            Write-Warning "Surefire reports unavailable; inspect $stagePath/compose.log."
        }
    }
}

function Get-RestartContainerId {
    param([string]$Service)
    $containerId = & docker compose -p bpm-p16-restart -f compose.restart.yaml ps --all --quiet $Service
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($containerId)) {
        throw "Cannot read P1.6 container ID for $Service"
    }
    return $containerId
}

Push-Location $repoPath
try {
    $env:P16_RESTART_ID = $runId
    $env:P16_RESTART_STAGE = 'prepare'
    Invoke-RestartCompose -ComposeArguments @('build', 'tests')
    Invoke-RestartCompose -ComposeArguments @('up', '-d', '--wait', '--wait-timeout', '120', 'postgres')
    Invoke-RestartStage 'prepare'
    $previousDatabaseContainer = Get-RestartContainerId 'postgres'
    $previousTestContainer = Get-RestartContainerId 'tests'
    # Close the old DB process/container. The named volume is retained.
    Invoke-RestartCompose -ComposeArguments @('stop', 'postgres')
    Invoke-RestartCompose -ComposeArguments @('up', '-d', '--no-deps', '--force-recreate', '--wait', '--wait-timeout', '120', 'postgres')
    # Recreates tests too: a new Maven/JVM process must load the previous mapping.
    Invoke-RestartStage 'resume'
    $newDatabaseContainer = Get-RestartContainerId 'postgres'
    $newTestContainer = Get-RestartContainerId 'tests'
    if ($previousDatabaseContainer -eq $newDatabaseContainer -or $previousTestContainer -eq $newTestContainer) {
        throw 'P1.6 requires both the database and JVM test runner containers to be recreated'
    }
    [ordered]@{
        runId = $runId
        prepareDatabaseContainer = $previousDatabaseContainer
        resumeDatabaseContainer = $newDatabaseContainer
        prepareTestContainer = $previousTestContainer
        resumeTestContainer = $newTestContainer
    } | ConvertTo-Json | Out-File -Encoding utf8 (Join-Path $reportPath 'containers.json')
    Invoke-RestartCompose -ComposeArguments @('down')
    Write-Output "P1.6 restart verification passed. Reports: $reportPath"
} finally {
    # On failure leave containers/volume for inspection, and preserve the original error.
    $env:P16_RESTART_STAGE = $previousStage
    $env:P16_RESTART_ID = $previousId
    Pop-Location
}
