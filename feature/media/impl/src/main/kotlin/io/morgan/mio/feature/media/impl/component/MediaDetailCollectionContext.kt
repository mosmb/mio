package io.morgan.mio.feature.media.impl.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.morgan.mio.core.designsystem.component.MioBottomSheet
import io.morgan.mio.core.designsystem.component.MioStatusRow
import io.morgan.mio.core.designsystem.theme.MioTheme
import io.morgan.mio.core.ui.progress.MioProgressStepper
import io.morgan.mio.core.ui.progress.MioProgressValue
import io.morgan.mio.feature.media.impl.MediaDetailCollectionStatus
import io.morgan.mio.feature.media.impl.MediaDetailCollectionUiModel
import io.morgan.mio.feature.media.impl.MediaDetailMediaType
import io.morgan.mio.feature.media.impl.MediaDetailProgressUnit
import io.morgan.mio.feature.media.impl.R

@Composable
internal fun MediaDetailCollectionContext(
    collection: MediaDetailCollectionUiModel?,
    modifier: Modifier = Modifier,
    title: String = "",
    mediaType: MediaDetailMediaType = MediaDetailMediaType.ANIME,
    writableStatuses: List<MediaDetailCollectionStatus> =
        MediaDetailCollectionStatus.entries.filterNot { status -> status == MediaDetailCollectionStatus.REPEATING },
    onDecrease: () -> Unit = {},
    onIncrease: () -> Unit = {},
    onEditProgress: () -> Unit = {},
    onChangeStatus: (MediaDetailCollectionStatus) -> Unit = {},
    onInteractionChanged: (Boolean) -> Unit = {},
    onMotionChanged: (Long, Boolean) -> Unit = { _, _ -> },
) {
    val motionGeneration = remember { mutableLongStateOf(0L) }
    val motionActive = remember { mutableStateOf(false) }
    val statusSheetVisible = remember { mutableStateOf(false) }
    val reportMotion: (Boolean) -> Unit = { active ->
        if (active) {
            if (!motionActive.value) {
                motionGeneration.longValue += 1L
                motionActive.value = true
            }
            onMotionChanged(motionGeneration.longValue, true)
        } else if (motionActive.value) {
            motionActive.value = false
            onMotionChanged(motionGeneration.longValue, false)
        }
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MioTheme.spacing.space2),
    ) {
        Text(
            text =
                if (collection == null) {
                    stringResource(R.string.media_detail_not_in_library)
                } else {
                    stringResource(R.string.media_detail_in_library)
                },
            style = MioTheme.typography.section,
            modifier = Modifier.semantics { heading() },
        )
        if (collection != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MioTheme.spacing.space3),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(MioTheme.spacing.space2),
                ) {
                    Text(
                        text = stringResource(R.string.media_detail_status, collection.status.statusLabel(mediaType)),
                        style = MioTheme.typography.body,
                    )
                    MioProgressValue(
                        value = collection.progress,
                        total = collection.total,
                        description = collection.progressDescription(),
                        onMotionChanged = reportMotion,
                    )
                }
                collection.controls?.let { controls ->
                    MioProgressStepper(
                        decreaseDescription =
                            stringResource(
                                collection.progressUnit.decreaseDescriptionRes,
                                collection.progress.coerceAtLeast(1) - 1,
                            ),
                        increaseDescription =
                            stringResource(
                                collection.progressUnit.increaseDescriptionRes,
                                collection.progress.takeIf { value -> value < Int.MAX_VALUE }?.plus(1)
                                    ?: collection.progress,
                            ),
                        decreaseEnabled = controls.decreaseEnabled,
                        increaseEnabled = controls.increaseEnabled,
                        onDecrease = onDecrease,
                        onIncrease = onIncrease,
                        onInteractionChanged = onInteractionChanged,
                    )
                }
            }
            if (collection.status == MediaDetailCollectionStatus.CURRENT ||
                collection.status == MediaDetailCollectionStatus.COMPLETED
            ) {
                TextButton(
                    colors = ButtonDefaults.textButtonColors(contentColor = MioTheme.colors.text),
                    onClick = onEditProgress,
                ) {
                    Text(stringResource(R.string.media_detail_edit_progress))
                }
            }
            TextButton(colors = ButtonDefaults.textButtonColors(contentColor = MioTheme.colors.text), onClick = {
                statusSheetVisible.value =
                    true
            }) {
                Text(stringResource(R.string.media_detail_change_status))
            }
        } else {
            Button(modifier = Modifier.fillMaxWidth(), onClick = { statusSheetVisible.value = true }) {
                Text(stringResource(R.string.media_detail_add_to_library))
            }
        }
    }
    MioBottomSheet(
        visible = statusSheetVisible.value,
        onDismissRequest = { statusSheetVisible.value = false },
        title = stringResource(R.string.media_detail_status_sheet_title),
        subtitle = title,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            writableStatuses.forEach { status ->
                MioStatusRow(
                    id = status.name,
                    label = status.statusLabel(mediaType),
                    selected = collection?.status == status,
                    onSelected = {
                        statusSheetVisible.value = false
                        if (collection?.status != status) onChangeStatus(status)
                    },
                )
            }
        }
    }
}

