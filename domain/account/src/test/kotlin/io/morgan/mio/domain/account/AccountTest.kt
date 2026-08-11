package io.morgan.mio.domain.account

import io.morgan.mio.domain.model.AccountId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class AccountTest {
    @Test
    fun accountRejectsBlankDisplayName() {
        assertThrows<IllegalArgumentException> {
            Account(
                id = AccountId("account-1"),
                provider = ConnectedProvider.ANILIST,
                displayName = "\n",
                avatar = null,
            )
        }
    }

    @Test
    fun accountUsesMioIdentityAndOptionalAvatar() {
        val account =
            Account(
                id = AccountId("account-1"),
                provider = ConnectedProvider.MY_ANIME_LIST,
                displayName = "Morgan",
                avatar = null,
            )

        assertEquals(AccountId("account-1"), account.id)
        assertEquals(ConnectedProvider.MY_ANIME_LIST, account.provider)
    }
}
