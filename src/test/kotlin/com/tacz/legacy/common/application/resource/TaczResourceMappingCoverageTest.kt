package com.tacz.legacy.common.application.resource

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

public class TaczResourceMappingCoverageTest {

    @Test
    public fun `mapping coverage should be at least ninety-five percent for TACZ assets`() {
        val root = findTaczAssetsRoot()
        assumeTrue("TACZ assets folder not found in sibling workspace.", root != null)

        val relativePaths = collectRelativePaths(requireNotNull(root))
        val summary = TaczResourceMappingManifestBuilder().build(relativePaths)

        assertTrue("Expected assets to be non-empty.", summary.totalFiles > 0)
        assertTrue(
            "Expected mapping coverage >= 95%, actual=${summary.coverageRatio}, manual=${summary.manualReviewCount}, samples=${summary.manualReviewSamples}",
            summary.coverageRatio >= MIN_COVERAGE
        )
    }

    private fun findTaczAssetsRoot(): Path? {
        val projectRoot = Paths.get("").toAbsolutePath().normalize()
        val relative = Paths.get("src", "main", "resources", "assets", "tacz")

        val candidates = listOf(
            projectRoot.resolveSibling("TACZ").resolve(relative),
            projectRoot.resolve("..").normalize().resolve("TACZ").resolve(relative)
        )

        return candidates.firstOrNull { candidate -> Files.isDirectory(candidate) }
    }

    private fun collectRelativePaths(root: Path): List<String> {
        val result = mutableListOf<String>()
        Files.walk(root).use { stream ->
            stream
                .filter { path -> Files.isRegularFile(path) }
                .forEach { path -> result += root.relativize(path).toString().replace('\\', '/') }
        }
        return result
    }

    private companion object {
        private const val MIN_COVERAGE: Double = 0.95
    }

}
