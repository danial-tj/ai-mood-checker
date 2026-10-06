$ErrorActionPreference = 'Stop'
$build = & (Join-Path $PSScriptRoot 'proactive/build.ps1') -Tests
& $build.Java --enable-native-access=ALL-UNNAMED -cp $build.Classpath com.aimoodchecker.planner.PlannerChecks
if ($LASTEXITCODE -ne 0) { throw 'Planner checks failed.' }
