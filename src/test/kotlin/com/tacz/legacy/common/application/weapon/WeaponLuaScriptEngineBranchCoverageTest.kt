package com.tacz.legacy.common.application.weapon

import com.tacz.legacy.common.application.gunpack.GunDisplayDefinition
import com.tacz.legacy.common.application.gunpack.GunDisplayRuntimeSnapshot
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

public class WeaponLuaScriptEngineBranchCoverageTest {

    @After
    public fun cleanup() {
        WeaponLuaScriptEngine.clear()
    }

    @Test
    public fun `preload should normalize gun id and ignore blank entries`() {
        val valid = sampleDisplayDefinition(
            gunId = "ak47",
            script = "function legacy_hud_hint(ctx) return '  OK  ' end"
        )
        val blankScript = sampleDisplayDefinition(
            gunId = "m4a1",
            script = "   "
        )
        val emptyScript = sampleDisplayDefinition(
            gunId = "hk416",
            script = ""
        )
        val nullScript = sampleDisplayDefinition(
            gunId = "famas",
            script = null
        )

        val cached = WeaponLuaScriptEngine.preload(
            GunDisplayRuntimeSnapshot(
                loadedAtEpochMillis = 0L,
                totalSources = 5,
                loadedDefinitionsByGunId = mapOf(
                    "  AK47  " to valid,
                    "   " to valid,
                    "m4a1" to blankScript,
                    "hk416" to emptyScript,
                    "famas" to nullScript
                ),
                failedSources = emptySet()
            )
        )

        assertEquals(1, cached)
        assertEquals(1, WeaponLuaScriptEngine.cachedScriptCount())

        val result = WeaponLuaScriptEngine.evaluate(
            gunId = "  ak47  ",
            displayDefinition = null,
            scriptParams = emptyMap(),
            ammoInMagazine = 30,
            ammoReserve = 90
        )

        assertNotNull(result)
        assertNull(result?.ballisticAdjustments)
        assertEquals("OK", result?.hudHint)
    }

    @Test
    public fun `evaluate should return null for blank gun id`() {
        val result = WeaponLuaScriptEngine.evaluate(
            gunId = "   ",
            displayDefinition = sampleDisplayDefinition("ak47", "function legacy_hud_hint(ctx) return 'X' end"),
            scriptParams = emptyMap(),
            ammoInMagazine = 1,
            ammoReserve = 1
        )

        assertNull(result)
    }

    @Test
    public fun `evaluate should return null when script compilation fails`() {
        val brokenScript = "function legacy_hud_hint(ctx) return 'BROKEN'"
        val definition = sampleDisplayDefinition(gunId = "ak47", script = brokenScript)

        val result = WeaponLuaScriptEngine.evaluate(
            gunId = "ak47",
            displayDefinition = definition,
            scriptParams = emptyMap(),
            ammoInMagazine = 10,
            ammoReserve = 10
        )

        assertNull(result)
    }

    @Test
    public fun `evaluate should return null when display script content is blank`() {
        val definition = sampleDisplayDefinition(gunId = "ak47", script = "   ")

        val result = WeaponLuaScriptEngine.evaluate(
            gunId = "ak47",
            displayDefinition = definition,
            scriptParams = emptyMap(),
            ammoInMagazine = 10,
            ammoReserve = 10
        )

        assertNull(result)
    }

    @Test
    public fun `evaluate should return null when display script content is empty string`() {
        val definition = sampleDisplayDefinition(gunId = "ak47", script = "")

        val result = WeaponLuaScriptEngine.evaluate(
            gunId = "ak47",
            displayDefinition = definition,
            scriptParams = emptyMap(),
            ammoInMagazine = 10,
            ammoReserve = 10
        )

        assertNull(result)
    }

    @Test
    public fun `evaluate should return null when display script is whitespace newlines`() {
        val definition = sampleDisplayDefinition(gunId = "ak47", script = "\n\t  \n")

        val result = WeaponLuaScriptEngine.evaluate(
            gunId = "ak47",
            displayDefinition = definition,
            scriptParams = emptyMap(),
            ammoInMagazine = 10,
            ammoReserve = 10
        )

        assertNull(result)
    }

    @Test
    public fun `evaluate should return null when display script content is null`() {
        val definition = sampleDisplayDefinition(gunId = "ak47", script = null)

        val result = WeaponLuaScriptEngine.evaluate(
            gunId = "ak47",
            displayDefinition = definition,
            scriptParams = emptyMap(),
            ammoInMagazine = 10,
            ammoReserve = 10
        )

        assertNull(result)
    }

