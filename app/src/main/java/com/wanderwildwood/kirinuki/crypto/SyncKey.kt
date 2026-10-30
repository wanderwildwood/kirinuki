package com.wanderwildwood.kirinuki.crypto

import java.security.SecureRandom
import java.util.Base64

/**
 * The secret a device keeps for Feeder-style sync, in the form the sync tables already hold:
 * base64(16 random bytes) + ":" + base64(32 random bytes). It was made by a cipher helper that
 * was never used for anything else, so only the string is made here, from SecureRandom.
 */
object SyncKey {
    private const val FIRST_BYTES = 16
    private const val SECOND_BYTES = 32

    fun generate(random: SecureRandom = SecureRandom()): String {
        val encoder = Base64.getEncoder()
        val a = ByteArray(FIRST_BYTES).also(random::nextBytes)
        val b = ByteArray(SECOND_BYTES).also(random::nextBytes)
        return encoder.encodeToString(a) + ":" + encoder.encodeToString(b)
    }
}
