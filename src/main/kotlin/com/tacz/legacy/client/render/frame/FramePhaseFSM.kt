package com.tacz.legacy.client.render.frame

public class FramePhaseFSM(
    phases: List<FramePhase> = FramePhase.defaultOrder()
) {

    private val orderedPhases: List<FramePhase> = phases.toList().also {
        require(it.isNotEmpty()) { "FramePhase list must not be empty." }
    }

    private var cursor: Int = 0

    public fun expectedPhase(): FramePhase = orderedPhases[cursor]

    public fun isExpecting(phase: FramePhase): Boolean = expectedPhase() == phase

    public fun advance(phase: FramePhase) {
        check(isExpecting(phase)) {
            "Unexpected frame phase. Expected=${expectedPhase()} Actual=$phase"
        }
        cursor = (cursor + 1) % orderedPhases.size
    }

    public fun reset() {
        cursor = 0
    }

    public fun index(): Int = cursor
}
