package io.morgan.mio.feature.onboarding.impl.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.morgan.mio.core.designsystem.accessibility.mioDecorative
import io.morgan.mio.core.designsystem.theme.MioTheme
import io.morgan.mio.feature.onboarding.impl.R
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val MOSAIC_TILE_COUNT = 10
private const val MOSAIC_COLUMNS = 5
private const val MOSAIC_TILE_HEIGHT_TO_WIDTH = 1.15f
private const val MOSAIC_GAP_DP = 8
private const val MOSAIC_FLIGHT_DURATION_MS = 480
private const val MOSAIC_STACK_FADE_DELAY_MS = 250
private const val MOSAIC_STACK_FADE_DURATION_MS = 220
private const val MOSAIC_REDUCED_CROSSFADE_MS = 180

private val MosaicFlightEasing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

internal fun onboardingMosaicTileDelays(): List<Int> = listOf(0, 24, 48, 72, 96, 220, 244, 268, 292, 316)

internal fun onboardingMosaicTileCount(): Int = MOSAIC_TILE_COUNT

internal data class OnboardingMosaicTileDimensions(
    val width: Dp,
    val height: Dp,
)

internal fun onboardingMosaicTileDimensions(availableWidth: Dp): OnboardingMosaicTileDimensions {
    val gap = MOSAIC_GAP_DP.dp
    val width = ((availableWidth - gap * 4) / MOSAIC_COLUMNS).coerceAtLeast(0.dp)
    return OnboardingMosaicTileDimensions(
        width = width,
        height = width * MOSAIC_TILE_HEIGHT_TO_WIDTH,
    )
}

@Composable
internal fun OnboardingMosaic(
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = MioTheme.accessibility.reducedMotionEnabled,
    onMotionSettled: () -> Unit = {},
) {
    val gap = MOSAIC_GAP_DP.dp
    val tileProgress = remember { List(MOSAIC_TILE_COUNT) { Animatable(0f) } }
    val stackAlpha = remember { Animatable(1f) }
    val reducedAlpha = remember { Animatable(if (reducedMotion) 1f else 0f) }
    val delays = remember { onboardingMosaicTileDelays() }
    val semanticsDescription = stringResource(R.string.onboarding_import_progress_semantics)
    val currentOnMotionSettled = rememberUpdatedState(onMotionSettled)

    LaunchedEffect(reducedMotion) {
        if (reducedMotion) {
            tileProgress.forEach { progress -> progress.snapTo(1f) }
            reducedAlpha.snapTo(0f)
            reducedAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(MOSAIC_REDUCED_CROSSFADE_MS),
            )
        } else {
            tileProgress.forEach { progress -> progress.snapTo(0f) }
            reducedAlpha.snapTo(1f)
            stackAlpha.snapTo(1f)
            coroutineScope {
                launch {
                    delay(MOSAIC_STACK_FADE_DELAY_MS.toLong())
                    stackAlpha.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(MOSAIC_STACK_FADE_DURATION_MS),
                    )
                }
                tileProgress.forEachIndexed { index, progress ->
                    launch {
                        delay(delays[index].toLong())
                        progress.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(MOSAIC_FLIGHT_DURATION_MS, easing = MosaicFlightEasing),
                        )
                    }
                }
            }
        }
        currentOnMotionSettled.value()
    }

    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    contentDescription = semanticsDescription
                },
    ) {
        val tileDimensions = onboardingMosaicTileDimensions(maxWidth)
        val tileWidth = tileDimensions.width
        val tileHeight = tileDimensions.height
        val mosaicHeight = tileHeight * 2 + gap
        val stackX = (maxWidth - tileWidth) / 2
        val stackY = (mosaicHeight - tileHeight) / 2
        val density = androidx.compose.ui.platform.LocalDensity.current

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(mosaicHeight)
                    .graphicsLayer {
                        alpha = if (reducedMotion) reducedAlpha.value else 1f
                    },
        ) {
            repeat(MOSAIC_TILE_COUNT) { index ->
                val column = index % MOSAIC_COLUMNS
                val row = index / MOSAIC_COLUMNS
                val finalX = (tileWidth + gap) * column
                val finalY = (tileHeight + gap) * row
                val progress = tileProgress[index].value
                val alpha = if (reducedMotion) 1f else maxOf(stackAlpha.value, progress)

                Box(
                    modifier =
                        Modifier
                            .offset {
                                IntOffset(
                                    x = with(density) { (stackX + (finalX - stackX) * progress).roundToPx() },
                                    y = with(density) { (stackY + (finalY - stackY) * progress).roundToPx() },
                                )
                            }.width(tileWidth)
                            .height(tileHeight)
                            .graphicsLayer { this.alpha = alpha }
                            .clip(MioTheme.shapes.sm)
                            .background(MioTheme.colors.surfaceSoft)
                            .border(1.dp, MioTheme.colors.border, MioTheme.shapes.sm)
                            .mioDecorative(),
                )
            }
        }
    }
}
