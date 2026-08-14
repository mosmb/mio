package io.morgan.mio.core.ui.image

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import coil3.ColorImage
import coil3.ImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.AsyncImagePainter
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import coil3.request.ErrorResult
import coil3.test.FakeImageLoaderEngine
import coil3.test.default
import io.morgan.mio.core.designsystem.theme.MioTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import androidx.compose.ui.graphics.Color as ComposeColor

@Suppress("DEPRECATION")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MioArtworkTest {
    private val activityRule = RobolectricActivityRule()
    private val imageLoaders = mutableListOf<ImageLoader>()

    @get:Rule
    val composeRule: AndroidComposeTestRule<RobolectricActivityRule, *> =
        AndroidComposeTestRule(activityRule) { rule -> rule.activity }

    @After
    fun closeImageLoaders() {
        imageLoaders.forEach(ImageLoader::shutdown)
    }

    @Test
    fun successAndNullStatesKeepTheCallerBoundsAndUseTheSuppliedLoader() {
        var requestedReference: Any? = null
        val engine =
            FakeImageLoaderEngine
                .Builder()
                .requestTransformer { request ->
                    requestedReference = request.data
                    request
                }.default(ColorDrawable(Color.TRANSPARENT))
                .build()
        val loader = imageLoader(engine)

        composeRule.setContent {
            MioTheme(highContrastOverride = false) {
                MioImageLoaderProvider(loader) {
                    CompositionLocalProvider(
                        LocalMioArtworkFallbackPainter provides ColorPainter(ComposeColor.Red),
                    ) {
                        MioArtwork(
                            reference = "https://images.invalid/art.png",
                            contentDescription = "Meaningful artwork",
                            modifier = Modifier.size(120.dp).testTag("artwork"),
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()

        val bounds = composeRule.onNodeWithTag("artwork").fetchSemanticsNode().boundsInRoot
        val expected = with(composeRule.density) { 120.dp.toPx() }
        assertEquals(expected, bounds.width, 0.5f)
        assertEquals(expected, bounds.height, 0.5f)
        assertEquals("https://images.invalid/art.png", requestedReference)
        composeRule.onAllNodesWithContentDescription("Meaningful artwork").assertCountEquals(1)
        composeRule
            .onNodeWithTag(MIO_ARTWORK_FALLBACK_TEST_TAG, useUnmergedTree = true)
            .assertDoesNotExist()
    }

    @OptIn(ExperimentalCoilApi::class)
    @Test
    fun nullLoadingAndErrorUseTheSameFallbackWithoutChangingBounds() {
        val loader = imageLoader(FakeImageLoaderEngine(ColorImage(Color.BLUE)))
        val loadingHandler = AsyncImagePreviewHandler { _, _ -> AsyncImagePainter.State.Loading(null) }
        val errorHandler =
            AsyncImagePreviewHandler { _, request ->
                AsyncImagePainter.State.Error(
                    null,
                    ErrorResult(null, request, IllegalStateException("Fixture image failure")),
                )
            }

        composeRule.setContent {
            MioTheme(highContrastOverride = false) {
                MioImageLoaderProvider(loader) {
                    CompositionLocalProvider(
                        LocalInspectionMode provides true,
                        LocalMioArtworkFallbackPainter provides ColorPainter(ComposeColor.Red),
                    ) {
                        Row {
                            MioArtwork(null, null, Modifier.size(96.dp, 72.dp).testTag("null"))
                            CompositionLocalProvider(LocalAsyncImagePreviewHandler provides loadingHandler) {
                                MioArtwork(
                                    "preview://loading",
                                    null,
                                    Modifier.size(96.dp, 72.dp).testTag("loading"),
                                )
                            }
                            CompositionLocalProvider(LocalAsyncImagePreviewHandler provides errorHandler) {
                                MioArtwork(
                                    "preview://error",
                                    null,
                                    Modifier.size(96.dp, 72.dp).testTag("error"),
                                )
                            }
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()

        val nodes = listOf("null", "loading", "error").map(composeRule::onNodeWithTag)
        val bounds = nodes.map { node -> node.fetchSemanticsNode().boundsInRoot }
        bounds.forEach { bound ->
            assertEquals(bounds.first().width, bound.width, 0.5f)
            assertEquals(bounds.first().height, bound.height, 0.5f)
        }
        composeRule
            .onAllNodesWithTag(MIO_ARTWORK_FALLBACK_TEST_TAG, useUnmergedTree = true)
            .assertCountEquals(3)
    }

    @Test
    fun posterOwnsOnlyTheReusableTwoToThreeRatio() {
        val loader = imageLoader(FakeImageLoaderEngine(ColorDrawable(Color.BLUE)))

        composeRule.setContent {
            MioTheme(highContrastOverride = false) {
                MioImageLoaderProvider(loader) {
                    CompositionLocalProvider(
                        LocalMioArtworkFallbackPainter provides ColorPainter(ComposeColor.Transparent),
                    ) {
                        MioPoster(
                            reference = null,
                            contentDescription = null,
                            modifier = Modifier.width(120.dp).testTag("poster"),
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()

        val bounds = composeRule.onNodeWithTag("poster").fetchSemanticsNode().boundsInRoot
        assertEquals(120f / 180f, bounds.width / bounds.height, 0.01f)
    }

    @Test
    fun decorativeArtworkDoesNotAddAnImageDescription() {
        val loader = imageLoader(FakeImageLoaderEngine(ColorDrawable(Color.BLUE)))

        composeRule.setContent {
            MioTheme(highContrastOverride = false) {
                MioImageLoaderProvider(loader) {
                    CompositionLocalProvider(
                        LocalMioArtworkFallbackPainter provides ColorPainter(ComposeColor.Transparent),
                    ) {
                        MioArtwork(
                            reference = "https://images.invalid/decorative.png",
                            contentDescription = null,
                            modifier = Modifier.size(80.dp).testTag("decorative"),
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Decorative artwork").assertDoesNotExist()
        assertTrue(
            composeRule
                .onNodeWithTag("decorative")
                .fetchSemanticsNode()
                .boundsInRoot.width > 0f,
        )
    }

    private fun imageLoader(engine: FakeImageLoaderEngine): ImageLoader =
        ImageLoader
            .Builder(ApplicationProvider.getApplicationContext())
            .components { add(engine) }
            .build()
            .also(imageLoaders::add)

    public class RobolectricActivityRule : TestRule {
        lateinit var activity: ComponentActivity

        override fun apply(
            base: Statement,
            description: Description,
        ): Statement =
            object : Statement() {
                override fun evaluate() {
                    activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
                    try {
                        base.evaluate()
                    } finally {
                        activity.finish()
                    }
                }
            }
    }
}
