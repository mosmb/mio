package io.morgan.mio.core.designsystem.accessibility

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import io.morgan.mio.core.designsystem.testing.DesignSystemActivityRule
import io.morgan.mio.core.designsystem.testing.composeRule
import io.morgan.mio.core.designsystem.theme.MioTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MioHapticsTest {
    private val activityRule = DesignSystemActivityRule()

    @get:Rule
    val composeRule: AndroidComposeTestRule<DesignSystemActivityRule, *> = activityRule.composeRule()

    @Test
    fun disabledHapticsDoNotCallThePlatform() {
        val recorder = RecordingHapticFeedback()
        var haptics: MioHaptics? = null
        composeRule.setContent {
            CompositionLocalProvider(LocalHapticFeedback provides recorder) {
                MioTheme(hapticsEnabled = false) {
                    val currentHaptics = rememberMioHaptics()
                    SideEffect { haptics = currentHaptics }
                    Text("haptics")
                }
            }
        }
        composeRule.waitForIdle()

        val resolvedHaptics = requireNotNull(haptics)
        resolvedHaptics.perform(MioHapticType.ROUTINE_TICK)
        resolvedHaptics.perform(MioHapticType.CONFIRMATION)
        resolvedHaptics.perform(MioHapticType.REJECTED)
        assertEquals(emptyList<HapticFeedbackType>(), recorder.calls)
    }

    @Test
    fun enabledHapticsMapToTheExactComposeTypes() {
        val recorder = RecordingHapticFeedback()
        var haptics: MioHaptics? = null
        composeRule.setContent {
            CompositionLocalProvider(LocalHapticFeedback provides recorder) {
                MioTheme(hapticsEnabled = true) {
                    val currentHaptics = rememberMioHaptics()
                    SideEffect { haptics = currentHaptics }
                    Text("haptics")
                }
            }
        }
        composeRule.waitForIdle()

        val resolvedHaptics = requireNotNull(haptics)
        resolvedHaptics.perform(MioHapticType.ROUTINE_TICK)
        resolvedHaptics.perform(MioHapticType.CONFIRMATION)
        resolvedHaptics.perform(MioHapticType.REJECTED)
        assertEquals(
            listOf(
                HapticFeedbackType.SegmentFrequentTick,
                HapticFeedbackType.Confirm,
                HapticFeedbackType.Reject,
            ),
            recorder.calls,
        )
    }

    private class RecordingHapticFeedback : HapticFeedback {
        val calls = mutableListOf<HapticFeedbackType>()

        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            calls += hapticFeedbackType
        }
    }
}
