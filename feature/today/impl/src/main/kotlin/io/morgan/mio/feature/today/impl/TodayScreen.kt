package io.morgan.mio.feature.today.impl

import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import coil3.ImageLoader
import dev.zacsweers.metrox.viewmodel.metroViewModel
import io.morgan.mio.core.designsystem.accessibility.MioHapticType
import io.morgan.mio.core.designsystem.accessibility.mioHeading
import io.morgan.mio.core.designsystem.accessibility.rememberMioHaptics
import io.morgan.mio.core.designsystem.component.MioEmptyState
import io.morgan.mio.core.designsystem.component.MioSkeleton
import io.morgan.mio.core.designsystem.component.MioSnackbarHost
import io.morgan.mio.core.designsystem.component.MioSnackbarMessage
import io.morgan.mio.core.designsystem.preview.MioPreviewConfiguration
import io.morgan.mio.core.designsystem.preview.MioPreviewSurface
import io.morgan.mio.core.designsystem.theme.MioTheme
import io.morgan.mio.core.ui.LocalAccountRecoveryActions
import io.morgan.mio.core.ui.image.MioImageLoaderProvider
import io.morgan.mio.domain.model.CollectionEntryId
import io.morgan.mio.domain.model.MediaId
import io.morgan.mio.domain.model.OperationId
import io.morgan.mio.feature.today.impl.component.TodayCaughtUpReceipt
import io.morgan.mio.feature.today.impl.component.TodayNewRow
import io.morgan.mio.feature.today.impl.component.TodayNextReleaseRow
import io.morgan.mio.feature.today.impl.component.TodayNowCard
import io.morgan.mio.feature.today.impl.component.TodayRemainingReadySummary

@Composable
internal fun TodayRoute(
    modifier: Modifier = Modifier,
    onMediaDetail: (MediaId) -> Unit,
    onRemainingReady: () -> Unit,
    onExploreDiscover: () -> Unit,
    onResolveProgressConflict: (OperationId, CollectionEntryId) -> Unit,
) {
    val viewModel = metroViewModel<TodayViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val recoveryActions = LocalAccountRecoveryActions.current
    val haptics by rememberUpdatedState(rememberMioHaptics())
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val timeoutMillis =
        remember(context) {
            (context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager)
                ?.getRecommendedTimeoutMillis(
                    DEFAULT_UNDO_TIMEOUT_MILLIS,
                    AccessibilityManager.FLAG_CONTENT_TEXT or AccessibilityManager.FLAG_CONTENT_CONTROLS,
                )?.toLong()
                ?: DEFAULT_UNDO_TIMEOUT_MILLIS.toLong()
        }
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            viewModel.onAction(TodayAction.VisibilityChanged(visible = true))
            try {
                viewModel.routineHaptics.collect { haptics.perform(MioHapticType.ROUTINE_TICK) }
            } finally {
                viewModel.onAction(TodayAction.VisibilityChanged(visible = false))
            }
        }
    }
    TodayScreen(
        modifier = modifier,
        state = state,
        onMediaDetail = { mediaId -> onMediaDetail(MediaId(mediaId)) },
        onRemainingReady = onRemainingReady,
        onExploreDiscover = onExploreDiscover,
        onResolveProgressConflict = { operationId, entryId ->
            onResolveProgressConflict(OperationId(operationId), CollectionEntryId(entryId))
        },
        onAction = viewModel::onAction,
        timeoutMillis = timeoutMillis,
        providerLabel = recoveryActions.providerLabel,
        onReconnect = recoveryActions.onReconnect,
        onRetryRefresh = recoveryActions.onRetryRefresh,
    )
}

