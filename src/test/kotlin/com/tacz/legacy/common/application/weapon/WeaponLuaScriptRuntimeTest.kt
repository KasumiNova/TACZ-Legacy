package com.tacz.legacy.common.application.weapon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

public class WeaponLuaScriptRuntimeTest {

    @Test
    public fun `resolveBallisticAdjustments should read alias scales`() {
        val adjustments = WeaponLuaScriptRuntime.resolveBallisticAdjustments(
            mapOf(
                "lua_damage_scale" to 1.2f,
                "script_speed_scale" to 0.9f,
                "knockback_scale" to 1.5f,
                "lua_inaccuracy_scale" to 0.75f,
                "rpm_scale" to 1.1f
            )
        )

        assertEquals(1.2f, adjustments.damageScale, 0.0001f)
        assertEquals(0.9f, adjustments.speedScale, 0.0001f)
        assertEquals(1.5f, adjustments.knockbackScale, 0.0001f)
        assertEquals(0.75f, adjustments.inaccuracyScale, 0.0001f)
        assertEquals(1.1f, adjustments.rpmScale, 0.0001f)
    }

    @Test
    public fun `resolveHudHint should emit low ammo hints by thresholds`() {
        val hintMag = WeaponLuaScriptRuntime.resolveHudHint(
            scriptParams = mapOf("lua_hud_low_mag_threshold" to 3f),
            ammoInMagazine = 2,
            ammoReserve = 50
        )
        assertEquals("LUA: MAG LOW", hintMag)

        val hintReserve = WeaponLuaScriptRuntime.resolveHudHint(
            scriptParams = mapOf("hud_low_reserve_threshold" to 20f),
            ammoInMagazine = 30,
            ammoReserve = 5
        )
        assertEquals("LUA: RES LOW", hintReserve)

        val noHint = WeaponLuaScriptRuntime.resolveHudHint(
            scriptParams = mapOf("lua_hud_low_mag_threshold" to 1f),
            ammoInMagazine = 10,
            ammoReserve = 100
        )
        assertNull(noHint)
    }

    @Test
    public fun `combineBallisticAdjustments should multiply scales`() {
        val combined = WeaponLuaScriptRuntime.combineBallisticAdjustments(
            base = WeaponLuaBallisticAdjustments(
                damageScale = 1.1f,
                speedScale = 0.9f,
                knockbackScale = 1.2f,
                inaccuracyScale = 0.8f,
                rpmScale = 1.05f
            ),
            overlay = WeaponLuaBallisticAdjustments(
                damageScale = 1.2f,
                speedScale = 1.1f,
                knockbackScale = 0.5f,
                inaccuracyScale = 1.25f,
                rpmScale = 0.95f
            )
        )

        assertEquals(1.32f, combined.damageScale, 0.0001f)
        assertEquals(0.99f, combined.speedScale, 0.0001f)
        assertEquals(0.6f, combined.knockbackScale, 0.0001f)
        assertEquals(1.0f, combined.inaccuracyScale, 0.0001f)
        assertEquals(0.9975f, combined.rpmScale, 0.0001f)
    }
}
