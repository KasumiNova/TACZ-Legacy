package com.tacz.legacy.common.application.weapon

public data class WeaponLuaBallisticAdjustments(
    val damageScale: Float = 1f,
    val speedScale: Float = 1f,
    val knockbackScale: Float = 1f,
    val inaccuracyScale: Float = 1f,
    val rpmScale: Float = 1f
)

public object WeaponLuaScriptRuntime {

    public fun combineBallisticAdjustments(
        base: WeaponLuaBallisticAdjustments,
        overlay: WeaponLuaBallisticAdjustments?
    ): WeaponLuaBallisticAdjustments {
        if (overlay == null) {
            return base
        }
        return WeaponLuaBallisticAdjustments(
            damageScale = (base.damageScale * overlay.damageScale).coerceAtLeast(0f),
            speedScale = (base.speedScale * overlay.speedScale).coerceAtLeast(0f),
            knockbackScale = (base.knockbackScale * overlay.knockbackScale).coerceAtLeast(0f),
            inaccuracyScale = (base.inaccuracyScale * overlay.inaccuracyScale).coerceAtLeast(0f),
            rpmScale = (base.rpmScale * overlay.rpmScale).coerceAtLeast(0f)
        )
    }

    public fun resolveBallisticAdjustments(scriptParams: Map<String, Float>): WeaponLuaBallisticAdjustments {
        return WeaponLuaBallisticAdjustments(
            damageScale = readPositiveScale(scriptParams, DAMAGE_SCALE_KEYS),
            speedScale = readPositiveScale(scriptParams, SPEED_SCALE_KEYS),
            knockbackScale = readPositiveScale(scriptParams, KNOCKBACK_SCALE_KEYS),
            inaccuracyScale = readPositiveScale(scriptParams, INACCURACY_SCALE_KEYS),
            rpmScale = readPositiveScale(scriptParams, RPM_SCALE_KEYS)
        )
    }

    public fun resolveHudHint(
        scriptParams: Map<String, Float>,
        ammoInMagazine: Int,
        ammoReserve: Int
    ): String? {
        val lowMagThreshold = readThreshold(scriptParams, LOW_MAG_THRESHOLD_KEYS)
        if (lowMagThreshold != null && ammoInMagazine <= lowMagThreshold) {
            return "LUA: MAG LOW"
        }

        val lowReserveThreshold = readThreshold(scriptParams, LOW_RESERVE_THRESHOLD_KEYS)
        if (lowReserveThreshold != null && ammoReserve <= lowReserveThreshold) {
            return "LUA: RES LOW"
        }

        return null
    }

    private fun readPositiveScale(scriptParams: Map<String, Float>, aliases: Set<String>): Float {
        val value = aliases
            .asSequence()
            .mapNotNull { key -> scriptParams[key] }
            .firstOrNull()
            ?: return 1f
        if (!value.isFinite()) {
            return 1f
        }
        return value.coerceAtLeast(0f)
    }

    private fun readThreshold(scriptParams: Map<String, Float>, aliases: Set<String>): Int? {
        val value = aliases
            .asSequence()
            .mapNotNull { key -> scriptParams[key] }
            .firstOrNull()
            ?: return null
        if (!value.isFinite()) {
            return null
        }
        return value.toInt().coerceAtLeast(0)
    }

    private val DAMAGE_SCALE_KEYS: Set<String> = setOf("lua_damage_scale", "script_damage_scale", "damage_scale")
    private val SPEED_SCALE_KEYS: Set<String> = setOf("lua_speed_scale", "script_speed_scale", "speed_scale")
    private val KNOCKBACK_SCALE_KEYS: Set<String> = setOf("lua_knockback_scale", "script_knockback_scale", "knockback_scale")
    private val INACCURACY_SCALE_KEYS: Set<String> = setOf("lua_inaccuracy_scale", "script_inaccuracy_scale", "inaccuracy_scale")
    private val RPM_SCALE_KEYS: Set<String> = setOf("lua_rpm_scale", "script_rpm_scale", "rpm_scale")
    private val LOW_MAG_THRESHOLD_KEYS: Set<String> = setOf("lua_hud_low_mag_threshold", "hud_low_mag_threshold")
    private val LOW_RESERVE_THRESHOLD_KEYS: Set<String> = setOf("lua_hud_low_reserve_threshold", "hud_low_reserve_threshold")
}
