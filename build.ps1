param([switch]$Tests, [string]$JdkHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$candidates = @($JdkHome, (Join-Path $env:ProgramFiles 'Java/jdk-24'))
$selectedJdk = $null
foreach ($candidate in $candidates) {
    if (!$candidate) { continue }
    $compiler = Join-Path $candidate 'bin/javac.exe'
    if (!(Test-Path -LiteralPath $compiler)) { continue }
    $compilerVersion = & $compiler --version
    if ($compilerVersion -match '^javac (\d+)' -and [int]$Matches[1] -ge 24) { $selectedJdk = $candidate; break }
}
if (!$selectedJdk) { throw 'Set JAVA_HOME or pass -JdkHome to a JDK 24 or newer installation.' }
$JdkHome = $selectedJdk
$javac = Join-Path $JdkHome 'bin/javac.exe'
$cache = Join-Path $env:USERPROFILE '.m2/repository'
[xml]$pom = Get-Content -LiteralPath (Join-Path $root 'pom.xml') -Raw
$fxVersion = $pom.project.properties.'javafx.version'
$sqliteVersion = $pom.project.properties.'sqlite.version'
$jacksonVersion = $pom.project.properties.'jackson.version'
$fx = @('base','graphics','controls','fxml') | ForEach-Object { Join-Path $cache "org/openjfx/javafx-$_/$fxVersion/javafx-$_-$fxVersion-win.jar" }
$libs = @(
 (Join-Path $cache "org/xerial/sqlite-jdbc/$sqliteVersion/sqlite-jdbc-$sqliteVersion.jar"),
 (Join-Path $cache 'org/slf4j/slf4j-api/1.7.36/slf4j-api-1.7.36.jar')
)
$libs += @('core','databind','annotations') | ForEach-Object { Join-Path $cache "com/fasterxml/jackson/core/jackson-$_/$jacksonVersion/jackson-$_-$jacksonVersion.jar" }
foreach ($file in @($fx) + @($libs)) { if (!(Test-Path -LiteralPath $file)) { throw "Missing cached dependency: $file. Run Maven dependency:go-offline first." } }
$output = Join-Path $root ('target/build-' + [Guid]::NewGuid().ToString('N'))
$classes = Join-Path $output 'classes'
$testClasses = Join-Path $output 'test-classes'
New-Item -ItemType Directory -Path $classes,$testClasses -Force | Out-Null
$sources = @(Get-ChildItem -LiteralPath (Join-Path $root 'src/main/java') -Filter '*.java' -Recurse | Where-Object Name -NotLike 'Test*.java' | ForEach-Object FullName)
& $javac -encoding UTF-8 --release 24 --module-path ($fx -join ';') --add-modules javafx.controls,javafx.fxml -cp ($libs -join ';') -d $classes @sources
if ($LASTEXITCODE -ne 0) { throw 'Application compilation failed.' }
Copy-Item -Path (Join-Path $root 'src/main/resources/*') -Destination $classes -Recurse -Force
if ($Tests) {
    $testSources = @(Get-ChildItem -LiteralPath (Join-Path $root 'src/test/java') -Filter '*.java' -Recurse | ForEach-Object FullName)
    & $javac -encoding UTF-8 --release 24 --module-path ($fx -join ';') --add-modules javafx.controls,javafx.fxml -cp ((@($classes) + @($libs)) -join ';') -d $testClasses @testSources
    if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed.' }
}
$jarPath = Join-Path $output 'AIMoodChecker.jar'
& (Join-Path $JdkHome 'bin/jar.exe') --create --file $jarPath --main-class com.aimoodchecker.Launcher -C $classes .
if ($LASTEXITCODE -ne 0) { throw 'Jar creation failed.' }
$result = [pscustomobject]@{ Root=$root; Output=$output; Classes=$classes; TestClasses=$testClasses; Jar=$jarPath; Java=(Join-Path $JdkHome 'bin/java.exe'); JdkHome=$JdkHome; Fx=@($fx); Dependencies=@($libs) }
$result | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $output 'build-info.json') -Encoding utf8
$result
