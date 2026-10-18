param(
    [switch]$Offline,
    [switch]$StopDaemons,
    [switch]$NoDaemon,
    [switch]$NoConfigurationCache,
    [switch]$Info,
    [switch]$JavaNetworkCheck,
    [switch]$NetworkOnly
)

# Set build-process variables only; restore the caller's environment afterwards.
$variableNames = @('JAVA_HOME', 'PATH', 'GRADLE_USER_HOME', 'GRADLE_RO_DEP_CACHE', 'ANDROID_USER_HOME')
$previous = @{}
foreach ($name in $variableNames) {
    $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}

try {
    $jdk = Join-Path $env:ProgramFiles 'Android/Android Studio/jbr'
    if (!(Test-Path (Join-Path $jdk 'bin/java.exe'))) {
        throw "Android Studio JDK not found: $jdk"
    }
    $env:JAVA_HOME = $jdk
    $env:PATH = "$jdk/bin;$env:PATH"
    $env:GRADLE_USER_HOME = Join-Path $PSScriptRoot '.gradle-user-home'
    $env:ANDROID_USER_HOME = Join-Path $env:GRADLE_USER_HOME 'android'
    New-Item -ItemType Directory -Path $env:ANDROID_USER_HOME -Force | Out-Null

    # Do not inherit an unrelated read-only cache when no valid cache is found.
    $env:GRADLE_RO_DEP_CACHE = $null
    # Reuse the existing dependency cache without writing to the SDK directory.
    if ($previous['GRADLE_USER_HOME']) {
        $sharedCache = Join-Path $previous['GRADLE_USER_HOME'] 'caches'
        if (($previous['GRADLE_USER_HOME'] -ne $env:GRADLE_USER_HOME) -and
            (Test-Path (Join-Path $sharedCache 'modules-2'))) {
            $env:GRADLE_RO_DEP_CACHE = $sharedCache
        }
    }

    Write-Output "JAVA_HOME=$env:JAVA_HOME"
    Write-Output "GRADLE_USER_HOME=$env:GRADLE_USER_HOME"
    Write-Output "GRADLE_RO_DEP_CACHE=$env:GRADLE_RO_DEP_CACHE"
    Write-Output "ANDROID_USER_HOME=$env:ANDROID_USER_HOME"
    Write-Output 'JVM/proxy option variables present (values omitted):'
    Get-ChildItem Env: | Where-Object Name -Match 'PROXY|JAVA_TOOL_OPTIONS|JDK_JAVA_OPTIONS|JAVA_OPTS|GRADLE_OPTS' |
        ForEach-Object { Write-Output $_.Name }

    if ($StopDaemons) {
        $daemonHomes = @($previous['GRADLE_USER_HOME'], $env:GRADLE_USER_HOME) |
            Where-Object { $_ } | Select-Object -Unique
        foreach ($daemonHome in $daemonHomes) {
            Write-Output "Stopping Gradle daemons registered in $daemonHome"
            & (Join-Path $PSScriptRoot 'gradlew.bat') -g $daemonHome --stop --console=plain
            if ($LASTEXITCODE -ne 0) { throw "Could not stop daemons in $daemonHome" }
        }
    }

    if ($JavaNetworkCheck -or $NetworkOnly) {
        $probePath = Join-Path $env:GRADLE_USER_HOME 'JavaNetworkProbe.java'
        $probeSource = @'
import java.net.*;
import java.util.*;
import javax.net.ssl.HttpsURLConnection;
class JavaNetworkProbe {
    public static void main(String[] args) {
        String host = "dl.google.com";
        URI uri = URI.create("https://dl.google.com/dl/android/maven2/com/android/tools/build/aapt2/9.4.1-15978811/aapt2-9.4.1-15978811.pom");
        for (String key : List.of("java.home", "java.version", "http.proxyHost", "http.proxyPort", "https.proxyHost", "https.proxyPort", "socksProxyHost", "java.net.useSystemProxies", "java.net.preferIPv4Stack", "java.net.preferIPv6Addresses"))
            System.out.println(key + "=" + System.getProperty(key));
        try { System.out.println("DNS=" + Arrays.toString(InetAddress.getAllByName(host))); }
        catch (Exception e) { e.printStackTrace(System.out); }
        try { System.out.println("ProxySelector=" + ProxySelector.getDefault().select(uri)); }
        catch (Exception e) { e.printStackTrace(System.out); }
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, 443), 10000);
            System.out.println("Java direct TCP: OK " + socket.getRemoteSocketAddress());
        } catch (Exception e) { System.out.println("Java direct TCP: FAILED"); e.printStackTrace(System.out); }
        HttpsURLConnection connection = null;
        try {
            connection = (HttpsURLConnection) uri.toURL().openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setRequestMethod("HEAD");
            System.out.println("Java HTTPS HEAD status=" + connection.getResponseCode());
        } catch (Exception e) { System.out.println("Java HTTPS: FAILED"); e.printStackTrace(System.out); }
        finally { if (connection != null) connection.disconnect(); }
    }
}
'@
        [IO.File]::WriteAllText($probePath, $probeSource, (New-Object System.Text.UTF8Encoding($false)))
        & (Join-Path $jdk 'bin/java.exe') $probePath
        if ($LASTEXITCODE -ne 0) { throw 'Java network probe could not run' }
        # Connection failures are printed above; NetworkOnly is diagnostic, not a connectivity assertion.
    }

    $buildExitCode = 0
    if (!$NetworkOnly) {
        $gradleArguments = @('assembleDebug', '--console=plain', '--stacktrace')
        if ($Offline) { $gradleArguments += '--offline' }
        if ($NoDaemon) { $gradleArguments += '--no-daemon' }
        if ($NoConfigurationCache) { $gradleArguments += '--no-configuration-cache' }
        if ($Info) { $gradleArguments += '--info' }
        & (Join-Path $PSScriptRoot 'gradlew.bat') -p $PSScriptRoot @gradleArguments
        $buildExitCode = $LASTEXITCODE
    }
}
finally {
    foreach ($name in $variableNames) {
        [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process')
    }
}

exit $buildExitCode