@Composable
internal fun TodayScreen(
    modifier: Modifier = Modifier,
    state: TodayUiState,
    onMediaDetail: (String) -> Unit,
    onRemainingReady: () -> Unit = {},
    onExploreDiscover: () -> Unit = {},
    onResolveProgressConflict: (String, String) -> Unit = { _, _ -> },
    onAction: (TodayAction) -> Unit = {},
    timeoutMillis: Long = DEFAULT_UNDO_TIMEOUT_MILLIS.toLong(),
    providerLabel: String? = null,
    onReconnect: () -> Unit = {},
    onRetryRefresh: () -> Unit = {},
) {
    val state =
        state.withTitleLanguage(
            io.morgan.mio.core.ui.title
                .rememberTitleResolver(),
        )
    val scrollState = rememberScrollState()
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(
                        PaddingValues(
                            start = MioTheme.spacing.screenGutter,
                            top = MioTheme.spacing.rootTopContentPadding,
                            end = MioTheme.spacing.screenGutter,
                            bottom = 128.dp,
                        ),
                    ),
        ) {
            Text(
                text = stringResource(R.string.today_title),
                style = MioTheme.typography.display,
                modifier = Modifier.mioHeading(),
            )
            TodayRecovery(
                syncIssues = state.syncIssues(),
                accountIssue = state.accountIssue(),
                providerLabel = providerLabel,
                onReconnect = onReconnect,
                onRetryRefresh = onRetryRefresh,
                onMediaDetail = onMediaDetail,
                onResolveProgressConflict = onResolveProgressConflict,
                onAction = onAction,
                modifier = Modifier.padding(top = 24.dp),
            )
            when (state) {
                is TodayUiState.Loading -> {
                    TodayNowSectionHeading()
                    TodayLoadingCard(modifier = Modifier.padding(top = 12.dp))
                }
                is TodayUiState.Populated -> {
                    TodayNowSectionHeading()
                    TodayNowCard(
                        now = state.now,
                        onClick = { onMediaDetail(state.now.mediaId) },
                        onDecrease = {
                            onAction(
                                TodayAction.Step(
                                    mediaId = state.now.mediaId,
                                    direction = TodayProgressDirection.DECREASE,
                                    timeoutMillis = timeoutMillis,
                                ),
                            )
                        },
                        onIncrease = {
                            onAction(
                                TodayAction.Step(
                                    mediaId = state.now.mediaId,
                                    direction = TodayProgressDirection.INCREASE,
                                    timeoutMillis = timeoutMillis,
                                ),
                            )
                        },
                        onInteractionChanged = { pressed ->
                            onAction(TodayAction.InteractionChanged(state.now.mediaId, pressed))
                        },
                        onMotionChanged = { generation, active ->
                            onAction(TodayAction.MotionChanged(state.now.mediaId, generation, active))
                        },
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    if (state.newRecommendations.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.today_new),
                            style = MioTheme.typography.section,
                            modifier = Modifier.padding(top = 24.dp).mioHeading(),
                        )
                        state.newRecommendations.forEachIndexed { index, recommendation ->
                            if (index > 0) TodayRowDivider()
                            TodayNewRow(
                                recommendation = recommendation,
                                onClick = { onMediaDetail(recommendation.mediaId) },
                                onDecrease = {
                                    onAction(
                                        TodayAction.Step(
                                            mediaId = recommendation.mediaId,
                                            direction = TodayProgressDirection.DECREASE,
                                            timeoutMillis = timeoutMillis,
                                        ),
                                    )
                                },
                                onIncrease = {
                                    onAction(
                                        TodayAction.Step(
                                            mediaId = recommendation.mediaId,
                                            direction = TodayProgressDirection.INCREASE,
                                            timeoutMillis = timeoutMillis,
                                        ),
                                    )
                                },
                                onInteractionChanged = { pressed ->
                                    onAction(TodayAction.InteractionChanged(recommendation.mediaId, pressed))
                                },
                                onMotionChanged = { generation, active ->
                                    onAction(TodayAction.MotionChanged(recommendation.mediaId, generation, active))
                                },
                                modifier = Modifier.padding(top = if (index == 0) 12.dp else 0.dp),
                            )
                        }
                    }
                    if (state.remainingReadyCount > 0) {
                        TodayRemainingReadySummary(
                            count = state.remainingReadyCount,
                            onClick = onRemainingReady,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                    }
                }
                is TodayUiState.CaughtUp -> {
                    if (state.isCollectionEmpty) {
                        MioEmptyState(
                            title = stringResource(R.string.today_empty_title),
                            message = stringResource(R.string.today_empty_message),
                            actionLabel = stringResource(R.string.today_search_titles),
                            onAction = onExploreDiscover,
                            modifier = Modifier.padding(top = 24.dp),
                        )
                    } else {
                        TodayCaughtUpReceipt(
                            receipt = state.receipt,
                            onExploreDiscover = onExploreDiscover,
                            modifier = Modifier.padding(top = 24.dp),
                        )
                    }
                    state.receipt.nextRelease?.let { recommendation ->
                        TodayNextReleaseRow(
                            recommendation = recommendation,
                            onClick = { onMediaDetail(recommendation.mediaId) },
                            modifier = Modifier.padding(top = 16.dp),
                        )
                    }
                }
            }
        }
        TodaySnackbar(
            state = state,
            onAction = onAction,
            providerLabel = providerLabel,
        )
    }
}

