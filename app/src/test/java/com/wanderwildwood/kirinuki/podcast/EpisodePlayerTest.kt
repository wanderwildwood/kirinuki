package com.wanderwildwood.kirinuki.podcast

import androidx.media3.common.Player
import io.mockk.mockk
import io.mockk.mockkClass
import io.mockk.slot
import io.mockk.verify
import org.junit.Test
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The media session hears everything about the episode through the listener [EpisodePlayer]
 * hands on. When that listener passed on nothing but the commands, the session never heard the
 * episode was ready or playing, and Play looked stuck at 0:00.
 */
class EpisodePlayerTest {
    private fun episodePlayer(inner: Player) = EpisodePlayer(inner, mode = { EpisodeButtons.SKIP }, toEpisode = {})

    /** Every callback, with made-up arguments of the right types. */
    private fun argsFor(method: Method): Array<Any?> =
        method.parameterTypes
            .map { type ->
                when (type) {
                    Int::class.javaPrimitiveType -> 2
                    Long::class.javaPrimitiveType -> 7L
                    Float::class.javaPrimitiveType -> 0.5f
                    Boolean::class.javaPrimitiveType -> true
                    List::class.java -> emptyList<Any>()
                    Player.Commands::class.java -> Player.Commands.EMPTY
                    else -> mockkClass(type.kotlin, relaxed = true)
                }
            }.toTypedArray()

    @Test
    fun everyEventReachesTheListener() {
        val inner = mockk<Player>(relaxed = true)
        val handedOn = slot<Player.Listener>()
        val heard = mutableListOf<Pair<String, List<Any?>>>()
        val listener =
            Proxy.newProxyInstance(
                Player.Listener::class.java.classLoader,
                arrayOf(Player.Listener::class.java),
            ) { _, method, args ->
                if (method.declaringClass == Player.Listener::class.java) {
                    heard += method.name to (args?.toList() ?: emptyList())
                }
                null
            } as Player.Listener

        val p = episodePlayer(inner)
        p.addListener(listener)
        // ForwardingPlayer wraps it once more on the way to the real player.
        verify { inner.addListener(capture(handedOn)) }

        val callbacks = Player.Listener::class.java.methods.filter { it.declaringClass == Player.Listener::class.java }
        assertTrue(callbacks.size > 30, "Player.Listener has ${callbacks.size} callbacks")
        for (m in callbacks) {
            heard.clear()
            val args = argsFor(m)
            m.invoke(handedOn.captured, *args)
            assertTrue(heard.isNotEmpty(), "${m.name}(${m.parameterTypes.joinToString { it.simpleName }}) was dropped")
            if (m.name != "onAvailableCommandsChanged" && m.name != "onLoadingChanged") {
                assertEquals(m.name, heard.last().first)
            }
        }
    }

    @Test
    fun aListenerTakenAwayIsTakenAway() {
        val inner = mockk<Player>(relaxed = true)
        val added = slot<Player.Listener>()
        val removed = slot<Player.Listener>()
        val listener = object : Player.Listener {}
        val p = episodePlayer(inner)
        p.addListener(listener)
        p.removeListener(listener)
        verify { inner.addListener(capture(added)) }
        verify { inner.removeListener(capture(removed)) }
        // The very wrapper that was added, or the real player keeps calling a listener that left.
        assertTrue(added.captured === removed.captured)
    }
}
