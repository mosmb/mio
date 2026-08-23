package io.morgan.mio

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import dev.zacsweers.metro.createDynamicGraphFactory
import io.morgan.mio.core.testing.provider.providerBindingFixture
import io.morgan.mio.data.account.AccountRefreshRequester
import io.morgan.mio.data.collection.CollectionRefreshRequester
import io.morgan.mio.di.AccountGraphRequest
import io.morgan.mio.di.AppGraph
import io.morgan.mio.di.TestGraphFixture
import io.morgan.mio.domain.account.AccountSessionState
import io.morgan.mio.domain.account.ConnectedProvider
import io.morgan.mio.domain.model.AccountId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class MainActivitySessionTest {
    private val activityRule = MainActivityRule()

    @get:Rule
    val composeRule = AndroidComposeTestRule(activityRule) { rule -> rule.activity }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun restorationKeepsTheOnboardingRootUntilControlledCompletion() {
        assertTrue(activityRule.graph.accountGraphLifecycle.activeHandle() == null)
        val releaseRestore = CompletableDeferred<AccountSessionState?>()
        var restoreStarted = false
        activityRule.fixture.accountSessionRepository.restoreOverride = {
            restoreStarted = true
            releaseRestore.await()
        }

        composeRule.onNodeWithText("Checking your connection").assertIsDisplayed()
        activityRule.scheduler.runCurrent()
        assertTrue(restoreStarted)
        composeRule.onNodeWithText("Checking your connection").assertIsDisplayed()

        releaseRestore.complete(null)
        activityRule.scheduler.runCurrent()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Continue with AniList").assertIsDisplayed()
        composeRule.onNodeWithText("Continue with MyAnimeList").assertIsDisplayed()
    }

    @Test
    fun restorationSuccessSelectsAuthenticatedTodayRootWithoutOnboardingSuccessUi() {
        activityRule.fixture.accountSessionRepository.restoreOverride = {
            AccountSessionState.Authenticated(
                accountId = AccountId("fixture-account"),
                provider = ConnectedProvider.ANILIST,
            )
        }

        activityRule.scheduler.runCurrent()
        composeRule.waitForIdle()

        composeRule.onAllNodesWithText("Today").assertCountEquals(2)
        composeRule.onAllNodesWithText("Continue with AniList").assertCountEquals(0)
        composeRule.onAllNodesWithText("Open Today").assertCountEquals(0)
    }

    @Test
    fun recreationUsesActiveAccountFactoryWhileInitialShellUsesAppFallback() {
        assertTrue(activityRule.graph.accountGraphLifecycle.activeHandle() == null)
        activityRule.fixture.accountSessionRepository.setStateForTest(
            AccountSessionState.Authenticated(
                accountId = AccountId("fixture-account"),
                provider = ConnectedProvider.ANILIST,
            ),
        )
        val activeHandle =
            activityRule.graph.accountGraphLifecycle.create(
                AccountGraphRequest(
                    accountId = AccountId("fixture-account"),
                    providerBinding = providerBindingFixture(),
                    accountRefreshRequester = AccountRefreshRequester {},
                    collectionRefreshRequester = CollectionRefreshRequester {},
                ),
            )

        activityRule.configurationChange()
        activityRule.scheduler.runCurrent()
        composeRule.waitForIdle()

        assertTrue(activityRule.graph.accountGraphLifecycle.activeHandle() === activeHandle)
        activeHandle.withGraph { accountGraph ->
            assertTrue(accountGraph.metroViewModelFactory !== activityRule.graph.metroViewModelFactory)
        }
        composeRule.onAllNodesWithText("Today").assertCountEquals(2)
    }

    @Test
    fun onboardingViewModelSurvivesConfigurationAndIsRecreatedAfterRootDisposal() {
        activityRule.scheduler.runCurrent()
        composeRule.waitForIdle()
        val repository = activityRule.fixture.accountSessionRepository
        assertEquals(2, repository.stateReads)

        activityRule.configurationChange()
        activityRule.scheduler.runCurrent()
        composeRule.waitForIdle()
        assertEquals(2, repository.stateReads)

        repository.setStateForTest(
            AccountSessionState.Authenticated(
                accountId = AccountId("fixture-account"),
                provider = ConnectedProvider.ANILIST,
            ),
        )
        activityRule.scheduler.runCurrent()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Today").assertCountEquals(2)

        repository.setStateForTest(AccountSessionState.SignedOut)
        activityRule.scheduler.runCurrent()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Continue with AniList").assertIsDisplayed()
        assertEquals(3, repository.stateReads)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("DEPRECATION")
class MainActivityRule : TestRule {
    val scheduler = TestCoroutineScheduler()
    internal val fixture: TestGraphFixture
    internal val graph: AppGraph
        get() = application.appGraph
    lateinit var activity: MainActivity
    private lateinit var controller: ActivityController<MainActivity>

    private val application = ApplicationProvider.getApplicationContext<MioApplication>()
    private val previousGraph: AppGraph

    init {
        previousGraph = application.appGraph
        fixture = TestGraphFixture(application, scheduler)
        replaceGraph(
            createDynamicGraphFactory<AppGraph.Factory>(fixture.bindings).create(application),
        )
        Dispatchers.setMain(StandardTestDispatcher(scheduler))
    }

    override fun apply(
        base: Statement,
        description: Description,
    ): Statement =
        object : Statement() {
            override fun evaluate() {
                try {
                    controller = Robolectric.buildActivity(MainActivity::class.java).setup()
                    activity = controller.get()
                    base.evaluate()
                } finally {
                    if (::activity.isInitialized) {
                        activity.finish()
                    }
                    application.appGraph.accountGraphLifecycle.destroy()
                    replaceGraph(previousGraph)
                    fixture.close()
                }
            }
        }

    fun configurationChange() {
        controller.configurationChange()
        activity = controller.get()
    }

    private fun replaceGraph(graph: AppGraph) {
        val field = MioApplication::class.java.getDeclaredField("appGraph")
        field.isAccessible = true
        field.set(application, graph)
    }
}
