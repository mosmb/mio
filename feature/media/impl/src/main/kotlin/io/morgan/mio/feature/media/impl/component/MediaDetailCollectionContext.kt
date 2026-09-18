package io.morgan.mio.feature.media.impl.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import io.morgan.mio.core.designsystem.theme.MioTheme
import io.morgan.mio.core.ui.progress.MioProgressStepper
import io.morgan.mio.core.ui.progress.MioProgressValue
import io.morgan.mio.feature.media.impl.MediaDetailCollectionStatus
import io.morgan.mio.feature.media.impl.MediaDetailCollectionUiModel
import io.morgan.mio.feature.media.impl.MediaDetailProgressUnit
import io.morgan.mio.feature.media.impl.R

@Composable
internal fun MediaDetailCollectionContext(
    collection: MediaDetailCollectionUiModel?,
    modifier: Modifier = Modifier,
    onDecrease: () -> Unit = {},
    onIncrease: () -> Unit = {},
    onEditProgress: () -> Unit = {},
    onInteractionChanged: (Boolean) -> Unit = {},
    onMotionChanged: (Long, Boolean) -> Unit = { _, _ -> },
) {
    val motionGeneration = remember { mutableLongStateOf(0L) }
    val motionActive = remember { mutableStateOf(false) }
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
                        text = stringResource(R.string.media_detail_status, collection.status.displayName()),
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
                TextButton(onClick = onEditProgress) {
                    Text(stringResource(R.string.media_detail_edit_progress))
                }
            }
        }
    }
}

@Composable
private fun MediaDetailCollectionStatus.displayName(): String =
    when (this) {
        MediaDetailCollectionStatus.CURRENT -> stringResource(R.string.media_detail_collection_current)
        MediaDetailCollectionStatus.PLANNED -> stringResource(R.string.media_detail_collection_planned)
        MediaDetailCollectionStatus.PAUSED -> stringResource(R.string.media_detail_collection_paused)
        MediaDetailCollectionStatus.COMPLETED -> stringResource(R.string.media_detail_collection_completed)
        MediaDetailCollectionStatus.DROPPED -> stringResource(R.string.media_detail_collection_dropped)
        MediaDetailCollectionStatus.REPEATING -> stringResource(R.string.media_detail_collection_repeating)
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
