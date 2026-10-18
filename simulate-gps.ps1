param(
    [ValidatePattern('^emulator-[0-9]+$')][string]$Serial = 'emulator-5554',
    [ValidateRange(-90, 90)][double]$Latitude = 37.5,
    [ValidateRange(-180, 180)][double]$Longitude = 127.0,
    [ValidateRange(1, 300)][int]$Seconds = 25,
    [string]$AdbPath = (Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe')
)
$ErrorActionPreference = 'Stop'
if (!(Test-Path -LiteralPath $AdbPath)) { throw 'ADB 경로가 없습니다. -AdbPath에 Android SDK의 adb.exe 경로를 지정해 주세요.' }
$culture = [Globalization.CultureInfo]::InvariantCulture
$lon = $Longitude.ToString($culture)
$lat = $Latitude.ToString($culture)
# The emulator console takes longitude first. Never send mock coordinates to a physical device.
for ($second = 0; $second -lt $Seconds; $second++) {
    & $AdbPath -s $Serial emu geo fix $lon $lat | Out-Null
    if ($LASTEXITCODE -ne 0) { throw '가상 GPS 전송 실패: 에뮬레이터 실행 상태와 Serial을 확인해 주세요.' }
    Write-Progress -Activity '에뮬레이터 가상 GPS 전송' -Status "$($second + 1)/$Seconds 초" -PercentComplete (($second + 1) * 100 / $Seconds)
    Start-Sleep -Seconds 1
}
Write-Progress -Activity '에뮬레이터 가상 GPS 전송' -Completed
Write-Output '가상 GPS 전송 완료. 앱의 운행 상세에서 도착 추정 기록을 확인해 주세요.'
