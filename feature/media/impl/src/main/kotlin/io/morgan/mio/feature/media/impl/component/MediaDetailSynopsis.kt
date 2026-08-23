package io.morgan.mio.feature.media.impl.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import io.morgan.mio.core.designsystem.theme.MioTheme
import io.morgan.mio.feature.media.impl.R

@Composable
internal fun MediaDetailSynopsis(
    synopsis: String?,
    modifier: Modifier = Modifier,
) {
    if (synopsis == null) return
    var expanded by rememberSaveable(synopsis) { mutableStateOf(false) }
    var hasOverflow by rememberSaveable(synopsis) { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.media_detail_synopsis),
            style = MioTheme.typography.section,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = synopsis,
            style = MioTheme.typography.body,
            maxLines = if (expanded) Int.MAX_VALUE else 4,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { result -> hasOverflow = result.hasVisualOverflow },
        )
        if (hasOverflow || expanded) {
            Text(
                text =
                    stringResource(
                        if (expanded) R.string.media_detail_read_less else R.string.media_detail_read_more,
                    ),
                style = MioTheme.typography.bodyStrong,
                color = MioTheme.colors.accent,
                modifier =
                    Modifier
                        .semantics { role = Role.Button }
                        .clickable { expanded = !expanded },
            )
        }
    }
}
