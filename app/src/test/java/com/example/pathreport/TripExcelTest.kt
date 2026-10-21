package com.example.pathreport

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

class TripExcelTest {
    private val zone = TimeZone.getTimeZone("Asia/Seoul")
    private val place = StopPlace("p", "집", "주소", 37.5, 127.0)
    private fun stamp(value: String): Long {
        val parsed = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA)
            .apply { timeZone = zone; isLenient = false }.parse(value)
        return parsed?.time ?: throw AssertionError("Invalid test timestamp")
    }
    private fun trip(direction: TripDirection, date: String, id: String = "e", name: String = "김어르신", recorded: Boolean = true): Trip {
        val time = stamp(date)
        return Trip(direction = direction, targets = listOf(TripTarget(id, name, place)), startedAt = time - 60_000,
            endedAt = time + 30_000, arrivals = if (recorded) listOf(Arrival(id, "p", time, time + 20_000)) else emptyList())
    }
    @Test fun combinesDirectionsAndUsesEarliestArrivalLatestDeparture() {
        val rows = tripExcelRows(listOf(
            trip(TripDirection.TO_CENTER, "2026-10-18 09:30"),
            trip(TripDirection.FROM_CENTER, "2026-10-18 17:30"),
            trip(TripDirection.TO_CENTER, "2026-10-18 08:30"),
            trip(TripDirection.FROM_CENTER, "2026-10-18 16:30")
        ), zone)
        assertEquals(listOf(TripExcelRow("2026-10-18", "김어르신", "08:30", "17:30")), rows)
    }
    @Test fun separatesDatesAndIdentitiesKeepsMissingAndExcludesActive() {
        val rows = tripExcelRows(listOf(
            trip(TripDirection.TO_CENTER, "2026-10-18 09:00"),
            trip(TripDirection.TO_CENTER, "2026-10-18 09:30", id = "other", recorded = false),
            trip(TripDirection.FROM_CENTER, "2026-10-17 17:00"),
            trip(TripDirection.TO_CENTER, "2026-10-19 09:00").copy(endedAt = null)
        ), zone)
        assertEquals(3, rows.size)
        assertEquals("2026-10-17", rows.first().date)
        assertEquals("17:00", rows.first().departure)
        assertTrue(rows.any { it.date == "2026-10-18" && it.arrival.isEmpty() && it.departure.isEmpty() })
    }
    @Test fun usesStartDayAcrossMidnight() {
        val t = trip(TripDirection.FROM_CENTER, "2026-10-19 00:00")
        assertEquals("2026-10-18", tripExcelRows(listOf(t), zone).single().date)
    }
    @Test fun exportsOnlySelectedRecordsAndNeverFallsBackToAll() {
        val morning = trip(TripDirection.TO_CENTER, "2026-10-18 09:00")
        val evening = trip(TripDirection.FROM_CENTER, "2026-10-18 17:00")
        val otherDay = trip(TripDirection.TO_CENTER, "2026-10-17 09:00")
        val all = listOf(morning, evening, otherDay)
        assertEquals(listOf(TripExcelRow("2026-10-18", "김어르신", "09:00", "17:00")),
            selectedTripExcelRows(all, setOf(morning.id, evening.id), zone))
        assertTrue(selectedTripExcelRows(all, emptySet(), zone).isEmpty())
        assertTrue(selectedTripExcelRows(all, setOf("missing"), zone).isEmpty())
    }
    @Test fun workbookContainsValidXmlAndLiteralNames() {
        val output = ByteArrayOutputStream()
        writeTripExcel(output, listOf(TripExcelRow("2026-10-18", "=김<&\u0001", "08:30", "")))
        val parts = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(output.toByteArray())).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                parts[entry.name] = zip.readBytes()
            }
        }
        assertEquals(6, parts.size)
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        parts.values.forEach { factory.newDocumentBuilder().parse(ByteArrayInputStream(it)) }
        val sheet = factory.newDocumentBuilder().parse(ByteArrayInputStream(parts.getValue("xl/worksheets/sheet1.xml")))
        assertEquals(0, sheet.getElementsByTagName("f").length)
        assertEquals(2, sheet.getElementsByTagName("row").length)
        assertTrue(sheet.documentElement.textContent.contains("=김<&"))
        assertTrue(sheet.documentElement.textContent.contains("하원 시간"))
    }
}
