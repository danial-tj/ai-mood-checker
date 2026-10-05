param([switch]$LaunchPreview, [string]$JdkHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
$build = & (Join-Path $PSScriptRoot 'build.ps1') -Tests -JdkHome $JdkHome
$directory = Join-Path $build.Output 'ui-fixture'
New-Item -ItemType Directory -Path $directory | Out-Null
Push-Location -LiteralPath $directory
try {
    $preview = @()
    if ($LaunchPreview) { $preview += '--preview' }
    & $build.Java --enable-native-access=javafx.graphics,ALL-UNNAMED '-Daimoodchecker.offline=true' --module-path ($build.Fx -join ';') --add-modules javafx.controls,javafx.fxml -cp ((@($build.TestClasses,$build.Classes) + @($build.Dependencies)) -join ';') com.aimoodchecker.UiSmokeCheck @preview
    if ($LASTEXITCODE -ne 0) { throw 'UI checks failed.' }
    Write-Output "Verified UI snapshots: $directory/screenshots"
} finally { Pop-Location }
