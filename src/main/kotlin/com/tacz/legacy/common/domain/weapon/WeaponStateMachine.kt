package com.tacz.legacy.common.domain.weapon

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

public enum class WeaponState {
    IDLE,
    FIRING,
    RELOADING
}

public enum class WeaponFireMode {
    AUTO,
    SEMI,
    BURST
}

public data class WeaponSpec(
    val magazineSize: Int,
    val roundsPerMinute: Int,
    val reloadTicks: Int,
    val fireMode: WeaponFireMode = WeaponFireMode.AUTO,
    val burstSize: Int = 3,
    val maxDistance: Double = 128.0
) {

    init {
        require(magazineSize > 0) { "magazineSize must be > 0, got $magazineSize" }
        require(roundsPerMinute > 0) { "roundsPerMinute must be > 0, got $roundsPerMinute" }
        require(reloadTicks > 0) { "reloadTicks must be > 0, got $reloadTicks" }
        require(burstSize > 0) { "burstSize must be > 0, got $burstSize" }
        require(maxDistance > 0.0) { "maxDistance must be > 0, got $maxDistance" }
    }

    /**
     * 以 20 TPS 计算最小射击间隔：1200 / RPM，并向上取整到 tick。
     */
    public fun shotIntervalTicks(): Int =
        max(1, ceil(1200.0 / roundsPerMinute.toDouble()).toInt())

}

public data class WeaponSnapshot(
    val state: WeaponState = WeaponState.IDLE,
    val ammoInMagazine: Int,
    val ammoReserve: Int,
    val isTriggerHeld: Boolean = false,
    val reloadTicksRemaining: Int = 0,
    val cooldownTicksRemaining: Int = 0,
    val semiLocked: Boolean = false,
    val burstShotsRemaining: Int = 0,
    val totalShotsFired: Int = 0
) {

    init {
        require(ammoInMagazine >= 0) { "ammoInMagazine must be >= 0, got $ammoInMagazine" }
        require(ammoReserve >= 0) { "ammoReserve must be >= 0, got $ammoReserve" }
        require(reloadTicksRemaining >= 0) { "reloadTicksRemaining must be >= 0, got $reloadTicksRemaining" }
        require(cooldownTicksRemaining >= 0) { "cooldownTicksRemaining must be >= 0, got $cooldownTicksRemaining" }
        require(burstShotsRemaining >= 0) { "burstShotsRemaining must be >= 0, got $burstShotsRemaining" }
        require(totalShotsFired >= 0) { "totalShotsFired must be >= 0, got $totalShotsFired" }
    }

}

public sealed class WeaponInput {
    public object TriggerPressed : WeaponInput()
    public object TriggerReleased : WeaponInput()
    public object ReloadPressed : WeaponInput()
    public object InspectPressed : WeaponInput()
    public object Tick : WeaponInput()
}

public data class WeaponStepResult(
    val snapshot: WeaponSnapshot,
    val shotFired: Boolean,
    val dryFired: Boolean,
    val reloadStarted: Boolean,
    val reloadCompleted: Boolean
)