@Composable
private fun TodayRecovery(
    syncIssues: List<TodayUiState.SyncIssueUiModel>,
    accountIssue: TodayUiState.AccountIssueUiModel?,
    providerLabel: String?,
    onReconnect: () -> Unit,
    onRetryRefresh: () -> Unit,
    onMediaDetail: (String) -> Unit,
    onResolveProgressConflict: (String, String) -> Unit,
    onAction: (TodayAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (syncIssues.isEmpty() && accountIssue == null) return
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement =
            androidx.compose.foundation.layout.Arrangement
                .spacedBy(12.dp),
    ) {
        accountIssue?.let { issue ->
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text =
                        if (issue.kind == AccountIssueKind.AUTHENTICATION && providerLabel != null) {
                            stringResource(R.string.today_sync_authentication, providerLabel)
                        } else {
                            stringResource(R.string.today_sync_refresh_issue)
                        },
                    style = MioTheme.typography.bodyStrong,
                )
                if (issue.kind == AccountIssueKind.AUTHENTICATION && providerLabel != null) {
                    TextButton(
                        onClick = onReconnect,
                        colors = ButtonDefaults.textButtonColors(contentColor = MioTheme.colors.text),
                    ) {
                        Text(stringResource(R.string.today_sync_reconnect))
                    }
                } else if (issue.kind == AccountIssueKind.INVALID_REMOTE_DATA) {
                    TextButton(
                        onClick = onRetryRefresh,
                        colors = ButtonDefaults.textButtonColors(contentColor = MioTheme.colors.text),
                    ) {
                        Text(stringResource(R.string.today_sync_retry_refresh))
                    }
                }
            }
        }
        syncIssues.forEach { issue ->
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .then(
                            if (issue.mediaId != null) {
                                Modifier.clickable { onMediaDetail(issue.mediaId) }
                            } else {
                                Modifier
                            },
                        ),
                verticalArrangement =
                    androidx.compose.foundation.layout.Arrangement
                        .spacedBy(4.dp),
            ) {
                Text(
                    text = issue.title ?: stringResource(R.string.today_sync_saved_title),
                    style = MioTheme.typography.bodyStrong,
                )
                Text(text = issue.message(providerLabel), style = MioTheme.typography.body)
                Text(
                    text =
                        stringResource(
                            R.string.today_sync_local_value,
                            issue.localProgress,
                            issue.localUnit.displayName(),
                        ),
                    style = MioTheme.typography.caption,
                )
                if (issue.remoteProgress != null && issue.remoteUnit != null) {
                    Text(
                        text =
                            stringResource(
                                R.string.today_sync_remote_value,
                                issue.remoteProgress,
                                issue.remoteUnit.displayName(),
                            ),
                        style = MioTheme.typography.caption,
                    )
                }
                if (issue.kind == SyncIssueKind.AUTHENTICATION && providerLabel != null) {
                    TextButton(
                        onClick = onReconnect,
                        colors = ButtonDefaults.textButtonColors(contentColor = MioTheme.colors.text),
                    ) {
                        Text(stringResource(R.string.today_sync_reconnect))
                    }
                } else if (issue.kind == SyncIssueKind.CONFLICT) {
                    TextButton(
                        onClick = { onResolveProgressConflict(issue.operationId, issue.entryId) },
                        colors = ButtonDefaults.textButtonColors(contentColor = MioTheme.colors.text),
                    ) {
                        Text(stringResource(R.string.today_sync_resolve))
                    }
                } else if (issue.canRetry) {
                    TextButton(
                        onClick = { onAction(TodayAction.RetrySync(issue.operationId)) },
                        colors = ButtonDefaults.textButtonColors(contentColor = MioTheme.colors.text),
                    ) {
                        Text(stringResource(R.string.today_sync_retry))
                    }
                }
            }
        }
    }
}

