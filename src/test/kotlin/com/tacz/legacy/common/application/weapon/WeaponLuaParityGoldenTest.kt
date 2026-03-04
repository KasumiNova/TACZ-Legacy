package com.tacz.legacy.common.application.weapon

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.tacz.legacy.common.application.gunpack.GunDisplayDefinition
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

public class WeaponLuaParityGoldenTest {

    @After
    public fun cleanup() {
        WeaponLuaScriptEngine.clear()
    }

    @Test
    public fun `golden scenarios should produce expected lua adjustments and hud hints`() {
        SCENARIO_FILES.forEach { resourcePath ->
            val scenario = readScenario(resourcePath)
            val displayDefinition = sampleDisplayDefinition(
                gunId = scenario.gunId,
                stateMachinePath = scenario.scriptId,
                script = scenario.script
            )

            val hookResult = WeaponLuaScriptEngine.evaluate(
                gunId = scenario.gunId,
                displayDefinition = displayDefinition,
                scriptParams = scenario.scriptParams,
                ammoInMagazine = scenario.ammoInMagazine,
                ammoReserve = scenario.ammoReserve
            )

            val combined = WeaponLuaScriptRuntime.combineBallisticAdjustments(
                base = WeaponLuaScriptRuntime.resolveBallisticAdjustments(scenario.scriptParams),
                overlay = hookResult?.ballisticAdjustments
            )

            val hudHint = hookResult?.hudHint ?: WeaponLuaScriptRuntime.resolveHudHint(
                scriptParams = scenario.scriptParams,
                ammoInMagazine = scenario.ammoInMagazine,
                ammoReserve = scenario.ammoReserve
            )

            assertEquals(
                "scenario=${scenario.scenarioId} damageScale",
                scenario.expected.damageScale,
                combined.damageScale,
                DELTA
            )
            assertEquals(
                "scenario=${scenario.scenarioId} speedScale",
                scenario.expected.speedScale,
                combined.speedScale,
                DELTA
            )
            assertEquals(
                "scenario=${scenario.scenarioId} knockbackScale",
                scenario.expected.knockbackScale,
                combined.knockbackScale,
                DELTA
            )
            assertEquals(
                "scenario=${scenario.scenarioId} inaccuracyScale",
                scenario.expected.inaccuracyScale,
                combined.inaccuracyScale,
                DELTA
            )
            assertEquals(
                "scenario=${scenario.scenarioId} rpmScale",
                scenario.expected.rpmScale,
                combined.rpmScale,
                DELTA
            )

            if (scenario.expected.hudHint == null) {
                assertNull("scenario=${scenario.scenarioId} hudHint", hudHint)
            } else {
                assertEquals(
                    "scenario=${scenario.scenarioId} hudHint",
                    scenario.expected.hudHint,
                    hudHint
                )
            }

            WeaponLuaScriptEngine.clear()
        }
    }

    private fun readScenario(resourcePath: String): Scenario {
        val json = requireNotNull(javaClass.classLoader.getResource(resourcePath)) {
            "scenario resource not found: $resourcePath"
        }.readText()

        val root = JsonParser().parse(json).asJsonObject
        val expected = root.readObject("expected")

        return Scenario(
            scenarioId = root.readString("scenarioId") ?: resourcePath,
            gunId = root.readString("gunId") ?: "unknown_gun",
            scriptId = root.readString("scriptId"),
            script = root.readString("script") ?: "",
            scriptParams = root.readFloatMap("scriptParams"),
            ammoInMagazine = root.readInt("ammoInMagazine") ?: 0,
            ammoReserve = root.readInt("ammoReserve") ?: 0,
            expected = Expected(
                damageScale = expected.readFloat("damageScale") ?: 1f,
                speedScale = expected.readFloat("speedScale") ?: 1f,
                knockbackScale = expected.readFloat("knockbackScale") ?: 1f,
                inaccuracyScale = expected.readFloat("inaccuracyScale") ?: 1f,
                rpmScale = expected.readFloat("rpmScale") ?: 1f,
                hudHint = expected.readString("hudHint")
            )
        )
    }

