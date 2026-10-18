param([switch]$Connected, [switch]$Lint, [switch]$Online)
$ErrorActionPreference = 'Stop'
$names = @('JAVA_HOME', 'PATH', 'GRADLE_USER_HOME', 'GRADLE_RO_DEP_CACHE', 'ANDROID_USER_HOME')
$previous = @{}
foreach ($name in $names) { $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
try {
    $env:JAVA_HOME = Join-Path $env:ProgramFiles 'Android/Android Studio/jbr'
    $env:PATH = "$env:JAVA_HOME/bin;$env:PATH"
    $env:GRADLE_USER_HOME = Join-Path $PSScriptRoot '.gradle-user-home'
    $env:ANDROID_USER_HOME = Join-Path $env:GRADLE_USER_HOME 'android'
    $env:GRADLE_RO_DEP_CACHE = $null
    if ($previous['GRADLE_USER_HOME']) {
        $sharedCache = Join-Path $previous['GRADLE_USER_HOME'] 'caches'
        if (($previous['GRADLE_USER_HOME'] -ne $env:GRADLE_USER_HOME) -and (Test-Path (Join-Path $sharedCache 'modules-2'))) {
            $env:GRADLE_RO_DEP_CACHE = $sharedCache
        }
    }
    $tasks = @('testDebugUnitTest', 'assembleDebugAndroidTest')
    if ($Lint) { $tasks += 'lintDebug' }
    if ($Connected) { $tasks += 'connectedDebugAndroidTest' }
    $options = @('--no-daemon', '--no-configuration-cache', '--console=plain', '-Pkotlin.compiler.execution.strategy=in-process')
    if (!$Online) { $options += '--offline' }
    & "$PSScriptRoot/gradlew.bat" -p $PSScriptRoot @tasks @options
    $result = $LASTEXITCODE
} finally {
    foreach ($name in $names) { [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process') }
}
exit $result
