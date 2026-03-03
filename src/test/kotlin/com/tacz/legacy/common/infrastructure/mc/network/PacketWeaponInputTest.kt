package com.tacz.legacy.common.infrastructure.mc.network

import com.tacz.legacy.common.application.weapon.WeaponSessionDebugSnapshot
import com.tacz.legacy.common.application.weapon.WeaponBehaviorResult
import com.tacz.legacy.common.domain.weapon.WeaponInput
import com.tacz.legacy.common.domain.weapon.WeaponSnapshot
import com.tacz.legacy.common.domain.weapon.WeaponStepResult
import io.netty.buffer.Unpooled
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

public class PacketWeaponInputTest {

    @Test
    public fun `inferAcceptedInputCorrectionReason should report shoot cooldown for trigger when cooldown active`() {
        val reason = PacketWeaponInput.inferAcceptedInputCorrectionReason(
            input = WeaponInput.TriggerPressed,
            snapshotBeforeDispatch = WeaponSnapshot(
                ammoInMagazine = 10,
                ammoReserve = 30,
                cooldownTicksRemaining = 3
            ),
            dispatchResult = WeaponBehaviorResult(
                step = WeaponStepResult(
                    snapshot = WeaponSnapshot(
                        ammoInMagazine = 10,
                        ammoReserve = 30,
                        cooldownTicksRemaining = 2
                    ),
                    shotFired = false,
                    dryFired = false,
                    reloadStarted = false,
                    reloadCompleted = false
                )
            )
        )

        assertEquals(WeaponSessionCorrectionReason.SHOOT_COOLDOWN, reason)
    }

    @Test
    public fun `inferAcceptedInputCorrectionReason should keep input accepted when trigger fired`() {
        val reason = PacketWeaponInput.inferAcceptedInputCorrectionReason(
            input = WeaponInput.TriggerPressed,
            snapshotBeforeDispatch = WeaponSnapshot(
                ammoInMagazine = 10,
                ammoReserve = 30,
                cooldownTicksRemaining = 0
            ),
            dispatchResult = WeaponBehaviorResult(
                step = WeaponStepResult(
                    snapshot = WeaponSnapshot(
                        ammoInMagazine = 9,
                        ammoReserve = 30,
                        cooldownTicksRemaining = 2,
                        totalShotsFired = 1
                    ),
                    shotFired = true,
                    dryFired = false,
                    reloadStarted = false,
                    reloadCompleted = false
                )
            )
        )

        assertEquals(WeaponSessionCorrectionReason.INPUT_ACCEPTED, reason)
    }

    @Test
    public fun `shouldAcceptInputSequence should allow first increasing and wrapped sequence`() {
        assertTrue(PacketWeaponInput.shouldAcceptInputSequence(lastAcceptedSequenceId = null, inputSequenceId = 1))
        assertTrue(PacketWeaponInput.shouldAcceptInputSequence(lastAcceptedSequenceId = 10, inputSequenceId = 11))
        assertTrue(
            PacketWeaponInput.shouldAcceptInputSequence(
                lastAcceptedSequenceId = Int.MAX_VALUE - 5,
                inputSequenceId = 2
            )
        )
    }

    @Test
    public fun `shouldAcceptInputSequence should reject duplicate and stale sequence`() {
        assertFalse(PacketWeaponInput.shouldAcceptInputSequence(lastAcceptedSequenceId = 10, inputSequenceId = 10))
        assertFalse(PacketWeaponInput.shouldAcceptInputSequence(lastAcceptedSequenceId = 10, inputSequenceId = 9))
    }

    @Test
    public fun `shouldDispatchSequencedInput should track accepted sequence and reject stale replay`() {
        PacketWeaponInput.clearTrackedInputSequence("player:test")

        assertTrue(PacketWeaponInput.shouldDispatchSequencedInput("player:test", 1))
        assertTrue(PacketWeaponInput.shouldDispatchSequencedInput("player:test", 2))
        assertFalse(PacketWeaponInput.shouldDispatchSequencedInput("player:test", 2))
        assertFalse(PacketWeaponInput.shouldDispatchSequencedInput("player:test", 1))

        assertTrue(PacketWeaponInput.clearTrackedInputSequence("player:test"))
        assertFalse(PacketWeaponInput.clearTrackedInputSequence("player:test"))
    }

