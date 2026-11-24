package com.joecell.adobepdf

import java.io.File

/**
 * Snapshot-based history for PDF edits.
 *
 * The history owns only snapshot files passed to [record]. When an edit is
 * discarded, history is cleared, or the bounded history evicts an entry,
 * unreferenced snapshots are deleted. Files still referenced by another
 * retained entry are kept because adjacent edits share boundary snapshots.
 */
internal class PdfEditHistory(private val maxEntries: Int = 30) {
    data class Entry(val before: File, val after: File, val label: String)

    private val entries = mutableListOf<Entry>()
    private var cursor = 0

    init { require(maxEntries > 0) { "History must retain at least one edit." } }

    val canUndo: Boolean get() = cursor > 0
    val canRedo: Boolean get() = cursor < entries.size
    val size: Int get() = entries.size
    val currentEntryIndex: Int get() = cursor - 1
    fun entries(): List<Entry> = entries.toList()

    /**
     * Records a completed edit. The caller transfers ownership of both
     * snapshots to this history after this method succeeds.
     */
    fun record(before: File, after: File, label: String) {
        require(before.isFile && before.length() > 0L) { "The previous PDF snapshot is missing." }
        require(after.isFile && after.length() > 0L) { "The edited PDF snapshot is missing." }
        require(before.canonicalFile != after.canonicalFile) { "An edit must create a new PDF snapshot." }

        val removed = mutableListOf<Entry>()
        while (entries.size > cursor) removed += entries.removeAt(entries.lastIndex)
        entries.add(Entry(before, after, label))
        if (entries.size > maxEntries) removed += entries.removeAt(0)
        cursor = entries.size
        deleteUnreferenced(removed)
    }

    fun undo(): File? {
        if (!canUndo) return null
        cursor--
        return entries[cursor].before.takeIf { it.isFile }
            ?: error("The previous PDF snapshot is no longer available.")
    }

    fun redo(): File? {
        if (!canRedo) return null
        val file = entries[cursor].after
        check(file.isFile && file.length() > 0L) { "The edited PDF snapshot is no longer available." }
        cursor++
        return file
    }

    /** Clears history and deletes every snapshot owned by it. */
    fun clear() {
        val owned = entries.toList()
        entries.clear()
        cursor = 0
        deleteUnreferenced(owned)
    }

    private fun deleteUnreferenced(removed: List<Entry>) {
        if (removed.isEmpty()) return
        val retained = entries.flatMap { listOf(it.before.canonicalPath, it.after.canonicalPath) }.toSet()
        removed.flatMap { listOf(it.before, it.after) }
            .distinctBy { runCatching { it.canonicalPath }.getOrDefault(it.absolutePath) }
            .forEach { file ->
                val path = runCatching { file.canonicalPath }.getOrDefault(file.absolutePath)
                if (path !in retained) runCatching { file.delete() }
            }
    }
}
