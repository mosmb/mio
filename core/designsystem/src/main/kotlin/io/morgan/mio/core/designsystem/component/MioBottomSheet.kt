package io.morgan.mio.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.morgan.mio.core.designsystem.layout.MioWindowInsets
import io.morgan.mio.core.designsystem.theme.MioTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun MioBottomSheet(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!visible) return

    if (MioTheme.accessibility.reducedMotionEnabled) {
        val reveal = androidx.compose.runtime.remember { Animatable(0f) }
        val revealMillis = MioTheme.motion.reducedMotionCrossfadeMillis
        LaunchedEffect(reveal) {
            reveal.animateTo(
                targetValue = 1f,
                animationSpec = tween(revealMillis),
            )
        }
        Dialog(
            onDismissRequest = onDismissRequest,
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(MioTheme.colors.scrim.copy(alpha = MioTheme.colors.scrim.alpha * reveal.value))
                            .clickable(onClick = onDismissRequest)
                            .clearAndSetSemantics {},
                )
                MioStaticSheet(
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .graphicsLayer { alpha = reveal.value }
                            .then(modifier),
                    title = title,
                    subtitle = subtitle,
                    content = content,
                )
            }
        }
    } else {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val density = LocalDensity.current
        val platformViewConfiguration = LocalViewConfiguration.current
        val sheetViewConfiguration =
            remember(density, platformViewConfiguration) {
                object : ViewConfiguration by platformViewConfiguration {
                    override val touchSlop: Float = with(density) { 10.dp.toPx() }
                }
            }
        CompositionLocalProvider(LocalViewConfiguration provides sheetViewConfiguration) {
            ModalBottomSheet(
                onDismissRequest = onDismissRequest,
                modifier = modifier,
                sheetState = sheetState,
                shape =
                    RoundedCornerShape(
                        topStart = 24.dp,
                        topEnd = 24.dp,
                        bottomStart = 0.dp,
                        bottomEnd = 0.dp,
                    ),
                containerColor = MioTheme.colors.surface,
                contentColor = MioTheme.colors.text,
                tonalElevation = 0.dp,
                scrimColor = MioTheme.colors.scrim,
                dragHandle = { MioSheetDragHandle() },
                contentWindowInsets = { MioWindowInsets.bottomSystemBarsOrIme },
            ) {
                MioSheetContent(
                    title = title,
                    subtitle = subtitle,
                    showDragHandle = false,
                    content = content,
                )
            }
        }
    }
}

@Composable
private fun MioStaticSheet(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(
                    MioTheme.colors.surface,
                    RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                ).pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent()
                        }
                    }
                },
    ) {
        MioSheetContent(
            title = title,
            subtitle = subtitle,
            showDragHandle = true,
            content = content,
        )
    }
}

@Composable
private fun MioSheetContent(
    title: String,
    subtitle: String?,
    showDragHandle: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding(),
    ) {
        if (showDragHandle) {
            MioSheetDragHandle()
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        ) {
            Text(
                text = title,
                style = MioTheme.typography.section,
                modifier = Modifier,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MioTheme.typography.body,
                    color = MioTheme.colors.textMuted,
                )
            }
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
private fun MioSheetDragHandle() {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag("MioBottomSheet:dragHandle")
                .padding(top = 12.dp, bottom = 16.dp)
                .height(4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(width = 32.dp, height = 4.dp)
                .clip(MioTheme.shapes.full)
                .background(MioTheme.colors.textMuted),
        )
    }
}
