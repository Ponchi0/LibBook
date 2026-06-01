# Проброс порта 3000 с ПК на телефон (USB). Запустите при подключённом телефоне.
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) {
    Write-Error "adb не найден: $adb"
    exit 1
}
& $adb reverse tcp:3000 tcp:3000
& $adb reverse --list
Write-Host "В debug-сборке LibBook используется http://127.0.0.1:3000"
