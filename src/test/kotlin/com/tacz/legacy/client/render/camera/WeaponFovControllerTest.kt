package com.tacz.legacy.client.render.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

public class WeaponFovControllerTest {

    @Test
    public fun `magnification to fov should keep base when magnification is one`() {
        val resolved = WeaponFovController.magnificationToFov(
            baseFovDegrees = 90f,
            magnification = 1f
        )

        assertEquals(90f, resolved, 1e-5f)
    }

    @Test
    public fun `magnification to fov should reduce fov when magnification increases`() {
        val x2 = WeaponFovController.magnificationToFov(
            baseFovDegrees = 90f,
            magnification = 2f
        )
        val x4 = WeaponFovController.magnificationToFov(
            baseFovDegrees = 90f,
            magnification = 4f
        )

        assertTrue(x2 in 0f..90f)
        assertTrue(x4 in 0f..x2)
    }

    @Test
    public fun `model fov target should linearly blend by aiming progress`() {
        val hip = WeaponFovController.resolveModelFovTarget(
            currentFovDegrees = 90f,
            modelFovDegrees = 45f,
            aimingProgress = 0f
        )
        val ads = WeaponFovController.resolveModelFovTarget(
            currentFovDegrees = 90f,
            modelFovDegrees = 45f,
            aimingProgress = 1f
        )
        val half = WeaponFovController.resolveModelFovTarget(
            currentFovDegrees = 90f,
            modelFovDegrees = 45f,
            aimingProgress = 0.5f
        )

        assertEquals(90f, hip, 1e-5f)
        assertEquals(45f, ads, 1e-5f)
        assertEquals(67.5f, half, 1e-5f)
    }

    @Test
    public fun `model fov target should clamp invalid inputs`() {
        val belowRange = WeaponFovController.resolveModelFovTarget(
            currentFovDegrees = -30f,
            modelFovDegrees = 300f,
            aimingProgress = 2f
        )
        val noProgress = WeaponFovController.resolveModelFovTarget(
            currentFovDegrees = -30f,
            modelFovDegrees = 300f,
            aimingProgress = -1f
        )

        assertEquals(179f, belowRange, 1e-5f)
        assertEquals(1f, noProgress, 1e-5f)
    }

    @Test
    public fun `delta seconds helper should use default on invalid time order`() {
        val fallback = WeaponFovController.resolveDeltaSecondsFromNanos(
            nowNanos = 100L,
            lastUpdateNanos = 200L
        )
        val firstTick = WeaponFovController.resolveDeltaSecondsFromNanos(
            nowNanos = 100L,
            lastUpdateNanos = 0L
        )

        assertEquals(0.05f, fallback, 1e-6f)
        assertEquals(0.05f, firstTick, 1e-6f)
    }

    @Test
    public fun `delta seconds helper should clamp range`() {
        val tooSmall = WeaponFovController.resolveDeltaSecondsFromNanos(
            nowNanos = 1_000L,
            lastUpdateNanos = 999L
        )
        val tooLarge = WeaponFovController.resolveDeltaSecondsFromNanos(
            nowNanos = 1_000_000_000_000L,
            lastUpdateNanos = 1L
        )

        assertEquals(0.001f, tooSmall, 1e-6f)
        assertEquals(0.25f, tooLarge, 1e-6f)
    }

    @Test
    public fun `depth compensation should be one when item and world fov equal`() {
        val scale = WeaponFovController.resolveDepthCompensationScale(
            itemFovDegrees = 70f,
            worldFovDegrees = 70f
        )

        assertEquals(1f, scale, 1e-5f)
    }

    @Test
    public fun `depth compensation should shrink when item fov is narrower`() {
        val scale = WeaponFovController.resolveDepthCompensationScale(
            itemFovDegrees = 45f,
            worldFovDegrees = 90f
        )

        assertTrue(scale in 0.01f..1f)
    }

    @Test
    public fun `depth compensation should grow when item fov is wider`() {
        val scale = WeaponFovController.resolveDepthCompensationScale(
            itemFovDegrees = 100f,
            worldFovDegrees = 60f
        )

        assertTrue(scale in 1f..100f)
    }
}
