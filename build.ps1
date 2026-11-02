# Build script for Minecraft 26.3 Java Agent
$ErrorActionPreference = "Stop"

$jdkPath = "$env:USERPROFILE\Downloads\graalvm-jdk-25_windows-x64_bin\graalvm-jdk-25.0.4+7.1"
$javac = Join-Path $jdkPath "bin\javac.exe"
$jar = Join-Path $jdkPath "bin\jar.exe"

$agentDir = $PSScriptRoot
$srcDir = Join-Path $agentDir "src"
$libDir = Join-Path $agentDir "lib"
$outDir = Join-Path $agentDir "out"
$manifestFile = Join-Path $agentDir "MANIFEST.MF"
$targetJar = Join-Path $agentDir "graal-agent.jar"
$tempJar = Join-Path $agentDir "graal-agent.jar.new"

if (-not (Test-Path $javac)) {
    Write-Error "JDK compiler not found at: $javac"
    exit 1
}

Write-Host ">>> Cleaning out directory..." -ForegroundColor Cyan
if (Test-Path $outDir) {
    Remove-Item -Recurse -Force $outDir
}
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

$cp = (Get-ChildItem -Path $libDir -Filter "*.jar" | ForEach-Object { $_.FullName }) -join ";"
$srcFiles = Get-ChildItem -Recurse -Path $srcDir -Filter "*.java" | ForEach-Object { $_.FullName }

Write-Host ">>> Compiling $($srcFiles.Count) Java source files with GraalVM JDK 25..." -ForegroundColor Cyan
& $javac -encoding UTF-8 -cp $cp -d $outDir $srcFiles

Write-Host ">>> Packaging $targetJar..." -ForegroundColor Cyan
& $jar cfm $tempJar $manifestFile -C $outDir .

try {
    Move-Item -Path $tempJar -Destination $targetJar -Force
    Write-Host ">>> Build successful! Target: $targetJar" -ForegroundColor Green
    Get-Item $targetJar | Select-Object Name, Length, LastWriteTime
} catch {
    Write-Warning "graal-agent.jar is currently locked by a running Minecraft instance."
    Write-Host ">>> Generated $tempJar successfully. Please exit Minecraft and re-run build.ps1 to apply." -ForegroundColor Yellow
}
