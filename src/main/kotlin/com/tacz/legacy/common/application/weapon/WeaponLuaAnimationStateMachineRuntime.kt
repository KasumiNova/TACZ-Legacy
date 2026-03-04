package com.tacz.legacy.common.application.weapon

import com.tacz.legacy.common.application.gunpack.GunDisplayDefinition

/**
 * Lua 动画状态机运行时桥接：
 * - 按 session 复用状态机实例；
 * - 脚本缺失/执行失败时返回 null 或 failed，调用方可回退 signal 路径；
 * - 当前只负责“可用性与状态推进”，不直接决定 clip（后续可在此扩展 state->clip 映射）。
 */
public object WeaponLuaAnimationStateMachineRuntime {

    private val sessionMachines: MutableMap<String, SessionMachine> = linkedMapOf()

    @Synchronized
    public fun tick(
        sessionId: String,
        gunId: String,
        displayDefinition: GunDisplayDefinition,
        gunScriptParams: Map<String, Float>,
        behaviorResult: WeaponBehaviorResult,
        nowMillis: Long = System.currentTimeMillis()
    ): WeaponLuaAnimationTickResult? {
        val normalizedSessionId = sessionId.trim()
        val normalizedGunId = gunId.trim().lowercase()
        if (normalizedSessionId.isEmpty() || normalizedGunId.isEmpty()) {
            clearSession(normalizedSessionId)
            return null
        }

        val scriptSource = displayDefinition.stateMachineScriptContent
            ?.trim()
            ?.ifBlank { null }
            ?: run {
                clearSession(normalizedSessionId)
                return null
            }

        val scriptId = displayDefinition.stateMachinePath
            ?.trim()
            ?.ifBlank { null }
            ?: "${displayDefinition.sourceId}#inline_state_machine"

        val machineState = resolveOrCreateSessionMachine(
            sessionId = normalizedSessionId,
            gunId = normalizedGunId,
            scriptId = scriptId,
            scriptSource = scriptSource
        )

        val context = buildLuaContext(
            gunId = normalizedGunId,
            displayDefinition = displayDefinition,
            gunScriptParams = gunScriptParams,
            behaviorResult = behaviorResult,
            nowMillis = nowMillis
        )

        val tickResult = machineState.machine.tick(context)
        machineState.lastTickResult = tickResult
        return tickResult
    }

    @Synchronized
    public fun clearSession(sessionId: String) {
        val normalizedSessionId = sessionId.trim()
        if (normalizedSessionId.isEmpty()) {
            return
        }
        val removed = sessionMachines.remove(normalizedSessionId) ?: return
        removed.machine.shutdown()
    }

    @Synchronized
    public fun clear() {
        val sessionIds = sessionMachines.keys.toList()
        sessionIds.forEach(::clearSession)
    }

    private fun resolveOrCreateSessionMachine(
        sessionId: String,
        gunId: String,
        scriptId: String,
        scriptSource: String
    ): SessionMachine {
        val existing = sessionMachines[sessionId]
        if (existing != null &&
            existing.gunId == gunId &&
            existing.scriptId == scriptId &&
            existing.scriptSource == scriptSource
        ) {
            return existing
        }

        existing?.machine?.shutdown()

        val machine = WeaponLuaAnimationStateMachine(
            scriptId = scriptId,
            scriptSource = scriptSource
        )
        val created = SessionMachine(
            gunId = gunId,
            scriptId = scriptId,
            scriptSource = scriptSource,
            machine = machine
        )
        sessionMachines[sessionId] = created
        return created
    }

