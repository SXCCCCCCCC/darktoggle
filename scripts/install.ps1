param(
    [string]$Adb = '',
    [int]$Port = 0,
    [switch]$Debug,
    [switch]$NoLaunch
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$pkg = 'com.github.sxcccccccc.darktoggle'
if ($Debug) {
    $pkg += '.debug'
}

if (-not $Adb) {
    $candidates = @(
        'D:\Projects\android-sideload\tools\adb-honor-guid\adb.exe',
        'D:\Projects\android-sideload\tools\platform-tools\adb.exe'
    )
    $Adb = $candidates | Where-Object { Test-Path $_ } | Select-Object -First 1
    if (-not $Adb) {
        $Adb = 'adb'
    }
}
# 荣耀 MagicOS 11 需要打过 GUID 补丁的 adb，且必须由它自己起 server。
if ($Port -eq 0 -and $Adb -like '*adb-honor-guid*') {
    $Port = 5039
}

$adbArgs = @()
if ($Port -gt 0) {
    $adbArgs += @('-P', "$Port")
}

$apk = if ($Debug) {
    Join-Path $root 'app\build\outputs\apk\debug\app-debug.apk'
} else {
    Join-Path $root 'out\darktoggle-release.apk'
}
if (-not (Test-Path $apk)) {
    throw "APK not found: $apk. Run scripts/build-release.ps1 first."
}

& $Adb @adbArgs start-server | Out-Null

# Android 14+ 默认拒绝 targetSdk < 23 的应用，必须带 bypass 参数。
& $Adb @adbArgs install -r --bypass-low-target-sdk-block $apk
if ($LASTEXITCODE -ne 0) {
    throw 'adb install failed.'
}

& $Adb @adbArgs shell pm grant $pkg android.permission.WRITE_SECURE_SETTINGS
if ($LASTEXITCODE -ne 0) {
    Write-Warning 'pm grant failed. The app list still works; only the global switch needs this permission.'
}

if (-not $NoLaunch) {
    & $Adb @adbArgs shell am start -n "$pkg/.MainActivity"
}

Write-Output "Installed: $pkg"
