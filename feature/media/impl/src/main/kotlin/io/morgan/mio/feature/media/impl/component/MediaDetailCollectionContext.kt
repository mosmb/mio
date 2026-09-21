package io.morgan.mio.feature.media.impl.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.morgan.mio.core.designsystem.component.MioBottomSheet
import io.morgan.mio.core.designsystem.component.MioButton
import io.morgan.mio.core.designsystem.component.MioIcon
import io.morgan.mio.core.designsystem.component.MioStatusRow
import io.morgan.mio.core.designsystem.theme.MioTheme
import io.morgan.mio.core.ui.progress.MioAchievementStamp
import io.morgan.mio.core.ui.progress.MioProgressStepper
import io.morgan.mio.core.ui.progress.MioProgressValue
import io.morgan.mio.feature.media.impl.MediaDetailCollectionStatus
import io.morgan.mio.feature.media.impl.MediaDetailCollectionUiModel
import io.morgan.mio.feature.media.impl.MediaDetailMediaType
import io.morgan.mio.feature.media.impl.MediaDetailProgressUnit
import io.morgan.mio.feature.media.impl.R

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MediaDetailCollectionContext(
    collection: MediaDetailCollectionUiModel?,
    modifier: Modifier = Modifier,
    title: String = "",
    completionId: String? = null,
    mediaType: MediaDetailMediaType = MediaDetailMediaType.ANIME,
    writableStatuses: List<MediaDetailCollectionStatus> =
        MediaDetailCollectionStatus.entries.filterNot { status -> status == MediaDetailCollectionStatus.REPEATING },
    onDecrease: () -> Unit = {},
    onIncrease: () -> Unit = {},
    onChangeStatus: (MediaDetailCollectionStatus) -> Unit = {},
    onInteractionChanged: (Boolean) -> Unit = {},
    onMotionChanged: (Long, Boolean) -> Unit = { _, _ -> },
) {
    val sourceBounds = remember { mutableStateOf<Rect?>(null) }
    val statusBounds = remember { mutableStateOf<Rect?>(null) }
    val largeText = LocalDensity.current.fontScale > 1.3f
    val colors = MioTheme.colors
    // Lime needs a darker shade when used as foreground artwork on a light surface.
    val activeIconTint =
        if (colors.surface.luminance() > 0.5f) lerp(colors.accent, colors.onAccent, 0.5f) else colors.accent
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
    Surface(
        modifier = modifier.fillMaxWidth().padding(12.dp),
        shape = RoundedCornerShape(22.dp),
        color = MioTheme.colors.surface,
        border = BorderStroke(1.dp, MioTheme.colors.borderStrong),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (collection != null) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    itemVerticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = if (largeText) Modifier.fillMaxWidth() else Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        if (collection.status == MediaDetailCollectionStatus.CURRENT ||
                            collection.status == MediaDetailCollectionStatus.REPEATING
                        ) {
                            MioIcon(
                                iconRes =
                                    if (mediaType == MediaDetailMediaType.MANGA) {
                                        R.drawable.media_detail_menu_book
                                    } else {
                                        R.drawable.media_detail_tv
                                    },
                                contentDescription = null,
                                tint = activeIconTint,
                            )
                        }
                        MioAchievementStamp(
                            completed = collection.status == MediaDetailCollectionStatus.COMPLETED,
                            label = collection.status.statusLabel(mediaType),
                            completionId = completionId,
                            sourceBounds = sourceBounds.value ?: statusBounds.value,
                        )
                    }
                    OutlinedButton(
                        onClick = { statusSheetVisible.value = true },
                        modifier = Modifier.onGloballyPositioned { statusBounds.value = it.boundsInWindow() },
                        shape = MioTheme.shapes.md,
                        border = BorderStroke(1.dp, MioTheme.colors.text),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MioTheme.colors.text),
                    ) {
                        Text(stringResource(R.string.media_detail_change_status))
                    }
                }
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    itemVerticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    MioProgressValue(
                        value = collection.progress,
                        total = collection.total,
                        description = collection.progressDescription(),
                        onMotionChanged = reportMotion,
                        modifier = if (largeText) Modifier.fillMaxWidth() else Modifier.weight(1f),
                    )
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
                            modifier = Modifier.onGloballyPositioned { sourceBounds.value = it.boundsInWindow() },
                        )
                    }
                }
            } else {
                MioButton(
                    text = stringResource(R.string.media_detail_add_to_library),
                    modifier =
                        Modifier.fillMaxWidth().onGloballyPositioned {
                            statusBounds.value = it.boundsInWindow()
                        },
                    onClick = { statusSheetVisible.value = true },
                )
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
                        sourceBounds.value = statusBounds.value
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
