package com.example.pathreport

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TripRouteCodecTest {
    private val trip = Trip("route-test", TripDirection.TO_CENTER,
        listOf(TripTarget("e", "</script><script>unsafe()</script>", StopPlace("p", "장소", "주소", 37.5, 127.0))),
        1_000, route = listOf(RoutePoint(37.5, 127.0, 1_000, 5.0, true), RoutePoint(37.501, 127.0, 6_000, 5.0)))
    @Test fun routeRoundTripAndLegacyV3Read() {
        val data = CareData(trips = listOf(trip))
        assertEquals(data, CareCodec.decode(CareCodec.encode(data)))
        val old = JSONObject(String(CareCodec.encode(data), Charsets.UTF_8)).put("version", 3)
        old.getJSONArray("trips").getJSONObject(0).remove("route")
        assertEquals(data.copy(trips = listOf(trip.copy(route = emptyList()))), CareCodec.decode(old.toString().toByteArray()))
    }
    @Test fun mapPayloadCannotInjectMarkup() {
        val payload = routeMapPayload(trip)
        assertFalse(payload.contains("</script>"))
        assertEquals(trip.targets.single().elderName, JSONObject(payload).getJSONArray("stops").getJSONObject(0).getString("label").substringAfter(". "))
    }
}
