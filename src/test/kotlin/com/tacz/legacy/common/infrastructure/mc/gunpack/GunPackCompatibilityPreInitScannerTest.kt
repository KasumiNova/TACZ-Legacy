package com.tacz.legacy.common.infrastructure.mc.gunpack

import com.tacz.legacy.common.application.gunpack.GunPackRuntimeRegistry
import org.apache.logging.log4j.LogManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

public class GunPackCompatibilityPreInitScannerTest {

    private val scanner: GunPackCompatibilityPreInitScanner = GunPackCompatibilityPreInitScanner()

    @Test
    public fun `scan should return null when candidate folders do not exist`() {
        val root = Files.createTempDirectory("tacz-legacy-scan-empty-")
        try {
            val report = scanner.scanAndLog(root, LogManager.getLogger("GunPackScanEmptyTest"))
            assertNull(report)
        } finally {
            deleteRecursively(root)
        }
    }

    @Test
    public fun `scan should parse json files under candidate folder and aggregate report`() {
        val root = Files.createTempDirectory("tacz-legacy-scan-populated-")
        try {
            val gunsRoot = root.resolve("tacz").resolve("gunpack").resolve("data").resolve("guns")
            Files.createDirectories(gunsRoot)

            Files.write(
                gunsRoot.resolve("ak47.json"),
                canonicalGunJson().toByteArray(StandardCharsets.UTF_8)
            )
            Files.write(
                gunsRoot.resolve("broken.json"),
                "{ invalid-json }".toByteArray(StandardCharsets.UTF_8)
            )

            val report = scanner.scanAndLog(root, LogManager.getLogger("GunPackScanPopulatedTest"))

            assertNotNull(report)
            assertEquals(2, report?.total)
            assertEquals(1, report?.successCount)
            assertEquals(1, report?.failedCount)
            assertEquals(1, report?.issueCodeHistogram()?.get("MALFORMED_JSON"))

            val snapshot = GunPackRuntimeRegistry().replace(report)
            assertEquals(1, snapshot.loadedCount)
            assertEquals(1, snapshot.failedSources.size)
            assertNotNull(snapshot.find("ak47.json"))
            assertNotNull(snapshot.findByGunId("ak47"))
            assertEquals(1, snapshot.findByAmmoId("tacz:762").size)
        } finally {
            deleteRecursively(root)
        }
    }

    @Test
    public fun `scan should parse TACZ style zip packs from sibling tacz folder`() {
        val gameRoot = Files.createTempDirectory("tacz-legacy-scan-zip-pack-")
        try {
            val configRoot = gameRoot.resolve("config")
            Files.createDirectories(configRoot)

            val taczRoot = gameRoot.resolve("tacz")
            Files.createDirectories(taczRoot)
            val zipPath = taczRoot.resolve("sample_pack.zip")

            createPackZip(
                zipPath = zipPath,
                namespace = "sample_pack",
                gunJsonPath = "data/sample_pack/data/guns/ak47_data.json",
                gunJson = canonicalGunJson()
            )

            val report = scanner.scanAndLog(configRoot, LogManager.getLogger("GunPackScanZipPackTest"))

            assertNotNull(report)
            assertEquals(1, report?.total)
            assertEquals(1, report?.successCount)
            assertEquals(0, report?.failedCount)
            assertEquals("rifle", report?.gunTypeHintsByGunId?.get("ak47"))

            val snapshot = GunPackRuntimeRegistry().replace(report)
            assertEquals(1, snapshot.loadedCount)
            assertNotNull(snapshot.findByGunId("ak47"))
            assertEquals("rifle", snapshot.findGunType("ak47"))
            assertEquals(1, snapshot.findByAmmoId("tacz:762").size)
        } finally {
            deleteRecursively(gameRoot)
        }
    }

    @Test
    public fun `scan should parse zip packs with nested root folder`() {
        val gameRoot = Files.createTempDirectory("tacz-legacy-scan-zip-nested-root-")
        try {
            val configRoot = gameRoot.resolve("config")
            Files.createDirectories(configRoot)

            val taczRoot = gameRoot.resolve("tacz")
            Files.createDirectories(taczRoot)
            val zipPath = taczRoot.resolve("sample_pack_nested.zip")

            ZipOutputStream(Files.newOutputStream(zipPath)).use { out ->
                out.putNextEntry(ZipEntry("sample_pack/gunpack.meta.json"))
                out.write("{\"namespace\":\"sample_pack\"}".toByteArray(StandardCharsets.UTF_8))
                out.closeEntry()

                out.putNextEntry(ZipEntry("sample_pack/data/sample_pack/data/guns/ak47_data.json"))
                out.write(canonicalGunJson().toByteArray(StandardCharsets.UTF_8))
                out.closeEntry()

                out.putNextEntry(ZipEntry("sample_pack/data/sample_pack/index/guns/ak47.json"))
                out.write(sampleGunIndexJson().toByteArray(StandardCharsets.UTF_8))
                out.closeEntry()
            }

            val report = scanner.scanAndLog(configRoot, LogManager.getLogger("GunPackScanZipNestedRootTest"))

            assertNotNull(report)
            assertEquals(1, report?.total)
            assertEquals(1, report?.successCount)
            assertEquals(0, report?.failedCount)
            assertEquals("rifle", report?.gunTypeHintsByGunId?.get("ak47"))

            val snapshot = GunPackRuntimeRegistry().replace(report)
            assertEquals(1, snapshot.loadedCount)
            assertNotNull(snapshot.findByGunId("ak47"))
            assertEquals("rifle", snapshot.findGunType("ak47"))
        } finally {
            deleteRecursively(gameRoot)
        }
    }

    private fun canonicalGunJson(): String =
        """
        {
          "ammo": "tacz:762",
          "ammo_amount": 30,
          "bolt": "closed_bolt",
          "rpm": 600,
          "fire_mode": ["auto", "semi"],
          "reload": {
            "type": "magazine",
            "infinite": false,
            "feed": {
              "empty": 2.4,
              "tactical": 2.1
            }
          },
          "bullet": {
            "life": 9.0,
            "bullet_amount": 1,
            "damage": 6.0,
            "speed": 5.2,
            "gravity": 0.02,
            "pierce": 2
          }
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

    private fun createPackZip(
        zipPath: Path,
        namespace: String,
        gunJsonPath: String,
        gunJson: String
    ) {
        ZipOutputStream(Files.newOutputStream(zipPath)).use { out ->
            out.putNextEntry(ZipEntry("gunpack.meta.json"))
            out.write("{\"namespace\":\"$namespace\"}".toByteArray(StandardCharsets.UTF_8))
            out.closeEntry()

            out.putNextEntry(ZipEntry(gunJsonPath))
            out.write(gunJson.toByteArray(StandardCharsets.UTF_8))
            out.closeEntry()

            out.putNextEntry(ZipEntry("data/$namespace/index/guns/ak47.json"))
            out.write(sampleGunIndexJson().toByteArray(StandardCharsets.UTF_8))
            out.closeEntry()
        }
    }

    private fun sampleGunIndexJson(): String =
        """
        {
          "type": "rifle",
          "data": "tacz:ak47_data",
          "display": "tacz:ak47_display"
        }
        """.trimIndent()

}