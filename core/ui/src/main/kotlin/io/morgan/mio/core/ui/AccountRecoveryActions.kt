package io.morgan.mio.core.ui

import androidx.compose.runtime.staticCompositionLocalOf

public data class AccountRecoveryActions(
    public val providerLabel: String,
    public val onReconnect: () -> Unit,
    public val onRetryRefresh: () -> Unit,
) {
    init {
        require(providerLabel.isNotBlank())
    }
}

public val LocalAccountRecoveryActions =
    staticCompositionLocalOf<AccountRecoveryActions> {
        error("AccountRecoveryActions must be provided by the authenticated app root")
    }
