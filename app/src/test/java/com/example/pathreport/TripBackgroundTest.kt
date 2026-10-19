package com.example.pathreport

import org.junit.Assert.*
import org.junit.After
import org.junit.Test

class TripBackgroundTest {
    private val place = StopPlace("p", "가상 장소", "가상 주소", 37.5, 127.0)
    private val trip = Trip("t", TripDirection.TO_CENTER, listOf(TripTarget("e", "가상 어르신", place)), 1000)
    private val arrival = Arrival("e", "p", 1000, 21000)
    @After fun clearRuntime() { TripRuntime.clearEnd("t"); TripRuntime.publish(TrackingInfo()) }
    @Test fun activeSavedTripWithNoLiveServiceRequiresResume() {
        val info = trackingFor(trip, TrackingInfo(), true, true)
        assertFalse(info.serviceAlive)
        assertEquals(TrackingPhase.STOPPED, info.phase)
        assertEquals("t", info.tripId)
        assertTrue(info.message.contains("중단"))
    }
    @Test fun oldServiceCannotMakeNewTripLookMonitored() {
        val old = TrackingInfo("other", serviceAlive = true, phase = TrackingPhase.RUNNING)
        assertFalse(trackingFor(trip, old, true, true).serviceAlive)
    }
    @Test fun arrivalsCompleteStillKeepsRouteServiceRunningUntilTripEnds() {
        val complete = trip.copy(arrivals = listOf(arrival))
        val info = trackingFor(complete, TrackingInfo("t", serviceAlive = true, phase = TrackingPhase.RUNNING), false, false)
        assertEquals(TrackingPhase.RUNNING, info.phase)
        assertTrue(info.serviceAlive)
        assertTrue(info.needsPermission)
        assertFalse(info.notificationsAllowed)
        val ended = trackingFor(complete.copy(endedAt = 22_000), info, true, true)
        assertEquals(TrackingPhase.COMPLETE, ended.phase)
        assertFalse(ended.serviceAlive)
    }
    @Test fun endBarrierCapturesPendingAndCanBeReleasedAfterFailure() {
        val route = listOf(RoutePoint(37.5, 127.0, 1_000, 5.0, true))
        TripRuntime.publish(TrackingInfo("t", pending = listOf(arrival), serviceAlive = true, pendingRoute = route))
        assertEquals(listOf(arrival), TripRuntime.beginEnd("t"))
        val saved = CareData(trips = listOf(trip)).appendRoutePoints("t", TripRuntime.status.value.pendingRoute)
            .appendDetectedArrivals("t", TripRuntime.status.value.pending).endTrip("t", 23_000)
        assertEquals(route, saved.trips.single().route)
        assertEquals("t", TripRuntime.endingTripId)
        TripRuntime.clearEnd("other")
        assertEquals("t", TripRuntime.endingTripId)
        TripRuntime.clearEnd("t")
        assertNull(TripRuntime.endingTripId)
    }
    @Test fun delayedServiceWriteCannotReopenEndedTrip() {
        val current = CareData(trips = listOf(trip)).endTrip("t", 23000)
        assertEquals(current, current.appendDetectedArrivals("t", listOf(arrival)))
        assertEquals(current, current.appendDetectedArrivals("missing", listOf(arrival)))
    }
    @Test fun endIncludesPendingAndPreservesPreviouslySavedArrival() {
        val extra = TripTarget("e2", "다른 가상 어르신", place)
        val current = CareData(trips = listOf(trip.copy(targets = trip.targets + extra, arrivals = listOf(arrival))))
        val pending = Arrival("e2", "p", 2000, 22000)
        val ended = current.appendDetectedArrivals("t", listOf(arrival, pending)).endTrip("t", 23000)
        assertEquals(2, ended.trips.single().arrivals.size)
        assertEquals(23000L, ended.trips.single().endedAt)
    }
}