@Composable
private fun TodayUiState.SyncIssueUiModel.message(providerLabel: String?): String =
    when (kind) {
        SyncIssueKind.AUTHENTICATION -> stringResource(R.string.today_sync_authentication_required)
        SyncIssueKind.REJECTED ->
            stringResource(R.string.today_sync_rejected, providerLabel ?: stringResource(R.string.today_sync_account))
        SyncIssueKind.CONFLICT -> stringResource(R.string.today_sync_conflict)
        SyncIssueKind.RETRY_EXHAUSTED -> stringResource(R.string.today_sync_retry_exhausted)
        SyncIssueKind.INVALID_REMOTE_DATA -> stringResource(R.string.today_sync_invalid_data)
    }

@Composable
private fun TodayProgressUnit.displayName(): String =
    when (this) {
        TodayProgressUnit.EPISODE -> stringResource(R.string.today_sync_episodes)
        TodayProgressUnit.CHAPTER -> stringResource(R.string.today_sync_chapters)
        TodayProgressUnit.VOLUME -> stringResource(R.string.today_sync_volumes)
    }

@Composable
private fun TodaySnackbar(
    state: TodayUiState,
    onAction: (TodayAction) -> Unit,
    providerLabel: String?,
) {
    val error = state.progressErrorOrNull()
    val undo = state.latestUndoOrNull()
    val syncIssue = state.syncIssues().firstOrNull { it.mediaId == undo?.mediaId }
    val message =
        when {
            error != null ->
                MioSnackbarMessage(
                    id = PROGRESS_ERROR_SNACKBAR_ID,
                    message = stringResource(R.string.today_progress_error),
                    actionLabel = stringResource(R.string.today_retry_progress).takeIf { error.retryable },
                )
            undo != null ->
                MioSnackbarMessage(
                    id = undo.groupId,
                    message =
                        if (syncIssue != null) {
                            syncIssue.message(providerLabel)
                        } else {
                            stringResource(
                                when (undo.unit) {
                                    TodayProgressUnit.EPISODE -> R.string.today_saved_episode
                                    TodayProgressUnit.CHAPTER -> R.string.today_saved_chapter
                                    TodayProgressUnit.VOLUME -> R.string.today_saved_volume
                                },
                                undo.progress,
                            )
                        },
                    actionLabel = stringResource(R.string.today_undo),
                )
            else -> null
        }
    // Root labels wrap at accessibility text sizes. Reserve two scaled label lines
    // in addition to the host's normal navigation clearance so Undo stays above them.
    val density = LocalDensity.current
    val largeTextNavigationClearance =
        if (density.fontScale > 1.3f) {
            with(density) {
                MioTheme.typography.label.lineHeight
                    .toDp()
            } * 2
        } else {
            0.dp
        }
    MioSnackbarHost(
        snackbar = message,
        bottomNavigationVisible = true,
        modifier = Modifier.padding(bottom = largeTextNavigationClearance),
        onAction = { id ->
            if (id == PROGRESS_ERROR_SNACKBAR_ID) {
                onAction(TodayAction.RetryProgress)
            } else {
                onAction(TodayAction.Undo(id))
            }
        },
    )
}

