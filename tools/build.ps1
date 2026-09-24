param([string[]]$Tasks=@('testDebugUnitTest','lintDebug','assembleDebug'))
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
if (-not $env:JAVA_HOME) {
    $localTools=Join-Path $projectRoot '../../work/android-tools/jdk'
    if (Test-Path $localTools) { $env:JAVA_HOME=(Get-ChildItem $localTools -Directory | Select-Object -First 1).FullName }
}
if (-not $env:JAVA_HOME) { throw 'Set JAVA_HOME to JDK 17 or build with Android Studio.' }
Push-Location $projectRoot
try { & ./gradlew.bat @Tasks --console=plain; if ($LASTEXITCODE) { throw "Gradle failed: $LASTEXITCODE" } }
finally { Pop-Location }
