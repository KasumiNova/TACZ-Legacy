package com.tacz.legacy.common.resource

import com.google.gson.JsonParser
import com.google.gson.stream.JsonReader
import net.minecraft.util.ResourceLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.io.StringReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class TACZPackConvertorTest {
    @Test
    fun `legacy zip pack converts to new layout and converted pack can be loaded`() {
        val tempRoot = Files.createTempDirectory("tacz-convert").toFile()
        try {
            TACZGunPackRuntimeRegistry.clearForTests()
            val legacyZip = File(tempRoot, "legacy_pack.zip")
            createLegacyPackZip(legacyZip)

            val legacyPack = TACZPackConvertor.fromZipFile(legacyZip)
            assertNotNull(legacyPack)

            val converted = legacyPack!!.convertTo(File(tempRoot, "converted"))
            ZipFile(converted).use { zip ->
                assertNotNull(zip.getEntry("gunpack.meta.json"))
                assertNotNull(zip.getEntry("assets/legacy/gunpack_info.json"))
                assertNotNull(zip.getEntry("assets/legacy/display/guns/test_rifle_display.json"))
                assertNotNull(zip.getEntry("data/legacy/index/guns/test_rifle.json"))
                assertNotNull(zip.getEntry("data/legacy/data/guns/test_rifle_data.json"))
                val recipeEntry = requireNotNull(zip.getEntry("data/legacy/recipes/test_recipe.json"))
                val recipeJson = zip.getInputStream(recipeEntry).bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
                val recipeObject = JsonParser().parse(JsonReader(StringReader(recipeJson)).apply { isLenient = true }).asJsonObject
                assertEquals("tacz:gun_smith_table_crafting", recipeObject.get("type").asString)
            }

            val gameDir = File(tempRoot, "game").apply { mkdirs() }
            File(gameDir, "tacz").apply { mkdirs() }
            converted.copyTo(File(gameDir, "tacz/${converted.name}"), overwrite = true)
            val snapshot = TACZGunPackRuntimeRegistry.reload(gameDir)
            assertTrue(snapshot.guns.containsKey(ResourceLocation("legacy", "test_rifle")))
        } finally {
            TACZGunPackRuntimeRegistry.clearForTests()
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun `version checker rejects missing dependency`() {
        val tempDir = Files.createTempDirectory("tacz-version-check").toFile()
        try {
            val legacyDir = File(tempDir, "legacy").apply { mkdirs() }
            File(legacyDir, "pack.json").writeText(
                """
                    {
                      "dependencies": {
                        "definitely_missing_mod": "[1.0,)"
                      }
                    }
                """.trimIndent(),
                StandardCharsets.UTF_8,
            )
            TACZPackVersionChecker.clearCache()
            assertFalse(TACZPackVersionChecker.match(legacyDir))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun createLegacyPackZip(target: File): Unit {
        FileOutputStream(target).use { output ->
            ZipOutputStream(output).use { zip ->
                writeEntry(zip, "legacy/pack.json", """
                    {
                      "version": "1.0.0",
                      "name": "legacy.pack.name"
                    }
                """.trimIndent())
                writeEntry(zip, "legacy/guns/display/test_rifle_display.json", "{}")
                writeEntry(zip, "legacy/guns/index/test_rifle.json", """
                    {
                      "name": "legacy.gun.test_rifle.name",
                      "display": "legacy:test_rifle_display",
                      "data": "legacy:test_rifle_data",
                      "type": "rifle"
                    }
                """.trimIndent())
                writeEntry(zip, "legacy/guns/data/test_rifle_data.json", """
                    {
                      "ammo": "legacy:test_round",
                      "ammo_amount": 20,
                      "rpm": 520
                    }
                """.trimIndent())
                writeEntry(zip, "legacy/recipes/test_recipe.json", """
                    {
                      "result": "legacy:test_rifle"
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
