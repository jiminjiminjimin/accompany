package com.example.pathreport

import org.junit.Assert.*
import org.junit.Test

class StopDetectorTest {
    private val place = StopPlace("p", "가상 장소", "가상 주소", 37.5, 127.0)
    private val trip = Trip(id = "t", direction = TripDirection.TO_CENTER,
        targets = listOf(TripTarget("e", "가상 어르신", place)), startedAt = 1_000_000)
    private fun sample(time: Long, lat: Double = 37.5, accuracy: Double = 5.0, speed: Double? = 0.0) =
        GpsSample(lat, 127.0, accuracy, time, trip.startedAt + time, speed)
    private fun feed(detector: StopDetector, from: Long, to: Long, saved: List<Arrival> = emptyList()) {
        for (time in from..to step 1_000L) detector.accept(sample(time), time, saved)
    }
    @Test fun requiresTwentySecondsAndKeepsStartTime() {
        val detector = StopDetector(trip)
        feed(detector, 0, 19_000)
        assertTrue(detector.pending(emptyList()).isEmpty())
        detector.accept(sample(20_000), 20_000, emptyList())
        val arrival = detector.pending(emptyList()).single()
        assertEquals(trip.startedAt, arrival.estimatedAt)
        assertEquals(trip.startedAt + 20_000, arrival.confirmedAt)
    }
    @Test fun leavingAndReenteringRestartsDwell() {
        val detector = StopDetector(trip)
        feed(detector, 0, 15_000)
        detector.accept(sample(16_000, lat = 37.501), 16_000, emptyList())
        feed(detector, 17_000, 36_000)
        assertTrue(detector.pending(emptyList()).isEmpty())
        feed(detector, 37_000, 37_000)
        assertEquals(trip.startedAt + 17_000, detector.pending(emptyList()).single().estimatedAt)
    }
    @Test fun badAccuracyAndInvalidFixesResetDwell() {
        for (accuracy in listOf(31.0, Double.NaN, -1.0, Double.POSITIVE_INFINITY)) {
            val detector = StopDetector(trip)
            feed(detector, 0, 15_000)
            detector.accept(sample(16_000, accuracy = accuracy), 16_000, emptyList())
            feed(detector, 17_000, 36_000)
            assertTrue(detector.pending(emptyList()).isEmpty())
        }
        val detector = StopDetector(trip)
        feed(detector, 0, 15_000)
        detector.accept(sample(16_000, lat = Double.NaN), 16_000, emptyList())
        assertEquals(0, detector.progressSeconds)
    }
    @Test fun gapsAndStaleFixesCannotConfirm() {
        val detector = StopDetector(trip)
        feed(detector, 0, 15_000)
        detector.accept(sample(21_000), 21_000, emptyList())
        assertTrue(detector.pending(emptyList()).isEmpty())
        assertEquals(0, detector.progressSeconds)
        feed(detector, 22_000, 35_000)
        detector.accept(sample(36_000), 42_000, emptyList())
        assertEquals(0, detector.progressSeconds)
        detector.accept(sample(44_000), 43_000, emptyList())
        assertTrue(detector.pending(emptyList()).isEmpty())
    }
    @Test fun repeatedFixAndTimerAloneNeverCreateArrival() {
        val detector = StopDetector(trip)
        detector.accept(sample(0), 0, emptyList())
        for (time in 1_000L..4_000L step 1_000L) detector.accept(sample(0), time, emptyList())
        assertEquals(0, detector.progressSeconds)
        detector.tick(21_000)
        assertTrue(detector.pending(emptyList()).isEmpty())
        feed(detector, 22_000, 41_000)
        assertTrue(detector.pending(emptyList()).isEmpty())
    }
    @Test fun fastMotionAndSlowDriftInsideRadiusDoNotCountAsStop() {
        val detector = StopDetector(trip)
        for (time in 0L..30_000L step 1_000L)
            detector.accept(sample(time, speed = 3.0), time, emptyList())
        assertTrue(detector.pending(emptyList()).isEmpty())
        for (time in 31_000L..61_000L step 1_000L)
            detector.accept(sample(time, lat = 37.5 + (time - 31_000) / 1000.0 * 0.00001, speed = null), time, emptyList())
        assertTrue(detector.pending(emptyList()).isEmpty())
    }
    @Test fun boundedJitterAndMissingSpeedCanStillConfirm() {
        val detector = StopDetector(trip)
        for (time in 0L..20_000L step 1_000L)
            detector.accept(sample(time, lat = 37.5 + if (time % 2000 == 0L) 0.00001 else -0.00001, speed = null), time, emptyList())
        assertEquals(1, detector.pending(emptyList()).size)
    }
    @Test fun oneStopRecordsAllSelectedPeopleAtThatPlaceOnly() {
        val other = place.copy(id = "q", latitude = 38.0)
        val many = trip.copy(targets = trip.targets + TripTarget("e2", "가상 어르신", place) + TripTarget("e3", "다른 대상", other))
        val detector = StopDetector(many)
        feed(detector, 0, 20_000)
        assertEquals(setOf("e", "e2"), detector.pending(emptyList()).map { it.elderId }.toSet())
    }
    @Test fun retriesKeepConfirmedEventAndPersistedRecordPreventsDuplicates() {
        val detector = StopDetector(trip)
        feed(detector, 0, 20_000)
        val pending = detector.pending(emptyList())
        detector.reset()
        assertEquals(pending, detector.pending(emptyList()))
        assertTrue(detector.pending(pending).isEmpty())
        feed(detector, 21_000, 45_000, pending)
        assertTrue(detector.pending(pending).isEmpty())
        val restarted = StopDetector(trip.copy(arrivals = pending))
        feed(restarted, 46_000, 70_000, pending)
        assertTrue(restarted.pending(pending).isEmpty())
    }
    @Test fun pauseAndClockCorrectionDoNotFinishEarly() {
        val detector = StopDetector(trip)
        feed(detector, 0, 15_000)
        detector.reset()
        for (time in 16_000L..35_000L step 1_000L)
            detector.accept(sample(time).copy(wallTimeMs = trip.startedAt - 5000), time, emptyList())
        assertTrue(detector.pending(emptyList()).isEmpty())
        detector.accept(sample(36_000).copy(wallTimeMs = trip.startedAt + 999_999), 36_000, emptyList())
        assertEquals(trip.startedAt, detector.pending(emptyList()).single().estimatedAt)
        assertEquals(trip.startedAt + 20_000, detector.pending(emptyList()).single().confirmedAt)
    }
    @Test fun storedRecordsValidateAndAreIdempotent() {
        val arrival = Arrival("e", "p", trip.startedAt, trip.startedAt + 20_000)
        val original = CareData(trips = listOf(trip))
        val saved = original.recordArrivals("t", listOf(arrival))
        assertEquals(saved, saved.recordArrivals("t", listOf(arrival)))
        val ended = saved.endTrip("t", trip.startedAt - 100)
        assertEquals(arrival.confirmedAt, ended.trips.single().endedAt)
        assertThrows(IllegalArgumentException::class.java) { ended.recordArrivals("t", listOf(arrival)) }
        assertThrows(IllegalArgumentException::class.java) { original.recordArrivals("t", listOf(arrival.copy(placeId = "wrong"))) }
        assertThrows(IllegalArgumentException::class.java) { original.recordArrivals("t", listOf(arrival.copy(confirmedAt = trip.startedAt + 19_999))) }
        assertThrows(IllegalArgumentException::class.java) { trip.copy(arrivals = listOf(arrival, arrival)).validate() }
        val stopped = StopDetector(ended.trips.single())
        feed(stopped, 0, 30_000)
        assertTrue(stopped.pending(emptyList()).isEmpty())
    }
    @Test fun distanceUsesMeters() {
        assertEquals(0.0, distanceMeters(37.5, 127.0, 37.5, 127.0), 0.001)
        assertEquals(111.2, distanceMeters(37.5, 127.0, 37.501, 127.0), 0.5)
    }
}
