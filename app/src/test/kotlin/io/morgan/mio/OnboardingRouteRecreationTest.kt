package io.morgan.mio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import dev.zacsweers.metro.createDynamicGraphFactory
import io.morgan.mio.core.testing.provider.FakeProviderAuthenticator
import io.morgan.mio.core.testing.provider.providerAuthorizationLaunchFixture
import io.morgan.mio.core.testing.provider.providerBindingFixture
import io.morgan.mio.data.provider.api.ProviderKey
import io.morgan.mio.data.provider.api.ProviderResult
import io.morgan.mio.di.AppGraph
import io.morgan.mio.di.TestGraphFixture
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("DEPRECATION")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OnboardingRouteRecreationTest {
    private val activityRule = RecreatingOnboardingActivityRule()

    @get:Rule
    val composeRule = AndroidComposeTestRule(activityRule) { rule -> rule.activity }

    @Test
    fun pendingLaunchSurvivesRecreationAndAcknowledgedLaunchDoesNotReplay() {
        composeRule.onNodeWithText("Continue with AniList").performClick()
        activityRule.scheduler.runCurrent()

        activityRule.moveBelowStarted()
        activityRule.releaseAuthorization.complete(Unit)
        activityRule.scheduler.runCurrent()
        assertEquals(emptyList<String>(), activityRule.uriHandler.openedUris)

        activityRule.configurationChange()
        activityRule.moveToResumed()
        activityRule.scheduler.runCurrent()
        composeRule.waitForIdle()
        activityRule.scheduler.runCurrent()
        assertEquals(listOf("https://auth.invalid/authorize"), activityRule.uriHandler.openedUris)

        activityRule.configurationChange()
        activityRule.scheduler.runCurrent()
        composeRule.waitForIdle()
        activityRule.scheduler.runCurrent()
        assertEquals(1, activityRule.uriHandler.openedUris.size)
    }
}

class RecreatingOnboardingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CompositionLocalProvider(LocalUriHandler provides uriHandler) {
                MioApp(
                    root = AppRoot.ONBOARDING,
                    navigationRegistry = graph.navigationRegistry,
                    imageLoader = graph.imageLoader,
                    viewModelFactory = graph.metroViewModelFactory,
                )
            }
        }
    }

    internal companion object {
        lateinit var graph: AppGraph
        lateinit var uriHandler: UriHandler
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("DEPRECATION")
class RecreatingOnboardingActivityRule : TestRule {
    val scheduler = TestCoroutineScheduler()
    val releaseAuthorization = CompletableDeferred<Unit>()
    val uriHandler = RecreationRecordingUriHandler()
    lateinit var activity: RecreatingOnboardingActivity
    private lateinit var controller: ActivityController<RecreatingOnboardingActivity>

    private val application = ApplicationProvider.getApplicationContext<MioApplication>()
    private val binding = providerBindingFixture(ProviderKey.ANILIST)
    private val fixture = TestGraphFixture(application, scheduler, binding)
    private val graph = createDynamicGraphFactory<AppGraph.Factory>(fixture.bindings).create(application)

    init {
        val authenticator = binding.authenticator as FakeProviderAuthenticator
        authenticator.enqueueBeginAuthorization {
            releaseAuthorization.await()
            ProviderResult.Success(providerAuthorizationLaunchFixture())
        }
        runBlocking { fixture.accountSessionRepository.restore() }
    }

    override fun apply(
        base: Statement,
        description: Description,
    ): Statement =
        object : Statement() {
            override fun evaluate() {
                Dispatchers.setMain(StandardTestDispatcher(scheduler))
                RecreatingOnboardingActivity.graph = graph
                RecreatingOnboardingActivity.uriHandler = uriHandler
                controller = Robolectric.buildActivity(RecreatingOnboardingActivity::class.java).setup()
                activity = controller.get()
                try {
                    base.evaluate()
                } finally {
                    activity.finish()
                    fixture.close()
                    Dispatchers.resetMain()
                }
            }
        }

    fun moveBelowStarted() {
        controller.pause().stop()
    }

    fun moveToResumed() {
        controller.start().resume().visible()
    }

    fun configurationChange() {
        controller.configurationChange()
        activity = controller.get()
    }
}

class RecreationRecordingUriHandler : UriHandler {
    val openedUris = mutableListOf<String>()

    override fun openUri(uri: String) {
        openedUris += uri
    }
}
