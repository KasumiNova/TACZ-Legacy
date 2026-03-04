package com.tacz.legacy.common.infrastructure.mc.gunpack

import com.tacz.legacy.common.application.weapon.WeaponLuaScriptEngine
import org.apache.logging.log4j.LogManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

public class GunPackReloadCoordinatorTest {

    @After
    public fun cleanupLuaScriptCache() {
        WeaponLuaScriptEngine.clear()
    }

    @Test
    public fun `compute item registry delta should be empty when sets are equal`() {
        val locked = setOf("tacz.dynamic.gun.ak47", "tacz.dynamic.gun.m4a1")
        val current = setOf("tacz.dynamic.gun.ak47", "tacz.dynamic.gun.m4a1")

        val delta = GunPackReloadCoordinator.computeItemRegistryDelta(locked, current)

        assertFalse(delta.changed)
        assertTrue(delta.addedPaths.isEmpty())
        assertTrue(delta.removedPaths.isEmpty())
    }

    @Test
    public fun `compute item registry delta should report added and removed entries`() {
        val locked = setOf("tacz.dynamic.gun.ak47", "tacz.dynamic.gun.m4a1")
        val current = setOf("tacz.dynamic.gun.ak47", "tacz.dynamic.gun.hk416")

        val delta = GunPackReloadCoordinator.computeItemRegistryDelta(locked, current)

        assertTrue(delta.changed)
        assertEquals(setOf("tacz.dynamic.gun.hk416"), delta.addedPaths)
        assertEquals(setOf("tacz.dynamic.gun.m4a1"), delta.removedPaths)
    }

    @Test
    public fun `bootstrap and reload should refresh lua hook cache from directory pack`() {
        val gameRoot = Files.createTempDirectory("tacz-legacy-reload-dir-pack-")
        try {
            val configRoot = gameRoot.resolve("config")
            Files.createDirectories(configRoot)

            val packRoot = gameRoot.resolve("tacz").resolve("sample_pack")
            stageDirectoryPack(
                packRoot = packRoot,
                scriptBody = luaScript(
                    damageScale = 1.25f,
                    hudText = "ready-v1"
                )
            )

            val logger = LogManager.getLogger("GunPackReloadCoordinatorDirPackTest")
            val bootstrap = GunPackReloadCoordinator.bootstrap(configRoot, logger)
            assertEquals(1, bootstrap.gunDisplaySnapshot.loadedCount)
            assertNotNull(bootstrap.gunDisplaySnapshot.findDefinition("ak47"))

            val first = WeaponLuaScriptEngine.evaluate(
                gunId = "ak47",
                displayDefinition = null,
                scriptParams = emptyMap(),
                ammoInMagazine = 30,
                ammoReserve = 90
            )
            assertNotNull(first)
            assertEquals(1.25f, first?.ballisticAdjustments?.damageScale ?: -1f, 0.0001f)
            assertEquals("ready-v1", first?.hudHint)

            val scriptFile = packRoot
                .resolve("assets")
                .resolve("tacz")
                .resolve("scripts")
                .resolve("ak47_state_machine.lua")
            Files.write(
                scriptFile,
                luaScript(
                    damageScale = 0.8f,
                    hudText = "ready-v2"
                ).toByteArray(StandardCharsets.UTF_8)
            )

            val reloaded = GunPackReloadCoordinator.reload(logger)
            assertNotNull(reloaded)
            assertEquals(1, reloaded?.gunDisplaySnapshot?.loadedCount)

            val second = WeaponLuaScriptEngine.evaluate(
                gunId = "ak47",
                displayDefinition = null,
                scriptParams = emptyMap(),
                ammoInMagazine = 30,
                ammoReserve = 90
            )
            assertNotNull(second)
            assertEquals(0.8f, second?.ballisticAdjustments?.damageScale ?: -1f, 0.0001f)
            assertEquals("ready-v2", second?.hudHint)
        } finally {
            deleteRecursively(gameRoot)
        }
    }

