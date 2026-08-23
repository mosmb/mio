package io.morgan.mio.feature.media.impl.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.morgan.mio.core.designsystem.component.MioIconButton
import io.morgan.mio.core.designsystem.icon.MioIcons
import io.morgan.mio.core.designsystem.theme.MioTheme
import io.morgan.mio.core.ui.image.MioArtwork
import io.morgan.mio.core.ui.image.MioPoster
import io.morgan.mio.feature.media.impl.MediaDetailFormat
import io.morgan.mio.feature.media.impl.MediaDetailMediaType
import io.morgan.mio.feature.media.impl.MediaDetailPublicationStatus
import io.morgan.mio.feature.media.impl.MediaDetailUiModel
import io.morgan.mio.feature.media.impl.R

@Composable
internal fun MediaDetailHeader(
    media: MediaDetailUiModel,
    rise: SheetRise,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth().testTag("media-detail-scrolling-hero")) {
        MediaDetailBackdrop(
            media = media,
            rise = rise,
            onBack = onBack,
        )
        MediaDetailSheetHeader(
            media = media,
            rise = rise,
            modifier = Modifier.padding(top = 232.dp),
        )
    }
}

@Composable
internal fun MediaDetailBackdrop(
    media: MediaDetailUiModel,
    rise: SheetRise,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = MioTheme.colors
    val typography = MioTheme.typography
    val reducedMotion = MioTheme.accessibility.reducedMotionEnabled
    val toolbarAlpha by
        animateFloatAsState(
            targetValue = rise.toolbarAlpha,
            animationSpec =
                if (reducedMotion) {
                    tween(MioTheme.motion.reducedMotionCrossfadeMillis)
                } else {
                    snap()
                },
            label = "mediaDetailToolbarAlpha",
        )
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(264.dp),
    ) {
        MioArtwork(
            reference = media.bannerReference ?: media.posterReference,
            contentDescription = null,
            modifier = Modifier.fillMaxWidth().height(264.dp),
        )
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .graphicsLayer { alpha = toolbarAlpha },
            color = colors.surface,
        ) {
            Text(
                text = media.title,
                style = typography.bodyStrong,
                color = colors.text,
                modifier =
                    Modifier
                        .padding(top = 16.dp, start = 72.dp, end = 72.dp)
                        .clearAndSetSemantics {},
            )
        }
        if (onBack != null) {
            MioIconButton(
                iconRes = MioIcons.Back,
                contentDescription = stringResource(R.string.media_detail_back),
                onClick = onBack,
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}

@Composable
internal fun MediaDetailSheetHeader(
    media: MediaDetailUiModel,
    rise: SheetRise,
    modifier: Modifier = Modifier,
) {
    val colors = MioTheme.colors
    val typography = MioTheme.typography
    val posterDescription = stringResource(R.string.media_detail_poster_description, media.title)
    val titleAlpha by
        animateFloatAsState(
            targetValue = rise.titleAlpha,
            animationSpec =
                if (MioTheme.accessibility.reducedMotionEnabled) {
                    tween(MioTheme.motion.reducedMotionCrossfadeMillis)
                } else {
                    snap()
                },
            label = "mediaDetailTitleAlpha",
        )
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = colors.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top,
            ) {
                MioPoster(
                    reference = media.posterReference,
                    contentDescription = null,
                    modifier =
                        Modifier
                            .size(width = 112.dp, height = 160.dp)
                            .offset(y = (-52).dp)
                            .semantics { contentDescription = posterDescription }
                            .graphicsLayer { translationY = rise.posterTranslationY.toPx() },
                )
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .graphicsLayer {
                                translationY = rise.introTranslationY.toPx()
                                alpha = titleAlpha
                            },
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = media.title,
                        style = typography.title,
                        color = colors.text,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = metadataLabel(media),
                        style = typography.label,
                        color = colors.textMuted,
                    )
                    if (media.scorePercent != null) {
                        Text(
                            text = stringResource(R.string.media_detail_score, media.scorePercent),
                            style = typography.label,
                            color = colors.accent,
                        )
                    }
                }
            }
            if (media.genres.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = media.genres.joinToString(" · "),
                    style = typography.label,
                    color = colors.textMuted,
                )
            }
        }
    }
}

@Composable
private fun metadataLabel(media: MediaDetailUiModel): String {
    val type =
        when (media.type) {
            MediaDetailMediaType.ANIME -> stringResource(R.string.media_detail_type_anime)
            MediaDetailMediaType.MANGA -> stringResource(R.string.media_detail_type_manga)
        }
    val format = media.format?.displayName()
    val status =
        when (media.publicationStatus) {
            MediaDetailPublicationStatus.UPCOMING -> stringResource(R.string.media_detail_status_upcoming)
            MediaDetailPublicationStatus.RELEASING -> stringResource(R.string.media_detail_status_releasing)
            MediaDetailPublicationStatus.FINISHED -> stringResource(R.string.media_detail_status_finished)
            MediaDetailPublicationStatus.CANCELLED -> stringResource(R.string.media_detail_status_cancelled)
            MediaDetailPublicationStatus.HIATUS -> stringResource(R.string.media_detail_status_hiatus)
            MediaDetailPublicationStatus.UNKNOWN -> stringResource(R.string.media_detail_status_unknown)
        }
    return listOfNotNull(type, format, media.releaseYear?.toString(), status).joinToString(" · ")
}

@Composable
private fun MediaDetailFormat.displayName(): String =
    when (this) {
        MediaDetailFormat.TV -> stringResource(R.string.media_detail_format_tv)
        MediaDetailFormat.MOVIE -> stringResource(R.string.media_detail_format_movie)
        MediaDetailFormat.OVA -> stringResource(R.string.media_detail_format_ova)
        MediaDetailFormat.ONA -> stringResource(R.string.media_detail_format_ona)
        MediaDetailFormat.SPECIAL -> stringResource(R.string.media_detail_format_special)
        MediaDetailFormat.MUSIC -> stringResource(R.string.media_detail_format_music)
        MediaDetailFormat.MANGA -> stringResource(R.string.media_detail_format_manga)
        MediaDetailFormat.NOVEL -> stringResource(R.string.media_detail_format_novel)
        MediaDetailFormat.ONE_SHOT -> stringResource(R.string.media_detail_format_one_shot)
        MediaDetailFormat.OTHER -> stringResource(R.string.media_detail_format_other)
    }
