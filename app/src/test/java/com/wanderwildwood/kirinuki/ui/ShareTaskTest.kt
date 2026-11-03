package com.wanderwildwood.kirinuki.ui

import android.content.Intent
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * A share used to be lost when the one before it had gone on into the app: the app's screens
 * sat in a task whose root was that first share, and Android brought the task to the front for
 * every later share (same action, same type -- the address is an extra) without delivering it.
 * These check the two things that keep it from happening again, from the manifest itself.
 */
class ShareTaskTest {
    private val android = "http://schemas.android.com/apk/res/android"

    private fun activity(name: String): Element {
        val manifest = listOf(File("src/main/AndroidManifest.xml"), File("app/src/main/AndroidManifest.xml")).first { it.isFile }
        val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(manifest)
        val activities = doc.getElementsByTagName("activity")
        return (0 until activities.length)
            .map { activities.item(it) as Element }
            .first { it.getAttributeNS(android, "name").endsWith(name) }
    }

    @Test
    fun `the share screen has a task of its own, apart from the app's`() {
        val share = activity(".ui.AddFeedFromShareActivity")
        val affinity = share.getAttributeNS(android, "taskAffinity")
        assertTrue(affinity.isNotEmpty(), "no taskAffinity: it would share the app's task")
        assertNotEquals(activity("MainActivity").getAttributeNS(android, "taskAffinity"), affinity)
    }

    @Test
    fun `a share while the screen is open is delivered, not dropped`() {
        assertEquals("singleTask", activity(".ui.AddFeedFromShareActivity").getAttributeNS(android, "launchMode"))
    }

    @Test
    fun `going on into the app asks for the app's own task`() {
        assertTrue(HAND_OVER_FLAGS and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }
}
