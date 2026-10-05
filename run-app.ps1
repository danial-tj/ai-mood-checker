param([switch]$Offline, [string]$JdkHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
$build = & (Join-Path $PSScriptRoot 'build.ps1') -JdkHome $JdkHome
$mode = @()
if ($Offline) { $mode += '-Daimoodchecker.offline=true' }
& $build.Java --enable-native-access=javafx.graphics,ALL-UNNAMED "-Daimoodchecker.dataDir=$PSScriptRoot" @mode --module-path ($build.Fx -join ';') --add-modules javafx.controls,javafx.fxml -cp ((@($build.Classes) + @($build.Dependencies)) -join ';') com.aimoodchecker.Main
if ($LASTEXITCODE -ne 0) { throw 'Application exited unsuccessfully.' }
