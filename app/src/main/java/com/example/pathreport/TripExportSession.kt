package com.example.pathreport

/** Covers both the system file picker and writing; the first selection owns the result. */
internal class TripExportSession(restoredPickerIds: Set<String> = emptySet()) {
    private var ids = restoredPickerIds.toSet()
    private var writing = false
    val busy: Boolean get() = ids.isNotEmpty()
    val pickerIds: Set<String> get() = if (writing) emptySet() else ids.toSet()
    fun begin(selectedIds: Set<String>): Boolean {
        if (busy || selectedIds.isEmpty()) return false
        ids = selectedIds.toSet()
        return true
    }
    fun beginWriting(): Set<String>? {
        if (!busy || writing) return null
        writing = true
        return ids.toSet()
    }
    fun cancelPicker() { if (!writing) finish() }
    fun finish() { ids = emptySet(); writing = false }
}
