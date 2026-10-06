$ErrorActionPreference = 'Stop'
$build = & (Join-Path $PSScriptRoot 'proactive/build.ps1')
& node (Join-Path $PSScriptRoot 'proactive/test/http-checks.mjs') $build.Java $build.Classpath $build.Root
if ($LASTEXITCODE -ne 0) { throw 'Planner HTTP checks failed.' }
