<#
.SYNOPSIS
Checks Google Desktop OAuth configuration and starts the local planner.
.DESCRIPTION
Reads a Google-downloaded Desktop client JSON file without copying it into the
project. Credentials are passed only through the current launch environment and
the previous environment is restored when the planner stops. CheckOnly does not
launch the app, contact Google, or read any calendar data.
.EXAMPLE
./setup-google-calendar.ps1 -CredentialsFile 'C:/private/google-desktop.json' -CheckOnly
.EXAMPLE
./setup-google-calendar.ps1 -CredentialsFile 'C:/private/google-desktop.json'
#>
param(
    [string]$CredentialsFile,
    [ValidateRange(1024, 65535)][int]$Port = 8471,
    [string]$DataFile,
    [switch]$CheckOnly
)
$ErrorActionPreference = 'Stop'
$clientId = $env:GOOGLE_CLIENT_ID
$clientSecret = $env:GOOGLE_CLIENT_SECRET

if ($CredentialsFile) {
    try {
        $credentialItem = Get-Item -LiteralPath $CredentialsFile
        if ($credentialItem.PSIsContainer -or $credentialItem.Length -gt 65536) {
            throw 'Invalid credential file.'
        }
        $configuration = Get-Content -LiteralPath $credentialItem.FullName -Raw | ConvertFrom-Json
    } catch {
        # Do not include JSON contents or a parser exception that could quote a secret.
        throw 'Could not read a valid Google OAuth JSON file (maximum 64 KiB).'
    }
    if (-not $configuration.installed -or $configuration.web) {
        throw 'Use credentials downloaded for an OAuth client of type Desktop app, not Web application or service account.'
    }
    $clientId = [string]$configuration.installed.client_id
    $clientSecret = [string]$configuration.installed.client_secret
}

if ([string]::IsNullOrWhiteSpace($clientId)) {
    throw 'Google is not configured. Enable the Google Calendar API, configure OAuth consent, create a Desktop app OAuth client, and pass its downloaded JSON using -CredentialsFile. See proactive/README.md.'
}
if ($clientId -cnotmatch '^[A-Za-z0-9_-]+\.apps\.googleusercontent\.com$') {
    throw 'The Google client ID format is invalid. Use the original Desktop app credential download.'
}
if ($clientId.Length -gt 512 -or ($clientSecret -and $clientSecret.Length -gt 1024)) {
    throw 'The OAuth configuration is unexpectedly large. Use the original Desktop app credential download.'
}

Write-Host 'Google OAuth configuration format checked. No credential values are shown or saved.'
Write-Host "Local callback: http://127.0.0.1:$Port/oauth/callback"
Write-Host 'Access requested: read-only calendar events. Only the primary calendar is imported.'
Write-Host 'Google API enablement, consent-screen test users, and account access still require a real sign-in check.'
Write-Host 'In Connections, start your own plan, then choose Connect Google Calendar.'
if ($CheckOnly) { return }

$previousClientId = $env:GOOGLE_CLIENT_ID
$previousClientSecret = $env:GOOGLE_CLIENT_SECRET
try {
    $env:GOOGLE_CLIENT_ID = $clientId
    $env:GOOGLE_CLIENT_SECRET = $clientSecret
    $launchArguments = @{ Port = $Port }
    if ($DataFile) { $launchArguments.DataFile = $DataFile }
    & (Join-Path $PSScriptRoot 'run-planner.ps1') @launchArguments
} finally {
    $env:GOOGLE_CLIENT_ID = $previousClientId
    $env:GOOGLE_CLIENT_SECRET = $previousClientSecret
    $clientId = $null
    $clientSecret = $null
    $configuration = $null
}
