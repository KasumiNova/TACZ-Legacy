package com.tacz.legacy.client.render.item

import org.junit.Assert.assertEquals
import org.junit.Test

public class FirstPersonFovCompensationTest {

    @Test
    public fun `apply scale should only affect depth axis`() {
        val compensated = FirstPersonFovCompensation.applyScale(
            x = 0.5f,
            y = -1.0f,
            z = 2.0f,
            scale = 2f
        )

        assertEquals(0.5f, compensated.x, 1e-6f)
        assertEquals(-1.0f, compensated.y, 1e-6f)
        assertEquals(4.0f, compensated.z, 1e-6f)
    }

    @Test
    public fun `sanitize scale should clamp and fallback for invalid values`() {
        assertEquals(1f, FirstPersonFovCompensation.sanitizeScale(Float.NaN), 1e-6f)
        assertEquals(1f, FirstPersonFovCompensation.sanitizeScale(Float.POSITIVE_INFINITY), 1e-6f)
        assertEquals(0.01f, FirstPersonFovCompensation.sanitizeScale(0.0001f), 1e-6f)
        assertEquals(100f, FirstPersonFovCompensation.sanitizeScale(200f), 1e-6f)
    }
}
