package com.tacz.legacy.common.infrastructure.mc.weapon

import com.tacz.legacy.common.application.weapon.WeaponAnimationSignal
import com.tacz.legacy.common.application.weapon.WeaponBehaviorResult
import com.tacz.legacy.common.domain.weapon.WeaponState

public enum class WeaponAnimationClipType {
    IDLE,
    FIRE,
    RELOAD,
    INSPECT,
    DRY_FIRE,
    DRAW,
    PUT_AWAY,
    WALK,
    RUN,
    AIM,
    BOLT
}

public enum class WeaponAnimationRuntimeEventType {
    SHELL_EJECT
}

public data class WeaponAnimationRuntimeEvent(
    val sequence: Long,
    val type: WeaponAnimationRuntimeEventType,
    val clip: WeaponAnimationClipType,
    val emittedAtMillis: Long
)

public data class WeaponAnimationShellEjectPlan(
    val fireTriggerMillis: Long? = 0L,
    val reloadTriggerMillis: Long? = null,
    val boltTriggerMillis: Long? = null
) {
    public fun normalized(): WeaponAnimationShellEjectPlan = WeaponAnimationShellEjectPlan(
        fireTriggerMillis = fireTriggerMillis?.coerceAtLeast(0L),
        reloadTriggerMillis = reloadTriggerMillis?.coerceAtLeast(0L),
        boltTriggerMillis = boltTriggerMillis?.coerceAtLeast(0L)
    )

    public fun triggerForClip(clip: WeaponAnimationClipType): Long? = when (clip) {
        WeaponAnimationClipType.FIRE -> fireTriggerMillis
        WeaponAnimationClipType.RELOAD -> reloadTriggerMillis
        WeaponAnimationClipType.BOLT -> boltTriggerMillis
        else -> null
    }
}

public data class WeaponAnimationRuntimeSnapshot(
    val sessionId: String,
    val gunId: String,
    val clip: WeaponAnimationClipType,
    val progress: Float,
    val elapsedMillis: Long,
    val durationMillis: Long,
    val lastUpdatedAtMillis: Long,
    val transientEvents: List<WeaponAnimationRuntimeEvent> = emptyList()
)

public object WeaponAnimationRuntimeRegistry {

    private val tracksBySessionId: MutableMap<String, SessionTrack> = linkedMapOf()

