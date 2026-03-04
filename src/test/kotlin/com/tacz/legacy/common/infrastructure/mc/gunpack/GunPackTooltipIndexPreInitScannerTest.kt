package com.tacz.legacy.common.infrastructure.mc.gunpack

import org.apache.logging.log4j.LogManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

public class GunPackTooltipIndexPreInitScannerTest {

    private val scanner: GunPackTooltipIndexPreInitScanner = GunPackTooltipIndexPreInitScanner()
    private val logger = LogManager.getLogger("TooltipIndexScanTest")

    @Test
    public fun `scan should return empty snapshot when tacz root is missing`() {
        val root = Files.createTempDirectory("tacz-legacy-tooltip-index-empty-")
        try {
            val snapshot = scanner.scan(root, logger)
            assertEquals(0, snapshot.totalEntries)
            assertEquals(0, snapshot.failedSources.size)
        } finally {
            deleteRecursively(root)
        }
    }

    @Test
    public fun `scan should parse directory pack index entries`() {
        val gameRoot = Files.createTempDirectory("tacz-legacy-tooltip-index-dir-")
        try {
            val configRoot = gameRoot.resolve("config")
            Files.createDirectories(configRoot)

            val packRoot = gameRoot.resolve("tacz").resolve("sample_pack")
            stageDirectoryPack(packRoot)

            val snapshot = scanner.scan(configRoot, logger)

            assertEquals(4, snapshot.totalEntries)
            assertEquals(1, snapshot.gunEntriesById.size)
            assertEquals(1, snapshot.attachmentEntriesById.size)
            assertEquals(1, snapshot.ammoEntriesById.size)
            assertEquals(1, snapshot.blockEntriesById.size)
            assertEquals(0, snapshot.failedSources.size)

            val gunEntry = snapshot.findGunEntry("tacz:ak47")
            assertNotNull(gunEntry)
            assertEquals("tacz.gun.ak47.name", gunEntry?.nameKey)
            assertEquals("tacz.gun.ak47.desc", gunEntry?.tooltipKey)
            assertEquals("tacz:ak47_display", gunEntry?.displayId)
            assertEquals("rifle", gunEntry?.type)

            val attachmentEntry = snapshot.findAttachmentEntry("tacz:scope_hamr")
            assertNotNull(attachmentEntry)
            assertNull(attachmentEntry?.tooltipKey)
            assertEquals("scope", attachmentEntry?.type)

            val ammoEntry = snapshot.findAmmoEntry("tacz:9mm")
            assertNotNull(ammoEntry)
            assertNull(ammoEntry?.tooltipKey)

            val blockEntry = snapshot.findBlockEntry("tacz:gun_smith_table")
            assertNotNull(blockEntry)
            assertEquals("tacz.block.gun_smith_table.desc", blockEntry?.tooltipKey)
        } finally {
            deleteRecursively(gameRoot)
        }
    }

    @Test
    public fun `scan should parse zip pack with nested root folder`() {
        val gameRoot = Files.createTempDirectory("tacz-legacy-tooltip-index-zip-")
        try {
            val configRoot = gameRoot.resolve("config")
            Files.createDirectories(configRoot)

            val taczRoot = gameRoot.resolve("tacz")
            Files.createDirectories(taczRoot)

            val zipPath = taczRoot.resolve("sample_pack_nested.zip")
            ZipOutputStream(Files.newOutputStream(zipPath)).use { out ->
                writeZipEntry(out, "sample_pack/gunpack.meta.json", "{}")
                writeZipEntry(out, "sample_pack/data/tacz/index/guns/ak47.json", gunIndexJson())
                writeZipEntry(out, "sample_pack/data/tacz/index/attachments/scope_hamr.json", attachmentIndexJson())
                writeZipEntry(out, "sample_pack/data/tacz/index/ammo/9mm.json", ammoIndexJson())
                writeZipEntry(out, "sample_pack/data/tacz/index/blocks/gun_smith_table.json", blockIndexJson())
            }

            val snapshot = scanner.scan(configRoot, logger)

            assertEquals(4, snapshot.totalEntries)
            assertEquals(0, snapshot.failedSources.size)
            assertEquals("tacz.gun.ak47.desc", snapshot.resolveGunTooltipKey("tacz:ak47"))
            assertEquals("tacz.block.gun_smith_table.desc", snapshot.resolveBlockTooltipKey("tacz:gun_smith_table"))
        } finally {
            deleteRecursively(gameRoot)
        }
    }

