package io.morgan.mio.data.account.mapper

import io.morgan.mio.core.database.model.StoredConnectedProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportedIdentityFactoryTest {
    @Test
    fun resultsAreDeterministicAcrossCalls() {
        val first = ImportedIdentityFactory.accountId(StoredConnectedProvider.ANILIST, "9876543210")
        val second = ImportedIdentityFactory.accountId(StoredConnectedProvider.ANILIST, "9876543210")

        assertEquals(first, second)
    }

    @Test
    fun resultsDifferAcrossKinds() {
        val accountId = ImportedIdentityFactory.accountId(StoredConnectedProvider.ANILIST, "9876543210")
        val mediaId = ImportedIdentityFactory.mediaId(StoredConnectedProvider.ANILIST, "9876543210")
        val entryId = ImportedIdentityFactory.collectionEntryId(StoredConnectedProvider.ANILIST, "9876543210")

        assertNotEquals(accountId.value, mediaId.value)
        assertNotEquals(mediaId.value, entryId.value)
        assertNotEquals(accountId.value, entryId.value)
    }

    @Test
    fun resultsDifferAcrossProviders() {
        val anilist = ImportedIdentityFactory.accountId(StoredConnectedProvider.ANILIST, "9876543210")
        val myAnimeList = ImportedIdentityFactory.accountId(StoredConnectedProvider.MY_ANIME_LIST, "9876543210")

        assertNotEquals(anilist.value, myAnimeList.value)
    }

    @Test
    fun resultsDifferAcrossRemoteIds() {
        val first = ImportedIdentityFactory.accountId(StoredConnectedProvider.ANILIST, "9876543210")
        val second = ImportedIdentityFactory.accountId(StoredConnectedProvider.ANILIST, "9876543211")

        assertNotEquals(first.value, second.value)
    }

    @Test
    fun resultIsThirtyTwoCharacterLowercaseHexadecimal() {
        val result = ImportedIdentityFactory.accountId(StoredConnectedProvider.ANILIST, "9876543210")

        assertEquals(32, result.value.length)
        assertTrue(result.value.all { char -> char in "0123456789abcdef" })
    }

    @Test
    fun blankRemoteIdIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            ImportedIdentityFactory.accountId(StoredConnectedProvider.ANILIST, " ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ImportedIdentityFactory.mediaId(StoredConnectedProvider.ANILIST, "")
        }
    }

    @Test
    fun resultContainsNoSubstringOfADistinctiveRemoteId() {
        val remoteId = "9876543210"
        val result = ImportedIdentityFactory.accountId(StoredConnectedProvider.ANILIST, remoteId)

        assertTrue(!result.value.contains(remoteId))
    }

    @Test
    fun existingMediaIdsRemainStableAfterHashExtraction() {
        assertEquals(
            "6287a3fe1ff569e5bd40f004c4d6bacd",
            ImportedIdentityFactory.mediaId(StoredConnectedProvider.ANILIST, "9876543210").value,
        )
    }
}
