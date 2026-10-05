param([string]$JdkHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
$build = & (Join-Path $PSScriptRoot 'build.ps1') -Tests -JdkHome $JdkHome
$directory = Join-Path $build.Output 'functional-fixture'
New-Item -ItemType Directory -Path $directory | Out-Null
Push-Location -LiteralPath $directory
try {
    & $build.Java --enable-native-access=ALL-UNNAMED -cp ((@($build.TestClasses,$build.Classes) + @($build.Dependencies)) -join ';') com.aimoodchecker.FunctionalCheck
    if ($LASTEXITCODE -ne 0) { throw 'Functional checks failed.' }
    Write-Output "Verified build: $($build.Output)"
} finally { Pop-Location }
