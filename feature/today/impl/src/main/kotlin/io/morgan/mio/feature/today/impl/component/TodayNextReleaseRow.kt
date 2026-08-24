package io.morgan.mio.feature.today.impl.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.morgan.mio.core.designsystem.accessibility.mioDecorative
import io.morgan.mio.core.designsystem.theme.MioTheme
import io.morgan.mio.core.ui.image.MioArtwork
import io.morgan.mio.feature.today.impl.R
import io.morgan.mio.feature.today.impl.TodayNextReleaseUiModel
import io.morgan.mio.feature.today.impl.TodayProgressUnit
import io.morgan.mio.feature.today.impl.TodayRelativeTime
import io.morgan.mio.feature.today.impl.TodayRelativeTimeUnit

@Composable
internal fun TodayNextReleaseRow(
    recommendation: TodayNextReleaseUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val actionLabel = stringResource(R.string.today_open_details, recommendation.title)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(MioTheme.shapes.sm)
                .clickable(
                    role = Role.Button,
                    onClickLabel = actionLabel,
                    onClick = onClick,
                ).semantics(mergeDescendants = true) {}
                .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MioArtwork(
            reference = recommendation.posterReference,
            contentDescription = null,
            modifier = Modifier.size(width = 56.dp, height = 80.dp).clip(MioTheme.shapes.sm).mioDecorative(),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = stringResource(R.string.today_next_release),
                style = MioTheme.typography.caption,
                color = MioTheme.colors.textMuted,
            )
            Text(text = recommendation.title, style = MioTheme.typography.bodyStrong)
            Text(
                text = stringResource(recommendation.progressUnit.nextUnitLabelRes, recommendation.unitNumber),
                style = MioTheme.typography.body,
            )
            Text(
                text = recommendation.waitUntilRelease.futureWaitText(),
                style = MioTheme.typography.caption,
                color = MioTheme.colors.textMuted,
            )
        }
    }
}

private val TodayProgressUnit.nextUnitLabelRes: Int
    get() =
        when (this) {
            TodayProgressUnit.EPISODE -> R.string.today_next_episode
            TodayProgressUnit.CHAPTER -> R.string.today_next_chapter
            TodayProgressUnit.VOLUME -> R.string.today_next_volume
        }

@Composable
private fun TodayRelativeTime.futureWaitText(): String =
    when (unit) {
        TodayRelativeTimeUnit.UNDER_HOUR -> stringResource(R.string.today_future_under_hour)
        TodayRelativeTimeUnit.HOURS ->
            pluralStringResource(R.plurals.today_future_hours, amount, amount)
        TodayRelativeTimeUnit.DAYS ->
            pluralStringResource(R.plurals.today_future_days, amount, amount)
    }
