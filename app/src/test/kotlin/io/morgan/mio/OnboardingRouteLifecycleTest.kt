package io.morgan.mio

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.zacsweers.metro.createDynamicGraphFactory
import io.morgan.mio.core.testing.provider.FakeProviderAuthenticator
import io.morgan.mio.core.testing.provider.providerAuthorizationLaunchFixture
import io.morgan.mio.core.testing.provider.providerBindingFixture
import io.morgan.mio.data.provider.api.ProviderKey
import io.morgan.mio.data.provider.api.ProviderResult
import io.morgan.mio.di.AppGraph
import io.morgan.mio.di.TestGraphFixture
import io.morgan.mio.domain.account.AccountSessionFailureReason
import io.morgan.mio.domain.account.AccountSessionState
import io.morgan.mio.domain.account.ConnectedProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
class OnboardingRouteLifecycleTest {
    private val activityRule = LifecycleActivityRule()

    @get:Rule
    val composeRule = AndroidComposeTestRule(activityRule) { rule -> rule.activity }

    @After
    fun resetDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun authorizationLaunchWaitsBelowStartedAndDoesNotReplayAfterAcknowledgement() {
        val scheduler = TestCoroutineScheduler()
        Dispatchers.setMain(StandardTestDispatcher(scheduler))
        val releaseAuthorization = CompletableDeferred<Unit>()
        val binding = providerBindingFixture(ProviderKey.ANILIST)
        val authenticator = binding.authenticator as FakeProviderAuthenticator
        authenticator.enqueueBeginAuthorization {
            releaseAuthorization.await()
            ProviderResult.Success(providerAuthorizationLaunchFixture())
        }
        val fixture = TestGraphFixture(activityRule.activity, scheduler, binding)
        try {
            runBlocking { fixture.accountSessionRepository.restore() }
            val graph = createDynamicGraphFactory<AppGraph.Factory>(fixture.bindings).create(activityRule.activity)
            val uriHandler = RecordingUriOpener()
            showOnboarding(graph, uriHandler)

            composeRule.onNodeWithText("Continue with AniList").performClick()
            scheduler.runCurrent()
            assertEquals(
                AccountSessionState.Authorizing(ConnectedProvider.ANILIST),
                fixture.accountSessionRepository.state.value,
            )

            activityRule.moveBelowStarted()
            releaseAuthorization.complete(Unit)
            scheduler.runCurrent()
            assertTrue(uriHandler.openedUris.isEmpty())

            activityRule.moveToResumed()
            scheduler.runCurrent()
            composeRule.waitForIdle()
            scheduler.runCurrent()
            assertEquals(listOf("https://auth.invalid/authorize"), uriHandler.openedUris)

            activityRule.moveBelowStarted()
            activityRule.moveToResumed()
            scheduler.runCurrent()
            composeRule.waitForIdle()
            assertEquals(1, uriHandler.openedUris.size)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun uriHandlerFailureTransitionsToRetryableFailureInsteadOfStrandingAuthorization() {
        val scheduler = TestCoroutineScheduler()
        Dispatchers.setMain(StandardTestDispatcher(scheduler))
        val binding = providerBindingFixture(ProviderKey.ANILIST)
        val authenticator = binding.authenticator as FakeProviderAuthenticator
        authenticator.enqueueBeginAuthorization { ProviderResult.Success(providerAuthorizationLaunchFixture()) }
        val fixture = TestGraphFixture(activityRule.activity, scheduler, binding)
        try {
            runBlocking { fixture.accountSessionRepository.restore() }
            val graph = createDynamicGraphFactory<AppGraph.Factory>(fixture.bindings).create(activityRule.activity)
            val uriHandler = RecordingUriOpener(failure = IllegalArgumentException("no browser"))
            showOnboarding(graph, uriHandler)

            composeRule.onNodeWithText("Continue with AniList").performClick()
            scheduler.runCurrent()
            composeRule.waitForIdle()
            scheduler.runCurrent()
            composeRule.waitForIdle()

            val failure = fixture.accountSessionRepository.state.value as AccountSessionState.Failed
            assertEquals(AccountSessionFailureReason.AUTHORIZATION_LAUNCH_FAILED, failure.reason)
            assertTrue(failure.retryable)
            assertEquals(1, uriHandler.openedUris.size)
            composeRule.onNodeWithText("We couldn't open AniList").assertIsDisplayed()
            composeRule.onNodeWithText("Retry").assertIsDisplayed()
        } finally {
            fixture.close()
        }
    }

    private fun showOnboarding(
        graph: AppGraph,
        uriHandler: UriHandler,
    ) {
        composeRule.setContent {
            CompositionLocalProvider(LocalUriHandler provides uriHandler) {
                MioApp(
                    root = AppRoot.ONBOARDING,
                    navigationRegistry = graph.navigationRegistry,
                    imageLoader = graph.imageLoader,
                    viewModelFactory = graph.metroViewModelFactory,
                )
            }
        }
        composeRule.onNodeWithText("Continue with AniList").assertIsDisplayed()
    }
}

private class RecordingUriOpener(
    private val failure: RuntimeException? = null,
) : UriHandler {
    val openedUris = mutableListOf<String>()

    override fun openUri(uri: String) {
        openedUris += uri
        failure?.let { throw it }
    }
}

@Suppress("DEPRECATION")
class LifecycleActivityRule : TestRule {
    lateinit var activity: ComponentActivity
    private lateinit var controller: ActivityController<ComponentActivity>

    override fun apply(
        base: Statement,
        description: Description,
    ): Statement =
        object : Statement() {
            override fun evaluate() {
                controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
                activity = controller.get()
                try {
                    base.evaluate()
                } finally {
                    activity.finish()
                }
            }
        }

    fun moveBelowStarted() {
        controller.pause().stop()
    }

    fun moveToResumed() {
        controller.start().resume().visible()
    }
}
