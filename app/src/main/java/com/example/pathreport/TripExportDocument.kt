package com.example.pathreport

import java.io.OutputStream
import java.io.IOException
import kotlinx.coroutines.CancellationException

internal class TripExportFailure(val partialFileRemoved: Boolean, cause: Exception) : Exception(cause)

/** discard is restricted to the new document returned by ACTION_CREATE_DOCUMENT. */
internal fun writeTripExcelDocument(rows: List<TripExcelRow>, open: () -> OutputStream?, discard: () -> Boolean) {
    try {
        require(rows.isNotEmpty())
        val destination = requireNotNull(open())
        destination.use { output ->
            // ZipOutputStream closes its input stream; shield the provider from being closed twice.
            val shield = object : OutputStream() {
                override fun write(value: Int) = output.write(value)
                override fun write(bytes: ByteArray, offset: Int, length: Int) = output.write(bytes, offset, length)
                override fun flush() = output.flush()
                override fun close() = flush()
            }
            writeTripExcel(shield, rows)
        }
    } catch (e: CancellationException) {
        // Preserve coroutine cancellation while still cleaning up the new document.
        runCatching { discard() }
        throw e
    } catch (e: IOException) {
        throw TripExportFailure(runCatching { discard() }.getOrDefault(false), e)
    } catch (e: SecurityException) {
        throw TripExportFailure(runCatching { discard() }.getOrDefault(false), e)
    } catch (e: IllegalArgumentException) {
        throw TripExportFailure(runCatching { discard() }.getOrDefault(false), e)
    } catch (e: Exception) {
        // Provider-specific failures must also clean up; retain the cause for the caller.
        throw TripExportFailure(runCatching { discard() }.getOrDefault(false), e)
    }
}
