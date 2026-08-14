package io.morgan.mio.core.ui.image

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.ColorImage
import coil3.ImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.AsyncImagePainter
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import coil3.compose.LocalPlatformContext
import coil3.request.ErrorResult
import io.morgan.mio.core.designsystem.theme.MioTheme

@Preview(
    name = "Artwork light",
    showBackground = true,
    widthDp = 393,
    heightDp = 852,
    uiMode = Configuration.UI_MODE_NIGHT_NO,
)
@Preview(
    name = "Artwork dark",
    showBackground = true,
    widthDp = 393,
    heightDp = 852,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@OptIn(ExperimentalCoilApi::class)
@Composable
internal fun MioArtworkCatalogPreview() {
    val platformContext = LocalPlatformContext.current
    val imageLoader = remember(platformContext) { ImageLoader.Builder(platformContext).build() }
    val successHandler =
        remember {
            AsyncImagePreviewHandler { _ -> ColorImage(0xFF536DFE.toInt()) }
        }
    val loadingHandler =
        remember {
            AsyncImagePreviewHandler { _, _ -> AsyncImagePainter.State.Loading(null) }
        }
    val errorHandler =
        remember {
            AsyncImagePreviewHandler { _, request ->
                AsyncImagePainter.State.Error(
                    painter = null,
                    result = ErrorResult(null, request, IllegalStateException("Preview error")),
                )
            }
        }
    DisposableEffect(imageLoader) {
        onDispose(imageLoader::shutdown)
    }
    MioTheme {
        MioImageLoaderProvider(imageLoader) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Success: meaningful poster / decorative banner")
                CompositionLocalProvider(LocalAsyncImagePreviewHandler provides successHandler) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MioPoster(
                            reference = "preview://poster",
                            contentDescription = "Preview poster artwork",
                            modifier = Modifier.width(96.dp),
                        )
                        MioArtwork(
                            reference = "preview://banner",
                            contentDescription = null,
                            modifier = Modifier.size(width = 240.dp, height = 144.dp),
                        )
                    }
                }
                Text("Missing / loading / error")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MioArtwork(null, null, Modifier.size(104.dp, 72.dp))
                    CompositionLocalProvider(LocalAsyncImagePreviewHandler provides loadingHandler) {
                        MioArtwork("preview://loading", null, Modifier.size(104.dp, 72.dp))
                    }
                    CompositionLocalProvider(LocalAsyncImagePreviewHandler provides errorHandler) {
                        MioArtwork("preview://error", null, Modifier.size(104.dp, 72.dp))
                    }
                }
            }
        }
    }
}
