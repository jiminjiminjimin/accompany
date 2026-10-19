package com.example.pathreport

data class RoutePoint(val latitude: Double, val longitude: Double, val recordedAt: Long,
    val accuracyMeters: Double, val breakBefore: Boolean = false)

/** Sample every five seconds; gaps/restarts are separate lines, never invented travel. */
internal class RouteRecorder {
    private var lastElapsed: Long? = null
    private var interrupted = true
    fun interrupt() { interrupted = true }
    fun accept(sample: GpsSample, nowElapsed: Long, startedAt: Long, previous: RoutePoint?): RoutePoint? {
        if (!validCoordinates(sample.latitude, sample.longitude) || !sample.accuracyMeters.isFinite() ||
            sample.accuracyMeters !in 0.0..30.0 || nowElapsed - sample.elapsedMs !in 0..5_000 ||
            sample.wallTimeMs < startedAt) return null
        val gap = lastElapsed?.let { sample.elapsedMs - it }
        if (gap != null && gap < 5_000) return null
        val timestamp = maxOf(sample.wallTimeMs, (previous?.recordedAt ?: (startedAt - 1)) + 1)
        val jump = previous != null && gap != null && distanceMeters(previous.latitude, previous.longitude,
            sample.latitude, sample.longitude) > 60.0 * gap / 1000.0
        val point = RoutePoint(sample.latitude, sample.longitude, timestamp, sample.accuracyMeters,
            interrupted || gap == null || gap > 30_000 || jump)
        lastElapsed = sample.elapsedMs
        interrupted = false
        return point
    }
}

internal const val MAX_ROUTE_POINTS = 20_000 // About 27 hours at the five-second sampling interval.

fun CareData.appendRoutePoints(tripId: String, points: List<RoutePoint>): CareData {
    val trip = trips.find { it.id == tripId && it.endedAt == null } ?: return this
    if (points.isEmpty()) return this
    val route = (trip.route + points).distinctBy { it.recordedAt }.sortedBy { it.recordedAt }.take(MAX_ROUTE_POINTS)
    return copy(trips = trips.map { if (it.id == tripId) it.copy(route = route) else it }).also { it.validate() }
}

internal fun routeDistanceMeters(route: List<RoutePoint>): Double = route.zipWithNext().sumOf { (a, b) ->
    if (b.breakBefore) 0.0 else distanceMeters(a.latitude, a.longitude, b.latitude, b.longitude)
}
