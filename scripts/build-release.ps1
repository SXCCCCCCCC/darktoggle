param(
    [string]$JdkHome = 'C:\Program Files\Java\jdk-21.0.10',
    [string]$SdkDir = ''
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot

if (-not $env:JAVA_HOME) {
    if (Test-Path $JdkHome) {
        $env:JAVA_HOME = $JdkHome
    } else {
        Write-Warning "JAVA_HOME is not set; using the Java on PATH (JDK 17+ required)."
    }
}

if (-not $SdkDir) {
    $localProps = Join-Path $root 'local.properties'
    if (Test-Path $localProps) {
        $line = Get-Content $localProps |
            Where-Object { $_ -match '^sdk\.dir=' } |
            Select-Object -First 1
        if ($line) {
            $SdkDir = ($line -replace '^sdk\.dir=', '').Trim()
        }
    }
}
if (-not $SdkDir) {
    $SdkDir = $env:ANDROID_HOME
}
if (-not $SdkDir) {
    $SdkDir = $env:ANDROID_SDK_ROOT
}
if (-not $SdkDir) {
    throw 'Android SDK path not found. Set sdk.dir in local.properties, ANDROID_HOME, or pass -SdkDir.'
}
$env:ANDROID_HOME = $SdkDir
$env:ANDROID_SDK_ROOT = $SdkDir

Push-Location $root
try {
    & .\gradlew.bat :app:assembleRelease
    if ($LASTEXITCODE -ne 0) {
        throw 'Gradle build failed.'
    }

    $apkDir = Join-Path $root 'app\build\outputs\apk\release'
    $apk = Get-ChildItem $apkDir -Filter '*.apk' -ErrorAction Stop |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if (-not $apk) {
        throw "No APK found in $apkDir"
    }

    $outDir = Join-Path $root 'out'
    New-Item -ItemType Directory -Force $outDir | Out-Null
    $dest = Join-Path $outDir 'darktoggle-release.apk'
    Copy-Item $apk.FullName $dest -Force

    $apksigner = Join-Path $SdkDir 'build-tools\36.0.0\apksigner.bat'
    if (Test-Path $apksigner) {
        & $apksigner verify --print-certs $dest
        if ($LASTEXITCODE -ne 0) {
            throw 'APK signature verification failed.'
        }
    } else {
        Write-Warning "apksigner not found at $apksigner; skipped signature verification."
    }

    Get-Item $dest | Select-Object FullName, Length, LastWriteTime
} finally {
    Pop-Location
}
