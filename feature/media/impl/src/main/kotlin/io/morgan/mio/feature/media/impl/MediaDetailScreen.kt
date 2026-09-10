package io.morgan.mio.feature.media.impl

import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import coil3.ImageLoader
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import io.morgan.mio.core.designsystem.accessibility.MioHapticType
import io.morgan.mio.core.designsystem.accessibility.rememberMioHaptics
import io.morgan.mio.core.designsystem.component.MioErrorState
import io.morgan.mio.core.designsystem.component.MioIconButton
import io.morgan.mio.core.designsystem.component.MioSnackbarHost
import io.morgan.mio.core.designsystem.component.MioSnackbarMessage
import io.morgan.mio.core.designsystem.icon.MioIcons
import io.morgan.mio.core.designsystem.preview.MioPreviewConfiguration
import io.morgan.mio.core.designsystem.preview.MioPreviewSurface
import io.morgan.mio.core.designsystem.theme.MioTheme
import io.morgan.mio.core.ui.image.MioImageLoaderProvider
import io.morgan.mio.domain.model.MediaId
import io.morgan.mio.feature.media.impl.component.MediaDetailBackdrop
import io.morgan.mio.feature.media.impl.component.MediaDetailCollectionContext
import io.morgan.mio.feature.media.impl.component.MediaDetailHeader
import io.morgan.mio.feature.media.impl.component.MediaDetailRelatedTitles
import io.morgan.mio.feature.media.impl.component.MediaDetailSheetHeader
import io.morgan.mio.feature.media.impl.component.MediaDetailSynopsis
import io.morgan.mio.feature.media.impl.component.sheetRiseAt

@Composable
internal fun MediaDetailRoute(
    mediaId: MediaId,
    onBack: () -> Unit,
    onRelatedTitle: (MediaId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel =
        assistedMetroViewModel<MediaDetailViewModel, MediaDetailViewModel.Factory>(
            key = mediaId.value,
        ) { create(mediaId) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
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
            viewModel.onAction(MediaDetailAction.VisibilityChanged(visible = true))
            try {
                viewModel.routineHaptics.collect { haptics.perform(MioHapticType.ROUTINE_TICK) }
            } finally {
                viewModel.onAction(MediaDetailAction.VisibilityChanged(visible = false))
            }
        }
    }
    MediaDetailScreen(
        modifier = modifier,
        state = state,
        onBack = onBack,
        onRetry = { viewModel.onAction(MediaDetailAction.Retry) },
        onRelatedTitleClick = { relatedId -> onRelatedTitle(MediaId(relatedId)) },
        onAction = viewModel::onAction,
        timeoutMillis = timeoutMillis,
    )
}

@Composable
internal fun MediaDetailScreen(
    state: MediaDetailUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onRelatedTitleClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    onAction: (MediaDetailAction) -> Unit = {},
    timeoutMillis: Long = DEFAULT_UNDO_TIMEOUT_MILLIS.toLong(),
) {
    val loadingDescription = stringResource(R.string.media_detail_loading)
    Box(modifier = modifier.fillMaxSize()) {
        when (state) {
            is MediaDetailUiState.Loading ->
                Box(
                    modifier = Modifier.fillMaxSize().semantics { liveRegion = LiveRegionMode.Polite },
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.semantics { contentDescription = loadingDescription },
                    )
                    MediaDetailBackButton(onBack = onBack, modifier = Modifier.align(Alignment.TopStart))
                }
            is MediaDetailUiState.Error ->
                Box(modifier = Modifier.fillMaxSize()) {
                    MioErrorState(
                        title = stringResource(R.string.media_detail_error_title),
                        message = stringResource(R.string.media_detail_error_message),
                        retryLabel = stringResource(R.string.media_detail_retry),
                        onRetry = onRetry,
                        modifier = Modifier.fillMaxSize(),
                    )
                    MediaDetailBackButton(onBack = onBack, modifier = Modifier.align(Alignment.TopStart))
                }
            is MediaDetailUiState.Content ->
                MediaDetailContent(
                    state = state,
                    onBack = onBack,
                    onRelatedTitleClick = onRelatedTitleClick,
                    onAction = onAction,
                    timeoutMillis = timeoutMillis,
                    modifier = Modifier.fillMaxSize(),
                )
        }
        MediaDetailSnackbar(state = state, onAction = onAction)
    }
}

