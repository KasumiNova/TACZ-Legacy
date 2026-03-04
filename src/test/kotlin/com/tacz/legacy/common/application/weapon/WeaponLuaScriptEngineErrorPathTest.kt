package com.tacz.legacy.common.application.weapon

import com.tacz.legacy.common.application.gunpack.GunDisplayDefinition
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

public class WeaponLuaScriptEngineErrorPathTest {

    @After
    public fun cleanup() {
        WeaponLuaScriptEngine.clear()
    }

    @Test
    public fun `evaluate should return null when gun id is blank`() {
        val result = WeaponLuaScriptEngine.evaluate(
            gunId = "   ",
            displayDefinition = sampleDisplayDefinition(
                gunId = "ak47",
                script = "function legacy_hud_hint(ctx) return 'OK' end"
            ),
            scriptParams = emptyMap(),
            ammoInMagazine = 30,
            ammoReserve = 90
        )

        assertNull(result)
    }

    @Test
    public fun `evaluate should return null when script has syntax error`() {
        val brokenScript = "function legacy_behavior_adjust(ctx) return { damage_scale = 1.2 }"

        val result = WeaponLuaScriptEngine.evaluate(
            gunId = "ak47",
            displayDefinition = sampleDisplayDefinition(gunId = "ak47", script = brokenScript),
            scriptParams = emptyMap(),
            ammoInMagazine = 30,
            ammoReserve = 90
        )

        assertNull(result)
    }

    @Test
    public fun `evaluate should ignore non function hooks and return null without fallback payload`() {
        val script = "legacy_behavior_adjust = 1"

        val result = WeaponLuaScriptEngine.evaluate(
            gunId = "ak47",
            displayDefinition = sampleDisplayDefinition(gunId = "ak47", script = script),
            scriptParams = emptyMap(),
            ammoInMagazine = 30,
            ammoReserve = 90
        )

        assertNull(result)
    }

    @Test
    public fun `evaluate should ignore non table behavior hook result`() {
        val script = """
            function legacy_behavior_adjust(ctx)
                return 123
            end
        """.trimIndent()

        val result = WeaponLuaScriptEngine.evaluate(
            gunId = "ak47",
            displayDefinition = sampleDisplayDefinition(gunId = "ak47", script = script),
            scriptParams = emptyMap(),
            ammoInMagazine = 30,
            ammoReserve = 90
        )

        assertNull(result)
    }

    @Test
    public fun `evaluate should sanitize behavior scales when lua values are invalid`() {
        val script = """
            function legacy_behavior_adjust(ctx)
                return {
                    damage_scale = "oops",
                    speed_scale = 0/0,
                    knockback_scale = math.huge,
                    inaccuracy_scale = -2,
                    rpm_scale = 1.5
                }
            end
        """.trimIndent()

        val result = WeaponLuaScriptEngine.evaluate(
            gunId = "ak47",
            displayDefinition = sampleDisplayDefinition(gunId = "ak47", script = script),
            scriptParams = emptyMap(),
            ammoInMagazine = 30,
            ammoReserve = 90
        )

        val adjustments = result?.ballisticAdjustments
        assertNotNull(adjustments)
        assertEquals(1f, adjustments?.damageScale ?: 0f, 0.0001f)
        assertEquals(1f, adjustments?.speedScale ?: 0f, 0.0001f)
        assertEquals(1f, adjustments?.knockbackScale ?: 0f, 0.0001f)
        assertEquals(0f, adjustments?.inaccuracyScale ?: 1f, 0.0001f)
        assertEquals(1.5f, adjustments?.rpmScale ?: 0f, 0.0001f)
    }

    @Test
    public fun `evaluate should coerce negative ammo values before passing context to lua`() {
        val script = """
            function legacy_behavior_adjust(ctx)
                if ctx.ammo_in_magazine == 0 and ctx.ammo_reserve == 0 then
                    return { damage_scale = 2.0 }
                end
                return { damage_scale = 1.0 }
            end
        """.trimIndent()

        val result = WeaponLuaScriptEngine.evaluate(
            gunId = "ak47",
            displayDefinition = sampleDisplayDefinition(gunId = "ak47", script = script),
            scriptParams = emptyMap(),
            ammoInMagazine = -3,
            ammoReserve = -10
        )

        assertEquals(2f, result?.ballisticAdjustments?.damageScale ?: 0f, 0.0001f)
    }

    @Test
    public fun `evaluate should ignore non string hud hook result and preserve behavior payload`() {
        val script = """
            function legacy_behavior_adjust(ctx)
                return { damage_scale = 1.2 }
            end

            function legacy_hud_hint(ctx)
                return { text = "bad" }
            end
        """.trimIndent()

        val result = WeaponLuaScriptEngine.evaluate(
            gunId = "ak47",
            displayDefinition = sampleDisplayDefinition(gunId = "ak47", script = script),
            scriptParams = emptyMap(),
            ammoInMagazine = 20,
            ammoReserve = 60
        )

        assertNotNull(result?.ballisticAdjustments)
        assertEquals(1.2f, result?.ballisticAdjustments?.damageScale ?: 0f, 0.0001f)
        assertNull(result?.hudHint)
    }

    @Test
    public fun `evaluate should trim blank hud result to null`() {
        val script = """
            function legacy_behavior_adjust(ctx)
                return { speed_scale = 1.1 }
            end

            function legacy_hud_hint(ctx)
                return "    "
            end
        """.trimIndent()

        val result = WeaponLuaScriptEngine.evaluate(
            gunId = "ak47",
            displayDefinition = sampleDisplayDefinition(gunId = "ak47", script = script),
            scriptParams = emptyMap(),
            ammoInMagazine = 20,
            ammoReserve = 60
        )

        assertNotNull(result?.ballisticAdjustments)
        assertNull(result?.hudHint)
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
