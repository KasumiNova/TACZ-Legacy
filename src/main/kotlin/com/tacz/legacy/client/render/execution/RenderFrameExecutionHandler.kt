package com.tacz.legacy.client.render.execution

import com.tacz.legacy.client.render.application.RenderFrameOrchestrator
import com.tacz.legacy.client.render.infrastructure.mc.RenderPipelineRuntimePortAdapter
import com.tacz.legacy.client.sound.TaczSoundEngine
import net.minecraft.client.Minecraft
import net.minecraftforge.client.event.RenderGameOverlayEvent
import net.minecraftforge.client.event.RenderWorldLastEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent

public class RenderFrameExecutionHandler(
    private val orchestrator: RenderFrameOrchestrator =
        RenderFrameOrchestrator(RenderPipelineRuntimePortAdapter)
) {

    @SubscribeEvent
    public fun onRenderTick(event: TickEvent.RenderTickEvent) {
        when (event.phase) {
            TickEvent.Phase.START -> onRenderTickStart(event)
            TickEvent.Phase.END -> onRenderTickEnd()
        }
    }

    @SubscribeEvent
    public fun onRenderWorldLast(event: RenderWorldLastEvent) {
        val worldAvailable = Minecraft.getMinecraft().world != null
        orchestrator.onRenderWorldLast(worldAvailable)
    }

    @SubscribeEvent
    public fun onRenderOverlayPre(event: RenderGameOverlayEvent.Pre) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL) {
            return
        }

        orchestrator.onOverlayPreAll()
    }

    private fun onRenderTickStart(event: TickEvent.RenderTickEvent) {
        val worldAvailable = Minecraft.getMinecraft().world != null
        orchestrator.onRenderTickStart(event.renderTickTime, System.nanoTime(), worldAvailable)
    }

    private fun onRenderTickEnd() {
        orchestrator.onRenderTickEnd()
        TaczSoundEngine.tick()
    }

}
