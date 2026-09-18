package io.morgan.mio.feature.discover.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zacsweers.metrox.viewmodel.metroViewModel
import io.morgan.mio.core.designsystem.component.MioButton
import io.morgan.mio.core.designsystem.component.MioFilterPill
import io.morgan.mio.core.designsystem.component.MioSkeleton
import io.morgan.mio.core.designsystem.preview.MioPreviewConfiguration
import io.morgan.mio.core.designsystem.preview.MioPreviewSurface
import io.morgan.mio.core.designsystem.theme.MioTheme
import io.morgan.mio.core.ui.image.MioPoster
import io.morgan.mio.domain.model.MediaId

@Composable
internal fun DiscoverRoute(
    onMediaDetail: (MediaId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = metroViewModel<DiscoverViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DiscoverScreen(
        state = state,
        onAction = viewModel::onAction,
        onMediaDetail = onMediaDetail,
        modifier = modifier,
    )
}

@Composable
internal fun DiscoverScreen(
    state: DiscoverUiState,
    onAction: (DiscoverAction) -> Unit,
    onMediaDetail: (MediaId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gridState = rememberLazyGridState()
    val keyboardController = LocalSoftwareKeyboardController.current
    var previousQuery by rememberSaveable { mutableStateOf(state.query) }
    var previousFilter by rememberSaveable { mutableStateOf(state.filter.name) }
    LaunchedEffect(state.query, state.filter) {
        if (previousQuery != state.query || previousFilter != state.filter.name) {
            gridState.scrollToItem(0)
        }
        previousQuery = state.query
        previousFilter = state.filter.name
    }

    Column(modifier = modifier.fillMaxSize().imePadding()) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        start = MioTheme.spacing.screenGutter,
                        top = MioTheme.spacing.rootTopContentPadding,
                        end = MioTheme.spacing.screenGutter,
                    ),
        ) {
            Text(
                text = stringResource(R.string.discover_title),
                style = MioTheme.typography.display,
            )
            OutlinedTextField(
                value = state.query,
                onValueChange = { query -> onAction(DiscoverAction.QueryChanged(query)) },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 56.dp),
                placeholder = { Text(stringResource(R.string.discover_search_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions =
                    KeyboardActions(
                        onSearch = {
                            onAction(DiscoverAction.SubmitQuery)
                            keyboardController?.hide()
                        },
                    ),
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        TextButton(
                            onClick = { onAction(DiscoverAction.QueryChanged("")) },
                            colors = ButtonDefaults.textButtonColors(contentColor = MioTheme.colors.text),
                        ) {
                            Text(stringResource(R.string.discover_clear_query))
                        }
                    }
                },
            )
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MediaFilterPill(
                    filter = DiscoverMediaFilter.ALL,
                    selected = state.filter == DiscoverMediaFilter.ALL,
                    onAction = onAction,
                )
                MediaFilterPill(
                    filter = DiscoverMediaFilter.ANIME,
                    selected = state.filter == DiscoverMediaFilter.ANIME,
                    onAction = onAction,
                )
                MediaFilterPill(
                    filter = DiscoverMediaFilter.MANGA,
                    selected = state.filter == DiscoverMediaFilter.MANGA,
                    onAction = onAction,
                )
            }
        }

        if (state.isSearching) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        } else {
            Spacer(Modifier.height(4.dp))
        }

        when {
            state.query.isBlank() -> EmptyPrompt(modifier = Modifier.weight(1f))
            !state.hasObservedResults && !state.refreshFailed -> LoadingGrid(modifier = Modifier.weight(1f))
            !state.hasObservedResults ->
                FailureState(
                    onRetry = { onAction(DiscoverAction.Retry) },
                    modifier = Modifier.weight(1f),
                )
            state.results.isEmpty() ->
                Column(modifier = Modifier.weight(1f)) {
                    if (state.refreshFailed) {
                        FailureBanner(
                            message = stringResource(R.string.discover_cached_failure_message),
                            onRetry = { onAction(DiscoverAction.Retry) },
                        )
                    }
                    EmptyResults(modifier = Modifier.weight(1f))
                }
            else ->
                Column(modifier = Modifier.weight(1f)) {
                    if (state.refreshFailed) {
                        FailureBanner(
                            message = stringResource(R.string.discover_cached_failure_message),
                            onRetry = { onAction(DiscoverAction.Retry) },
                        )
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        state = gridState,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentPadding =
                            PaddingValues(
                                start = MioTheme.spacing.screenGutter,
                                top = 12.dp,
                                end = MioTheme.spacing.screenGutter,
                                bottom = 128.dp,
                            ),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(items = state.results, key = DiscoverResultUiModel::mediaId) { result ->
                            DiscoverResultTile(
                                result = result,
                                onClick = {
                                    keyboardController?.hide()
                                    onMediaDetail(MediaId(result.mediaId))
                                },
                            )
                        }
                        if (state.hasMore) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                LoadMoreAction(
                                    isLoading = state.isLoadingMore,
                                    failed = state.loadMoreFailed,
                                    enabled = !state.isSearching,
                                    onClick = {
                                        keyboardController?.hide()
                                        onAction(DiscoverAction.LoadMore)
                                    },
                                )
                            }
                        }
                    }
                }
        }
    }
}

