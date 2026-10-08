$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$testOutput = Join-Path $projectRoot 'build/validation/adhesive-rules'
New-Item -ItemType Directory -Force -Path $testOutput | Out-Null
$compiler = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/javac.exe' } else { 'javac.exe' }
$runtime = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { 'java.exe' }
& $compiler -encoding UTF-8 -d $testOutput (Join-Path $projectRoot 'src/main/java/com/mfqm/morefunquicksandmod/gameplay/AdhesiveRules.java') (Join-Path $projectRoot 'src/test/java/com/mfqm/morefunquicksandmod/gameplay/AdhesiveRulesTest.java')
if ($LASTEXITCODE -ne 0) { throw 'Adhesive rule tests did not compile' }
& $runtime -cp $testOutput com.mfqm.morefunquicksandmod.gameplay.AdhesiveRulesTest
if ($LASTEXITCODE -ne 0) { throw 'Adhesive behavior checks failed' }
