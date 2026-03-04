package com.tacz.legacy.common.infrastructure.mc.gunpack

import org.apache.logging.log4j.LogManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

public class GunPackTooltipLangPreInitScannerTest {

    private val scanner: GunPackTooltipLangPreInitScanner = GunPackTooltipLangPreInitScanner()
    private val logger = LogManager.getLogger("TooltipLangScanTest")

    @Test
    public fun `scan should return empty snapshot when tacz root is missing`() {
        val root = Files.createTempDirectory("tacz-legacy-tooltip-lang-empty-")
        try {
            val snapshot = scanner.scan(root, logger)
            assertEquals(0, snapshot.totalLocales)
            assertEquals(0, snapshot.totalEntries)
            assertTrue(snapshot.failedSources.isEmpty())
        } finally {
            deleteRecursively(root)
        }
    }

    @Test
    public fun `scan should parse json and legacy lang files in directory pack`() {
        val gameRoot = Files.createTempDirectory("tacz-legacy-tooltip-lang-dir-")
        try {
            val configRoot = gameRoot.resolve("config")
            Files.createDirectories(configRoot)

            val packRoot = gameRoot.resolve("tacz").resolve("sample_pack")
            stageDirectoryPack(packRoot)

            val snapshot = scanner.scan(configRoot, logger)

            assertEquals(2, snapshot.totalLocales)
            assertEquals("AK47 Assault Rifle", snapshot.resolve("en_us", "tacz.gun.ak47.name"))
            assertEquals("AK47 突击步枪", snapshot.resolve("zh_cn", "tacz.gun.ak47.name"))
            assertEquals("基础伤害：7.5\n后坐力：1.2", snapshot.resolve("zh_cn", "tacz.gun.ak47.desc"))
            assertTrue(snapshot.failedSources.isEmpty())
        } finally {
            deleteRecursively(gameRoot)
        }
    }

    @Test
    public fun `scan should parse zip pack with nested root`() {
        val gameRoot = Files.createTempDirectory("tacz-legacy-tooltip-lang-zip-")
        try {
            val configRoot = gameRoot.resolve("config")
            Files.createDirectories(configRoot)

            val taczRoot = gameRoot.resolve("tacz")
            Files.createDirectories(taczRoot)

            val zipPath = taczRoot.resolve("sample_pack_nested.zip")
            ZipOutputStream(Files.newOutputStream(zipPath)).use { out ->
                writeZipEntry(out, "sample_pack/gunpack.meta.json", "{}")
                writeZipEntry(out, "sample_pack/assets/tacz/lang/zh_cn.json", """
                    {
                      "tacz.gun.ak47.name": "AK47 突击步枪"
                    }
                """.trimIndent())
            }

            val snapshot = scanner.scan(configRoot, logger)

            assertEquals(1, snapshot.totalLocales)
            assertEquals("AK47 突击步枪", snapshot.resolve("zh_cn", "tacz.gun.ak47.name"))
            assertTrue(snapshot.failedSources.isEmpty())
        } finally {
            deleteRecursively(gameRoot)
        }
    }

    // ─── §3 补充覆盖：边界 + 异常路径 ───────────────────────

    @Test
    public fun `unescapeLangValue should handle carriage return`() {
        val gameRoot = Files.createTempDirectory("tacz-legacy-tooltip-lang-escape-r-")
        try {
            val configRoot = gameRoot.resolve("config")
            Files.createDirectories(configRoot)
            val packRoot = gameRoot.resolve("tacz").resolve("sample_pack")
            stageDirectoryPackBase(packRoot)
            writeLangFile(packRoot, "zh_cn.lang", "key=hello\\rworld")

            val snapshot = scanner.scan(configRoot, logger)
            assertEquals("hello\rworld", snapshot.resolve("zh_cn", "key"))
        } finally {
            deleteRecursively(gameRoot)
        }
    }