@Composable
private fun MediaFilterPill(
    filter: DiscoverMediaFilter,
    selected: Boolean,
    onAction: (DiscoverAction) -> Unit,
) {
    val label =
        when (filter) {
            DiscoverMediaFilter.ALL -> stringResource(R.string.discover_filter_all)
            DiscoverMediaFilter.ANIME -> stringResource(R.string.discover_filter_anime)
            DiscoverMediaFilter.MANGA -> stringResource(R.string.discover_filter_manga)
        }
    MioFilterPill(
        label = label,
        selected = selected,
        onClick = { onAction(DiscoverAction.SelectFilter(filter)) },
    )
}

@Composable
private fun DiscoverResultTile(
    result: DiscoverResultUiModel,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick)
                .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        MioPoster(
            reference = result.posterReference,
            contentDescription = null,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = result.title,
            style = MioTheme.typography.bodyStrong,
            maxLines = 2,
        )
        Text(
            text = mediaTypeLabel(result.type),
            style = MioTheme.typography.caption,
            color = MioTheme.colors.textMuted,
        )
        result.trackedStatus?.let { status ->
            Text(
                text = trackedStatusLabel(result.type, status),
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MioTheme.colors.surfaceSoft)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                style = MioTheme.typography.caption,
                color = MioTheme.colors.text,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun mediaTypeLabel(type: DiscoverMediaType): String =
    when (type) {
        DiscoverMediaType.ANIME -> stringResource(R.string.discover_filter_anime)
        DiscoverMediaType.MANGA -> stringResource(R.string.discover_filter_manga)
    }

@Composable
private fun trackedStatusLabel(
    type: DiscoverMediaType,
    status: DiscoverTrackedStatus,
): String =
    when (status) {
        DiscoverTrackedStatus.CURRENT ->
            stringResource(
                if (type ==
                    DiscoverMediaType.ANIME
                ) {
                    R.string.discover_status_watching
                } else {
                    R.string.discover_status_reading
                },
            )
        DiscoverTrackedStatus.PLANNED ->
            stringResource(
                if (type ==
                    DiscoverMediaType.ANIME
                ) {
                    R.string.discover_status_plan_to_watch
                } else {
                    R.string.discover_status_plan_to_read
                },
            )
        DiscoverTrackedStatus.PAUSED -> stringResource(R.string.discover_status_paused)
        DiscoverTrackedStatus.COMPLETED -> stringResource(R.string.discover_status_completed)
        DiscoverTrackedStatus.DROPPED -> stringResource(R.string.discover_status_dropped)
        DiscoverTrackedStatus.REPEATING ->
            stringResource(
                if (type ==
                    DiscoverMediaType.ANIME
                ) {
                    R.string.discover_status_rewatching
                } else {
                    R.string.discover_status_rereading
                },
            )
    }

@Composable
private fun EmptyPrompt(modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxSize().padding(horizontal = MioTheme.spacing.screenGutter),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.discover_prompt_title),
                style = MioTheme.typography.section,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.discover_prompt_message),
                style = MioTheme.typography.body,
                color = MioTheme.colors.textMuted,
            )
        }
    }
}

@Composable
private fun LoadingGrid(modifier: Modifier = Modifier) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier.fillMaxWidth(),
        contentPadding =
            PaddingValues(
                start = MioTheme.spacing.screenGutter,
                top = 12.dp,
                end = MioTheme.spacing.screenGutter,
                bottom = 128.dp,
            ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(4) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MioSkeleton(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(MioTheme.shapes.sm))
                MioSkeleton(Modifier.fillMaxWidth(0.8f).height(16.dp))
                MioSkeleton(Modifier.fillMaxWidth(0.55f).height(12.dp))
            }
        }
    }
}

