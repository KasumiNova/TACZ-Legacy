package com.tacz.legacy.client.event

import com.tacz.legacy.util.math.MathUtil
import org.junit.Assert.assertEquals
import org.junit.Test

class FirstPersonFovHooksTest {
    @Test
    fun `resolve item model fov target prefers attachment views fov with zoom wrap`() {
        val resolved = FirstPersonFovHooks.resolveItemModelFovTarget(floatArrayOf(38.0f, 26.0f), 3, 52.0f, 70.0f)

        assertEquals(26.0f, resolved, 1.0e-6f)
    }

    @Test
    fun `resolve item model fov target falls back to gun display fov`() {
        val resolved = FirstPersonFovHooks.resolveItemModelFovTarget(null, 7, 52.0f, 70.0f)

        assertEquals(52.0f, resolved, 1.0e-6f)
    }

    @Test
    fun `resolve item model fov target falls back to original fov when no overrides exist`() {
        val resolved = FirstPersonFovHooks.resolveItemModelFovTarget(null, 0, null, 70.0f)

        assertEquals(70.0f, resolved, 1.0e-6f)
    }

    @Test
    fun `compute magnified world fov matches upstream math util curve`() {
        val expected = MathUtil.magnificationToFov(2.5, 70.0)
        val resolved = FirstPersonFovHooks.computeMagnifiedWorldFov(70.0f, 4.0f, 0.5f)

        assertEquals(expected.toFloat(), resolved, 1.0e-5f)
    }

    @Test
    fun `blend item model fov lerps by aiming progress`() {
        val blended = FirstPersonFovHooks.blendItemModelFov(70.0f, 40.0f, 0.25f)

        assertEquals(62.5f, blended, 1.0e-6f)
    }
}
