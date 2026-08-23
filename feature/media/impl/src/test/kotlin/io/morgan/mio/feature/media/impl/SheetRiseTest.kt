package io.morgan.mio.feature.media.impl

import androidx.compose.ui.unit.dp
import io.morgan.mio.feature.media.impl.component.sheetRiseAt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SheetRiseTest {
    @Test
    fun normalModeIsBoundedAtTheDocumentedEndpoints() {
        val top = sheetRiseAt(0.dp)
        val middle = sheetRiseAt(60.dp)
        val collapsed = sheetRiseAt(120.dp)

        assertEquals(0f, top.posterTranslationY.value, 0f)
        assertEquals(0f, top.introTranslationY.value, 0f)
        assertEquals(0f, top.toolbarAlpha)
        assertEquals(1f, top.titleAlpha)
        assertEquals(-20.dp, collapsed.posterTranslationY)
        assertEquals(-8.dp, collapsed.introTranslationY)
        assertEquals(1f, collapsed.toolbarAlpha)
        assertEquals(0f, collapsed.titleAlpha)
        assertEquals(-10.dp, middle.posterTranslationY)
        assertEquals(-4.dp, middle.introTranslationY)
    }

    @Test
    fun valuesClampAndReverseWithoutDirectionState() {
        assertEquals(sheetRiseAt(0.dp), sheetRiseAt((-80).dp))
        assertEquals(sheetRiseAt(120.dp), sheetRiseAt(240.dp))
        assertEquals(sheetRiseAt(48.dp), sheetRiseAt(48.dp))
        assertTrue(sheetRiseAt(48.dp).posterTranslationY in (-20).dp..0.dp)
        assertTrue(sheetRiseAt(48.dp).introTranslationY in (-8).dp..0.dp)
    }

    @Test
    fun reducedMotionRemovesSpatialTravelButKeepsOpacityCrossfadeValues() {
        val top = sheetRiseAt(0.dp, reducedMotion = true)
        val collapsed = sheetRiseAt(120.dp, reducedMotion = true)

        assertEquals(0f, top.posterTranslationY.value, 0f)
        assertEquals(0f, top.introTranslationY.value, 0f)
        assertEquals(0f, collapsed.posterTranslationY.value, 0f)
        assertEquals(0f, collapsed.introTranslationY.value, 0f)
        assertEquals(0f, top.toolbarAlpha)
        assertEquals(1f, top.titleAlpha)
        assertEquals(1f, collapsed.toolbarAlpha)
        assertEquals(0f, collapsed.titleAlpha)
    }
}
