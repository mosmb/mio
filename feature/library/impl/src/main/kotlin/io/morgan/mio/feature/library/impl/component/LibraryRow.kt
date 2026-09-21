package io.morgan.mio.feature.library.impl.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.morgan.mio.core.designsystem.component.MioStepButton
import io.morgan.mio.core.designsystem.icon.MioIcons
import io.morgan.mio.core.designsystem.theme.MioTheme
import io.morgan.mio.core.ui.image.MioArtwork
import io.morgan.mio.core.ui.progress.MioAchievementStamp
import io.morgan.mio.feature.library.impl.LibraryProgressUnit
import io.morgan.mio.feature.library.impl.LibraryRowMediaType
import io.morgan.mio.feature.library.impl.LibraryRowStatus
import io.morgan.mio.feature.library.impl.LibraryRowUiModel
import io.morgan.mio.feature.library.impl.R
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop

@Composable
internal fun LibraryRow(
    row: LibraryRowUiModel,
    onClick: () -> Unit,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    modifier: Modifier = Modifier,
    completionId: String? = null,
    onInteractionChanged: (Boolean) -> Unit = {},
) {
    val sourceBounds = remember { mutableStateOf<Rect?>(null) }
    val title = row.title.ifBlank { stringResource(R.string.library_unavailable_title) }
    val mediaType =
        when (row.mediaType) {
            LibraryRowMediaType.ANIME -> stringResource(R.string.library_filter_anime)
            LibraryRowMediaType.MANGA -> stringResource(R.string.library_filter_manga)
            null -> stringResource(R.string.library_media_type_unavailable)
        }
    val status =
        when (row.status) {
            LibraryRowStatus.CURRENT -> stringResource(R.string.library_status_current)
            LibraryRowStatus.PLANNED -> stringResource(R.string.library_status_planning)
            LibraryRowStatus.PAUSED -> stringResource(R.string.library_status_paused)
            LibraryRowStatus.COMPLETED -> stringResource(R.string.library_status_completed)
            LibraryRowStatus.DROPPED -> stringResource(R.string.library_status_dropped)
            LibraryRowStatus.REPEATING -> stringResource(R.string.library_status_repeating)
        }
    val unit =
        when (row.progressUnit) {
            LibraryProgressUnit.EPISODE -> stringResource(R.string.library_unit_episode)
            LibraryProgressUnit.CHAPTER -> stringResource(R.string.library_unit_chapter)
            LibraryProgressUnit.VOLUME -> stringResource(R.string.library_unit_volume)
        }
    val progressText =
        row.total?.let { total ->
            stringResource(R.string.library_progress_with_total, unit, row.progress, total)
        } ?: stringResource(R.string.library_progress_without_total, unit, row.progress)
    val latestInteractionChanged by rememberUpdatedState(onInteractionChanged)
    val bodyInteractionSource = remember { MutableInteractionSource() }
    val decreaseInteractionSource = remember { MutableInteractionSource() }
    val increaseInteractionSource = remember { MutableInteractionSource() }
    val decreasePressed = decreaseInteractionSource.collectIsPressedAsState()
    val increasePressed = increaseInteractionSource.collectIsPressedAsState()
    val bodyPressed = bodyInteractionSource.collectIsPressedAsState()
    LaunchedEffect(bodyInteractionSource, decreaseInteractionSource, increaseInteractionSource) {
        snapshotFlow { bodyPressed.value || decreasePressed.value || increasePressed.value }
            .distinctUntilChanged()
            .drop(1)
            .collect { latestInteractionChanged(it) }
    }
    DisposableEffect(Unit) {
        onDispose { latestInteractionChanged(false) }
    }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 136.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier =
                Modifier
                    .weight(1f)
                    .heightIn(min = 136.dp)
                    .clickable(interactionSource = bodyInteractionSource, role = Role.Button, onClick = onClick),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MioArtwork(
                reference = row.posterReference,
                contentDescription = null,
                modifier = Modifier.size(width = 84.dp, height = 120.dp).clip(RoundedCornerShape(12.dp)),
            )
            Column(
                modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = title,
                    style = MioTheme.typography.bodyStrong,
                    maxLines = 2,
                )
                MioAchievementStamp(
                    completed = row.status == LibraryRowStatus.COMPLETED,
                    label =
                        if (row.status ==
                            LibraryRowStatus.COMPLETED
                        ) {
                            status
                        } else {
                            stringResource(R.string.library_row_type_and_status, mediaType, status)
                        },
                    completionId = completionId,
                    sourceBounds = sourceBounds.value,
                    labelStyle = MioTheme.typography.caption,
                    labelColor = MioTheme.colors.textMuted,
                )
                Text(
                    text = progressText,
                    style = MioTheme.typography.body,
                    maxLines = 1,
                )
            }
        }
        row.controls?.let { controls ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MioStepButton(
                    iconRes = MioIcons.Remove,
                    contentDescription =
                        if (controls.decreaseEnabled) {
                            stringResource(R.string.library_decrease_progress, unit.lowercase(), row.progress - 1)
                        } else {
                            stringResource(R.string.library_decrease_unavailable, unit.lowercase())
                        },
                    enabled = controls.decreaseEnabled,
                    accent = false,
                    onClick = onDecrease,
                    interactionSource = decreaseInteractionSource,
                )
                MioStepButton(
                    iconRes = MioIcons.Add,
                    contentDescription =
                        if (controls.increaseEnabled) {
                            stringResource(R.string.library_increase_progress, unit.lowercase(), row.progress + 1)
                        } else {
                            stringResource(R.string.library_increase_unavailable, unit.lowercase())
                        },
                    enabled = controls.increaseEnabled,
                    accent = true,
                    onClick = onIncrease,
                    interactionSource = increaseInteractionSource,
                    modifier = Modifier.onGloballyPositioned { sourceBounds.value = it.boundsInWindow() },
                )
            }
        }
    }
}
