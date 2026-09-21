package io.morgan.mio.feature.today.impl.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeightIn
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.morgan.mio.core.designsystem.accessibility.mioDecorative
import io.morgan.mio.core.designsystem.theme.MioTheme
import io.morgan.mio.core.ui.image.MioArtwork
import io.morgan.mio.core.ui.progress.MioAchievementStamp
import io.morgan.mio.core.ui.progress.MioProgressStepper
import io.morgan.mio.core.ui.progress.MioProgressValue
import io.morgan.mio.feature.today.impl.R
import io.morgan.mio.feature.today.impl.TodayCollectionStatus
import io.morgan.mio.feature.today.impl.TodayMediaType
import io.morgan.mio.feature.today.impl.TodayNowUiModel
import io.morgan.mio.feature.today.impl.TodayProgressUnit

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TodayNowCard(
    now: TodayNowUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    completionId: String? = null,
    onCompletionFinished: (String) -> Unit = {},
    onDecrease: () -> Unit = {},
    onIncrease: () -> Unit = {},
    onInteractionChanged: (Boolean) -> Unit = {},
    onMotionChanged: (Long, Boolean) -> Unit = { _, _ -> },
) {
    val sourceBounds = remember { mutableStateOf<Rect?>(null) }
    val largeText = LocalDensity.current.fontScale > 1.3f
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
    val actionLabel = stringResource(R.string.today_open_details, now.title)
    val cardShape = MioTheme.shapes.lg
    Box(
        modifier =
            modifier
                .observeTodayPointer(onInteractionChanged)
                .fillMaxWidth()
                .requiredHeightIn(min = 332.dp)
                .clip(cardShape)
                .clickable(role = Role.Button, onClickLabel = actionLabel, onClick = onClick),
    ) {
        MioArtwork(
            reference = now.bannerReference ?: now.posterReference,
            contentDescription = null,
            modifier = Modifier.mioDecorative().matchParentSize(),
        )
        Box(
            modifier =
                Modifier
                    .background(todayNowGradient)
                    .matchParentSize(),
        )
        FlowRow(
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(20.dp),
            maxItemsInEachRow = if (largeText) 1 else Int.MAX_VALUE,
        ) {
            Column(
                modifier =
                    Modifier
                        .then(if (largeText) Modifier.fillMaxWidth() else Modifier.weight(1f)),
            ) {
                Text(
                    text = stringResource(now.mediaType.labelRes),
                    style = MioTheme.typography.caption,
                    color = Color.White.copy(alpha = 0.86f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = now.title,
                    style = MioTheme.typography.cardTitle,
                    color = Color.White,
                    modifier = Modifier.padding(top = 8.dp),
                )
                // Removing an expired claim must not move the bottom-aligned title or controls.
                // Reserve its measured space until this card displays a different media identity.
                val claimHeight = remember(now.mediaId) { mutableIntStateOf(0) }
                Column(
                    modifier =
                        Modifier
                            .heightIn(min = with(LocalDensity.current) { claimHeight.intValue.toDp() })
                            .onSizeChanged { claimHeight.intValue = maxOf(claimHeight.intValue, it.height) },
                ) {
                    now.nextUnit?.let { nextUnit ->
                        Text(
                            text = stringResource(now.progressUnit.nextUnitLabelRes, nextUnit),
                            style = MioTheme.typography.bodyStrong,
                            color = Color.White,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    now.availableUnitCount?.takeIf { count -> count > 0 }?.let { availableCount ->
                        Text(
                            text =
                                pluralStringResource(
                                    now.progressUnit.availableUnitsRes,
                                    availableCount,
                                    availableCount,
                                ),
                            style = MioTheme.typography.body,
                            color = Color.White,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    MioAchievementStamp(
                        completed = now.status == TodayCollectionStatus.COMPLETED,
                        label = if (now.status == TodayCollectionStatus.CURRENT) "" else now.status.displayName(),
                        completionId = completionId,
                        sourceBounds = sourceBounds.value,
                        labelColor = Color.White,
                        onFinished = onCompletionFinished,
                    )
                }

                CompositionLocalProvider(LocalContentColor provides Color.White) {
                    MioProgressValue(
                        value = now.progress,
                        total = now.total,
                        description = now.progressLabel(),
                        onMotionChanged = reportMotion,
                    )
                }
            }
            now.controls?.let { controls ->
                MioProgressStepper(
                    decreaseDescription =
                        stringResource(
                            now.progressUnit.decreaseDescriptionRes,
                            now.progress.coerceAtLeast(1) - 1,
                        ),
                    increaseDescription =
                        stringResource(
                            now.progressUnit.increaseDescriptionRes,
                            now.progress.takeIf { value -> value < Int.MAX_VALUE }?.plus(1) ?: now.progress,
                        ),
                    decreaseEnabled = controls.decreaseEnabled,
                    increaseEnabled = controls.increaseEnabled,
                    onDecrease = onDecrease,
                    onIncrease = onIncrease,
                    modifier =
                        Modifier
                            .onGloballyPositioned { sourceBounds.value = it.boundsInWindow() }
                            .align(
                                Alignment.Bottom,
                            ).padding(
                                start = if (largeText) 0.dp else 8.dp,
                                top = if (largeText) 12.dp else 0.dp,
                            ),
                )
            }
        }
    }
}

private val todayNowGradient =
    Brush.verticalGradient(
        colorStops =
            arrayOf(
                0f to Color.Transparent,
                0.44f to Color.Black.copy(alpha = TODAY_NOW_LOWER_SCRIM_ALPHA),
                1f to Color.Black,
            ),
    )

internal const val TODAY_NOW_LOWER_SCRIM_ALPHA: Float = 0.82f

private val TodayMediaType.labelRes: Int
    get() =
        when (this) {
            TodayMediaType.ANIME -> R.string.today_media_type_anime
            TodayMediaType.MANGA -> R.string.today_media_type_manga
        }

private val TodayProgressUnit.nextUnitLabelRes: Int
    get() =
        when (this) {
            TodayProgressUnit.EPISODE -> R.string.today_next_episode
            TodayProgressUnit.CHAPTER -> R.string.today_next_chapter
            TodayProgressUnit.VOLUME -> R.string.today_next_volume
        }

private val TodayProgressUnit.availableUnitsRes: Int
    get() =
        when (this) {
            TodayProgressUnit.EPISODE -> R.plurals.today_available_episodes
            TodayProgressUnit.CHAPTER -> R.plurals.today_available_chapters
            TodayProgressUnit.VOLUME -> R.plurals.today_available_volumes
        }

private val TodayProgressUnit.decreaseDescriptionRes: Int
    get() =
        when (this) {
            TodayProgressUnit.EPISODE -> R.string.today_decrease_episode
            TodayProgressUnit.CHAPTER -> R.string.today_decrease_chapter
            TodayProgressUnit.VOLUME -> R.string.today_decrease_volume
        }

private val TodayProgressUnit.increaseDescriptionRes: Int
    get() =
        when (this) {
            TodayProgressUnit.EPISODE -> R.string.today_increase_episode
            TodayProgressUnit.CHAPTER -> R.string.today_increase_chapter
            TodayProgressUnit.VOLUME -> R.string.today_increase_volume
        }

@Composable
private fun TodayCollectionStatus.displayName(): String =
    when (this) {
        TodayCollectionStatus.CURRENT -> stringResource(R.string.today_status_current)
        TodayCollectionStatus.PLANNED -> stringResource(R.string.today_status_planned)
        TodayCollectionStatus.PAUSED -> stringResource(R.string.today_status_paused)
        TodayCollectionStatus.COMPLETED -> stringResource(R.string.today_status_completed)
        TodayCollectionStatus.DROPPED -> stringResource(R.string.today_status_dropped)
        TodayCollectionStatus.REPEATING -> stringResource(R.string.today_status_repeating)
    }

@Composable
private fun TodayNowUiModel.progressLabel(): String =
    if (total == null) {
        stringResource(progressUnit.progressUnknownLabelRes, progress)
    } else {
        stringResource(progressUnit.progressKnownLabelRes, progress, total)
    }

private val TodayProgressUnit.progressKnownLabelRes: Int
    get() =
        when (this) {
            TodayProgressUnit.EPISODE -> R.string.today_progress_episode_known
            TodayProgressUnit.CHAPTER -> R.string.today_progress_chapter_known
            TodayProgressUnit.VOLUME -> R.string.today_progress_volume_known
        }

private val TodayProgressUnit.progressUnknownLabelRes: Int
    get() =
        when (this) {
            TodayProgressUnit.EPISODE -> R.string.today_progress_episode_unknown
            TodayProgressUnit.CHAPTER -> R.string.today_progress_chapter_unknown
            TodayProgressUnit.VOLUME -> R.string.today_progress_volume_unknown
        }
