package com.example.pathreport

import kotlin.math.*

/** Raw detector samples stay in memory; selected route points are encrypted with the trip. */
data class GpsSample(val latitude: Double, val longitude: Double, val accuracyMeters: Double,
    val elapsedMs: Long, val wallTimeMs: Long, val speedMetersPerSecond: Double? = null)

data class Arrival(val elderId: String, val placeId: String, val estimatedAt: Long,
    val confirmedAt: Long, val source: String = "GPS_DWELL_20S")

object StopPolicy {
    const val RADIUS_METERS = 50.0
    const val MAX_ACCURACY_METERS = 30.0
    const val DWELL_MS = 20_000L
    const val MAX_GAP_MS = 5_000L
    const val MAX_SPEED_MPS = 1.5
    const val ANCHOR_RADIUS_METERS = 10.0
}

/** One detector per trip. Only fresh, sequential samples can confirm a stop. */
class StopDetector(private val trip: Trip) {
    private data class Candidate(val anchor: GpsSample, val estimatedAt: Long)
    private val candidates = mutableMapOf<String, Candidate>()
    private val ready = linkedMapOf<String, Arrival>()
    private var lastSampleMs: Long? = null
    var progressSeconds: Int = 0
        private set
    var message: String = "GPS 위치 수신을 기다리고 있습니다."
        private set

    fun reset(message: String = "정차 확인을 다시 시작합니다.") {
        candidates.clear()
        lastSampleMs = null
        progressSeconds = 0
        this.message = message
        // Confirmed events remain pending until the encrypted store acknowledges them.
    }

    fun pending(saved: List<Arrival>): List<Arrival> {
        val savedIds = saved.map { it.elderId }.toSet()
        ready.keys.removeAll(savedIds)
        return ready.values.toList()
    }

    fun tick(nowElapsedMs: Long) {
        if (lastSampleMs?.let { nowElapsedMs - it > StopPolicy.MAX_GAP_MS } == true)
            reset("GPS 수신이 끊겼습니다. 새 위치부터 정차 시간을 다시 확인합니다.")
    }

    fun accept(sample: GpsSample, nowElapsedMs: Long, saved: List<Arrival>) {
        if (trip.endedAt != null) { reset("종료된 운행입니다."); return }
        pending(saved)
        if (!validCoordinates(sample.latitude, sample.longitude) ||
            !sample.accuracyMeters.isFinite() || sample.accuracyMeters < 0 ||
            sample.accuracyMeters > StopPolicy.MAX_ACCURACY_METERS ||
            sample.elapsedMs < 0 || sample.elapsedMs > nowElapsedMs ||
            nowElapsedMs - sample.elapsedMs > StopPolicy.MAX_GAP_MS || sample.wallTimeMs <= 0) {
            reset("위치가 오래되었거나 오차가 큽니다. 정확도 30m 이하의 새 위치를 기다립니다.")
            return
        }
        // Replayed/cached fixes cannot advance a candidate's clock.
        val previous = lastSampleMs
        if (previous != null && sample.elapsedMs <= previous) return
        if (previous != null && sample.elapsedMs - previous > StopPolicy.MAX_GAP_MS) candidates.clear()
        lastSampleMs = sample.elapsedMs
        if (sample.speedMetersPerSecond?.let { !it.isFinite() || it < 0 || it > StopPolicy.MAX_SPEED_MPS } == true) {
            candidates.clear(); progressSeconds = 0; message = "이동 중입니다. 정차하면 시간을 확인합니다."
            return
        }
        val completed = saved.map { it.elderId }.toSet() + ready.keys
        val remaining = trip.targets.filterNot { it.elderId in completed }
        val places = remaining.map { it.place }.distinctBy { it.id }
        candidates.keys.retainAll(places.map { it.id }.toSet())
        progressSeconds = 0
        for (place in places) {
            if (distanceMeters(sample.latitude, sample.longitude, place.latitude!!, place.longitude!!) > StopPolicy.RADIUS_METERS) {
                candidates.remove(place.id)
                continue
            }
            var candidate = candidates[place.id]
            if (candidate == null || distanceMeters(sample.latitude, sample.longitude,
                    candidate.anchor.latitude, candidate.anchor.longitude) > StopPolicy.ANCHOR_RADIUS_METERS) {
                candidate = Candidate(sample, maxOf(sample.wallTimeMs, trip.startedAt))
                candidates[place.id] = candidate
            }
            val duration = sample.elapsedMs - candidate.anchor.elapsedMs
            progressSeconds = maxOf(progressSeconds, (duration / 1000).coerceAtMost(20).toInt())
            if (duration >= StopPolicy.DWELL_MS) {
                // Derive confirmation from monotonic duration so wall-clock corrections do not reverse time.
                val confirmedAt = candidate.estimatedAt + duration
                remaining.filter { it.place.id == place.id }.forEach { target ->
                    ready.putIfAbsent(target.elderId, Arrival(target.elderId, place.id, candidate.estimatedAt, confirmedAt))
                }
                candidates.remove(place.id)
            }
        }
        message = when {
            ready.isNotEmpty() -> "도착 추정 기록을 저장하고 있습니다."
            remaining.isEmpty() -> "모든 대상의 도착 추정 기록을 저장했습니다."
            candidates.isNotEmpty() -> "정차 확인 ${progressSeconds}/20초"
            else -> "등록 장소 반경 50m 안에서 정차를 기다립니다."
        }
    }
}

internal fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
    return 6_371_000.0 * 2 * asin(sqrt(a.coerceIn(0.0, 1.0)))
}

fun CareData.recordArrivals(tripId: String, arrivals: List<Arrival>): CareData {
    val trip = trips.single { it.id == tripId }
    require(trip.endedAt == null) { "종료된 운행에는 자동 기록을 추가할 수 없습니다." }
    val combined = (trip.arrivals + arrivals).distinctBy { it.elderId }
    return copy(trips = trips.map { if (it.id == tripId) it.copy(arrivals = combined) else it })
        .also { it.validate() }
}

/** Delayed service callbacks cannot append to an ended/missing trip. */
fun CareData.appendDetectedArrivals(tripId: String, arrivals: List<Arrival>): CareData =
    if (trips.any { it.id == tripId && it.endedAt == null }) recordArrivals(tripId, arrivals) else this