@Composable
private fun MediaDetailContent(
    state: MediaDetailUiState.Content,
    onBack: () -> Unit,
    onRelatedTitleClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    onAction: (MediaDetailAction) -> Unit = {},
    timeoutMillis: Long = DEFAULT_UNDO_TIMEOUT_MILLIS.toLong(),
) {
    if (MioTheme.accessibility.reducedMotionEnabled) {
        ReducedMotionMediaDetailContent(
            state = state,
            onBack = onBack,
            onRelatedTitleClick = onRelatedTitleClick,
            onAction = onAction,
            timeoutMillis = timeoutMillis,
            modifier = modifier,
        )
    } else {
        SheetRiseMediaDetailContent(
            state = state,
            onBack = onBack,
            onRelatedTitleClick = onRelatedTitleClick,
            onAction = onAction,
            timeoutMillis = timeoutMillis,
            modifier = modifier,
        )
    }
}

@Composable
private fun SheetRiseMediaDetailContent(
    state: MediaDetailUiState.Content,
    onBack: () -> Unit,
    onRelatedTitleClick: (String) -> Unit,
    onAction: (MediaDetailAction) -> Unit,
    timeoutMillis: Long,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val scrollOffset by
        remember(listState, density) {
            derivedStateOf {
                with(density) {
                    if (listState.firstVisibleItemIndex == 0) {
                        listState.firstVisibleItemScrollOffset.toDp()
                    } else {
                        232.dp + listState.firstVisibleItemScrollOffset.toDp()
                    }
                }
            }
        }
    val rise = remember(scrollOffset) { sheetRiseAt(scrollOffset) }
    Box(modifier = modifier.fillMaxSize()) {
        MediaDetailBackdrop(
            media = state.media,
            rise = rise,
            onBack = null,
            modifier = Modifier.testTag("media-detail-anchored-hero"),
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
        ) {
            item(key = "media-detail-hero-space") {
                Spacer(Modifier.fillMaxWidth().height(232.dp))
            }
            item(key = "media-detail-sheet") {
                Column(modifier = Modifier.fillMaxWidth().testTag("media-detail-sheet")) {
                    MediaDetailSheetHeader(media = state.media, rise = rise)
                    MediaDetailSections(
                        state = state,
                        onRelatedTitleClick = onRelatedTitleClick,
                        onAction = onAction,
                        timeoutMillis = timeoutMillis,
                        modifier = Modifier.background(MioTheme.colors.surface),
                    )
                }
            }
        }
        MediaDetailBackButton(onBack = onBack, modifier = Modifier.align(Alignment.TopStart))
    }
}

@Composable
private fun ReducedMotionMediaDetailContent(
    state: MediaDetailUiState.Content,
    onBack: () -> Unit,
    onRelatedTitleClick: (String) -> Unit,
    onAction: (MediaDetailAction) -> Unit,
    timeoutMillis: Long,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val scrollOffset by
        remember(listState, density) {
            derivedStateOf {
                with(density) {
                    if (listState.firstVisibleItemIndex == 0) {
                        listState.firstVisibleItemScrollOffset.toDp()
                    } else {
                        120.dp
                    }
                }
            }
        }
    val rise = remember(scrollOffset) { sheetRiseAt(scrollOffset, reducedMotion = true) }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item(key = "media-detail-header") {
            MediaDetailHeader(media = state.media, rise = rise, onBack = onBack)
        }
        item(key = "media-detail-sections") {
            MediaDetailSections(
                state = state,
                onRelatedTitleClick = onRelatedTitleClick,
                onAction = onAction,
                timeoutMillis = timeoutMillis,
            )
        }
    }
}

