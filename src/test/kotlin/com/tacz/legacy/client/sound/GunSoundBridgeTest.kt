package com.tacz.legacy.client.sound

import com.tacz.legacy.api.client.animation.AnimationSoundChannelContent
import com.tacz.legacy.api.client.animation.ObjectAnimationSoundChannel
import net.minecraft.util.ResourceLocation
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class GunSoundBridgeTest {
    @After
    fun tearDown() {
        GunSoundPlayManager.resetPlaybackBackendForTesting()
    }

    @Test
    fun `asset locator reads sound from directory pack`() {
        val root = Files.createTempDirectory("tacz-sound-pack-dir")
        val soundPath = root.resolve("assets/example/sounds/weapons/test.ogg")
        Files.createDirectories(soundPath.parent)
        val expected = "directory-ogg".toByteArray(StandardCharsets.UTF_8)
        Files.write(soundPath, expected)

        val soundId = ResourceLocation("example", "sounds/weapons/test.ogg")
        assertTrue(GunPackAssetLocator.resourceExists(listOf(root.toFile()), soundId))

        val actual = GunPackAssetLocator.openResource(listOf(root.toFile()), soundId)
            ?.use { it.readBytes() }
        assertNotNull(actual)
        assertArrayEquals(expected, actual)
    }

    @Test
    fun `asset locator reads sound from zip pack`() {
        val zipPath = Files.createTempFile("tacz-sound-pack-zip", ".zip")
        val expected = "zip-ogg".toByteArray(StandardCharsets.UTF_8)
        ZipOutputStream(Files.newOutputStream(zipPath)).use { zip ->
            zip.putNextEntry(ZipEntry("assets/example/sounds/weapons/test.ogg"))
            zip.write(expected)
            zip.closeEntry()
        }

        val soundId = ResourceLocation("example", "sounds/weapons/test.ogg")
        assertTrue(GunPackAssetLocator.resourceExists(listOf(zipPath.toFile()), soundId))

        val actual = GunPackAssetLocator.openResource(listOf(zipPath.toFile()), soundId)
            ?.use { it.readBytes() }
        assertNotNull(actual)
        assertArrayEquals(expected, actual)
    }

    @Test
    fun `sound channel plays keyframes inside open closed interval`() {
        val played = mutableListOf<ResourceLocation>()
        GunSoundPlayManager.setPlaybackBackendForTesting(
            GunSoundPlayManager.SoundPlaybackBackend { _, soundName, _, _, _ ->
                played += soundName
                null
            }
        )

        val content = AnimationSoundChannelContent().apply {
            keyframeTimeS = doubleArrayOf(0.0, 0.25, 0.5)
            keyframeSoundName = arrayOf(
                ResourceLocation("example", "sound/start"),
                ResourceLocation("example", "sound/mid"),
                ResourceLocation("example", "sound/end"),
            )
        }
        val channel = ObjectAnimationSoundChannel(content)

        channel.playSound(0.01, 0.5, null, 16, 1f, 1f)

        assertEquals(
            listOf(
                ResourceLocation("example", "sound/mid"),
                ResourceLocation("example", "sound/end"),
            ),
            played,
        )
    }

    @Test
    fun `sound channel wrap around plays head then tail segments`() {
        val played = mutableListOf<ResourceLocation>()
        GunSoundPlayManager.setPlaybackBackendForTesting(
            GunSoundPlayManager.SoundPlaybackBackend { _, soundName, _, _, _ ->
                played += soundName
                null
            }
        )

        val content = AnimationSoundChannelContent().apply {
            keyframeTimeS = doubleArrayOf(0.0, 0.25, 0.5)
            keyframeSoundName = arrayOf(
                ResourceLocation("example", "sound/start"),
                ResourceLocation("example", "sound/mid"),
                ResourceLocation("example", "sound/end"),
            )
        }
        val channel = ObjectAnimationSoundChannel(content)

        channel.playSound(0.4, 0.1, null, 16, 1f, 1f)

        assertEquals(
            listOf(
                ResourceLocation("example", "sound/start"),
                ResourceLocation("example", "sound/end"),
            ),
            played,
        )
    }
}
