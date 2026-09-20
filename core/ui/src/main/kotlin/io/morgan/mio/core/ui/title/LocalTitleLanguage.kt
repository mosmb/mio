package io.morgan.mio.core.ui.title

import androidx.compose.runtime.staticCompositionLocalOf

public val LocalTitleLanguage: androidx.compose.runtime.ProvidableCompositionLocal<TitleLanguage> =
    staticCompositionLocalOf { TitleLanguage.SYSTEM }
