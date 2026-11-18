package com.wanderwildwood.kirinuki.model.opml

import org.junit.Test
import kotlin.test.assertEquals

class ReadTheBytesTest {
    @Test
    fun readsAWholeSmallStream() {
        val bytes = ByteArray(20_000) { (it % 251).toByte() }
        assertEquals(bytes.toList(), bytes.inputStream().readTheBytes(limit = 1_000_000).toList())
    }

    @Test
    fun stopsAtTheLimit() {
        val endless = generateSequence { 'a'.code.toByte() }.take(100_000).toList().toByteArray().inputStream()
        assertEquals(30_001, endless.readTheBytes(limit = 30_001).size)
    }
}
