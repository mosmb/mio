package io.morgan.mio.feature.progress.impl

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import io.morgan.mio.core.designsystem.component.MioErrorState
import io.morgan.mio.core.designsystem.theme.MioTheme
import io.morgan.mio.domain.model.CollectionEntryId
import io.morgan.mio.domain.model.OperationId
import io.morgan.mio.feature.progress.api.ProgressConflictKey

@Composable
internal fun ProgressConflictRoute(
    key: ProgressConflictKey,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val operationId = OperationId(key.operationIdValue)
    val entryId = CollectionEntryId(key.entryIdValue)
    val viewModel =
        assistedMetroViewModel<ProgressConflictViewModel, ProgressConflictViewModel.Factory>(
            key = "${entryId.value}:${operationId.value}",
        ) {
            create(operationId, entryId)
        }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.resolved.collect { onBack() } }
    ProgressConflictScreen(
        state = state,
        onBack = onBack,
        onAction = viewModel::onAction,
        modifier = modifier,
    )
}

@Composable
internal fun ProgressConflictScreen(
    state: ProgressConflictUiState,
    onBack: () -> Unit,
    onAction: (ProgressConflictAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val resolveTitle =
        io.morgan.mio.core.ui.title
            .rememberTitleResolver()
    val state =
        if (state is ProgressConflictUiState.Content) {
            state.copy(
                title = resolveTitle(state.title, state.titleNames),
            )
        } else {
            state
        }

    var confirming by remember((state as? ProgressConflictUiState.Content)?.issue) {
        mutableStateOf<ProgressConflictSelection?>(null)
    }
    when (state) {
        ProgressConflictUiState.Loading ->
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        ProgressConflictUiState.Unavailable ->
            MioErrorState(
                title = stringResource(R.string.progress_conflict_unavailable_title),
                message = stringResource(R.string.progress_conflict_unavailable_message),
                retryLabel = stringResource(R.string.progress_editor_back),
                onRetry = onBack,
                modifier = modifier.fillMaxSize(),
            )
        is ProgressConflictUiState.Content -> {
            ProgressConflictContent(state, onBack, { confirming = state.selected }, onAction, modifier)
            val choice = confirming
            if (choice != null) {
                val value = state.choiceLabel(choice == ProgressConflictSelection.KEEP_DEVICE)
                AlertDialog(
                    onDismissRequest = { confirming = null },
                    title = { Text(stringResource(R.string.progress_conflict_confirm_title)) },
                    text = {
                        Text(
                            stringResource(
                                R.string.progress_conflict_confirm_message,
                                value,
                            ),
                        )
                    },
                    confirmButton = {
                        TextButton(
                            colors = ButtonDefaults.textButtonColors(contentColor = MioTheme.colors.text),
                            onClick = {
                                confirming = null
                                onAction(ProgressConflictAction.Confirm)
                            },
                            enabled = !state.saving,
                        ) {
                            Text(stringResource(R.string.progress_conflict_resolve))
                        }
                    },
                    dismissButton = {
                        TextButton(
                            colors =
                                ButtonDefaults.textButtonColors(
                                    contentColor = MioTheme.colors.text,
                                ),
                            onClick = {
                                confirming =
                                    null
                            },
                        ) {
                            Text(stringResource(R.string.progress_editor_cancel))
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun ProgressConflictContent(
    state: ProgressConflictUiState.Content,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    onAction: (ProgressConflictAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TextButton(colors = ButtonDefaults.textButtonColors(contentColor = MioTheme.colors.text), onClick = onBack) {
            Text(stringResource(R.string.progress_editor_cancel))
        }
        Text(stringResource(R.string.progress_conflict_title), style = MioTheme.typography.title)
        Text(
            state.title.ifBlank {
                stringResource(R.string.progress_conflict_saved_title)
            },
            style = MioTheme.typography.section,
        )
        Text(stringResource(R.string.progress_conflict_description), style = MioTheme.typography.body)
        ConflictChoiceRow(
            selected = state.selected == ProgressConflictSelection.KEEP_DEVICE,
            title = stringResource(R.string.progress_conflict_keep_device),
            value = state.choiceLabel(keepDevice = true),
            onClick = { onAction(ProgressConflictAction.Select(ProgressConflictSelection.KEEP_DEVICE)) },
        )
        ConflictChoiceRow(
            selected = state.selected == ProgressConflictSelection.USE_PROVIDER,
            title = stringResource(R.string.progress_conflict_use_provider),
            value = state.choiceLabel(keepDevice = false),
            onClick = { onAction(ProgressConflictAction.Select(ProgressConflictSelection.USE_PROVIDER)) },
        )
        state.error?.let { error ->
            Text(
                text =
                    stringResource(
                        when (error) {
                            ProgressConflictError.CHANGED -> R.string.progress_conflict_changed
                            ProgressConflictError.SAVE_FAILED -> R.string.progress_conflict_save_failed
                        },
                    ),
                style = MioTheme.typography.caption,
                color = MioTheme.colors.error,
            )
        }
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth(), enabled = state.canConfirm) {
            Text(stringResource(R.string.progress_conflict_continue))
        }
        TextButton(
            colors = ButtonDefaults.textButtonColors(contentColor = MioTheme.colors.text),
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.progress_editor_cancel))
        }
    }
}

@Composable
private fun ConflictChoiceRow(
    selected: Boolean,
    title: String,
    value: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MioTheme.colors.surface,
        border = BorderStroke(1.dp, if (selected) MioTheme.colors.accent else MioTheme.colors.border),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Column(modifier = Modifier.padding(start = 8.dp)) {
                Text(title, style = MioTheme.typography.bodyStrong)
                Text(
                    value,
                    style = MioTheme.typography.caption,
                    color = MioTheme.colors.textMuted,
                )
            }
        }
    }
}

@Composable
private fun ProgressConflictUnit.displayName(): String =
    stringResource(
        when (this) {
            ProgressConflictUnit.EPISODE -> R.string.progress_unit_episodes
            ProgressConflictUnit.CHAPTER -> R.string.progress_unit_chapters
            ProgressConflictUnit.VOLUME -> R.string.progress_unit_volumes
        },
    )

@Composable
private fun io.morgan.mio.domain.collection.CollectionStatus.displayName(): String =
    stringResource(
        when (this) {
            io.morgan.mio.domain.collection.CollectionStatus.CURRENT -> R.string.progress_status_current
            io.morgan.mio.domain.collection.CollectionStatus.PLANNED -> R.string.progress_status_planned
            io.morgan.mio.domain.collection.CollectionStatus.PAUSED -> R.string.progress_status_paused
            io.morgan.mio.domain.collection.CollectionStatus.COMPLETED -> R.string.progress_status_completed
            io.morgan.mio.domain.collection.CollectionStatus.DROPPED -> R.string.progress_status_dropped
            io.morgan.mio.domain.collection.CollectionStatus.REPEATING -> R.string.progress_status_repeating
        },
    )

@Composable
private fun ProgressConflictUiState.Content.choiceLabel(keepDevice: Boolean): String {
    if (issue.base is io.morgan.mio.domain.sync.OperationValue.Membership &&
        keepDevice &&
        !issue.localPresent
    ) {
        return stringResource(R.string.progress_conflict_not_in_library)
    }
    val snapshot = if (keepDevice) issue.local else requireNotNull(issue.remote)
    if (issue.base is io.morgan.mio.domain.sync.OperationValue.Status) return snapshot.status.displayName()
    val progress = stringResource(R.string.progress_conflict_value, snapshot.progress.value, unit.displayName())
    return if (issue.local.status != issue.remote?.status) {
        stringResource(R.string.progress_conflict_value_and_status, progress, snapshot.status.displayName())
    } else {
        progress
    }
}
