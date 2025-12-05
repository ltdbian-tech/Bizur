param(
    [string]$BackendUrl,
    [string]$Identity,
    [string]$AuthToken,
    [string]$ApiKey,
    [switch]$Install
)

# Simple helper to register an identity and build/install the Android app.
# Usage example:
#   powershell -ExecutionPolicy Bypass -File .\signaling-setup.ps1 -BackendUrl "https://your-backend" -Identity "alice" -AuthToken "<AUTH_TOKEN>" -Install

function Read-Required([string]$Label) {
    $value = Read-Host $Label
    if (-not $value) {
        Write-Error "$Label is required." -ErrorAction Stop
    }
    return $value
}

if (-not $BackendUrl) { $BackendUrl = Read-Required "Enter backend URL (e.g. https://your-backend)" }
if (-not $Identity) { $Identity = Read-Required "Enter your identity/username" }
if (-not $AuthToken) { $AuthToken = Read-Required "Enter AUTH_TOKEN (shared secret)" }

$BackendUrl = $BackendUrl.TrimEnd('/')

if (-not $ApiKey) {
    Write-Host "Requesting apiKey for identity '$Identity'..."
    $registerUrl = "$BackendUrl/auth/register"
    $headers = @{ "x-api-key" = $AuthToken }
    $body = @{ identity = $Identity } | ConvertTo-Json

    try {
        $resp = Invoke-RestMethod -Method Post -Uri $registerUrl -Headers $headers -Body $body -ContentType "application/json"
        $ApiKey = $resp.apiKey
        if (-not $ApiKey) {
            Write-Error "apiKey missing in response. Got: $($resp | ConvertTo-Json -Depth 5)" -ErrorAction Stop
        }
    }
    catch {
        Write-Error "Failed to register identity. $_" -ErrorAction Stop
    }
}

Write-Host "Using apiKey: $ApiKey" -ForegroundColor Green

Push-Location "android"

$gradleCmd = ".\gradlew.bat"
$commonArgs = @("-PAUTH_TOKEN=$AuthToken", "-PAPI_KEY=$ApiKey")

Write-Host "Building (assembleDebug)..." -ForegroundColor Cyan
& $gradleCmd @("assembleDebug") @commonArgs
if ($LASTEXITCODE -ne 0) {
    Pop-Location
    exit $LASTEXITCODE
}

if ($Install) {
    Write-Host "Installing to connected device/emulator..." -ForegroundColor Cyan
    & $gradleCmd @("installDebug") @commonArgs
    if ($LASTEXITCODE -ne 0) {
        Pop-Location
        exit $LASTEXITCODE
    }
}

Pop-Location

Write-Host "Done." -ForegroundColor Green
