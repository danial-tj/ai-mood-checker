<#
.SYNOPSIS
Verifies the Google setup helper using synthetic credentials only.
.DESCRIPTION
The parent does not read or change Google credentials in its environment. It
starts an isolated PowerShell process with known synthetic Google variables.
All fixture files are written under ignored proactive/target. No network or
build commands are run, and captured credential output is never printed.
#>
param([switch]$FixtureWorker, [string]$FixtureDirectory)
$ErrorActionPreference = 'Stop'

$syntheticExistingId = 'synthetic-existing-client.apps.googleusercontent.com'
$syntheticExistingSecret = 'synthetic-existing-secret-for-tests-only'
$syntheticFileId = 'synthetic-desktop-client.apps.googleusercontent.com'
$syntheticFileSecret = 'synthetic-downloaded-secret-for-tests-only'
$targetRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'proactive/target'))

if (-not $FixtureWorker) {
    $testRoot = Join-Path $targetRoot ('google-setup-checks-' + [Guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Force -Path $testRoot | Out-Null
    if ($PSVersionTable.PSEdition -eq 'Core') { $shellExecutable = Join-Path $PSHOME 'pwsh.exe' }
    else { $shellExecutable = Join-Path $PSHOME 'powershell.exe' }
    $startInfo = New-Object System.Diagnostics.ProcessStartInfo
    $startInfo.FileName = $shellExecutable
    $startInfo.Arguments = '-NoProfile -NonInteractive -File "' + $PSCommandPath + '" -FixtureWorker -FixtureDirectory "' + $testRoot + '"'
    $startInfo.WorkingDirectory = $PSScriptRoot
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.WindowStyle = [Diagnostics.ProcessWindowStyle]::Hidden
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    # Override before the child starts; never inspect the parent's Google values.
    $startInfo.EnvironmentVariables['GOOGLE_CLIENT_ID'] = $syntheticExistingId
    $startInfo.EnvironmentVariables['GOOGLE_CLIENT_SECRET'] = $syntheticExistingSecret
    $worker = New-Object System.Diagnostics.Process
    $worker.StartInfo = $startInfo
    try {
        if (-not $worker.Start()) { throw 'Could not start the synthetic Google setup checks.' }
        $workerOutput = $worker.StandardOutput.ReadToEndAsync()
        $workerErrors = $worker.StandardError.ReadToEndAsync()
        if (-not $worker.WaitForExit(30000)) {
            $worker.Kill()
            throw 'Synthetic Google setup checks timed out. No credential output was printed.'
        }
        $capturedOutput = $workerOutput.GetAwaiter().GetResult()
        $capturedErrors = $workerErrors.GetAwaiter().GetResult()
        if ($worker.ExitCode -ne 0) {
            throw 'Synthetic Google setup checks failed. Captured credential output was not printed.'
        }
        foreach ($privateValue in @($syntheticExistingId, $syntheticExistingSecret, $syntheticFileId, $syntheticFileSecret)) {
            if ($capturedOutput.Contains($privateValue) -or $capturedErrors.Contains($privateValue)) {
                throw 'Synthetic Google setup checks detected credential output; that output was not printed.'
            }
        }
        # Only the worker's assertion summaries are intended for display.
        $capturedOutput -split '\r?\n' | Where-Object { $_ -match '^PASS: ' } | ForEach-Object { Write-Host $_ }
    } finally { $worker.Dispose() }
    return
}

$fixtureRoot = [IO.Path]::GetFullPath($FixtureDirectory)
$requiredPrefix = $targetRoot.TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
if (-not $fixtureRoot.StartsWith($requiredPrefix, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Synthetic fixtures must stay in proactive/target.'
}
if ($env:GOOGLE_CLIENT_ID -ne $syntheticExistingId -or $env:GOOGLE_CLIENT_SECRET -ne $syntheticExistingSecret) {
    throw 'The fixture worker requires the isolated synthetic environment. Run this script without worker arguments.'
}

$setupPath = Join-Path $PSScriptRoot 'setup-google-calendar.ps1'
$script:checks = 0
$script:captures = New-Object 'System.Collections.Generic.List[string]'
function Assert-SetupCheck([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw ('Synthetic setup assertion failed: ' + $Message) }
    $script:checks++
    Write-Host ('PASS: ' + $Message)
}
function Invoke-SyntheticCheck([string]$File) {
    $output = ''
    $succeeded = $false
    try {
        if ($File) { $output = (& $setupPath -CredentialsFile $File -CheckOnly *>&1 | Out-String) }
        else { $output = (& $setupPath -CheckOnly *>&1 | Out-String) }
        $succeeded = $true
    } catch { $output += ($_ | Out-String) }
    $script:captures.Add($output)
    [pscustomobject]@{ Succeeded = $succeeded; Output = $output }
}

$desktopPath = Join-Path $fixtureRoot 'desktop-synthetic.json'
$webPath = Join-Path $fixtureRoot 'web-synthetic.json'
$malformedPath = Join-Path $fixtureRoot 'malformed-synthetic.json'
$badIdPath = Join-Path $fixtureRoot 'bad-id-synthetic.json'
@{ installed = @{ client_id = $syntheticFileId; client_secret = $syntheticFileSecret } } | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $desktopPath -Encoding UTF8
@{ web = @{ client_id = $syntheticFileId; client_secret = $syntheticFileSecret } } | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $webPath -Encoding UTF8
('{"installed":{"client_secret":"' + $syntheticFileSecret + '",') | Set-Content -LiteralPath $malformedPath -Encoding UTF8
@{ installed = @{ client_id = ($syntheticFileSecret + '@invalid'); client_secret = $syntheticFileSecret } } | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $badIdPath -Encoding UTF8

$valid = Invoke-SyntheticCheck $desktopPath
Assert-SetupCheck $valid.Succeeded 'Accepts a downloaded Desktop OAuth JSON file in CheckOnly mode'
Assert-SetupCheck ($valid.Output.Contains('configuration format checked')) 'Reports a local format check without claiming account validation'
Assert-SetupCheck ($valid.Output.Contains('real sign-in check')) 'Explains that live Google access still needs verification'
Assert-SetupCheck ($env:GOOGLE_CLIENT_ID -eq $syntheticExistingId -and $env:GOOGLE_CLIENT_SECRET -eq $syntheticExistingSecret) 'CheckOnly preserves the existing process environment'

$web = Invoke-SyntheticCheck $webPath
Assert-SetupCheck (-not $web.Succeeded -and $web.Output.Contains('Desktop app')) 'Rejects Web client credentials with Desktop setup guidance'
$malformed = Invoke-SyntheticCheck $malformedPath
Assert-SetupCheck (-not $malformed.Succeeded -and $malformed.Output.Contains('valid Google OAuth JSON')) 'Rejects malformed JSON without printing parser contents'
$badId = Invoke-SyntheticCheck $badIdPath
Assert-SetupCheck (-not $badId.Succeeded -and $badId.Output.Contains('client ID format')) 'Rejects a malformed client identifier'
Assert-SetupCheck ($env:GOOGLE_CLIENT_ID -eq $syntheticExistingId -and $env:GOOGLE_CLIENT_SECRET -eq $syntheticExistingSecret) 'Invalid files also preserve the existing process environment'

$existing = Invoke-SyntheticCheck
Assert-SetupCheck $existing.Succeeded 'Accepts existing synthetic process configuration'
$env:GOOGLE_CLIENT_ID = $null
$env:GOOGLE_CLIENT_SECRET = $null
$missing = Invoke-SyntheticCheck
Assert-SetupCheck (-not $missing.Succeeded -and $missing.Output.Contains('-CredentialsFile') -and $missing.Output.Contains('Google Calendar API')) 'Missing configuration gives concrete setup guidance'
Assert-SetupCheck ([string]::IsNullOrEmpty($env:GOOGLE_CLIENT_ID) -and [string]::IsNullOrEmpty($env:GOOGLE_CLIENT_SECRET)) 'Missing configuration leaves the empty environment unchanged'

$noCredentialOutput = $true
foreach ($capture in $script:captures) {
    foreach ($privateValue in @($syntheticExistingId, $syntheticExistingSecret, $syntheticFileId, $syntheticFileSecret)) {
        if ($capture.Contains($privateValue)) { $noCredentialOutput = $false }
    }
}
Assert-SetupCheck $noCredentialOutput 'All success and failure output omits synthetic credential values'
Write-Host ('PASS: ' + $script:checks + ' Google setup checks; synthetic credentials only, no network or build commands.')
