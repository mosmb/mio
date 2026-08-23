package io.morgan.mio.feature.media.impl.component

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal data class SheetRise(
    val posterTranslationY: Dp,
    val introTranslationY: Dp,
    val toolbarAlpha: Float,
    val titleAlpha: Float,
)

internal fun sheetRiseAt(
    offset: Dp,
    reducedMotion: Boolean = false,
): SheetRise {
    val progress = (offset / SHEET_RISE_DISTANCE).coerceIn(0f, 1f)
    return if (reducedMotion) {
        SheetRise(
            posterTranslationY = 0.dp,
            introTranslationY = 0.dp,
            toolbarAlpha = progress,
            titleAlpha = 1f - progress,
        )
    } else {
        SheetRise(
            posterTranslationY = -20.dp * progress,
            introTranslationY = -8.dp * progress,
            toolbarAlpha = progress,
            titleAlpha = 1f - progress,
        )
    }
}

private val SHEET_RISE_DISTANCE: Dp = 120.dp
