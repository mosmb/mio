package io.morgan.mio.feature.media.impl.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.morgan.mio.core.designsystem.theme.MioTheme
import io.morgan.mio.core.ui.image.MioPoster
import io.morgan.mio.feature.media.impl.MediaDetailRelatedTitleUiModel
import io.morgan.mio.feature.media.impl.R

@Composable
internal fun MediaDetailRelatedTitles(
    titles: List<MediaDetailRelatedTitleUiModel>,
    onTitleClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (titles.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.media_detail_related_titles),
            style = MioTheme.typography.section,
            modifier = Modifier.semantics { heading() },
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MioTheme.spacing.space3),
        ) {
            items(titles, key = { item -> item.id }) { item ->
                val posterDescription =
                    stringResource(
                        R.string.media_detail_related_poster_description,
                        item.title,
                    )
                Column(
                    modifier =
                        Modifier
                            .width(96.dp)
                            .clickable { onTitleClick(item.id) }
                            .semantics { role = Role.Button },
                ) {
                    MioPoster(
                        reference = item.posterReference,
                        contentDescription = null,
                        modifier =
                            Modifier
                                .width(96.dp)
                                .semantics { contentDescription = posterDescription },
                    )
                    Text(
                        text = item.title,
                        style = MioTheme.typography.label,
                        maxLines = 2,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}
