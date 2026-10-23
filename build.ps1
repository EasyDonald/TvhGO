$ErrorActionPreference = 'Stop'

$root = $PSScriptRoot
$sdk = Join-Path (Split-Path $root -Parent) 'android-sdk'
$plat = Join-Path $sdk 'platforms\android-34\android.jar'
$bt = Join-Path $sdk 'build-tools\35.0.0'
$app = Join-Path $root 'android'
$build = Join-Path $root 'build'
$javaHome = 'C:\Program Files\Microsoft\jdk-21.0.9.10-hotspot'
$env:JAVA_HOME = $javaHome

Write-Host '== clean =='
if (Test-Path $build) { Remove-Item -Recurse -Force $build }
New-Item -ItemType Directory -Force $build | Out-Null
New-Item -ItemType Directory -Force (Join-Path $build 'gen') | Out-Null
New-Item -ItemType Directory -Force (Join-Path $build 'classes') | Out-Null
New-Item -ItemType Directory -Force (Join-Path $build 'dex') | Out-Null

Write-Host '== aapt2 compile =='
& (Join-Path $bt 'aapt2.exe') compile --dir (Join-Path $app 'res') -o (Join-Path $build 'res.zip')
if ($LASTEXITCODE -ne 0) { throw 'aapt2 compile failed' }

Write-Host '== aapt2 link =='
& (Join-Path $bt 'aapt2.exe') link -o (Join-Path $build 'app-unsigned.apk') `
    -I $plat `
    --manifest (Join-Path $app 'AndroidManifest.xml') `
    -R (Join-Path $build 'res.zip') `
    --java (Join-Path $build 'gen') `
    --auto-add-overlay `
    --min-sdk-version 21 --target-sdk-version 34 `
    --version-code 1 --version-name 1.0
if ($LASTEXITCODE -ne 0) { throw 'aapt2 link failed' }

Write-Host '== javac =='
$files = @()
$files += Get-ChildItem -Recurse (Join-Path $build 'gen') -Filter *.java | ForEach-Object { $_.FullName }
$files += Get-ChildItem -Recurse (Join-Path $app 'src') -Filter *.java | ForEach-Object { $_.FullName }
Write-Host ("   sources: " + $files.Count)
& (Join-Path $javaHome 'bin\javac.exe') -encoding UTF-8 -source 8 -target 8 `
    -bootclasspath $plat -cp $plat -d (Join-Path $build 'classes') $files
if ($LASTEXITCODE -ne 0) { throw 'javac failed' }

Write-Host '== jar + d8 =='
Push-Location (Join-Path $build 'classes')
& (Join-Path $javaHome 'bin\jar.exe') cf (Join-Path $build 'classes.jar') .
Pop-Location
& (Join-Path $bt 'd8.bat') --min-api 21 --lib $plat --output (Join-Path $build 'dex') (Join-Path $build 'classes.jar')
if ($LASTEXITCODE -ne 0) { throw 'd8 failed' }

Write-Host '== package =='
$py = 'C:\Users\Administrator\.dsh\dsh-runtimes\dsh-primary-runtime\dependencies\python\python.exe'
& $py (Join-Path $root 'add_dex.py')
if ($LASTEXITCODE -ne 0) { throw 'add_dex failed' }

Write-Host '== zipalign =='
& (Join-Path $bt 'zipalign.exe') -f 4 (Join-Path $build 'app-unsigned.apk') (Join-Path $build 'app-aligned.apk')
if ($LASTEXITCODE -ne 0) { throw 'zipalign failed' }

Write-Host '== sign =='
$ks = Join-Path $root 'debug.keystore'
if (-not (Test-Path $ks)) {
    & (Join-Path $javaHome 'bin\keytool.exe') -genkeypair -v -keystore $ks -storepass android -keypass android `
        -alias tvhclient -keyalg RSA -keysize 2048 -validity 10000 `
        -dname 'CN=TVH Client, OU=DSH, O=DSH, L=Local, C=CN'
    if ($LASTEXITCODE -ne 0) { throw 'keytool failed' }
}
$apk = Join-Path $root 'TvhGO-1.6.apk'
& (Join-Path $bt 'apksigner.bat') sign --ks $ks --ks-pass pass:android --key-pass pass:android `
    --out $apk (Join-Path $build 'app-aligned.apk')
if ($LASTEXITCODE -ne 0) { throw 'apksigner failed' }

Write-Host '== verify =='
& (Join-Path $bt 'apksigner.bat') verify --print-certs $apk
& (Join-Path $bt 'aapt2.exe') dump badging $apk | Select-Object -First 6

Write-Host ("APK: " + $apk + "  " + (Get-Item $apk).Length + " bytes")


