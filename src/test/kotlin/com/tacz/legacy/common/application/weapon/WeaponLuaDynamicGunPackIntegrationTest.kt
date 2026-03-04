package com.tacz.legacy.common.application.weapon

import com.tacz.legacy.common.application.gunpack.GunDisplayRuntimeRegistry
import com.tacz.legacy.common.infrastructure.mc.gunpack.GunDisplayPreInitScanner
import org.apache.logging.log4j.LogManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

public class WeaponLuaDynamicGunPackIntegrationTest {

    private val scanner: GunDisplayPreInitScanner = GunDisplayPreInitScanner()

    @After
    public fun cleanup() {
        WeaponLuaScriptEngine.clear()
    }

    @Test
    public fun `directory gunpack should load lua script and evaluate hooks`() {
        val gameRoot = Files.createTempDirectory("tacz-legacy-lua-dir-pack-")
        try {
            val configRoot = gameRoot.resolve("config")
            Files.createDirectories(configRoot)

            val packRoot = gameRoot.resolve("tacz").resolve("sample_lua_pack")
            stageDirectoryPack(packRoot)

            val report = scanner.scanAndLog(configRoot, LogManager.getLogger("WeaponLuaDynamicDirPackTest"))
            assertNotNull(report)
            assertEquals(1, report?.successCount)

            val snapshot = GunDisplayRuntimeRegistry().replace(report)
            val definition = snapshot.findDefinition("ak47")
            assertNotNull(definition)

            val hook = WeaponLuaScriptEngine.evaluate(
                gunId = "ak47",
                displayDefinition = definition,
                scriptParams = mapOf("bonus_speed" to 1.2f),
                ammoInMagazine = 2,
                ammoReserve = 50
            )

            assertNotNull(hook)
            assertEquals(1.25f, hook?.ballisticAdjustments?.damageScale ?: 0f, DELTA)
            assertEquals(1.2f, hook?.ballisticAdjustments?.speedScale ?: 0f, DELTA)
            assertEquals(0.85f, hook?.ballisticAdjustments?.rpmScale ?: 0f, DELTA)
            assertEquals("SCRIPT: MAG LOW", hook?.hudHint)
        } finally {
            deleteRecursively(gameRoot)
        }
    }

    @Test
    public fun `zip gunpack should load lua script and evaluate hooks`() {
        val gameRoot = Files.createTempDirectory("tacz-legacy-lua-zip-pack-")
        try {
            val configRoot = gameRoot.resolve("config")
            Files.createDirectories(configRoot)
            val taczRoot = gameRoot.resolve("tacz")
            Files.createDirectories(taczRoot)

            createPackZip(taczRoot.resolve("sample_lua_pack.zip"))

            val report = scanner.scanAndLog(configRoot, LogManager.getLogger("WeaponLuaDynamicZipPackTest"))
            assertNotNull(report)
            assertEquals(1, report?.successCount)

            val snapshot = GunDisplayRuntimeRegistry().replace(report)
            val definition = snapshot.findDefinition("ak47")
            assertNotNull(definition)

            val hook = WeaponLuaScriptEngine.evaluate(
                gunId = "ak47",
                displayDefinition = definition,
                scriptParams = mapOf("bonus_speed" to 1.15f),
                ammoInMagazine = 3,
                ammoReserve = 80
            )

            assertNotNull(hook)
            assertEquals(1.25f, hook?.ballisticAdjustments?.damageScale ?: 0f, DELTA)
            assertEquals(1.15f, hook?.ballisticAdjustments?.speedScale ?: 0f, DELTA)
            assertEquals("SCRIPT: MAG LOW", hook?.hudHint)
        } finally {
            deleteRecursively(gameRoot)
        }
    }

    private fun stageDirectoryPack(packRoot: Path) {
        Files.createDirectories(packRoot)
        Files.write(
            packRoot.resolve("gunpack.meta.json"),
            "{\"namespace\":\"sample_lua_pack\"}".toByteArray(StandardCharsets.UTF_8)
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

        val scriptFile = packRoot
            .resolve("assets")
            .resolve("tacz")
            .resolve("scripts")
            .resolve("ak47_state_machine.lua")
        Files.createDirectories(scriptFile.parent)
        Files.write(scriptFile, sampleStateMachineLua().toByteArray(StandardCharsets.UTF_8))
    }

    private fun createPackZip(zipPath: Path) {
        ZipOutputStream(Files.newOutputStream(zipPath)).use { out ->
            out.putNextEntry(ZipEntry("gunpack.meta.json"))
            out.write("{\"namespace\":\"sample_lua_pack\"}".toByteArray(StandardCharsets.UTF_8))
            out.closeEntry()

            out.putNextEntry(ZipEntry("data/tacz/index/guns/ak47.json"))
            out.write(sampleIndexJson().toByteArray(StandardCharsets.UTF_8))
            out.closeEntry()

            out.putNextEntry(ZipEntry("assets/tacz/display/guns/ak47_display.json"))
            out.write(sampleDisplayJson().toByteArray(StandardCharsets.UTF_8))
            out.closeEntry()

            out.putNextEntry(ZipEntry("assets/tacz/scripts/ak47_state_machine.lua"))
            out.write(sampleStateMachineLua().toByteArray(StandardCharsets.UTF_8))
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

    private fun sampleStateMachineLua(): String =
        """
        function legacy_behavior_adjust(ctx)
            local result = {}
            if ctx.ammo_in_magazine <= 5 then
                result.damage_scale = 1.25
                result.rpm_scale = 0.85
            end
            if ctx.script_params and ctx.script_params.bonus_speed then
                result.speed_scale = ctx.script_params.bonus_speed
            end
            return result
        end

        function legacy_hud_hint(ctx)
            if ctx.ammo_in_magazine <= 3 then
                return "SCRIPT: MAG LOW"
            end
            return nil
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

    private companion object {
        private const val DELTA: Float = 0.0001f
    }
}
