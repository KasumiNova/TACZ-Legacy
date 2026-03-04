package com.tacz.legacy.common.application.weapon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

public class WeaponLuaScriptRuntimeBranchCoverageTest {

    @Test
    public fun `combineBallisticAdjustments should return base when overlay is null`() {
        val base = WeaponLuaBallisticAdjustments(
            damageScale = 1.1f,
            speedScale = 0.9f,
            knockbackScale = 1.2f,
            inaccuracyScale = 0.8f,
            rpmScale = 1.05f
        )

        val combined = WeaponLuaScriptRuntime.combineBallisticAdjustments(base, null)

        assertSame(base, combined)
    }

    @Test
    public fun `combineBallisticAdjustments should clamp negative multiplied values to zero`() {
        val combined = WeaponLuaScriptRuntime.combineBallisticAdjustments(
            base = WeaponLuaBallisticAdjustments(
                damageScale = -1f,
                speedScale = 2f,
                knockbackScale = -0.5f,
                inaccuracyScale = 1f,
                rpmScale = 0.5f
            ),
            overlay = WeaponLuaBallisticAdjustments(
                damageScale = 2f,
                speedScale = -1f,
                knockbackScale = 3f,
                inaccuracyScale = -2f,
                rpmScale = 2f
            )
        )

        assertEquals(0f, combined.damageScale, 0.0001f)
        assertEquals(0f, combined.speedScale, 0.0001f)
        assertEquals(0f, combined.knockbackScale, 0.0001f)
        assertEquals(0f, combined.inaccuracyScale, 0.0001f)
        assertEquals(1f, combined.rpmScale, 0.0001f)
    }

    @Test
    public fun `resolveBallisticAdjustments should sanitize non finite and negative values`() {
        val adjustments = WeaponLuaScriptRuntime.resolveBallisticAdjustments(
            mapOf(
                "lua_damage_scale" to Float.NaN,
                "script_speed_scale" to Float.POSITIVE_INFINITY,
                "knockback_scale" to -2f,
                "rpm_scale" to 0.5f
            )
        )

        assertEquals(1f, adjustments.damageScale, 0.0001f)
        assertEquals(1f, adjustments.speedScale, 0.0001f)
        assertEquals(0f, adjustments.knockbackScale, 0.0001f)
        assertEquals(1f, adjustments.inaccuracyScale, 0.0001f)
        assertEquals(0.5f, adjustments.rpmScale, 0.0001f)
    }

    @Test
    public fun `resolveHudHint should ignore non finite thresholds`() {
        val hint = WeaponLuaScriptRuntime.resolveHudHint(
            scriptParams = mapOf(
                "lua_hud_low_mag_threshold" to Float.NaN,
                "lua_hud_low_reserve_threshold" to Float.POSITIVE_INFINITY
            ),
            ammoInMagazine = 0,
            ammoReserve = 0
        )

        assertNull(hint)
    }

    @Test
    public fun `resolveHudHint should clamp negative threshold to zero`() {
        val hint = WeaponLuaScriptRuntime.resolveHudHint(
            scriptParams = mapOf("lua_hud_low_reserve_threshold" to -3f),
            ammoInMagazine = 10,
            ammoReserve = 0
        )

        assertEquals("LUA: RES LOW", hint)
    }

    @Test
    public fun `resolveHudHint should prioritize magazine hint before reserve hint`() {
        val hint = WeaponLuaScriptRuntime.resolveHudHint(
            scriptParams = mapOf(
                "lua_hud_low_mag_threshold" to 5f,
                "lua_hud_low_reserve_threshold" to 100f
            ),
            ammoInMagazine = 3,
            ammoReserve = 1
        )

        assertEquals("LUA: MAG LOW", hint)
    }

    @Test
    public fun `resolveHudHint should return null when reserve threshold exists but ammo is above it`() {
        val hint = WeaponLuaScriptRuntime.resolveHudHint(
            scriptParams = mapOf("lua_hud_low_reserve_threshold" to 2f),
            ammoInMagazine = 30,
            ammoReserve = 5
        )

        assertNull(hint)
    }
}