public class WeaponStateMachine(
    private val spec: WeaponSpec,
    initialSnapshot: WeaponSnapshot = WeaponSnapshot(
        ammoInMagazine = spec.magazineSize,
        ammoReserve = 0
    )
) {

    private var current: WeaponSnapshot = validateInitialSnapshot(initialSnapshot)

    public fun snapshot(): WeaponSnapshot = current

    public fun dispatch(input: WeaponInput): WeaponStepResult {
        var next = current
        var shotFired = false
        var dryFired = false
        var reloadStarted = false
        var reloadCompleted = false

        when (input) {
            WeaponInput.TriggerPressed -> {
                next = next.copy(isTriggerHeld = true)
                val fireResult = tryFire(next)
                next = fireResult.snapshot
                shotFired = fireResult.shotFired
                dryFired = fireResult.dryFired
            }

            WeaponInput.TriggerReleased -> {
                next = next.copy(
                    isTriggerHeld = false,
                    semiLocked = false,
                    burstShotsRemaining = 0,
                    state = if (next.state == WeaponState.FIRING) WeaponState.IDLE else next.state
                )
            }

            WeaponInput.ReloadPressed -> {
                val reloadResult = tryStartReload(next)
                next = reloadResult.snapshot
                reloadStarted = reloadResult.started
            }

            WeaponInput.InspectPressed -> {
                // 检视输入属于动画/表现层事件，状态机核心数值不变。
            }

            WeaponInput.Tick -> {
                next = tickCooldown(next)

                if (next.state == WeaponState.RELOADING) {
                    val reloadTickResult = tickReload(next)
                    next = reloadTickResult.snapshot
                    reloadCompleted = reloadTickResult.completed
                }

                if (next.state != WeaponState.RELOADING) {
                    if (!next.isTriggerHeld && next.state == WeaponState.FIRING) {
                        next = next.copy(state = WeaponState.IDLE)
                    }

                    if (next.isTriggerHeld && spec.fireMode == WeaponFireMode.AUTO) {
                        val fireResult = tryFire(next)
                        next = fireResult.snapshot
                        shotFired = shotFired || fireResult.shotFired
                    }

                    if (next.isTriggerHeld && spec.fireMode == WeaponFireMode.BURST && next.burstShotsRemaining > 0) {
                        val fireResult = tryFire(next)
                        next = fireResult.snapshot
                        shotFired = shotFired || fireResult.shotFired
                    }
                }
            }
        }

        current = next
        return WeaponStepResult(
            snapshot = current,
            shotFired = shotFired,
            dryFired = dryFired,
            reloadStarted = reloadStarted,
            reloadCompleted = reloadCompleted
        )
    }

    private fun validateInitialSnapshot(snapshot: WeaponSnapshot): WeaponSnapshot {
        var normalized = snapshot
        require(snapshot.ammoInMagazine <= spec.magazineSize) {
            "ammoInMagazine (${snapshot.ammoInMagazine}) cannot exceed magazineSize (${spec.magazineSize})"
        }
        require(snapshot.state != WeaponState.RELOADING || snapshot.reloadTicksRemaining > 0) {
            "reloadTicksRemaining must be > 0 when state is RELOADING"
        }

        if (spec.fireMode != WeaponFireMode.BURST && normalized.burstShotsRemaining != 0) {
            normalized = normalized.copy(burstShotsRemaining = 0)
        }
        if (spec.fireMode == WeaponFireMode.BURST && normalized.burstShotsRemaining >= spec.burstSize) {
            normalized = normalized.copy(burstShotsRemaining = spec.burstSize - 1)
        }
        return normalized
    }

    private fun tickCooldown(snapshot: WeaponSnapshot): WeaponSnapshot {
        if (snapshot.cooldownTicksRemaining <= 0) {
            return snapshot
        }
        return snapshot.copy(cooldownTicksRemaining = snapshot.cooldownTicksRemaining - 1)
    }

    private fun tryFire(snapshot: WeaponSnapshot): FireResult {
        if (snapshot.state == WeaponState.RELOADING) {
            return FireResult(snapshot, shotFired = false, dryFired = false)
        }
        if (snapshot.cooldownTicksRemaining > 0) {
            return FireResult(snapshot, shotFired = false, dryFired = false)
        }
        if (snapshot.ammoInMagazine <= 0) {
            return FireResult(
                snapshot = if (snapshot.state == WeaponState.FIRING) {
                    snapshot.copy(state = WeaponState.IDLE, burstShotsRemaining = 0)
                } else {
                    snapshot.copy(burstShotsRemaining = 0)
                },
                shotFired = false,
                dryFired = true
            )
        }
        if (spec.fireMode == WeaponFireMode.SEMI && snapshot.semiLocked) {
            return FireResult(snapshot, shotFired = false, dryFired = false)
        }
        if (spec.fireMode == WeaponFireMode.BURST && snapshot.semiLocked && snapshot.burstShotsRemaining <= 0) {
            return FireResult(snapshot, shotFired = false, dryFired = false)
        }

        val nextBurstShotsRemaining = when (spec.fireMode) {
            WeaponFireMode.BURST -> {
                if (snapshot.burstShotsRemaining > 0) {
                    snapshot.burstShotsRemaining - 1
                } else {
                    spec.burstSize - 1
                }
            }

            else -> 0
        }

        val nextSemiLocked = when (spec.fireMode) {
            WeaponFireMode.AUTO -> false
            WeaponFireMode.SEMI -> true
            WeaponFireMode.BURST -> nextBurstShotsRemaining == 0
        }

        return FireResult(
            snapshot = snapshot.copy(
                state = WeaponState.FIRING,
                ammoInMagazine = snapshot.ammoInMagazine - 1,
                cooldownTicksRemaining = spec.shotIntervalTicks(),
                semiLocked = nextSemiLocked,
                burstShotsRemaining = nextBurstShotsRemaining,
                totalShotsFired = snapshot.totalShotsFired + 1
            ),
            shotFired = true,
            dryFired = false
        )
    }

    private fun tryStartReload(snapshot: WeaponSnapshot): ReloadStartResult {
        if (snapshot.state == WeaponState.RELOADING) {
            return ReloadStartResult(snapshot, started = false)
        }
        if (snapshot.ammoInMagazine >= spec.magazineSize) {
            return ReloadStartResult(snapshot, started = false)
        }
        if (snapshot.ammoReserve <= 0) {
            return ReloadStartResult(snapshot, started = false)
        }

        return ReloadStartResult(
            snapshot = snapshot.copy(
                state = WeaponState.RELOADING,
                reloadTicksRemaining = spec.reloadTicks,
                cooldownTicksRemaining = 0,
                semiLocked = false,
                isTriggerHeld = false,
                burstShotsRemaining = 0
            ),
            started = true
        )
    }

    private fun tickReload(snapshot: WeaponSnapshot): ReloadTickResult {
        val remaining = snapshot.reloadTicksRemaining
        if (remaining > 1) {
            return ReloadTickResult(
                snapshot = snapshot.copy(reloadTicksRemaining = remaining - 1),
                completed = false
            )
        }

        val needed = spec.magazineSize - snapshot.ammoInMagazine
        val loaded = min(needed, snapshot.ammoReserve)

        return ReloadTickResult(
            snapshot = snapshot.copy(
                state = WeaponState.IDLE,
                ammoInMagazine = snapshot.ammoInMagazine + loaded,
                ammoReserve = snapshot.ammoReserve - loaded,
                burstShotsRemaining = 0,
                reloadTicksRemaining = 0
            ),
            completed = true
        )
    }

    private data class FireResult(
        val snapshot: WeaponSnapshot,
        val shotFired: Boolean,
        val dryFired: Boolean
    )

    private data class ReloadStartResult(
        val snapshot: WeaponSnapshot,
        val started: Boolean
    )

    private data class ReloadTickResult(
        val snapshot: WeaponSnapshot,
        val completed: Boolean
    )

}