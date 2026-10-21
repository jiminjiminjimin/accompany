package com.example.pathreport

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CoordinateOnlyStorageTest {
    private fun sample(): CareData {
        val place = StopPlace("p", "승하차 장소", "PRIVATE_ADDRESS_123", 37.5, 127.0)
        return CareData(listOf(place), listOf(Elder("e", "가상 이름")), listOf(PlaceElder("p", "e")),
            trips = listOf(Trip("t", TripDirection.TO_CENTER, listOf(TripTarget("e", "가상 이름", place)), 1000)))
    }

    @Test fun addressNeverEntersSerializedPlacesOrTripSnapshots() {
        val original = sample()
        val bytes = CareCodec.encode(original)
        val json = bytes.toString(Charsets.UTF_8)
        assertFalse(json.contains("PRIVATE_ADDRESS_123"))
        assertFalse(json.contains("\"address\""))
        assertEquals(6, JSONObject(json).getInt("version"))
        assertEquals(original.withoutStoredAddresses(), CareCodec.decode(bytes))
    }

    @Test fun upgradingVersionFourPreservesLocationsLinksAndHistory() {
        val original = sample()
        val old = JSONObject(CareCodec.encode(original).toString(Charsets.UTF_8)).put("version", 4)
        old.getJSONArray("places").getJSONObject(0).put("address", "PRIVATE_ADDRESS_123")
        old.getJSONArray("trips").getJSONObject(0).getJSONArray("targets").getJSONObject(0)
            .getJSONObject("place").put("address", "PRIVATE_ADDRESS_123")
        val decoded = CareCodec.decode(old.toString().toByteArray())
        val upgraded = CareCodec.decode(CareCodec.encode(decoded.withoutStoredAddresses()))
        assertEquals(original.withoutStoredAddresses(), upgraded)
        assertEquals(upgraded, upgraded.withoutStoredAddresses())
    }

    @Test fun legacyMovementHistoryIsDiscardedWithoutLosingArrivals() {
        val original = sample().let { data -> data.copy(trips = data.trips.map {
            it.copy(arrivals = listOf(Arrival("e", "p", 1000, 21000)), endedAt = 22000)
        }) }.withoutStoredAddresses()
        for (version in 4..5) {
            val old = JSONObject(CareCodec.encode(original).toString(Charsets.UTF_8)).put("version", version)
            old.getJSONArray("trips").getJSONObject(0).put("route", org.json.JSONArray(
                """[{"lat":37.501,"lon":127.001,"time":1000,"accuracy":5,"break":true}]"""))
            val decoded = CareCodec.decode(old.toString().toByteArray())
            assertEquals(original, decoded)
            val rewritten = CareCodec.encode(decoded).toString(Charsets.UTF_8)
            assertFalse(JSONObject(rewritten).getJSONArray("trips").getJSONObject(0).has("route"))
            assertEquals(6, JSONObject(rewritten).getInt("version"))
        }
    }
    @Test fun draftNeedsCoordinatesButNotSearchAddress() {
        val draft = PlaceDraft(name = "승하차 장소", lat = "37.5", lon = "127.0")
        assertNull(draft.error())
        assertEquals("", draft.copy(address = "PRIVATE_ADDRESS_123").place().address)
        val latitude = draft.place().latitude ?: throw AssertionError("Saved place must have latitude")
        assertEquals(37.5, latitude, 0.0)
        assertNotNull(draft.copy(lat = "").error())
    }
}
