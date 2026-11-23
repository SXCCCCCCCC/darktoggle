param(
    [string]$JdkHome = 'C:\Program Files\Java\jdk-21.0.10',
    [switch]$Force
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$keystoreDir = Join-Path $root 'keystore'
$keystore = Join-Path $keystoreDir 'darktoggle-release.jks'
$propsFile = Join-Path $root 'keystore.properties'

if ((Test-Path $keystore) -or (Test-Path $propsFile)) {
    if (-not $Force) {
        throw 'Keystore or keystore.properties already exists. Use -Force only if you really want a new signing identity.'
    }
}

$keytool = Join-Path $JdkHome 'bin\keytool.exe'
if (-not (Test-Path $keytool)) {
    $keytool = 'keytool'
}

$alphabet = (48..57) + (65..90) + (97..122)
$password = -join (1..32 | ForEach-Object { [char]($alphabet | Get-Random) })

New-Item -ItemType Directory -Force $keystoreDir | Out-Null

& $keytool -genkeypair -v `
    -keystore $keystore `
    -storetype PKCS12 `
    -alias darktoggle `
    -keyalg RSA `
    -keysize 4096 `
    -validity 10000 `
    -dname 'CN=DarkToggle, OU=Android, O=SXCCCCCCCC, C=CN' `
    -storepass $password `
    -keypass $password
if ($LASTEXITCODE -ne 0) {
    throw 'keytool failed.'
}

@(
    'storeFile=keystore/darktoggle-release.jks'
    "storePassword=$password"
    'keyAlias=darktoggle'
    "keyPassword=$password"
) | Set-Content -Path $propsFile -Encoding utf8

Write-Output "Keystore:  $keystore"
Write-Output "Password:  written to $propsFile"
Write-Output ''
Write-Output 'Back up BOTH files. Losing them means you cannot update an installed app.'
