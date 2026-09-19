package io.morgan.mio.core.datastore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PreferencesKeysTest {
    @Test
    fun stableKeysUseTheDocumentedVocabulary() {
        assertEquals("device.appearance", PreferencesKeys.deviceAppearance.name)
        assertEquals("device.motion", PreferencesKeys.deviceMotion.name)
        assertEquals("device.haptics_enabled", PreferencesKeys.deviceHapticsEnabled.name)
        assertEquals(
            "account.account-1.library_media_filter",
            PreferencesKeys.accountLibraryMediaFilter("account-1").name,
        )
        assertEquals(
            "account.account-1.library_status_filter",
            PreferencesKeys.accountLibraryStatusFilter("account-1").name,
        )
    }

    @Test
    fun dynamicKeysRejectBlankAccountIds() {
        assertThrows(IllegalArgumentException::class.java) {
            PreferencesKeys.accountLibraryMediaFilter(" ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            PreferencesKeys.accountLibraryStatusFilter(" ")
        }
    }
}