    // ─── §2 补充覆盖：边界 + 异常路径 ───────────────────────

    @Test
    public fun `scan should skip directory pack without gunpack meta`() {
        val gameRoot = Files.createTempDirectory("tacz-legacy-tooltip-index-nometa-")
        try {
            val configRoot = gameRoot.resolve("config")
            Files.createDirectories(configRoot)
            val packDir = gameRoot.resolve("tacz").resolve("no_meta_pack")
            Files.createDirectories(packDir)
            writeIndex(packDir, "guns/ak47.json", gunIndexJson())

            val snapshot = scanner.scan(configRoot, logger)
            assertEquals(0, snapshot.totalEntries)
        } finally {
            deleteRecursively(gameRoot)
        }
    }

    @Test
    public fun `scan should skip directory pack without data subdirectory`() {
        val gameRoot = Files.createTempDirectory("tacz-legacy-tooltip-index-nodata-")
        try {
            val configRoot = gameRoot.resolve("config")
            Files.createDirectories(configRoot)
            val packDir = gameRoot.resolve("tacz").resolve("pack_no_data")
            Files.createDirectories(packDir)
            Files.write(packDir.resolve("gunpack.meta.json"), "{}".toByteArray(StandardCharsets.UTF_8))

            val snapshot = scanner.scan(configRoot, logger)
            assertEquals(0, snapshot.totalEntries)
        } finally {
            deleteRecursively(gameRoot)
        }
    }

    @Test
    public fun `scan should skip non-json files in directory pack`() {
        val gameRoot = Files.createTempDirectory("tacz-legacy-tooltip-index-nonjson-")
        try {
            val configRoot = gameRoot.resolve("config")
            Files.createDirectories(configRoot)
            val packDir = gameRoot.resolve("tacz").resolve("sample_pack")
            Files.createDirectories(packDir)
            Files.write(packDir.resolve("gunpack.meta.json"), "{}".toByteArray(StandardCharsets.UTF_8))
            val txtFile = packDir.resolve("data").resolve("tacz").resolve("index").resolve("guns").resolve("readme.txt")
            Files.createDirectories(txtFile.parent)
            Files.write(txtFile, "not json".toByteArray(StandardCharsets.UTF_8))

            val snapshot = scanner.scan(configRoot, logger)
            assertEquals(0, snapshot.totalEntries)
        } finally {
            deleteRecursively(gameRoot)
        }
    }

    @Test
    public fun `scan should skip json files that do not match any category regex`() {
        val gameRoot = Files.createTempDirectory("tacz-legacy-tooltip-index-unmatched-")
        try {
            val configRoot = gameRoot.resolve("config")
            Files.createDirectories(configRoot)
            val packDir = gameRoot.resolve("tacz").resolve("sample_pack")
            Files.createDirectories(packDir)
            Files.write(packDir.resolve("gunpack.meta.json"), "{}".toByteArray(StandardCharsets.UTF_8))
            val unknown = packDir.resolve("data").resolve("tacz").resolve("unknown_dir").resolve("file.json")
            Files.createDirectories(unknown.parent)
            Files.write(unknown, """{"name":"test"}""".toByteArray(StandardCharsets.UTF_8))

            val snapshot = scanner.scan(configRoot, logger)
            assertEquals(0, snapshot.totalEntries)
        } finally {
            deleteRecursively(gameRoot)
        }
    }