@Composable
private fun MediaDetailSections(
    state: MediaDetailUiState.Content,
    onRelatedTitleClick: (String) -> Unit,
    onAction: (MediaDetailAction) -> Unit,
    timeoutMillis: Long,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        MediaDetailCollectionContext(
            collection = state.collection,
            onDecrease = {
                onAction(
                    MediaDetailAction.Step(
                        mediaId = state.media.id,
                        direction = MediaDetailProgressDirection.DECREASE,
                        timeoutMillis = timeoutMillis,
                    ),
                )
            },
            onIncrease = {
                onAction(
                    MediaDetailAction.Step(
                        mediaId = state.media.id,
                        direction = MediaDetailProgressDirection.INCREASE,
                        timeoutMillis = timeoutMillis,
                    ),
                )
            },
            onInteractionChanged = { pressed ->
                onAction(MediaDetailAction.InteractionChanged(state.media.id, pressed))
            },
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        MediaDetailSynopsis(
            synopsis = state.media.synopsis,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        MediaDetailTotals(
            totals = state.media.totals,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        MediaDetailRelatedTitles(
            titles = state.relatedTitles,
            onTitleClick = onRelatedTitleClick,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun MediaDetailBackButton(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MioIconButton(
        iconRes = MioIcons.Back,
        contentDescription = stringResource(R.string.media_detail_back),
        onClick = onBack,
        modifier = modifier.padding(8.dp),
    )
}

@Composable
private fun MediaDetailTotals(
    totals: MediaDetailTotalsUiModel,
    modifier: Modifier = Modifier,
) {
    val labels =
        listOfNotNull(
            totals.episodes?.let { value -> pluralStringResource(R.plurals.media_detail_episodes, value, value) },
            totals.chapters?.let { value -> pluralStringResource(R.plurals.media_detail_chapters, value, value) },
            totals.volumes?.let { value -> pluralStringResource(R.plurals.media_detail_volumes, value, value) },
        )
    if (labels.isEmpty()) return
    androidx.compose.foundation.layout.Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.media_detail_totals),
            style = MioTheme.typography.section,
            modifier = Modifier.semantics { heading() },
        )
        Text(text = labels.joinToString(" · "), style = MioTheme.typography.body)
    }
}

@Composable
private fun MediaDetailSnackbar(
    state: MediaDetailUiState,
    onAction: (MediaDetailAction) -> Unit,
) {
    val error = state.progressErrorOrNull()
    val undo = state.latestUndoOrNull()
    val message =
        when {
            error != null ->
                MioSnackbarMessage(
                    id = PROGRESS_ERROR_SNACKBAR_ID,
                    message = stringResource(R.string.media_detail_progress_error),
                    actionLabel = stringResource(R.string.media_detail_retry_progress).takeIf { error.retryable },
                )
            undo != null ->
                MioSnackbarMessage(
                    id = undo.groupId,
                    message =
                        stringResource(
                            when (undo.unit) {
                                MediaDetailProgressUnit.EPISODE -> R.string.media_detail_saved_episode
                                MediaDetailProgressUnit.CHAPTER -> R.string.media_detail_saved_chapter
                                MediaDetailProgressUnit.VOLUME -> R.string.media_detail_saved_volume
                            },
                            undo.progress,
                        ),
                    actionLabel = stringResource(R.string.media_detail_undo),
                )
            else -> null
        }
    MioSnackbarHost(
        snackbar = message,
        bottomNavigationVisible = false,
        onAction = { id ->
            if (id == PROGRESS_ERROR_SNACKBAR_ID) {
                onAction(MediaDetailAction.RetryProgress)
            } else {
                onAction(MediaDetailAction.Undo(id))
            }
        },
    )
}

private fun MediaDetailUiState.progressErrorOrNull(): MediaDetailProgressErrorUiModel? =
    when (this) {
        is MediaDetailUiState.Loading -> progressError
        is MediaDetailUiState.Error -> progressError
        is MediaDetailUiState.Content -> progressError
    }

private fun MediaDetailUiState.latestUndoOrNull(): MediaDetailUndoUiModel? =
    when (this) {
        is MediaDetailUiState.Loading -> latestUndo
        is MediaDetailUiState.Error -> latestUndo
        is MediaDetailUiState.Content -> latestUndo
    }

private const val DEFAULT_UNDO_TIMEOUT_MILLIS: Int = 6_000
private const val PROGRESS_ERROR_SNACKBAR_ID: String = "progress-error"

private val PreviewImageLoader: @Composable () -> ImageLoader = {
    val context = LocalContext.current
    remember(context) { ImageLoader.Builder(context).build() }
}

@Preview(name = "Media detail light", widthDp = 393, heightDp = 852)
@Composable
private fun MediaDetailLightPreview() {
    MediaDetailPreview(MioPreviewConfiguration.Light, mediaDetailContentState())
}

@Preview(name = "Media detail dark", widthDp = 393, heightDp = 852)
@Composable
private fun MediaDetailDarkPreview() {
    MediaDetailPreview(MioPreviewConfiguration.Dark, mediaDetailContentState())
}

@Preview(name = "Media detail absent optional", widthDp = 393, heightDp = 852)
@Composable
private fun MediaDetailAbsentOptionalPreview() {
    MediaDetailPreview(MioPreviewConfiguration.Light, mediaDetailContentState(withOptionalData = false))
}

@Preview(name = "Media detail loading", widthDp = 393, heightDp = 852)
@Composable
private fun MediaDetailLoadingPreview() {
    MediaDetailPreview(MioPreviewConfiguration.Light, MediaDetailUiState.Loading())
}

@Preview(name = "Media detail failure", widthDp = 393, heightDp = 852)
@Composable
private fun MediaDetailFailurePreview() {
    MediaDetailPreview(MioPreviewConfiguration.Light, MediaDetailUiState.Error())
}

@Preview(name = "Media detail large text", widthDp = 320, heightDp = 852, fontScale = 2f)
@Composable
private fun MediaDetailLargeTextPreview() {
    MediaDetailPreview(MioPreviewConfiguration.Light, mediaDetailContentState())
}

@Preview(name = "Media detail reduced motion", widthDp = 393, heightDp = 852)
@Composable
private fun MediaDetailReducedMotionPreview() {
    MediaDetailPreview(MioPreviewConfiguration.ReducedMotionLight, mediaDetailContentState())
}

@Preview(name = "Media progress COMPLETED", widthDp = 393, heightDp = 852)
@Composable
private fun MediaProgressCompletedPreview() {
    val state = mediaDetailContentState()
    MediaDetailPreview(
        MioPreviewConfiguration.Light,
        state.copy(
            collection =
                requireNotNull(
                    state.collection,
                ).copy(status = MediaDetailCollectionStatus.COMPLETED, progress = 12, controls = null),
            latestUndo =
                MediaDetailUndoUiModel(
                    "preview-action",
                    "preview-media",
                    12,
                    MediaDetailProgressUnit.EPISODE,
                    true,
                ),
        ),
    )
}

@Preview(name = "Media progress PLANNED", widthDp = 393, heightDp = 852)
@Composable
private fun MediaProgressPlannedPreview() {
    val state = mediaDetailContentState()
    MediaDetailPreview(
        MioPreviewConfiguration.Light,
        state.copy(
            collection =
                requireNotNull(
                    state.collection,
                ).copy(status = MediaDetailCollectionStatus.PLANNED, progress = 12, controls = null),
        ),
    )
}

@Preview(name = "Media progress PAUSED", widthDp = 393, heightDp = 852)
@Composable
private fun MediaProgressPausedPreview() {
    val state = mediaDetailContentState()
    MediaDetailPreview(
        MioPreviewConfiguration.Light,
        state.copy(
            collection =
                requireNotNull(
                    state.collection,
                ).copy(status = MediaDetailCollectionStatus.PAUSED, progress = 12, controls = null),
        ),
    )
}

@Preview(name = "Media progress DROPPED", widthDp = 393, heightDp = 852)
@Composable
private fun MediaProgressDroppedPreview() {
    val state = mediaDetailContentState()
    MediaDetailPreview(
        MioPreviewConfiguration.Light,
        state.copy(
            collection =
                requireNotNull(
                    state.collection,
                ).copy(status = MediaDetailCollectionStatus.DROPPED, progress = 12, controls = null),
        ),
    )
}

@Preview(name = "Media progress REPEATING", widthDp = 393, heightDp = 852)
@Composable
private fun MediaProgressRepeatingPreview() {
    val state = mediaDetailContentState()
    MediaDetailPreview(
        MioPreviewConfiguration.Light,
        state.copy(
            collection =
                requireNotNull(
                    state.collection,
                ).copy(status = MediaDetailCollectionStatus.REPEATING, progress = 12, controls = null),
        ),
    )
}

@Preview(name = "Media progress Zero", widthDp = 393, heightDp = 852)
@Composable
private fun MediaProgressZeroPreview() {
    val state = mediaDetailContentState()
    MediaDetailPreview(
        MioPreviewConfiguration.Light,
        state.copy(
            collection =
                requireNotNull(
                    state.collection,
                ).copy(progress = 0, total = null, controls = MediaDetailProgressControlsUiModel(false, true)),
        ),
    )
}

@Preview(name = "Media progress Chapter", widthDp = 393, heightDp = 852)
@Composable
private fun MediaProgressChapterPreview() {
    val state = mediaDetailContentState()
    MediaDetailPreview(
        MioPreviewConfiguration.Light,
        state.copy(collection = requireNotNull(state.collection).copy(progressUnit = MediaDetailProgressUnit.CHAPTER)),
    )
}

@Preview(name = "Media progress Volume", widthDp = 393, heightDp = 852)
@Composable
private fun MediaProgressVolumePreview() {
    val state = mediaDetailContentState()
    MediaDetailPreview(
        MioPreviewConfiguration.Light,
        state.copy(collection = requireNotNull(state.collection).copy(progressUnit = MediaDetailProgressUnit.VOLUME)),
    )
}

@Preview(name = "Media progress save error", widthDp = 393, heightDp = 852)
@Composable
private fun MediaProgressSaveErrorPreview() {
    MediaDetailPreview(
        MioPreviewConfiguration.Light,
        mediaDetailContentState().copy(progressError = MediaDetailProgressErrorUiModel(true)),
    )
}

@Preview(name = "Media Undo large text", widthDp = 320, heightDp = 852, fontScale = 2f)
@Composable
private fun MediaProgressUndoLargePreview() {
    MediaDetailPreview(
        MioPreviewConfiguration.Light,
        mediaDetailContentState().copy(
            latestUndo =
                MediaDetailUndoUiModel(
                    "preview-action",
                    "preview-media",
                    8,
                    MediaDetailProgressUnit.EPISODE,
                    false,
                ),
        ),
    )
}

@Composable
private fun MediaDetailPreview(
    configuration: MioPreviewConfiguration,
    state: MediaDetailUiState,
) {
    MioPreviewSurface(configuration) {
        MioImageLoaderProvider(PreviewImageLoader()) {
            MediaDetailScreen(
                state = state,
                onBack = {},
                onRetry = {},
                onRelatedTitleClick = {},
            )
        }
    }
}

private fun mediaDetailContentState(withOptionalData: Boolean = true): MediaDetailUiState.Content =
    MediaDetailUiState.Content(
        media =
            MediaDetailUiModel(
                id = "preview-media",
                title = "A Story With Room to Breathe",
                type = MediaDetailMediaType.ANIME,
                format = MediaDetailFormat.TV,
                publicationStatus = MediaDetailPublicationStatus.RELEASING,
                synopsis =
                    if (withOptionalData) {
                        "A measured synopsis with safe formatting and enough detail to demonstrate the local expansion affordance."
                    } else {
                        null
                    },
                posterReference = if (withOptionalData) "preview://poster" else null,
                bannerReference = if (withOptionalData) "preview://broken-banner" else null,
                totals =
                    MediaDetailTotalsUiModel(
                        episodes = if (withOptionalData) 12 else null,
                        chapters = null,
                        volumes = null,
                    ),
                genres = if (withOptionalData) listOf("Adventure", "Drama") else emptyList(),
                releaseYear = if (withOptionalData) 2026 else null,
                scorePercent = if (withOptionalData) 87 else null,
            ),
        relatedTitles =
            if (withOptionalData) {
                listOf(
                    MediaDetailRelatedTitleUiModel(
                        "related-1",
                        "A Neighboring Story",
                        MediaDetailMediaType.ANIME,
                        "preview://related-1",
                    ),
                    MediaDetailRelatedTitleUiModel("related-2", "Another Thread", MediaDetailMediaType.MANGA, null),
                )
            } else {
                emptyList()
            },
        collection =
            MediaDetailCollectionUiModel(
                status = MediaDetailCollectionStatus.CURRENT,
                progress = 8,
                progressUnit = MediaDetailProgressUnit.EPISODE,
                total = 12,
                controls = MediaDetailProgressControlsUiModel(true, true),
            ),
    )