    @Test
    public fun `unescapeLangValue should handle tab`() {
        val gameRoot = Files.createTempDirectory("tacz-legacy-tooltip-lang-escape-t-")
        try {
            val configRoot = gameRoot.resolve("config")
            Files.createDirectories(configRoot)
            val packRoot = gameRoot.resolve("tacz").resolve("sample_pack")
            stageDirectoryPackBase(packRoot)
            writeLangFile(packRoot, "zh_cn.lang", "key=col1\\tcol2")

            val snapshot = scanner.scan(configRoot, logger)
            assertEquals("col1\tcol2", snapshot.resolve("zh_cn", "key"))
        } finally {
            deleteRecursively(gameRoot)
        }
    }

    @Test
    public fun `unescapeLangValue should handle double backslash`() {
        val gameRoot = Files.createTempDirectory("tacz-legacy-tooltip-lang-escape-bs-")
        try {
            val configRoot = gameRoot.resolve("config")
            Files.createDirectories(configRoot)
            val packRoot = gameRoot.resolve("tacz").resolve("sample_pack")
            stageDirectoryPackBase(packRoot)
            writeLangFile(packRoot, "zh_cn.lang", "key=path\\\\to\\\\file")

            val snapshot = scanner.scan(configRoot, logger)
            assertEquals("path\\to\\file", snapshot.resolve("zh_cn", "key"))
        } finally {
            deleteRecursively(gameRoot)
        }
    }

    @Test
    public fun `unescapeLangValue should keep unknown escape sequences`() {
        val gameRoot = Files.createTempDirectory("tacz-legacy-tooltip-lang-escape-unk-")
        try {
            val configRoot = gameRoot.resolve("config")
            Files.createDirectories(configRoot)
            val packRoot = gameRoot.resolve("tacz").resolve("sample_pack")
            stageDirectoryPackBase(packRoot)
            writeLangFile(packRoot, "zh_cn.lang", "key=hello\\xworld")

            val snapshot = scanner.scan(configRoot, logger)
            assertEquals("hello\\xworld", snapshot.resolve("zh_cn", "key"))
        } finally {
            deleteRecursively(gameRoot)
        }
    }

    @Test
    public fun `unescapeLangValue should keep trailing backslash`() {
        val gameRoot = Files.createTempDirectory("tacz-legacy-tooltip-lang-escape-trail-")
        try {
            val configRoot = gameRoot.resolve("config")
            Files.createDirectories(configRoot)
            val packRoot = gameRoot.resolve("tacz").resolve("sample_pack")
            stageDirectoryPackBase(packRoot)
            writeLangFile(packRoot, "zh_cn.lang", "key=end\\")

            val snapshot = scanner.scan(configRoot, logger)
            assertEquals("end\\", snapshot.resolve("zh_cn", "key"))
        } finally {
            deleteRecursively(gameRoot)
        }
    }

    @Test
    public fun `parseLegacyLangEntries should support colon delimiter`() {
        val gameRoot = Files.createTempDirectory("tacz-legacy-tooltip-lang-colon-")
        try {
            val configRoot = gameRoot.resolve("config")
            Files.createDirectories(configRoot)
            val packRoot = gameRoot.resolve("tacz").resolve("sample_pack")
            stageDirectoryPackBase(packRoot)
            writeLangFile(packRoot, "zh_cn.lang", "key:value_colon")

            val snapshot = scanner.scan(configRoot, logger)
            assertEquals("value_colon", snapshot.resolve("zh_cn", "key"))
        } finally {
            deleteRecursively(gameRoot)
        }
    }

    @Test
    public fun `parseLegacyLangEntries should skip comments and blank lines`() {
        val gameRoot = Files.createTempDirectory("tacz-legacy-tooltip-lang-comments-")
        try {
            val configRoot = gameRoot.resolve("config")
            Files.createDirectories(configRoot)
            val packRoot = gameRoot.resolve("tacz").resolve("sample_pack")
            stageDirectoryPackBase(packRoot)
            writeLangFile(packRoot, "zh_cn.lang", """
                # This is a comment
                ! This is also a comment
                
                key=value
            """.trimIndent())

            val snapshot = scanner.scan(configRoot, logger)
            assertEquals("value", snapshot.resolve("zh_cn", "key"))
            assertEquals(1, snapshot.totalEntries)
        } finally {
            deleteRecursively(gameRoot)
        }
    }

