package io.morgan.mio.core.designsystem.layout

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable

public object MioWindowInsets {
    public val safeDrawing: WindowInsets
        @Composable
        get() = WindowInsets.safeDrawing

    public val horizontalAndTop: WindowInsets
        @Composable
        get() = safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)

    public val bottomSystemBars: WindowInsets
        @Composable
        get() = safeDrawing.only(WindowInsetsSides.Bottom)

    public val ime: WindowInsets
        @Composable
        get() = WindowInsets.ime

    public val bottomSystemBarsOrIme: WindowInsets
        @Composable
        get() = safeDrawing.union(ime).only(WindowInsetsSides.Bottom)
}
