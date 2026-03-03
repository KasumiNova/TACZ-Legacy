package com.tacz.legacy.client.command

import com.tacz.legacy.common.infrastructure.mc.network.WeaponSessionCorrectionReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

public class TaczWeaponDiagClientCommandTest {

    @Test
    public fun `resolve action should map supported aliases`() {
        assertEquals(
            TaczWeaponDiagClientCommand.Action.STATUS,
            TaczWeaponDiagClientCommand.resolveAction(null)
        )
        assertEquals(
            TaczWeaponDiagClientCommand.Action.STATUS,
            TaczWeaponDiagClientCommand.resolveAction("show")
        )
        assertEquals(
            TaczWeaponDiagClientCommand.Action.RESET,
            TaczWeaponDiagClientCommand.resolveAction("clear")
        )
        assertEquals(
            TaczWeaponDiagClientCommand.Action.RESET_ALL,
            TaczWeaponDiagClientCommand.resolveAction("all")
        )
    }

    @Test
    public fun `resolve action should reject unknown arg`() {
        assertNull(TaczWeaponDiagClientCommand.resolveAction("nonsense"))
    }

    @Test
    public fun `reason summary should sort by count and fallback to none`() {
        val summary = TaczWeaponDiagClientCommand.formatReasonSummary(
            mapOf(
                WeaponSessionCorrectionReason.PERIODIC to 1,
                WeaponSessionCorrectionReason.INPUT_REJECTED to 3,
                WeaponSessionCorrectionReason.SHOOT_COOLDOWN to 2
            )
        )
        assertEquals(
            "input_rejected:3,shoot_cooldown:2,periodic:1",
            summary
        )

        assertEquals(
            "none",
            TaczWeaponDiagClientCommand.formatReasonSummary(emptyMap())
        )
    }
}
