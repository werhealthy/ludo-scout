# One-time local Android Firebase config import for Ludo Scout.
# Source: the owner's own Firebase Console download, NEVER copied into Git.
# No network access, no credential handling, no Git branch changes, no APK install.
param(
    [string]$ConfigPath
)
$ErrorActionPreference = 'Stop'

function Fail([string]$Reason) {
    throw $Reason
}

try {
    if (-not (Get-Command git.exe -ErrorAction SilentlyContinue)) {
        Fail 'Git for Windows non trovato.'
    }

    $repo = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
    if (-not (Test-Path -LiteralPath (Join-Path $repo 'app\build.gradle') -PathType Leaf)) {
        Fail 'Esegui questo script dalla cartella tools della repository Ludo Scout.'
    }
    $origin = [string](& git -C $repo config --get remote.origin.url)
    if ($LASTEXITCODE -ne 0 -or $origin.Trim() -notmatch '^(?:https://github\.com/|git@github\.com:|ssh://git@github\.com/)werhealthy/ludo-scout(?:\.git)?/?$') {
        Fail 'La repository Git non corrisponde a werhealthy/ludo-scout.'
    }

    if ([string]::IsNullOrWhiteSpace($ConfigPath)) {
        $download = Join-Path $env:USERPROFILE 'Downloads\google-services.json'
        if (Test-Path -LiteralPath $download -PathType Leaf) {
            $ConfigPath = $download
        } else {
            Add-Type -AssemblyName System.Windows.Forms
            $picker = New-Object System.Windows.Forms.OpenFileDialog
            $picker.Title = 'Scegli google-services.json scaricato da Firebase'
            $picker.Filter = 'Firebase Android JSON|google-services.json|JSON files|*.json'
            $picker.CheckFileExists = $true
            if ($picker.ShowDialog() -ne [System.Windows.Forms.DialogResult]::OK) {
                Fail 'Operazione annullata senza copiare file.'
            }
            $ConfigPath = $picker.FileName
            $picker.Dispose()
        }
    }
    if (-not (Test-Path -LiteralPath $ConfigPath -PathType Leaf)) {
        Fail 'File google-services.json non trovato.'
    }

    $config = Get-Content -LiteralPath $ConfigPath -Raw -Encoding UTF8 | ConvertFrom-Json
    if ([string]$config.project_info.project_id -cne 'ludo-scout' -or
        [string]$config.project_info.project_number -cne '977410404587') {
        Fail 'Progetto Firebase diverso da Ludo Scout.'
    }
    $matching = @($config.client | Where-Object {
        $_.client_info.android_client_info.package_name -ceq 'it.vintedaffari.app'
    })
    if ($matching.Count -ne 1) {
        Fail 'Package Android mancante o ambiguo.'
    }
    if ([string]$matching[0].client_info.mobilesdk_app_id -cne
        '1:977410404587:android:cc620ef66162ba4266ceae') {
        Fail 'Firebase Android App ID inatteso.'
    }
    if ([string]::IsNullOrWhiteSpace([string]$matching[0].api_key[0].current_key)) {
        Fail 'Firebase Android API key mancante.'
    }

    $relative = 'app/google-services.json'
    $target = Join-Path $repo 'app\google-services.json'
    $tracked = @(& git -C $repo ls-files -- $relative)
    if ($LASTEXITCODE -ne 0 -or $tracked.Count -gt 0) {
        Fail 'Il JSON risulta tracciato da Git o il controllo Git non e riuscito.'
    }
    & git -C $repo check-ignore -q -- $relative
    if ($LASTEXITCODE -ne 0) {
        Fail 'Il JSON non e ignorato da Git. Non lo copio: aggiorna prima il branch della PR.'
    }

    $sourceHash = (Get-FileHash -LiteralPath $ConfigPath -Algorithm SHA256).Hash
    if (Test-Path -LiteralPath $target -PathType Leaf) {
        $existingHash = (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash
        if ($existingHash -ne $sourceHash) {
            Fail 'Configurazione diversa gia presente. Non sovrascrivo nulla.'
        }
    } else {
        Copy-Item -LiteralPath $ConfigPath -Destination $target -ErrorAction Stop
    }
    if ((Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash -ne $sourceHash) {
        Fail 'Verifica integrita del file copiato fallita.'
    }
    $gitStatus = @(& git -C $repo status --porcelain --untracked-files=all -- $relative)
    if ($LASTEXITCODE -ne 0 -or $gitStatus.Count -gt 0) {
        Fail 'Il JSON compare nello stato Git; fermarsi senza creare commit.'
    }

    Write-Host ''
    Write-Host 'OK: configurazione Firebase Android installata in locale.' -ForegroundColor Green
    Write-Host 'Git: file ignorato e non tracciato.'
    Write-Host 'Firestore, Hermes, build APK e telefono non modificati.'
    exit 0
} catch {
    Write-Host ('ERRORE: ' + $_.Exception.Message) -ForegroundColor Red
    Write-Host 'Configurazione non confermata. Nessun upload eseguito.'
    exit 1
}
