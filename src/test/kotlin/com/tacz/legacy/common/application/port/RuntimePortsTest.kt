package com.tacz.legacy.common.application.port

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

public class RuntimePortsTest {

    @Test
    public fun `seeded random port should be deterministic with same seed`() {
        val a: RandomPort = SeededRandomPort(seed = 42L)
        val b: RandomPort = SeededRandomPort(seed = 42L)

        val sequenceA = List(16) { a.nextInt(10_000) }
        val sequenceB = List(16) { b.nextInt(10_000) }

        assertEquals(sequenceA, sequenceB)
    }

    @Test
    public fun `seeded random port should validate bound`() {
        val random: RandomPort = SeededRandomPort(seed = 1L)

        val exception = runCatching {
            random.nextInt(0)
        }.exceptionOrNull()

        assertTrue(exception is IllegalArgumentException)
    }

    @Test
    public fun `vec3 zero should expose origin coordinates`() {
        assertEquals(0.0, Vec3d.ZERO.x, 0.0)
        assertEquals(0.0, Vec3d.ZERO.y, 0.0)
        assertEquals(0.0, Vec3d.ZERO.z, 0.0)
    }

}
