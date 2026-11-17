package com.wanderwildwood.kirinuki.net.gopher

import org.junit.Test
import kotlin.test.assertFailsWith

class GopherClientTest {
    // Port 1 on localhost: if the check let the address through, the connect would fail
    // with an IOException instead, so the type of the failure is what proves the point.
    private val client = GopherClient(connectTimeoutMs = 500, readTimeoutMs = 500)

    @Test
    fun `a selector holding an encoded line break is refused before any connection`() {
        assertFailsWith<GopherException> {
            client.fetch("gopher://127.0.0.1:1/0/foo%0D%0ASET%20x%201")
        }
    }

    @Test
    fun `a bare encoded newline is refused too`() {
        assertFailsWith<GopherException> {
            client.fetch("gopher://127.0.0.1:1/1/foo%0Abar")
        }
    }

    @Test
    fun `an ordinary selector gets as far as the connection`() {
        assertFailsWith<java.io.IOException> {
            client.fetch("gopher://127.0.0.1:1/0/about.txt")
        }
    }
}
