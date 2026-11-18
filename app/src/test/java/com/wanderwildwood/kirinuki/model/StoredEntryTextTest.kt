package com.wanderwildwood.kirinuki.model

import com.wanderwildwood.kirinuki.blob.blobFile
import com.wanderwildwood.kirinuki.blob.blobOutputStream
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals

class StoredEntryTextTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun readsTheTextSavedAtTheFirstSync() {
        val dir = tmp.newFolder("articles")
        blobOutputStream(7L, dir).bufferedWriter().use { it.write("# A walk by the river\nAda Whitlock writes.") }

        assertEquals("# A walk by the river\nAda Whitlock writes.", storedEntryText(7L, dir))
    }

    @Test
    fun noFileIsNoText() {
        val dir = tmp.newFolder("articles")

        assertEquals("", storedEntryText(8L, dir))
    }

    @Test
    fun aFileThatIsNotGzipIsNoText() {
        val dir = tmp.newFolder("articles")
        blobFile(9L, dir).writeText("not gzip")

        assertEquals("", storedEntryText(9L, dir))
    }
}
