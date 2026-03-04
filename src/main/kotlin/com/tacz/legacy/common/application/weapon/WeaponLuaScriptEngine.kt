package com.tacz.legacy.common.application.weapon

import com.tacz.legacy.common.application.gunpack.GunDisplayDefinition
import com.tacz.legacy.common.application.gunpack.GunDisplayRuntimeSnapshot
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.jse.JsePlatform
import java.util.concurrent.ConcurrentHashMap

public data class WeaponLuaHookResult(
    val ballisticAdjustments: WeaponLuaBallisticAdjustments? = null,
    val hudHint: String? = null
)

public object WeaponLuaScriptEngine {

    private val scriptsByGunId: MutableMap<String, CachedLuaScript> = ConcurrentHashMap()

    @Synchronized
    public fun preload(snapshot: GunDisplayRuntimeSnapshot): Int {
        scriptsByGunId.clear()

        snapshot.loadedDefinitionsByGunId
            .entries
            .sortedBy { it.key }
            .forEach { (gunId, definition) ->
                val normalizedGunId = gunId.trim().lowercase().ifBlank { return@forEach }
                val rawScript = definition.stateMachineScriptContent ?: return@forEach
                val scriptSource = rawScript.trim()
                if (scriptSource.isBlank()) {
                    return@forEach
                }

                scriptsByGunId[normalizedGunId] = CachedLuaScript(
                    scriptId = definition.stateMachinePath ?: definition.sourceId,
                    scriptSource = scriptSource,
                    hasBehaviorHook = scriptSource.contains(HOOK_BEHAVIOR_FUNCTION),
                    hasHudHook = scriptSource.contains(HOOK_HUD_FUNCTION)
                )
            }

        return scriptsByGunId.size
    }

    @Synchronized
    public fun clear() {
        scriptsByGunId.clear()
    }

    internal fun cachedScriptCount(): Int = scriptsByGunId.size

    public fun evaluate(
        gunId: String,
        displayDefinition: GunDisplayDefinition?,
        scriptParams: Map<String, Float>,
        ammoInMagazine: Int,
        ammoReserve: Int
    ): WeaponLuaHookResult? {
        val normalizedGunId = gunId.trim().lowercase().ifBlank { return null }
        val cachedScript = resolveCachedScript(normalizedGunId, displayDefinition) ?: return null
        if (!cachedScript.hasBehaviorHook && !cachedScript.hasHudHook) {
            return null
        }

        return runCatching {
            val globals = JsePlatform.standardGlobals()
            WeaponLuaRequireSupport.install(globals, cachedScript.scriptId)
            globals.load(cachedScript.scriptSource, "@${cachedScript.scriptId}").call()

            val context = LuaTable().apply {
                set("gun_id", LuaValue.valueOf(normalizedGunId))
                set("ammo_in_magazine", LuaValue.valueOf(ammoInMagazine.coerceAtLeast(0)))
                set("ammo_reserve", LuaValue.valueOf(ammoReserve.coerceAtLeast(0)))
                set("script_params", buildScriptParamsTable(scriptParams))
            }

            val adjustments = if (cachedScript.hasBehaviorHook) {
                readBallisticAdjustmentsFromHook(globals, context)
            } else {
                null
            }

            val hudHint = if (cachedScript.hasHudHook) {
                readHudHintFromHook(globals, context)
            } else {
                null
            }

            if (adjustments == null && hudHint == null) {
                null
            } else {
                WeaponLuaHookResult(
                    ballisticAdjustments = adjustments,
                    hudHint = hudHint
                )
            }
        }.getOrNull()
    }

    private fun resolveCachedScript(gunId: String, displayDefinition: GunDisplayDefinition?): CachedLuaScript? {
        scriptsByGunId[gunId]?.let { return it }

        val rawSource = displayDefinition?.stateMachineScriptContent ?: return null
        val source = rawSource.trim()
        if (source.isBlank()) {
            return null
        }

        val cached = CachedLuaScript(
            scriptId = displayDefinition.stateMachinePath ?: displayDefinition.sourceId,
            scriptSource = source,
            hasBehaviorHook = source.contains(HOOK_BEHAVIOR_FUNCTION),
            hasHudHook = source.contains(HOOK_HUD_FUNCTION)
        )

        scriptsByGunId.putIfAbsent(gunId, cached)
        return scriptsByGunId[gunId]
    }

    private fun buildScriptParamsTable(scriptParams: Map<String, Float>): LuaTable {
        val table = LuaTable()
        scriptParams.forEach { (key, value) ->
            table.set(key, LuaValue.valueOf(value.toDouble()))
        }
        return table
    }

    private fun readBallisticAdjustmentsFromHook(globals: LuaValue, context: LuaTable): WeaponLuaBallisticAdjustments? {
        val function = globals.get(HOOK_BEHAVIOR_FUNCTION)
        if (!function.isfunction()) {
            return null
        }

        val result = function.call(context)
        if (!result.istable()) {
            return null
        }

        return WeaponLuaBallisticAdjustments(
            damageScale = readScale(result, "damage_scale"),
            speedScale = readScale(result, "speed_scale"),
            knockbackScale = readScale(result, "knockback_scale"),
            inaccuracyScale = readScale(result, "inaccuracy_scale"),
            rpmScale = readScale(result, "rpm_scale")
        )
    }

    private fun readHudHintFromHook(globals: LuaValue, context: LuaTable): String? {
        val function = globals.get(HOOK_HUD_FUNCTION)
        if (!function.isfunction()) {
            return null
        }

        val result = function.call(context)
        if (!result.isstring()) {
            return null
        }

        return result.tojstring().trim().ifBlank { null }
    }

    private fun readScale(table: LuaValue, key: String): Float {
        val value = table.get(key)
        if (!value.isnumber()) {
            return 1f
        }
        val numeric = value.tofloat()
        if (!numeric.isFinite()) {
            return 1f
        }
        return numeric.coerceAtLeast(0f)
    }

    private data class CachedLuaScript(
        val scriptId: String,
        val scriptSource: String,
        val hasBehaviorHook: Boolean,
        val hasHudHook: Boolean
    )

    private const val HOOK_BEHAVIOR_FUNCTION: String = "legacy_behavior_adjust"
    private const val HOOK_HUD_FUNCTION: String = "legacy_hud_hint"
}
