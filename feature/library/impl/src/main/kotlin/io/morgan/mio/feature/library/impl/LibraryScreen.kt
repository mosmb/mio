package io.morgan.mio.feature.library.impl

import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import io.morgan.mio.core.designsystem.accessibility.MioHapticType
import io.morgan.mio.core.designsystem.accessibility.rememberMioHaptics
import io.morgan.mio.core.designsystem.component.MioBottomSheet
import io.morgan.mio.core.designsystem.component.MioEmptyState
import io.morgan.mio.core.designsystem.component.MioErrorState
import io.morgan.mio.core.designsystem.component.MioFilterPill
import io.morgan.mio.core.designsystem.component.MioIconButton
import io.morgan.mio.core.designsystem.component.MioSkeleton
import io.morgan.mio.core.designsystem.component.MioSnackbarHost
import io.morgan.mio.core.designsystem.component.MioSnackbarMessage
import io.morgan.mio.core.designsystem.component.MioStatusRow
import io.morgan.mio.core.designsystem.icon.MioIcons
import io.morgan.mio.core.designsystem.preview.MioPreviewConfiguration
import io.morgan.mio.core.designsystem.preview.MioPreviewSurface
import io.morgan.mio.core.designsystem.theme.MioTheme
import io.morgan.mio.domain.model.MediaId
import io.morgan.mio.feature.library.impl.component.LibraryRow

