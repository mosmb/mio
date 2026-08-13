package io.morgan.mio.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.morgan.mio.core.designsystem.icon.MioIcons
import io.morgan.mio.core.designsystem.theme.MioTheme

@Composable
public fun MioErrorState(
    title: String,
    message: String,
    retryLabel: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    supportingContent: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MioIcon(
                iconRes = MioIcons.Error,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MioTheme.colors.error,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = title,
                style = MioTheme.typography.section,
                modifier = Modifier.semantics { heading() },
            )
        }
        Text(
            text = message,
            style = MioTheme.typography.body,
            color = MioTheme.colors.textMuted,
        )
        if (supportingContent != null) {
            supportingContent()
        }
        MioButton(
            text = retryLabel,
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
