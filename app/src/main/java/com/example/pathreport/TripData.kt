package com.example.pathreport

import java.util.UUID

enum class TripDirection(val label: String) { TO_CENTER("등원"), FROM_CENTER("하원") }

/** Immutable snapshots retain the chosen identity and location after management edits. */
data class TripTarget(val elderId: String, val elderName: String, val place: StopPlace)
data class Trip(val id: String = UUID.randomUUID().toString(), val direction: TripDirection,
    val targets: List<TripTarget>, val startedAt: Long, val endedAt: Long? = null, val arrivals: List<Arrival> = emptyList()) {
    fun validate() {
        require(id.isNotBlank() && startedAt > 0)
        require(endedAt == null || endedAt >= startedAt)
        require(targets.isNotEmpty() && targets.map { it.elderId }.distinct().size == targets.size)
        targets.forEach {
            require(it.elderId.isNotBlank() && it.elderName.isNotBlank())
            val p = it.place
            require(p.id.isNotBlank() && p.name.isNotBlank())
            require(p.latitude != null && p.longitude != null && validCoordinates(p.latitude, p.longitude))
        }
        require(arrivals.map { it.elderId }.distinct().size == arrivals.size)
        arrivals.forEach { arrival ->
            require(targets.any { it.elderId == arrival.elderId && it.place.id == arrival.placeId })
            require(arrival.source == "GPS_DWELL_20S")
            require(arrival.estimatedAt >= startedAt && arrival.confirmedAt >= arrival.estimatedAt)
            require(arrival.confirmedAt - arrival.estimatedAt >= StopPolicy.DWELL_MS)
            require(endedAt == null || arrival.confirmedAt <= endedAt)
        }
        require(targets.groupBy { it.place.id }.values.all { group -> group.map { it.place }.distinct().size == 1 })
    }
}

fun CareData.startTrip(direction: TripDirection, selections: Map<String, String>, now: Long): CareData {
    validate()
    require(trips.none { it.endedAt == null }) { "이미 진행 중인 운행이 있습니다." }
    require(selections.isNotEmpty()) { "운행 대상을 선택해 주세요." }
    val targets = selections.map { (elderId, placeId) ->
        require(links.contains(PlaceElder(placeId, elderId)))
        TripTarget(elderId, elders.single { it.id == elderId }.name, places.single { it.id == placeId })
    }
    val trip = Trip(direction = direction, targets = targets, startedAt = now)
    return copy(trips = trips + trip).also { it.validate() }
}

fun CareData.endTrip(tripId: String, now: Long): CareData {
    validate()
    val trip = trips.single { it.id == tripId }
    require(trip.endedAt == null)
    // A wall-clock correction must not make an otherwise valid trip impossible to finish.
    return copy(trips = trips.map { if (it.id == tripId) it.copy(endedAt = maxOf(now, it.startedAt, it.arrivals.maxOfOrNull { arrival -> arrival.confirmedAt } ?: it.startedAt)) else it })
        .also { it.validate() }
}
