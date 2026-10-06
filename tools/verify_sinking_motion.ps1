$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$testOutput = Join-Path $projectRoot 'run/1.21.11/validation/motion-tests'
New-Item -ItemType Directory -Force -Path $testOutput | Out-Null
$compiler = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/javac.exe' } else { 'javac.exe' }
$runtime = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { 'java.exe' }
& $compiler -encoding UTF-8 -d $testOutput (Join-Path $projectRoot 'src/main/java/com/mfqm/morefunquicksandmod/gameplay/SinkingMotion.java') (Join-Path $projectRoot 'src/test/java/com/mfqm/morefunquicksandmod/gameplay/SinkingMotionTest.java')
if ($LASTEXITCODE -ne 0) { throw 'Motion tests did not compile' }
& $runtime -cp $testOutput com.mfqm.morefunquicksandmod.gameplay.SinkingMotionTest
if ($LASTEXITCODE -ne 0) { throw 'Motion behavior checks failed' }