    private fun sampleDisplayDefinition(
        gunId: String,
        stateMachinePath: String?,
        script: String
    ): GunDisplayDefinition {
        return GunDisplayDefinition(
            sourceId = "golden_pack/assets/tacz/display/guns/$gunId.json",
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
            stateMachineResolved = script.isNotBlank(),
            playerAnimatorResolved = false,
            hudTexturePath = null,
            hudEmptyTexturePath = null,
            showCrosshair = true
        )
    }

    private fun JsonObject.readString(name: String): String? {
        val element = get(name) ?: return null
        if (!element.isJsonPrimitive || !element.asJsonPrimitive.isString) {
            return null
        }
        return element.asString
    }

    private fun JsonObject.readInt(name: String): Int? {
        val element = get(name) ?: return null
        if (!element.isJsonPrimitive || !element.asJsonPrimitive.isNumber) {
            return null
        }
        return element.asInt
    }

    private fun JsonObject.readFloat(name: String): Float? {
        val element = get(name) ?: return null
        if (!element.isJsonPrimitive || !element.asJsonPrimitive.isNumber) {
            return null
        }
        val value = element.asFloat
        return if (value.isFinite()) value else null
    }

    private fun JsonObject.readObject(name: String): JsonObject {
        val element = get(name)
        if (element == null || !element.isJsonObject) {
            return JsonObject()
        }
        return element.asJsonObject
    }

    private fun JsonObject.readFloatMap(name: String): Map<String, Float> {
        val element = get(name)
        if (element == null || !element.isJsonObject) {
            return emptyMap()
        }

        val out = linkedMapOf<String, Float>()
        element.asJsonObject.entrySet().forEach { (key, rawValue) ->
            if (!rawValue.isJsonPrimitive || !rawValue.asJsonPrimitive.isNumber) {
                return@forEach
            }
            val value = rawValue.asFloat
            if (!value.isFinite()) {
                return@forEach
            }
            out[key.trim().lowercase()] = value
        }
        return out
    }

    private data class Scenario(
        val scenarioId: String,
        val gunId: String,
        val scriptId: String?,
        val script: String,
        val scriptParams: Map<String, Float>,
        val ammoInMagazine: Int,
        val ammoReserve: Int,
        val expected: Expected
    )

    private data class Expected(
        val damageScale: Float,
        val speedScale: Float,
        val knockbackScale: Float,
        val inaccuracyScale: Float,
        val rpmScale: Float,
        val hudHint: String?
    )

    private companion object {
        private const val DELTA: Float = 0.0001f
        private val SCENARIO_FILES: List<String> = listOf(
            "lua-parity/scenarios/ak47_low_ammo_golden.json",
            "lua-parity/scenarios/ak47_runtime_fallback_golden.json",
            "lua-parity/scenarios/m4_alias_base_only_golden.json",
            "lua-parity/scenarios/m4_negative_base_clamp_golden.json",
            "lua-parity/scenarios/scar_overlay_partial_multiply_golden.json",
            "lua-parity/scenarios/scar_overlay_negative_and_nan_golden.json",
            "lua-parity/scenarios/g36_hud_script_blank_fallback_mag_golden.json",
            "lua-parity/scenarios/g36_hud_script_override_runtime_golden.json",
            "lua-parity/scenarios/aug_script_error_runtime_fallback_reserve_golden.json",
            "lua-parity/scenarios/famas_non_table_and_non_string_hook_golden.json",
            "lua-parity/scenarios/ak12_alias_precedence_with_overlay_golden.json",
            "lua-parity/scenarios/uzi_overlay_rpm_only_golden.json",
            "lua-parity/scenarios/mp5_hud_low_reserve_no_script_golden.json",
            "lua-parity/scenarios/mp5_hud_priority_mag_over_reserve_golden.json",
            "lua-parity/scenarios/qbz_unknown_params_ignored_golden.json",
            "lua-parity/scenarios/hk416_behavior_empty_table_keeps_base_golden.json",
            "lua-parity/scenarios/hk416_behavior_string_values_ignored_golden.json",
            "lua-parity/scenarios/fal_hud_number_fallback_reserve_golden.json",
            "lua-parity/scenarios/fal_whitespace_script_trim_and_hook_golden.json",
            "lua-parity/scenarios/an94_base_zero_overlay_any_golden.json"
        )
    }
}