@Composable
internal fun LibraryRoute(
    readyRequestId: String?,
    onMediaDetail: (MediaId) -> Unit,
    onExploreDiscover: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel =
        assistedMetroViewModel<LibraryViewModel, LibraryViewModel.Factory>(
            key = readyRequestId ?: DEFAULT_LIBRARY_VIEW_MODEL_KEY,
        ) { create(initialReady = readyRequestId != null) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val haptics by rememberUpdatedState(rememberMioHaptics())
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
    LaunchedEffect(viewModel) {
        viewModel.routineHaptics.collect { haptics.perform(MioHapticType.ROUTINE_TICK) }
    }
    LibraryScreen(
        state = state,
        onAction = viewModel::onAction,
        onMediaDetail = { mediaId -> onMediaDetail(MediaId(mediaId)) },
        onExploreDiscover = onExploreDiscover,
        timeoutMillis = timeoutMillis,
        modifier = modifier,
    )
}

@Composable
internal fun LibraryScreen(
    state: LibraryUiState,
    onAction: (LibraryAction) -> Unit,
    onMediaDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
    onExploreDiscover: () -> Unit = {},
    timeoutMillis: Long = DEFAULT_UNDO_TIMEOUT_MILLIS.toLong(),
) {
    val state =
        state.withTitleLanguage(
            io.morgan.mio.core.ui.title
                .rememberTitleResolver(),
        )
    val listState = rememberLazyListState()
    var previousMediaFilter by rememberSaveable { mutableStateOf(state.mediaFilter.name) }
    var previousStatusFilter by rememberSaveable { mutableStateOf(state.statusFilter.name) }
    LaunchedEffect(state.mediaFilter, state.statusFilter) {
        val filtersChanged =
            previousMediaFilter != state.mediaFilter.name || previousStatusFilter != state.statusFilter.name
        if (filtersChanged) listState.scrollToItem(0)
        previousMediaFilter = state.mediaFilter.name
        previousStatusFilter = state.statusFilter.name
    }

    var isStatusSheetVisible by remember { mutableStateOf(false) }
    var isSearchVisible by rememberSaveable { mutableStateOf(state.query.isNotEmpty()) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val searchButtonDescription =
        stringResource(
            if (isSearchVisible) R.string.library_close_search else R.string.library_search,
        )
    LaunchedEffect(isSearchVisible) {
        if (isSearchVisible) focusRequester.requestFocus()
    }
    Box(modifier = modifier.fillMaxSize().imePadding()) {
        Column(modifier = Modifier.fillMaxSize()) {
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.library_title),
                        style = MioTheme.typography.display,
                    )
                    MioIconButton(
                        iconRes = if (isSearchVisible) MioIcons.Close else MioIcons.Search,
                        contentDescription = searchButtonDescription,
                        onClick = {
                            if (isSearchVisible) {
                                onAction(LibraryAction.QueryChanged(""))
                                keyboardController?.hide()
                            }
                            isSearchVisible = !isSearchVisible
                        },
                    )
                }
                if (isSearchVisible) {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = { onAction(LibraryAction.QueryChanged(it)) },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .heightIn(min = 56.dp)
                                .focusRequester(focusRequester),
                        placeholder = { Text(stringResource(R.string.library_search_hint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
                        trailingIcon = {
                            if (state.query.isNotEmpty()) {
                                TextButton(
                                    onClick = { onAction(LibraryAction.QueryChanged("")) },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MioTheme.colors.text),
                                ) {
                                    Text(stringResource(R.string.library_clear_search))
                                }
                            }
                        },
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MediaFilterPill(
                        filter = LibraryMediaFilter.ALL,
                        selected = state.mediaFilter == LibraryMediaFilter.ALL,
                        onAction = onAction,
                    )
                    MediaFilterPill(
                        filter = LibraryMediaFilter.ANIME,
                        selected = state.mediaFilter == LibraryMediaFilter.ANIME,
                        onAction = onAction,
                    )
                    MediaFilterPill(
                        filter = LibraryMediaFilter.MANGA,
                        selected = state.mediaFilter == LibraryMediaFilter.MANGA,
                        onAction = onAction,
                    )
                    MioFilterPill(
                        label = statusFilterLabel(state.statusFilter),
                        selected = state.statusFilter != LibraryStatusFilter.ALL,
                        onClick = { isStatusSheetVisible = true },
                    )
                }
            }

            when {
                state.isLoading && !state.hasObservedCollection -> LibraryLoadingList(modifier = Modifier.weight(1f))
                else -> {
                    if (state.observationFailed) {
                        MioErrorState(
                            title = stringResource(R.string.library_observation_error_title),
                            message = stringResource(R.string.library_observation_error_message),
                            retryLabel = stringResource(R.string.library_retry),
                            onRetry = { onAction(LibraryAction.RetryObservation) },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = MioTheme.spacing.screenGutter),
                        )
                    }
                    when {
                        !state.hasObservedCollection -> Unit
                        state.isCollectionEmpty ->
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                MioEmptyState(
                                    title = stringResource(R.string.library_empty_title),
                                    message = stringResource(R.string.library_empty_message),
                                    actionLabel = stringResource(R.string.library_search_titles),
                                    onAction = onExploreDiscover,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        state.isSearchEmpty ->
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                MioEmptyState(
                                    title = stringResource(R.string.library_search_empty_title),
                                    message = stringResource(R.string.library_search_empty_message),
                                    actionLabel = stringResource(R.string.library_clear_search),
                                    onAction = { onAction(LibraryAction.QueryChanged("")) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        state.rows.isEmpty() ->
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                MioEmptyState(
                                    title = stringResource(R.string.library_filtered_empty_title),
                                    message = stringResource(R.string.library_filtered_empty_message),
                                    actionLabel = stringResource(R.string.library_clear_filters),
                                    onAction = { onAction(LibraryAction.ClearFilters) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        else ->
                            LibraryRows(
                                rows = state.rows,
                                listState = listState,
                                onMediaDetail = onMediaDetail,
                                onAction = onAction,
                                timeoutMillis = timeoutMillis,
                                modifier = Modifier.weight(1f),
                            )
                    }
                }
            }
        }

        LibrarySnackbar(state = state, onAction = onAction)
    }

    MioBottomSheet(
        visible = isStatusSheetVisible,
        onDismissRequest = { isStatusSheetVisible = false },
        title = stringResource(R.string.library_status_sheet_title),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            state.availableStatusFilters.forEach { filter ->
                MioStatusRow(
                    id = filter.name,
                    label = statusFilterLabel(filter),
                    selected = filter == state.statusFilter,
                    onSelected = {
                        onAction(LibraryAction.SelectStatusFilter(filter))
                        isStatusSheetVisible = false
                    },
                )
            }
        }
    }
}

@Composable
private fun MediaFilterPill(
    filter: LibraryMediaFilter,
    selected: Boolean,
    onAction: (LibraryAction) -> Unit,
) {
    val label =
        when (filter) {
            LibraryMediaFilter.ALL -> stringResource(R.string.library_filter_all)
            LibraryMediaFilter.ANIME -> stringResource(R.string.library_filter_anime)
            LibraryMediaFilter.MANGA -> stringResource(R.string.library_filter_manga)
        }
    MioFilterPill(
        label = label,
        selected = selected,
        onClick = { onAction(LibraryAction.SelectMediaFilter(filter)) },
    )
}

@Composable
private fun LibraryRows(
    rows: List<LibraryRowUiModel>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onMediaDetail: (String) -> Unit,
    onAction: (LibraryAction) -> Unit,
    timeoutMillis: Long,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding =
            PaddingValues(
                start = MioTheme.spacing.screenGutter,
                top = 12.dp,
                end = MioTheme.spacing.screenGutter,
                bottom = 128.dp,
            ),
    ) {
        items(items = rows, key = LibraryRowUiModel::entryId) { row ->
            LibraryRow(
                row = row,
                onClick = { onMediaDetail(row.mediaId) },
                onDecrease = {
                    onAction(LibraryAction.Step(row.mediaId, LibraryProgressDirection.DECREASE, timeoutMillis))
                },
                onIncrease = {
                    onAction(LibraryAction.Step(row.mediaId, LibraryProgressDirection.INCREASE, timeoutMillis))
                },
                onInteractionChanged = { pressed ->
                    onAction(LibraryAction.InteractionChanged(row.mediaId, pressed))
                },
            )
            Spacer(Modifier.height(8.dp))
            androidx.compose.material3.HorizontalDivider(color = MioTheme.colors.border)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun LibrarySnackbar(
    state: LibraryUiState,
    onAction: (LibraryAction) -> Unit,
) {
    val message =
        when {
            state.progressError != null ->
                MioSnackbarMessage(
                    id = PROGRESS_ERROR_SNACKBAR_ID,
                    message =
                        stringResource(
                            if (state.progressError.retryable) {
                                R.string.library_progress_error
                            } else {
                                R.string.library_progress_rejected
                            },
                        ),
                    actionLabel =
                        stringResource(
                            if (state.progressError.retryable) {
                                R.string.library_retry_progress
                            } else {
                                R.string.library_dismiss
                            },
                        ),
                )
            state.latestUndo != null ->
                MioSnackbarMessage(
                    id = state.latestUndo.groupId,
                    message =
                        stringResource(
                            when (state.latestUndo.unit) {
                                LibraryProgressUnit.EPISODE -> R.string.library_saved_episode
                                LibraryProgressUnit.CHAPTER -> R.string.library_saved_chapter
                                LibraryProgressUnit.VOLUME -> R.string.library_saved_volume
                            },
                            state.latestUndo.progress,
                        ),
                    actionLabel = stringResource(R.string.library_undo),
                )
            else -> null
        }
    MioSnackbarHost(
        snackbar = message,
        bottomNavigationVisible = true,
        onAction = { id ->
            if (id == PROGRESS_ERROR_SNACKBAR_ID) {
                if (state.progressError?.retryable == true) {
                    onAction(LibraryAction.RetryProgress)
                } else {
                    onAction(LibraryAction.DismissProgressError)
                }
            } else {
                onAction(LibraryAction.Undo(id))
            }
        },
    )
}

@Composable
private fun LibraryLoadingList(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding =
            PaddingValues(
                start = MioTheme.spacing.screenGutter,
                top = 12.dp,
                end = MioTheme.spacing.screenGutter,
                bottom = 128.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(5) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                MioSkeleton(Modifier.size(width = 84.dp, height = 120.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MioSkeleton(Modifier.fillMaxWidth(0.65f).height(18.dp))
                    MioSkeleton(Modifier.width(120.dp).height(14.dp))
                    MioSkeleton(Modifier.width(160.dp).height(14.dp))
                }
            }
        }
    }
}

@Composable
private fun statusFilterLabel(filter: LibraryStatusFilter): String =
    when (filter) {
        LibraryStatusFilter.ALL -> stringResource(R.string.library_filter_all_statuses)
        LibraryStatusFilter.READY -> stringResource(R.string.library_filter_ready)
        LibraryStatusFilter.CURRENT -> stringResource(R.string.library_status_current)
        LibraryStatusFilter.PLANNED -> stringResource(R.string.library_status_planning)
        LibraryStatusFilter.PAUSED -> stringResource(R.string.library_status_paused)
        LibraryStatusFilter.COMPLETED -> stringResource(R.string.library_status_completed)
        LibraryStatusFilter.DROPPED -> stringResource(R.string.library_status_dropped)
        LibraryStatusFilter.REPEATING -> stringResource(R.string.library_status_repeating)
    }

@Preview(name = "Library loading", widthDp = 393, heightDp = 852, showBackground = true)
@Composable
internal fun LibraryLoadingPreview() {
    MioPreviewSurface(MioPreviewConfiguration.Light) {
        LibraryScreen(
            state = LibraryUiState(),
            onAction = {},
            onMediaDetail = {},
        )
    }
}

@Preview(name = "Library populated", widthDp = 393, heightDp = 852, showBackground = true)
@Composable
internal fun LibraryPopulatedPreview() {
    MioPreviewSurface(MioPreviewConfiguration.Light) {
        LibraryScreen(
            state =
                LibraryUiState(
                    isLoading = false,
                    hasObservedCollection = true,
                    collectionSize = 2,
                    rows =
                        listOf(
                            previewLibraryRow(
                                "one",
                                "Night Train",
                                LibraryRowMediaType.ANIME,
                                LibraryRowStatus.CURRENT,
                            ),
                            previewLibraryRow(
                                "two",
                                "Quiet Garden",
                                LibraryRowMediaType.MANGA,
                                LibraryRowStatus.PAUSED,
                            ),
                        ),
                    availableStatusFilters = LibraryUiState.DEFAULT_STATUS_FILTERS,
                ),
            onAction = {},
            onMediaDetail = {},
        )
    }
}

@Preview(
    name = "Library dark",
    widthDp = 393,
    heightDp = 852,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
internal fun LibraryDarkPreview() {
    MioPreviewSurface(MioPreviewConfiguration.Dark) {
        LibraryScreen(
            state =
                LibraryUiState(
                    isLoading = false,
                    hasObservedCollection = true,
                    collectionSize = 1,
                    rows =
                        listOf(
                            previewLibraryRow(
                                "one",
                                "Night Train",
                                LibraryRowMediaType.ANIME,
                                LibraryRowStatus.CURRENT,
                            ),
                        ),
                ),
            onAction = {},
            onMediaDetail = {},
        )
    }
}

@Preview(name = "Library empty", widthDp = 393, heightDp = 852, showBackground = true)
@Composable
internal fun LibraryEmptyPreview() {
    MioPreviewSurface(MioPreviewConfiguration.Light) {
        LibraryScreen(
            state = LibraryUiState(isLoading = false, hasObservedCollection = true),
            onAction = {},
            onMediaDetail = {},
        )
    }
}

@Preview(name = "Library recoverable failure", widthDp = 393, heightDp = 852, showBackground = true)
@Composable
internal fun LibraryFailurePreview() {
    MioPreviewSurface(MioPreviewConfiguration.Dark) {
        LibraryScreen(
            state =
                LibraryUiState(
                    isLoading = false,
                    hasObservedCollection = true,
                    observationFailed = true,
                    collectionSize = 1,
                    rows =
                        listOf(
                            previewLibraryRow(
                                "one",
                                "Night Train",
                                LibraryRowMediaType.ANIME,
                                LibraryRowStatus.CURRENT,
                            ),
                        ),
                ),
            onAction = {},
            onMediaDetail = {},
        )
    }
}

private const val DEFAULT_LIBRARY_VIEW_MODEL_KEY = "default"

private fun previewLibraryRow(
    entryId: String,
    title: String,
    mediaType: LibraryRowMediaType,
    status: LibraryRowStatus,
): LibraryRowUiModel =
    LibraryRowUiModel(
        entryId = entryId,
        mediaId = "media-$entryId",
        title = title,
        posterReference = null,
        mediaType = mediaType,
        status = status,
        progress = 8,
        progressUnit = LibraryProgressUnit.EPISODE,
        total = 12,
        controls =
            if (status == LibraryRowStatus.CURRENT) {
                LibraryProgressControlsUiModel(decreaseEnabled = true, increaseEnabled = true)
            } else {
                null
            },
    )

private const val DEFAULT_UNDO_TIMEOUT_MILLIS: Int = 6_000
private const val PROGRESS_ERROR_SNACKBAR_ID: String = "progress-error"
