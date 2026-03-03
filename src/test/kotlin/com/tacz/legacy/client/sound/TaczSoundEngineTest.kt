package com.tacz.legacy.client.sound

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import org.lwjgl.openal.AL10
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.concurrent.CopyOnWriteArrayList

public class TaczSoundEngineTest {

    @After
    public fun tearDown() {
        // Ensure singleton state does not leak across tests.
        TaczSoundEngine.cleanup()
    }

    @Test
    public fun `queue preload should replace previous queued sources`() {
        val sourceA = FakeSoundPackSource("a")
        val sourceB = FakeSoundPackSource("b")

        TaczSoundEngine.queuePreloadFromPacks(listOf(sourceA))
        TaczSoundEngine.queuePreloadFromPacks(listOf(sourceB))

        val queued = deferredPreloadSourcesSnapshot()
        assertEquals("queue should keep latest source list only", 1, queued.size)
        assertTrue("queue should be replaced by second call", queued[0] === sourceB)
        assertTrue("hasDeferredPreload should be true after queueing", hasDeferredPreloadFlag())
    }

    @Test
    public fun `cleanup should clear deferred preload state`() {
        val source = FakeSoundPackSource("cleanup")
        TaczSoundEngine.queuePreloadFromPacks(listOf(source))

        TaczSoundEngine.cleanup()

        assertFalse("hasDeferredPreload should be reset by cleanup", hasDeferredPreloadFlag())
        assertTrue("deferred queue should be cleared by cleanup", deferredPreloadSourcesSnapshot().isEmpty())
        assertEquals("loaded sounds should be cleared by cleanup", 0, TaczSoundEngine.loadedSoundCount())
    }

    @Test
    public fun `preload should fallback to deferred queue when openal native is unavailable`() {
        Assume.assumeFalse("test requires unavailable OpenAL native binding", isOpenAlNativeCallable())

        val source = FakeSoundPackSource("fallback")

        TaczSoundEngine.preloadFromPacks(listOf(source))

        assertFalse(
            "discover should not run when immediate preload is skipped due to unavailable OpenAL",
            source.discoverCalled
        )
        assertTrue("hasDeferredPreload should be set after fallback queueing", hasDeferredPreloadFlag())
        assertEquals("deferred queue should contain exactly one source", 1, deferredPreloadSourcesSnapshot().size)
    }

    @Suppress("UNCHECKED_CAST")
    private fun deferredPreloadSourcesSnapshot(): List<TaczSoundEngine.SoundPackSource> {
        val field = TaczSoundEngine::class.java.getDeclaredField("deferredPreloadSources")
        field.isAccessible = true
        val list = field.get(TaczSoundEngine) as CopyOnWriteArrayList<TaczSoundEngine.SoundPackSource>
        return list.toList()
    }

    private fun hasDeferredPreloadFlag(): Boolean {
        val field = TaczSoundEngine::class.java.getDeclaredField("hasDeferredPreload")
        field.isAccessible = true
        return field.getBoolean(TaczSoundEngine)
    }

    private fun isOpenAlNativeCallable(): Boolean {
        return try {
            AL10.alGetError()
            true
        } catch (_: Throwable) {
            false
        }
    }

    private class FakeSoundPackSource(
        private val keySuffix: String
    ) : TaczSoundEngine.SoundPackSource {

        var discoverCalled: Boolean = false

        override fun discoverSoundEntries(): List<TaczSoundEngine.SoundEntry> {
            discoverCalled = true
            return listOf(
                object : TaczSoundEngine.SoundEntry {
                    override val soundKey: String = "tacz:test/$keySuffix"

                    override fun openStream(): InputStream {
                        // Minimal OggS header; decode success is irrelevant for this test class.
                        return ByteArrayInputStream(
                            byteArrayOf(
                                'O'.code.toByte(),
                                'g'.code.toByte(),
                                'g'.code.toByte(),
                                'S'.code.toByte(),
                                0,
                                0,
                                0,
                                0
                            )
                        )
                    }
                }
            )
        }
    }
}
