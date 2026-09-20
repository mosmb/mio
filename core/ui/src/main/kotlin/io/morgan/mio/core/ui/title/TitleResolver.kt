package io.morgan.mio.core.ui.title

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration

@Composable
public fun rememberTitleResolver(): (String, MediaTitleNames) -> String {
    val language = LocalTitleLanguage.current
    val deviceLanguage = LocalConfiguration.current.locales[0]?.language ?: "en"
    return remember(language, deviceLanguage) {
        {
            fallback,
            names,
            ->
            names.resolve(fallback, language, deviceLanguage)
        }
    }
}