    @Synchronized
    public fun observeBehavior(
        sessionId: String,
        gunId: String,
        result: WeaponBehaviorResult,
        clipDurationOverridesMillis: Map<WeaponAnimationClipType, Long> = emptyMap(),
        reloadTicks: Int? = null,
        preferBoltCycleAfterFire: Boolean = false,
        shellEjectPlan: WeaponAnimationShellEjectPlan = WeaponAnimationShellEjectPlan(),
        nowMillis: Long = System.currentTimeMillis()
    ) {
        val normalizedGunId = gunId.trim().lowercase()
        if (normalizedGunId.isBlank()) {
            tracksBySessionId.remove(sessionId)
            return
        }

        val track = tracksBySessionId[sessionId]
        val current = if (track == null || track.gunId != normalizedGunId) {
            val normalizedPlan = shellEjectPlan.normalized()
            SessionTrack(
                gunId = normalizedGunId,
                clip = WeaponAnimationClipType.IDLE,
                clipStartedAtMillis = nowMillis,
                clipDurationMillis = 0L,
                lastUpdatedAtMillis = nowMillis,
                reloadProgressHint = null,
                preferBoltCycleAfterFire = preferBoltCycleAfterFire,
                boltClipDurationMillisHint = resolveBoltClipDurationHint(clipDurationOverridesMillis),
                shellEjectPlan = normalizedPlan,
                shellEjectTriggerMillisForCurrentClip = normalizedPlan.triggerForClip(WeaponAnimationClipType.IDLE),
                shellEjectEmittedForCurrentClip = false,
                nextEventSequence = 0L,
                pendingTransientEvents = mutableListOf()
            )
        } else {
            track
        }
        current.preferBoltCycleAfterFire = preferBoltCycleAfterFire
        current.boltClipDurationMillisHint = resolveBoltClipDurationHint(clipDurationOverridesMillis)
        current.shellEjectPlan = shellEjectPlan.normalized()
        if (!current.shellEjectEmittedForCurrentClip) {
            current.shellEjectTriggerMillisForCurrentClip = current.shellEjectPlan.triggerForClip(current.clip)
        }

        val step = result.step
        val signals = result.animationSignals
        val selectedClip = selectClip(signals, step.snapshot.state)

        if (selectedClip != null) {
            val shouldRestart = selectedClip != current.clip || shouldRestartClip(selectedClip, signals)
            if (shouldRestart) {
                switchClip(
                    track = current,
                    clip = selectedClip,
                    nowMillis = nowMillis,
                    durationMillis = resolveClipDurationMillis(
                        clip = selectedClip,
                        clipDurationOverridesMillis = clipDurationOverridesMillis,
                        reloadTicks = reloadTicks,
                        reloadTicksRemaining = step.snapshot.reloadTicksRemaining
                    )
                )
            } else if (selectedClip == WeaponAnimationClipType.RELOAD) {
                current.clipDurationMillis = resolveClipDurationMillis(
                    clip = WeaponAnimationClipType.RELOAD,
                    clipDurationOverridesMillis = clipDurationOverridesMillis,
                    reloadTicks = reloadTicks,
                    reloadTicksRemaining = step.snapshot.reloadTicksRemaining
                )
            }
        } else if (signals.contains(WeaponAnimationSignal.RELOAD_COMPLETE)) {
            switchClip(
                track = current,
                clip = WeaponAnimationClipType.IDLE,
                nowMillis = nowMillis,
                durationMillis = 0L
            )
        } else if (shouldExpireTransientClip(current, nowMillis)) {
            val nextClip = resolveClipOnTransientExpire(current)
            switchClip(
                track = current,
                clip = nextClip,
                nowMillis = nowMillis,
                durationMillis = resolveClipDurationMillis(
                    clip = nextClip,
                    clipDurationOverridesMillis = clipDurationOverridesMillis,
                    reloadTicks = reloadTicks,
                    reloadTicksRemaining = step.snapshot.reloadTicksRemaining
                )
            )
        }

        current.lastUpdatedAtMillis = nowMillis
        current.reloadProgressHint = if (current.clip == WeaponAnimationClipType.RELOAD) {
            val totalReloadTicks = reloadTicks?.coerceAtLeast(1)
                ?: (current.clipDurationMillis / MILLIS_PER_TICK).toInt().coerceAtLeast(1)
            val remaining = step.snapshot.reloadTicksRemaining.coerceAtLeast(0)
            (1f - (remaining.toFloat() / totalReloadTicks.toFloat())).coerceIn(0f, 1f)
        } else {
            null
        }

        maybeEmitShellEjectEvent(current, nowMillis)
        pruneExpiredTransientEvents(current, nowMillis)

        tracksBySessionId[sessionId] = current
    }

    @Synchronized
    public fun snapshot(sessionId: String, nowMillis: Long = System.currentTimeMillis()): WeaponAnimationRuntimeSnapshot? {
        val track = tracksBySessionId[sessionId] ?: return null

        if (shouldExpireTransientClip(track, nowMillis)) {
            val nextClip = resolveClipOnTransientExpire(track)
            switchClip(
                track = track,
                clip = nextClip,
                nowMillis = nowMillis,
                durationMillis = when (nextClip) {
                    WeaponAnimationClipType.BOLT -> track.boltClipDurationMillisHint
                    else -> 0L
                }
            )
        }

        maybeEmitShellEjectEvent(track, nowMillis)
        pruneExpiredTransientEvents(track, nowMillis)

        val elapsed = (nowMillis - track.clipStartedAtMillis).coerceAtLeast(0L)
        val progress = when {
            track.clip == WeaponAnimationClipType.IDLE -> 0f
            track.clip == WeaponAnimationClipType.RELOAD && track.reloadProgressHint != null ->
                track.reloadProgressHint!!.coerceIn(0f, 1f)
            track.clipDurationMillis <= 0L -> 1f
            else -> (elapsed.toFloat() / track.clipDurationMillis.toFloat()).coerceIn(0f, 1f)
        }

        return WeaponAnimationRuntimeSnapshot(
            sessionId = sessionId,
            gunId = track.gunId,
            clip = track.clip,
            progress = progress,
            elapsedMillis = elapsed,
            durationMillis = track.clipDurationMillis,
            lastUpdatedAtMillis = track.lastUpdatedAtMillis,
            transientEvents = track.pendingTransientEvents.toList()
        )
    }

    @Synchronized
    public fun removeSession(sessionId: String) {
        tracksBySessionId.remove(sessionId)
    }

    @Synchronized
    public fun clear() {
        tracksBySessionId.clear()
    }

    private fun selectClip(
        signals: Set<WeaponAnimationSignal>,
        state: WeaponState
    ): WeaponAnimationClipType? {
        if (signals.contains(WeaponAnimationSignal.RELOAD_START) || state == WeaponState.RELOADING) {
            return WeaponAnimationClipType.RELOAD
        }
        if (signals.contains(WeaponAnimationSignal.INSPECT)) {
            return WeaponAnimationClipType.INSPECT
        }
        if (signals.contains(WeaponAnimationSignal.FIRE)) {
            return WeaponAnimationClipType.FIRE
        }
        if (signals.contains(WeaponAnimationSignal.DRY_FIRE)) {
            return WeaponAnimationClipType.DRY_FIRE
        }
        return null
    }