    @Test
    public fun `scan should add to failedSources when json is invalid`() {
        val gameRoot = Files.createTempDirectory("tacz-legacy-tooltip-index-badjson-")
        try {
            val configRoot = gameRoot.resolve("config")
            Files.createDirectories(configRoot)
            val packDir = gameRoot.resolve("tacz").resolve("sample_pack")
            Files.createDirectories(packDir)
            Files.write(packDir.resolve("gunpack.meta.json"), "{}".toByteArray(StandardCharsets.UTF_8))
            writeIndex(packDir, "guns/bad.json", "NOT VALID JSON {{{")

            val snapshot = scanner.scan(configRoot, logger)
            assertEquals(0, snapshot.gunEntriesById.size)
            assertTrue(snapshot.failedSources.isNotEmpty())
        } finally {
            deleteRecursively(gameRoot)
        }
    }

    @Test
    public fun `scan should add to failedSources when json is array not object`() {
        val gameRoot = Files.createTempDirectory("tacz-legacy-tooltip-index-notobj-")
        try {
            val configRoot = gameRoot.resolve("config")
            Files.createDirectories(configRoot)
            val packDir = gameRoot.resolve("tacz").resolve("sample_pack")
            Files.createDirectories(packDir)
            Files.write(packDir.resolve("gunpack.meta.json"), "{}".toByteArray(StandardCharsets.UTF_8))
            writeIndex(packDir, "guns/arr.json", "[1,2,3]")

            val snapshot = scanner.scan(configRoot, logger)
            assertEquals(0, snapshot.gunEntriesById.size)
            assertTrue(snapshot.failedSources.isNotEmpty())
        } finally {
            deleteRecursively(gameRoot)
        }
    }

    @Test
    public fun `scan should handle json with non-string name field`() {
        val gameRoot = Files.createTempDirectory("tacz-legacy-tooltip-index-nonstr-")
        try {
            val configRoot = gameRoot.resolve("config")
            Files.createDirectories(configRoot)
            val packDir = gameRoot.resolve("tacz").resolve("sample_pack")
            stageDirectoryPackBase(packDir)
            writeIndex(packDir, "guns/ak47.json", """{"name": 123, "tooltip": true}""")

            val snapshot = scanner.scan(configRoot, logger)
            val entry = snapshot.findGunEntry("tacz:ak47")
            assertNotNull(entry)
            assertNull(entry?.nameKey)
            assertNull(entry?.tooltipKey)
        } finally {
            deleteRecursively(gameRoot)
        }
    }

    @Test
    public fun `scan should strip block comments in json`() {
        val gameRoot = Files.createTempDirectory("tacz-legacy-tooltip-index-blockcomment-")
        try {
            val configRoot = gameRoot.resolve("config")
            Files.createDirectories(configRoot)
            val packDir = gameRoot.resolve("tacz").resolve("sample_pack")
            stageDirectoryPackBase(packDir)
            writeIndex(packDir, "guns/ak47.json", """
                {
                  /* this is a block comment */
                  "name": "tacz.gun.ak47.name",
                  "tooltip": "tacz.gun.ak47.desc"
                }
            """.trimIndent())

            val snapshot = scanner.scan(configRoot, logger)
            val entry = snapshot.findGunEntry("tacz:ak47")
            assertNotNull(entry)
            assertEquals("tacz.gun.ak47.name", entry?.nameKey)
        } finally {
            deleteRecursively(gameRoot)
        }
    }

    @Test
    public fun `scan should handle escaped quotes in json strings`() {
        val gameRoot = Files.createTempDirectory("tacz-legacy-tooltip-index-escaped-")
        try {
            val configRoot = gameRoot.resolve("config")
            Files.createDirectories(configRoot)
            val packDir = gameRoot.resolve("tacz").resolve("sample_pack")
            stageDirectoryPackBase(packDir)
            writeIndex(packDir, "guns/ak47.json", """{"name": "test\"name"}""")

            val snapshot = scanner.scan(configRoot, logger)
            val entry = snapshot.findGunEntry("tacz:ak47")
            assertNotNull(entry)
            assertEquals("test\"name", entry?.nameKey)
        } finally {
            deleteRecursively(gameRoot)
        }
    }