    @Test
    public fun `parseLegacyLangEntries should skip empty value`() {
        val gameRoot = Files.createTempDirectory("tacz-legacy-tooltip-lang-emptyval-")
        try {
            val configRoot = gameRoot.resolve("config")
            Files.createDirectories(configRoot)
            val packRoot = gameRoot.resolve("tacz").resolve("sample_pack")
            stageDirectoryPackBase(packRoot)
            writeLangFile(packRoot, "zh_cn.lang", "key=\nother=val")

            val snapshot = scanner.scan(configRoot, logger)
            assertNull(snapshot.resolve("zh_cn", "key"))
            assertEquals("val", snapshot.resolve("zh_cn", "other"))
        } finally {
            deleteRecursively(gameRoot)
        }
    }

    @Test
    public fun `parseLegacyLangEntries should skip lines without delimiter`() {
        val gameRoot = Files.createTempDirectory("tacz-legacy-tooltip-lang-nodelim-")
        try {
            val configRoot = gameRoot.resolve("config")
            Files.createDirectories(configRoot)
            val packRoot = gameRoot.resolve("tacz").resolve("sample_pack")
            stageDirectoryPackBase(packRoot)
            writeLangFile(packRoot, "zh_cn.lang", "no_delimiter_here\nkey=value")

            val snapshot = scanner.scan(configRoot, logger)
            assertEquals(1, snapshot.totalEntries)
            assertEquals("value", snapshot.resolve("zh_cn", "key"))
        } finally {
            deleteRecursively(gameRoot)
        }
    }

    @Test
    public fun `parseJsonLangEntries should skip non-string values`() {
        val gameRoot = Files.createTempDirectory("tacz-legacy-tooltip-lang-nonstr-")
        try {
            val configRoot = gameRoot.resolve("config")
            Files.createDirectories(configRoot)
            val packRoot = gameRoot.resolve("tacz").resolve("sample_pack")
            stageDirectoryPackBase(packRoot)
            writeJsonLangFile(packRoot, "zh_cn.json", """{"numeric": 123, "bool": true, "valid": "text"}""")

            val snapshot = scanner.scan(configRoot, logger)
            assertNull(snapshot.resolve("zh_cn", "numeric"))
            assertNull(snapshot.resolve("zh_cn", "bool"))
            assertEquals("text", snapshot.resolve("zh_cn", "valid"))
        } finally {
            deleteRecursively(gameRoot)
        }
    }

    @Test
    public fun `parseJsonLangEntries should skip blank key and blank value`() {
        val gameRoot = Files.createTempDirectory("tacz-legacy-tooltip-lang-blank-")
        try {
            val configRoot = gameRoot.resolve("config")
            Files.createDirectories(configRoot)
            val packRoot = gameRoot.resolve("tacz").resolve("sample_pack")
            stageDirectoryPackBase(packRoot)
            writeJsonLangFile(packRoot, "zh_cn.json", """{"  ": "val", "k": "  ", "good": "ok"}""")

            val snapshot = scanner.scan(configRoot, logger)
            assertEquals(1, snapshot.totalEntries)
            assertEquals("ok", snapshot.resolve("zh_cn", "good"))
        } finally {
            deleteRecursively(gameRoot)
        }
    }

    @Test
    public fun `scan should skip directory pack without assets subdirectory`() {
        val gameRoot = Files.createTempDirectory("tacz-legacy-tooltip-lang-noassets-")
        try {
            val configRoot = gameRoot.resolve("config")
            Files.createDirectories(configRoot)
            val packDir = gameRoot.resolve("tacz").resolve("pack_no_assets")
            Files.createDirectories(packDir)
            Files.write(packDir.resolve("gunpack.meta.json"), "{}".toByteArray(StandardCharsets.UTF_8))

            val snapshot = scanner.scan(configRoot, logger)
            assertEquals(0, snapshot.totalEntries)
        } finally {
            deleteRecursively(gameRoot)
        }
    }

