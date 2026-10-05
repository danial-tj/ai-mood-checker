param([string]$JdkHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
$build = & (Join-Path $PSScriptRoot 'build.ps1') -JdkHome $JdkHome
$inputDirectory = Join-Path $build.Output 'package-input'
$destination = Join-Path $build.Output 'package'
New-Item -ItemType Directory -Path $inputDirectory -Force | Out-Null
Copy-Item -LiteralPath $build.Jar -Destination $inputDirectory
foreach ($library in $build.Dependencies) { Copy-Item -LiteralPath $library -Destination $inputDirectory }
& (Join-Path $build.JdkHome 'bin/jpackage.exe') --type app-image --name AIMoodChecker --app-version 1.0.0 --vendor 'AIMoodChecker' --description 'Local mood journal with optional AI reflections' --input $inputDirectory --dest $destination --main-jar AIMoodChecker.jar --main-class com.aimoodchecker.Launcher --module-path ($build.Fx -join ';') --add-modules 'javafx.controls,javafx.fxml,java.sql,java.net.http,java.desktop,jdk.unsupported,jdk.crypto.ec' --java-options '--enable-native-access=javafx.graphics,ALL-UNNAMED' --jlink-options '--strip-debug --no-header-files --no-man-pages'
if ($LASTEXITCODE -ne 0) { throw 'jpackage failed.' }
$image = Join-Path $destination 'AIMoodChecker'
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'README.md') -Destination (Join-Path $image 'README.md')
$zip = Join-Path $build.Output 'AIMoodChecker-Windows-x64.zip'
Compress-Archive -LiteralPath $image -DestinationPath $zip -CompressionLevel Optimal
Get-FileHash -LiteralPath $zip -Algorithm SHA256 | Format-List
Write-Output "Portable app: $image"
Write-Output "Release archive: $zip"
