package com.tacz.legacy.client.render.frame

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

public class FramePhaseFSMTest {

    @Test
    public fun `fsm should advance in default order and wrap`() {
        val fsm = FramePhaseFSM()

        assertEquals(FramePhase.PREPARE, fsm.expectedPhase())

        FramePhase.defaultOrder().forEach { phase ->
            assertEquals(phase, fsm.expectedPhase())
            fsm.advance(phase)
        }

        assertEquals(FramePhase.PREPARE, fsm.expectedPhase())
        assertEquals(0, fsm.index())
    }

    @Test
    public fun `fsm should reject out of order phase`() {
        val fsm = FramePhaseFSM()

        val ex = runCatching {
            fsm.advance(FramePhase.UPDATE)
        }.exceptionOrNull()

        assertTrue(ex is IllegalStateException)
    }

}
