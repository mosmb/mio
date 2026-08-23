package io.morgan.mio.feature.today.impl.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import io.morgan.mio.feature.today.impl.TodayNowUiModel
import io.morgan.mio.feature.today.impl.TodayProgressUnit

@Composable
internal fun TodayNowCard(
    now: TodayNowUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val actionLabel = stringResource(R.string.today_open_details, now.title)
    val cardShape = MioTheme.shapes.lg
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 332.dp)
                .clip(cardShape)
                .clickable(
                    role = Role.Button,
                    onClickLabel = actionLabel,
                    onClick = onClick,
                ).semantics(mergeDescendants = true) {},
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
        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(20.dp),
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
            Text(
                text = stringResource(now.progressUnit.nextUnitLabelRes, now.nextUnit),
                style = MioTheme.typography.bodyStrong,
                color = Color.White,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (now.availableUnitCount > 0) {
                Text(
                    text =
                        pluralStringResource(
                            now.progressUnit.availableUnitsRes,
                            now.availableUnitCount,
                            now.availableUnitCount,
                        ),
                    style = MioTheme.typography.body,
                    color = Color.White,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Text(
                text = now.progressLabel(),
                style = MioTheme.typography.body,
                color = Color.White,
                modifier = Modifier.padding(top = 4.dp),
            )
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