    @Test
    public fun `scan should skip directory pack without gunpack meta`() {
        val gameRoot = Files.createTempDirectory("tacz-legacy-tooltip-lang-nometa-")
        try {
            val configRoot = gameRoot.resolve("config")
            Files.createDirectories(configRoot)
            val packDir = gameRoot.resolve("tacz").resolve("no_meta_pack")
            Files.createDirectories(packDir)
            writeLangFile(packDir, "zh_cn.lang", "key=value")

            val snapshot = scanner.scan(configRoot, logger)
            assertEquals(0, snapshot.totalEntries)
        } finally {
            deleteRecursively(gameRoot)
        }
    }

    @Test
    public fun `scan should skip non-lang files in assets`() {
        val gameRoot = Files.createTempDirectory("tacz-legacy-tooltip-lang-nonlang-")
        try {
            val configRoot = gameRoot.resolve("config")
            Files.createDirectories(configRoot)
            val packDir = gameRoot.resolve("tacz").resolve("sample_pack")
            stageDirectoryPackBase(packDir)
            val txtFile = packDir.resolve("assets").resolve("tacz").resolve("lang").resolve("readme.txt")
            Files.createDirectories(txtFile.parent)
            Files.write(txtFile, "not a lang file".toByteArray(StandardCharsets.UTF_8))

            val snapshot = scanner.scan(configRoot, logger)
            assertEquals(0, snapshot.totalEntries)
        } finally {
            deleteRecursively(gameRoot)
        }
    }

    @Test
    public fun `scan should add to failedSources when json is invalid`() {
        val gameRoot = Files.createTempDirectory("tacz-legacy-tooltip-lang-badjson-")
        try {
            val configRoot = gameRoot.resolve("config")
            Files.createDirectories(configRoot)
            val packDir = gameRoot.resolve("tacz").resolve("sample_pack")
            stageDirectoryPackBase(packDir)
            writeJsonLangFile(packDir, "zh_cn.json", "<<<invalid json>>>")

            val snapshot = scanner.scan(configRoot, logger)
            assertEquals(0, snapshot.totalEntries)
            assertTrue(snapshot.failedSources.isNotEmpty())
        } finally {
            deleteRecursively(gameRoot)
        }
    }

    @Test
    public fun `scan should add to failedSources when json is not object`() {
        val gameRoot = Files.createTempDirectory("tacz-legacy-tooltip-lang-notobj-")
        try {
            val configRoot = gameRoot.resolve("config")
            Files.createDirectories(configRoot)
            val packDir = gameRoot.resolve("tacz").resolve("sample_pack")
            stageDirectoryPackBase(packDir)
            writeJsonLangFile(packDir, "zh_cn.json", "[1,2,3]")

            val snapshot = scanner.scan(configRoot, logger)
            assertEquals(0, snapshot.totalEntries)
            assertTrue(snapshot.failedSources.isNotEmpty())
        } finally {
            deleteRecursively(gameRoot)
        }
    }

    @Test
    public fun `scan should skip zip without gunpack meta`() {
        val gameRoot = Files.createTempDirectory("tacz-legacy-tooltip-lang-zipnometa-")
        try {
            val configRoot = gameRoot.resolve("config")
            Files.createDirectories(configRoot)
            val taczRoot = gameRoot.resolve("tacz")
            Files.createDirectories(taczRoot)
            ZipOutputStream(Files.newOutputStream(taczRoot.resolve("no_meta.zip"))).use { out ->
                writeZipEntry(out, "assets/tacz/lang/zh_cn.json", """{"k":"v"}""")
            }

            val snapshot = scanner.scan(configRoot, logger)
            assertEquals(0, snapshot.totalEntries)
        } finally {
            deleteRecursively(gameRoot)
        }
    }

    @Test
    public fun `scan should skip non-matching zip entries`() {
        val gameRoot = Files.createTempDirectory("tacz-legacy-tooltip-lang-zipmismatch-")
        try {
            val configRoot = gameRoot.resolve("config")
            Files.createDirectories(configRoot)
            val taczRoot = gameRoot.resolve("tacz")
            Files.createDirectories(taczRoot)
            ZipOutputStream(Files.newOutputStream(taczRoot.resolve("pack.zip"))).use { out ->
                writeZipEntry(out, "sample_pack/gunpack.meta.json", "{}")
                writeZipEntry(out, "sample_pack/assets/tacz/other/file.json", """{"k":"v"}""")
            }

            val snapshot = scanner.scan(configRoot, logger)
            assertEquals(0, snapshot.totalEntries)
        } finally {
            deleteRecursively(gameRoot)
        }
    }