    private fun shouldRestartClip(
        clip: WeaponAnimationClipType,
        signals: Set<WeaponAnimationSignal>
    ): Boolean = when (clip) {
        WeaponAnimationClipType.IDLE -> false
        WeaponAnimationClipType.FIRE -> signals.contains(WeaponAnimationSignal.FIRE)
        WeaponAnimationClipType.RELOAD -> signals.contains(WeaponAnimationSignal.RELOAD_START)
        WeaponAnimationClipType.INSPECT -> signals.contains(WeaponAnimationSignal.INSPECT)
        WeaponAnimationClipType.DRY_FIRE -> signals.contains(WeaponAnimationSignal.DRY_FIRE)
        WeaponAnimationClipType.DRAW,
        WeaponAnimationClipType.PUT_AWAY,
        WeaponAnimationClipType.WALK,
        WeaponAnimationClipType.RUN,
        WeaponAnimationClipType.AIM,
        WeaponAnimationClipType.BOLT -> false
    }

    private fun switchClip(
        track: SessionTrack,
        clip: WeaponAnimationClipType,
        nowMillis: Long,
        durationMillis: Long
    ) {
        track.clip = clip
        track.clipStartedAtMillis = nowMillis
        track.clipDurationMillis = durationMillis.coerceAtLeast(0L)
        track.lastUpdatedAtMillis = nowMillis
        if (clip != WeaponAnimationClipType.RELOAD) {
            track.reloadProgressHint = null
        }
        track.shellEjectTriggerMillisForCurrentClip = track.shellEjectPlan.triggerForClip(clip)
        track.shellEjectEmittedForCurrentClip = false
    }

    private fun maybeEmitShellEjectEvent(track: SessionTrack, nowMillis: Long) {
        val trigger = track.shellEjectTriggerMillisForCurrentClip ?: return
        if (track.shellEjectEmittedForCurrentClip) {
            return
        }

        val elapsed = (nowMillis - track.clipStartedAtMillis).coerceAtLeast(0L)
        if (elapsed < trigger) {
            return
        }

        val sequence = track.nextEventSequence + 1L
        track.nextEventSequence = sequence
        track.pendingTransientEvents += WeaponAnimationRuntimeEvent(
            sequence = sequence,
            type = WeaponAnimationRuntimeEventType.SHELL_EJECT,
            clip = track.clip,
            emittedAtMillis = nowMillis
        )
        if (track.pendingTransientEvents.size > MAX_TRANSIENT_EVENTS_PER_SESSION) {
            val overflow = track.pendingTransientEvents.size - MAX_TRANSIENT_EVENTS_PER_SESSION
            repeat(overflow) {
                track.pendingTransientEvents.removeAt(0)
            }
        }
        track.shellEjectEmittedForCurrentClip = true
    }

    private fun pruneExpiredTransientEvents(track: SessionTrack, nowMillis: Long) {
        if (track.pendingTransientEvents.isEmpty()) {
            return
        }
        val minAliveAt = nowMillis - TRANSIENT_EVENT_RETAIN_MILLIS
        val iter = track.pendingTransientEvents.iterator()
        while (iter.hasNext()) {
            val event = iter.next()
            if (event.emittedAtMillis < minAliveAt) {
                iter.remove()
            }
        }
    }

    private fun resolveClipOnTransientExpire(track: SessionTrack): WeaponAnimationClipType {
        if (track.clip == WeaponAnimationClipType.FIRE && track.preferBoltCycleAfterFire) {
            return WeaponAnimationClipType.BOLT
        }
        return WeaponAnimationClipType.IDLE
    }

    private fun resolveBoltClipDurationHint(
        clipDurationOverridesMillis: Map<WeaponAnimationClipType, Long>
    ): Long = clipDurationOverridesMillis[WeaponAnimationClipType.BOLT]
        ?.takeIf { it > 0L }
        ?: BOLT_CLIP_DURATION_MS

    private fun shouldExpireTransientClip(track: SessionTrack, nowMillis: Long): Boolean {
        if (track.clip == WeaponAnimationClipType.IDLE ||
            track.clip == WeaponAnimationClipType.RELOAD ||
            track.clip == WeaponAnimationClipType.WALK ||
            track.clip == WeaponAnimationClipType.RUN ||
            track.clip == WeaponAnimationClipType.AIM
        ) {
            return false
        }
        if (track.clipDurationMillis <= 0L) {
            return true
        }
        return nowMillis - track.clipStartedAtMillis >= track.clipDurationMillis
    }

