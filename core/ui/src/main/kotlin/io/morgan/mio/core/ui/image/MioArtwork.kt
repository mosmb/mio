package io.morgan.mio.core.ui.image

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberConstraintsSizeResolver
import coil3.request.ImageRequest
import io.morgan.mio.core.designsystem.R
import io.morgan.mio.core.designsystem.theme.MioTheme

private val LocalMioImageLoader =
    staticCompositionLocalOf<ImageLoader> {
        error("MioImageLoaderProvider must wrap artwork content")
    }

internal val LocalMioArtworkFallbackPainter = staticCompositionLocalOf<Painter?> { null }
internal const val MIO_ARTWORK_FALLBACK_TEST_TAG: String = "mio-artwork-fallback"

@Composable
public fun MioImageLoaderProvider(
    imageLoader: ImageLoader,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalMioImageLoader provides imageLoader, content = content)
}

@Composable
public fun MioArtwork(
    reference: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val colors = MioTheme.colors
    if (LocalInspectionMode.current) {
        Box(modifier = modifier.background(colors.surfaceSoft)) { ArtworkFallbackMark() }
        return
    }
    val imageLoader = LocalMioImageLoader.current
    val sizeResolver = rememberConstraintsSizeResolver()
    val platformContext = LocalPlatformContext.current
    var showFallback by remember(reference) { mutableStateOf(true) }
    val request =
        remember(reference, platformContext, sizeResolver) {
            ImageRequest
                .Builder(platformContext)
                .data(reference)
                .size(sizeResolver)
                .build()
        }
    Box(
        modifier = modifier.background(colors.surfaceSoft),
    ) {
        if (showFallback) {
            ArtworkFallbackMark()
        }
        if (reference != null) {
            AsyncImage(
                model = request,
                contentDescription = contentDescription,
                imageLoader = imageLoader,
                modifier = Modifier.fillMaxSize().then(sizeResolver),
                contentScale = contentScale,
                onState = { state -> showFallback = state !is AsyncImagePainter.State.Success },
            )
        }
    }
}

@Composable
public fun MioPoster(
    reference: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    MioArtwork(
        reference = reference,
        contentDescription = contentDescription,
        modifier = modifier.aspectRatio(2f / 3f).clip(MioTheme.shapes.sm),
        contentScale = ContentScale.Crop,
    )
}

@Composable
private fun BoxScope.ArtworkFallbackMark() {
    val fallbackPainter = LocalMioArtworkFallbackPainter.current ?: painterResource(R.drawable.mio_mark)
    Icon(
        painter = fallbackPainter,
        contentDescription = null,
        modifier =
            Modifier
                .align(Alignment.Center)
                .size(32.dp)
                .padding(4.dp)
                .testTag(MIO_ARTWORK_FALLBACK_TEST_TAG),
        tint = MioTheme.colors.textMuted,
    )
}
