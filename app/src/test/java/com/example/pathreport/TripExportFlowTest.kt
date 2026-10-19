package com.example.pathreport

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.zip.ZipInputStream

class TripExportFlowTest {
    private val rows = listOf(TripExcelRow("2026-10-19", "가상 어르신", "09:00", "17:00"))
    @Test fun pickerAndWritingBlockDuplicateRequestsWithoutChangingSelection() {
        val session = TripExportSession()
        val ids = mutableSetOf("morning")
        assertTrue(session.begin(ids))
        ids.add("unselected")
        assertFalse(session.begin(setOf("other")))
        assertEquals(setOf("morning"), session.pickerIds)
        assertEquals(setOf("morning"), session.beginWriting())
        assertTrue(session.busy)
        assertFalse(session.begin(setOf("other")))
        assertNull(session.beginWriting())
        session.finish()
        assertTrue(session.begin(setOf("other")))
    }
    @Test fun pickerCancellationAndRestorationPreserveOnlyPendingSelection() {
        val original = TripExportSession()
        assertFalse(original.begin(emptySet()))
        assertTrue(original.begin(setOf("t")))
        val restored = TripExportSession(original.pickerIds)
        assertTrue(restored.busy)
        assertEquals(setOf("t"), restored.beginWriting())
        assertTrue(restored.pickerIds.isEmpty())
        restored.cancelPicker() // A duplicate cancelled callback must not reset an ongoing write.
        assertTrue(restored.busy)
        original.cancelPicker()
        assertFalse(original.busy)
        assertTrue(original.begin(setOf("new")))
    }
    @Test fun successfulWorkbookClosesProviderOnceAndDoesNotDeleteDocument() {
        val bytes = ByteArrayOutputStream()
        var closed = 0
        val destination = object : OutputStream() {
            override fun write(value: Int) = bytes.write(value)
            override fun write(buffer: ByteArray, offset: Int, length: Int) = bytes.write(buffer, offset, length)
            override fun close() { closed++; check(closed == 1) }
        }
        var removed = false
        writeTripExcelDocument(rows, { destination }, { removed = true; true })
        assertEquals(1, closed)
        assertFalse(removed)
        var entries = 0
        ZipInputStream(bytes.toByteArray().inputStream()).use { zip -> while (zip.nextEntry != null) { zip.readBytes(); entries++ } }
        assertEquals(6, entries)
    }
    @Test fun failedWriteDiscardsIncompleteDocumentAndClosesStream() {
        var closed = false
        var removed = false
        val broken = object : OutputStream() {
            override fun write(value: Int) { throw IOException("test provider failure") }
            override fun close() { closed = true }
        }
        val error = assertThrows(TripExportFailure::class.java) {
            writeTripExcelDocument(rows, { broken }, { removed = true; true })
        }
        assertTrue(error.partialFileRemoved)
        assertTrue(removed)
        assertTrue(closed)
    }
    @Test fun failedCloseOrUnsupportedDeletionReportsPossiblePartialFile() {
        val brokenClose = object : ByteArrayOutputStream() { override fun close() { throw IOException("test close failure") } }
        val error = assertThrows(TripExportFailure::class.java) {
            writeTripExcelDocument(rows, { brokenClose }, { throw IOException("provider cannot delete") })
        }
        assertFalse(error.partialFileRemoved)
    }
    @Test fun nullOutputAndNoRowsDoNotReportSuccess() {
        val error = assertThrows(TripExportFailure::class.java) { writeTripExcelDocument(rows, { null }, { true }) }
        assertTrue(error.partialFileRemoved)
        var opened = false
        assertThrows(TripExportFailure::class.java) { writeTripExcelDocument(emptyList(), { opened = true; ByteArrayOutputStream() }, { true }) }
        assertFalse(opened)
    }
}
