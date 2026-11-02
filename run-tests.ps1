# PowerShell 7 Test Suite Runner for mc-agent
$ErrorActionPreference = "Stop"

$jdkPath = "$env:USERPROFILE\Downloads\graalvm-jdk-25_windows-x64_bin\graalvm-jdk-25.0.4+7.1"
$javac = Join-Path $jdkPath "bin\javac.exe"
$java = Join-Path $jdkPath "bin\java.exe"

$agentDir = $PSScriptRoot
$srcDir = Join-Path $agentDir "src"
$testDir = Join-Path $agentDir "test"
$libDir = Join-Path $agentDir "lib"
$outDir = Join-Path $agentDir "out"
$testOutDir = Join-Path $agentDir "test-out"

if (Test-Path $testOutDir) {
    Remove-Item -Recurse -Force $testOutDir
}
New-Item -ItemType Directory -Force -Path $testOutDir | Out-Null
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

$libCp = (Get-ChildItem -Path $libDir -Filter "*.jar" | ForEach-Object { $_.FullName }) -join ";"
$srcFiles = Get-ChildItem -Recurse -Path $srcDir -Filter "*.java" -ErrorAction SilentlyContinue | ForEach-Object { $_.FullName }
$testFiles = Get-ChildItem -Recurse -Path $testDir -Filter "*.java" -ErrorAction SilentlyContinue | ForEach-Object { $_.FullName }

# 1. Compile source files
if ($srcFiles.Count -gt 0) {
    & $javac -encoding UTF-8 -cp $libCp -d $outDir $srcFiles
}

# 2. Compile test files
$brigadierJar = "H:\mc-mod\.minecraft\libraries\com\mojang\brigadier\1.3.11\brigadier-1.3.11.jar"
$testCp = "$outDir;$libCp"
if (Test-Path $brigadierJar) {
    $testCp = "$outDir;$libCp;$brigadierJar"
}
if ($testFiles.Count -gt 0) {
    & $javac -encoding UTF-8 -cp $testCp -d $testOutDir $testFiles
}

# 3. Execute Test Runner
$runCp = "$outDir;$testOutDir;$libCp"
if (Test-Path $brigadierJar) {
    $runCp = "$outDir;$testOutDir;$libCp;$brigadierJar"
}
& $java -ea "-Dfile.encoding=UTF-8" -cp $runCp com.mcagent.test.TestRunner @args
exit $LASTEXITCODE