    private fun buildLuaContext(
        gunId: String,
        displayDefinition: GunDisplayDefinition,
        gunScriptParams: Map<String, Float>,
        behaviorResult: WeaponBehaviorResult,
        nowMillis: Long
    ): Map<String, Any?> {
        val snapshot = behaviorResult.step.snapshot
        val signals = behaviorResult.animationSignals
        val inputToken = resolveInputToken(behaviorResult)
        val out = linkedMapOf<String, Any?>()

        out["gun_id"] = gunId
        out["state"] = snapshot.state.name.lowercase()
        out["ammo_in_magazine"] = snapshot.ammoInMagazine
        out["ammo_reserve"] = snapshot.ammoReserve
        out["reload_ticks_remaining"] = snapshot.reloadTicksRemaining
        out["cooldown_ticks_remaining"] = snapshot.cooldownTicksRemaining
        out["total_shots_fired"] = snapshot.totalShotsFired
        out["is_trigger_held"] = snapshot.isTriggerHeld
        out["semi_locked"] = snapshot.semiLocked
        out["burst_shots_remaining"] = snapshot.burstShotsRemaining

        out["shot_fired"] = behaviorResult.step.shotFired
        out["dry_fired"] = behaviorResult.step.dryFired
        out["reload_started"] = behaviorResult.step.reloadStarted
        out["reload_completed"] = behaviorResult.step.reloadCompleted

        out["signal_fire"] = signals.contains(WeaponAnimationSignal.FIRE)
        out["signal_dry_fire"] = signals.contains(WeaponAnimationSignal.DRY_FIRE)
        out["signal_reload_start"] = signals.contains(WeaponAnimationSignal.RELOAD_START)
        out["signal_reload_complete"] = signals.contains(WeaponAnimationSignal.RELOAD_COMPLETE)
        out["signal_inspect"] = signals.contains(WeaponAnimationSignal.INSPECT)

        out["fire"] = out["signal_fire"]
        out["dry_fire"] = out["signal_dry_fire"]
        out["inspect"] = out["signal_inspect"]

        out["input"] = inputToken
        out["input_token"] = inputToken
        out["input_shoot"] = inputToken == INPUT_SHOOT
        out["input_reload"] = inputToken == INPUT_RELOAD
        out["input_inspect"] = inputToken == INPUT_INSPECT
        out["input_dry_fire"] = inputToken == INPUT_DRY_FIRE
        out["input_idle"] = inputToken == INPUT_IDLE

        out["state_machine_source"] = displayDefinition.stateMachineSource
        out["timestamp_millis"] = nowMillis

        gunScriptParams.forEach { (rawKey, value) ->
            val key = normalizeContextKey(rawKey) ?: return@forEach
            out[key] = value
            out["param_$key"] = value
        }

        return out.toMap()
    }

    private fun resolveInputToken(behaviorResult: WeaponBehaviorResult): String {
        val signals = behaviorResult.animationSignals
        return when {
            signals.contains(WeaponAnimationSignal.RELOAD_START) -> INPUT_RELOAD
            signals.contains(WeaponAnimationSignal.INSPECT) -> INPUT_INSPECT
            signals.contains(WeaponAnimationSignal.FIRE) -> INPUT_SHOOT
            signals.contains(WeaponAnimationSignal.DRY_FIRE) -> INPUT_DRY_FIRE
            behaviorResult.step.snapshot.state.name.equals("RELOADING", ignoreCase = true) -> INPUT_RELOAD
            else -> INPUT_IDLE
        }
    }

    private fun normalizeContextKey(raw: String): String? {
        val normalized = raw.trim()
            .lowercase()
            .map { ch -> if (ch.isLetterOrDigit()) ch else '_' }
            .joinToString(separator = "")
            .replace("__+".toRegex(), "_")
            .trim('_')
        return normalized.ifBlank { null }
    }

    private data class SessionMachine(
        val gunId: String,
        val scriptId: String,
        val scriptSource: String,
        val machine: WeaponLuaAnimationStateMachine,
        var lastTickResult: WeaponLuaAnimationTickResult? = null
    )

    private const val INPUT_SHOOT: String = "shoot"
    private const val INPUT_RELOAD: String = "reload"
    private const val INPUT_INSPECT: String = "inspect"
    private const val INPUT_DRY_FIRE: String = "dry_fire"
    private const val INPUT_IDLE: String = "idle"
}
