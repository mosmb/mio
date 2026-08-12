package io.morgan.mio.core.designsystem.brand

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.core.app.ApplicationProvider
import io.morgan.mio.core.designsystem.R
import io.morgan.mio.core.designsystem.icon.MioIcons
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.xmlpull.v1.XmlPullParser
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MioBrandResourcesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun fontMarkWordmarkAndFunctionalIconsResolve() {
        context.resources.getFont(R.font.gabarito_variable)
        assertTrue(context.resources.openRawResource(R.raw.gabarito_ofl).available() > 0)
        assertTrue(context.resources.openRawResource(R.raw.material_symbols_license).available() > 0)
        assertTrue(context.getDrawable(R.drawable.mio_mark) != null)
        assertTrue(context.getDrawable(R.drawable.mio_wordmark) != null)
        assertTrue(context.getDrawable(R.drawable.mio_launcher_foreground) != null)

        val icons =
            listOf(
                MioIcons.TodayOutline,
                MioIcons.TodayFilled,
                MioIcons.LibraryOutline,
                MioIcons.LibraryFilled,
                MioIcons.DiscoverOutline,
                MioIcons.DiscoverFilled,
                MioIcons.YouOutline,
                MioIcons.YouFilled,
                MioIcons.Back,
                MioIcons.Close,
                MioIcons.Search,
                MioIcons.Sort,
                MioIcons.Add,
                MioIcons.Remove,
                MioIcons.Check,
                MioIcons.Refresh,
                MioIcons.Error,
            )
        icons.forEach { id -> assertTrue(context.getDrawable(id) != null) }
    }

    @Test
    fun markAndWordmarkExposeNonEmptySingleColorVectorGeometry() {
        listOf(R.drawable.mio_mark, R.drawable.mio_wordmark).forEach(::assertSingleColorVector)
    }

    @Test
    fun markAndWordmarkRenderAsUnclippedSingleColorInkAtReferenceSizes() {
        listOf(R.drawable.mio_mark, R.drawable.mio_wordmark).forEach { resourceId ->
            listOf(24, 48).forEach { height ->
                assertUnclippedSingleColorInk(
                    bitmap = render(resourceId, height),
                    expectedRgb = 0x0B0D0F,
                )
            }
        }
    }

    @Test
    fun wordmarkIntrinsicRatioMatchesItsViewport() {
        val drawable = context.getDrawable(R.drawable.mio_wordmark)!!
        assertEquals(1.9f, drawable.intrinsicWidth.toFloat() / drawable.intrinsicHeight, 0.01f)
    }

    @Test
    fun functionalIconsKeepTheirTwentyFourDpIntrinsicSize() {
        val ids =
            listOf(
                MioIcons.TodayOutline,
                MioIcons.TodayFilled,
                MioIcons.LibraryOutline,
                MioIcons.LibraryFilled,
                MioIcons.DiscoverOutline,
                MioIcons.DiscoverFilled,
                MioIcons.YouOutline,
                MioIcons.YouFilled,
                MioIcons.Back,
                MioIcons.Close,
                MioIcons.Search,
                MioIcons.Sort,
                MioIcons.Add,
                MioIcons.Remove,
                MioIcons.Check,
                MioIcons.Refresh,
                MioIcons.Error,
            )
        ids.forEach { id ->
            val drawable = context.getDrawable(id)!!
            assertEquals(24, drawable.intrinsicWidth)
            assertEquals(24, drawable.intrinsicHeight)
            assertSingleColorVector(id)
            assertUnclippedSingleColorInk(
                bitmap = render(id, targetHeight = 24),
                expectedRgb = 0x000000,
            )
        }
    }

    private fun assertSingleColorVector(resourceId: Int) {
        val parser = context.resources.getXml(resourceId)
        val fillColors = mutableSetOf<String>()
        var pathCount = 0
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "path") {
                pathCount++
                val pathData = parser.getAttributeValue(ANDROID_NAMESPACE, "pathData")
                val fillColor = parser.getAttributeValue(ANDROID_NAMESPACE, "fillColor")
                assertTrue(pathData.isNullOrBlank().not())
                assertTrue(fillColor.isNullOrBlank().not())
                fillColors += fillColor!!
            }
            event = parser.next()
        }
        assertEquals(1, pathCount)
        assertEquals(1, fillColors.size)
    }

    private fun render(
        resourceId: Int,
        targetHeight: Int,
    ): Bitmap {
        val drawable = context.getDrawable(resourceId)!!
        val targetWidth =
            (targetHeight * drawable.intrinsicWidth.toFloat() / drawable.intrinsicHeight).roundToInt()
        val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, targetWidth, targetHeight)
        drawable.draw(Canvas(bitmap))
        return bitmap
    }

    private fun assertUnclippedSingleColorInk(
        bitmap: Bitmap,
        expectedRgb: Int,
    ) {
        val rgbColors = mutableSetOf<Int>()
        val opaqueRgbColors = mutableSetOf<Int>()
        var minX = bitmap.width
        var minY = bitmap.height
        var maxX = -1
        var maxY = -1
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                if (pixel ushr 24 != 0) {
                    val rgb = pixel and 0x00FFFFFF
                    rgbColors += rgb
                    if (pixel ushr 24 == 0xFF) opaqueRgbColors += rgb
                    minX = minOf(minX, x)
                    minY = minOf(minY, y)
                    maxX = maxOf(maxX, x)
                    maxY = maxOf(maxY, y)
                }
            }
        }

        assertEquals(setOf(expectedRgb), opaqueRgbColors)
        assertTrue(
            rgbColors.all { rgb ->
                (rgb shr 16 and 0xFF) <= 0x0F &&
                    (rgb shr 8 and 0xFF) <= 0x0F &&
                    (rgb and 0xFF) <= 0x0F
            },
        )
        assertTrue(minX > 0)
        assertTrue(minY > 0)
        assertTrue(maxX < bitmap.width - 1)
        assertTrue(maxY < bitmap.height - 1)
    }

    private companion object {
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
    }
}
