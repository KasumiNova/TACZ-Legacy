package com.tacz.legacy.common.application.weapon

import com.tacz.legacy.common.application.gunpack.GunDisplayDefinition
import com.tacz.legacy.common.application.gunpack.GunDisplayRuntimeSnapshot
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

public class WeaponLuaScriptEngineTest {

    @After
    public fun cleanup() {
        WeaponLuaScriptEngine.clear()
    }

    @Test
    public fun `preload should cache only display definitions with script source`() {
        val snapshot = GunDisplayRuntimeSnapshot(
            loadedAtEpochMillis = 0L,
            totalSources = 2,
            loadedDefinitionsByGunId = mapOf(
                "ak47" to sampleDisplayDefinition(
                    gunId = "ak47",
                    script = "function legacy_hud_hint(ctx) return 'LOW' end"
                ),
                "m4a1" to sampleDisplayDefinition(
                    gunId = "m4a1",
                    script = null
                )
            ),
            failedSources = emptySet()
        )

        val cached = WeaponLuaScriptEngine.preload(snapshot)

        assertEquals(1, cached)
    }

    @Test
    public fun `evaluate should execute behavior and hud hooks`() {
        val script = """
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
                if ctx.ammo_reserve <= 10 then
                    return "SCRIPT: RES LOW"
                end
                return nil
            end
        """.trimIndent()

        val displayDefinition = sampleDisplayDefinition(gunId = "ak47", script = script)
        WeaponLuaScriptEngine.preload(
            GunDisplayRuntimeSnapshot(
                loadedAtEpochMillis = 0L,
                totalSources = 1,
                loadedDefinitionsByGunId = mapOf("ak47" to displayDefinition),
                failedSources = emptySet()
            )
        )

        val result = WeaponLuaScriptEngine.evaluate(
            gunId = "ak47",
            displayDefinition = displayDefinition,
            scriptParams = mapOf("bonus_speed" to 1.1f),
            ammoInMagazine = 3,
            ammoReserve = 8
        )

        assertNotNull(result)
        assertEquals(1.25f, result?.ballisticAdjustments?.damageScale ?: 0f, 0.0001f)
        assertEquals(1.1f, result?.ballisticAdjustments?.speedScale ?: 0f, 0.0001f)
        assertEquals(0.85f, result?.ballisticAdjustments?.rpmScale ?: 0f, 0.0001f)
        assertEquals("SCRIPT: RES LOW", result?.hudHint)
    }

    @Test
    public fun `evaluate should return null when script has no hooks`() {
        val displayDefinition = sampleDisplayDefinition(
            gunId = "ak47",
            script = "local x = 1 + 1"
        )

        val result = WeaponLuaScriptEngine.evaluate(
            gunId = "ak47",
            displayDefinition = displayDefinition,
            scriptParams = emptyMap(),
            ammoInMagazine = 30,
            ammoReserve = 90
        )

        assertNull(result)
    }

    private fun sampleDisplayDefinition(gunId: String, script: String?): GunDisplayDefinition {
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
            stateMachinePath = "assets/tacz/scripts/${gunId}_state_machine.lua",
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