    private fun resolveClipDurationMillis(
        clip: WeaponAnimationClipType,
        clipDurationOverridesMillis: Map<WeaponAnimationClipType, Long>,
        reloadTicks: Int?,
        reloadTicksRemaining: Int
    ): Long = when (clip) {
        WeaponAnimationClipType.IDLE -> 0L
        WeaponAnimationClipType.FIRE -> clipDurationOverridesMillis[WeaponAnimationClipType.FIRE]?.takeIf { it > 0L }
            ?: FIRE_CLIP_DURATION_MS
        WeaponAnimationClipType.INSPECT -> clipDurationOverridesMillis[WeaponAnimationClipType.INSPECT]?.takeIf { it > 0L }
            ?: INSPECT_CLIP_DURATION_MS
        WeaponAnimationClipType.DRY_FIRE -> clipDurationOverridesMillis[WeaponAnimationClipType.DRY_FIRE]?.takeIf { it > 0L }
            ?: DRY_FIRE_CLIP_DURATION_MS
        WeaponAnimationClipType.DRAW -> clipDurationOverridesMillis[WeaponAnimationClipType.DRAW]?.takeIf { it > 0L }
            ?: DRAW_CLIP_DURATION_MS
        WeaponAnimationClipType.PUT_AWAY -> clipDurationOverridesMillis[WeaponAnimationClipType.PUT_AWAY]?.takeIf { it > 0L }
            ?: PUT_AWAY_CLIP_DURATION_MS
        WeaponAnimationClipType.WALK -> clipDurationOverridesMillis[WeaponAnimationClipType.WALK]?.takeIf { it > 0L }
            ?: WALK_CLIP_DURATION_MS
        WeaponAnimationClipType.RUN -> clipDurationOverridesMillis[WeaponAnimationClipType.RUN]?.takeIf { it > 0L }
            ?: RUN_CLIP_DURATION_MS
        WeaponAnimationClipType.AIM -> clipDurationOverridesMillis[WeaponAnimationClipType.AIM]?.takeIf { it > 0L }
            ?: AIM_CLIP_DURATION_MS
        WeaponAnimationClipType.BOLT -> clipDurationOverridesMillis[WeaponAnimationClipType.BOLT]?.takeIf { it > 0L }
            ?: BOLT_CLIP_DURATION_MS
        WeaponAnimationClipType.RELOAD -> {
            val overrideDuration = clipDurationOverridesMillis[WeaponAnimationClipType.RELOAD]
                ?.takeIf { it > 0L }
            if (overrideDuration != null) {
                overrideDuration
            } else {
                val ticks = reloadTicks?.coerceAtLeast(1)
                    ?: reloadTicksRemaining.coerceAtLeast(DEFAULT_RELOAD_TICKS)
                ticks.toLong() * MILLIS_PER_TICK
            }
        }
    }

    private data class SessionTrack(
        val gunId: String,
        var clip: WeaponAnimationClipType,
        var clipStartedAtMillis: Long,
        var clipDurationMillis: Long,
        var lastUpdatedAtMillis: Long,
        var reloadProgressHint: Float?,
        var preferBoltCycleAfterFire: Boolean,
        var boltClipDurationMillisHint: Long,
        var shellEjectPlan: WeaponAnimationShellEjectPlan,
        var shellEjectTriggerMillisForCurrentClip: Long?,
        var shellEjectEmittedForCurrentClip: Boolean,
        var nextEventSequence: Long,
        val pendingTransientEvents: MutableList<WeaponAnimationRuntimeEvent>
    )

    private const val MILLIS_PER_TICK: Long = 50L
    private const val FIRE_CLIP_DURATION_MS: Long = 120L
    private const val DRY_FIRE_CLIP_DURATION_MS: Long = 150L
    private const val INSPECT_CLIP_DURATION_MS: Long = 1_200L
    private const val DRAW_CLIP_DURATION_MS: Long = 320L
    private const val PUT_AWAY_CLIP_DURATION_MS: Long = 240L
    private const val WALK_CLIP_DURATION_MS: Long = 650L
    private const val RUN_CLIP_DURATION_MS: Long = 520L
    private const val AIM_CLIP_DURATION_MS: Long = 300L
    private const val BOLT_CLIP_DURATION_MS: Long = 220L
    private const val DEFAULT_RELOAD_TICKS: Int = 20
    private const val TRANSIENT_EVENT_RETAIN_MILLIS: Long = 1_200L
    private const val MAX_TRANSIENT_EVENTS_PER_SESSION: Int = 16
}