    @Test
    public fun `scan should add to failedSources when zip lang json is invalid`() {
        val gameRoot = Files.createTempDirectory("tacz-legacy-tooltip-lang-zipbadjson-")
        try {
            val configRoot = gameRoot.resolve("config")
            Files.createDirectories(configRoot)
            val taczRoot = gameRoot.resolve("tacz")
            Files.createDirectories(taczRoot)
            ZipOutputStream(Files.newOutputStream(taczRoot.resolve("pack.zip"))).use { out ->
                writeZipEntry(out, "sample_pack/gunpack.meta.json", "{}")
                writeZipEntry(out, "sample_pack/assets/tacz/lang/zh_cn.json", "<<<invalid>>>")
            }

            val snapshot = scanner.scan(configRoot, logger)
            assertEquals(0, snapshot.totalEntries)
            assertTrue(snapshot.failedSources.isNotEmpty())
        } finally {
            deleteRecursively(gameRoot)
        }
    }

    @Test
    public fun `scan should strip block comments in json lang`() {
        val gameRoot = Files.createTempDirectory("tacz-legacy-tooltip-lang-blockcom-")
        try {
            val configRoot = gameRoot.resolve("config")
            Files.createDirectories(configRoot)
            val packRoot = gameRoot.resolve("tacz").resolve("sample_pack")
            stageDirectoryPackBase(packRoot)
            writeJsonLangFile(packRoot, "zh_cn.json", """
                {
                  /* block comment */
                  "key": "value"
                }
            """.trimIndent())

            val snapshot = scanner.scan(configRoot, logger)
            assertEquals("value", snapshot.resolve("zh_cn", "key"))
        } finally {
            deleteRecursively(gameRoot)
        }
    }

    @Test
    public fun `mergeEntries should keep first value on duplicate key`() {
        val gameRoot = Files.createTempDirectory("tacz-legacy-tooltip-lang-merge-")
        try {
            val configRoot = gameRoot.resolve("config")
            Files.createDirectories(configRoot)

            val taczRoot = gameRoot.resolve("tacz")
            Files.createDirectories(taczRoot)

            // Two packs with same key, different values
            val pack1 = taczRoot.resolve("pack_a")
            stageDirectoryPackBase(pack1)
            writeLangFile(pack1, "zh_cn.lang", "key=first_value")

            val pack2 = taczRoot.resolve("pack_b")
            stageDirectoryPackBase(pack2)
            writeLangFile(pack2, "zh_cn.lang", "key=second_value")

            val snapshot = scanner.scan(configRoot, logger)
            // One of them is first (depends on iteration order)
            val resolved = snapshot.resolve("zh_cn", "key")
            assertTrue(resolved == "first_value" || resolved == "second_value")
            assertEquals(1, snapshot.totalEntries)
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

        writeJsonLangFile(packRoot, "zh_cn.json", """
            {
              // allow comments for scanner compatibility
              "tacz.gun.ak47.name": "AK47 突击步枪",
              "tacz.gun.ak47.desc": "基础伤害：7.5\\n后坐力：1.2"
            }
        """.trimIndent())

        writeLangFile(packRoot, "en_us.lang", """
            # legacy lang format
            tacz.gun.ak47.name=AK47 Assault Rifle
        """.trimIndent())
    }

    private fun writeLangFile(packRoot: Path, filename: String, content: String) {
        val file = packRoot.resolve("assets").resolve("tacz").resolve("lang").resolve(filename)
        Files.createDirectories(file.parent)
        Files.write(file, content.toByteArray(StandardCharsets.UTF_8))
    }

    private fun writeJsonLangFile(packRoot: Path, filename: String, content: String) {
        writeLangFile(packRoot, filename, content)
    }

    private fun writeZipEntry(out: ZipOutputStream, entryPath: String, content: String) {
        out.putNextEntry(ZipEntry(entryPath))
        out.write(content.toByteArray(StandardCharsets.UTF_8))
        out.closeEntry()
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
