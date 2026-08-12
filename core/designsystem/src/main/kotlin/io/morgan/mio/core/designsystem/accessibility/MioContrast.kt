@file:Suppress("ktlint:standard:property-naming")

package io.morgan.mio.core.designsystem.accessibility

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

public object MioContrast {
    public const val NormalText: Float = 4.5f
    public const val LargeTextAndEssentialIcons: Float = 3.0f
}

public fun Color.contrastRatioAgainst(background: Color): Float {
    val renderedForeground = if (alpha < 1f) compositeOver(background) else this
    val foregroundLuminance = relativeLuminance(renderedForeground)
    val backgroundLuminance = relativeLuminance(background)
    val lighter = max(foregroundLuminance, backgroundLuminance)
    val darker = min(foregroundLuminance, backgroundLuminance)
    return (lighter + 0.05f) / (darker + 0.05f)
}

public fun Color.meetsContrastAgainst(
    background: Color,
    minimum: Float,
): Boolean = contrastRatioAgainst(background) >= minimum

private fun relativeLuminance(color: Color): Float {
    fun linearize(channel: Float): Float {
        val normalized = channel.coerceIn(0f, 1f)
        return if (normalized <= 0.04045f) {
            normalized / 12.92f
        } else {
            ((normalized + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat()
        }
    }

    return (0.2126f * linearize(color.red)) +
        (0.7152f * linearize(color.green)) +
        (0.0722f * linearize(color.blue))
}
