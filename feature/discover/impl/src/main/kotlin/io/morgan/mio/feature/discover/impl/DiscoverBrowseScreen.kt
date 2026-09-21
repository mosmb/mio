package io.morgan.mio.feature.discover.impl

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zacsweers.metrox.viewmodel.metroViewModel
import io.morgan.mio.core.designsystem.component.MioFilterPill
import io.morgan.mio.core.designsystem.component.MioSkeleton
import io.morgan.mio.core.designsystem.preview.MioPreviewConfiguration
import io.morgan.mio.core.designsystem.preview.MioPreviewSurface
import io.morgan.mio.core.designsystem.theme.MioTheme
import io.morgan.mio.core.ui.title.rememberTitleResolver
import io.morgan.mio.domain.media.MediaBrowseCategory
import io.morgan.mio.domain.model.MediaId
import io.morgan.mio.domain.model.MediaType
import io.morgan.mio.feature.discover.api.DiscoverCategoryKey

@Composable
internal fun DiscoverBrowseHome(
    state: DiscoverBrowseUiState,
    onRetry: (MediaBrowseCategory) -> Unit,
    onCategory: (MediaBrowseCategory) -> Unit,
    onMediaDetail: (MediaId) -> Unit,
) {
    val resolve = rememberTitleResolver()
    val genres = state.categories.filter { it.ordinal >= MediaBrowseCategory.ACTION.ordinal }
    val directory = state.categories.drop(2).filterNot { it in genres }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 128.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        items(state.categories.take(2), key = { it.name }) { category ->
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = MioTheme.spacing.screenGutter),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(browseCategoryLabel(category, state.type), style = MioTheme.typography.section)
                        BrowseSubtitle(category, state)
                    }
                    TextButton(onClick = {
                        onCategory(category)
                    }, colors = ButtonDefaults.textButtonColors(contentColor = MioTheme.colors.text)) {
                        Text(stringResource(R.string.discover_see_all))
                        Icon(painterResource(R.drawable.discover_chevron_right), null, Modifier.size(20.dp))
                    }
                }
                val results = state.items[category]
                if (category in state.failed) {
                    FailureBanner(browseFailureMessage(results), { onRetry(category) })
                }
                when {
                    results == null && category !in state.failed ->
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = MioTheme.spacing.screenGutter),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(3) { MioSkeleton(Modifier.width(128.dp).height(192.dp)) }
                        }
                    results.isNullOrEmpty() ->
                        Text(
                            stringResource(
                                if (category in
                                    state.failed
                                ) {
                                    R.string.discover_browse_unavailable
                                } else {
                                    R.string.discover_browse_empty
                                },
                            ),
                            style = MioTheme.typography.body,
                            color = MioTheme.colors.textMuted,
                            modifier = Modifier.padding(horizontal = MioTheme.spacing.screenGutter),
                        )
                    else ->
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = MioTheme.spacing.screenGutter),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(results.take(8), key = { it.mediaId }) { result ->
                                Box(Modifier.width(128.dp)) {
                                    DiscoverResultTile(result.copy(title = resolve(result.title, result.titleNames)), {
                                        onMediaDetail(MediaId(result.mediaId))
                                    })
                                }
                            }
                        }
                }
            }
        }
        if (directory.isNotEmpty()) {
            item {
                Column(
                    Modifier.padding(horizontal = MioTheme.spacing.screenGutter),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stringResource(R.string.discover_explore_more), style = MioTheme.typography.section)
                    Surface(
                        shape = MioTheme.shapes.lg,
                        color = MioTheme.colors.surfaceSoft,
                        border = BorderStroke(1.dp, MioTheme.colors.border),
                    ) {
                        Column {
                            directory.forEachIndexed { index, category ->
                                if (index >
                                    0
                                ) {
                                    HorizontalDivider(
                                        Modifier.padding(horizontal = 16.dp),
                                        color = MioTheme.colors.border,
                                    )
                                }
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable(role = Role.Button) {
                                            onCategory(category)
                                        }.heightIn(
                                            min = 64.dp,
                                        ).padding(
                                            horizontal = 16.dp,
                                            vertical = 12.dp,
                                        ),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        painterResource(browseCategoryIcon(category)),
                                        contentDescription = null,
                                        tint = MioTheme.colors.textMuted,
                                        modifier = Modifier.padding(end = 16.dp).size(24.dp),
                                    )
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            browseCategoryLabel(category, state.type),
                                            style = MioTheme.typography.bodyStrong,
                                        )
                                        BrowseSubtitle(category, state)
                                    }
                                    Icon(
                                        painterResource(R.drawable.discover_chevron_right),
                                        contentDescription = null,
                                        tint = MioTheme.colors.textMuted,
                                        modifier = Modifier.padding(start = 12.dp).size(20.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        if (genres.isNotEmpty()) {
            item {
                Column(
                    Modifier.padding(horizontal = MioTheme.spacing.screenGutter),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stringResource(R.string.discover_genres), style = MioTheme.typography.section)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        genres.forEach { category ->
                            MioFilterPill(browseCategoryLabel(category, state.type), false, { onCategory(category) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun DiscoverCategoryRoute(
    key: DiscoverCategoryKey,
    onBack: () -> Unit,
    onMediaDetail: (MediaId) -> Unit,
) {
    val viewModel = metroViewModel<DiscoverBrowseViewModel>()
    val type = MediaType.valueOf(key.mediaType)
    val category = MediaBrowseCategory.valueOf(key.category)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(key) { viewModel.open(type, category) }
    DiscoverCategoryScreen(state, category, onBack, { viewModel.retry(category) }, onMediaDetail)
}

@Composable
internal fun DiscoverCategoryScreen(
    state: DiscoverBrowseUiState,
    category: MediaBrowseCategory,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onMediaDetail: (MediaId) -> Unit,
) {
    val resolve = rememberTitleResolver()
    val items = state.items[category]
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = MioTheme.spacing.screenGutter, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            io.morgan.mio.core.designsystem.component.MioIconButton(
                io.morgan.mio.core.designsystem.R.drawable.mio_icon_back,
                stringResource(R.string.discover_back),
                onBack,
            )
            Column(Modifier.weight(1f)) {
                Text(browseCategoryLabel(category, state.type), style = MioTheme.typography.title)
                val typeLabel =
                    stringResource(
                        if (state.type ==
                            MediaType.ANIME
                        ) {
                            R.string.discover_filter_anime
                        } else {
                            R.string.discover_filter_manga
                        },
                    )
                Text(
                    if (category == MediaBrowseCategory.THIS_SEASON || category == MediaBrowseCategory.NEXT_SEASON) {
                        stringResource(
                            R.string.discover_category_context,
                            typeLabel,
                            browseSeasonLabel(
                                if (category == MediaBrowseCategory.NEXT_SEASON) state.season.next() else state.season,
                            ),
                        )
                    } else {
                        typeLabel
                    },
                    style = MioTheme.typography.caption,
                    color = MioTheme.colors.textMuted,
                )
            }
        }
        if (category in state.failed) FailureBanner(browseFailureMessage(items), onRetry)
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.weight(1f),
            contentPadding =
                PaddingValues(
                    start = MioTheme.spacing.screenGutter,
                    end = MioTheme.spacing.screenGutter,
                    top = 12.dp,
                    bottom = 32.dp,
                ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (items == null &&
                category !in state.failed
            ) {
                items(4) { MioSkeleton(Modifier.fillMaxWidth().aspectRatio(2f / 3f)) }
            }
            items(items.orEmpty(), key = { it.mediaId }) { result ->
                DiscoverResultTile(result.copy(title = resolve(result.title, result.titleNames)), {
                    onMediaDetail(MediaId(result.mediaId))
                })
            }
            if (items != null || category in state.failed) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        stringResource(
                            when {
                                items == null -> R.string.discover_browse_unavailable
                                items.isEmpty() -> R.string.discover_browse_empty
                                else -> R.string.discover_browse_end
                            },
                        ),
                        style = MioTheme.typography.caption,
                        color = MioTheme.colors.textMuted,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun browseFailureMessage(items: List<DiscoverResultUiModel>?): String =
    stringResource(
        if (items.isNullOrEmpty()) R.string.discover_browse_load_failure else R.string.discover_browse_failure,
    )

@Composable
private fun BrowseSubtitle(
    category: MediaBrowseCategory,
    state: DiscoverBrowseUiState,
) {
    val subtitle =
        when (category) {
            MediaBrowseCategory.THIS_SEASON -> browseSeasonLabel(state.season)
            MediaBrowseCategory.NEXT_SEASON -> {
                val next = state.season.next()
                stringResource(
                    listOf(
                        R.string.discover_season_start_january,
                        R.string.discover_season_start_april,
                        R.string.discover_season_start_july,
                        R.string.discover_season_start_october,
                    )[next.quarter],
                    browseSeasonLabel(next),
                )
            }
            else ->
                stringResource(
                    when (category) {
                        MediaBrowseCategory.TRENDING -> R.string.discover_subtitle_trending
                        MediaBrowseCategory.POPULAR -> R.string.discover_subtitle_popular
                        MediaBrowseCategory.AIRING -> R.string.discover_subtitle_airing
                        MediaBrowseCategory.PUBLISHING -> R.string.discover_subtitle_publishing
                        MediaBrowseCategory.NEWLY_STARTED -> R.string.discover_subtitle_newly_started
                        MediaBrowseCategory.TOP_RATED -> R.string.discover_subtitle_top_rated
                        MediaBrowseCategory.MOVIES -> R.string.discover_subtitle_movies
                        MediaBrowseCategory.COMPLETED ->
                            if (state.type ==
                                MediaType.ANIME
                            ) {
                                R.string.discover_subtitle_finished
                            } else {
                                R.string.discover_subtitle_completed
                            }
                        MediaBrowseCategory.ONE_SHOTS -> R.string.discover_subtitle_one_shots
                        else -> error("Category has no directory subtitle: $category")
                    },
                )
        }
    Text(subtitle, style = MioTheme.typography.caption, color = MioTheme.colors.textMuted)
}

@Composable
private fun browseSeasonLabel(season: DiscoverBrowseSeason): String =
    stringResource(
        listOf(
            R.string.discover_season_winter,
            R.string.discover_season_spring,
            R.string.discover_season_summer,
            R.string.discover_season_fall,
        )[season.quarter],
        season.year,
    )

private fun browseCategoryIcon(category: MediaBrowseCategory): Int =
    when (category) {
        MediaBrowseCategory.THIS_SEASON, MediaBrowseCategory.NEXT_SEASON -> R.drawable.discover_calendar_month
        MediaBrowseCategory.AIRING, MediaBrowseCategory.PUBLISHING -> R.drawable.discover_sensors
        MediaBrowseCategory.TOP_RATED -> R.drawable.discover_star
        MediaBrowseCategory.MOVIES -> R.drawable.discover_movie
        MediaBrowseCategory.COMPLETED -> R.drawable.discover_check_circle
        MediaBrowseCategory.ONE_SHOTS -> R.drawable.discover_book
        else -> R.drawable.discover_auto_awesome
    }

@Composable
internal fun browseCategoryLabel(
    category: MediaBrowseCategory,
    type: MediaType,
): String =
    stringResource(
        when (category) {
            MediaBrowseCategory.TRENDING -> R.string.discover_category_trending
            MediaBrowseCategory.POPULAR -> R.string.discover_category_popular
            MediaBrowseCategory.NEXT_SEASON -> R.string.discover_category_next_season
            MediaBrowseCategory.THIS_SEASON -> R.string.discover_category_this_season
            MediaBrowseCategory.AIRING -> R.string.discover_category_airing
            MediaBrowseCategory.PUBLISHING -> R.string.discover_category_publishing
            MediaBrowseCategory.NEWLY_STARTED -> R.string.discover_category_newly_started
            MediaBrowseCategory.TOP_RATED -> R.string.discover_category_top_rated
            MediaBrowseCategory.MOVIES -> R.string.discover_category_movies
            MediaBrowseCategory.COMPLETED ->
                if (type ==
                    MediaType.ANIME
                ) {
                    R.string.discover_category_finished
                } else {
                    R.string.discover_category_completed
                }
            MediaBrowseCategory.ONE_SHOTS -> R.string.discover_category_one_shots
            MediaBrowseCategory.ACTION -> R.string.discover_category_action
            MediaBrowseCategory.FANTASY -> R.string.discover_category_fantasy
            MediaBrowseCategory.ROMANCE -> R.string.discover_category_romance
            MediaBrowseCategory.MYSTERY -> R.string.discover_category_mystery
            MediaBrowseCategory.SLICE_OF_LIFE -> R.string.discover_category_slice_of_life
            MediaBrowseCategory.SCI_FI -> R.string.discover_category_sci_fi
        },
    )

@Preview(name = "Discover browse light", widthDp = 393, heightDp = 852)
@Composable
private fun BrowseLightPreview() {
    MioPreviewSurface(MioPreviewConfiguration.Light) { DiscoverBrowseHome(previewBrowseState(), {}, {}, {}) }
}

@Preview(name = "Discover category dark", widthDp = 393, heightDp = 852)
@Composable
private fun CategoryDarkPreview() {
    MioPreviewSurface(MioPreviewConfiguration.Dark) {
        DiscoverCategoryScreen(previewBrowseState(), MediaBrowseCategory.TRENDING, {}, {}, {})
    }
}

private fun previewBrowseState() =
    DiscoverBrowseUiState(
        categories =
            listOf(
                MediaBrowseCategory.TRENDING,
                MediaBrowseCategory.NEXT_SEASON,
                MediaBrowseCategory.TOP_RATED,
            ),
        items =
            mapOf(
                MediaBrowseCategory.TRENDING to
                    listOf(
                        DiscoverResultUiModel(
                            "preview",
                            "A new adventure",
                            null,
                            DiscoverMediaType.ANIME,
                            DiscoverTrackedStatus.PLANNED,
                        ),
                    ),
            ),
    )
