package com.wanderwildwood.kirinuki.net

import com.wanderwildwood.kirinuki.net.gemini.GeminiClient
import com.wanderwildwood.kirinuki.net.gemini.GeminiResponse
import com.wanderwildwood.kirinuki.net.gopher.GopherClient
import com.wanderwildwood.kirinuki.net.gopher.GopherResponse
import com.wanderwildwood.kirinuki.net.spartan.SpartanClient
import com.wanderwildwood.kirinuki.net.spartan.SpartanResponse
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.instance

/**
 * What a smolnet address holds, as text, whichever of the three protocols it speaks.
 *
 * The tour needs this and so does the page screen, and they have to agree: a page the
 * sync stored and a page the screen fetched must be the same text, or reading offline
 * shows something subtly different from reading online.
 */
class SmolnetFetcher(
    override val di: DI,
) : DIAware {
    private val gemini: GeminiClient by instance()
    private val gopher: GopherClient by instance()
    private val spartan: SpartanClient by instance()

    /** The text, or null when the address holds something this app does not read. */
    fun fetchText(url: String): String? =
        when {
            isGeminiUrl(url) ->
                (gemini.fetch(url) as? GeminiResponse.Body)
                    ?.takeIf { it.isGemtext }
                    ?.text()

            isSpartanUrl(url) ->
                (spartan.fetch(url) as? SpartanResponse.Body)
                    ?.takeIf { it.isGemtext }
                    ?.text

            isGopherUrl(url) ->
                when (val response = gopher.fetch(url)) {
                    is GopherResponse.Menu -> response.text
                    is GopherResponse.Text -> response.text
                    is GopherResponse.Unsupported -> null
                }

            else -> null
        }
}
