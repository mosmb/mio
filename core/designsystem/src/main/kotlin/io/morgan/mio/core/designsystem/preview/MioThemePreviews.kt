package io.morgan.mio.core.designsystem.preview

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview

@Preview(
    name = "Light reference",
    widthDp = 393,
    heightDp = 852,
    uiMode = Configuration.UI_MODE_NIGHT_NO,
    showBackground = true,
)
@Preview(
    name = "Dark reference",
    widthDp = 393,
    heightDp = 852,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
)
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.FUNCTION)
public annotation class MioThemePreviews
