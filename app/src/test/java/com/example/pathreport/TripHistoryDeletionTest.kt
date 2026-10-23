package com.example.pathreport

import org.junit.Assert.*
import org.junit.Test

class TripHistoryDeletionTest {
    private val place = StopPlace("p", "장소", "", 37.5, 127.0)
    private val targets = listOf(TripTarget("a", "동명이인", place), TripTarget("b", "동명이인", place))
    private val finished = Trip("done", TripDirection.TO_CENTER, targets, 1000, 22000,
        listOf(Arrival("a", "p", 1000, 21000), Arrival("b", "p", 1000, 21000)))
    private val active = Trip("active", TripDirection.FROM_CENTER, targets, 23000)
    private val data = CareData(places = listOf(place), elders = listOf(Elder("a", "동명이인"), Elder("b", "동명이인")),
        links = listOf(PlaceElder("p", "a"), PlaceElder("p", "b")), trips = listOf(finished, active))

    @Test fun deletesOnlySelectedIdentityAndPreservesRegistrationAndActiveTrip() {
        val result = data.deleteTripHistory("done", "a")
        assertEquals(listOf("b"), result.trips.first().targets.map { it.elderId })
        assertEquals(listOf("b"), result.trips.first().arrivals.map { it.elderId })
        assertEquals(data.places, result.places)
        assertEquals(data.elders, result.elders)
        assertEquals(data.links, result.links)
        assertEquals(active, result.trips.last())
        assertEquals(result.withoutStoredAddresses(), CareCodec.decode(CareCodec.encode(result)))
    }
    @Test fun deletesWholeTripOrLastTargetWithoutLeavingInvalidEmptyTrip() {
        assertEquals(listOf(active), data.deleteTripHistory("done").trips)
        assertEquals(listOf(active), data.deleteTripHistory("done", "a").deleteTripHistory("done", "b").trips)
    }
    @Test fun refusesActiveTripAndHandlesRepeatedOrMissingSelection() {
        assertThrows(IllegalArgumentException::class.java) { data.deleteTripHistory("active") }
        assertEquals(data, data.deleteTripHistory("missing"))
        assertEquals(data, data.deleteTripHistory("done", "missing"))
        val result = data.deleteTripHistory("done")
        assertEquals(result, result.deleteTripHistory("done"))
    }
}
