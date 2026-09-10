package io.morgan.mio.core.ui.progress

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import io.morgan.mio.core.designsystem.theme.MioTheme
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Suppress("DEPRECATION")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MioProgressStepperTest {
    private val activityRule = ActivityRule()

    @get:Rule
    val composeRule: AndroidComposeTestRule<ActivityRule, *> =
        AndroidComposeTestRule(activityRule) { rule -> rule.activity }

    @Test
    fun blankDescriptionsAreRejectedBeforeTheSharedButtonsAreComposed() {
        assertThrows(Throwable::class.java) {
            composeRule.setContent {
                MioTheme {
                    MioProgressStepper(
                        decreaseDescription = " ",
                        increaseDescription = "Increase",
                        decreaseEnabled = true,
                        increaseEnabled = true,
                        onDecrease = {},
                        onIncrease = {},
                    )
                }
            }
        }
    }

    @Test
    fun blankIncreaseDescriptionIsAlsoRejected() {
        assertThrows(Throwable::class.java) {
            composeRule.setContent {
                MioTheme {
                    MioProgressStepper(
                        decreaseDescription = "Decrease",
                        increaseDescription = "",
                        decreaseEnabled = true,
                        increaseEnabled = true,
                        onDecrease = {},
                        onIncrease = {},
                    )
                }
            }
        }
    }

    class ActivityRule : TestRule {
        lateinit var activity: ComponentActivity

        override fun apply(
            base: Statement,
            description: Description,
        ): Statement =
            object : Statement() {
                override fun evaluate() {
                    activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
                    try {
                        base.evaluate()
                    } finally {
                        activity.finish()
                    }
                }
            }
    }
}
