package io.morgan.mio.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

@Composable
public fun MioIcon(
    @DrawableRes iconRes: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    Icon(
        painter = painterResource(iconRes),
        contentDescription = contentDescription,
        modifier = modifier.size(24.dp),
        tint = tint,
    )
}