    @Test
    public fun `evaluate should return null when hooks are not functions`() {
        val script = """
            legacy_behavior_adjust = 1
            legacy_hud_hint = 2
        """.trimIndent()
        val definition = sampleDisplayDefinition(gunId = "ak47", script = script)

        val result = WeaponLuaScriptEngine.evaluate(
            gunId = "AK47",
            displayDefinition = definition,
            scriptParams = emptyMap(),
            ammoInMagazine = 10,
            ammoReserve = 10
        )

        assertNull(result)
    }

    @Test
    public fun `evaluate should return null when behavior hook returns non table`() {
        val script = "function legacy_behavior_adjust(ctx) return 123 end"
        val definition = sampleDisplayDefinition(gunId = "ak47", script = script)

        val result = WeaponLuaScriptEngine.evaluate(
            gunId = "ak47",
            displayDefinition = definition,
            scriptParams = emptyMap(),
            ammoInMagazine = 10,
            ammoReserve = 10
        )

        assertNull(result)
    }

    @Test
    public fun `evaluate should return null when hud hook returns non string`() {
        val script = "function legacy_hud_hint(ctx) return { value = 99 } end"
        val definition = sampleDisplayDefinition(gunId = "ak47", script = script)

        val result = WeaponLuaScriptEngine.evaluate(
            gunId = "ak47",
            displayDefinition = definition,
            scriptParams = emptyMap(),
            ammoInMagazine = 10,
            ammoReserve = 10
        )

        assertNull(result)
    }

    @Test
    public fun `evaluate should return null when hud hook returns blank string`() {
        val script = "function legacy_hud_hint(ctx) return '   ' end"
        val definition = sampleDisplayDefinition(gunId = "ak47", script = script)

        val result = WeaponLuaScriptEngine.evaluate(
            gunId = "ak47",
            displayDefinition = definition,
            scriptParams = emptyMap(),
            ammoInMagazine = 10,
            ammoReserve = 10
        )

        assertNull(result)
    }

    @Test
    public fun `evaluate should stringify numeric hud hook result under luaj semantics`() {
        val script = "function legacy_hud_hint(ctx) return 123 end"
        val definition = sampleDisplayDefinition(gunId = "ak47", script = script)

        val result = WeaponLuaScriptEngine.evaluate(
            gunId = "ak47",
            displayDefinition = definition,
            scriptParams = emptyMap(),
            ammoInMagazine = 10,
            ammoReserve = 10
        )

        assertEquals("123", result?.hudHint)
    }

    @Test
    public fun `evaluate should sanitize invalid scale values`() {
        val script = """
            function legacy_behavior_adjust(ctx)
                return {
                    damage_scale = 'not-a-number',
                    speed_scale = 0/0,
                    knockback_scale = math.huge,
                    inaccuracy_scale = -2,
                    rpm_scale = 1.5
                }
            end
        """.trimIndent()
        val definition = sampleDisplayDefinition(gunId = "ak47", script = script)

        val result = WeaponLuaScriptEngine.evaluate(
            gunId = "ak47",
            displayDefinition = definition,
            scriptParams = emptyMap(),
            ammoInMagazine = 10,
            ammoReserve = 10
        )

        val adjustments = result?.ballisticAdjustments
        assertNotNull(adjustments)
        assertEquals(1f, adjustments?.damageScale ?: 0f, 0.0001f)
        assertEquals(1f, adjustments?.speedScale ?: 0f, 0.0001f)
        assertEquals(1f, adjustments?.knockbackScale ?: 0f, 0.0001f)
        assertEquals(0f, adjustments?.inaccuracyScale ?: -1f, 0.0001f)
        assertEquals(1.5f, adjustments?.rpmScale ?: 0f, 0.0001f)
    }

    @Test
    public fun `evaluate should lazily cache script and rebuild after clear`() {
        val definition = sampleDisplayDefinition(
            gunId = "m4a1",
            script = "function legacy_hud_hint(ctx) return 'CACHE' end"
        )

        assertNull(
            WeaponLuaScriptEngine.evaluate(
                gunId = "m4a1",
                displayDefinition = null,
                scriptParams = emptyMap(),
                ammoInMagazine = 30,
                ammoReserve = 90
            )
        )

        val first = WeaponLuaScriptEngine.evaluate(
            gunId = "  M4A1  ",
            displayDefinition = definition,
            scriptParams = emptyMap(),
            ammoInMagazine = 30,
            ammoReserve = 90
        )
        assertEquals("CACHE", first?.hudHint)
        assertEquals(1, WeaponLuaScriptEngine.cachedScriptCount())

        val second = WeaponLuaScriptEngine.evaluate(
            gunId = "m4a1",
            displayDefinition = null,
            scriptParams = emptyMap(),
            ammoInMagazine = 30,
            ammoReserve = 90
        )
        assertEquals("CACHE", second?.hudHint)

        WeaponLuaScriptEngine.clear()
        assertEquals(0, WeaponLuaScriptEngine.cachedScriptCount())

        val rebuilt = WeaponLuaScriptEngine.evaluate(
            gunId = "m4a1",
            displayDefinition = definition,
            scriptParams = emptyMap(),
            ammoInMagazine = 30,
            ammoReserve = 90
        )
        assertEquals("CACHE", rebuilt?.hudHint)
    }

