package io.morgan.mio.feature.you.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.morgan.mio.core.common.di.AccountScope
import io.morgan.mio.domain.account.Account
import io.morgan.mio.domain.account.AccountRepository
import io.morgan.mio.domain.collection.CollectionStatisticsRepository
import io.morgan.mio.domain.collection.CollectionStatisticsSnapshot
import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.model.MediaType
import io.morgan.mio.domain.model.OperationId
import io.morgan.mio.domain.preferences.AppearancePreference
import io.morgan.mio.domain.preferences.PreferencesRepository
import io.morgan.mio.domain.progress.ProgressActivityRepository
import io.morgan.mio.domain.progress.RecordedProgressActivity
import io.morgan.mio.domain.sync.RetrySyncIssueUseCase
import io.morgan.mio.domain.sync.SyncInfrastructureException
import io.morgan.mio.domain.sync.SyncRecoveryRepository
import io.morgan.mio.domain.sync.SyncRecoveryState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlin.time.Clock

@OptIn(ExperimentalCoroutinesApi::class)
@ContributesIntoMap(AccountScope::class)
@ViewModelKey
@Inject
internal class YouViewModel(
    private val accountRepository: AccountRepository,
    private val preferencesRepository: PreferencesRepository,
    private val syncRecoveryRepository: SyncRecoveryRepository,
    private val retrySyncIssue: RetrySyncIssueUseCase,
    private val collectionStatisticsRepository: CollectionStatisticsRepository,
    private val progressActivityRepository: ProgressActivityRepository,
    private val clock: Clock,
) : ViewModel() {
    private val pendingRetryIds = MutableStateFlow<Set<String>>(emptySet())
    private val retryFailed = MutableStateFlow(false)
    private val appearanceSaveFailed = MutableStateFlow(false)
    private val reload = MutableStateFlow(0)
    private val selectedStatisticsType = MutableStateFlow(MediaType.ANIME)
    private val activityWindow = MutableStateFlow(currentActivityWindow())

    private val accountContent: Flow<AccountSource> =
        reload.flatMapLatest {
            accountRepository
                .observeSelectedAccount()
                .flatMapLatest { account ->
                    if (account == null) {
                        flowOf(AccountSource.NoAccount)
                    } else {
                        val accountId: AccountId = account.id
                        val statistics =
                            collectionStatisticsRepository
                                .observeStatistics(accountId)
                                .map { snapshot -> StatisticsSource.Content(snapshot) as StatisticsSource }
                                .onStart { emit(StatisticsSource.Loading) }
                                .catch { emit(StatisticsSource.Unavailable) }
                        val activity =
                            activityWindow.flatMapLatest { window ->
                                progressActivityRepository
                                    .observeRecordedActivity(
                                        accountId = accountId,
                                        fromInclusive = window.fromInclusive,
                                        untilExclusive = window.untilExclusive,
                                    ).map { records ->
                                        ActivitySource.Content(window, records) as ActivitySource
                                    }.onStart { emit(ActivitySource.Loading(window)) }
                                    .catch { emit(ActivitySource.Unavailable(window)) }
                            }
                        val content: Flow<AccountSource> =
                            combine(
                                statistics,
                                activity,
                                preferencesRepository.observeDevicePreferences(),
                                syncRecoveryRepository.observeState(),
                            ) { stats, recordedActivity, preferences, recovery ->
                                AccountSource.Content(
                                    account = account,
                                    appearance = preferences.appearance,
                                    recovery = recovery,
                                    statistics = stats,
                                    activity = recordedActivity,
                                )
                            }
                        content.onStart { emit(AccountSource.Loading) }
                    }
                }.catch { emit(AccountSource.Error) }
        }

    internal val uiState: StateFlow<YouUiState> =
        combine(
            accountContent,
            pendingRetryIds,
            retryFailed,
            selectedStatisticsType,
            appearanceSaveFailed,
        ) { source, pending, failed, selectedType, appearanceFailed ->
            when (source) {
                AccountSource.Loading -> YouUiState.Loading
                AccountSource.NoAccount -> YouUiState.NoAccount
                AccountSource.Error -> YouUiState.Error
                is AccountSource.Content -> {
                    val statistics =
                        when (val value = source.statistics) {
                            StatisticsSource.Loading -> YouCollectionStatisticsUiState.Loading
                            StatisticsSource.Unavailable -> YouCollectionStatisticsUiState.Unavailable
                            is StatisticsSource.Content -> YouUiMapper.mapStatistics(value.snapshot)
                        }
                    val activityState =
                        when (val value = source.activity) {
                            is ActivitySource.Loading -> YouActivityUiState.Loading
                            is ActivitySource.Unavailable ->
                                YouActivityUiState.Unavailable(
                                    fromDate = value.window.fromDate,
                                    throughDate = value.window.throughDate,
                                )
                            is ActivitySource.Content ->
                                YouUiMapper.mapActivity(
                                    window = value.window,
                                    activities = value.records,
                                    mediaType = selectedType,
                                )
                        }
                    val rotationDate = source.activity.window.throughDate
                    YouUiMapper
                        .map(
                            account = source.account,
                            appearance = source.appearance,
                            recovery = source.recovery,
                            pendingRetryIds = pending,
                            retryFailed = failed,
                            statistics = statistics,
                            activity = activityState,
                            selectedStatisticsType = selectedType,
                            rotationDate = rotationDate,
                        ).copy(appearanceSaveFailed = appearanceFailed)
                }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = YouUiState.Loading,
        )

    internal fun onScreenResumed() {
        activityWindow.value = currentActivityWindow()
    }

    internal fun onAction(action: YouAction) {
        when (action) {
            is YouAction.SelectAppearance -> selectAppearance(action.preference)
            is YouAction.SelectStatisticsType -> selectedStatisticsType.value = action.mediaType
            is YouAction.RetrySync -> retry(action.operationId)
            YouAction.RetryScreen -> reload.update { it + 1 }
            YouAction.DismissAppearanceError -> appearanceSaveFailed.value = false
            YouAction.DismissRetryError -> retryFailed.value = false
        }
    }

    private fun currentActivityWindow(): YouActivityWindow =
        YouActivityWindow.current(clock.now(), TimeZone.currentSystemDefault())

    private fun selectAppearance(preference: AppearancePreference) {
        appearanceSaveFailed.value = false
        viewModelScope.launch {
            try {
                preferencesRepository.setAppearance(preference)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                appearanceSaveFailed.value = true
            }
        }
    }

    private fun retry(operationId: String) {
        val id = runCatching { OperationId(operationId) }.getOrNull() ?: return
        if (operationId in pendingRetryIds.value) return
        pendingRetryIds.update { it + operationId }
        retryFailed.value = false
        viewModelScope.launch {
            try {
                if (!retrySyncIssue(id)) retryFailed.value = true
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: SyncInfrastructureException) {
                retryFailed.value = true
            } finally {
                pendingRetryIds.update { it - operationId }
            }
        }
    }

    private sealed interface AccountSource {
        data object Loading : AccountSource

        data object NoAccount : AccountSource

        data object Error : AccountSource

        data class Content(
            val account: Account,
            val appearance: AppearancePreference,
            val recovery: SyncRecoveryState,
            val statistics: StatisticsSource,
            val activity: ActivitySource,
        ) : AccountSource
    }

    private sealed interface StatisticsSource {
        data object Loading : StatisticsSource

        data object Unavailable : StatisticsSource

        data class Content(
            val snapshot: CollectionStatisticsSnapshot,
        ) : StatisticsSource
    }

    private sealed interface ActivitySource {
        val window: YouActivityWindow

        data class Loading(
            override val window: YouActivityWindow,
        ) : ActivitySource

        data class Unavailable(
            override val window: YouActivityWindow,
        ) : ActivitySource

        data class Content(
            override val window: YouActivityWindow,
            val records: List<RecordedProgressActivity>,
        ) : ActivitySource
    }
}
