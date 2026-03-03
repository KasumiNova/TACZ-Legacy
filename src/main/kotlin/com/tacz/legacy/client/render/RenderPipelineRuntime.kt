package com.tacz.legacy.client.render

import com.tacz.legacy.client.render.core.RenderPipelineBootstrap
import com.tacz.legacy.client.render.core.RenderContext
import com.tacz.legacy.client.render.core.RenderPipelineConfig
import com.tacz.legacy.client.render.core.RenderPipelineCoordinator
import com.tacz.legacy.client.render.execution.FrameEventPhasePlan
import com.tacz.legacy.client.render.feature.builtin.DebugOverlayFeature
import com.tacz.legacy.client.render.feature.builtin.DrawCommandDiagnosticsFeature
import com.tacz.legacy.client.render.feature.builtin.NoopFeature
import com.tacz.legacy.client.render.feature.builtin.SpecialBlockModelProbeFeature
import com.tacz.legacy.client.render.feature.builtin.WeaponModelProbeFeature
import com.tacz.legacy.client.render.frame.FramePhase
import org.apache.logging.log4j.Logger

public object RenderPipelineRuntime {

    private val coordinator: RenderPipelineCoordinator = RenderPipelineCoordinator()
    private var config: RenderPipelineConfig = RenderPipelineBootstrap.createDefaultConfig()
    private var initialized: Boolean = false
    private var frameCounter: Long = 0L
    private var latestContext: RenderContext? = null
    private var activeContext: RenderContext? = null
    private var activeFrameStartNs: Long = 0L
    private val phasePlan: FrameEventPhasePlan = FrameEventPhasePlan.defaultPlan()

    public fun initialize(logger: Logger): RenderPipelineConfig {
        if (!initialized) {
            logger.info("[TACZ-Legacy] Initializing R1 event-driven render pipeline skeleton.")
            logger.info("[TACZ-Legacy] Frame phases: {}", RenderPipelineBootstrap.createDefaultPhaseOrder())
            registerBuiltins(logger)
            initialized = true
        }
        return config
    }

    public fun coordinator(): RenderPipelineCoordinator = coordinator

    public fun currentConfig(): RenderPipelineConfig = config

    public fun updateConfig(transform: (RenderPipelineConfig) -> RenderPipelineConfig): RenderPipelineConfig {
        config = transform(config)
        return config
    }

    public fun latestContext(): RenderContext? = latestContext

    public fun phasePlan(): FrameEventPhasePlan = phasePlan

    public fun expectedPhase(): FramePhase? = if (activeContext != null) coordinator.expectedPhase() else null

    public fun hasActiveFrame(): Boolean = activeContext != null

    public fun beginFrame(partialTicks: Float, finishTimeNano: Long): RenderContext? {
        if (!initialized) {
            return null
        }

        if (activeContext != null) {
            abortActiveFrame("stale_frame_detected")
        }

        coordinator.resetPhaseSequence()
        frameCounter += 1
        activeFrameStartNs = System.nanoTime()
        activeContext = RenderContext(
            frameId = frameCounter,
            partialTicks = partialTicks,
            finishTimeNano = finishTimeNano,
            pipelineConfig = config
        )
        activeContext?.diagnostics?.put("phase.plan", "default")
        return activeContext
    }

    public fun runPhases(phases: List<FramePhase>): RenderContext? {
        phases.forEach { phase ->
            if (runPhase(phase) == null) {
                return null
            }
        }
        return activeContext
    }

    public fun runPhase(phase: FramePhase): RenderContext? {
        val context = activeContext ?: return null

        return try {
            val phaseStartNs = System.nanoTime()
            coordinator.runPhase(phase, context)
            if (context.pipelineConfig.enableDiagnostics) {
                context.diagnostics["phase.${phase.name}.ns"] = System.nanoTime() - phaseStartNs
            }
            context
        } catch (throwable: Throwable) {
            context.diagnostics["frame.error"] = throwable.message ?: throwable::class.java.simpleName
            latestContext = context
            activeContext = null
            coordinator.resetPhaseSequence()
            null
        }
    }

    public fun completeFrame(): RenderContext? {
        val context = activeContext ?: return null

        if (context.pipelineConfig.enableDiagnostics) {
            context.diagnostics["frame.total.ns"] = System.nanoTime() - activeFrameStartNs
        }

        latestContext = context
        activeContext = null
        coordinator.resetPhaseSequence()
        return context
    }

    public fun abortActiveFrame(reason: String) {
        val context = activeContext ?: return
        context.diagnostics["frame.aborted"] = reason
        if (context.pipelineConfig.enableDiagnostics) {
            context.diagnostics["frame.total.ns"] = System.nanoTime() - activeFrameStartNs
        }
        latestContext = context
        activeContext = null
        coordinator.resetPhaseSequence()
    }

    public fun completeRemainingPhases(reason: String): RenderContext? {
        val context = activeContext ?: return null
        val expected = coordinator.expectedPhase()
        val order = RenderPipelineBootstrap.createDefaultPhaseOrder()
        val startIndex = order.indexOf(expected)

        if (startIndex < 0) {
            abortActiveFrame("invalid_expected_phase")
            return latestContext
        }

        context.diagnostics["frame.compensated"] = reason
        if (runPhases(order.subList(startIndex, order.size)) == null) {
            return latestContext
        }

        return completeFrame()
    }

    public fun runFrame(partialTicks: Float, finishTimeNano: Long): RenderContext? {
        val context = beginFrame(partialTicks, finishTimeNano) ?: return null
        val phases = RenderPipelineBootstrap.createDefaultPhaseOrder()

        phases.forEach { phase ->
            if (runPhase(phase) == null) {
                return latestContext
            }
        }

        return completeFrame() ?: context
    }

    private fun registerBuiltins(logger: Logger) {
        coordinator.registerFeature(NoopFeature)
        coordinator.registerFeature(WeaponModelProbeFeature())
        coordinator.registerFeature(SpecialBlockModelProbeFeature())
        coordinator.registerFeature(DrawCommandDiagnosticsFeature)
        coordinator.registerFeature(DebugOverlayFeature)
        logger.info("[TACZ-Legacy] Builtin render features: {}", coordinator.listRegisteredFeatures())
    }

}
