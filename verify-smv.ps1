param(
    [string]$Script = "scripts\11_server_plugins_verify.js"
)

# PowerShell 7 SMV JavaScript Verification Runner
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

New-Item -ItemType Directory -Force -Path $testOutDir | Out-Null
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

$libCp = (Get-ChildItem -Path $libDir -Filter "*.jar" | ForEach-Object { $_.FullName }) -join ";"
$srcFiles = Get-ChildItem -Recurse -Path $srcDir -Filter "*.java" -ErrorAction SilentlyContinue | ForEach-Object { $_.FullName }
$testFiles = Get-ChildItem -Recurse -Path $testDir -Filter "*.java" -ErrorAction SilentlyContinue | ForEach-Object { $_.FullName }

# 1. Compile
if ($srcFiles.Count -gt 0) {
    & $javac -encoding UTF-8 -cp $libCp -d $outDir $srcFiles
}
$brigadierJar = "H:\mc-mod\.minecraft\libraries\com\mojang\brigadier\1.3.11\brigadier-1.3.11.jar"
$testCp = "$outDir;$libCp"
if (Test-Path $brigadierJar) {
    $testCp = "$outDir;$libCp;$brigadierJar"
}
if ($testFiles.Count -gt 0) {
    & $javac -encoding UTF-8 -cp $testCp -d $testOutDir $testFiles
}

# 2. Run JS script
$runCp = "$outDir;$testOutDir;$libCp"
if (Test-Path $brigadierJar) {
    $runCp = "$outDir;$testOutDir;$libCp;$brigadierJar"
}

$scriptPath = Join-Path $agentDir $Script
& $java -ea "-Dfile.encoding=UTF-8" -cp $runCp com.graalagent.test.RunJsRunner $scriptPath
exit $LASTEXITCODE
