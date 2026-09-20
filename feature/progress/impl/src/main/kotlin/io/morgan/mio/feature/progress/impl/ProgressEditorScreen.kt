package io.morgan.mio.feature.progress.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import io.morgan.mio.core.designsystem.component.MioErrorState
import io.morgan.mio.core.designsystem.theme.MioTheme
import io.morgan.mio.domain.model.MediaId
import io.morgan.mio.feature.progress.api.ProgressEditorKey

@Composable
internal fun ProgressEditorRoute(
    key: ProgressEditorKey,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mediaId = MediaId(key.mediaIdValue)
    val viewModel =
        assistedMetroViewModel<ProgressEditorViewModel, ProgressEditorViewModel.Factory>(key = mediaId.value) {
            create(mediaId)
        }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.saved.collect { onBack() } }
    ProgressEditorScreen(
        state = state,
        onBack = onBack,
        onAction = viewModel::onAction,
        modifier = modifier,
    )
}

@Composable
internal fun ProgressEditorScreen(
    state: ProgressEditorUiState,
    onBack: () -> Unit,
    onAction: (ProgressEditorAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val resolveTitle =
        io.morgan.mio.core.ui.title
            .rememberTitleResolver()
    val state =
        if (state is ProgressEditorUiState.Content) {
            state.copy(
                title = resolveTitle(state.title, state.titleNames),
            )
        } else {
            state
        }

    when (state) {
        ProgressEditorUiState.Loading ->
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        ProgressEditorUiState.Missing ->
            MioErrorState(
                title = stringResource(R.string.progress_editor_missing_title),
                message = stringResource(R.string.progress_editor_missing_message),
                retryLabel = stringResource(R.string.progress_editor_back),
                onRetry = onBack,
                modifier = modifier.fillMaxSize(),
            )
        is ProgressEditorUiState.Content ->
            ProgressEditorContent(state, onBack, onAction, modifier)
    }
}

@Composable
private fun ProgressEditorContent(
    state: ProgressEditorUiState.Content,
    onBack: () -> Unit,
    onAction: (ProgressEditorAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    Column(
        modifier = modifier.fillMaxSize().imePadding().padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TextButton(
            colors = ButtonDefaults.textButtonColors(contentColor = MioTheme.colors.text),
            onClick = onBack,
            enabled = !state.saving,
        ) {
            Text(stringResource(R.string.progress_editor_cancel))
        }
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(state.title, style = MioTheme.typography.title)
            Text(
                text =
                    stringResource(
                        R.string.progress_editor_current,
                        state.currentProgress,
                        state.unit.displayName(),
                    ),
                style = MioTheme.typography.body,
            )
            Text(
                text =
                    state.total?.let { total ->
                        stringResource(R.string.progress_editor_limit, total, state.unit.displayName())
                    } ?: stringResource(R.string.progress_editor_unknown_limit),
                style = MioTheme.typography.caption,
                color = MioTheme.colors.textMuted,
            )
            OutlinedTextField(
                value = state.targetText,
                onValueChange = { value -> onAction(ProgressEditorAction.TargetChanged(value)) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.saving,
                label = { Text(stringResource(R.string.progress_editor_value_label, state.unit.displayName())) },
                singleLine = true,
                isError = state.error != null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions =
                    KeyboardActions(onDone = {
                        focusManager.clearFocus()
                        if (state.canSave) onAction(ProgressEditorAction.Save)
                    }),
            )
            state.error?.let { error ->
                Text(
                    text = error.message(state.total, state.unit),
                    style = MioTheme.typography.caption,
                    color = MioTheme.colors.error,
                )
            }
        }
        Button(
            onClick = { onAction(ProgressEditorAction.Save) },
            modifier = Modifier.fillMaxWidth(),
            enabled = state.canSave,
        ) {
            if (state.saving) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(8.dp))
            }
            Text(stringResource(R.string.progress_editor_save))
        }
    }
}

@Composable
private fun ProgressEditorUnit.displayName(): String =
    stringResource(
        when (this) {
            ProgressEditorUnit.EPISODE -> R.string.progress_unit_episodes
            ProgressEditorUnit.CHAPTER -> R.string.progress_unit_chapters
            ProgressEditorUnit.VOLUME -> R.string.progress_unit_volumes
        },
    )

@Composable
private fun ProgressEditorError.message(
    total: Int?,
    unit: ProgressEditorUnit,
): String =
    when (this) {
        ProgressEditorError.INVALID_VALUE -> stringResource(R.string.progress_editor_invalid)
        ProgressEditorError.ABOVE_TOTAL ->
            stringResource(R.string.progress_editor_above_total, total ?: 0, unit.displayName())
        ProgressEditorError.UNCHANGED -> stringResource(R.string.progress_editor_unchanged)
        ProgressEditorError.INELIGIBLE -> stringResource(R.string.progress_editor_ineligible)
        ProgressEditorError.STALE -> stringResource(R.string.progress_editor_stale)
        ProgressEditorError.SAVE_FAILED -> stringResource(R.string.progress_editor_save_failed)
    }
