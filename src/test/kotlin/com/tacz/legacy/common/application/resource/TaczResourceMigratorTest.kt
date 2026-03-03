package com.tacz.legacy.common.application.resource

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator

public class TaczResourceMigratorTest {

    private val migrator: TaczResourceMigrator = TaczResourceMigrator()

    @Test
    public fun `migrator should build manifest from relative paths`() {
        val manifest = migrator.buildManifestFromRelativePaths(
            relativePaths = listOf(
                "textures/gui/hud.png",
                "custom/tacz_default_gun/data/tacz/data/guns/ak47_data.json",
                "lang/zh_cn.json",
                "custom/tacz_default_gun/README.txt",
                "unknown/foo.bar"
            ),
            sourceRootLabel = "sample-assets"
        )

        assertEquals(1, manifest.schemaVersion)
        assertEquals("sample-assets", manifest.sourceRoot)
        assertEquals(5, manifest.totalFiles)
        assertEquals(4, manifest.mappedCount)
        assertEquals(0.8, manifest.coverageRatio, 0.0001)
        assertEquals(1, manifest.actionCounts[ResourceMappingAction.DIRECT_COPY])
        assertEquals(1, manifest.actionCounts[ResourceMappingAction.COPY_TO_GUNPACK])
        assertEquals(1, manifest.actionCounts[ResourceMappingAction.CONVERT_LANG_JSON_TO_LANG])
        assertEquals(1, manifest.actionCounts[ResourceMappingAction.IGNORE])
        assertEquals(1, manifest.actionCounts[ResourceMappingAction.MANUAL_REVIEW])
        assertEquals(listOf("unknown/foo.bar"), manifest.manualReviewSamples)
    }

    @Test
    public fun `migrator should scan source root and produce sorted entries`() {
        val root = Files.createTempDirectory("tacz-resource-migrator-")
        try {
            createFile(root, "custom/sample_pack/gunpack.meta.json", "{}")
            createFile(root, "custom/sample_pack/data/tacz/data/guns/a_data.json", "{}")
            createFile(root, "textures/hud/reticle.png", "x")

            val manifest = migrator.buildManifestFromSourceRoot(root)

            assertEquals(3, manifest.totalFiles)
            assertTrue(manifest.coverageRatio >= 1.0)
            assertEquals(
                listOf(
                    "custom/sample_pack/data/tacz/data/guns/a_data.json",
                    "custom/sample_pack/gunpack.meta.json",
                    "textures/hud/reticle.png"
                ),
                manifest.entries.map { it.sourcePath }
            )
        } finally {
            deleteRecursively(root)
        }
    }

    @Test
    public fun `manifest writer should write pretty json file`() {
        val outputRoot = Files.createTempDirectory("tacz-resource-manifest-")
        try {
            val manifest = migrator.buildManifestFromRelativePaths(
                listOf("textures/gui/hud.png"),
                "sample-assets"
            )

            val output = outputRoot.resolve("reports").resolve("resource-mapping.json")
            val writer = TaczResourceMigrationManifestWriter()
            writer.writeToFile(manifest, output)

            assertTrue(Files.exists(output))
            val json = String(Files.readAllBytes(output), StandardCharsets.UTF_8)
            val rootObj = JsonParser().parse(json).asJsonObject

            assertEquals(1, rootObj.get("schemaVersion").asInt)
            assertEquals("sample-assets", rootObj.get("sourceRoot").asString)
            assertEquals(1, rootObj.get("totalFiles").asInt)
            assertEquals(1, rootObj.getAsJsonArray("entries").size())
        } finally {
            deleteRecursively(outputRoot)
        }
    }

    private fun createFile(root: Path, relativePath: String, content: String) {
        val file = root.resolve(relativePath)
        Files.createDirectories(file.parent)
        Files.write(file, content.toByteArray(StandardCharsets.UTF_8))
    }

    private fun deleteRecursively(root: Path) {
        if (!Files.exists(root)) {
            return
        }

        Files.walk(root).use { stream ->
            stream.sorted(Comparator.reverseOrder()).forEach { path ->
                Files.deleteIfExists(path)
            }
        }
    }

}
