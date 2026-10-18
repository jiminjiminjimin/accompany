package com.example.pathreport

import org.junit.Assert.*
import org.junit.Test

class CareDataTest {
    @Test fun legacyMigrationSeparatesSameNamesAndIsIdempotent() {
        val entries = listOf(LegacyPerson("가상 이름", "가상 주소"), LegacyPerson("가상 이름", "가상 주소"))
        val migrated = migrateLegacyItems(CareData(), entries, "test-digest")
        assertEquals(1, migrated.places.size); assertEquals(2, migrated.elders.size); assertEquals(2, migrated.links.size)
        assertNotEquals(migrated.elders[0].id, migrated.elders[1].id)
        assertNull(migrated.places[0].latitude)
        assertEquals(migrated, migrateLegacyItems(migrated, entries, "test-digest"))
        assertThrows(Exception::class.java) { migrateLegacyItems(migrated, entries, "changed-source") }
        assertThrows(Exception::class.java) { migrateLegacyItems(CareData(), listOf(LegacyPerson("", "가상 주소")), "invalid") }
    }
    @Test fun coordinatesRejectPartialNonfiniteAndOutOfRange() {
        assertTrue(validCoordinates(null, null))
        assertTrue(validCoordinates(37.5, 127.0))
        assertFalse(validCoordinates(null, 127.0))
        assertFalse(validCoordinates(Double.NaN, 127.0))
        assertFalse(validCoordinates(90.1, 0.0))
        assertFalse(validCoordinates(0.0, Double.POSITIVE_INFINITY))
    }
    @Test fun deletingPlacePreservesPeopleAndOtherLinks() {
        val a = StopPlace(name = "가상 정류장 가", address = "가상 주소 가")
        val b = StopPlace(name = "가상 정류장 나", address = "가상 주소 나")
        val x = Elder(name = "가상 어르신"); val y = Elder(name = "가상 어르신")
        assertNotEquals(x.id, y.id)
        val data = CareData(listOf(a, b), listOf(x, y), listOf(PlaceElder(a.id, x.id), PlaceElder(a.id, y.id), PlaceElder(b.id, x.id)))
        data.validate()
        val removed = data.withoutPlace(a.id)
        removed.validate(); assertEquals(2, removed.elders.size); assertEquals(listOf(PlaceElder(b.id, x.id)), removed.links)
        val elderRemoved = data.withoutElder(x.id)
        elderRemoved.validate(); assertEquals(2, elderRemoved.places.size); assertEquals(listOf(PlaceElder(a.id, y.id)), elderRemoved.links)
    }
    @Test(expected = IllegalArgumentException::class) fun danglingLinksRejected() {
        CareData(links = listOf(PlaceElder("unknown", "unknown"))).validate()
    }
    @Test fun draftsRequireAddressNameAndCoordinates() {
        assertNotNull(PlaceDraft().error())
        assertNotNull(PlaceDraft(name = "가상 장소", address = "가상 주소").error())
        assertNull(PlaceDraft(name = "가상 장소", address = "가상 주소", lat = "37.5", lon = "127").error())
        assertNotNull(PlaceDraft(name = "가상 장소", address = "가상 주소", lat = "NaN", lon = "127").error())
    }
}
