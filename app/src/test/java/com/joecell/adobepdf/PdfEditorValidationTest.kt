package com.joecell.adobepdf

import org.junit.Assert.assertThrows
import org.junit.Test

class PdfEditorValidationTest {
    @Test fun acceptsValidPageAndRejectsOutOfRangePage() {
        PdfEditorValidation.requirePageIndex(0, 1)
        PdfEditorValidation.requirePageIndex(2, 3)
        assertThrows(IllegalArgumentException::class.java) { PdfEditorValidation.requirePageIndex(3, 3) }
        assertThrows(IllegalArgumentException::class.java) { PdfEditorValidation.requirePageIndex(0, 0) }
    }

    @Test fun acceptsNormalizedPointAndRejectsNanOrOutOfBounds() {
        PdfEditorValidation.requireNormalizedPoint(0f, 1f)
        assertThrows(IllegalArgumentException::class.java) { PdfEditorValidation.requireNormalizedPoint(Float.NaN, 0.5f) }
        assertThrows(IllegalArgumentException::class.java) { PdfEditorValidation.requireNormalizedPoint(1.1f, 0.5f) }
    }

    @Test fun redactionRectangleMustFitWithinPage() {
        PdfEditorValidation.requireNormalizedRectangle(0.1f, 0.1f, 0.5f, 0.5f)
        assertThrows(IllegalArgumentException::class.java) { PdfEditorValidation.requireNormalizedRectangle(0.8f, 0.1f, 0.5f, 0.2f) }
        assertThrows(IllegalArgumentException::class.java) { PdfEditorValidation.requireNormalizedRectangle(0.1f, 0.1f, 0f, 0.2f) }
    }

    @Test fun onlyHttpAndHttpsUrlsAreAccepted() {
        PdfEditorValidation.requireHttpUrl("https://example.com/path")
        PdfEditorValidation.requireHttpUrl("http://example.com")
        assertThrows(IllegalArgumentException::class.java) { PdfEditorValidation.requireHttpUrl("javascript:alert(1)") }
        assertThrows(IllegalArgumentException::class.java) { PdfEditorValidation.requireHttpUrl("https:///missing-host") }
    }

    @Test fun signatureRequiresAtLeastTwoPoints() {
        assertThrows(IllegalArgumentException::class.java) {
            PdfEditorValidation.requireSignatureStrokes(listOf(listOf(0.5f to 0.5f)))
        }
    }

    @Test fun signatureAllowsIndependentStrokes() {
        PdfEditorValidation.requireSignatureStrokes(
            listOf(listOf(0.1f to 0.2f, 0.2f to 0.3f), listOf(0.7f to 0.8f, 0.8f to 0.7f))
        )
    }

    @Test fun signatureRejectsCoordinatesOutsidePage() {
        assertThrows(IllegalArgumentException::class.java) {
            PdfEditorValidation.requireSignatureStrokes(listOf(listOf(0.1f to 0.2f, 1.1f to 0.3f)))
        }
    }
}