    @Test
    public fun `bootstrap should preload lua hook from zip pack with nested root`() {
        val gameRoot = Files.createTempDirectory("tacz-legacy-reload-zip-pack-")
        try {
            val configRoot = gameRoot.resolve("config")
            Files.createDirectories(configRoot)

            val taczRoot = gameRoot.resolve("tacz")
            Files.createDirectories(taczRoot)

            val zipPath = taczRoot.resolve("sample_pack_nested.zip")
            createPackZipWithNestedRoot(
                zipPath = zipPath,
                scriptBody = luaScript(
                    damageScale = 1.1f,
                    hudText = "zip-ready"
                )
            )

            val logger = LogManager.getLogger("GunPackReloadCoordinatorZipPackTest")
            val outcome = GunPackReloadCoordinator.bootstrap(configRoot, logger)
            assertEquals(1, outcome.gunDisplaySnapshot.loadedCount)
            assertNotNull(outcome.gunDisplaySnapshot.findDefinition("ak47"))

            val result = WeaponLuaScriptEngine.evaluate(
                gunId = "ak47",
                displayDefinition = null,
                scriptParams = emptyMap(),
                ammoInMagazine = 25,
                ammoReserve = 80
            )
            assertNotNull(result)
            assertEquals(1.1f, result?.ballisticAdjustments?.damageScale ?: -1f, 0.0001f)
            assertEquals("zip-ready", result?.hudHint)
        } finally {
            deleteRecursively(gameRoot)
        }
    }

    @Test
    public fun `reload should return null when bootstrap was not called`() {
        val logger = LogManager.getLogger("GunPackReloadCoordinatorNoBootstrapTest")
        val outcome = GunPackReloadCoordinator.reload(logger)
        assertNull(outcome)
    }

    private fun stageDirectoryPack(packRoot: Path, scriptBody: String) {
        Files.createDirectories(packRoot)
        Files.write(
            packRoot.resolve("gunpack.meta.json"),
            "{\"namespace\":\"sample_pack\"}".toByteArray(StandardCharsets.UTF_8)
        )

        val indexFile = packRoot
            .resolve("data")
            .resolve("tacz")
            .resolve("index")
            .resolve("guns")
            .resolve("ak47.json")
        Files.createDirectories(indexFile.parent)
        Files.write(indexFile, sampleIndexJson().toByteArray(StandardCharsets.UTF_8))

        val displayFile = packRoot
            .resolve("assets")
            .resolve("tacz")
            .resolve("display")
            .resolve("guns")
            .resolve("ak47_display.json")
        Files.createDirectories(displayFile.parent)
        Files.write(displayFile, sampleDisplayJson().toByteArray(StandardCharsets.UTF_8))

        val stateMachineFile = packRoot
            .resolve("assets")
            .resolve("tacz")
            .resolve("scripts")
            .resolve("ak47_state_machine.lua")
        Files.createDirectories(stateMachineFile.parent)
        Files.write(stateMachineFile, scriptBody.toByteArray(StandardCharsets.UTF_8))
    }

    private fun createPackZipWithNestedRoot(zipPath: Path, scriptBody: String) {
        ZipOutputStream(Files.newOutputStream(zipPath)).use { out ->
            out.putNextEntry(ZipEntry("sample_pack/gunpack.meta.json"))
            out.write("{\"namespace\":\"sample_pack\"}".toByteArray(StandardCharsets.UTF_8))
            out.closeEntry()

            out.putNextEntry(ZipEntry("sample_pack/data/tacz/index/guns/ak47.json"))
            out.write(sampleIndexJson().toByteArray(StandardCharsets.UTF_8))
            out.closeEntry()

            out.putNextEntry(ZipEntry("sample_pack/assets/tacz/display/guns/ak47_display.json"))
            out.write(sampleDisplayJson().toByteArray(StandardCharsets.UTF_8))
            out.closeEntry()

            out.putNextEntry(ZipEntry("sample_pack/assets/tacz/scripts/ak47_state_machine.lua"))
            out.write(scriptBody.toByteArray(StandardCharsets.UTF_8))
            out.closeEntry()
        }
    }

    private fun sampleIndexJson(): String =
        """
        {
          "display": "tacz:ak47_display"
        }
        """.trimIndent()

    private fun sampleDisplayJson(): String =
        """
        {
          "state_machine": "tacz:ak47_state_machine"
        }
        """.trimIndent()

    private fun luaScript(damageScale: Float, hudText: String): String =
        """
        function legacy_behavior_adjust(ctx)
          return {
            damage_scale = $damageScale
          }
        end

        function legacy_hud_hint(ctx)
          return "$hudText"
        end
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
