package io.morgan.mio.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.morgan.mio.core.designsystem.theme.MioTheme

@Composable
public fun MioProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    progressDescription: String? = null,
) {
    require(progress.isFinite() && progress in 0f..1f) {
        "MioProgressBar progress must be finite and within 0f..1f"
    }
    val shape = MioTheme.shapes.full
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(shape)
                .background(MioTheme.colors.surfaceSoft)
                .semantics {
                    progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f, 0)
                    if (progressDescription != null) {
                        contentDescription = progressDescription
                    }
                },
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth(progress)
                    .height(4.dp)
                    .background(MioTheme.colors.accent, shape),
        )
    }
}
