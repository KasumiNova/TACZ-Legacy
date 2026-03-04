package com.tacz.legacy.common.application.weapon

import com.tacz.legacy.common.application.port.Vec3d
import com.tacz.legacy.common.domain.weapon.WeaponInput

public data class WeaponTickContext(
    val sessionId: String,
    val currentGunId: String?,
    val muzzlePosition: Vec3d,
    val shotDirection: Vec3d,
    val initialAmmoInMagazine: Int? = null,
    val initialAmmoReserve: Int? = null,
    val behaviorConfig: WeaponBehaviorConfig? = null
)

public class WeaponAutoSessionOrchestrator(
    private val sessionService: WeaponSessionService
) {

    private val trackedGunBySessionId: MutableMap<String, String> = linkedMapOf()

    public fun trackedSessionCount(): Int = trackedGunBySessionId.size

    public fun onTick(context: WeaponTickContext): WeaponBehaviorResult? {
        val normalizedGunId = context.currentGunId?.trim()?.ifBlank { null }
        if (normalizedGunId == null) {
            onSessionEnd(context.sessionId)
            return null
        }

        if (!ensureSession(context, normalizedGunId)) {
            return null
        }

        return sessionService.dispatch(
            sessionId = context.sessionId,
            input = WeaponInput.Tick,
            muzzlePosition = context.muzzlePosition,
            shotDirection = context.shotDirection,
            config = context.behaviorConfig
        )
    }

    public fun onInput(context: WeaponTickContext, input: WeaponInput): WeaponBehaviorResult? {
        val normalizedGunId = context.currentGunId?.trim()?.ifBlank { null }
        if (normalizedGunId == null) {
            onSessionEnd(context.sessionId)
            return null
        }

        if (!ensureSession(context, normalizedGunId)) {
            return null
        }

        return sessionService.dispatch(
            sessionId = context.sessionId,
            input = input,
            muzzlePosition = context.muzzlePosition,
            shotDirection = context.shotDirection,
            config = context.behaviorConfig
        )
    }

    public fun onSessionEnd(sessionId: String) {
        trackedGunBySessionId.remove(sessionId)
        sessionService.closeSession(sessionId)
    }

    public fun clear() {
        trackedGunBySessionId.keys.toList().forEach { sessionId ->
            sessionService.closeSession(sessionId)
        }
        trackedGunBySessionId.clear()
    }

    private fun ensureSession(context: WeaponTickContext, gunId: String): Boolean {
        val sessionId = context.sessionId
        val trackedGunId = trackedGunBySessionId[sessionId]
        val needsOpen = trackedGunId != gunId || !sessionService.hasSession(sessionId)
        if (!needsOpen) {
            return true
        }

        sessionService.closeSession(sessionId)
        val handle = sessionService.openSession(
            sessionId = sessionId,
            gunId = gunId,
            ammoReserve = context.initialAmmoReserve ?: 0,
            ammoInMagazine = context.initialAmmoInMagazine,
            allowFallbackDefinition = true
        ) ?: run {
            trackedGunBySessionId.remove(sessionId)
            return false
        }

        trackedGunBySessionId[sessionId] = handle.gunId
        return true
    }

}