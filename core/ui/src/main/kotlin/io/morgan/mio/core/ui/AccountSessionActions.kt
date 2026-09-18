package io.morgan.mio.core.ui

import androidx.compose.runtime.staticCompositionLocalOf

public data class AccountSessionActions(
    public val onDisconnect: () -> Unit,
)

public val LocalAccountSessionActions =
    staticCompositionLocalOf<AccountSessionActions> {
        error("AccountSessionActions must be provided by the authenticated app root")
    }
