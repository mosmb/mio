package io.morgan.mio.feature.today.impl.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.morgan.mio.core.designsystem.accessibility.mioDecorative
import io.morgan.mio.core.designsystem.icon.MioIcons
import io.morgan.mio.core.designsystem.theme.MioTheme
import io.morgan.mio.core.ui.image.MioArtwork
import io.morgan.mio.feature.today.impl.R
import io.morgan.mio.feature.today.impl.TodayCaughtUpUiModel

@Composable
internal fun TodayCaughtUpReceipt(
    receipt: TodayCaughtUpUiModel,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 252.dp)
                .clip(MioTheme.shapes.lg)
                .background(ColorReceipt)
                .border(1.dp, MioTheme.colors.borderStrong, MioTheme.shapes.lg)
                .semantics(mergeDescendants = true) {}
                .padding(20.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier.size(32.dp).clip(CircleShape).background(MioTheme.colors.accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(MioIcons.Check),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MioTheme.colors.onAccent,
                )
            }
            Text(
                text = stringResource(R.string.today_caught_up),
                style = MioTheme.typography.cardTitle,
                color = Color.White,
            )
            Text(
                text = stringResource(R.string.today_caught_up_supporting),
                style = MioTheme.typography.body,
                color = Color.White.copy(alpha = 0.82f),
            )
        }
        if (receipt.artwork.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().height(72.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                receipt.artwork.forEach { artwork ->
                    MioArtwork(
                        reference = artwork.reference,
                        contentDescription = null,
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(MioTheme.shapes.xs)
                                .mioDecorative(),
                    )
                }
            }
        }
    }
}

private val ColorReceipt = Color(0xFF111418)
