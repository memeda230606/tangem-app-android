param(
    [Parameter(Mandatory = $true)]
    [string]$Device,

    [string]$Output = "build/logs/mocked-nfc.log",

    [switch]$Clear
)

$adbCommand = Get-Command adb -ErrorAction SilentlyContinue
if ($adbCommand) {
    $adb = $adbCommand.Source
} else {
    $adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
}

if (-not (Test-Path -LiteralPath $adb)) {
    throw "adb was not found. Add platform-tools to PATH or install the Android SDK."
}

$outputPath = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot "..\$Output"))
$outputDirectory = Split-Path -Parent $outputPath
New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null

if ($Clear) {
    & $adb -s $Device logcat -c
    if ($LASTEXITCODE -ne 0) {
        throw "Unable to clear Logcat for device $Device."
    }
}

Write-Host "Capturing mocked NFC logs from $Device"
Write-Host "Output: $outputPath"
Write-Host "Press Ctrl+C to stop."

& $adb -s $Device logcat -v threadtime "TangemNfcDemo:I" "TangemMockServer:I" "*:S" |
    Tee-Object -FilePath $outputPath
