package io.morgan.mio.domain.preferences

import io.morgan.mio.domain.model.AccountId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PreferencesTest {
    @Test
    fun defaultsSeparateDeviceAndAccountPreferences() {
        val accountId = AccountId("account-1")

        assertEquals(
            DevicePreferences(
                appearance = AppearancePreference.SYSTEM,
                motion = MotionPreference.FOLLOW_SYSTEM,
                hapticsEnabled = true,
            ),
            DevicePreferences.Default,
        )
        assertEquals(AccountPreferences.default(accountId), AccountPreferences(accountId, LibraryMediaFilter.ALL))
    }
}
