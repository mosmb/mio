package io.morgan.mio.feature.you.impl

import io.morgan.mio.domain.account.Account
import io.morgan.mio.domain.account.AccountRepository
import io.morgan.mio.domain.account.ConnectedProvider
import io.morgan.mio.domain.collection.CollectionStatisticsRepository
import io.morgan.mio.domain.collection.CollectionStatisticsSnapshot
import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.model.OperationId
import io.morgan.mio.domain.preferences.AccountPreferences
import io.morgan.mio.domain.preferences.AppearancePreference
import io.morgan.mio.domain.preferences.DevicePreferences
import io.morgan.mio.domain.preferences.LibraryMediaFilter
import io.morgan.mio.domain.preferences.LibraryStatusFilter
import io.morgan.mio.domain.preferences.MotionPreference
import io.morgan.mio.domain.preferences.PreferencesRepository
import io.morgan.mio.domain.progress.ProgressActivityRepository
import io.morgan.mio.domain.progress.RecordedProgressActivity
import io.morgan.mio.domain.sync.PendingOperationGroup
import io.morgan.mio.domain.sync.RetrySyncIssueUseCase
import io.morgan.mio.domain.sync.SyncIssue
import io.morgan.mio.domain.sync.SyncRecoveryRepository
import io.morgan.mio.domain.sync.SyncRecoveryState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Clock
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class YouViewModelTest {
    @After
    fun resetMain() = Dispatchers.resetMain()

    @Test
    fun accountPreferencesAndRecoveryReadFailuresCanBeRetriedWithoutRecreatingScreen() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            for (source in listOf("account", "preferences", "recovery")) {
                val fixture = Fixture().apply { failure = source }
                val model = fixture.viewModel()
                val observer = backgroundScope.launch { model.uiState.collect() }
                runCurrent()
                assertEquals(source, YouUiState.Error, model.uiState.value)

                fixture.failure = null
                model.onAction(YouAction.RetryScreen)
                runCurrent()
                assertTrue(source, model.uiState.value is YouUiState.Content)
                observer.cancel()
            }
        }

    @Test
    fun failedStatisticsAndActivityKeepAccountControlsAndRetryTheirSubscriptions() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            for (source in listOf("statistics", "activity")) {
                val fixture = Fixture().apply { failure = source }
                val model = fixture.viewModel()
                val observer = backgroundScope.launch { model.uiState.collect() }
                runCurrent()
                val content = model.uiState.value as YouUiState.Content
                assertEquals("Fixture account", content.identity.displayName)
                if (source == "statistics") {
                    assertEquals(YouCollectionStatisticsUiState.Unavailable, content.statistics)
                } else {
                    assertTrue(content.activity is YouActivityUiState.Unavailable)
                }

                fixture.failure = null
                model.onAction(YouAction.RetryScreen)
                runCurrent()
                val recovered = model.uiState.value as YouUiState.Content
                assertTrue(recovered.statistics is YouCollectionStatisticsUiState.Content)
                assertTrue(recovered.activity is YouActivityUiState.Content)
                observer.cancel()
            }
        }

    @Test
    fun appearanceWriteFailureIsDistinctFromSyncFailureAndCanBeDismissed() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val fixture = Fixture().apply { failure = "writeAppearance" }
            val model = fixture.viewModel()
            backgroundScope.launch { model.uiState.collect() }
            runCurrent()
            model.onAction(YouAction.SelectAppearance(AppearancePreference.DARK))
            runCurrent()
            val content = model.uiState.value as YouUiState.Content
            assertTrue(content.appearanceSaveFailed)
            assertFalse(content.retryFailed)
            model.onAction(YouAction.DismissAppearanceError)
            runCurrent()
            assertFalse((model.uiState.value as YouUiState.Content).appearanceSaveFailed)
        }

    private class Fixture :
        AccountRepository,
        PreferencesRepository,
        SyncRecoveryRepository,
        CollectionStatisticsRepository,
        ProgressActivityRepository {
        var failure: String? = null

        private fun <T> source(
            name: String,
            value: T,
        ): Flow<T> =
            flow {
                check(failure != name) { "fixture read failure" }
                emit(value)
            }

        fun viewModel() = YouViewModel(this, this, this, RetrySyncIssueUseCase(this), this, this, Clock.System)

        override fun observeSelectedAccount() =
            source("account", Account(AccountId("account"), ConnectedProvider.ANILIST, "Fixture account", null))

        override suspend fun refreshSelectedAccount() = Unit

        override fun observeDevicePreferences() = source("preferences", DevicePreferences.Default)

        override fun observeAccountPreferences(accountId: AccountId): Flow<AccountPreferences> = error("unused")

        override suspend fun setAppearance(preference: AppearancePreference) {
            check(failure != "writeAppearance")
        }

        override suspend fun setMotion(preference: MotionPreference) = Unit

        override suspend fun setHapticsEnabled(enabled: Boolean) = Unit

        override suspend fun setLibraryMediaFilter(
            accountId: AccountId,
            filter: LibraryMediaFilter,
        ) = Unit

        override suspend fun setLibraryStatusFilter(
            accountId: AccountId,
            filter: LibraryStatusFilter,
        ) = Unit

        override fun observeState() = source("recovery", SyncRecoveryState(null, emptyList()))

        override suspend fun readIssue(operationId: OperationId): SyncIssue? = null

        override suspend fun rearm(issue: SyncIssue) = false

        override suspend fun resolveConflict(
            issue: SyncIssue,
            resolution: PendingOperationGroup,
        ) = false

        override suspend fun recoverAuthentication(
            accountId: AccountId,
            issueIds: Set<String>,
        ) = false

        override fun observeStatistics(accountId: AccountId) =
            source(
                "statistics",
                CollectionStatisticsSnapshot(
                    completedTitleCount = 0,
                    recordedEpisodes = CollectionStatisticsSnapshot.RecordedProgressTotal(null, 0, 0),
                    recordedChapters = CollectionStatisticsSnapshot.RecordedProgressTotal(null, 0, 0),
                    anime = CollectionStatisticsSnapshot.TypeStatistics(0, 0, 0, emptyList()),
                    manga = CollectionStatisticsSnapshot.TypeStatistics(0, 0, 0, emptyList()),
                    artwork = emptyList(),
                ),
            )

        override fun observeRecordedActivity(
            accountId: AccountId,
            fromInclusive: Instant,
            untilExclusive: Instant,
        ): Flow<List<RecordedProgressActivity>> = source("activity", emptyList())
    }
}
