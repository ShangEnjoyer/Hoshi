package com.example.tvcatalog.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyServerTest {

    @Test
    fun parsesTrimmedApiKey() {
        val key = "0123456789abcdef0123456789abcdef"

        assertEquals(key, parseTmdbKey("key=%20$key%20"))
    }

    @Test
    fun rejectsMalformedApiKeys() {
        assertNull(parseTmdbKey("key=short"))
        assertNull(parseTmdbKey("key=0123456789abcdef0123456789abcde"))
        assertNull(parseTmdbKey("key=0123456789abcdef0123456789abcde!"))
        assertNull(parseTmdbKey("other=0123456789abcdef0123456789abcdef"))
    }

    @Test
    fun pairingTokenIsUrlSafeAndUses192BitsOfRandomness() {
        val token = newPairingToken()

        assertEquals(32, token.length)
        assertTrue(token.matches(Regex("^[A-Za-z0-9_-]+$")))
    }
}
