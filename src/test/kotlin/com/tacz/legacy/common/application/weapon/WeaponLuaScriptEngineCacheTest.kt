package com.tacz.legacy.common.application.weapon

import com.tacz.legacy.common.application.gunpack.GunDisplayDefinition
import com.tacz.legacy.common.application.gunpack.GunDisplayRuntimeSnapshot
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

public class WeaponLuaScriptEngineCacheTest {

    @After
    public fun cleanup() {
        WeaponLuaScriptEngine.clear()
    }

    @Test
    public fun `evaluate should lazily cache script when preload is skipped`() {
        assertEquals(0, WeaponLuaScriptEngine.cachedScriptCount())

        val displayDefinition = sampleDisplayDefinition(
            gunId = "ak47",
            script = "function legacy_hud_hint(ctx) return 'HUD' end"
        )

        val result = WeaponLuaScriptEngine.evaluate(
            gunId = "ak47",
            displayDefinition = displayDefinition,
            scriptParams = emptyMap(),
            ammoInMagazine = 30,
            ammoReserve = 90
        )

        assertEquals("HUD", result?.hudHint)
        assertEquals(1, WeaponLuaScriptEngine.cachedScriptCount())
    }

    @Test
    public fun `evaluate should prioritize preloaded cache over incoming display definition`() {
        val preloaded = sampleDisplayDefinition(
            gunId = "ak47",
            script = "function legacy_hud_hint(ctx) return 'PRELOADED' end"
        )
        val incoming = sampleDisplayDefinition(
            gunId = "ak47",
            script = "function legacy_hud_hint(ctx) return 'INCOMING' end"
        )

        WeaponLuaScriptEngine.preload(
            GunDisplayRuntimeSnapshot(
                loadedAtEpochMillis = 0L,
                totalSources = 1,
                loadedDefinitionsByGunId = mapOf("ak47" to preloaded),
                failedSources = emptySet()
            )
        )

        val result = WeaponLuaScriptEngine.evaluate(
            gunId = "ak47",
            displayDefinition = incoming,
            scriptParams = emptyMap(),
            ammoInMagazine = 30,
            ammoReserve = 90
        )

        assertEquals("PRELOADED", result?.hudHint)
    }

    @Test
    public fun `evaluate should reuse cache for normalized gun id`() {
        val preloaded = sampleDisplayDefinition(
            gunId = "ak47",
            script = "function legacy_hud_hint(ctx) return ctx.gun_id end"
        )

        WeaponLuaScriptEngine.preload(
            GunDisplayRuntimeSnapshot(
                loadedAtEpochMillis = 0L,
                totalSources = 1,
                loadedDefinitionsByGunId = mapOf("ak47" to preloaded),
                failedSources = emptySet()
            )
        )

        val result = WeaponLuaScriptEngine.evaluate(
            gunId = "  AK47  ",
            displayDefinition = null,
            scriptParams = emptyMap(),
            ammoInMagazine = 30,
            ammoReserve = 90
        )

        assertEquals("ak47", result?.hudHint)
    }

    @Test
    public fun `evaluate should return null when display is missing and cache miss occurs`() {
        val result = WeaponLuaScriptEngine.evaluate(
            gunId = "ak47",
            displayDefinition = null,
            scriptParams = emptyMap(),
            ammoInMagazine = 30,
            ammoReserve = 90
        )

        assertNull(result)
    }

    @Test
    public fun `evaluate should return null when display script source is blank`() {
        val result = WeaponLuaScriptEngine.evaluate(
            gunId = "ak47",
            displayDefinition = sampleDisplayDefinition(gunId = "ak47", script = "   "),
            scriptParams = emptyMap(),
            ammoInMagazine = 30,
            ammoReserve = 90
        )

        assertNull(result)
    }

    @Test
    public fun `evaluate should return null when display script source is null`() {
        val result = WeaponLuaScriptEngine.evaluate(
            gunId = "ak47",
            displayDefinition = sampleDisplayDefinition(gunId = "ak47", script = null),
            scriptParams = emptyMap(),
            ammoInMagazine = 30,
            ammoReserve = 90
        )

        assertNull(result)
    }

