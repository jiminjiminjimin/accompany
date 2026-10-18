package com.example.pathreport

import org.junit.Assert.*
import org.junit.Test

class TripDataTest {
    private val p = StopPlace("p", "가상 장소", "가상 주소", 37.5, 127.0)
    private val e = Elder("e", "가상 어르신")
    private fun data() = CareData(listOf(p), listOf(e), listOf(PlaceElder(p.id, e.id)))
    @Test fun startEndAndSingleActiveTrip() {
        val started = data().startTrip(TripDirection.TO_CENTER, mapOf("e" to "p"), 100)
        assertNull(started.trips.single().endedAt)
        assertThrows(IllegalArgumentException::class.java) { started.startTrip(TripDirection.FROM_CENTER, mapOf("e" to "p"), 200) }
        val ended = started.endTrip(started.trips.single().id, 200)
        assertEquals(200L, ended.trips.single().endedAt)
        assertThrows(IllegalArgumentException::class.java) { ended.endTrip(ended.trips.single().id, 300) }
        assertEquals(2, ended.startTrip(TripDirection.FROM_CENTER, mapOf("e" to "p"), 300).trips.size)
    }
    @Test fun snapshotsSurviveEditsDeletionAndLegacyMigration() {
        val started = data().startTrip(TripDirection.TO_CENTER, mapOf("e" to "p"), 100)
        val edited = started.copy(places = listOf(p.copy(name = "수정 장소")), elders = listOf(e.copy(name = "수정 이름")))
        val removed = edited.withoutPlace("p").withoutElder("e")
        removed.validate()
        assertEquals(TripTarget("e", "가상 어르신", p), removed.trips.single().targets.single())
        assertEquals(started.trips, migrateLegacyItems(removed, listOf(LegacyPerson("가상", "가상 주소")), "digest").trips)
    }
    @Test fun invalidSelectionsAndCoordinatesRejected() {
        assertThrows(IllegalArgumentException::class.java) { data().startTrip(TripDirection.TO_CENTER, emptyMap(), 100) }
        assertThrows(IllegalArgumentException::class.java) { data().startTrip(TripDirection.TO_CENTER, mapOf("e" to "unknown"), 100) }
        assertThrows(IllegalArgumentException::class.java) { data().copy(places = listOf(p.copy(latitude = null, longitude = null))).startTrip(TripDirection.TO_CENTER, mapOf("e" to "p"), 100) }
        assertThrows(IllegalArgumentException::class.java) { data().startTrip(TripDirection.TO_CENTER, mapOf("e" to "p"), 0) }
    }
    @Test fun invalidStoredTripsRejectedAndClockCorrectionCanFinish() {
        val started = data().startTrip(TripDirection.TO_CENTER, mapOf("e" to "p"), 100)
        val trip = started.trips.single()
        assertThrows(IllegalArgumentException::class.java) { started.copy(trips = listOf(trip, trip)).validate() }
        assertThrows(IllegalArgumentException::class.java) { trip.copy(targets = trip.targets + trip.targets).validate() }
        assertThrows(IllegalArgumentException::class.java) { trip.copy(endedAt = 99).validate() }
        assertEquals(100L, started.endTrip(trip.id, 90).trips.single().endedAt)
    }
}
