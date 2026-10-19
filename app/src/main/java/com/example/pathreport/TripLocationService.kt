package com.example.pathreport

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*

/** Started from the visible, authenticated app. Screen locking does not own this service's lifetime. */
class TripLocationService : Service(), LocationListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var store: SecureCareStore
    private lateinit var locations: LocationManager
    private lateinit var wakeLock: PowerManager.WakeLock
    private var session: Job? = null
    private var trip: Trip? = null
    private var detector: StopDetector? = null
    private var routeRecorder = RouteRecorder()
    private val pendingRoute = mutableListOf<RoutePoint>()
    private var requested = false
    private var requestedAt = 0L
    private var wakeRenewedAt = 0L
    private var lastSaveAttempt = -5_000L
    private var lastFixMs: Long? = null
    private var accuracy: Int? = null
    private var saveFailed = false
    private var serviceTripId: String? = null
    private var notificationText = ""
    private var foreground = false
    private var finished = false

    override fun onBind(intent: Intent?) = null
    override fun onCreate() {
        super.onCreate()
        store = SecureCareStore(applicationContext)
        locations = getSystemService(LocationManager::class.java)
        wakeLock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "pathreport:trip-gps").apply { setReferenceCounted(false) }
        if (Build.VERSION.SDK_INT >= 26) getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "운행 감지 상태", NotificationManager.IMPORTANCE_LOW).apply {
                description = "운행 중 GPS 감지와 중단 상태를 알립니다."
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            })
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getStringExtra("tripId")
        if (id.isNullOrBlank()) { stopSelf(startId); return START_NOT_STICKY }
        if (session?.isActive == true && serviceTripId == id && !finished) return START_NOT_STICKY
        session?.cancel()
        releaseGps()
        trip = null; detector = null; finished = false; saveFailed = false; lastSaveAttempt = -5_000L
        routeRecorder = RouteRecorder()
        pendingRoute.clear()
        serviceTripId = id
        try {
            val initialNotification = notification("운행 감지를 준비하고 있습니다.")
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION_ID, initialNotification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            else startForeground(NOTIFICATION_ID, initialNotification)
            foreground = true
        } catch (_: Exception) {
            TripRuntime.publish(TrackingInfo(id, "위치 권한 또는 서비스 실행 조건을 확인한 뒤 앱에서 감지를 재개해 주세요."))
            stopSelf(startId)
            return START_NOT_STICKY
        }
        TripRuntime.publish(TrackingInfo(id, "GPS 수신을 준비하고 있습니다.", phase = TrackingPhase.STARTING, serviceAlive = true))
        session = scope.launch {
            var loadedRevision = -1L
            while (isActive) {
                try {
                    val revision = StoreChanges.revision.value
                    if (trip == null || loadedRevision != revision) {
                        val data = withContext(Dispatchers.IO) { store.loadAndMigrate() }
                        trip = data.trips.find { it.id == id }
                        loadedRevision = revision
                    }
                    val current = trip
                    if (current == null || current.endedAt != null) { finish("운행이 종료되어 GPS 감지를 멈췄습니다.", false); break }
                    if (detector == null) detector = StopDetector(current)
                    val engine = requireNotNull(detector)
                    if (engine.pending(current.arrivals).isEmpty() && pendingRoute.isEmpty()) saveFailed = false

                    val allowed = permitted()
                    val gpsEnabled = runCatching { locations.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)
                    when {
                        TripRuntime.endingTripId == id -> {
                            releaseGps(); engine.reset("운행 종료를 저장하고 있습니다.")
                        }
                        !allowed -> { releaseGps(); engine.reset("정확한 위치 권한이 없어 감지가 중단되었습니다. 앱에서 권한을 확인해 주세요.") }
                        !gpsEnabled -> { releaseGps(); engine.reset("GPS가 꺼져 감지가 중단되었습니다. 기기의 위치 설정을 켜 주세요.") }
                        else -> {
                            if (!requested) {
                                engine.reset("GPS 위치 수신을 기다리고 있습니다.")
                                requestedAt = SystemClock.elapsedRealtime()
                                locations.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1_000L, 0f, this@TripLocationService, Looper.getMainLooper())
                                requested = true
                            }
                            if (!wakeLock.isHeld || SystemClock.elapsedRealtime() - wakeRenewedAt >= 30_000L) {
                                wakeLock.acquire(60_000L)
                                wakeRenewedAt = SystemClock.elapsedRealtime()
                            }
                            engine.tick(SystemClock.elapsedRealtime())
                        }
                    }
                    val pending = engine.pending(current.arrivals)
                    if ((pending.isNotEmpty() || pendingRoute.isNotEmpty()) && TripRuntime.endingTripId != id && SystemClock.elapsedRealtime() - lastSaveAttempt >= 5_000L) {
                        lastSaveAttempt = SystemClock.elapsedRealtime()
                        try {
                            val routeBatch = pendingRoute.toList()
                            val saved = withContext(Dispatchers.IO) {
                                store.update { latest -> latest.appendRoutePoints(id, routeBatch).appendDetectedArrivals(id, pending) }
                            }
                            pendingRoute.removeAll(routeBatch.toSet())
                            trip = saved.trips.find { it.id == id }
                            loadedRevision = -1L
                            saveFailed = false
                        } catch (e: CancellationException) { throw e }
                        catch (_: Exception) { saveFailed = true }
                    }
                    publish()
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) {
                    releaseGps()
                    detector?.reset("자료 또는 위치 상태를 확인하지 못해 감지가 중단되었습니다.")
                    publish("자료 또는 위치 상태를 확인하지 못해 감지가 중단되었습니다. 다시 확인 중입니다.")
                    loadedRevision = -1L
                    delay(4_000)
                }
                delay(1_000)
            }
        }
        return START_NOT_STICKY
    }
    private fun permitted() = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    override fun onLocationChanged(location: Location) {
        val current = trip ?: return
        if (!requested || finished || current.endedAt != null || TripRuntime.endingTripId == current.id) return
        if (!permitted()) { releaseGps(); detector?.reset("위치 권한이 해제되어 감지가 중단되었습니다."); publish(); return }
        val elapsed = location.elapsedRealtimeNanos / 1_000_000L
        if (elapsed < requestedAt) return
        lastFixMs = elapsed
        accuracy = if (location.hasAccuracy() && location.accuracy.isFinite() && location.accuracy >= 0) location.accuracy.toInt() else null
        val sample = GpsSample(location.latitude, location.longitude,
            if (location.hasAccuracy()) location.accuracy.toDouble() else Double.NaN, elapsed, location.time,
            if (location.hasSpeed()) location.speed.toDouble() else null)
        detector?.accept(sample, SystemClock.elapsedRealtime(), current.arrivals)
        if (current.route.size + pendingRoute.size < MAX_ROUTE_POINTS) {
            routeRecorder.accept(sample, SystemClock.elapsedRealtime(), current.startedAt,
                pendingRoute.lastOrNull() ?: current.route.lastOrNull())?.let { pendingRoute.add(it) }
        }
        publish()
    }
    override fun onProviderDisabled(provider: String) { releaseGps(); detector?.reset("GPS가 꺼져 감지가 중단되었습니다."); publish() }
    override fun onProviderEnabled(provider: String) {}
    @Deprecated("Legacy callback") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    private fun releaseGps() {
        routeRecorder.interrupt()
        runCatching { locations.removeUpdates(this) }
        requested = false
        if (wakeLock.isHeld) runCatching { wakeLock.release() }
        lastFixMs = null
        accuracy = null
    }
    private fun publish(overrideMessage: String? = null) {
        val current = trip
        val ending = TripRuntime.endingTripId == serviceTripId
        val text = overrideMessage ?: when {
            ending -> "운행 종료를 저장하고 있습니다."
            saveFailed -> "운행 기록 저장에 실패했습니다. 기록을 유지하고 다시 저장하는 중입니다."
            current != null && current.route.size + pendingRoute.size >= MAX_ROUTE_POINTS -> "경로 기록 한도에 도달했습니다. 운행을 종료하고 새로 시작해 주세요."
            current != null && current.arrivals.size == current.targets.size -> "모든 대상의 도착을 기록했습니다. 운행 종료까지 이동 경로를 기록합니다."
            else -> detector?.message ?: "GPS 위치 수신을 기다리고 있습니다."
        }
        val fresh = lastFixMs?.let { SystemClock.elapsedRealtime() - it in 0..StopPolicy.MAX_GAP_MS } == true
        TripRuntime.publish(TrackingInfo(serviceTripId, text, detector?.pending(current?.arrivals.orEmpty()).orEmpty(),
            needsPermission = !permitted(), phase = when {
                ending -> TrackingPhase.ENDING
                saveFailed -> TrackingPhase.SAVE_FAILED
                overrideMessage != null || !requested || !fresh -> TrackingPhase.WAITING
                else -> TrackingPhase.RUNNING
            }, serviceAlive = true, pendingRoute = pendingRoute.toList(), accuracyMeters = if (fresh) accuracy else null))
        val generic = when {
            ending -> "운행 종료를 저장하고 있습니다."
            saveFailed -> "운행 기록 저장을 다시 시도하고 있습니다. 앱에서 확인해 주세요."
            overrideMessage != null || !requested || !fresh -> "GPS 감지 대기 또는 중단 상태입니다. 앱에서 확인해 주세요."
            else -> "화면이 꺼져도 운행 도착과 이동 경로를 기록합니다."
        }
        if (foreground && notificationText != generic) {
            notificationText = generic
            runCatching { getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(generic)) }
        }
    }
    private fun notification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val open = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("안심 동행 · 운행 기록").setContentText(text).setContentIntent(open)
            .setCategory(NotificationCompat.CATEGORY_SERVICE).setOngoing(true).setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build()
    }
    private fun finish(message: String, complete: Boolean) {
        finished = true
        releaseGps()
        TripRuntime.publish(TrackingInfo(serviceTripId, message, phase = if (complete) TrackingPhase.COMPLETE else TrackingPhase.STOPPED))
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    override fun onDestroy() {
        scope.cancel()
        releaseGps()
        if (!finished) TripRuntime.publish(TrackingInfo(serviceTripId, "GPS 감지가 중단되었습니다. 앱에서 감지 재개를 눌러 주세요."))
        serviceTripId?.let { TripRuntime.clearEnd(it) }
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
    companion object { private const val CHANNEL = "trip-location"; private const val NOTIFICATION_ID = 3401 }
}