@Composable
private fun MediaDetailCollectionStatus.statusLabel(mediaType: MediaDetailMediaType): String =
    when (this) {
        MediaDetailCollectionStatus.CURRENT ->
            stringResource(
                if (mediaType == MediaDetailMediaType.ANIME) {
                    R.string.media_detail_collection_watching
                } else {
                    R.string.media_detail_collection_reading
                },
            )
        MediaDetailCollectionStatus.PLANNED ->
            stringResource(
                if (mediaType == MediaDetailMediaType.ANIME) {
                    R.string.media_detail_collection_plan_to_watch
                } else {
                    R.string.media_detail_collection_plan_to_read
                },
            )
        MediaDetailCollectionStatus.PAUSED -> stringResource(R.string.media_detail_collection_paused)
        MediaDetailCollectionStatus.COMPLETED -> stringResource(R.string.media_detail_collection_completed)
        MediaDetailCollectionStatus.DROPPED -> stringResource(R.string.media_detail_collection_dropped)
        MediaDetailCollectionStatus.REPEATING ->
            stringResource(
                if (mediaType == MediaDetailMediaType.ANIME) {
                    R.string.media_detail_collection_rewatching
                } else {
                    R.string.media_detail_collection_rereading
                },
            )
    }

@Composable
private fun MediaDetailProgressUnit.displayName(): String =
    when (this) {
        MediaDetailProgressUnit.EPISODE -> stringResource(R.string.media_detail_unit_episodes)
        MediaDetailProgressUnit.CHAPTER -> stringResource(R.string.media_detail_unit_chapters)
        MediaDetailProgressUnit.VOLUME -> stringResource(R.string.media_detail_unit_volumes)
    }

@Composable
private fun MediaDetailCollectionUiModel.progressDescription(): String =
    stringResource(
        R.string.media_detail_progress,
        progress,
        total ?: "—",
        progressUnit.displayName(),
    )

private val MediaDetailProgressUnit.decreaseDescriptionRes: Int
    get() =
        when (this) {
            MediaDetailProgressUnit.EPISODE -> R.string.media_detail_decrease_episode
            MediaDetailProgressUnit.CHAPTER -> R.string.media_detail_decrease_chapter
            MediaDetailProgressUnit.VOLUME -> R.string.media_detail_decrease_volume
        }

private val MediaDetailProgressUnit.increaseDescriptionRes: Int
    get() =
        when (this) {
            MediaDetailProgressUnit.EPISODE -> R.string.media_detail_increase_episode
            MediaDetailProgressUnit.CHAPTER -> R.string.media_detail_increase_chapter
            MediaDetailProgressUnit.VOLUME -> R.string.media_detail_increase_volume
        }
