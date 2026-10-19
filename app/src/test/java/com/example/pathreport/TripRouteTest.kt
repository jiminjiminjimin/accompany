package com.example.pathreport

import org.junit.Assert.*
import org.junit.Test

class TripRouteTest {
    private val trip = Trip("t", TripDirection.TO_CENTER,
        listOf(TripTarget("e", "테스트", StopPlace("p", "장소", "주소", 37.5, 127.0))), 1_000)
    private fun sample(elapsed: Long, time: Long = elapsed) = GpsSample(37.5, 127.0, 5.0, elapsed, time)
    private fun point(time: Long, gap: Boolean = false) = RoutePoint(37.5, 127.0, time, 5.0, gap)

    @Test fun samplesEveryFiveSecondsAndRejectsUnreliableOrStaleFixes() {
        val recorder = RouteRecorder()
        assertNull(recorder.accept(sample(1_000).copy(accuracyMeters = 31.0), 1_000, 1_000, null))
        assertNull(recorder.accept(sample(1_000), 7_000, 1_000, null))
        assertNull(recorder.accept(sample(1_000).copy(latitude = Double.NaN), 1_000, 1_000, null))
        val first = recorder.accept(sample(1_000), 1_000, 1_000, null)!!
        assertTrue(first.breakBefore)
        assertNull(recorder.accept(sample(2_000), 2_000, 1_000, first))
        assertFalse(recorder.accept(sample(6_000), 6_000, 1_000, first)!!.breakBefore)
    }
    @Test fun gapsAndRestartDoNotDrawInventedConnections() {
        val recorder = RouteRecorder()
        val first = recorder.accept(sample(1_000), 1_000, 1_000, null)!!
        val gap = recorder.accept(sample(40_000), 40_000, 1_000, first)!!
        assertTrue(gap.breakBefore)
        recorder.interrupt()
        assertTrue(recorder.accept(sample(45_000), 45_000, 1_000, gap)!!.breakBefore)
        assertTrue(RouteRecorder().accept(sample(50_000), 50_000, 1_000, gap)!!.breakBefore)
    }
    @Test fun implausibleGpsJumpDoesNotAddTravelDistance() {
        val recorder = RouteRecorder()
        val first = recorder.accept(sample(1_000), 1_000, 1_000, null)!!
        val jump = recorder.accept(sample(6_000).copy(latitude = 38.5), 6_000, 1_000, first)!!
        assertTrue(jump.breakBefore)
        assertEquals(0.0, routeDistanceMeters(listOf(first, jump)), 0.0)
    }
    @Test fun clockCorrectionAndEndTimeStayValid() {
        val recorder = RouteRecorder()
        val first = recorder.accept(sample(1_000, 10_000), 1_000, 1_000, null)!!
        val corrected = recorder.accept(sample(6_000, 5_000), 6_000, 1_000, first)!!
        assertEquals(10_001L, corrected.recordedAt)
        val ended = CareData(trips = listOf(trip)).appendRoutePoints("t", listOf(first, corrected)).endTrip("t", 5_000)
        ended.validate()
        assertEquals(10_001L, ended.trips.single().endedAt)
    }
    @Test fun retryMergesWithoutDuplicatesAndKeepsArrivals() {
        val arrival = Arrival("e", "p", 1_000, 21_000)
        val initial = CareData(trips = listOf(trip.copy(arrivals = listOf(arrival))))
        val first = initial.appendRoutePoints("t", listOf(point(1_000)))
        val saved = first.appendRoutePoints("t", listOf(point(1_000), point(6_000)))
        assertEquals(2, saved.trips.single().route.size)
        assertEquals(listOf(arrival), saved.trips.single().arrivals)
        val ended = saved.endTrip("t", 25_000)
        assertEquals(ended, ended.appendRoutePoints("t", listOf(point(30_000))))
        assertEquals(ended, ended.appendRoutePoints("missing", listOf(point(30_000))))
    }
    @Test fun distanceExcludesMissingSegments() {
        val a = point(1_000)
        val b = point(6_000).copy(latitude = 37.501)
        val c = point(40_000, true).copy(latitude = 38.0)
        assertEquals(routeDistanceMeters(listOf(a, b)), routeDistanceMeters(listOf(a, b, c)), 0.001)
        assertEquals(0.0, routeDistanceMeters(listOf(a, c)), 0.0)
    }
    @Test fun invalidPersistedRoutesFailValidation() {
        assertThrows(IllegalArgumentException::class.java) { trip.copy(route = listOf(point(500))).validate() }
        assertThrows(IllegalArgumentException::class.java) { trip.copy(route = listOf(point(2_000), point(1_000))).validate() }
        assertThrows(IllegalArgumentException::class.java) { trip.copy(route = listOf(point(1_000).copy(accuracyMeters = Double.NaN))).validate() }
    }
}
