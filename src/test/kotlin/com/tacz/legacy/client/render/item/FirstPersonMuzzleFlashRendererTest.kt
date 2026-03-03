package com.tacz.legacy.client.render.item

import com.tacz.legacy.common.infrastructure.mc.weapon.WeaponAnimationClipType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

public class FirstPersonMuzzleFlashRendererTest {

    @Test
    public fun `flash alpha should be zero outside fire clip`() {
        val idle = FirstPersonMuzzleFlashRenderer.resolveFlashAlpha(
            clipType = WeaponAnimationClipType.IDLE,
            elapsedMillis = 10L,
            durationMillis = 120L
        )
        val nullClip = FirstPersonMuzzleFlashRenderer.resolveFlashAlpha(
            clipType = null,
            elapsedMillis = 10L,
            durationMillis = 120L
        )

        assertEquals(0f, idle, 1e-6f)
        assertEquals(0f, nullClip, 1e-6f)
    }

    @Test
    public fun `flash alpha should attack then decay during fire clip`() {
        val early = FirstPersonMuzzleFlashRenderer.resolveFlashAlpha(
            clipType = WeaponAnimationClipType.FIRE,
            elapsedMillis = 5L,
            durationMillis = 100L
        )
        val attackPeak = FirstPersonMuzzleFlashRenderer.resolveFlashAlpha(
            clipType = WeaponAnimationClipType.FIRE,
            elapsedMillis = 20L,
            durationMillis = 100L
        )
        val decay = FirstPersonMuzzleFlashRenderer.resolveFlashAlpha(
            clipType = WeaponAnimationClipType.FIRE,
            elapsedMillis = 70L,
            durationMillis = 100L
        )
        val end = FirstPersonMuzzleFlashRenderer.resolveFlashAlpha(
            clipType = WeaponAnimationClipType.FIRE,
            elapsedMillis = 100L,
            durationMillis = 100L
        )

        assertTrue(early > 0f)
        assertTrue(attackPeak >= early)
        assertTrue(decay in 0f..attackPeak)
        assertEquals(0f, end, 1e-6f)
    }

    @Test
    public fun `flash size should shrink while aiming`() {
        val hip = FirstPersonMuzzleFlashRenderer.resolveFlashSize(aimingProgress = 0f)
        val ads = FirstPersonMuzzleFlashRenderer.resolveFlashSize(aimingProgress = 1f)

        assertTrue(hip > ads)
        assertTrue(ads > 0f)
    }
}
