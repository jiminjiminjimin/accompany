package com.example.pathreport

import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class TripExcelRow(val date: String, val name: String, val arrival: String, val departure: String)

fun selectedTripExcelRows(trips: List<Trip>, selectedIds: Set<String>, zone: TimeZone = TimeZone.getDefault()): List<TripExcelRow> =
    tripExcelRows(trips.filter { it.id in selectedIds }, zone)

/** Group by stable identity, not name; use the same local start date as history. */
fun tripExcelRows(trips: List<Trip>, zone: TimeZone = TimeZone.getDefault()): List<TripExcelRow> {
    val day = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).apply { timeZone = zone }
    val time = SimpleDateFormat("HH:mm", Locale.KOREA).apply { timeZone = zone }
    data class Entry(val trip: Trip, val target: TripTarget)
    return trips.filter { it.endedAt != null }.sortedBy { it.startedAt }
        .flatMap { trip -> trip.targets.map { Entry(trip, it) } }
        .groupBy { day.format(Date(it.trip.startedAt)) to it.target.elderId }
        .map { (key, entries) ->
            fun times(direction: TripDirection) = entries.filter { it.trip.direction == direction }
                .mapNotNull { entry -> entry.trip.arrivals.find { it.elderId == entry.target.elderId }?.estimatedAt }
            TripExcelRow(key.first, entries.last().target.elderName,
                times(TripDirection.TO_CENTER).minOrNull()?.let { time.format(Date(it)) }.orEmpty(),
                times(TripDirection.FROM_CENTER).maxOrNull()?.let { time.format(Date(it)) }.orEmpty())
        }.sortedWith(compareBy<TripExcelRow> { it.date }.thenBy { it.name })
}

/** Small, dependency-free OOXML workbook. Inline strings cannot execute as formulas. */
fun writeTripExcel(output: OutputStream, rows: List<TripExcelRow>) {
    require(rows.size < 1_048_576) { "엑셀의 최대 행 수를 초과했습니다." }
    fun xml(value: String) = buildString {
        value.codePoints().forEach { c ->
            when (c) {
                38 -> append("&amp;")
                60 -> append("&lt;")
                62 -> append("&gt;")
                34 -> append("&quot;")
                else -> if (c == 9 || c == 10 || c == 13 || c in 0x20..0xD7FF || c in 0xE000..0xFFFD || c in 0x10000..0x10FFFF) appendCodePoint(c)
            }
        }
    }
    ZipOutputStream(output).use { zip ->
        fun part(name: String, content: String) {
            zip.putNextEntry(ZipEntry(name))
            zip.write(content.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        part("[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/></Types>""")
        part("_rels/.rels", """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
        part("xl/workbook.xml", """<?xml version="1.0" encoding="UTF-8"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="운행 내역" sheetId="1" r:id="rId1"/></sheets></workbook>""")
        part("xl/_rels/workbook.xml.rels", """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>""")
        part("xl/styles.xml", """<?xml version="1.0" encoding="UTF-8"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><fonts count="2"><font><sz val="11"/><name val="맑은 고딕"/></font><font><b/><sz val="11"/><name val="맑은 고딕"/></font></fonts><fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills><borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="2"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/></cellXfs><cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles></styleSheet>""")
        zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
        fun emit(value: String) { zip.write(value.toByteArray(Charsets.UTF_8)) }
        emit("""<?xml version="1.0" encoding="UTF-8"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetViews><sheetView workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews><cols><col min="1" max="1" width="16" customWidth="1"/><col min="2" max="2" width="24" customWidth="1"/><col min="3" max="4" width="16" customWidth="1"/></cols><sheetData>""")
        fun row(number: Int, cells: List<String>) {
            emit("<row r=\"$number\">")
            cells.forEachIndexed { index, value ->
                emit("<c r=\"${'A' + index}$number\" t=\"inlineStr\" s=\"${if (number == 1) 1 else 0}\"><is><t xml:space=\"preserve\">${xml(value)}</t></is></c>")
            }
            emit("</row>")
        }
        row(1, listOf("날짜", "어르신 이름", "등원 시간", "하원 시간"))
        rows.forEachIndexed { index, r -> row(index + 2, listOf(r.date, r.name, r.arrival, r.departure)) }
        emit("</sheetData><autoFilter ref=\"A1:D${rows.size + 1}\"/></worksheet>")
        zip.closeEntry()
    }
}
