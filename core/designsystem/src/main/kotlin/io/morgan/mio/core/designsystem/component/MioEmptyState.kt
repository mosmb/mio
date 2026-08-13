package io.morgan.mio.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.morgan.mio.core.designsystem.accessibility.mioDecorative
import io.morgan.mio.core.designsystem.theme.MioTheme

@Composable
public fun MioEmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    illustration: (@Composable () -> Unit)? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    require((actionLabel == null) == (onAction == null)) {
        "MioEmptyState requires both actionLabel and onAction, or neither"
    }

    Column(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (illustration != null) {
            Box(
                modifier =
                    Modifier
                        .sizeIn(
                            minWidth = 96.dp,
                            minHeight = 96.dp,
                            maxWidth = 140.dp,
                            maxHeight = 140.dp,
                        ).mioDecorative(),
                contentAlignment = Alignment.Center,
            ) {
                illustration()
            }
        }
        Text(
            text = title,
            style = MioTheme.typography.section,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = message,
            style = MioTheme.typography.body,
            color = MioTheme.colors.textMuted,
        )
        if (actionLabel != null && onAction != null) {
            MioButton(
                text = actionLabel,
                onClick = onAction,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
