package com.tacz.legacy.client.render.debug

import com.tacz.legacy.TACZLegacy
import com.tacz.legacy.common.domain.weapon.WeaponSnapshot
import com.tacz.legacy.common.infrastructure.mc.network.WeaponSessionCorrectionReason
import com.tacz.legacy.common.infrastructure.mc.weapon.WeaponAnimationClipType
import com.tacz.legacy.common.infrastructure.mc.weapon.WeaponAnimationRuntimeEventType
import com.tacz.legacy.common.infrastructure.mc.weapon.WeaponAnimationRuntimeSnapshot

public object WeaponConsistencyDiagnostics {

    private val statesBySessionId: MutableMap<String, SessionDiagnosticsState> = linkedMapOf()

    @Synchronized
    public fun observeAnimationSnapshot(sessionId: String, snapshot: WeaponAnimationRuntimeSnapshot?) {
        val normalizedSessionId = normalizeSessionId(sessionId) ?: return
        val runtimeSnapshot = snapshot ?: return

        val state = statesBySessionId.getOrPut(normalizedSessionId) { SessionDiagnosticsState() }

        val previousClip = state.lastClip
        val currentClip = runtimeSnapshot.clip
        if (previousClip != null && previousClip != currentClip) {
            val transitionKey = "${previousClip.name}->${currentClip.name}"
            state.transitionCounts[transitionKey] = (state.transitionCounts[transitionKey] ?: 0) + 1
            state.transitionTotal += 1
            state.lastTransition = transitionKey
        }
        state.lastClip = currentClip

        val newShellEvents = runtimeSnapshot.transientEvents
            .asSequence()
            .filter { event ->
                event.type == WeaponAnimationRuntimeEventType.SHELL_EJECT && event.sequence > state.lastObservedShellEventSequence
            }
            .sortedBy { event -> event.sequence }
            .toList()
        if (newShellEvents.isNotEmpty()) {
            state.lastObservedShellEventSequence = newShellEvents.last().sequence
            state.shellEventsObserved += newShellEvents.size
        }
    }

    @Synchronized
    public fun recordShellSpawn(
        sessionId: String,
        perspective: ShellSpawnPerspective,
        source: ShellSpawnSource,
        count: Int = 1
    ) {
        val normalizedSessionId = normalizeSessionId(sessionId) ?: return
        if (count <= 0) {
            return
        }

        val state = statesBySessionId.getOrPut(normalizedSessionId) { SessionDiagnosticsState() }
        when (perspective) {
            ShellSpawnPerspective.FIRST_PERSON -> {
                when (source) {
                    ShellSpawnSource.EVENT -> state.firstPersonEventSpawnCount += count
                    ShellSpawnSource.FALLBACK -> state.firstPersonFallbackSpawnCount += count
                }
            }

            ShellSpawnPerspective.THIRD_PERSON -> {
                when (source) {
                    ShellSpawnSource.EVENT -> state.thirdPersonEventSpawnCount += count
                    ShellSpawnSource.FALLBACK -> state.thirdPersonFallbackSpawnCount += count
                }
            }
        }
    }

    @Synchronized
    public fun recordSessionDrift(
        sessionId: String,
        driftFields: Int,
        correctionReason: WeaponSessionCorrectionReason,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        val normalizedSessionId = normalizeSessionId(sessionId) ?: return
        val state = statesBySessionId.getOrPut(normalizedSessionId) { SessionDiagnosticsState() }

        val drift = driftFields.coerceAtLeast(0)
        state.driftSampleCount += 1
        state.driftLastFields = drift
        if (drift > 0) {
            state.driftDetectedCount += 1
            state.driftMaxFields = maxOf(state.driftMaxFields, drift)
            maybeLogDrift(
                sessionId = normalizedSessionId,
                driftFields = drift,
                correctionReason = correctionReason,
                state = state,
                nowMillis = nowMillis
            )
        }

        state.correctionReasonCounts[correctionReason] =
            (state.correctionReasonCounts[correctionReason] ?: 0) + 1
    }

    public fun countSnapshotDriftFields(local: WeaponSnapshot, authoritative: WeaponSnapshot): Int {
        var mismatch = 0
        if (local.state != authoritative.state) mismatch += 1
        if (local.ammoInMagazine != authoritative.ammoInMagazine) mismatch += 1
        if (local.ammoReserve != authoritative.ammoReserve) mismatch += 1
        if (local.isTriggerHeld != authoritative.isTriggerHeld) mismatch += 1
        if (local.reloadTicksRemaining != authoritative.reloadTicksRemaining) mismatch += 1
        if (local.cooldownTicksRemaining != authoritative.cooldownTicksRemaining) mismatch += 1
        if (local.semiLocked != authoritative.semiLocked) mismatch += 1
        if (local.burstShotsRemaining != authoritative.burstShotsRemaining) mismatch += 1
        if (local.totalShotsFired != authoritative.totalShotsFired) mismatch += 1
        return mismatch
    }

