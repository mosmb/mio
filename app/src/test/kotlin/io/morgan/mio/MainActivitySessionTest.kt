package io.morgan.mio

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import dev.zacsweers.metro.createDynamicGraphFactory
import io.morgan.mio.core.testing.provider.FakeProviderAuthenticator
import io.morgan.mio.core.testing.provider.FakeProviderIdentityDataSource
import io.morgan.mio.core.testing.provider.FakeProviderInitialImportDataSource
import io.morgan.mio.core.testing.provider.completeProviderImportFixture
import io.morgan.mio.core.testing.provider.providerBindingFixture
import io.morgan.mio.core.testing.provider.providerCredentialsFixture
import io.morgan.mio.core.testing.provider.providerIdentityFixture
import io.morgan.mio.data.provider.api.ProviderResult
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
import org.junit.Assert.assertFalse
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
            activityRule.activateAccountGraph("fixture-account")
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
    fun completedImportShowsTransientFeedbackButRestorationDoesNotReplayIt() {
        activityRule.scheduler.runCurrent()
        activityRule.fixture.accountSessionRepository.setStateForTest(
            AccountSessionState.Importing(ConnectedProvider.ANILIST),
        )
        activityRule.scheduler.runCurrent()
        composeRule.waitForIdle()

        activityRule.activateAccountGraph("fixture-account")
        activityRule.fixture.accountSessionRepository.setStateForTest(
            AccountSessionState.Authenticated(
                accountId = AccountId("fixture-account"),
                provider = ConnectedProvider.ANILIST,
            ),
        )
        activityRule.scheduler.runCurrent()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Library imported").assertIsDisplayed()
        activityRule.scheduler.advanceTimeBy(4_001)
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Library imported").assertCountEquals(0)

        activityRule.configurationChange()
        activityRule.scheduler.runCurrent()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Library imported").assertCountEquals(0)
    }

    @Test
    fun matchingOAuthIntentCompletesThePendingProviderAuthorization() {
        activityRule.scheduler.runCurrent()
        val repository = activityRule.fixture.accountSessionRepository
        val binding = repository.providerBinding
        val authenticator = binding.authenticator as FakeProviderAuthenticator
        val identity = binding.identityDataSource as FakeProviderIdentityDataSource
        val initialImport = binding.initialImportDataSource as FakeProviderInitialImportDataSource
        authenticator.enqueueBeginAuthorization {
            ProviderResult.Success(
                io.morgan.mio.core.testing.provider
                    .providerAuthorizationLaunchFixture(),
            )
        }
        authenticator.enqueueCompleteAuthorization { _, _ ->
            ProviderResult.Success(providerCredentialsFixture())
        }
        identity.enqueue { ProviderResult.Success(providerIdentityFixture()) }
        initialImport.enqueue { _, accountId ->
            ProviderResult.Success(completeProviderImportFixture(accountId = accountId))
        }
        kotlinx.coroutines.runBlocking {
            repository.startConnection(ConnectedProvider.ANILIST)
        }

        activityRule.deliverIntent(
            Intent(Intent.ACTION_VIEW, Uri.parse("mio://oauth/anilist#access_token=fixture-sensitive-value")),
        )
        activityRule.scheduler.runCurrent()

        assertTrue(repository.state.value is AccountSessionState.Authenticated)
        assertFalse(repository.operations.toString().contains("fixture-sensitive-value"))
    }

    @Test
    fun returningWithoutAnOAuthRedirectAllowsAnotherReconnect() {
        prepareReconnect()
        composeRule.onNodeWithText("Reconnect").performSemanticsAction(SemanticsActions.OnClick) { it() }
        awaitReconnectAuthorization()
        activityRule.backgroundAndReturn()
        activityRule.scheduler.runCurrent()
        assertEquals(
            io.morgan.mio.domain.account.AccountReconnectionState.Idle,
            activityRule.graph.accountReconnectionRepository.state.value,
        )
        composeRule.onNodeWithText("Reconnect").performSemanticsAction(SemanticsActions.OnClick) { it() }
        awaitReconnectAuthorization()
    }

    @Test
    fun reconnectRetainsCapturedIssuesAcrossConfigurationAndConsumesTheRedirectOnce() {
        prepareReconnect()
        composeRule.onNodeWithText("Reconnect").performSemanticsAction(SemanticsActions.OnClick) { it() }
        awaitReconnectAuthorization()
        activityRule.configurationChange()
        activityRule.scheduler.runCurrent()
        val binding = activityRule.fixture.accountSessionRepository.providerBinding
        (binding.authenticator as FakeProviderAuthenticator).enqueueCompleteAuthorization { _, _ ->
            ProviderResult.Success(providerCredentialsFixture())
        }
        (binding.identityDataSource as FakeProviderIdentityDataSource).enqueue {
            ProviderResult.Success(providerIdentityFixture())
        }
        activityRule.deliverIntent(Intent(Intent.ACTION_VIEW, Uri.parse("mio://oauth/anilist?code=fixture")))
        composeRule.waitUntil(10_000) {
            activityRule.scheduler.runCurrent()
            kotlinx.coroutines.runBlocking {
                activityRule.graph.database
                    .syncDao()
                    .getAccountIssue("fixture-account") ==
                    null
            }
        }
        activityRule.configurationChange()
        activityRule.scheduler.runCurrent()
        assertEquals(1, (binding.authenticator as FakeProviderAuthenticator).completeCalls.size)
    }

    private fun awaitReconnectAuthorization() {
        composeRule.waitUntil(10_000) {
            activityRule.scheduler.runCurrent()
            activityRule.graph.accountReconnectionRepository.state.value is
                io.morgan.mio.domain.account.AccountReconnectionState.Authorizing
        }
    }

    private fun prepareReconnect() {
        activityRule.scheduler.runCurrent()
        val fixture = activityRule.fixture
        kotlinx.coroutines.runBlocking {
            fixture.database.selectedAccountDao().replaceSelectedAccount(
                io.morgan.mio.core.database.entity.SelectedAccountEntity(
                    "selected",
                    "fixture-account",
                    io.morgan.mio.core.database.model.StoredConnectedProvider.ANILIST,
                    "Fixture",
                    null,
                ),
                io.morgan.mio.core.database.entity.AccountProviderIdEntity(
                    "fixture-account",
                    io.morgan.mio.core.database.model.StoredConnectedProvider.ANILIST,
                    "account-fixture",
                ),
            )
            fixture.database.syncDao().upsertAccountIssue(
                io.morgan.mio.core.database.entity.SyncAccountIssueEntity(
                    "reconnect-test",
                    "fixture-account",
                    "authentication",
                    fixture.clock.now(),
                ),
            )
        }
        activityRule.activateAccountGraph("fixture-account")
        fixture.accountSessionRepository.setStateForTest(
            AccountSessionState.Authenticated(AccountId("fixture-account"), ConnectedProvider.ANILIST),
        )
        val authenticator = fixture.accountSessionRepository.providerBinding.authenticator as FakeProviderAuthenticator
        repeat(2) {
            authenticator.enqueueBeginAuthorization {
                ProviderResult.Success(
                    io.morgan.mio.core.testing.provider
                        .providerAuthorizationLaunchFixture(),
                )
            }
        }
        activityRule.scheduler.runCurrent()
        composeRule.waitUntil(10_000) {
            activityRule.scheduler.runCurrent()
            composeRule.onAllNodesWithText("Reconnect").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun onboardingViewModelSurvivesConfigurationAndIsRecreatedAfterRootDisposal() {
        activityRule.scheduler.runCurrent()
        composeRule.waitForIdle()
        val repository = activityRule.fixture.accountSessionRepository
        assertEquals(3, repository.stateReads)

        activityRule.configurationChange()
        activityRule.scheduler.runCurrent()
        composeRule.waitForIdle()
        assertEquals(3, repository.stateReads)

        repository.setStateForTest(
            AccountSessionState.Authenticated(
                accountId = AccountId("fixture-account"),
                provider = ConnectedProvider.ANILIST,
            ),
        )
        activityRule.activateAccountGraph("fixture-account")
        activityRule.scheduler.runCurrent()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Today").assertCountEquals(2)

        repository.setStateForTest(AccountSessionState.SignedOut)
        activityRule.scheduler.runCurrent()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Continue with AniList").assertIsDisplayed()
        assertEquals(4, repository.stateReads)
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

    fun backgroundAndReturn() {
        controller.pause().stop()
        controller.restart().start().resume()
    }

    fun deliverIntent(intent: Intent) {
        controller.newIntent(intent)
        activity = controller.get()
    }

    fun activateAccountGraph(accountId: String) {
        if (graph.accountGraphLifecycle.activeHandle() != null) return
        graph.accountGraphLifecycle.create(
            AccountGraphRequest(
                accountId = AccountId(accountId),
                providerBinding = providerBindingFixture(),
            ),
        )
    }

    private fun replaceGraph(graph: AppGraph) {
        val field = MioApplication::class.java.getDeclaredField("appGraph")
        field.isAccessible = true
        field.set(application, graph)
    }
}
