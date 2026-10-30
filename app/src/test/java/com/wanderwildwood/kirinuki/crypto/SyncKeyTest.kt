package com.wanderwildwood.kirinuki.crypto

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SyncKeyTest {
    @Test
    fun `key has the two-part base64 form with 16 and 32 bytes`() {
        val parts = SyncKey.generate().split(":")
        assertEquals(2, parts.size)
        assertEquals(16, Base64.getDecoder().decode(parts[0]).size)
        assertEquals(32, Base64.getDecoder().decode(parts[1]).size)
    }

    @Test
    fun `two keys differ`() {
        assertNotEquals(SyncKey.generate(), SyncKey.generate())
    }
}