    @Test
    public fun `shouldDispatchSequencedInput should accept sequence-less compatibility packets`() {
        PacketWeaponInput.clearTrackedInputState("player:compat")

        assertTrue(PacketWeaponInput.shouldDispatchSequencedInput("player:compat", -1))
        assertTrue(PacketWeaponInput.shouldDispatchSequencedInput("player:compat", -1))
    }

    @Test
    public fun `shouldAcceptInputTiming should pass in allowed drift window and fail when out of range`() {
        assertTrue(
            PacketWeaponInput.shouldAcceptInputTiming(
                serverBaseTimestampEpochMillis = 10_000L,
                inputRelativeTimestampMillis = 500L,
                nowEpochMillis = 10_520L
            )
        )

        assertFalse(
            PacketWeaponInput.shouldAcceptInputTiming(
                serverBaseTimestampEpochMillis = 10_000L,
                inputRelativeTimestampMillis = 500L,
                nowEpochMillis = 9_000L
            )
        )

        assertFalse(
            PacketWeaponInput.shouldAcceptInputTiming(
                serverBaseTimestampEpochMillis = 10_000L,
                inputRelativeTimestampMillis = 500L,
                nowEpochMillis = 11_200L
            )
        )
    }

    @Test
    public fun `shouldAcceptTimedInput should use tracked server base timestamp`() {
        PacketWeaponInput.clearTrackedInputState("player:time")
        PacketWeaponInput.recordServerBaseTimestamp("player:time", epochMillis = 10_000L)

        assertTrue(
            PacketWeaponInput.shouldAcceptTimedInput(
                sessionId = "player:time",
                inputRelativeTimestampMillis = 600L,
                nowEpochMillis = 10_700L
            )
        )
        assertFalse(
            PacketWeaponInput.shouldAcceptTimedInput(
                sessionId = "player:time",
                inputRelativeTimestampMillis = 200L,
                nowEpochMillis = 11_500L
            )
        )
    }

    @Test
    public fun `shouldAcceptTimedInput should infer first server base from relative timestamp`() {
        PacketWeaponInput.clearTrackedInputState("player:infer")

        assertTrue(
            PacketWeaponInput.shouldAcceptTimedInput(
                sessionId = "player:infer",
                inputRelativeTimestampMillis = 5_000L,
                nowEpochMillis = 20_000L
            )
        )

        assertTrue(
            PacketWeaponInput.shouldAcceptTimedInput(
                sessionId = "player:infer",
                inputRelativeTimestampMillis = 5_050L,
                nowEpochMillis = 20_050L
            )
        )

        assertFalse(
            PacketWeaponInput.shouldAcceptTimedInput(
                sessionId = "player:infer",
                inputRelativeTimestampMillis = 4_000L,
                nowEpochMillis = 20_050L
            )
        )
    }

    @Test
    public fun `fromInput and codec should round trip supported weapon inputs`() {
        val inputs = listOf(
            WeaponInput.TriggerPressed,
            WeaponInput.TriggerReleased,
            WeaponInput.ReloadPressed,
            WeaponInput.InspectPressed
        )

        inputs.forEachIndexed { index, input ->
            val expectedSequenceId = index + 1
            val expectedRelativeTimestamp = 1000L + index
            val packet = PacketWeaponInput.fromInput(
                input = input,
                inputSequenceId = expectedSequenceId,
                inputRelativeTimestampMillis = expectedRelativeTimestamp
            )
            val buf = Unpooled.buffer()

            requireNotNull(packet).toBytes(buf)

            val decodedPacket = PacketWeaponInput()
            decodedPacket.fromBytes(buf)
            val decodedInput = decodedPacket.toWeaponInputOrNull()

            assertEquals(input::class, decodedInput?.let { it::class })
            assertEquals(expectedSequenceId, decodedPacket.inputSequenceId)
            val expectedEncodedTimestamp = if (input == WeaponInput.TriggerPressed) {
                expectedRelativeTimestamp
            } else {
                -1L
            }
            assertEquals(expectedEncodedTimestamp, decodedPacket.inputRelativeTimestampMillis)
        }
    }

