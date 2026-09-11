package com.wanderwildwood.kirinuki.ui.gemini

import android.app.Application
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.wanderwildwood.kirinuki.R
import com.wanderwildwood.kirinuki.base.DIAwareViewModel
import com.wanderwildwood.kirinuki.model.gemtext.GemtextParser
import com.wanderwildwood.kirinuki.model.html.LinearArticle
import com.wanderwildwood.kirinuki.net.isGopherUrl
import com.wanderwildwood.kirinuki.net.isSpartanUrl
import com.wanderwildwood.kirinuki.net.spartan.SpartanResponse
import com.wanderwildwood.kirinuki.net.spartan.SpartanClient
import com.wanderwildwood.kirinuki.net.gopher.GopherResponse
import com.wanderwildwood.kirinuki.net.gopher.GopherClient
import com.wanderwildwood.kirinuki.model.gopher.GopherMenuParser
import com.wanderwildwood.kirinuki.net.gemini.GeminiClient
import com.wanderwildwood.kirinuki.net.gemini.GeminiResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.kodein.di.DI
import org.kodein.di.instance
import java.security.cert.CertificateException

sealed interface GeminiPageState {
    data object Loading : GeminiPageState

    data class Page(
        val article: LinearArticle,
    ) : GeminiPageState

    /**
     * A gopher text file, which is fixed-width by nature: it was laid out against 80
     * columns and reflowing it destroys tables, art and anything aligned. Kept as one
     * block for the screen to fit rather than turned into paragraphs.
     */
    data class Preformatted(
        val text: String,
    ) : GeminiPageState

    /** Said in words, because an empty screen is not an answer. */
    data class Problem(
        val message: String,
        val detail: String?,
    ) : GeminiPageState
}

class GeminiPageViewModel(
    di: DI,
) : DIAwareViewModel(di) {
    private val client: GeminiClient by instance()
    private val gopherClient: GopherClient by instance()
    private val spartanClient: SpartanClient by instance()

    private val _state = MutableStateFlow<GeminiPageState>(GeminiPageState.Loading)
    val state: StateFlow<GeminiPageState> = _state

    private val _url = MutableStateFlow("")
    val url: StateFlow<String> = _url

    fun load(url: String) {
        if (_url.value == url && _state.value is GeminiPageState.Page) return
        _url.value = url
        _state.value = GeminiPageState.Loading

        viewModelScope.launch {
            _state.value = fetch(url)
        }
    }

    fun reload() {
        val url = _url.value
        if (url.isNotBlank()) {
            _state.value = GeminiPageState.Loading
            viewModelScope.launch { _state.value = fetch(url) }
        }
    }

    private suspend fun fetch(url: String): GeminiPageState =
        withContext(Dispatchers.IO) {
            val app = getApplication<Application>()
            try {
                if (isGopherUrl(url)) {
                    return@withContext fetchGopher(url, app)
                }
                if (isSpartanUrl(url)) {
                    return@withContext fetchSpartan(url, app)
                }
                when (val response = client.fetch(url)) {
                    is GeminiResponse.Body ->
                        if (response.isGemtext) {
                            GeminiPageState.Page(GemtextParser(response.url).parse(response.text()))
                        } else {
                            GeminiPageState.Problem(
                                app.getString(R.string.gemini_not_gemtext),
                                response.mimeType,
                            )
                        }

                    is GeminiResponse.Input ->
                        // 1x wants a query string back. Nothing in this app can ask for
                        // one yet, so say what the capsule wanted rather than pretend.
                        GeminiPageState.Problem(
                            app.getString(R.string.gemini_wants_input),
                            response.prompt,
                        )

                    is GeminiResponse.CertificateRequired ->
                        GeminiPageState.Problem(
                            app.getString(R.string.gemini_wants_certificate),
                            response.message,
                        )

                    is GeminiResponse.Failure ->
                        GeminiPageState.Problem(
                            app.getString(R.string.gemini_refused, response.status),
                            response.message.ifBlank { null },
                        )
                }
            } catch (e: CertificateException) {
                // The interesting failure: the host is not showing what it showed before.
                GeminiPageState.Problem(
                    app.getString(R.string.gemini_certificate_changed),
                    e.message,
                )
            } catch (e: Exception) {
                Log.e(LOG_TAG, "Could not reach $url", e)
                GeminiPageState.Problem(
                    app.getString(R.string.gemini_unreachable),
                    e.message,
                )
            }
        }

    /**
     * Gopher says what a thing is in its *address* rather than its response, so a menu
     * and a text file are told apart before the fetch. Both end up as the same
     * [LinearArticle] gemtext does, which is why this screen renders either.
     */
    private fun fetchGopher(
        url: String,
        app: Application,
    ): GeminiPageState =
        when (val response = gopherClient.fetch(url)) {
            is GopherResponse.Menu ->
                GeminiPageState.Page(GopherMenuParser().parse(response.text))

            is GopherResponse.Text -> GeminiPageState.Preformatted(response.text)

            is GopherResponse.Unsupported ->
                GeminiPageState.Problem(
                    app.getString(R.string.gopher_unsupported_type),
                    response.type.toString(),
                )
        }

    /**
     * Spartan carries gemtext, so past the status line this is the same reader as Gemini.
     */
    private fun fetchSpartan(
        url: String,
        app: Application,
    ): GeminiPageState =
        when (val response = spartanClient.fetch(url)) {
            is SpartanResponse.Body ->
                if (response.isGemtext) {
                    GeminiPageState.Page(GemtextParser(response.url).parse(response.text))
                } else {
                    GeminiPageState.Problem(
                        app.getString(R.string.gemini_not_gemtext),
                        response.mimeType,
                    )
                }

            is SpartanResponse.Failure ->
                GeminiPageState.Problem(
                    app.getString(R.string.gemini_refused, response.status),
                    response.message.ifBlank { null },
                )
        }

    companion object {
        private const val LOG_TAG = "KIRINUKI_GEMINI"
    }
}
