package com.tacz.legacy.client.render.draw

import com.tacz.legacy.client.render.core.RenderContext
import com.tacz.legacy.client.render.core.RenderPipelineConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

public class DrawCommandBufferTest {

    @Test
    public fun `buffer should execute submitted commands and clear pending queue`() {
        val buffer = DrawCommandBuffer()
        val context = RenderContext(
            frameId = 1,
            partialTicks = 0.0f,
            finishTimeNano = 0L,
            pipelineConfig = RenderPipelineConfig()
        )

        var hitCount = 0
        buffer.submit(LambdaDrawCommand("cmd-1") { hitCount += 1 })
        buffer.submit(LambdaDrawCommand("cmd-2") { hitCount += 1 })

        assertEquals(2, buffer.size())
        val executed = buffer.executeAll(context)

        assertEquals(2, executed)
        assertEquals(2, hitCount)
        assertEquals(0, buffer.size())
    }

    @Test
    public fun `buffer should support submitAll and snapshot ids`() {
        val buffer = DrawCommandBuffer()

        buffer.submitAll(
            listOf(
                LambdaDrawCommand("cmd-a") { _ -> },
                LambdaDrawCommand("cmd-b") { _ -> }
            )
        )

        assertEquals(listOf("cmd-a", "cmd-b"), buffer.snapshotIds())
        assertEquals(2, buffer.size())
    }

    @Test
    public fun `execution report should keep running on command failure when fallback enabled`() {
        val buffer = DrawCommandBuffer()
        val context = RenderContext(
            frameId = 2,
            partialTicks = 0.0f,
            finishTimeNano = 0L,
            pipelineConfig = RenderPipelineConfig(enableVanillaFallback = true)
        )

        var hitCount = 0
        buffer.submit(LambdaDrawCommand("ok-1") { _ -> hitCount += 1 })
        buffer.submit(LambdaDrawCommand("boom") { _ -> error("boom") })
        buffer.submit(LambdaDrawCommand("ok-2") { _ -> hitCount += 1 })

        val report = buffer.executeAllWithReport(context)

        assertEquals(3, report.submitted)
        assertEquals(2, report.executed)
        assertEquals(1, report.failed)
        assertEquals(listOf("boom"), report.failedIds)
        assertEquals(3, report.consumed)
        assertEquals(2, hitCount)
        assertEquals(0, buffer.size())
    }

    @Test
    public fun `execution report should fail fast in strict mode`() {
        val buffer = DrawCommandBuffer()
        val context = RenderContext(
            frameId = 3,
            partialTicks = 0.0f,
            finishTimeNano = 0L,
            pipelineConfig = RenderPipelineConfig(enableVanillaFallback = false)
        )

        buffer.submit(LambdaDrawCommand("boom") { _ -> error("strict-fail") })

        val exception = runCatching {
            buffer.executeAllWithReport(context)
        }.exceptionOrNull()

        assertTrue(exception is IllegalStateException)
        assertEquals(0, buffer.size())
    }

}
