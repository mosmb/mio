package io.morgan.mio.feature.you.impl

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import dev.zacsweers.metrox.viewmodel.metroViewModel
import io.morgan.mio.core.designsystem.component.MioIcon
import io.morgan.mio.core.designsystem.preview.MioPreviewConfiguration
import io.morgan.mio.core.designsystem.preview.MioPreviewSurface
import io.morgan.mio.core.designsystem.theme.MioTheme
import io.morgan.mio.core.navigation.MioNavigator
import io.morgan.mio.core.ui.LocalAccountRecoveryActions
import io.morgan.mio.core.ui.LocalAccountSessionActions
import io.morgan.mio.core.ui.image.MioArtwork
import io.morgan.mio.domain.model.MediaType
import io.morgan.mio.domain.preferences.AppearancePreference
import io.morgan.mio.feature.you.api.YouResolveProgressRequest
import kotlinx.coroutines.awaitCancellation
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import java.text.DateFormatSymbols
import java.text.NumberFormat
import java.util.Locale

@Composable
internal fun YouRoute(
    navigator: MioNavigator,
    modifier: Modifier = Modifier,
) {
    val viewModel = metroViewModel<YouViewModel>()
    val state = viewModel.uiState.collectAsStateWithLifecycle().value
    val lifecycleOwner = LocalLifecycleOwner.current
    val recoveryActions = LocalAccountRecoveryActions.current
    val accountActions = LocalAccountSessionActions.current
    LaunchedEffect(lifecycleOwner, viewModel) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.onScreenResumed()
            awaitCancellation()
        }
    }
    YouScreen(
        state = state,
        onAction = viewModel::onAction,
        onReconnect = recoveryActions.onReconnect,
        onRetryRefresh = recoveryActions.onRetryRefresh,
        onResolve = { operationId, entryId ->
            navigator.navigate(YouResolveProgressRequest(operationIdValue = operationId, entryIdValue = entryId))
        },
        onDisconnect = accountActions.onDisconnect,
        modifier = modifier,
    )
}

@Composable
internal fun YouScreen(
    state: YouUiState,
    onAction: (YouAction) -> Unit,
    onReconnect: () -> Unit,
    onRetryRefresh: () -> Unit,
    onResolve: (operationId: String, entryId: String) -> Unit,
    modifier: Modifier = Modifier,
    onDisconnect: () -> Unit = {},
) {
    val state =
        state.withTitleLanguage(
            io.morgan.mio.core.ui.title
                .rememberTitleResolver(),
        )
    var showDisconnectConfirmation by remember { mutableStateOf(false) }
    when (state) {
        YouUiState.Loading ->
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        YouUiState.Error ->
            Column(
                modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(stringResource(R.string.you_account_unavailable), style = MioTheme.typography.body)
                TextButton(colors = ButtonDefaults.textButtonColors(contentColor = MioTheme.colors.text), onClick = {
                    onAction(YouAction.RetryScreen)
                }) { Text(stringResource(R.string.you_retry)) }
            }
        YouUiState.NoAccount ->
            Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.you_account_unavailable), style = MioTheme.typography.body)
            }
        is YouUiState.Content ->
            Column(
                modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(MioTheme.spacing.space6),
            ) {
                Text(
                    text = stringResource(R.string.you_title),
                    style = MioTheme.typography.title,
                    modifier = Modifier.semantics { heading() },
                )
                RecapSection(state.statistics, state.rotationDate) { onAction(YouAction.RetryScreen) }
                StatisticsSection(
                    statistics = state.statistics,
                    selectedType = state.selectedStatisticsType,
                    onSelectType = { type -> onAction(YouAction.SelectStatisticsType(type)) },
                )
                ActivitySection(state.activity, state.selectedStatisticsType) { onAction(YouAction.RetryScreen) }
                YouPreferencesSection(
                    appearance = state.appearance,
                    titleLanguage = state.titleLanguage,
                    onTitleLanguage = { onAction(YouAction.SelectTitleLanguage(it)) },
                    saveFailed = state.appearanceSaveFailed,
                    onDismissError = { onAction(YouAction.DismissAppearanceError) },
                    onAppearance = { preference -> onAction(YouAction.SelectAppearance(preference)) },
                )
                AccountSection(state.identity, onDisconnect = { showDisconnectConfirmation = true })
                Spacer(Modifier.height(128.dp))
            }
    }
    if (showDisconnectConfirmation && state is YouUiState.Content) {
        DisconnectConfirmationDialog(
            providerName = stringResource(state.identity.provider.labelRes),
            onConfirm = {
                showDisconnectConfirmation = false
                onDisconnect()
            },
            onDismiss = { showDisconnectConfirmation = false },
        )
    }
}

