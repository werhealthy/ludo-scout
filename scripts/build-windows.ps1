param(
    [Parameter(Mandatory=$true)][ValidateRange(1,2000000000)][int]$VersionSequence,
    [switch]$InstallUsb,
    [switch]$DistributeFirebase
)
$ErrorActionPreference = 'Stop'
function Invoke-Checked {
    param([string]$Program,[string[]]$Arguments)
    & $Program @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Program failed with exit code $LASTEXITCODE" }
}
$root = Split-Path $PSScriptRoot -Parent
Push-Location $root
$previousVersion = $env:LUDO_VERSION_CODE
try {
    if (-not $env:JAVA_HOME) {
        $studioJdk = Join-Path $env:ProgramFiles 'Android\Android Studio\jbr'
        if (Test-Path $studioJdk) { $env:JAVA_HOME = $studioJdk }
    }
    if (-not $env:JAVA_HOME) { throw 'Set JAVA_HOME to the JDK used by Android Studio.' }
    if (-not $env:ANDROID_HOME) { $env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
    $signer = Join-Path $env:ANDROID_HOME 'build-tools\35.0.0\apksigner.bat'
    $key = $env:ANDROID_DEBUG_KEYSTORE
    if (-not $key) { $key = Join-Path $env:USERPROFILE '.android\debug.keystore' }
    if (-not (Test-Path $signer)) { throw 'Install Android SDK Build-Tools 35.0.0 in Android Studio SDK Manager.' }
    if (-not (Test-Path $key)) { throw 'Original signing keystore missing. Do not generate a replacement.' }
    $expected = 'C7DF7C31D0FE0D059307F4DE7B67BE5992DC87EC73E623CC9E8B4E87C63D8710'
    $keytool = Join-Path $env:JAVA_HOME 'bin\keytool.exe'
    $certificate = [IO.Path]::GetTempFileName()
    try {
        Invoke-Checked $keytool @('-exportcert','-keystore',$key,'-alias','androiddebugkey','-storepass','android','-file',$certificate)
        if ((Get-FileHash $certificate -Algorithm SHA256).Hash -ne $expected) { throw 'Signing key differs from installed Ludo.' }
    } finally { Remove-Item $certificate -ErrorAction SilentlyContinue }
    if ($DistributeFirebase) {
        foreach ($name in @('GOOGLE_APPLICATION_CREDENTIALS','FIREBASE_APP_ID','FIREBASE_TESTERS')) {
            if (-not [Environment]::GetEnvironmentVariable($name)) { throw "Missing local variable: $name" }
        }
        if (-not (Test-Path $env:GOOGLE_APPLICATION_CREDENTIALS)) { throw 'Firebase JSON file missing.' }
        $firebase = (Get-Command firebase.cmd -ErrorAction Stop).Source
    }
    $env:ANDROID_DEBUG_KEYSTORE = $key
    $env:LUDO_VERSION_CODE = "$VersionSequence"
    Invoke-Checked (Join-Path $root 'gradlew.bat') @('--no-daemon',':app:testDebugUnitTest',':app:assembleDebug')
    $apk = Join-Path $root 'app\build\outputs\apk\debug\app-debug.apk'
    $signingOutput = & $signer verify --print-certs $apk
    if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed.' }
    $digestLine = $signingOutput | Where-Object { $_ -match '^Signer #1 certificate SHA-256 digest:' }
    $digest = ($digestLine -replace '^Signer #1 certificate SHA-256 digest:\s*','').Trim().ToUpperInvariant()
    if ($digest -ne $expected) { throw 'Unexpected built APK signer.' }
    if ($InstallUsb) {
        Invoke-Checked (Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe') @('install','-r',$apk)
    }
    if ($DistributeFirebase) {
        Invoke-Checked $firebase @('appdistribution:distribute',$apk,'--app',$env:FIREBASE_APP_ID,'--testers',$env:FIREBASE_TESTERS,'--release-notes',"Ludo Windows sequence $VersionSequence")
    }
    Write-Host "APK verified: $apk"
} finally {
    $env:LUDO_VERSION_CODE = $previousVersion
    Pop-Location
}
