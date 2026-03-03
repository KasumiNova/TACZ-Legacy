package com.tacz.legacy.client.render

import com.tacz.legacy.client.render.frame.FramePhase
import org.apache.logging.log4j.LogManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

public class RenderPipelineRuntimeCompensationTest {

    @Test
    public fun `complete remaining phases should finish active frame`() {
        RenderPipelineRuntime.initialize(LogManager.getLogger("RenderPipelineRuntimeCompensationTest"))

        val context = RenderPipelineRuntime.beginFrame(0.0f, System.nanoTime())
        assertNotNull(context)

        RenderPipelineRuntime.runPhases(
            listOf(
                FramePhase.PREPARE,
                FramePhase.PRE_UPDATE,
                FramePhase.UPDATE
            )
        )

        val completed = RenderPipelineRuntime.completeRemainingPhases("unit_test_compensation")
        assertNotNull(completed)
        assertEquals("unit_test_compensation", completed?.diagnostics?.get("frame.compensated"))
        assertFalse(RenderPipelineRuntime.hasActiveFrame())
    }

    @Test
    public fun `complete remaining phases should return null without active frame`() {
        RenderPipelineRuntime.abortActiveFrame("cleanup_before_null_case")

        val completed = RenderPipelineRuntime.completeRemainingPhases("no_active")
        assertNull(completed)
    }

}
