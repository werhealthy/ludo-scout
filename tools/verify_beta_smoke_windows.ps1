# Beta smoke v5.12.203. No uninstall, wipe, downgrade, Firebase upload or checkout change.
param([Parameter(Mandatory=$true)][string]$Commit, [switch]$Install)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repo = 'C:\Users\checc\Documents\GitHub\ludo-scout'
$expectedSigner = 'C7DF7C31D0FE0D059307F4DE7B67BE5992DC87EC73E623CC9E8B4E87C63D8710'
$dir = $null
$worktreeAdded = $false
$beforeHead = $null
$originalVersionOverride = [Environment]::GetEnvironmentVariable('LUDO_VERSION_CODE', 'Process')
$originalAndroidHome = [Environment]::GetEnvironmentVariable('ANDROID_HOME', 'Process')
try {
    if ($PSVersionTable.PSVersion.Major -ne 5) { throw 'Windows PowerShell 5.1 required.' }
    if ($Commit -notmatch '^[0-9a-f]{40}$') { throw 'Pinned commit SHA required.' }
    if (-not (Test-Path -LiteralPath $repo -PathType Container)) { throw 'Git checkout unavailable.' }
    $beforeHead = (& git -C $repo rev-parse HEAD).Trim()
    if ($LASTEXITCODE -ne 0) { throw 'HEAD read failed.' }
    $kind = (& git -C $repo cat-file -t $Commit).Trim()
    if ($LASTEXITCODE -ne 0 -or $kind -cne 'commit') { throw 'Commit unavailable.' }

    $object = $Commit + ':app/build.gradle'
    $gradleContent = (& git -C $repo show $object | Out-String)
    if ($LASTEXITCODE -ne 0 -or
        -not $gradleContent.Contains("versionName '5.12.203-beta-smoke'") -or
        -not $gradleContent.Contains('1002034 + ciVersionCode.toInteger()')) {
        throw 'Unexpected source versioning.'
    }

    $sdk = $env:ANDROID_HOME
    if (-not $sdk) { $sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
    $aapt = Join-Path $sdk 'build-tools\35.0.0\aapt.exe'
    $signer = Join-Path $sdk 'build-tools\35.0.0\apksigner.bat'
    $adb = Join-Path $sdk 'platform-tools\adb.exe'
    foreach ($tool in @($aapt, $signer, $adb)) {
        if (-not (Test-Path -LiteralPath $tool -PathType Leaf)) {
            throw ('Android SDK tool missing: ' + $tool)
        }
    }
    $env:ANDROID_HOME = $sdk

    $userProps = Join-Path $env:USERPROFILE '.gradle\gradle.properties'
    $tokenConfigured = $false
    if (Test-Path -LiteralPath $userProps) {
        $tokenConfigured = [bool](Select-String -LiteralPath $userProps -Pattern '^\s*BGG_TOKEN\s*=\s*\S+' -Quiet)
    }
    if (-not $tokenConfigured -and -not $env:BGG_TOKEN) {
        throw 'BGG_TOKEN missing from user Gradle configuration or environment.'
    }

    $dir = Join-Path $env:TEMP ('ludo-beta-smoke-' + [guid]::NewGuid().ToString('N'))
    & git -C $repo worktree add --detach $dir $Commit
    if ($LASTEXITCODE -ne 0) { throw 'Isolated worktree creation failed.' }
    $worktreeAdded = $true
    Remove-Item Env:\LUDO_VERSION_CODE -ErrorAction SilentlyContinue

    Push-Location $dir
    try {
        & (Join-Path $dir 'gradlew.bat') --no-daemon ':app:assembleDebug'
        if ($LASTEXITCODE -ne 0) { throw 'Android build failed.' }
    } finally { Pop-Location }
    Write-Host 'BUILD=PASS'

    $apk = Join-Path $dir 'app\build\outputs\apk\debug\app-debug.apk'
    if (-not (Test-Path -LiteralPath $apk -PathType Leaf)) { throw 'APK missing.' }
    $signOutput = @(& $signer verify --print-certs $apk)
    if ($LASTEXITCODE -ne 0) { throw 'APK signer verification failed.' }
    $signMatch = [regex]::Match(($signOutput -join [Environment]::NewLine), '(?im)^Signer #1 certificate SHA-256 digest:\s*([0-9A-F]{64})\s*$')
    if (-not $signMatch.Success -or
        $signMatch.Groups[1].Value.ToUpperInvariant() -cne $expectedSigner) {
        throw 'Unexpected signing certificate. No installation performed.'
    }
    Write-Host 'SIGNER=PASS'

    $badging = @(& $aapt dump badging $apk)
    if ($LASTEXITCODE -ne 0) { throw 'APK badging inspection failed.' }
    $package = $badging | Where-Object { $_ -match '^package:\s' } | Select-Object -First 1
    if (-not $package -or $package -notmatch "name='it\.vintedaffari\.app'" -or
        $package -notmatch "versionCode='1002034'" -or
        $package -notmatch "versionName='5\.12\.203-beta-smoke'") {
        throw 'APK identity/version mismatch. No installation performed.'
    }
    Write-Host 'APK_VERSION=PASS 5.12.203-beta-smoke/1002034'

    $downloads = Join-Path $env:USERPROFILE 'Downloads'
    if (-not (Test-Path -LiteralPath $downloads -PathType Container)) {
        throw 'Downloads destination missing.'
    }
    $output = Join-Path $downloads 'LudoScout-5.12.203-beta-smoke-1002034.apk'
    if (Test-Path -LiteralPath $output) {
        $oldHash = (Get-FileHash -LiteralPath $output -Algorithm SHA256).Hash
        $newHash = (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash
        if ($oldHash -cne $newHash) { throw 'Existing Downloads APK differs; refusing overwrite.' }
    } else {
        Copy-Item -LiteralPath $apk -Destination $output
    }
    Write-Host ('APK_VERIFIED_FILE=' + $output)

    if ($Install) {
        $devices = @(& $adb devices | Where-Object { $_ -match '^\S+\s+device\s*$' })
        if ($LASTEXITCODE -ne 0 -or $devices.Count -ne 1) {
            throw 'Exactly one authorized Android device required.'
        }
        $prior = & $adb shell dumpsys package it.vintedaffari.app | Out-String
        if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect installed Ludo.' }
        $m = [regex]::Match($prior, 'versionCode=(\d+)')
        if (-not $m.Success) { throw 'Ludo not currently installed; refusing fresh install.' }
        $current = [int]$m.Groups[1].Value
        Write-Host ('DEVICE_VERSION_BEFORE=' + $current)
        if ($current -ge 1002034) { throw 'Installed version >= candidate; refusing downgrade.' }
        $installResult = @(& $adb install -r $output)
        if ($LASTEXITCODE -ne 0 -or -not (@($installResult | Where-Object { $_ -match '^Success\s*$' }).Count -eq 1)) {
            throw ('Non-destructive adb upgrade failed: ' + ($installResult -join ' | '))
        }
        $post = & $adb shell dumpsys package it.vintedaffari.app | Out-String
        if ($LASTEXITCODE -ne 0 -or -not [regex]::IsMatch($post,'versionCode=1002034\b')) {
            throw 'Unexpected installed version after upgrade.'
        }
        Write-Host 'DEVICE_INSTALL_R=PASS'
        Write-Host 'DEVICE_VERSION_AFTER=1002034'
    } else {
        Write-Host 'DEVICE_INSTALL=NOT_REQUESTED'
    }
} finally {
    [Environment]::SetEnvironmentVariable('LUDO_VERSION_CODE', $originalVersionOverride, 'Process')
    [Environment]::SetEnvironmentVariable('ANDROID_HOME', $originalAndroidHome, 'Process')
    if ($worktreeAdded) {
        & git -C $repo worktree remove --force $dir 2>$null
        if ($LASTEXITCODE -ne 0) { Write-Warning ('Temporary worktree remains: ' + $dir) }
    }
    if ($beforeHead) {
        $afterHead = (& git -C $repo rev-parse HEAD).Trim()
        if ($LASTEXITCODE -eq 0 -and $afterHead -ceq $beforeHead) {
            Write-Host 'LOCAL_CHECKOUT_HEAD_UNCHANGED=PASS'
        } else {
            Write-Warning 'Local checkout HEAD changed unexpectedly.'
        }
    }
}