    @Test
    public fun `scan should normalize type token with special chars`() {
        val gameRoot = Files.createTempDirectory("tacz-legacy-tooltip-index-typetok-")
        try {
            val configRoot = gameRoot.resolve("config")
            Files.createDirectories(configRoot)
            val packDir = gameRoot.resolve("tacz").resolve("sample_pack")
            stageDirectoryPackBase(packDir)
            writeIndex(packDir, "guns/ak47.json", """{"name": "n", "type": "Rifle-Type!"}""")

            val snapshot = scanner.scan(configRoot, logger)
            val entry = snapshot.findGunEntry("tacz:ak47")
            assertNotNull(entry)
            assertEquals("rifle_type", entry?.type)
        } finally {
            deleteRecursively(gameRoot)
        }
    }

    @Test
    public fun `scan should support custom namespace in data path`() {
        val gameRoot = Files.createTempDirectory("tacz-legacy-tooltip-index-customns-")
        try {
            val configRoot = gameRoot.resolve("config")
            Files.createDirectories(configRoot)
            val packDir = gameRoot.resolve("tacz").resolve("sample_pack")
            stageDirectoryPackBase(packDir)
            val gunFile = packDir.resolve("data").resolve("mymod").resolve("index").resolve("guns").resolve("m4a1.json")
            Files.createDirectories(gunFile.parent)
            Files.write(gunFile, """{"name": "mymod.m4a1.name"}""".toByteArray(StandardCharsets.UTF_8))

            val snapshot = scanner.scan(configRoot, logger)
            val entry = snapshot.findGunEntry("mymod:m4a1")
            assertNotNull(entry)
            assertEquals("mymod.m4a1.name", entry?.nameKey)
        } finally {
            deleteRecursively(gameRoot)
        }
    }

    @Test
    public fun `scan should skip zip without gunpack meta`() {
        val gameRoot = Files.createTempDirectory("tacz-legacy-tooltip-index-zipnometa-")
        try {
            val configRoot = gameRoot.resolve("config")
            Files.createDirectories(configRoot)
            val taczRoot = gameRoot.resolve("tacz")
            Files.createDirectories(taczRoot)
            ZipOutputStream(Files.newOutputStream(taczRoot.resolve("no_meta.zip"))).use { out ->
                writeZipEntry(out, "data/tacz/index/guns/ak47.json", gunIndexJson())
            }

            val snapshot = scanner.scan(configRoot, logger)
            assertEquals(0, snapshot.totalEntries)
        } finally {
            deleteRecursively(gameRoot)
        }
    }

    @Test
    public fun `scan should skip non-matching zip entries`() {
        val gameRoot = Files.createTempDirectory("tacz-legacy-tooltip-index-zipmismatch-")
        try {
            val configRoot = gameRoot.resolve("config")
            Files.createDirectories(configRoot)
            val taczRoot = gameRoot.resolve("tacz")
            Files.createDirectories(taczRoot)
            ZipOutputStream(Files.newOutputStream(taczRoot.resolve("pack.zip"))).use { out ->
                writeZipEntry(out, "sample_pack/gunpack.meta.json", "{}")
                writeZipEntry(out, "sample_pack/data/tacz/unknowndir/file.json", """{"k":"v"}""")
                writeZipEntry(out, "sample_pack/data/tacz/index/guns/readme.txt", "not json")
            }

            val snapshot = scanner.scan(configRoot, logger)
            assertEquals(0, snapshot.totalEntries)
        } finally {
            deleteRecursively(gameRoot)
        }
    }

