package com.tacz.legacy.common.application.weapon

import com.tacz.legacy.common.application.port.Vec3d
import com.tacz.legacy.common.domain.weapon.WeaponInput
import com.tacz.legacy.common.domain.weapon.WeaponSnapshot

public data class WeaponSessionHandle(
    val sessionId: String,
    val sourceId: String,
    val gunId: String
)

public data class WeaponSessionDebugSnapshot(
    val sourceId: String,
    val gunId: String,
    val snapshot: WeaponSnapshot
)

public class WeaponSessionService(
    private val runtimeRegistry: WeaponRuntimeRegistry,
    private val behaviorEngine: WeaponPortBehaviorEngine
) {

    private val sessionsById: MutableMap<String, WeaponSession> = linkedMapOf()

    @Synchronized
    public fun openSession(
        sessionId: String,
        gunId: String,
        ammoReserve: Int = 0,
        ammoInMagazine: Int? = null,
        allowFallbackDefinition: Boolean = false
    ): WeaponSessionHandle? {
        val session = runtimeRegistry.createSession(
            gunId = gunId,
            ammoReserve = ammoReserve,
            ammoInMagazine = ammoInMagazine,
            allowFallbackDefinition = allowFallbackDefinition
        ) ?: return null

        sessionsById[sessionId] = session
        return WeaponSessionHandle(
            sessionId = sessionId,
            sourceId = session.sourceId,
            gunId = session.gunId
        )
    }

    @Synchronized
    public fun hasSession(sessionId: String): Boolean = sessionsById.containsKey(sessionId)

    @Synchronized
    public fun sessionCount(): Int = sessionsById.size

    @Synchronized
    public fun closeSession(sessionId: String): Boolean = sessionsById.remove(sessionId) != null

    @Synchronized
    public fun upsertAuthoritativeSnapshot(
        sessionId: String,
        gunId: String,
        snapshot: WeaponSnapshot,
        allowFallbackDefinition: Boolean = false
    ): WeaponSessionHandle? {
        val session = runtimeRegistry.createSessionFromSnapshot(
            gunId = gunId,
            authoritativeSnapshot = snapshot,
            allowFallbackDefinition = allowFallbackDefinition
        ) ?: return null
        sessionsById[sessionId] = session
        return WeaponSessionHandle(
            sessionId = sessionId,
            sourceId = session.sourceId,
            gunId = session.gunId
        )
    }

    @Synchronized
    public fun clearSessions() {
        sessionsById.clear()
    }

    @Synchronized
    public fun snapshot(sessionId: String): WeaponSnapshot? = sessionsById[sessionId]?.machine?.snapshot()

    @Synchronized
    public fun debugSnapshot(sessionId: String): WeaponSessionDebugSnapshot? {
        val session = sessionsById[sessionId] ?: return null
        return WeaponSessionDebugSnapshot(
            sourceId = session.sourceId,
            gunId = session.gunId,
            snapshot = session.machine.snapshot()
        )
    }

    @Synchronized
    public fun dispatch(
        sessionId: String,
        input: WeaponInput,
        muzzlePosition: Vec3d,
        shotDirection: Vec3d,
        config: WeaponBehaviorConfig? = null
    ): WeaponBehaviorResult? {
        val session = sessionsById[sessionId] ?: return null
        val effectiveConfig = config ?: session.defaultBehaviorConfig
        return behaviorEngine.dispatch(
            machine = session.machine,
            input = input,
            muzzlePosition = muzzlePosition,
            shotDirection = shotDirection,
            config = effectiveConfig
        )
    }

}