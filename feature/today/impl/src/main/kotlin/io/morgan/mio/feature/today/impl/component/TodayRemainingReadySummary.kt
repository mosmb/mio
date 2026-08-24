package io.morgan.mio.feature.today.impl.component

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import io.morgan.mio.core.designsystem.theme.MioTheme
import io.morgan.mio.feature.today.impl.R

@Composable
internal fun TodayRemainingReadySummary(
    count: Int,
    modifier: Modifier = Modifier,
) {
    require(count > 0)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clip(MioTheme.shapes.sm)
                .border(1.dp, MioTheme.colors.border, MioTheme.shapes.sm)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = pluralStringResource(R.plurals.today_remaining_titles, count, count),
            style = MioTheme.typography.body,
            color = MioTheme.colors.textMuted,
        )
    }
}
