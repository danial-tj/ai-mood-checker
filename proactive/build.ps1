param([switch]$Tests)
$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$jdk = $null
foreach ($candidate in @($env:JAVA_HOME,(Join-Path $env:ProgramFiles 'Java/jdk-24'))) {
 if (!$candidate) { continue }
 $candidateCompiler = Join-Path $candidate 'bin/javac.exe'
 if (Test-Path -LiteralPath $candidateCompiler) {
  $version = & $candidateCompiler --version
  if ($version -match '^javac (\d+)' -and [int]$Matches[1] -ge 24) { $jdk = $candidate; break }
 }
}
if (!$jdk) { throw 'Set JAVA_HOME to JDK 24 or newer.' }
$compiler = Join-Path $jdk 'bin/javac.exe'
if (!(Test-Path -LiteralPath $compiler)) { throw 'Set JAVA_HOME to JDK 24 or newer.' }
$cache = Join-Path $env:USERPROFILE '.m2/repository'
$dependencies = @(
 'org/xerial/sqlite-jdbc/3.44.1.0/sqlite-jdbc-3.44.1.0.jar',
 'org/slf4j/slf4j-api/1.7.36/slf4j-api-1.7.36.jar',
 'com/fasterxml/jackson/core/jackson-core/2.16.1/jackson-core-2.16.1.jar',
 'com/fasterxml/jackson/core/jackson-databind/2.16.1/jackson-databind-2.16.1.jar',
 'com/fasterxml/jackson/core/jackson-annotations/2.16.1/jackson-annotations-2.16.1.jar'
)
$output = Join-Path $root 'target'
$classes = Join-Path $output 'classes'
$lib = Join-Path $output 'lib'
New-Item -ItemType Directory -Force -Path $classes,$lib | Out-Null
foreach ($relative in $dependencies) {
 $destination = Join-Path $lib (Split-Path $relative -Leaf)
 if (!(Test-Path -LiteralPath $destination)) {
  $source = Join-Path $cache $relative
  if (!(Test-Path -LiteralPath $source)) { throw "Missing cached dependency. Run 'mvn dependency:go-offline' in the repository first." }
  Copy-Item -LiteralPath $source -Destination $destination
 }
}
$cp = (Get-ChildItem -LiteralPath $lib -Filter '*.jar' | ForEach-Object FullName) -join ';'
$sources = @(Get-ChildItem -LiteralPath (Join-Path $root 'src') -Filter '*.java' -Recurse | ForEach-Object FullName)
if ($Tests) { $sources += @(Get-ChildItem -LiteralPath (Join-Path $root 'test') -Filter '*.java' -Recurse | ForEach-Object FullName) }
& $compiler --release 24 -encoding UTF-8 --add-modules jdk.httpserver -cp $cp -d $classes @sources
if ($LASTEXITCODE -ne 0) { throw 'Planner compilation failed.' }
[pscustomobject]@{ Java=(Join-Path $jdk 'bin/java.exe'); Classpath=($classes+';'+$cp); Root=$root }
