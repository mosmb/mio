package io.morgan.mio.domain.preferences

import io.morgan.mio.domain.model.AccountId

public data class AccountPreferences(
    public val accountId: AccountId,
    public val libraryMediaFilter: LibraryMediaFilter,
) {
    public companion object {
        public fun default(accountId: AccountId): AccountPreferences =
            AccountPreferences(
                accountId = accountId,
                libraryMediaFilter = LibraryMediaFilter.ALL,
            )
    }
}
