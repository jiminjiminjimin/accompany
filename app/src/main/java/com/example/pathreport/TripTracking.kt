package com.example.pathreport

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class TrackingPhase { STOPPED, STARTING, RUNNING, WAITING, SAVE_FAILED, ENDING, COMPLETE }
data class TrackingInfo(val tripId: String? = null,
    val message: String = "GPS 감지가 중지되어 있습니다. 앱에서 감지를 재개해 주세요.",
    val pending: List<Arrival> = emptyList(), val needsPermission: Boolean = false,
    val phase: TrackingPhase = TrackingPhase.STOPPED, val serviceAlive: Boolean = false,
    val accuracyMeters: Int? = null, val notificationsAllowed: Boolean = true)

/** Contains no names, addresses or route history. It resets on process death. */
object TripRuntime {
    private val mutableStatus = MutableStateFlow(TrackingInfo())
    val status = mutableStatus.asStateFlow()
    // Main-thread barrier: a confirmed end action prevents accepting more GPS samples immediately.
    var endingTripId: String? = null
        private set
    fun publish(info: TrackingInfo) { mutableStatus.value = info }
    fun beginEnd(tripId: String): List<Arrival> {
        endingTripId = tripId
        return status.value.takeIf { it.tripId == tripId }?.pending.orEmpty()
    }
    fun clearEnd(tripId: String) { if (endingTripId == tripId) endingTripId = null }
    fun start(context: Context, tripId: String) {
        if (status.value.tripId == tripId && status.value.serviceAlive) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            publish(TrackingInfo(tripId, "정확한 위치 권한을 허용한 뒤 GPS 감지를 시작해 주세요.", needsPermission = true))
            return
        }
        val gps = context.getSystemService(android.location.LocationManager::class.java)
        if (!runCatching { gps.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) }.getOrDefault(false)) {
            publish(TrackingInfo(tripId, "GPS가 꺼져 있거나 지원되지 않습니다. 기기의 위치 설정을 켠 뒤 감지를 재개해 주세요."))
            return
        }
        if ((context as? LifecycleOwner)?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) != true) {
            publish(TrackingInfo(tripId, "앱 화면으로 돌아온 뒤 GPS 감지를 재개해 주세요."))
            return
        }
        try {
            publish(TrackingInfo(tripId, "운행 감지 서비스를 시작하고 있습니다.", phase = TrackingPhase.STARTING))
            ContextCompat.startForegroundService(context, Intent(context, TripLocationService::class.java).putExtra("tripId", tripId))
        } catch (_: Exception) {
            publish(TrackingInfo(tripId, "감지 서비스를 시작하지 못했습니다. 위치 설정을 확인하고 다시 시도해 주세요."))
        }
    }
}

/** A saved active trip alone does not prove the OS is still running its location service. */
fun trackingFor(trip: Trip?, runtime: TrackingInfo, fineGranted: Boolean, notificationsAllowed: Boolean): TrackingInfo {
    val info = when {
        trip == null -> TrackingInfo()
        trip.arrivals.size == trip.targets.size -> TrackingInfo(trip.id, "모든 대상의 기록을 저장하여 GPS 감지를 종료했습니다.", phase = TrackingPhase.COMPLETE)
        runtime.tripId == trip.id -> runtime
        else -> TrackingInfo(trip.id, "진행 중인 운행이 있지만 GPS 감지는 중단되어 있습니다. 감지 재개를 눌러 주세요.")
    }
    return info.copy(needsPermission = !fineGranted, notificationsAllowed = notificationsAllowed)
}
