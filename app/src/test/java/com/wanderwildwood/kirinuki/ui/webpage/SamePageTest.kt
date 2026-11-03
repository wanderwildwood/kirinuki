package com.wanderwildwood.kirinuki.ui.webpage

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SamePageTest {
    @Test
    fun `a footnote on this page is this page`() {
        assertTrue(samePage("https://example.org/a-walk#fn-1", "https://example.org/a-walk"))
        assertTrue(samePage("https://example.org/a-walk#fn-2", "https://example.org/a-walk#fn-1"))
    }

    @Test
    fun `another page is another page`() {
        assertFalse(samePage("https://example.org/the-rain", "https://example.org/a-walk"))
        assertFalse(samePage("https://example.org/a-walk?page=2", "https://example.org/a-walk"))
    }
}