    @Test
    public fun `scan should handle json string with null content in zip entry`() {
        val gameRoot = Files.createTempDirectory("tacz-legacy-tooltip-index-zipbadjson-")
        try {
            val configRoot = gameRoot.resolve("config")
            Files.createDirectories(configRoot)
            val taczRoot = gameRoot.resolve("tacz")
            Files.createDirectories(taczRoot)
            ZipOutputStream(Files.newOutputStream(taczRoot.resolve("pack.zip"))).use { out ->
                writeZipEntry(out, "sample_pack/gunpack.meta.json", "{}")
                writeZipEntry(out, "sample_pack/data/tacz/index/guns/broken.json", "<<<invalid>>>")
            }

            val snapshot = scanner.scan(configRoot, logger)
            assertEquals(0, snapshot.gunEntriesById.size)
            assertTrue(snapshot.failedSources.isNotEmpty())
        } finally {
            deleteRecursively(gameRoot)
        }
    }

    @Test
    public fun `scan should read json string with plain string as non-object`() {
        val gameRoot = Files.createTempDirectory("tacz-legacy-tooltip-index-zipstr-")
        try {
            val configRoot = gameRoot.resolve("config")
            Files.createDirectories(configRoot)
            val taczRoot = gameRoot.resolve("tacz")
            Files.createDirectories(taczRoot)
            ZipOutputStream(Files.newOutputStream(taczRoot.resolve("pack.zip"))).use { out ->
                writeZipEntry(out, "sample_pack/gunpack.meta.json", "{}")
                writeZipEntry(out, "sample_pack/data/tacz/index/guns/str.json", "\"just a string\"")
            }

            val snapshot = scanner.scan(configRoot, logger)
            assertEquals(0, snapshot.gunEntriesById.size)
            assertTrue(snapshot.failedSources.isNotEmpty())
        } finally {
            deleteRecursively(gameRoot)
        }
    }

    // ─── helpers ──────────────────────────────────────────────

    private fun stageDirectoryPackBase(packDir: Path) {
        Files.createDirectories(packDir)
        Files.write(packDir.resolve("gunpack.meta.json"), "{}".toByteArray(StandardCharsets.UTF_8))
    }

    private fun stageDirectoryPack(packRoot: Path) {
        stageDirectoryPackBase(packRoot)
        writeIndex(packRoot, "guns/ak47.json", gunIndexJson())
        writeIndex(packRoot, "attachments/scope_hamr.json", attachmentIndexJson())
        writeIndex(packRoot, "ammo/9mm.json", ammoIndexJson())
        writeIndex(packRoot, "blocks/gun_smith_table.json", blockIndexJson())
    }

    private fun writeIndex(packRoot: Path, relative: String, content: String) {
        val file = packRoot
            .resolve("data")
            .resolve("tacz")
            .resolve("index")
            .resolve(relative)
        Files.createDirectories(file.parent)
        Files.write(file, content.toByteArray(StandardCharsets.UTF_8))
    }

    private fun writeZipEntry(out: ZipOutputStream, entryPath: String, content: String) {
        out.putNextEntry(ZipEntry(entryPath))
        out.write(content.toByteArray(StandardCharsets.UTF_8))
        out.closeEntry()
    }

    private fun gunIndexJson(): String =
        """
        {
          "name": "tacz.gun.ak47.name",
          "display": "tacz:ak47_display",
          "tooltip": "tacz.gun.ak47.desc",
          "type": "rifle"
        }
        """.trimIndent()

    private fun attachmentIndexJson(): String =
        """
        {
          // attachment can omit tooltip
          "name": "tacz.attachment.scope_hamr.name",
          "display": "tacz:scope_hamr_display",
          "type": "scope"
        }
        """.trimIndent()

    private fun ammoIndexJson(): String =
        """
        {
          "name": "tacz.ammo.9mm.name",
          "display": "tacz:9mm_display"
        }
        """.trimIndent()

    private fun blockIndexJson(): String =
        """
        {
          "name": "tacz.block.gun_smith_table.name",
          "display": "tacz:gun_smith_table",
          "tooltip": "tacz.block.gun_smith_table.desc"
        }
        """.trimIndent()

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