@Composable
private fun FailureState(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = MioTheme.spacing.screenGutter),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.discover_failure_title), style = MioTheme.typography.section)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.discover_failure_message),
            style = MioTheme.typography.body,
            color = MioTheme.colors.textMuted,
        )
        MioButton(
            text = stringResource(R.string.discover_retry),
            onClick = onRetry,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

@Composable
private fun FailureBanner(
    message: String,
    onRetry: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = MioTheme.spacing.screenGutter, vertical = 8.dp)
                .clip(MioTheme.shapes.sm)
                .background(MioTheme.colors.surfaceSoft)
                .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            modifier = Modifier.weight(1f),
            style = MioTheme.typography.caption,
            color = MioTheme.colors.textMuted,
        )
        TextButton(
            onClick = onRetry,
            colors = ButtonDefaults.textButtonColors(contentColor = MioTheme.colors.text),
        ) { Text(stringResource(R.string.discover_retry)) }
    }
}

@Composable
private fun EmptyResults(modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxSize().padding(horizontal = MioTheme.spacing.screenGutter),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.discover_no_results_title), style = MioTheme.typography.section)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.discover_no_results_message),
                style = MioTheme.typography.body,
                color = MioTheme.colors.textMuted,
            )
        }
    }
}

@Composable
private fun LoadMoreAction(
    isLoading: Boolean,
    failed: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (failed) {
            Text(
                text = stringResource(R.string.discover_load_more_failure),
                style = MioTheme.typography.caption,
                color = MioTheme.colors.textMuted,
            )
            Spacer(Modifier.height(4.dp))
        }
        MioButton(
            text =
                stringResource(
                    when {
                        isLoading -> R.string.discover_loading_more
                        failed -> R.string.discover_retry
                        else -> R.string.discover_load_more
                    },
                ),
            onClick = onClick,
            enabled = enabled && !isLoading,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Preview(name = "Discover light", widthDp = 393, heightDp = 852, showBackground = true)
@Composable
internal fun DiscoverScreenLightPreview() {
    DiscoverPreview(MioPreviewConfiguration.Light, previewPopulatedState())
}

@Preview(
    name = "Discover dark",
    widthDp = 393,
    heightDp = 852,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
)
@Composable
internal fun DiscoverScreenDarkPreview() {
    DiscoverPreview(MioPreviewConfiguration.Dark, previewPopulatedState())
}

@Preview(name = "Discover loading", widthDp = 393, heightDp = 852, showBackground = true)
@Composable
internal fun DiscoverScreenLoadingPreview() {
    DiscoverPreview(
        MioPreviewConfiguration.Light,
        DiscoverUiState(query = "studio ghibli", isSearching = true),
    )
}

@Preview(name = "Discover empty", widthDp = 393, heightDp = 852, showBackground = true)
@Composable
internal fun DiscoverScreenEmptyPreview() {
    DiscoverPreview(
        MioPreviewConfiguration.Light,
        DiscoverUiState(query = "no match", hasObservedResults = true),
    )
}

@Preview(name = "Discover failure", widthDp = 393, heightDp = 852, showBackground = true)
@Composable
internal fun DiscoverScreenFailurePreview() {
    DiscoverPreview(
        MioPreviewConfiguration.Light,
        DiscoverUiState(query = "studio ghibli", refreshFailed = true),
    )
}

@Composable
private fun DiscoverPreview(
    configuration: MioPreviewConfiguration,
    state: DiscoverUiState,
) {
    MioPreviewSurface(configuration) {
        DiscoverScreen(state = state, onAction = {}, onMediaDetail = {})
    }
}

private fun previewPopulatedState(): DiscoverUiState =
    DiscoverUiState(
        query = "studio ghibli",
        results =
            listOf(
                DiscoverResultUiModel(
                    "preview-1",
                    "Spirited Away",
                    null,
                    DiscoverMediaType.ANIME,
                    DiscoverTrackedStatus.CURRENT,
                ),
                DiscoverResultUiModel("preview-2", "Kiki's Delivery Service", null, DiscoverMediaType.ANIME, null),
                DiscoverResultUiModel(
                    "preview-3",
                    "Nausicaä of the Valley of the Wind",
                    null,
                    DiscoverMediaType.MANGA,
                    DiscoverTrackedStatus.PLANNED,
                ),
                DiscoverResultUiModel("preview-4", "Howl's Moving Castle", null, DiscoverMediaType.MANGA, null),
            ),
        hasObservedResults = true,
        hasMore = true,
    )
