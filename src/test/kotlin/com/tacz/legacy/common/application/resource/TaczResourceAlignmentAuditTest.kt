package com.tacz.legacy.common.application.resource

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest

public class TaczResourceAlignmentAuditTest {

    @Test
    public fun `legacy source resources should align with sibling TACZ except declared version conversions`() {
        val taczRoot = findTaczResourceRoot()
        assumeTrue("TACZ source resources not found in sibling workspace.", taczRoot != null)

        val legacyRoot = Paths.get("src", "main", "resources").toAbsolutePath().normalize()
        assumeTrue("Legacy source resources not found.", Files.isDirectory(legacyRoot))

        val leftFiles = collectRelativeFiles(requireNotNull(taczRoot))
        val rightFiles = collectRelativeFiles(legacyRoot)

        val leftOnly = (leftFiles - rightFiles).sorted()
        val rightOnly = (rightFiles - leftFiles).sorted()
        val common = (leftFiles intersect rightFiles).sorted()
        val changed = common.filter { relative ->
            sha1(taczRoot.resolve(relative)) != sha1(legacyRoot.resolve(relative))
        }

        val unexpectedLeftOnly = leftOnly.filterNot(::isExpectedLeftOnly)
        val unexpectedRightOnly = rightOnly.filterNot { relative ->
            isExpectedRightOnly(relative, leftFiles)
        }
        val unexpectedChanged = changed.filterNot { it in EXPECTED_CHANGED_FILES }

        assertTrue(
            "Unexpected TACZ-only resources (not covered by version conversion policy): ${unexpectedLeftOnly.take(30)}",
            unexpectedLeftOnly.isEmpty()
        )
        assertTrue(
            "Unexpected Legacy-only resources (not covered by version conversion policy): ${unexpectedRightOnly.take(30)}",
            unexpectedRightOnly.isEmpty()
        )
        assertTrue(
            "Unexpected content-changed common resources: ${unexpectedChanged.take(30)}",
            unexpectedChanged.isEmpty()
        )
    }

    private fun findTaczResourceRoot(): Path? {
        val projectRoot = Paths.get("").toAbsolutePath().normalize()
        val relative = Paths.get("src", "main", "resources")

        val candidates = listOf(
            projectRoot.resolveSibling("TACZ").resolve(relative),
            projectRoot.resolve("..").normalize().resolve("TACZ").resolve(relative)
        )

        return candidates.firstOrNull { Files.isDirectory(it) }
    }

    private fun collectRelativeFiles(root: Path): Set<String> {
        val out = linkedSetOf<String>()
        Files.walk(root).use { stream ->
            stream.filter { Files.isRegularFile(it) }
                .forEach { file ->
                    out += root.relativize(file).toString().replace('\\', '/')
                }
        }
        return out
    }

    private fun isExpectedLeftOnly(relativePath: String): Boolean {
        if (relativePath.startsWith("data/")) {
            // 1.20 数据驱动资源（damage type / recipe / tag / loot table）在 1.12.2 不可直接使用
            return true
        }
        if (relativePath.startsWith("META-INF/")) {
            // Forge 新旧元数据体系差异：mods.toml vs mcmod.info + 1.12 AT 差异
            return true
        }
        return relativePath in EXPECTED_TACZ_ROOT_ONLY
    }

    private fun isExpectedRightOnly(relativePath: String, taczFiles: Set<String>): Boolean {
        if (relativePath == "mcmod.info" || relativePath == "mixins.tacz.json") {
            // 1.12.2 Forge + MixinBooter 运行时元数据
            return true
        }

        if (relativePath.startsWith("assets/tacz/lang/") && relativePath.endsWith(".lang")) {
            val stem = relativePath.substringAfterLast('/').substringBeforeLast('.')
            val jsonCandidates = linkedSetOf(
                "assets/tacz/lang/$stem.json"
            )
            if (stem == "tr_tr") {
                jsonCandidates += "assets/tacz/lang/tr-TR.json"
            }
            return jsonCandidates.any { it in taczFiles }
        }

        return relativePath in EXPECTED_LEGACY_ONLY
    }

    private fun sha1(file: Path): String {
        val digest = MessageDigest.getInstance("SHA-1")
        val bytes = Files.readAllBytes(file)
        val hash = digest.digest(bytes)
        return buildString(hash.size * 2) {
            hash.forEach { b -> append(String.format("%02x", b)) }
        }
    }

    private companion object {
        private val EXPECTED_TACZ_ROOT_ONLY: Set<String> = setOf(
            "icon.png",
            "logo.png",
            "kubejs.classfilter.txt",
            "kubejs.plugins.txt",
            "shouldersurfing_plugin.json",
            "tacz.compat.acceleratedrendering.mixins.json",
            "tacz.mixins.json"
        )

        private val EXPECTED_LEGACY_ONLY: Set<String> = setOf(
            "assets/tacz/blockstates/steel_target.json",
            "assets/tacz/blockstates/weapon_workbench.json",
            "assets/tacz/models/block/steel_target.json",
            "assets/tacz/models/block/weapon_workbench.json",
            "assets/tacz/models/item/ak47.json",
            "assets/tacz/models/item/flat_icon.json",
            "assets/tacz/models/item/steel_target.json",
            "assets/tacz/models/item/weapon_debug_core.json",
            "assets/tacz/models/item/weapon_workbench.json"
        )

        private val EXPECTED_CHANGED_FILES: Set<String> = setOf(
            "pack.mcmeta",
            "assets/tacz/models/block/gun_smith_table.json",
            "assets/tacz/models/block/statue.json",
            "assets/tacz/models/block/target.json",
            "assets/tacz/models/item/ammo.json",
            "assets/tacz/models/item/attachment.json",
            "assets/tacz/models/item/modern_kinetic_gun.json"
        )
    }
}
