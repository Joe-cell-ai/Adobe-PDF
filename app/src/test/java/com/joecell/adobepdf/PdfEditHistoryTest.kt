package com.joecell.adobepdf

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class PdfEditHistoryTest {
    private fun pdf(name: String): File = File.createTempFile(name, ".pdf").apply { writeBytes(byteArrayOf(1, 2, 3)) }

    @Test fun undoAndRedoReturnImmutableSnapshotsInOrder() {
        val original = pdf("original")
        val first = pdf("first")
        val second = pdf("second")
        val history = PdfEditHistory()
        history.record(original, first, "Add text")
        history.record(first, second, "Insert image")

        assertEquals(second, history.redo())
        assertEquals(first, history.undo())
        assertEquals(original, history.undo())
        assertNull(history.undo())
        assertEquals(first, history.redo())
        assertEquals(second, history.redo())
        assertNull(history.redo())
    }

    @Test fun recordingAfterUndoDiscardsRedoBranch() {
        val original = pdf("original")
        val first = pdf("first")
        val second = pdf("second")
        val alternate = pdf("alternate")
        val history = PdfEditHistory()
        history.record(original, first, "First")
        history.record(first, second, "Second")
        assertEquals(first, history.undo())

        history.record(first, alternate, "Alternate")
        assertFalse(history.canRedo)
        assertEquals(alternate, history.undo())
        assertEquals(first, history.redo())
        assertEquals(alternate, history.redo())
    }

    @Test fun historyIsBounded() {
        val history = PdfEditHistory(maxEntries = 2)
        val files = (0..3).map { pdf("snapshot$it") }
        history.record(files[0], files[1], "1")
        history.record(files[1], files[2], "2")
        history.record(files[2], files[3], "3")
        assertEquals(2, history.size)
        assertEquals(files[2], history.undo())
        assertEquals(files[1], history.undo())
        assertNull(history.undo())
    }
}
