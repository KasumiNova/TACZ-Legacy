package com.tacz.legacy.client.render.execution

import com.tacz.legacy.client.render.frame.FramePhase

public enum class FrameEventTrigger {
    RENDER_TICK_START,
    RENDER_WORLD_LAST,
    OVERLAY_PRE_ALL
}

public data class FrameEventPhasePlan(
    val triggerPhases: Map<FrameEventTrigger, List<FramePhase>>
) {

    public fun phasesFor(trigger: FrameEventTrigger): List<FramePhase> =
        triggerPhases[trigger].orEmpty()

    public companion object {
        public fun defaultPlan(): FrameEventPhasePlan =
            FrameEventPhasePlan(
                triggerPhases = mapOf(
                    FrameEventTrigger.RENDER_TICK_START to listOf(
                        FramePhase.PREPARE,
                        FramePhase.PRE_UPDATE,
                        FramePhase.UPDATE
                    ),
                    FrameEventTrigger.RENDER_WORLD_LAST to listOf(
                        FramePhase.RENDER_OPAQUE,
                        FramePhase.RENDER_TRANSLUCENT,
                        FramePhase.POST_UPDATE
                    ),
                    FrameEventTrigger.OVERLAY_PRE_ALL to listOf(
                        FramePhase.RENDER_OVERLAY
                    )
                )
            )
    }

}
