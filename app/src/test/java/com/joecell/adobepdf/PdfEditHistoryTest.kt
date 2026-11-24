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
        assertEquals(first, history.undo())
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

    @Test fun discardedRedoBranchDeletesOnlyUnreferencedSnapshots() {
        val original = pdf("original")
        val first = pdf("first")
        val second = pdf("second")
        val alternate = pdf("alternate")
        val history = PdfEditHistory()
        history.record(original, first, "First")
        history.record(first, second, "Second")
        history.undo()

        history.record(first, alternate, "Alternate")

        assertTrue(original.isFile)
        assertTrue(first.isFile)
        assertFalse(second.exists())
        assertTrue(alternate.isFile)
        history.clear()
        assertFalse(original.exists())
        assertFalse(first.exists())
        assertFalse(alternate.exists())
    }

    @Test fun evictingOldestEntryDeletesOnlyItsUnsharedSnapshot() {
        val history = PdfEditHistory(maxEntries = 2)
        val files = (0..3).map { pdf("evict$it") }
        history.record(files[0], files[1], "1")
        history.record(files[1], files[2], "2")
        history.record(files[2], files[3], "3")

        assertFalse(files[0].exists())
        assertTrue(files[1].isFile)
        assertTrue(files[2].isFile)
        assertTrue(files[3].isFile)
        history.clear()
        assertTrue(files[0].exists().not())
        assertFalse(files[1].exists())
        assertFalse(files[2].exists())
        assertFalse(files[3].exists())
    }
}
