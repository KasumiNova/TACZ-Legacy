package com.tacz.legacy.common.application.gunpack

import com.tacz.legacy.common.infrastructure.mc.gunpack.GunPackCompatibilityPreInitScanner
import org.apache.logging.log4j.LogManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.nio.file.StandardCopyOption
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Comparator

public class GunPackTaczDefaultSamplesTest {

    private val analyzer: GunPackCompatibilityBatchAnalyzer = GunPackCompatibilityBatchAnalyzer()
    private val scanner: GunPackCompatibilityPreInitScanner = GunPackCompatibilityPreInitScanner()

    @Test
    public fun `analyzer should parse first twenty TACZ default gun data files`() {
        val root = findTaczDefaultGunDataRoot()
        assumeTrue("TACZ default gunpack data folder not found in sibling workspace.", root != null)

        val sampleFiles = collectSampleFiles(requireNotNull(root), SAMPLE_COUNT)
        assumeTrue("Expected at least $SAMPLE_COUNT TACZ gun data files, got ${sampleFiles.size}.", sampleFiles.size >= SAMPLE_COUNT)

        val sources = sampleFiles.map { file ->
            GunPackCompatibilitySource(
                sourceId = file.fileName.toString(),
                json = String(Files.readAllBytes(file), StandardCharsets.UTF_8)
            )
        }

        val report = analyzer.analyze(sources)
        val issueHistogram = report.issueCodeHistogram()

        assertEquals(SAMPLE_COUNT, report.total)
        assertEquals(SAMPLE_COUNT, report.successCount)
        assertEquals(0, issueHistogram["MALFORMED_JSON"] ?: 0)
        assertEquals(0, issueHistogram["MISSING_REQUIRED_FIELD"] ?: 0)

        val snapshot = GunPackRuntimeRegistry().replace(report)
        assertEquals(SAMPLE_COUNT, snapshot.loadedCount)
        assertTrue(snapshot.hasDuplicateConflicts().not())
    }

    @Test
    public fun `scanner should load first twenty TACZ default gun files from TACZ style directory pack`() {
        val root = findTaczDefaultGunDataRoot()
        assumeTrue("TACZ default gunpack data folder not found in sibling workspace.", root != null)

        val sampleFiles = collectSampleFiles(requireNotNull(root), SAMPLE_COUNT)
        assumeTrue("Expected at least $SAMPLE_COUNT TACZ gun data files, got ${sampleFiles.size}.", sampleFiles.size >= SAMPLE_COUNT)

        val gameRoot = Files.createTempDirectory("tacz-legacy-sample-pack-")
        try {
            val configRoot = gameRoot.resolve("config")
            Files.createDirectories(configRoot)

            val packDir = gameRoot.resolve("tacz").resolve("sample_default_pack")
            stageDirectoryPack(packDir, sampleFiles)

            val report = scanner.scanAndLog(configRoot, LogManager.getLogger("GunPackTaczDefaultSamplesTest"))

            assertNotNull(report)
            assertEquals(SAMPLE_COUNT, report?.total)
            assertEquals(SAMPLE_COUNT, report?.successCount)
            assertEquals(0, report?.failedCount)
            assertEquals(0, report?.issueCodeHistogram()?.get("MALFORMED_JSON") ?: 0)

            val snapshot = GunPackRuntimeRegistry().replace(report)
            assertEquals(SAMPLE_COUNT, snapshot.loadedCount)
            assertTrue(snapshot.hasDuplicateConflicts().not())
        } finally {
            deleteRecursively(gameRoot)
        }
    }

    private fun collectSampleFiles(root: Path, limit: Int): List<Path> {
        val result = mutableListOf<Path>()
        Files.list(root).use { stream ->
            stream
                .filter { path ->
                    Files.isRegularFile(path) && path.fileName.toString().endsWith("_data.json")
                }
                .sorted(Comparator.comparing<Path, String> { it.fileName.toString() })
                .limit(limit.toLong())
                .forEach { path -> result.add(path) }
        }
        return result
    }

    private fun stageDirectoryPack(packDir: Path, sampleFiles: List<Path>) {
        Files.createDirectories(packDir)
        Files.write(
            packDir.resolve("gunpack.meta.json"),
            "{\"namespace\":\"sample_default_pack\"}".toByteArray(StandardCharsets.UTF_8)
        )

        val gunsDir = packDir
            .resolve("data")
            .resolve("tacz")
            .resolve("data")
            .resolve("guns")
        Files.createDirectories(gunsDir)

        sampleFiles.forEach { sample ->
            Files.copy(
                sample,
                gunsDir.resolve(sample.fileName.toString()),
                StandardCopyOption.REPLACE_EXISTING
            )
        }
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

    private fun findTaczDefaultGunDataRoot(): Path? {
        val projectRoot = Paths.get("").toAbsolutePath().normalize()
        val relative = Paths.get(
            "src",
            "main",
            "resources",
            "assets",
            "tacz",
            "custom",
            "tacz_default_gun",
            "data",
            "tacz",
            "data",
            "guns"
        )

        val candidates = listOf(
            projectRoot.resolveSibling("TACZ").resolve(relative),
            projectRoot.resolve("..").normalize().resolve("TACZ").resolve(relative)
        )

        return candidates.firstOrNull { candidate -> Files.isDirectory(candidate) }
    }

    private companion object {
        private const val SAMPLE_COUNT: Int = 20
    }

}
