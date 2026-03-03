package com.tacz.legacy.client.render.item

import com.tacz.legacy.common.infrastructure.mc.weapon.WeaponAnimationClipType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

public class FirstPersonTracerRendererTest {

    @Test
    fun `should spawn tracer on first fire frame`() {
        val shouldSpawn = FirstPersonTracerRenderer.shouldSpawnTracerInCurrentFrame(
            previousClipType = null,
            previousElapsedMillis = Long.MAX_VALUE,
            currentClipType = WeaponAnimationClipType.FIRE,
            currentElapsedMillis = 0L,
            spawnedInCurrentFire = false
        )

        assertTrue(shouldSpawn)
    }

    @Test
    fun `should spawn tracer when first observed fire frame is beyond spawn window`() {
        val shouldSpawn = FirstPersonTracerRenderer.shouldSpawnTracerInCurrentFrame(
            previousClipType = WeaponAnimationClipType.IDLE,
            previousElapsedMillis = Long.MAX_VALUE,
            currentClipType = WeaponAnimationClipType.FIRE,
            currentElapsedMillis = 60L,
            spawnedInCurrentFire = false
        )

        assertTrue(shouldSpawn)
    }

    @Test
    fun `should spawn tracer on fire restart even when restart frame elapsed exceeds window`() {
        val shouldSpawn = FirstPersonTracerRenderer.shouldSpawnTracerInCurrentFrame(
            previousClipType = WeaponAnimationClipType.FIRE,
            previousElapsedMillis = 90L,
            currentClipType = WeaponAnimationClipType.FIRE,
            currentElapsedMillis = 45L,
            spawnedInCurrentFire = true
        )

        assertTrue(shouldSpawn)
    }

    @Test
    fun `should not spawn duplicate tracer in same fire phase`() {
        val shouldSpawn = FirstPersonTracerRenderer.shouldSpawnTracerInCurrentFrame(
            previousClipType = WeaponAnimationClipType.FIRE,
            previousElapsedMillis = 8L,
            currentClipType = WeaponAnimationClipType.FIRE,
            currentElapsedMillis = 10L,
            spawnedInCurrentFire = true
        )

        assertFalse(shouldSpawn)
    }

    @Test
    fun `ads tracer segment should be shorter than hip fire`() {
        val anchor = FirstPersonTracerRenderer.TracerVec3(0f, 0f, -1f)
        val hip = FirstPersonTracerRenderer.resolveTracerSegment(anchor, aimingProgress = 0f)
        val ads = FirstPersonTracerRenderer.resolveTracerSegment(anchor, aimingProgress = 1f)

        val hipLength = segmentLength(hip)
        val adsLength = segmentLength(ads)

        assertTrue(adsLength < hipLength)
    }

    @Test
    fun `tracer alpha should decay to zero`() {
        val startAlpha = FirstPersonTracerRenderer.resolveTracerAlpha(80L)
        val midAlpha = FirstPersonTracerRenderer.resolveTracerAlpha(40L)
        val endAlpha = FirstPersonTracerRenderer.resolveTracerAlpha(0L)

        assertTrue(startAlpha > midAlpha)
        assertTrue(midAlpha > endAlpha)
        assertEquals(0f, endAlpha, 1e-6f)
    }

    private fun segmentLength(segment: FirstPersonTracerRenderer.TracerSegment): Float {
        val dx = segment.end.x - segment.start.x
        val dy = segment.end.y - segment.start.y
        val dz = segment.end.z - segment.start.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }
}
