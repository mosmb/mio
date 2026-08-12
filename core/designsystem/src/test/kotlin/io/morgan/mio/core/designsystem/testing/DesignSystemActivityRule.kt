package io.morgan.mio.core.designsystem.testing

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.robolectric.Robolectric

@Suppress("DEPRECATION")
public class DesignSystemActivityRule : TestRule {
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

@Suppress("DEPRECATION")
internal fun DesignSystemActivityRule.composeRule():
    AndroidComposeTestRule<DesignSystemActivityRule, ComponentActivity> =
    AndroidComposeTestRule(this) { rule -> rule.activity }
