package io.morgan.mio.feature.today.impl

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.ImageLoader
import dev.zacsweers.metrox.viewmodel.metroViewModel
import io.morgan.mio.core.designsystem.accessibility.mioHeading
import io.morgan.mio.core.designsystem.component.MioSkeleton
import io.morgan.mio.core.designsystem.preview.MioPreviewConfiguration
import io.morgan.mio.core.designsystem.preview.MioPreviewSurface
import io.morgan.mio.core.designsystem.theme.MioTheme
import io.morgan.mio.core.ui.image.MioImageLoaderProvider
import io.morgan.mio.domain.model.MediaId
import io.morgan.mio.feature.today.impl.component.TodayCaughtUpReceipt
import io.morgan.mio.feature.today.impl.component.TodayNewRow
import io.morgan.mio.feature.today.impl.component.TodayNextReleaseRow
import io.morgan.mio.feature.today.impl.component.TodayNowCard
import io.morgan.mio.feature.today.impl.component.TodayRemainingReadySummary

@Composable
internal fun TodayRoute(
    modifier: Modifier = Modifier,
    onMediaDetail: (MediaId) -> Unit,
) {
    val viewModel = metroViewModel<TodayViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TodayScreen(
        modifier = modifier,
        state = state,
        onMediaDetail = { mediaId -> onMediaDetail(MediaId(mediaId)) },
    )
}

@Composable
internal fun TodayScreen(
    modifier: Modifier = Modifier,
    state: TodayUiState,
    onMediaDetail: (String) -> Unit,
) {
    val scrollState = rememberScrollState()
    Column(
        modifier =
            modifier
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
        when (state) {
            TodayUiState.Loading -> {
                TodayNowSectionHeading()
                TodayLoadingCard(modifier = Modifier.padding(top = 12.dp))
            }
            is TodayUiState.Populated -> {
                TodayNowSectionHeading()
                TodayNowCard(
                    now = state.now,
                    onClick = { onMediaDetail(state.now.mediaId) },
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
                            modifier = Modifier.padding(top = if (index == 0) 12.dp else 0.dp),
                        )
                    }
                }
                if (state.remainingReadyCount > 0) {
                    TodayRemainingReadySummary(
                        count = state.remainingReadyCount,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            }
            is TodayUiState.CaughtUp -> {
                TodayCaughtUpReceipt(
                    receipt = state.receipt,
                    modifier = Modifier.padding(top = 24.dp),
                )
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
}

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
    TodayPreview(MioPreviewConfiguration.Light, TodayUiState.Loading)
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
