package io.morgan.mio.core.ui.progress

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import io.morgan.mio.core.designsystem.theme.MioTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Suppress("DEPRECATION")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MioAchievementStampTest {
    private val activityRule = MioProgressValueTest.ActivityRule()

    @get:Rule
    val composeRule = AndroidComposeTestRule(activityRule) { it.activity }

    @Test
    fun undoBeforeLandingCancelsConfirmationAndFinishAndNewCompletionCanReplay() {
        val id = mutableStateOf<String?>(null)
        val finished = mutableListOf<String>()
        var confirmations = 0
        composeRule.setContent {
            MioTheme {
                CompositionLocalProvider(
                    LocalHapticFeedback provides
                        object : HapticFeedback {
                            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                                confirmations++
                            }
                        },
                ) {
                    MioAchievementStamp(true, "Completed", id.value, onFinished = finished::add)
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false

        fun change(value: String?) {
            composeRule.runOnIdle {
                id.value = value
                Snapshot.sendApplyNotifications()
            }
            composeRule.mainClock.advanceTimeByFrame()
        }
        change("first")
        composeRule.mainClock.advanceTimeBy(240)
        assertEquals(0, confirmations)
        change(null) // Undo is requested; the database has not necessarily restored status yet.
        composeRule.mainClock.advanceTimeBy(1000)
        assertEquals(0, confirmations)
        assertEquals(emptyList<String>(), finished)
        change("second")
        composeRule.mainClock.advanceTimeBy(600)
        assertEquals(1, confirmations)
        assertEquals(emptyList<String>(), finished)
        composeRule.mainClock.advanceTimeBy(300)
        assertEquals(listOf("second"), finished)
    }

    @Test
    fun reducedMotionFinishesWithoutWaitingForTravelOrParticles() {
        val id = mutableStateOf<String?>(null)
        val finished = mutableListOf<String>()
        composeRule.setContent {
            MioTheme(reducedMotionOverride = true) {
                MioAchievementStamp(true, "Completed", id.value, onFinished = finished::add)
            }
        }
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false
        composeRule.runOnIdle {
            id.value = "reduced"
            Snapshot.sendApplyNotifications()
        }
        composeRule.mainClock.advanceTimeBy(208)
        assertEquals(listOf("reduced"), finished)
    }

    @Test
    fun leavingDuringParticlesCancelsFinishCallback() {
        val visible = mutableStateOf(true)
        val id = mutableStateOf<String?>(null)
        val finished = mutableListOf<String>()
        composeRule.setContent {
            MioTheme {
                if (visible.value) MioAchievementStamp(true, "Completed", id.value, onFinished = finished::add)
            }
        }
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false
        composeRule.runOnIdle {
            id.value = "completion"
            Snapshot.sendApplyNotifications()
        }
        composeRule.mainClock.advanceTimeBy(640)
        composeRule.runOnIdle {
            visible.value = false
            Snapshot.sendApplyNotifications()
        }
        composeRule.mainClock.advanceTimeBy(1000)
        assertEquals(emptyList<String>(), finished)
    }
}