    @Test
    public fun `preload and evaluate should fallback to sourceId when stateMachinePath is null`() {
        val definition = sampleDisplayDefinition(
            gunId = "scar",
            script = "function legacy_hud_hint(ctx) return 'SCAR' end"
        ).copy(stateMachinePath = null)

        val cached = WeaponLuaScriptEngine.preload(
            GunDisplayRuntimeSnapshot(
                loadedAtEpochMillis = 0L,
                totalSources = 1,
                loadedDefinitionsByGunId = mapOf("scar" to definition),
                failedSources = emptySet()
            )
        )
        assertEquals(1, cached)

        val result = WeaponLuaScriptEngine.evaluate(
            gunId = "scar",
            displayDefinition = definition,
            scriptParams = emptyMap(),
            ammoInMagazine = 30,
            ammoReserve = 90
        )

        assertEquals("SCAR", result?.hudHint)
    }

    @Test
    public fun `preload should ignore script content that trims to empty`() {
        val definition = sampleDisplayDefinition(
            gunId = "aug",
            script = "\n\t  \n"
        )

        val cached = WeaponLuaScriptEngine.preload(
            GunDisplayRuntimeSnapshot(
                loadedAtEpochMillis = 0L,
                totalSources = 1,
                loadedDefinitionsByGunId = mapOf("aug" to definition),
                failedSources = emptySet()
            )
        )

        assertEquals(0, cached)
    }

    @Test
    public fun `evaluate should resolve sourceId fallback when stateMachinePath is null without preload`() {
        val definition = sampleDisplayDefinition(
            gunId = "g36",
            script = "function legacy_hud_hint(ctx) return 'G36' end"
        ).copy(stateMachinePath = null)

        val result = WeaponLuaScriptEngine.evaluate(
            gunId = "g36",
            displayDefinition = definition,
            scriptParams = emptyMap(),
            ammoInMagazine = 30,
            ammoReserve = 90
        )

        assertEquals("G36", result?.hudHint)
    }

    @Test
    public fun `evaluate should support bundled default module require in hud hook`() {
        val definition = sampleDisplayDefinition(
            gunId = "ak47",
            script = """
                local default = require("tacz_default_state_machine")

                function legacy_hud_hint(ctx)
                    if default ~= nil and default.main_track_states ~= nil then
                        return "REQ_OK"
                    end
                    return nil
                end
            """.trimIndent()
        )

        val result = WeaponLuaScriptEngine.evaluate(
            gunId = "ak47",
            displayDefinition = definition,
            scriptParams = emptyMap(),
            ammoInMagazine = 10,
            ammoReserve = 10
        )

        assertEquals("REQ_OK", result?.hudHint)
    }

    @Test
    public fun `evaluate should support dynamic namespace require in behavior hook`() {
        val definition = sampleDisplayDefinition(
            gunId = "ak47",
            script = """
                local state_machine = require("tacz_ak47_state_machine")

                function legacy_behavior_adjust(ctx)
                    if state_machine ~= nil and state_machine.main_track_states ~= nil then
                        return { damage_scale = 1.2 }
                    end
                    return nil
                end
            """.trimIndent()
        )

        val result = WeaponLuaScriptEngine.evaluate(
            gunId = "ak47",
            displayDefinition = definition,
            scriptParams = emptyMap(),
            ammoInMagazine = 10,
            ammoReserve = 10
        )

        assertEquals(1.2f, result?.ballisticAdjustments?.damageScale ?: 0f, 0.0001f)
    }

    @Test
    public fun `evaluate should support script relative require resolution`() {
        val definition = sampleDisplayDefinition(
            gunId = "ak47",
            script = """
                local default = require("default_state_machine")

                function legacy_hud_hint(ctx)
                    if default ~= nil and default.main_track_states ~= nil then
                        return "RELATIVE_OK"
                    end
                    return nil
                end
            """.trimIndent()
        ).copy(
            stateMachinePath = "assets/tacz/custom/tacz_default_gun/assets/tacz/scripts/test_wrapper_state_machine.lua"
        )

        val result = WeaponLuaScriptEngine.evaluate(
            gunId = "ak47",
            displayDefinition = definition,
            scriptParams = emptyMap(),
            ammoInMagazine = 10,
            ammoReserve = 10
        )

        assertEquals("RELATIVE_OK", result?.hudHint)
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