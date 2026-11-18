package com.wanderwildwood.kirinuki.blob

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class BlobTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun aWrittenTextReadsBackAndLeavesNothingAside() {
        val dir = tmp.newFolder("articles")
        writeBlob(3L, dir, "Tomas Reyes on the morning ferry.")

        assertEquals("Tomas Reyes on the morning ferry.", blobInputStream(3L, dir).bufferedReader().use { it.readText() })
        assertFalse(File(blobFile(3L, dir).path + ".part").exists())
    }

    @Test
    fun aSecondWriteReplacesTheFirst() {
        val dir = tmp.newFolder("articles")
        writeBlob(4L, dir, "First draft.")
        writeBlob(4L, dir, "Second draft.")

        assertEquals("Second draft.", blobInputStream(4L, dir).bufferedReader().use { it.readText() })
    }

    @Test
    fun aWriteThatCannotBeMovedIntoPlaceLeavesNoPartFile() {
        val dir = tmp.newFolder("articles")
        // A non-empty directory where the file should go: the move fails.
        blobFile(5L, dir).mkdirs()
        File(blobFile(5L, dir), "inside").writeText("x")

        assertFailsWith<IOException> { writeBlob(5L, dir, "Ada Whitlock's notes.") }
        assertFalse(File(blobFile(5L, dir).path + ".part").exists())
    }
}
