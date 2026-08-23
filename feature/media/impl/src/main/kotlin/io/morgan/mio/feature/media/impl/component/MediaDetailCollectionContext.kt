package io.morgan.mio.feature.media.impl.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.morgan.mio.core.designsystem.theme.MioTheme
import io.morgan.mio.feature.media.impl.MediaDetailCollectionStatus
import io.morgan.mio.feature.media.impl.MediaDetailCollectionUiModel
import io.morgan.mio.feature.media.impl.MediaDetailProgressUnit
import io.morgan.mio.feature.media.impl.R

@Composable
internal fun MediaDetailCollectionContext(
    collection: MediaDetailCollectionUiModel?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MioTheme.spacing.space2),
    ) {
        Text(
            text =
                if (collection == null) {
                    stringResource(R.string.media_detail_not_in_library)
                } else {
                    stringResource(R.string.media_detail_in_library)
                },
            style = MioTheme.typography.section,
            modifier = Modifier.semantics { heading() },
        )
        if (collection != null) {
            Text(
                text = stringResource(R.string.media_detail_status, collection.status.displayName()),
                style = MioTheme.typography.body,
            )
            Text(
                text =
                    stringResource(
                        R.string.media_detail_progress,
                        collection.progress,
                        collection.total ?: "—",
                        collection.progressUnit.displayName(),
                    ),
                style = MioTheme.typography.body,
            )
        }
    }
}

@Composable
private fun MediaDetailCollectionStatus.displayName(): String =
    when (this) {
        MediaDetailCollectionStatus.CURRENT -> stringResource(R.string.media_detail_collection_current)
        MediaDetailCollectionStatus.PLANNED -> stringResource(R.string.media_detail_collection_planned)
        MediaDetailCollectionStatus.PAUSED -> stringResource(R.string.media_detail_collection_paused)
        MediaDetailCollectionStatus.COMPLETED -> stringResource(R.string.media_detail_collection_completed)
        MediaDetailCollectionStatus.DROPPED -> stringResource(R.string.media_detail_collection_dropped)
        MediaDetailCollectionStatus.REPEATING -> stringResource(R.string.media_detail_collection_repeating)
    }

@Composable
private fun MediaDetailProgressUnit.displayName(): String =
    when (this) {
        MediaDetailProgressUnit.EPISODE -> stringResource(R.string.media_detail_unit_episodes)
        MediaDetailProgressUnit.CHAPTER -> stringResource(R.string.media_detail_unit_chapters)
        MediaDetailProgressUnit.VOLUME -> stringResource(R.string.media_detail_unit_volumes)
    }
