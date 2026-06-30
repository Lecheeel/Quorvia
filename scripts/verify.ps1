Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot

function Invoke-Step {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Name,
        [Parameter(Mandatory = $true)]
        [scriptblock] $Command
    )

    Write-Host ""
    Write-Host "==> $Name"
    & $Command
}

Invoke-Step "Android unit tests" {
    Push-Location (Join-Path $root "android")
    try {
        .\gradlew.bat testDebugUnitTest
    } finally {
        Pop-Location
    }
}

Invoke-Step "Android debug build" {
    Push-Location (Join-Path $root "android")
    try {
        .\gradlew.bat assembleDebug
    } finally {
        Pop-Location
    }
}

Invoke-Step "Android lint" {
    Push-Location (Join-Path $root "android")
    try {
        .\gradlew.bat lintDebug
    } finally {
        Pop-Location
    }
}

Invoke-Step "Server tests and build" {
    Push-Location (Join-Path $root "server")
    try {
        npm test
        npm run check
        npm run build
    } finally {
        Pop-Location
    }
}

Invoke-Step "Secret scan" {
    $patterns = @(
        "AQN_API_KEY=.*[A-Za-z0-9]{10,}",
        "AMAP_ANDROID_KEY=.*[a-f0-9]{10,}"
    ) -join "|"

    $rgArgs = @(
        "-n",
        $patterns,
        "-S",
        "--glob", "!scripts/verify.ps1",
        "--glob", "!server/node_modules/**",
        "--glob", "!server/dist/**",
        "--glob", "!android/**/build/**",
        "--glob", "!android/.gradle/**",
        "--glob", "!android/local.properties",
        "--glob", "!.quorvia.local.env",
        "--glob", "!*.jks",
        "--glob", "!.git/**",
        "."
    )

    Push-Location $root
    try {
        $output = & rg @rgArgs
        if ($LASTEXITCODE -eq 0) {
            $output
            throw "Secret scan found possible sensitive values."
        }
        if ($LASTEXITCODE -gt 1) {
            throw "Secret scan failed with exit code $LASTEXITCODE."
        }
        Write-Host "No environment-style sensitive values found in tracked candidates."
    } finally {
        Pop-Location
    }
}

Write-Host ""
Write-Host "Verification complete."