    @Synchronized
    public fun snapshot(sessionId: String): WeaponConsistencyDiagnosticSnapshot? {
        val normalizedSessionId = normalizeSessionId(sessionId) ?: return null
        val state = statesBySessionId[normalizedSessionId] ?: return null
        return WeaponConsistencyDiagnosticSnapshot(
            transitionTotal = state.transitionTotal,
            lastTransition = state.lastTransition,
            shellEventsObserved = state.shellEventsObserved,
            firstPersonEventSpawnCount = state.firstPersonEventSpawnCount,
            firstPersonFallbackSpawnCount = state.firstPersonFallbackSpawnCount,
            thirdPersonEventSpawnCount = state.thirdPersonEventSpawnCount,
            thirdPersonFallbackSpawnCount = state.thirdPersonFallbackSpawnCount,
            driftSampleCount = state.driftSampleCount,
            driftDetectedCount = state.driftDetectedCount,
            driftLastFields = state.driftLastFields,
            driftMaxFields = state.driftMaxFields,
            correctionReasonCounts = state.correctionReasonCounts.toMap(),
            transitionCounts = state.transitionCounts.toMap()
        )
    }

    @Synchronized
    public fun clearSession(sessionId: String): Boolean {
        val normalizedSessionId = normalizeSessionId(sessionId) ?: return false
        return statesBySessionId.remove(normalizedSessionId) != null
    }

    @Synchronized
    public fun clearAll() {
        statesBySessionId.clear()
    }

    private fun maybeLogDrift(
        sessionId: String,
        driftFields: Int,
        correctionReason: WeaponSessionCorrectionReason,
        state: SessionDiagnosticsState,
        nowMillis: Long
    ) {
        val elapsed = (nowMillis - state.lastDriftLogAtMillis).coerceAtLeast(0L)
        if (state.lastDriftLogAtMillis > 0L && elapsed < DRIFT_LOG_INTERVAL_MILLIS) {
            return
        }

        TACZLegacy.logger.debug(
            "[WeaponConsistency] drift sid={} driftFields={} correction={} driftDetected={} driftSamples={} max={}",
            sessionId,
            driftFields,
            correctionReason.name.lowercase(),
            state.driftDetectedCount,
            state.driftSampleCount,
            state.driftMaxFields
        )
        state.lastDriftLogAtMillis = nowMillis
    }

    private fun normalizeSessionId(sessionId: String?): String? {
        return sessionId
            ?.trim()
            ?.ifBlank { null }
    }

    private data class SessionDiagnosticsState(
        var lastClip: WeaponAnimationClipType? = null,
        var lastTransition: String? = null,
        var transitionTotal: Int = 0,
        val transitionCounts: MutableMap<String, Int> = linkedMapOf(),
        var lastObservedShellEventSequence: Long = 0L,
        var shellEventsObserved: Int = 0,
        var firstPersonEventSpawnCount: Int = 0,
        var firstPersonFallbackSpawnCount: Int = 0,
        var thirdPersonEventSpawnCount: Int = 0,
        var thirdPersonFallbackSpawnCount: Int = 0,
        var driftSampleCount: Int = 0,
        var driftDetectedCount: Int = 0,
        var driftLastFields: Int = 0,
        var driftMaxFields: Int = 0,
        var lastDriftLogAtMillis: Long = 0L,
        val correctionReasonCounts: MutableMap<WeaponSessionCorrectionReason, Int> = linkedMapOf()
    )

    private const val DRIFT_LOG_INTERVAL_MILLIS: Long = 1_000L
}

public enum class ShellSpawnPerspective {
    FIRST_PERSON,
    THIRD_PERSON
}

public enum class ShellSpawnSource {
    EVENT,
    FALLBACK
}

public data class WeaponConsistencyDiagnosticSnapshot(
    val transitionTotal: Int,
    val lastTransition: String?,
    val shellEventsObserved: Int,
    val firstPersonEventSpawnCount: Int,
    val firstPersonFallbackSpawnCount: Int,
    val thirdPersonEventSpawnCount: Int,
    val thirdPersonFallbackSpawnCount: Int,
    val driftSampleCount: Int,
    val driftDetectedCount: Int,
    val driftLastFields: Int,
    val driftMaxFields: Int,
    val correctionReasonCounts: Map<WeaponSessionCorrectionReason, Int>,
    val transitionCounts: Map<String, Int>
)
