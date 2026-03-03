package com.tacz.legacy.client.render.execution

import com.tacz.legacy.client.render.frame.FramePhase
import org.junit.Assert.assertEquals
import org.junit.Test

public class FrameEventPhasePlanTest {

    @Test
    public fun `default plan should map all known triggers`() {
        val plan = FrameEventPhasePlan.defaultPlan()

        assertEquals(
            listOf(FramePhase.PREPARE, FramePhase.PRE_UPDATE, FramePhase.UPDATE),
            plan.phasesFor(FrameEventTrigger.RENDER_TICK_START)
        )
        assertEquals(
            listOf(FramePhase.RENDER_OPAQUE, FramePhase.RENDER_TRANSLUCENT, FramePhase.POST_UPDATE),
            plan.phasesFor(FrameEventTrigger.RENDER_WORLD_LAST)
        )
        assertEquals(
            listOf(FramePhase.RENDER_OVERLAY),
            plan.phasesFor(FrameEventTrigger.OVERLAY_PRE_ALL)
        )
    }

}