private fun TodayUiState.progressErrorOrNull(): TodayProgressErrorUiModel? =
    when (this) {
        is TodayUiState.Loading -> progressError
        is TodayUiState.Populated -> progressError
        is TodayUiState.CaughtUp -> progressError
    }

private fun TodayUiState.latestUndoOrNull(): TodayUndoUiModel? =
    when (this) {
        is TodayUiState.Loading -> latestUndo
        is TodayUiState.Populated -> latestUndo
        is TodayUiState.CaughtUp -> latestUndo
    }

private fun TodayUiState.syncIssues(): List<TodayUiState.SyncIssueUiModel> =
    when (this) {
        is TodayUiState.Loading -> syncIssues
        is TodayUiState.Populated -> syncIssues
        is TodayUiState.CaughtUp -> syncIssues
    }

private fun TodayUiState.accountIssue(): TodayUiState.AccountIssueUiModel? =
    when (this) {
        is TodayUiState.Loading -> accountIssue
        is TodayUiState.Populated -> accountIssue
        is TodayUiState.CaughtUp -> accountIssue
    }

private const val DEFAULT_UNDO_TIMEOUT_MILLIS: Int = 6_000
private const val PROGRESS_ERROR_SNACKBAR_ID: String = "progress-error"

@Composable
private fun TodayNowSectionHeading() {
    Text(
        text = stringResource(R.string.today_now),
        style = MioTheme.typography.section,
        modifier = Modifier.padding(top = 24.dp).mioHeading(),
    )
}

@Composable
private fun TodayRowDivider() {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MioTheme.colors.border),
    )
}

@Composable
private fun TodayLoadingCard(modifier: Modifier = Modifier) {
    val loadingDescription = stringResource(R.string.today_loading)
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(332.dp)
                .semantics(mergeDescendants = true) {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = loadingDescription
                },
        contentAlignment = Alignment.Center,
    ) {
        MioSkeleton(
            modifier = Modifier.fillMaxSize(),
            shape = MioTheme.shapes.lg,
        )
    }
}

private val PreviewImageLoader: @Composable () -> ImageLoader = {
    val context: Context = LocalContext.current
    remember(context) { ImageLoader.Builder(context).build() }
}

@Preview(name = "Today populated light", widthDp = 393, heightDp = 852, showBackground = true)
@Composable
private fun TodayScreenLightPreview() {
    TodayPreview(MioPreviewConfiguration.Light, todayPreviewPopulated())
}

@Preview(
    name = "Today populated dark",
    widthDp = 393,
    heightDp = 852,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
)
@Composable
private fun TodayScreenDarkPreview() {
    TodayPreview(MioPreviewConfiguration.Dark, todayPreviewPopulated())
}

@Preview(name = "Today unknown total", widthDp = 393, heightDp = 852)
@Composable
private fun TodayScreenUnknownTotalPreview() {
    TodayPreview(
        MioPreviewConfiguration.Light,
        TodayUiState.Populated(
            now =
                todayPreviewNow(
                    total = null,
                    progress = 41,
                    nextUnit = 42,
                    availableUnitCount = 3,
                    bannerReference = PREVIEW_VERY_LIGHT_ARTWORK,
                ),
            newRecommendations = emptyList(),
            remainingReadyCount = 0,
        ),
    )
}

@Preview(name = "Today missing artwork", widthDp = 393, heightDp = 852)
@Composable
private fun TodayScreenMissingArtworkPreview() {
    TodayPreview(
        MioPreviewConfiguration.Light,
        TodayUiState.Populated(
            now = todayPreviewNow(posterReference = null, bannerReference = null),
            newRecommendations =
                todayPreviewNewRecommendations()
                    .take(1)
                    .map { recommendation -> recommendation.copy(posterReference = null) },
            remainingReadyCount = 0,
        ),
    )
}

