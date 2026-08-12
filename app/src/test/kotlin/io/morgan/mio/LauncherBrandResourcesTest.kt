package io.morgan.mio

import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LauncherBrandResourcesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun manifestUsesMioAdaptiveLauncherIcon() {
        val applicationInfo =
            context.packageManager.getApplicationInfo(
                context.packageName,
                PackageManager.GET_META_DATA,
            )
        assertEquals(R.mipmap.ic_launcher, applicationInfo.icon)
        assertNotEquals(android.R.drawable.sym_def_app_icon, applicationInfo.icon)
    }

    @Test
    @Config(sdk = [30])
    fun api30ResolvesTheAdaptiveV26Resource() {
        val tags = readLauncherTags()
        assertEquals("adaptive-icon", tags.first())
        assertTrue(tags.contains("background"))
        assertTrue(tags.contains("foreground"))
    }

    @Test
    fun api33ResourceIncludesTheMonochromeLayer() {
        val tags = readLauncherTags()
        assertEquals("adaptive-icon", tags.first())
        assertTrue(tags.contains("monochrome"))
    }

    private fun readLauncherTags(): List<String> {
        val parser = context.resources.getXml(R.mipmap.ic_launcher)
        val tags = mutableListOf<String>()
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG) tags += parser.name
        }
        parser.close()
        return tags
    }
}
