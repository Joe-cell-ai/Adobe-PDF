package com.joecell.adobepdf

import java.io.File

/**
 * Snapshot-based history for PDF edits.
 *
 * Every entry stores immutable before/after files. Files are not overwritten by
 * undo/redo, so reopening or saving a restored snapshot is safe.
 */
internal class PdfEditHistory(private val maxEntries: Int = 30) {
    data class Entry(val before: File, val after: File, val label: String)

    private val entries = mutableListOf<Entry>()
    private var cursor = 0

    init { require(maxEntries > 0) { "History must retain at least one edit." } }

    val canUndo: Boolean get() = cursor > 0
    val canRedo: Boolean get() = cursor < entries.size
    val size: Int get() = entries.size

    fun record(before: File, after: File, label: String) {
        require(before.isFile && before.length() > 0L) { "The previous PDF snapshot is missing." }
        require(after.isFile && after.length() > 0L) { "The edited PDF snapshot is missing." }
        require(before.canonicalFile != after.canonicalFile) { "An edit must create a new PDF snapshot." }
        while (entries.size > cursor) entries.removeAt(entries.lastIndex)
        entries.add(Entry(before, after, label))
        if (entries.size > maxEntries) entries.removeAt(0)
        cursor = entries.size
    }

    fun undo(): File? {
        if (!canUndo) return null
        cursor--
        return entries[cursor].before
    }

    fun redo(): File? {
        if (!canRedo) return null
        val file = entries[cursor].after
        cursor++
        return file
    }

    fun clear() {
        entries.clear()
        cursor = 0
    }
}
