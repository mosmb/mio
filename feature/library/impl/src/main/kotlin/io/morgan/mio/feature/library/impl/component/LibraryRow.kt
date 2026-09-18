package io.morgan.mio.feature.library.impl.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.morgan.mio.core.designsystem.theme.MioTheme
import io.morgan.mio.core.ui.image.MioArtwork
import io.morgan.mio.feature.library.impl.LibraryProgressUnit
import io.morgan.mio.feature.library.impl.LibraryRowMediaType
import io.morgan.mio.feature.library.impl.LibraryRowStatus
import io.morgan.mio.feature.library.impl.LibraryRowUiModel
import io.morgan.mio.feature.library.impl.R

@Composable
internal fun LibraryRow(
    row: LibraryRowUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = row.title.ifBlank { stringResource(R.string.library_unavailable_title) }
    val mediaType =
        when (row.mediaType) {
            LibraryRowMediaType.ANIME -> stringResource(R.string.library_filter_anime)
            LibraryRowMediaType.MANGA -> stringResource(R.string.library_filter_manga)
            null -> stringResource(R.string.library_media_type_unavailable)
        }
    val status =
        when (row.status) {
            LibraryRowStatus.CURRENT -> stringResource(R.string.library_status_current)
            LibraryRowStatus.PLANNED -> stringResource(R.string.library_status_planning)
            LibraryRowStatus.PAUSED -> stringResource(R.string.library_status_paused)
            LibraryRowStatus.COMPLETED -> stringResource(R.string.library_status_completed)
            LibraryRowStatus.DROPPED -> stringResource(R.string.library_status_dropped)
            LibraryRowStatus.REPEATING -> stringResource(R.string.library_status_repeating)
        }
    val unit =
        when (row.progressUnit) {
            LibraryProgressUnit.EPISODE -> stringResource(R.string.library_unit_episode)
            LibraryProgressUnit.CHAPTER -> stringResource(R.string.library_unit_chapter)
            LibraryProgressUnit.VOLUME -> stringResource(R.string.library_unit_volume)
        }
    val progressText =
        row.total?.let { total ->
            stringResource(R.string.library_progress_with_total, unit, row.progress, total)
        } ?: stringResource(R.string.library_progress_without_total, unit, row.progress)

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 136.dp)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MioArtwork(
            reference = row.posterReference,
            contentDescription = null,
            modifier = Modifier.size(width = 84.dp, height = 120.dp).clip(RoundedCornerShape(12.dp)),
        )
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.weight(1f).padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                style = MioTheme.typography.bodyStrong,
                maxLines = 2,
            )
            Text(
                text = stringResource(R.string.library_row_type_and_status, mediaType, status),
                style = MioTheme.typography.caption,
                color = MioTheme.colors.textMuted,
                maxLines = 1,
            )
            Text(
                text = progressText,
                style = MioTheme.typography.body,
                maxLines = 1,
            )
        }
    }
}
