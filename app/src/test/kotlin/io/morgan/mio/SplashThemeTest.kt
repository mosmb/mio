package io.morgan.mio

import android.content.Context
import android.content.res.Configuration
import android.util.TypedValue
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import androidx.core.splashscreen.R as SplashR

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SplashThemeTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun dayAndNightStartingThemesUseTheBackgroundAdaptiveIconAndPostTheme() {
        assertStartingTheme(
            resources = context.resources,
            expectedBackground = 0xFFFFFFFF.toInt(),
        )

        val nightConfiguration =
            Configuration(context.resources.configuration).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
            }
        val nightContext = context.createConfigurationContext(nightConfiguration)
        assertStartingTheme(
            resources = nightContext.resources,
            expectedBackground = 0xFF0B0D0F.toInt(),
        )
    }

    @Test
    fun mainActivityStartsTheExistingRootWithoutAnAdditionalActivity() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        try {
            assertTrue(activity.isFinishing.not())
            assertEquals(MainActivity::class.java.name, activity.componentName.className)
        } finally {
            activity.finish()
        }
    }

    private fun assertStartingTheme(
        resources: android.content.res.Resources,
        expectedBackground: Int,
    ) {
        val theme = resources.newTheme()
        theme.applyStyle(R.style.Theme_Mio_Starting, true)

        val background = TypedValue()
        assertTrue(theme.resolveAttribute(SplashR.attr.windowSplashScreenBackground, background, true))
        assertEquals(expectedBackground, resources.getColor(background.resourceId, theme))

        val icon = TypedValue()
        assertTrue(theme.resolveAttribute(SplashR.attr.windowSplashScreenAnimatedIcon, icon, true))
        assertEquals(R.mipmap.ic_launcher, icon.resourceId)

        val postTheme = TypedValue()
        assertTrue(theme.resolveAttribute(SplashR.attr.postSplashScreenTheme, postTheme, true))
        assertEquals(R.style.Theme_Mio, postTheme.resourceId)
    }
}
