package com.tacz.legacy.client.render.item

import com.tacz.legacy.common.infrastructure.mc.weapon.WeaponAnimationClipType
import com.tacz.legacy.common.infrastructure.mc.weapon.WeaponAnimationRuntimeEvent
import com.tacz.legacy.common.infrastructure.mc.weapon.WeaponAnimationRuntimeEventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

public class ThirdPersonShellEjectEventHandlerTest {

    private val handler: ThirdPersonShellEjectEventHandler = ThirdPersonShellEjectEventHandler()

    @Test
    public fun `initial cursor should prime to latest shell event sequence`() {
        val initialSequence = handler.resolveInitialLastConsumedSequence(
            transientEvents = listOf(
                WeaponAnimationRuntimeEvent(
                    sequence = 2L,
                    type = WeaponAnimationRuntimeEventType.SHELL_EJECT,
                    clip = WeaponAnimationClipType.FIRE,
                    emittedAtMillis = 1_000L
                ),
                WeaponAnimationRuntimeEvent(
                    sequence = 3L,
                    type = WeaponAnimationRuntimeEventType.SHELL_EJECT,
                    clip = WeaponAnimationClipType.BOLT,
                    emittedAtMillis = 1_050L
                )
            )
        )

        assertEquals(3L, initialSequence)
    }

    @Test
    public fun `initial cursor should ignore non shell events`() {
        val initialSequence = handler.resolveInitialLastConsumedSequence(
            transientEvents = listOf(
                WeaponAnimationRuntimeEvent(
                    sequence = 9L,
                    type = WeaponAnimationRuntimeEventType.SHELL_EJECT,
                    clip = WeaponAnimationClipType.RELOAD,
                    emittedAtMillis = 2_000L
                ),
                WeaponAnimationRuntimeEvent(
                    sequence = 12L,
                    type = WeaponAnimationRuntimeEventType.SHELL_EJECT,
                    clip = WeaponAnimationClipType.FIRE,
                    emittedAtMillis = 2_020L
                )
            )
        )

        assertEquals(12L, initialSequence)
    }

    @Test
    public fun `third person shell anchor should follow player yaw basis`() {
        val yaw0 = handler.resolveThirdPersonShellAnchor(
            originX = 0.0,
            originY = 64.0,
            originZ = 0.0,
            eyeHeight = 1.62f,
            yawDegrees = 0f
        )
        assertTrue(yaw0.x > 0.0)
        assertTrue(yaw0.z > 0.0)

        val yaw90 = handler.resolveThirdPersonShellAnchor(
            originX = 0.0,
            originY = 64.0,
            originZ = 0.0,
            eyeHeight = 1.62f,
            yawDegrees = 90f
        )
        assertTrue(yaw90.x < 0.0)
        assertTrue(yaw90.z > 0.0)
        assertEquals(64.0 + 1.62 - 0.30, yaw90.y, 1e-6)
    }
}