@Composable
private fun RecapSection(
    state: YouCollectionStatisticsUiState,
    rotationDate: LocalDate,
    onRetry: () -> Unit,
) {
    when (state) {
        YouCollectionStatisticsUiState.Loading -> {
            SectionHeading(stringResource(R.string.you_recap_title))
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
        }
        YouCollectionStatisticsUiState.Unavailable -> {
            SectionHeading(stringResource(R.string.you_recap_title))
            Text(stringResource(R.string.you_statistics_unavailable), style = MioTheme.typography.body)
            TextButton(
                colors = ButtonDefaults.textButtonColors(contentColor = MioTheme.colors.text),
                onClick = onRetry,
            ) {
                Text(stringResource(R.string.you_retry))
            }
        }
        is YouCollectionStatisticsUiState.Content -> {
            val artwork =
                remember(state.artworkCandidates, rotationDate) {
                    rotatingArtworkSample(state.artworkCandidates, rotationDate)
                }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF111418),
                shape =
                    androidx.compose.foundation.shape
                        .RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            stringResource(R.string.you_recap_title),
                            style = MioTheme.typography.section,
                            color = Color.White,
                            modifier = Modifier.semantics { heading() },
                        )
                        Text(
                            stringResource(R.string.you_recap_cumulative),
                            style = MioTheme.typography.caption,
                            color = Color.White.copy(alpha = 0.72f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RecapMetric(
                            label = stringResource(R.string.you_recap_completed),
                            value = formatCount(state.completedTitleCount),
                            note =
                                pluralStringResource(
                                    R.plurals.you_recap_titles,
                                    if (state.completedTitleCount ==
                                        1L
                                    ) {
                                        1
                                    } else {
                                        2
                                    },
                                ),
                            modifier = Modifier.weight(1f),
                        )
                        RecapMetric(
                            label = stringResource(R.string.you_recap_episodes),
                            value =
                                state.episodes.value?.let(::formatCount)
                                    ?: stringResource(R.string.you_metric_unavailable),
                            note = state.episodes.note(R.string.you_episode, R.string.you_episode_progress_unavailable),
                            modifier = Modifier.weight(1f),
                        )
                        RecapMetric(
                            label = stringResource(R.string.you_recap_chapters),
                            value =
                                state.chapters.value?.let(::formatCount)
                                    ?: stringResource(R.string.you_metric_unavailable),
                            note = state.chapters.note(R.string.you_chapter, R.string.you_chapter_progress_unavailable),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (artwork.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            artwork.forEach { item ->
                                MioArtwork(
                                    reference = item.posterReference,
                                    contentDescription = null,
                                    modifier = Modifier.weight(1f).height(88.dp).clip(MioTheme.shapes.sm),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecapMetric(
    label: String,
    value: String,
    note: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MioTheme.typography.label, color = Color.White.copy(alpha = 0.74f))
        Text(value, style = MioTheme.typography.cardTitle, color = Color.White, fontWeight = FontWeight.SemiBold)
        Text(note, style = MioTheme.typography.caption, color = Color.White.copy(alpha = 0.72f))
    }
}

@Composable
private fun YouRecordedProgressTotalUiModel.note(
    unitLabelRes: Int,
    unavailableLabelRes: Int,
): String =
    when {
        value == null -> stringResource(unavailableLabelRes)
        includedTitleCount < trackedTitleCount ->
            stringResource(
                R.string.you_progress_from_titles,
                formatCount(includedTitleCount),
                formatCount(trackedTitleCount),
                stringResource(unitLabelRes),
            )
        else -> stringResource(R.string.you_recap_cumulative_short)
    }

@Composable
private fun StatisticsSection(
    statistics: YouCollectionStatisticsUiState,
    selectedType: MediaType,
    onSelectType: (MediaType) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(MioTheme.spacing.space3)) {
        SectionHeading(stringResource(R.string.you_statistics_title))
        YouStatisticsSelector(selectedType, onSelectType)
        when (statistics) {
            YouCollectionStatisticsUiState.Loading ->
                CircularProgressIndicator(
                    Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                )
            YouCollectionStatisticsUiState.Unavailable ->
                Text(stringResource(R.string.you_statistics_unavailable), style = MioTheme.typography.body)
            is YouCollectionStatisticsUiState.Content -> {
                val selected =
                    if (selectedType == MediaType.ANIME) statistics.anime else statistics.manga
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MioTheme.colors.surfaceSoft,
                    shape = MioTheme.shapes.lg,
                    border = BorderStroke(1.dp, MioTheme.colors.border),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            pluralStringResource(
                                R.plurals.you_tracked_title_count,
                                if (selected.trackedTitleCount == 1L) 1 else 2,
                                stringResource(selectedType.labelRes()),
                                formatCount(selected.trackedTitleCount),
                            ),
                            style = MioTheme.typography.bodyStrong,
                        )
                        Text(
                            stringResource(
                                R.string.you_type_completed_count,
                                formatCount(selected.completedTitleCount),
                            ),
                            style = MioTheme.typography.caption,
                            color = MioTheme.colors.textMuted,
                        )
                        Text(stringResource(R.string.you_top_genres), style = MioTheme.typography.bodyStrong)
                        when {
                            selected.trackedTitleCount == 0L ->
                                Text(stringResource(R.string.you_no_tracked_titles), style = MioTheme.typography.body)
                            selected.genreMetadataTitleCount == 0L || selected.topGenres.isEmpty() ->
                                Text(
                                    stringResource(R.string.you_genre_data_unavailable),
                                    style = MioTheme.typography.body,
                                    color = MioTheme.colors.textMuted,
                                )
                            else -> {
                                selected.topGenres.forEachIndexed { index, genre ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        Text(
                                            stringResource(R.string.you_genre_ranked, index + 1, genre.name),
                                            style = MioTheme.typography.body,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Text(
                                            pluralStringResource(
                                                R.plurals.you_genre_title_count,
                                                if (genre.titleCount == 1L) 1 else 2,
                                                formatCount(genre.titleCount),
                                            ),
                                            style = MioTheme.typography.caption,
                                            color = MioTheme.colors.textMuted,
                                        )
                                    }
                                }
                                if (selected.genreMetadataTitleCount < selected.trackedTitleCount) {
                                    Text(
                                        stringResource(
                                            R.string.you_genre_partial_coverage,
                                            formatCount(selected.genreMetadataTitleCount),
                                            formatCount(selected.trackedTitleCount),
                                        ),
                                        style = MioTheme.typography.caption,
                                        color = MioTheme.colors.textMuted,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivitySection(
    state: YouActivityUiState,
    selectedType: MediaType,
    onRetry: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(MioTheme.spacing.space3)) {
        SectionHeading(stringResource(R.string.you_activity_title))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MioTheme.colors.surfaceSoft,
            shape = MioTheme.shapes.lg,
            border = BorderStroke(1.dp, MioTheme.colors.border),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    stringResource(R.string.you_activity_recorded_heading),
                    style = MioTheme.typography.bodyStrong,
                )
                when (state) {
                    YouActivityUiState.Loading -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    is YouActivityUiState.Unavailable -> {
                        Text(
                            stringResource(R.string.you_activity_unavailable),
                            style = MioTheme.typography.body,
                        )
                        TextButton(
                            colors = ButtonDefaults.textButtonColors(contentColor = MioTheme.colors.text),
                            onClick = onRetry,
                        ) {
                            Text(stringResource(R.string.you_retry))
                        }
                        Text(
                            stringResource(
                                R.string.you_activity_date_range,
                                state.fromDate.shortDate(),
                                state.throughDate.shortDate(),
                            ),
                            style = MioTheme.typography.caption,
                            color = MioTheme.colors.textMuted,
                        )
                    }
                    is YouActivityUiState.Content -> {
                        Text(
                            stringResource(
                                R.string.you_activity_date_range,
                                state.fromDate.shortDate(),
                                state.throughDate.shortDate(),
                            ),
                            style = MioTheme.typography.caption,
                            color = MioTheme.colors.textMuted,
                        )
                        Text(
                            stringResource(R.string.you_activity_measurement, stringResource(selectedType.labelRes())),
                            style = MioTheme.typography.label,
                            color = MioTheme.colors.textMuted,
                        )
                        if (state.totalActions == 0L) {
                            Text(
                                stringResource(R.string.you_activity_empty),
                                style = MioTheme.typography.body,
                            )
                        } else {
                            ActivityBarChart(state.weeks)
                            Text(
                                stringResource(
                                    R.string.you_activity_breakdown,
                                    formatCount(state.totalActions),
                                    formatCount(state.totalIncreases),
                                    formatCount(state.totalDecreases),
                                    formatCount(state.totalCorrections),
                                ),
                                style = MioTheme.typography.body,
                            )
                        }
                        Text(
                            stringResource(R.string.you_activity_retained_note),
                            style = MioTheme.typography.caption,
                            color = MioTheme.colors.textMuted,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityBarChart(weeks: List<YouActivityWeekUiModel>) {
    val maxCount = weeks.maxOfOrNull(YouActivityWeekUiModel::actionCount)?.coerceAtLeast(1L) ?: 1L
    Row(
        modifier = Modifier.fillMaxWidth().height(126.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        weeks.forEach { week ->
            Column(
                modifier = Modifier.weight(1f).fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Text(
                    formatCount(week.actionCount),
                    style = MioTheme.typography.caption,
                    color = MioTheme.colors.textMuted,
                )
                Spacer(Modifier.height(3.dp))
                val fraction = week.actionCount.toFloat() / maxCount.toFloat()
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth(0.68f)
                            .height((if (week.actionCount == 0L) 3f else (64f * fraction).coerceAtLeast(7f)).dp)
                            .clip(MioTheme.shapes.sm)
                            .background(
                                if (week.actionCount ==
                                    0L
                                ) {
                                    MioTheme.colors.borderStrong
                                } else {
                                    MioTheme.colors.accent
                                },
                            ),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    week.weekStart.shortNumericDate(),
                    style = MioTheme.typography.caption,
                    color = MioTheme.colors.textMuted,
                    maxLines = 1,
                )
            }
        }
    }
}

private fun rotatingArtworkSample(
    candidates: List<YouArtworkUiModel>,
    date: LocalDate,
): List<YouArtworkUiModel> {
    if (candidates.isEmpty()) return emptyList()
    val sampleCount = minOf(5, candidates.size)
    val startIndex = Math.floorMod(date.toEpochDays(), candidates.size.toLong()).toInt()
    return (0 until sampleCount).map { offset -> candidates[(startIndex + offset) % candidates.size] }
}

private fun MediaType.labelRes(): Int =
    when (this) {
        MediaType.ANIME -> R.string.you_type_anime
        MediaType.MANGA -> R.string.you_type_manga
    }

private fun formatCount(value: Long): String = NumberFormat.getIntegerInstance().format(value)

private fun LocalDate.shortDate(): String {
    val monthName = DateFormatSymbols.getInstance(Locale.getDefault()).shortMonths.getOrNull(month.ordinal)
    val monthNumber = month.ordinal + 1
    return if (monthName.isNullOrBlank()) "$monthNumber/$day" else "$monthName $day"
}

private fun LocalDate.shortNumericDate(): String = "${month.ordinal + 1}/$day"

@Composable
private fun AccountSection(
    identity: YouIdentityUiModel,
    onDisconnect: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(MioTheme.spacing.space3)) {
        SectionHeading(stringResource(R.string.you_account))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MioTheme.colors.surface,
            shape = MioTheme.shapes.lg,
            border = BorderStroke(1.dp, MioTheme.colors.border),
        ) {
            Column {
                IdentityContent(identity)
                HorizontalDivider(Modifier.padding(horizontal = 18.dp), color = MioTheme.colors.border)
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button, onClick = onDisconnect)
                            .padding(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MioIcon(R.drawable.you_logout, null, tint = MioTheme.colors.error)
                    Text(
                        stringResource(R.string.you_disconnect_provider, stringResource(identity.provider.labelRes)),
                        style = MioTheme.typography.bodyStrong,
                        color = MioTheme.colors.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun DisconnectConfirmationDialog(
    providerName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.you_disconnect_title, providerName)) },
        text = { Text(stringResource(R.string.you_disconnect_message, providerName)) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MioTheme.colors.errorSurface,
                        contentColor = MioTheme.colors.error,
                    ),
            ) {
                Text(stringResource(R.string.you_disconnect_confirm))
            }
        },
        dismissButton = {
            TextButton(
                colors = ButtonDefaults.textButtonColors(contentColor = MioTheme.colors.text),
                onClick = onDismiss,
            ) {
                Text(stringResource(R.string.you_disconnect_cancel))
            }
        },
    )
}

@Composable
private fun IdentityContent(identity: YouIdentityUiModel) {
    Row(
        modifier = Modifier.padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (identity.avatarReference != null) {
            MioArtwork(
                reference = identity.avatarReference,
                contentDescription = stringResource(R.string.you_avatar_description, identity.displayName),
                modifier = Modifier.size(56.dp).clip(CircleShape),
            )
        } else {
            Box(
                modifier = Modifier.size(56.dp).clip(CircleShape).background(MioTheme.colors.surfaceSoft),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    identity.displayName.firstOrNull()?.uppercase() ?: "?",
                    style = MioTheme.typography.section,
                    color = MioTheme.colors.textMuted,
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(identity.displayName, style = MioTheme.typography.cardTitle, fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(identity.provider.labelRes),
                style = MioTheme.typography.label,
                color = MioTheme.colors.textMuted,
            )
        }
    }
}

@Composable
private fun SynchronizationSection(
    state: YouUiState.Content,
    onRetry: (String) -> Unit,
    onReconnect: () -> Unit,
    onRetryRefresh: () -> Unit,
    onResolve: (String, String) -> Unit,
    onDismissError: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(MioTheme.spacing.space3)) {
        SectionHeading(stringResource(R.string.you_synchronization))
        if (state.retryFailed) {
            Surface(color = MioTheme.colors.errorSurface, shape = MioTheme.shapes.md) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.you_recovery_failed),
                        color = MioTheme.colors.error,
                        style = MioTheme.typography.body,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        colors = ButtonDefaults.textButtonColors(contentColor = MioTheme.colors.text),
                        onClick = onDismissError,
                    ) {
                        Text(stringResource(R.string.you_dismiss))
                    }
                }
            }
        }
        state.accountIssue?.let { issue ->
            RecoveryCard(
                title = stringResource(issue.titleRes()),
                message = stringResource(issue.messageRes()),
                actionLabel = stringResource(issue.actionLabelRes()),
                onAction = if (issue == YouAccountIssueKind.AUTHENTICATION) onReconnect else onRetryRefresh,
            )
        }
        state.syncIssues.forEach { issue ->
            RecoveryCard(
                title = stringResource(issue.titleRes()),
                message = issue.message(),
                actionLabel = stringResource(issue.actionLabelRes()),
                pending = issue.operationId in state.pendingRetryIds,
                onAction = {
                    when {
                        issue.needsReconnect -> onReconnect()
                        issue.needsResolve -> onResolve(issue.operationId, issue.entryId)
                        issue.canRetry -> onRetry(issue.operationId)
                        else -> onRetryRefresh()
                    }
                },
            )
        }
        if (state.accountIssue == null && state.syncIssues.isEmpty()) {
            Text(
                stringResource(R.string.you_sync_healthy),
                style = MioTheme.typography.body,
                color = MioTheme.colors.textMuted,
            )
        }
    }
}

@Composable
private fun RecoveryCard(
    title: String,
    message: String,
    actionLabel: String,
    modifier: Modifier = Modifier,
    pending: Boolean = false,
    onAction: () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MioTheme.colors.errorSurface,
        shape = MioTheme.shapes.md,
        border = BorderStroke(1.dp, MioTheme.colors.border),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MioTheme.typography.cardTitle, color = MioTheme.colors.error)
            Text(message, style = MioTheme.typography.body, color = MioTheme.colors.text)
            Button(onClick = onAction, enabled = !pending) {
                if (pending) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(text, style = MioTheme.typography.section, modifier = Modifier.semantics { heading() })
}

@Composable
private fun YouSyncIssueUiModel.message(): String =
    if (kind == YouSyncIssueKind.CONFLICT && remoteProgress != null) {
        stringResource(
            R.string.you_sync_conflict_message,
            localProgress,
            stringResource(localUnit.labelRes()),
            remoteProgress,
            stringResource((remoteUnit ?: localUnit).labelRes()),
        )
    } else {
        stringResource(kind.messageRes())
    }

private fun YouSyncIssueUiModel.titleRes(): Int =
    when (kind) {
        YouSyncIssueKind.AUTHENTICATION -> R.string.you_sync_authentication_title
        YouSyncIssueKind.REJECTED -> R.string.you_sync_rejected_title
        YouSyncIssueKind.CONFLICT -> R.string.you_sync_conflict_title
        YouSyncIssueKind.RETRY_EXHAUSTED -> R.string.you_sync_retry_exhausted_title
        YouSyncIssueKind.INVALID_REMOTE_DATA -> R.string.you_sync_invalid_title
    }

private fun YouSyncIssueUiModel.actionLabelRes(): Int =
    when {
        needsReconnect -> R.string.you_reconnect
        needsResolve -> R.string.you_resolve
        canRetry -> R.string.you_retry
        else -> R.string.you_refresh
    }

private fun YouSyncIssueKind.messageRes(): Int =
    when (this) {
        YouSyncIssueKind.AUTHENTICATION -> R.string.you_sync_authentication_message
        YouSyncIssueKind.REJECTED -> R.string.you_sync_rejected_message
        YouSyncIssueKind.CONFLICT -> R.string.you_sync_conflict_unavailable_message
        YouSyncIssueKind.RETRY_EXHAUSTED -> R.string.you_sync_retry_exhausted_message
        YouSyncIssueKind.INVALID_REMOTE_DATA -> R.string.you_sync_invalid_message
    }

private fun YouAccountIssueKind.titleRes(): Int =
    when (this) {
        YouAccountIssueKind.AUTHENTICATION -> R.string.you_sync_authentication_title
        YouAccountIssueKind.INVALID_REMOTE_DATA -> R.string.you_sync_invalid_title
    }

private fun YouAccountIssueKind.messageRes(): Int =
    when (this) {
        YouAccountIssueKind.AUTHENTICATION -> R.string.you_sync_authentication_message
        YouAccountIssueKind.INVALID_REMOTE_DATA -> R.string.you_sync_invalid_message
    }

private fun YouAccountIssueKind.actionLabelRes(): Int =
    when (this) {
        YouAccountIssueKind.AUTHENTICATION -> R.string.you_reconnect
        YouAccountIssueKind.INVALID_REMOTE_DATA -> R.string.you_refresh
    }

private fun YouProgressUnit.labelRes(): Int =
    when (this) {
        YouProgressUnit.EPISODE -> R.string.you_episode
        YouProgressUnit.CHAPTER -> R.string.you_chapter
        YouProgressUnit.VOLUME -> R.string.you_volume
    }

internal fun AppearancePreference.labelRes(): Int =
    when (this) {
        AppearancePreference.SYSTEM -> R.string.you_appearance_system
        AppearancePreference.LIGHT -> R.string.you_appearance_light
        AppearancePreference.DARK -> R.string.you_appearance_dark
    }

@Preview(name = "You light", widthDp = 393, heightDp = 852, showBackground = true)
@Composable
internal fun YouScreenLightPreview() {
    YouPreview(MioPreviewConfiguration.Light)
}

@Preview(
    name = "You dark",
    widthDp = 393,
    heightDp = 852,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
)
@Composable
internal fun YouScreenDarkPreview() {
    YouPreview(MioPreviewConfiguration.Dark)
}

@Composable
private fun YouPreview(configuration: MioPreviewConfiguration) {
    MioPreviewSurface(configuration) {
        YouScreen(
            state =
                YouUiState.Content(
                    identity = YouIdentityUiModel("Morgan", YouProvider.ANILIST, null),
                    appearance = AppearancePreference.SYSTEM,
                    syncIssues = emptyList(),
                    accountIssue = null,
                    pendingRetryIds = emptySet(),
                    retryFailed = false,
                    statistics =
                        YouCollectionStatisticsUiState.Content(
                            completedTitleCount = 38,
                            episodes = YouRecordedProgressTotalUiModel(412, 58, 58),
                            chapters = YouRecordedProgressTotalUiModel(867, 92, 77),
                            anime =
                                YouMediaStatisticsUiModel(
                                    trackedTitleCount = 58,
                                    completedTitleCount = 21,
                                    genreMetadataTitleCount = 51,
                                    topGenres =
                                        listOf(
                                            YouGenreUiModel("Action", 24),
                                            YouGenreUiModel("Comedy", 18),
                                            YouGenreUiModel("Drama", 13),
                                        ),
                                ),
                            manga =
                                YouMediaStatisticsUiModel(
                                    trackedTitleCount = 92,
                                    completedTitleCount = 17,
                                    genreMetadataTitleCount = 82,
                                    topGenres =
                                        listOf(
                                            YouGenreUiModel("Drama", 39),
                                            YouGenreUiModel("Romance", 28),
                                            YouGenreUiModel("Fantasy", 21),
                                        ),
                                ),
                            artworkCandidates = emptyList(),
                        ),
                    activity =
                        YouActivityUiState.Content(
                            fromDate = LocalDate(2026, 7, 27),
                            throughDate = LocalDate(2026, 9, 18),
                            weeks =
                                (0..7).map { index ->
                                    val increases = listOf(1L, 3L, 0L, 2L, 1L, 4L, 2L, 3L)[index]
                                    YouActivityWeekUiModel(
                                        weekStart = LocalDate(2026, 7, 27) + DatePeriod(days = index * 7),
                                        actionCount = increases + if (index == 5) 1 else 0,
                                        increaseCount = increases,
                                        decreaseCount = 0,
                                        correctionCount = if (index == 5) 1 else 0,
                                    )
                                },
                        ),
                    selectedStatisticsType = MediaType.ANIME,
                    rotationDate = LocalDate(2026, 9, 18),
                ),
            onAction = {},
            onReconnect = {},
            onRetryRefresh = {},
            onResolve = { _, _ -> },
        )
    }
}
