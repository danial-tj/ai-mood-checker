param([int]$Port = 8471, [string]$DataFile)
$ErrorActionPreference = 'Stop'
$build = & (Join-Path $PSScriptRoot 'proactive/build.ps1')
$options = @('--enable-native-access=ALL-UNNAMED', '--add-modules', 'jdk.httpserver', "-Dplanner.port=$Port", "-Dplanner.root=$($build.Root)")
if ($DataFile) { $options += "-Dplanner.data=$([IO.Path]::GetFullPath($DataFile))" }
& $build.Java @options -cp $build.Classpath com.aimoodchecker.planner.PlannerServer
if ($LASTEXITCODE -ne 0) { throw 'Planner stopped with an error.' }