    @Test
    public fun `shouldCarryRelativeTimestamp should only enable trigger pressed`() {
        assertTrue(PacketWeaponInput.shouldCarryRelativeTimestamp(WeaponInput.TriggerPressed))
        assertFalse(PacketWeaponInput.shouldCarryRelativeTimestamp(WeaponInput.TriggerReleased))
        assertFalse(PacketWeaponInput.shouldCarryRelativeTimestamp(WeaponInput.ReloadPressed))
        assertFalse(PacketWeaponInput.shouldCarryRelativeTimestamp(WeaponInput.InspectPressed))
    }

    @Test
    public fun `fromInput should ignore non-network tick input`() {
        assertNull(PacketWeaponInput.fromInput(WeaponInput.Tick))
    }

    @Test
    public fun `decode should return null for unknown code`() {
        val packet = PacketWeaponInput(127)
        assertNull(packet.toWeaponInputOrNull())
    }

    @Test
    public fun `emitAuthoritativeCorrection should send sync when snapshot exists`() {
        var syncCalled = 0
        var clearCalled = 0

        val sent = PacketWeaponInput.emitAuthoritativeCorrection(
            sessionId = " player:abc ",
            debugSnapshot = WeaponSessionDebugSnapshot(
                sourceId = "src",
                gunId = "ak47",
                snapshot = WeaponSnapshot(
                    ammoInMagazine = 30,
                    ammoReserve = 90
                )
            ),
            ackSequenceId = 17,
            correctionReason = WeaponSessionCorrectionReason.INPUT_ACCEPTED,
            sendSync = { sessionId, _ ->
                syncCalled += 1
                assertEquals("player:abc", sessionId)
                true
            },
            sendClear = { _, _, _ ->
                clearCalled += 1
                true
            }
        )

        assertTrue(sent)
        assertEquals(1, syncCalled)
        assertEquals(0, clearCalled)
    }

    @Test
    public fun `emitAuthoritativeCorrection should send clear when snapshot missing`() {
        var syncCalled = 0
        var clearCalled = 0

        val sent = PacketWeaponInput.emitAuthoritativeCorrection(
            sessionId = "player:abc",
            debugSnapshot = null,
            ackSequenceId = 42,
            correctionReason = WeaponSessionCorrectionReason.NO_SESSION,
            sendSync = { _, _ ->
                syncCalled += 1
                true
            },
            sendClear = { sessionId, ack, reason ->
                clearCalled += 1
                assertEquals("player:abc", sessionId)
                assertEquals(42, ack)
                assertEquals(WeaponSessionCorrectionReason.NO_SESSION, reason)
                true
            }
        )

        assertTrue(sent)
        assertEquals(0, syncCalled)
        assertEquals(1, clearCalled)
    }

    @Test
    public fun `emitAuthoritativeCorrection should reject blank session id`() {
        var syncCalled = 0
        var clearCalled = 0

        val sent = PacketWeaponInput.emitAuthoritativeCorrection(
            sessionId = "   ",
            debugSnapshot = null,
            ackSequenceId = 9,
            correctionReason = WeaponSessionCorrectionReason.NO_SESSION,
            sendSync = { _, _ ->
                syncCalled += 1
                true
            },
            sendClear = { _, _, _ ->
                clearCalled += 1
                true
            }
        )

        assertFalse(sent)
        assertEquals(0, syncCalled)
        assertEquals(0, clearCalled)
    }

}
