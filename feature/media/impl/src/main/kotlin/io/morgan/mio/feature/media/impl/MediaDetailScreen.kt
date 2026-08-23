package io.morgan.mio.feature.media.impl

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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.ImageLoader
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import io.morgan.mio.core.designsystem.component.MioErrorState
import io.morgan.mio.core.designsystem.component.MioIconButton
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
    MediaDetailScreen(
        modifier = modifier,
        state = state,
        onBack = onBack,
        onRetry = { viewModel.onAction(MediaDetailAction.Retry) },
        onRelatedTitleClick = { relatedId -> onRelatedTitle(MediaId(relatedId)) },
    )
}

@Composable
internal fun MediaDetailScreen(
    state: MediaDetailUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onRelatedTitleClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val loadingDescription = stringResource(R.string.media_detail_loading)
    when (state) {
        MediaDetailUiState.Loading ->
            Box(
                modifier =
                    modifier
                        .fillMaxSize()
                        .semantics { liveRegion = LiveRegionMode.Polite },
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.semantics { contentDescription = loadingDescription },
                )
                MediaDetailBackButton(onBack = onBack, modifier = Modifier.align(Alignment.TopStart))
            }
        MediaDetailUiState.Error ->
            Box(modifier = modifier.fillMaxSize()) {
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
                modifier = modifier,
            )
    }
}

@Composable
private fun MediaDetailContent(
    state: MediaDetailUiState.Content,
    onBack: () -> Unit,
    onRelatedTitleClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (MioTheme.accessibility.reducedMotionEnabled) {
        ReducedMotionMediaDetailContent(
            state = state,
            onBack = onBack,
            onRelatedTitleClick = onRelatedTitleClick,
            modifier = modifier,
        )
    } else {
        SheetRiseMediaDetailContent(
            state = state,
            onBack = onBack,
            onRelatedTitleClick = onRelatedTitleClick,
            modifier = modifier,
        )
    }
}

@Composable
private fun SheetRiseMediaDetailContent(
    state: MediaDetailUiState.Content,
    onBack: () -> Unit,
    onRelatedTitleClick: (String) -> Unit,
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
            MediaDetailSections(state = state, onRelatedTitleClick = onRelatedTitleClick)
        }
    }
}

@Composable
private fun MediaDetailSections(
    state: MediaDetailUiState.Content,
    onRelatedTitleClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        MediaDetailCollectionContext(
            collection = state.collection,
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
    MediaDetailPreview(MioPreviewConfiguration.Light, MediaDetailUiState.Loading)
}

@Preview(name = "Media detail failure", widthDp = 393, heightDp = 852)
@Composable
private fun MediaDetailFailurePreview() {
    MediaDetailPreview(MioPreviewConfiguration.Light, MediaDetailUiState.Error)
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
        collection = null,
    )
