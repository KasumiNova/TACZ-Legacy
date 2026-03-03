package com.tacz.legacy.client.resource

import net.minecraft.util.ResourceLocation
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

public class GunPackExternalResourcePackSoundBridgeTest {

    @Rule
    @JvmField
    public val tempFolder: TemporaryFolder = TemporaryFolder()

    @Test
    public fun `sounds ogg requests should resolve from tacz_sounds directory`() {
        val packDir = tempFolder.newFolder("pack").toPath()
        Files.write(packDir.resolve("gunpack.meta.json"), "{}".toByteArray(StandardCharsets.UTF_8))

        val oggPath = packDir.resolve("assets/tacz/tacz_sounds/ak47/ak47_inspect_up.ogg")
        Files.createDirectories(oggPath.parent)
        // Minimal OGG header magic; content doesn't need to be a valid sound for this bridge test.
        Files.write(oggPath, byteArrayOf('O'.code.toByte(), 'g'.code.toByte(), 'g'.code.toByte(), 'S'.code.toByte(), 0, 0, 0, 0))

        val pack = GunPackExternalResourcePackManager.createExternalResourcePackForTests(
            packId = "tacz_default_gun",
            containerPath = packDir,
            isZip = false,
            resourceDomains = setOf("tacz")
        )

        val requested = ResourceLocation("tacz", "sounds/ak47/ak47_inspect_up.ogg")
        assertTrue("resourceExists should be true for bridged sounds path", pack.resourceExists(requested))

        pack.getInputStream(requested).use { input ->
            val header = ByteArray(4)
            val read = input.read(header)
            assertTrue("expected to read OGG header bytes", read >= 4)
            assertTrue("expected OggS header", header.contentEquals(byteArrayOf('O'.code.toByte(), 'g'.code.toByte(), 'g'.code.toByte(), 'S'.code.toByte())))
        }

        val soundsJson = ResourceLocation("tacz", "sounds.json")
        assertTrue("sounds.json should be virtually generated when ogg sounds exist", pack.resourceExists(soundsJson))
        val jsonText = pack.getInputStream(soundsJson).use { it.readBytes().toString(StandardCharsets.UTF_8) }
        assertNotNull(jsonText)
        assertTrue(
            "generated sounds.json should include the key derived from the tacz_sounds file",
            jsonText.contains("\"ak47/ak47_inspect_up\"")
        )
    }

    @Test
    public fun `mp3 sounds should not be registered in generated sounds_json`() {
        val packDir = tempFolder.newFolder("pack_mp3").toPath()
        Files.write(packDir.resolve("gunpack.meta.json"), "{}".toByteArray(StandardCharsets.UTF_8))

        val mp3Path = packDir.resolve("assets/tacz/tacz_sounds/test/test_mp3.mp3")
        Files.createDirectories(mp3Path.parent)
        Files.write(mp3Path, byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 3))

        val pack = GunPackExternalResourcePackManager.createExternalResourcePackForTests(
            packId = "some_pack",
            containerPath = packDir,
            isZip = false,
            resourceDomains = setOf("tacz")
        )

        val soundsJson = ResourceLocation("tacz", "sounds.json")
        val requestedOgg = ResourceLocation("tacz", "sounds/test/test_mp3.ogg")
        val transcodingAvailable = GunPackExternalResourcePackManager.isAudioCompatTranscodingAvailableForTests()
        val nonOggKeyGenerationEnabled = GunPackExternalResourcePackManager.isAudioCompatNonOggKeyGenerationEnabledForTests()
        if (transcodingAvailable && nonOggKeyGenerationEnabled) {
            assertTrue(
                "with transcoder + non-ogg key generation, sounds.json should include compatible fallback keys",
                pack.resourceExists(soundsJson)
            )
            assertTrue("when transcoder is available, mp3 source should be considered a compatible fallback", pack.resourceExists(requestedOgg))
        } else {
            assertFalse(
                "sounds.json should not be generated when only non-ogg files exist and compatibility generation is disabled",
                pack.resourceExists(soundsJson)
            )
            assertFalse("without transcoder, mp3 should not satisfy an .ogg request", pack.resourceExists(requestedOgg))
        }
    }

    @Test
    public fun `zip packs should stream entry input without loading whole file`() {
        val zipFile = tempFolder.newFile("pack.zip").toPath()

        val entryPath = "assets/tacz/tacz_sounds/test/zip_sound.ogg"
        val payload = byteArrayOf(
            'O'.code.toByte(), 'g'.code.toByte(), 'g'.code.toByte(), 'S'.code.toByte(),
            1, 2, 3, 4, 5
        )

        ZipOutputStream(Files.newOutputStream(zipFile)).use { out ->
            out.putNextEntry(ZipEntry("gunpack.meta.json"))
            out.write("{}".toByteArray(StandardCharsets.UTF_8))
            out.closeEntry()

            out.putNextEntry(ZipEntry(entryPath))
            out.write(payload)
            out.closeEntry()
        }

        val pack = GunPackExternalResourcePackManager.createExternalResourcePackForTests(
            packId = "zip_pack",
            containerPath = zipFile,
            isZip = true,
            resourceDomains = setOf("tacz")
        )

        val requested = ResourceLocation("tacz", "sounds/test/zip_sound.ogg")
        assertTrue("resourceExists should be true for bridged zip sounds path", pack.resourceExists(requested))

        val readBack = pack.getInputStream(requested).use { it.readBytes() }
        assertTrue("expected streamed zip entry to match payload", readBack.contentEquals(payload))
    }
}
