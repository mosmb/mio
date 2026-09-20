package io.morgan.mio.feature.media.impl.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.morgan.mio.core.designsystem.theme.MioTheme
import io.morgan.mio.core.ui.image.MioArtwork
import io.morgan.mio.feature.media.impl.MediaDetailMediaType
import io.morgan.mio.feature.media.impl.MediaDetailRelatedTitleUiModel
import io.morgan.mio.feature.media.impl.R

@Composable
internal fun MediaDetailRelatedTitles(
    titles: List<MediaDetailRelatedTitleUiModel>,
    onTitleClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (titles.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.media_detail_related_titles),
            style = MioTheme.typography.section,
            modifier = Modifier.semantics { heading() },
        )
        MediaDetailMediaType.entries.forEach { type ->
            val group = titles.filter { it.type == type }
            if (group.isNotEmpty()) {
                val typeLabel =
                    stringResource(
                        if (type ==
                            MediaDetailMediaType.ANIME
                        ) {
                            R.string.media_detail_type_anime
                        } else {
                            R.string.media_detail_type_manga
                        },
                    )
                Text(typeLabel, style = MioTheme.typography.bodyStrong, modifier = Modifier.semantics { heading() })
                group.forEach { item ->
                    val description = stringResource(R.string.media_detail_related_poster_description, item.title)
                    Surface(
                        shape = MioTheme.shapes.md,
                        color = MioTheme.colors.surface,
                        border = BorderStroke(1.dp, MioTheme.colors.border),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(MioTheme.shapes.md)
                                .clickable(role = Role.Button) { onTitleClick(item.id) }
                                .semantics { contentDescription = description },
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MioArtwork(
                                reference = item.posterReference,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.width(72.dp).aspectRatio(2f / 3f).clip(MioTheme.shapes.sm),
                            )
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(item.title, style = MioTheme.typography.bodyStrong)
                                Text(
                                    listOfNotNull(typeLabel, item.format?.displayName(), item.releaseYear?.toString())
                                        .distinct()
                                        .joinToString(" · "),
                                    style = MioTheme.typography.caption,
                                    color = MioTheme.colors.textMuted,
                                )
                                item.episodes?.let { count ->
                                    Text(
                                        pluralStringResource(R.plurals.media_detail_episodes, count, count),
                                        style = MioTheme.typography.caption,
                                        color = MioTheme.colors.textMuted,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
