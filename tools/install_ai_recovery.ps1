param([Parameter(Mandatory = $true)][string]$ListingIds)
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
Set-Location $repo
function Require-Exit([string]$Action) {
    if ($LASTEXITCODE -ne 0) { throw "$Action fallita (exit $LASTEXITCODE). Nessuna reinstallazione distruttiva viene eseguita." }
}
$python = (Get-Command python -ErrorAction Stop).Source
Get-Command java -ErrorAction Stop | Out-Null
$sdk = $env:ANDROID_HOME
if (-not $sdk) { $sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
$adb = Join-Path $sdk 'platform-tools\adb.exe'
if (-not (Test-Path $adb)) { throw 'ADB non trovato nel tuo Android SDK.' }
$env:ADB = $adb
$devices = @(& $adb devices | Where-Object { $_ -match '^\S+\s+device$' })
if ($devices.Count -ne 1) { throw 'Serve un solo telefono ADB autorizzato e collegato.' }
$package = & $adb shell dumpsys package it.vintedaffari.app
Require-Exit 'Lettura versione installata'
$match = [regex]::Match(($package -join "`n"), 'versionCode=(\d+)')
if (-not $match.Success) { throw 'App esistente non trovata: questa procedura aggiorna soltanto una installazione presente.' }
$oldVersion = [int]$match.Groups[1].Value
$previousVersionEnv = $env:LUDO_VERSION_CODE
$env:LUDO_VERSION_CODE = [string][Math]::Max(1, $oldVersion - 1000000 + 1)
try {
    foreach ($test in @('ai_engine_wait_backoff','ai_recovery_state_machine','ai_recovery_pipeline','ai_real_listings','ai_local_usb_android','ai_local_usb_bridge','ai_filtered_recovery','ai_recovery_bgg_handoff','ai_recovery_durable_marker','bgg_state_monotonicity_v51211','bgg_review_write_accountability_v51212','bgg_sqlite_state_machine_v51215','bgg_version_affinity_v51214')) {
        & $python "regression\$test.py"
        Require-Exit "Regressione $test"
    }
    & .\gradlew.bat --no-daemon :app:lintDebug :app:testDebugUnitTest :app:compileDebugJavaWithJavac :app:assembleDebug
    Require-Exit 'Build/lint/unit test Android'
} finally { $env:LUDO_VERSION_CODE = $previousVersionEnv }
$apk = Join-Path $repo 'app\build\outputs\apk\debug\app-debug.apk'
$buildTools = Get-ChildItem (Join-Path $sdk 'build-tools') -Directory |
    Where-Object { $_.Name -match '^\d+\.\d+\.\d+$' } |
    Sort-Object { [version]$_.Name } -Descending | Select-Object -First 1
if (-not $buildTools) { throw 'Android build-tools non trovati.' }
$signature = & (Join-Path $buildTools.FullName 'apksigner.bat') verify --print-certs $apk
Require-Exit 'Verifica firma APK'
$digest = [regex]::Match(($signature -join "`n"), 'Signer #1 certificate SHA-256 digest:\s*([a-fA-F0-9]+)').Groups[1].Value
if ($digest.ToUpperInvariant() -ne 'C7DF7C31D0FE0D059307F4DE7B67BE5992DC87EC73E623CC9E8B4E87C63D8710') {
    throw 'Firma diversa da quella della beta: aggiornamento bloccato per preservare i dati.'
}
$reports = Join-Path $env:TEMP ('ludo-ai-recovery-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Path $reports | Out-Null
$headers = @{ Authorization = 'Bearer ludo-local-usb-debug' }
$status = $null
try { $status = Invoke-RestMethod 'http://127.0.0.1:8765/v1/status' -Headers $headers -TimeoutSec 10 } catch {}
if (-not $status) {
    $bridge = Join-Path $repo 'tools\ai_local_bridge.py'
    Start-Process -FilePath $python -ArgumentList "-u `"$bridge`"" -WorkingDirectory $repo -RedirectStandardOutput (Join-Path $reports 'bridge.log') -RedirectStandardError (Join-Path $reports 'bridge-errors.log') | Out-Null
    foreach ($attempt in 1..10) {
        Start-Sleep -Seconds 2
        try { $status = Invoke-RestMethod 'http://127.0.0.1:8765/v1/status' -Headers $headers -TimeoutSec 10; break } catch {}
    }
}
if (-not $status -or -not $status.local_online) { throw "Ollama/Qwen o bridge non pronti. Log: $reports" }
& $python tools\ai_recovery_runtime_audit.py --ids $ListingIds --output (Join-Path $reports 'ai-recovery-before.json')
Require-Exit 'Audit prima dell aggiornamento'
& $adb install -r $apk
Require-Exit 'Aggiornamento APK con dati preservati'
& $adb reverse tcp:8765 tcp:8765
Require-Exit 'Connessione USB Qwen'
& $adb shell am start -n it.vintedaffari.app/.MainActivity
Require-Exit 'Apertura app'
$installed = & $adb shell dumpsys package it.vintedaffari.app
Require-Exit 'Verifica versione aggiornata'
$installed | Select-String 'versionCode=|versionName='
if (($installed -join "`n") -notmatch 'versionName=5\.12\.201-ai-category-state') { throw 'La versione installata non coincide con il fix.' }
Write-Host 'Lascia l app aperta con AI via USB attiva. Attendo il batch e BGG per 18 minuti senza riavviarla.'
$after = Join-Path $reports 'ai-recovery-after.json'
Start-Sleep -Seconds 1080
& $python tools\ai_recovery_runtime_audit.py --ids $ListingIds --output $after
Require-Exit 'Audit runtime'
$result = Get-Content $after -Raw | ConvertFrom-Json
$passed = $result.cohort_pass
if ($passed) { Write-Host 'Batch verificato: tutti gli annunci richiesti hanno prova AI e sono ACTIVE nello stato BGG successivo.' }
else { Write-Host 'Prova ancora aperta: il report conserva il punto di arresto; non dichiarare il bug chiuso.' }
Write-Host "Carica questo file in chat: $after"