    @Test
    public fun `evaluate should fallback to source id when state machine path is missing`() {
        val result = WeaponLuaScriptEngine.evaluate(
            gunId = "ak47",
            displayDefinition = sampleDisplayDefinition(
                gunId = "ak47",
                script = "function legacy_hud_hint(ctx) return 'fallback_source_id' end",
                stateMachinePath = null
            ),
            scriptParams = emptyMap(),
            ammoInMagazine = 30,
            ammoReserve = 90
        )

        assertEquals("fallback_source_id", result?.hudHint)
    }

    @Test
    public fun `clear should drop all cached scripts`() {
        val displayDefinition = sampleDisplayDefinition(
            gunId = "ak47",
            script = "function legacy_hud_hint(ctx) return 'HUD' end"
        )

        WeaponLuaScriptEngine.evaluate(
            gunId = "ak47",
            displayDefinition = displayDefinition,
            scriptParams = emptyMap(),
            ammoInMagazine = 30,
            ammoReserve = 90
        )
        assertEquals(1, WeaponLuaScriptEngine.cachedScriptCount())

        WeaponLuaScriptEngine.clear()
        assertEquals(0, WeaponLuaScriptEngine.cachedScriptCount())

        val resultAfterClear = WeaponLuaScriptEngine.evaluate(
            gunId = "ak47",
            displayDefinition = null,
            scriptParams = emptyMap(),
            ammoInMagazine = 30,
            ammoReserve = 90
        )
        assertNull(resultAfterClear)
    }

    @Test
    public fun `preload should record both hook flags in cache metadata path`() {
        val displayDefinition = sampleDisplayDefinition(
            gunId = "ak47",
            script = """
                function legacy_behavior_adjust(ctx)
                    return { damage_scale = 1.05 }
                end

                function legacy_hud_hint(ctx)
                    return "OK"
                end
            """.trimIndent()
        )

        val cachedCount = WeaponLuaScriptEngine.preload(
            GunDisplayRuntimeSnapshot(
                loadedAtEpochMillis = 0L,
                totalSources = 1,
                loadedDefinitionsByGunId = mapOf("ak47" to displayDefinition),
                failedSources = emptySet()
            )
        )

        val result = WeaponLuaScriptEngine.evaluate(
            gunId = "ak47",
            displayDefinition = null,
            scriptParams = emptyMap(),
            ammoInMagazine = 30,
            ammoReserve = 90
        )

        assertEquals(1, cachedCount)
        assertEquals(1, WeaponLuaScriptEngine.cachedScriptCount())
        assertNotNull(result?.ballisticAdjustments)
        assertEquals("OK", result?.hudHint)
    }

    @Test
    public fun `preload should skip entries with null script content`() {
        val displayDefinition = sampleDisplayDefinition(gunId = "ak47", script = null)
        val snapshot = GunDisplayRuntimeSnapshot(
            loadedAtEpochMillis = 0L,
            totalSources = 1,
            loadedDefinitionsByGunId = mapOf("ak47" to displayDefinition),
            failedSources = emptySet()
        )

        val cachedCount = WeaponLuaScriptEngine.preload(snapshot)

        assertEquals(0, cachedCount)
        assertEquals(0, WeaponLuaScriptEngine.cachedScriptCount())
    }

    private fun sampleDisplayDefinition(
        gunId: String,
        script: String?,
        stateMachinePath: String? = "assets/tacz/scripts/${gunId}_state_machine.lua"
    ): GunDisplayDefinition {
        return GunDisplayDefinition(
            sourceId = "sample_pack/assets/tacz/display/guns/$gunId.json",
            gunId = gunId,
            displayResource = "tacz:$gunId",
            modelPath = null,
            modelTexturePath = null,
            lodModelPath = null,
            lodTexturePath = null,
            slotTexturePath = null,
            animationPath = null,
            stateMachinePath = stateMachinePath,
            stateMachineScriptContent = script,
            playerAnimator3rdPath = null,
            thirdPersonAnimation = null,
            modelParseSucceeded = false,
            modelBoneCount = null,
            modelCubeCount = null,
            animationParseSucceeded = false,
            animationClipCount = null,
            stateMachineResolved = !script.isNullOrBlank(),
            playerAnimatorResolved = false,
            hudTexturePath = null,
            hudEmptyTexturePath = null,
            showCrosshair = true
        )
    }
}