@Preview(name = "Today loading", widthDp = 393, heightDp = 852)
@Composable
private fun TodayScreenLoadingPreview() {
    TodayPreview(MioPreviewConfiguration.Light, TodayUiState.Loading())
}

@Preview(name = "Today no recommendation", widthDp = 393, heightDp = 852)
@Composable
private fun TodayScreenEmptyPreview() {
    TodayPreview(
        MioPreviewConfiguration.Light,
        TodayUiState.CaughtUp(TodayCaughtUpUiModel(artwork = emptyList(), nextRelease = null)),
    )
}

@Preview(name = "Today large text", widthDp = 320, heightDp = 852, fontScale = 2f)
@Composable
private fun TodayScreenLargeTextPreview() {
    TodayPreview(
        MioPreviewConfiguration.Light,
        todayPreviewPopulated(now = todayPreviewNow(bannerReference = PREVIEW_VERY_DARK_ARTWORK)),
    )
}

@Preview(name = "Today caught up light", widthDp = 393, heightDp = 852, showBackground = true)
@Composable
private fun TodayScreenCaughtUpLightPreview() {
    TodayPreview(MioPreviewConfiguration.Light, todayPreviewCaughtUp())
}

@Preview(
    name = "Today caught up dark",
    widthDp = 393,
    heightDp = 852,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
)
@Composable
private fun TodayScreenCaughtUpDarkPreview() {
    TodayPreview(MioPreviewConfiguration.Dark, todayPreviewCaughtUp())
}

@Preview(name = "Today backlog only", widthDp = 393, heightDp = 852)
@Composable
private fun TodayScreenBacklogPreview() {
    TodayPreview(
        MioPreviewConfiguration.Light,
        todayPreviewPopulated(newRecommendations = emptyList(), remainingReadyCount = 4),
    )
}

@Preview(name = "Today caught up large text", widthDp = 320, heightDp = 852, fontScale = 2f)
@Composable
private fun TodayScreenCaughtUpLargeTextPreview() {
    TodayPreview(MioPreviewConfiguration.Light, todayPreviewCaughtUp())
}

@Preview(name = "Today progress Zero", widthDp = 393, heightDp = 852)
@Composable
private fun TodayProgressZeroPreview() {
    TodayPreview(
        MioPreviewConfiguration.Light,
        todayPreviewPopulated(now = todayPreviewNow(progress = 0, nextUnit = 1)),
    )
}

@Preview(name = "Today progress Chapter", widthDp = 393, heightDp = 852)
@Composable
private fun TodayProgressChapterPreview() {
    TodayPreview(
        MioPreviewConfiguration.Light,
        todayPreviewPopulated(
            now =
                todayPreviewNow(
                    progress = 41,
                    total = 50,
                    nextUnit = 42,
                ).copy(progressUnit = TodayProgressUnit.CHAPTER),
        ),
    )
}

@Preview(name = "Today progress Volume", widthDp = 393, heightDp = 852)
@Composable
private fun TodayProgressVolumePreview() {
    TodayPreview(
        MioPreviewConfiguration.Light,
        todayPreviewPopulated(
            now = todayPreviewNow(progress = 2, total = 3, nextUnit = 3).copy(progressUnit = TodayProgressUnit.VOLUME),
        ),
    )
}

@Preview(name = "Today progress CompletedUndo", widthDp = 393, heightDp = 852)
@Composable
private fun TodayProgressCompletedUndoPreview() {
    TodayPreview(
        MioPreviewConfiguration.Light,
        todayPreviewPopulated(
            now =
                todayPreviewNow(
                    progress = 12,
                ).copy(
                    status = TodayCollectionStatus.COMPLETED,
                    controls = null,
                    nextUnit = null,
                    availableUnitCount = null,
                ),
        ).copy(
            latestUndo =
                TodayUndoUiModel(
                    "preview-action",
                    "preview-media",
                    12,
                    TodayProgressUnit.EPISODE,
                    true,
                ),
        ),
    )
}

