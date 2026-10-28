package com.wanderwildwood.kirinuki.model.readaloud

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Speaks a [ReadAloudScript] with the phone's own text-to-speech engine, a sentence to an
 * utterance, and says which sentence it is on.
 *
 * Everything from the chosen sentence to the end is handed to the engine at once, so the engine
 * goes on reading with the screen off and the app asleep. A pause, a step or a seek empties the
 * engine's queue and fills it again from the new sentence; utterances from before that carry an
 * older [generation] in their id and are ignored when they report.
 *
 * Nothing here ticks. [state] changes when a sentence starts and when speaking stops, and that
 * is all the screen is ever asked to redraw for.
 */
class ReadAloudPlayer(
    context: Context,
) {
    enum class Engine { STARTING, READY, NONE }

    data class State(
        val engine: Engine = Engine.STARTING,
        val speaking: Boolean = false,
        val index: Int = 0,
        val finished: Boolean = false,
        /** Characters a second, as heard since the voice last started; null until measured. */
        val pace: Float? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var script: List<Spoken> = emptyList()
    @Volatile
    private var generation = 0

    /** Where and when the voice last started speaking, to measure its pace from. */
    @Volatile
    private var startedIndex = -1

    @Volatile
    private var startedAt = 0L

    /** Asked to speak before the engine answered: done once it does. */
    private var playWhenReady = false

    private val tts: TextToSpeech =
        TextToSpeech(context.applicationContext) { status ->
            val ok = status == TextToSpeech.SUCCESS
            _state.update { it.copy(engine = if (ok) Engine.READY else Engine.NONE) }
            if (ok && playWhenReady) {
                playWhenReady = false
                play()
            }
        }

    init {
        tts.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    val (gen, index) = parse(utteranceId) ?: return
                    if (gen != generation) return
                    val now = android.os.SystemClock.elapsedRealtime()
                    val pace =
                        if (startedIndex < 0 || index < startedIndex) {
                            startedIndex = index
                            startedAt = now
                            null
                        } else {
                            val chars = (startedIndex until index).sumOf { script.getOrNull(it)?.text?.length ?: 0 }
                            ReadAloudTimeline.paceOf(chars, now - startedAt)
                        }
                    _state.update { it.copy(index = index, speaking = true, finished = false, pace = pace ?: it.pace) }
                }

                override fun onDone(utteranceId: String?) {
                    val (gen, index) = parse(utteranceId) ?: return
                    if (gen != generation) return
                    if (index >= script.size - 1) {
                        // The end: the next Play starts from the top.
                        _state.update { it.copy(index = 0, speaking = false, finished = true) }
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    onError(utteranceId, TextToSpeech.ERROR)
                }

                override fun onError(
                    utteranceId: String?,
                    errorCode: Int,
                ) {
                    val (gen, _) = parse(utteranceId) ?: return
                    if (gen != generation) return
                    Log.w(LOG_TAG, "Speech failed: $errorCode")
                    _state.update { it.copy(speaking = false) }
                }

                override fun onStop(
                    utteranceId: String?,
                    interrupted: Boolean,
                ) = Unit
            },
        )
    }

    /** A new text to read, from its start; whatever was being read stops. */
    fun load(newScript: List<Spoken>) {
        if (newScript == script) return
        halt()
        script = newScript
        _state.update { it.copy(index = 0, speaking = false, finished = false) }
    }

    fun play() {
        if (script.isEmpty()) return
        when (_state.value.engine) {
            Engine.STARTING -> {
                playWhenReady = true
                return
            }
            Engine.NONE -> return
            Engine.READY -> Unit
        }
        val from = _state.value.index.coerceIn(0, script.size - 1)
        generation++
        startedIndex = -1
        val params = Bundle()
        for (i in from until script.size) {
            tts.speak(
                script[i].text,
                if (i == from) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD,
                params,
                "$generation:$i",
            )
        }
        _state.update { it.copy(index = from, speaking = true, finished = false) }
    }

    /** Stops where it is; Play goes on from the start of the sentence it was in. */
    fun pause() {
        playWhenReady = false
        halt()
        _state.update { it.copy(speaking = false) }
    }

    /** To the start of sentence [index], still speaking if it was. */
    fun seekTo(index: Int) {
        if (script.isEmpty()) return
        val to = index.coerceIn(0, script.size - 1)
        val wasSpeaking = _state.value.speaking
        halt()
        _state.update { it.copy(index = to, speaking = false, finished = false) }
        if (wasSpeaking) play()
    }

    fun release() {
        playWhenReady = false
        halt()
        tts.shutdown()
    }

    private fun halt() {
        // Anything still queued from before reports under an old generation and is ignored.
        generation++
        tts.stop()
    }

    private fun parse(utteranceId: String?): Pair<Int, Int>? {
        val parts = utteranceId?.split(':') ?: return null
        if (parts.size != 2) return null
        val gen = parts[0].toIntOrNull() ?: return null
        val index = parts[1].toIntOrNull() ?: return null
        return gen to index
    }

    companion object {
        private const val LOG_TAG = "KIRINUKI_READALOUD"
    }
}
