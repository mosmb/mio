package io.morgan.mio.feature.today.impl.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.morgan.mio.core.designsystem.accessibility.mioDecorative
import io.morgan.mio.core.designsystem.theme.MioTheme
import io.morgan.mio.core.ui.image.MioArtwork
import io.morgan.mio.core.ui.progress.MioProgressStepper
import io.morgan.mio.core.ui.progress.MioProgressValue
import io.morgan.mio.feature.today.impl.R
import io.morgan.mio.feature.today.impl.TodayCollectionStatus
import io.morgan.mio.feature.today.impl.TodayMediaType
import io.morgan.mio.feature.today.impl.TodayNewUiModel
import io.morgan.mio.feature.today.impl.TodayProgressUnit
import io.morgan.mio.feature.today.impl.TodayRelativeTime
import io.morgan.mio.feature.today.impl.TodayRelativeTimeUnit

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TodayNewRow(
    recommendation: TodayNewUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onDecrease: () -> Unit = {},
    onIncrease: () -> Unit = {},
    onInteractionChanged: (Boolean) -> Unit = {},
    onMotionChanged: (Long, Boolean) -> Unit = { _, _ -> },
) {
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
    val actionLabel = stringResource(R.string.today_open_details, recommendation.title)
    FlowRow(
        modifier =
            modifier
                .observeTodayPointer(onInteractionChanged)
                .fillMaxWidth()
                .heightIn(min = 104.dp)
                .clip(MioTheme.shapes.sm)
                .padding(horizontal = 12.dp, vertical = 4.dp),
        maxItemsInEachRow = if (largeText) 1 else Int.MAX_VALUE,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MioArtwork(
            reference = recommendation.posterReference,
            contentDescription = null,
            modifier = Modifier.size(width = 72.dp, height = 96.dp).clip(MioTheme.shapes.sm).mioDecorative(),
        )
        Column(
            modifier =
                Modifier
                    .then(if (largeText) Modifier.fillMaxWidth() else Modifier.weight(1f))
                    .clickable(
                        role = Role.Button,
                        onClickLabel = actionLabel,
                        onClick = onClick,
                    ).semantics(mergeDescendants = true) {},
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = stringResource(recommendation.mediaType.labelRes),
                style = MioTheme.typography.caption,
                color = MioTheme.colors.textMuted,
            )
            Text(
                text = recommendation.title,
                style = MioTheme.typography.bodyStrong,
            )
            val claimHeight = remember(recommendation.mediaId) { mutableIntStateOf(0) }
            Column(
                modifier =
                    Modifier
                        .heightIn(min = with(LocalDensity.current) { claimHeight.intValue.toDp() })
                        .onSizeChanged { claimHeight.intValue = maxOf(claimHeight.intValue, it.height) },
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                recommendation.releaseAge?.let { age ->
                    Text(
                        text = age.recencyText(),
                        style = MioTheme.typography.caption,
                        color = MioTheme.colors.textMuted,
                    )
                }
                recommendation.nextUnit?.let { nextUnit ->
                    Text(
                        text = stringResource(recommendation.progressUnit.nextUnitLabelRes, nextUnit),
                        style = MioTheme.typography.body,
                    )
                }
                recommendation.availableUnitCount?.let { availableCount ->
                    Text(
                        text =
                            pluralStringResource(
                                recommendation.progressUnit.availableUnitsRes,
                                availableCount,
                                availableCount,
                            ),
                        style = MioTheme.typography.caption,
                    )
                }
            }
            if (recommendation.status != TodayCollectionStatus.CURRENT) {
                Text(
                    text = recommendation.status.displayName(),
                    style = MioTheme.typography.bodyStrong,
                )
            }
            MioProgressValue(
                value = recommendation.progress,
                total = recommendation.total,
                description = recommendation.progressLabel(),
                onMotionChanged = reportMotion,
            )
        }
        recommendation.controls?.let { controls ->
            MioProgressStepper(
                decreaseDescription =
                    stringResource(
                        recommendation.progressUnit.decreaseDescriptionRes,
                        recommendation.progress.coerceAtLeast(1) - 1,
                    ),
                increaseDescription =
                    stringResource(
                        recommendation.progressUnit.increaseDescriptionRes,
                        recommendation.progress.takeIf { value -> value < Int.MAX_VALUE }?.plus(1)
                            ?: recommendation.progress,
                    ),
                decreaseEnabled = controls.decreaseEnabled,
                increaseEnabled = controls.increaseEnabled,
                onDecrease = onDecrease,
                onIncrease = onIncrease,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

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
private fun TodayNewUiModel.progressLabel(): String =
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

@Composable
private fun TodayRelativeTime.recencyText(): String =
    when (unit) {
        TodayRelativeTimeUnit.UNDER_HOUR -> stringResource(R.string.today_new_under_hour)
        TodayRelativeTimeUnit.HOURS ->
            pluralStringResource(R.plurals.today_new_hours, amount, amount)
        TodayRelativeTimeUnit.DAYS ->
            pluralStringResource(R.plurals.today_new_days, amount, amount)
    }