@Preview(name = "Today progress SaveError", widthDp = 393, heightDp = 852)
@Composable
private fun TodayProgressSaveErrorPreview() {
    TodayPreview(
        MioPreviewConfiguration.Light,
        todayPreviewPopulated().copy(progressError = TodayProgressErrorUiModel(true)),
    )
}

@Preview(name = "Today progress ReducedMotion", widthDp = 393, heightDp = 852)
@Composable
private fun TodayProgressReducedMotionPreview() {
    TodayPreview(MioPreviewConfiguration.ReducedMotionLight, todayPreviewPopulated())
}

@Preview(name = "Today error large text", widthDp = 320, heightDp = 852, fontScale = 2f)
@Composable
private fun TodayProgressErrorLargePreview() {
    TodayPreview(
        MioPreviewConfiguration.Light,
        todayPreviewPopulated().copy(progressError = TodayProgressErrorUiModel(true)),
    )
}

@Composable
private fun TodayPreview(
    configuration: MioPreviewConfiguration,
    state: TodayUiState,
) {
    MioPreviewSurface(configuration) {
        MioImageLoaderProvider(PreviewImageLoader()) {
            TodayScreen(
                state = state,
                onMediaDetail = {},
            )
        }
    }
}

private fun todayPreviewNow(
    total: Int? = 12,
    progress: Int = 8,
    nextUnit: Int = 9,
    availableUnitCount: Int = 4,
    posterReference: String? = PREVIEW_HIGH_DETAIL_ARTWORK,
    bannerReference: String? = PREVIEW_HIGH_DETAIL_ARTWORK,
): TodayNowUiModel =
    TodayNowUiModel(
        mediaId = "preview-media",
        mediaType = TodayMediaType.ANIME,
        title = "A Story With Room to Breathe",
        posterReference = posterReference,
        bannerReference = bannerReference,
        progress = progress,
        progressUnit = TodayProgressUnit.EPISODE,
        total = total,
        nextUnit = nextUnit,
        availableUnitCount = availableUnitCount,
        controls = TodayProgressControlsUiModel(progress > 0, total == null || progress < total),
    )

private fun todayPreviewPopulated(
    now: TodayNowUiModel = todayPreviewNow(),
    newRecommendations: List<TodayNewUiModel> = todayPreviewNewRecommendations(),
    remainingReadyCount: Int = 2,
): TodayUiState.Populated =
    TodayUiState.Populated(
        now = now,
        newRecommendations = newRecommendations,
        remainingReadyCount = remainingReadyCount,
    )

private fun todayPreviewNewRecommendations(): List<TodayNewUiModel> =
    listOf(
        todayPreviewNew("new-one", "A Gentle Beginning", TodayRelativeTime(0, TodayRelativeTimeUnit.UNDER_HOUR)),
        todayPreviewNew("new-two", "A Longer Evening", TodayRelativeTime(3, TodayRelativeTimeUnit.HOURS)),
        todayPreviewNew("new-three", "A Distant Return", TodayRelativeTime(7, TodayRelativeTimeUnit.DAYS)),
    )

private fun todayPreviewNew(
    mediaId: String,
    title: String,
    releaseAge: TodayRelativeTime,
): TodayNewUiModel =
    TodayNewUiModel(
        mediaId = mediaId,
        mediaType = TodayMediaType.ANIME,
        title = title,
        posterReference = PREVIEW_HIGH_DETAIL_ARTWORK,
        progress = 8,
        progressUnit = TodayProgressUnit.EPISODE,
        total = 12,
        nextUnit = 9,
        availableUnitCount = 2,
        releaseAge = releaseAge,
        controls = TodayProgressControlsUiModel(true, true),
    )

