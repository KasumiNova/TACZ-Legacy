package com.tacz.legacy.client.render.core

import com.tacz.legacy.client.render.feature.RenderFeature
import com.tacz.legacy.client.render.feature.RenderFeatureRegistry
import com.tacz.legacy.client.render.frame.FramePhase
import com.tacz.legacy.client.render.frame.FramePhaseFSM
import com.tacz.legacy.client.render.frame.PhaseTiming
import com.tacz.legacy.client.render.pass.RenderPass

public class RenderPipelineCoordinator(
    private val phases: List<FramePhase> = RenderPipelineBootstrap.createDefaultPhaseOrder()
) {

    private val fsm: FramePhaseFSM = FramePhaseFSM(phases)
    private val passMap: MutableMap<FramePhase, RenderPass> = phases
        .associateWith { phase -> RenderPass(phase.name) }
        .toMutableMap()

    private val featureRegistry: RenderFeatureRegistry = RenderFeatureRegistry(passMap)
    private val features: MutableMap<String, RenderFeature> = linkedMapOf()

    private val phaseCallbacks: MutableMap<FramePhase, MutableMap<PhaseTiming, MutableList<(RenderContext) -> Unit>>> =
        mutableMapOf()

    public fun registerFeature(feature: RenderFeature) {
        if (features.putIfAbsent(feature.id, feature) == null) {
            feature.install(featureRegistry)
        }
    }

    public fun registerCallback(
        phase: FramePhase,
        timing: PhaseTiming,
        callback: (RenderContext) -> Unit
    ) {
        val timingMap = phaseCallbacks.computeIfAbsent(phase) { mutableMapOf() }
        val callbacks = timingMap.computeIfAbsent(timing) { mutableListOf() }
        callbacks += callback
    }

    public fun expectedPhase(): FramePhase = fsm.expectedPhase()

    public fun resetPhaseSequence() {
        fsm.reset()
    }

    public fun runFrame(context: RenderContext) {
        if (!context.pipelineConfig.enableNewPipeline) {
            if (context.pipelineConfig.enableDiagnostics) {
                context.diagnostics["pipeline.skipped"] = true
            }
            return
        }

        val frameStartNs = System.nanoTime()

        resetPhaseSequence()

        phases.forEach { phase ->
            val phaseStartNs = System.nanoTime()
            runPhase(phase, context)
            if (context.pipelineConfig.enableDiagnostics) {
                context.diagnostics["phase.${phase.name}.ns"] = System.nanoTime() - phaseStartNs
            }
        }

        if (context.pipelineConfig.enableDiagnostics) {
            context.diagnostics["frame.total.ns"] = System.nanoTime() - frameStartNs
        }

        resetPhaseSequence()
    }

    public fun runPhase(phase: FramePhase, context: RenderContext) {
        check(fsm.isExpecting(phase)) {
            "Render phase out of order. Expected=${fsm.expectedPhase()} Actual=$phase"
        }

        invokeCallbacks(phase, PhaseTiming.BEFORE, context)
        passMap.getValue(phase).render(context)
        invokeCallbacks(phase, PhaseTiming.AFTER, context)

        fsm.advance(phase)
    }

    public fun listRegisteredFeatures(): List<String> = features.keys.toList()

    public fun listSubpasses(phase: FramePhase): List<String> = passMap.getValue(phase).listSubpassIds()

    private fun invokeCallbacks(phase: FramePhase, timing: PhaseTiming, context: RenderContext) {
        phaseCallbacks[phase]?.get(timing)?.forEach { callback -> callback(context) }
    }

}
