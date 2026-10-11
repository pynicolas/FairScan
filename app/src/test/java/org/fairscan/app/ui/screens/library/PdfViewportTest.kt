package org.fairscan.app.ui.screens.library

import org.junit.Assert.assertEquals
import org.junit.Test

class PdfViewportTest {
    @Test fun pinchKeepsTheDocumentPointUnderTheFingers() {
        val before = PdfViewport(2f, -200f, -800f)
        val after = before.transform(1.4f, 130f, 270f, 0f, 0f, 500f, 800f, 3000f)
        assertEquals((130f - before.x) / before.zoom, (130f - after.x) / after.zoom, 0.001f)
        assertEquals((270f - before.y) / before.zoom, (270f - after.y) / after.zoom, 0.001f)
    }

    @Test fun oneFingerMovesBothAxesWithoutChangingScale() {
        val after = PdfViewport(2f, -200f, -800f)
            .transform(1f, 100f, 100f, 35f, -70f, 500f, 800f, 3000f)
        assertEquals(-165f, after.x, 0.001f)
        assertEquals(-870f, after.y, 0.001f)
        assertEquals(2f, after.zoom, 0.001f)
    }

    @Test fun dragCanReachTheBottomOfAMultiPageDocument() {
        val after = PdfViewport(2f).transform(1f, 0f, 0f, -9000f, -9000f, 500f, 800f, 3000f)
        assertEquals(-500f, after.x, 0.001f)
        assertEquals(-5200f, after.y, 0.001f)
    }

    @Test fun smallDocumentIsCenteredAndWidthFitResetsHorizontalOffset() {
        val after = PdfViewport(1f, -200f, -400f).bounded(500f, 800f, 600f)
        assertEquals(0f, after.x, 0.001f)
        assertEquals(100f, after.y, 0.001f)
    }

    @Test fun movingPinchCenterAlsoPansTheAnchoredPoint() {
        val before = PdfViewport(2f, -200f, -800f)
        val after = before.transform(1.5f, 150f, 250f, 20f, 30f, 500f, 800f, 3000f)
        assertEquals((150f - before.x) / before.zoom, (170f - after.x) / after.zoom, 0.001f)
        assertEquals((250f - before.y) / before.zoom, (280f - after.y) / after.zoom, 0.001f)
    }
}