private fun todayPreviewCaughtUp(): TodayUiState.CaughtUp =
    TodayUiState.CaughtUp(
        TodayCaughtUpUiModel(
            artwork =
                (1..6).map { index ->
                    TodayCaughtUpArtworkUiModel(
                        mediaId = "receipt-$index",
                        title = "Receipt title $index",
                        reference = if (index == 6) null else PREVIEW_HIGH_DETAIL_ARTWORK,
                    )
                },
            nextRelease =
                TodayNextReleaseUiModel(
                    mediaId = "future-preview",
                    mediaType = TodayMediaType.MANGA,
                    title = "The Next Chapter",
                    posterReference = PREVIEW_VERY_DARK_ARTWORK,
                    progressUnit = TodayProgressUnit.CHAPTER,
                    unitNumber = 42,
                    waitUntilRelease = TodayRelativeTime(2, TodayRelativeTimeUnit.DAYS),
                ),
        ),
    )

private const val PREVIEW_DATA_URI_PREFIX = "data:image/png;base64,"
private const val PREVIEW_VERY_LIGHT_ARTWORK =
    PREVIEW_DATA_URI_PREFIX +
        "iVBORw0KGgoAAAANSUhEUgAAAEAAAABACAIAAAAlC+aJAAAAS0lEQVR42u3PMQ0AAAwDoPo33UrY" +
        "vQQckD4XAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAYHLAMpT" +
        "0sIcNbcEAAAAAElFTkSuQmCC"
private const val PREVIEW_VERY_DARK_ARTWORK =
    PREVIEW_DATA_URI_PREFIX +
        "iVBORw0KGgoAAAANSUhEUgAAAEAAAABACAIAAAAlC+aJAAAAIklEQVR42u3BAQ0AAADCoPdPbQ8H" +
        "FAAAAAAAAAAAAAAA8G4wQAAB7OHIlwAAAABJRU5ErkJggg=="
private const val PREVIEW_HIGH_DETAIL_ARTWORK =
    PREVIEW_DATA_URI_PREFIX +
        "iVBORw0KGgoAAAANSUhEUgAAAEAAAABACAIAAAAlC+aJAAACOklEQVR42tXaMVLEMAwF0FyFihvQ" +
        "UNJScDqGgjvAvSihD7s7SyJbXwqSba2ccbUZyH8Zx5bjLOvl+Hl6qNr69Qjb3es3bM8fL7C933/C" +
        "tr4tsEnHyo76xFyA/cQ5KzJkBtAfl2tcZkgLqH5Z9sSlIScAeIpuQwwJAbg71c/udiQDiE8zGEBV" +
        "w20BYDCFabbTkeO6NCPtf4Ju6CLdUckQDNDTa4BTVmiIBBSTlQMADWEAmt4P4IYYQJW+CVAZAgA8" +
        "fSuAGkYDYPoOgENDF4CUXgPY7iivZu3juhRo+" +
        "1emiXKxdgnJ0AjQ0/cEwMq2EUBnqwiAUp07ANVcGwTgBh+AVwpxgMrgAMA6JxSg5D4ESFVaNODQY" +
        "ErvAbTX6/oaiEfZLjF2AWTu04LBlP6mgFNcZIDpI5agznGdGWD6xABmgOlzA0oDTJ8eQAxSlZYec" +
        "DEoNWY3gCGQcaKh92LgCm4QgF94JgCdrc5ZkSEvoJprr3GZISmAVwp74tKQEQDrnKLbEEM6gFSl1" +
        "c/udqQCKDUmGEBVgxnQvmGxX+PfgRwruA5v5iBAT99lg2MgYEszdIdmFICmnw9QpZ8MwNPPBIDpp" +
        "wFI6TsCxDc37fsD1i1ozx39O/q/mbN+AeDuEpKhCUBnq9EAusfeB1DNtQEAaHACeKUQA+AGDwDWO" +
        "WGAymAGSFVaJED/bkYDKDVmMODQYB4Zx71HUl5sKWugG9xR55s5wTAPgOyxTwtAhtkAzDAhoDTMC" +
        "VC/m5kE8Gf4BYcW+ynUIxRlAAAAAElFTkSuQmCC"
