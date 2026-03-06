package com.tacz.legacy.common.resource

import net.minecraft.util.ResourceLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class TACZGunPackRuntimeTest {
    @Test
    fun `exporter loads bundled default pack and modern kinetic gun resolves loaded id`() {
        val gameDir = Files.createTempDirectory("tacz-runtime").toFile()
        try {
            TACZGunPackRuntimeRegistry.clearForTests()

            val exportResult = DefaultGunPackExporter.exportIfNeeded(gameDir)
            val snapshot = TACZGunPackRuntimeRegistry.getSnapshot()

            assertTrue(exportResult.targetDirectory.isDirectory)
            assertTrue(snapshot.packs.isNotEmpty())
            assertTrue(snapshot.guns.size >= 40)
            assertTrue(snapshot.attachments.size >= 50)
            assertTrue(snapshot.gunItemTypes().contains("modern_kinetic"))

            val resolvedGunId = snapshot.resolveDefaultGunId("modern_kinetic")
            assertNotNull(resolvedGunId)
            assertTrue(snapshot.guns.containsKey(resolvedGunId))
        } finally {
            TACZGunPackRuntimeRegistry.clearForTests()
            gameDir.deleteRecursively()
        }
    }

    @Test
    fun `scanner loads custom zip pack and links attachment modifiers`() {
        val gameDir = Files.createTempDirectory("tacz-runtime-zip").toFile()
        try {
            TACZGunPackRuntimeRegistry.clearForTests()
            val packDirectory = File(gameDir, "tacz").apply { mkdirs() }
            createDemoPackZip(File(packDirectory, "demo_pack.zip"))

            val snapshot = TACZGunPackRuntimeRegistry.reload(gameDir)
            val gunId = ResourceLocation("demo", "test_rifle")
            val attachmentId = ResourceLocation("demo", "test_scope")

            assertEquals(1, snapshot.packs.size)
            assertTrue(snapshot.packInfos.containsKey("demo"))
            assertTrue(snapshot.guns.containsKey(gunId))
            assertTrue(snapshot.attachments.containsKey(attachmentId))
            assertEquals(1, snapshot.ammos.size)
            assertTrue(snapshot.attachments.getValue(attachmentId).data.modifiers.containsKey("ads"))
            assertTrue(snapshot.attachments.getValue(attachmentId).data.modifiers.containsKey("recoil"))
        } finally {
            TACZGunPackRuntimeRegistry.clearForTests()
            gameDir.deleteRecursively()
        }
    }

    private fun createDemoPackZip(target: File): Unit {
        FileOutputStream(target).use { output ->
            ZipOutputStream(output).use { zip ->
                writeEntry(zip, "gunpack.meta.json", """
                    {
                      "namespace": "demo"
                    }
                """.trimIndent())
                writeEntry(zip, "assets/demo/gunpack_info.json", """
                    {
                      // version comment should survive lenient parsing
                      "version": "1.0.0",
                      "name": "pack.demo.name"
                    }
                """.trimIndent())
                writeEntry(zip, "data/demo/index/guns/test_rifle.json", """
                    {
                      "name": "demo.gun.test_rifle.name",
                      "display": "demo:test_rifle_display",
                      "data": "demo:test_rifle_data",
                      "type": "rifle",
                      "item_type": "modern_kinetic",
                      "sort": 7
                    }
                """.trimIndent())
                writeEntry(zip, "data/demo/data/guns/test_rifle_data.json", """
                    {
                      "ammo": "demo:test_round",
                      "ammo_amount": 30,
                      "rpm": 600,
                      "weight": 3.1,
                      "aim_time": 0.15,
                      "allow_attachment_types": ["scope"]
                    }
                """.trimIndent())
                writeEntry(zip, "data/demo/index/attachments/test_scope.json", """
                    {
                      "name": "demo.attachment.test_scope.name",
                      "display": "demo:test_scope_display",
                      "data": "demo:test_scope_data",
                      "type": "scope",
                      "sort": 3
                    }
                """.trimIndent())
                writeEntry(zip, "data/demo/data/attachments/test_scope_data.json", """
                    {
                      "weight": 0.25,
                      "ads_addend": -0.05,
                      "recoil_modifier": {
                        "pitch": 0.1,
                        "yaw": -0.2
                      }
                    }
                """.trimIndent())
                writeEntry(zip, "data/demo/index/ammo/test_round.json", """
                    {
                      "name": "demo.ammo.test_round.name",
                      "display": "demo:test_round_display",
                      "stack_size": 16
                    }
                """.trimIndent())
            }
        }
    }

    private fun writeEntry(zip: ZipOutputStream, path: String, content: String): Unit {
        zip.putNextEntry(ZipEntry(path))
        zip.write(content.toByteArray(StandardCharsets.UTF_8))
        zip.closeEntry()
    }
}
