package io.morgan.mio.feature.today.impl.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.morgan.mio.core.designsystem.accessibility.mioDecorative
import io.morgan.mio.core.designsystem.theme.MioTheme
import io.morgan.mio.core.ui.image.MioArtwork
import io.morgan.mio.feature.today.impl.R
import io.morgan.mio.feature.today.impl.TodayMediaType
import io.morgan.mio.feature.today.impl.TodayNewUiModel
import io.morgan.mio.feature.today.impl.TodayProgressUnit
import io.morgan.mio.feature.today.impl.TodayRelativeTime
import io.morgan.mio.feature.today.impl.TodayRelativeTimeUnit

@Composable
internal fun TodayNewRow(
    recommendation: TodayNewUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val actionLabel = stringResource(R.string.today_open_details, recommendation.title)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 104.dp)
                .clip(MioTheme.shapes.sm)
                .clickable(
                    role = Role.Button,
                    onClickLabel = actionLabel,
                    onClick = onClick,
                ).semantics(mergeDescendants = true) {}
                .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MioArtwork(
            reference = recommendation.posterReference,
            contentDescription = null,
            modifier = Modifier.size(width = 72.dp, height = 96.dp).clip(MioTheme.shapes.sm).mioDecorative(),
        )
        Column(
            modifier = Modifier.weight(1f),
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
            Text(
                text = recommendation.releaseAge.recencyText(),
                style = MioTheme.typography.caption,
                color = MioTheme.colors.textMuted,
            )
            Text(
                text = stringResource(recommendation.progressUnit.nextUnitLabelRes, recommendation.nextUnit),
                style = MioTheme.typography.body,
            )
            Text(
                text =
                    pluralStringResource(
                        recommendation.progressUnit.availableUnitsRes,
                        recommendation.availableUnitCount,
                        recommendation.availableUnitCount,
                    ),
                style = MioTheme.typography.caption,
            )
            Text(
                text = recommendation.progressLabel(),
                style = MioTheme.typography.caption,
                color = MioTheme.colors.textMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
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
