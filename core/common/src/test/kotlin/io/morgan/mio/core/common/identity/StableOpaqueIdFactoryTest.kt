package io.morgan.mio.core.common.identity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class StableOpaqueIdFactoryTest {
    @Test
    fun createIsDeterministicAndUsesTheCompatibilityVector() {
        val first = StableOpaqueIdFactory.create("anilist", "media", "9876543210")
        val second = StableOpaqueIdFactory.create("anilist", "media", "9876543210")

        assertEquals(first, second)
        assertEquals("6287a3fe1ff569e5bd40f004c4d6bacd", first)
    }

    @Test
    fun namespaceKindAndRemoteIdRemainSeparated() {
        val base = StableOpaqueIdFactory.create("anilist", "media", "101")

        assertNotEquals(base, StableOpaqueIdFactory.create("my_anime_list", "media", "101"))
        assertNotEquals(base, StableOpaqueIdFactory.create("anilist", "account", "101"))
        assertNotEquals(base, StableOpaqueIdFactory.create("anilist", "media", "102"))
    }

    @Test
    fun createReturnsThirtyTwoLowercaseHexCharactersAndRejectsBlanks() {
        val value = StableOpaqueIdFactory.create("anilist", "media", "101")

        assertEquals(32, value.length)
        assertTrue(value.all { character -> character in "0123456789abcdef" })
        listOf(
            {
                StableOpaqueIdFactory.create("", "media", "101")
                Unit
            },
            {
                StableOpaqueIdFactory.create("anilist", " ", "101")
                Unit
            },
            {
                StableOpaqueIdFactory.create("anilist", "media", "")
                Unit
            },
        ).forEach { create -> assertThrows<IllegalArgumentException> { create() } }
    }
}
