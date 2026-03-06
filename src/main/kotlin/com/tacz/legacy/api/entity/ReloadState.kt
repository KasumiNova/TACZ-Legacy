package com.tacz.legacy.api.entity

/**
 * 换弹状态，与上游 TACZ ReloadState 行为一致。
 */
public class ReloadState(
    public var stateType: StateType = StateType.NOT_RELOADING,
    public var countDown: Long = NOT_RELOADING_COUNTDOWN,
) {
    public constructor(src: ReloadState) : this(src.stateType, src.countDown)

    public fun getEffectiveCountDown(): Long {
        return if (stateType == StateType.NOT_RELOADING) NOT_RELOADING_COUNTDOWN else countDown
    }

    public fun isReloading(): Boolean = stateType.isReloading()
    public fun isReloadingEmpty(): Boolean = stateType.isReloadingEmpty()
    public fun isReloadingTactical(): Boolean = stateType.isReloadingTactical()
    public fun isReloadFinishing(): Boolean = stateType.isReloadFinishing()

    override fun equals(other: Any?): Boolean {
        if (other !is ReloadState) return false
        return other.stateType == stateType && other.countDown == countDown
    }

    override fun hashCode(): Int = 31 * stateType.hashCode() + countDown.hashCode()

    public enum class StateType {
        NOT_RELOADING,
        EMPTY_RELOAD_FEEDING,
        EMPTY_RELOAD_FINISHING,
        TACTICAL_RELOAD_FEEDING,
        TACTICAL_RELOAD_FINISHING;

        public fun isReloadingEmpty(): Boolean =
            this == EMPTY_RELOAD_FEEDING || this == EMPTY_RELOAD_FINISHING

        public fun isReloadingTactical(): Boolean =
            this == TACTICAL_RELOAD_FEEDING || this == TACTICAL_RELOAD_FINISHING

        public fun isReloading(): Boolean = isReloadingEmpty() || isReloadingTactical()

        public fun isReloadFinishing(): Boolean =
            this == EMPTY_RELOAD_FINISHING || this == TACTICAL_RELOAD_FINISHING
    }

    public companion object {
        public const val NOT_RELOADING_COUNTDOWN: Long = -1L
    }
}
