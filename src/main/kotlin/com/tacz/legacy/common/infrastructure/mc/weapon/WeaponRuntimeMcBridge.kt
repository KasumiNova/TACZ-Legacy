package com.tacz.legacy.common.infrastructure.mc.weapon

import com.tacz.legacy.common.application.weapon.WeaponAutoSessionOrchestrator
import com.tacz.legacy.common.application.weapon.WeaponBehaviorResult
import com.tacz.legacy.common.application.weapon.WeaponPortBehaviorEngine
import com.tacz.legacy.common.application.weapon.WeaponRuntime
import com.tacz.legacy.common.application.weapon.WeaponSessionService
import com.tacz.legacy.common.domain.weapon.WeaponSnapshot
import com.tacz.legacy.common.infrastructure.mc.network.LegacyNetworkHandler
import com.tacz.legacy.common.domain.weapon.WeaponInput
import net.minecraft.entity.player.EntityPlayer
import net.minecraftforge.common.MinecraftForge
import org.apache.logging.log4j.Logger

public object WeaponRuntimeMcBridge {

    @Volatile
    private var installed: Boolean = false

    @Volatile
    private var sessionService: WeaponSessionService? = null

    @Volatile
    private var tickEventHandler: WeaponPlayerTickEventHandler? = null

    @Synchronized
    public fun install(logger: Logger) {
        if (installed) {
            return
        }

        val context = WeaponMcExecutionContext()

        val behaviorEngine = WeaponPortBehaviorEngine(
            world = MinecraftWorldPort(context),
            audio = MinecraftAudioPort(context),
            particles = MinecraftParticlePort(context)
        )
        val service = WeaponSessionService(
            runtimeRegistry = WeaponRuntime.registry(),
            behaviorEngine = behaviorEngine
        )
        val orchestrator = WeaponAutoSessionOrchestrator(service)
        val handler = WeaponPlayerTickEventHandler(orchestrator, context)

        MinecraftForge.EVENT_BUS.register(handler)
        sessionService = service
        tickEventHandler = handler
        installed = true

        logger.info("[WeaponRuntimeMcBridge] Installed PlayerTick bridge with application session orchestration.")
    }

    public fun sessionServiceOrNull(): WeaponSessionService? = sessionService

    public fun dispatchInput(player: EntityPlayer, input: WeaponInput): WeaponBehaviorResult? {
        if (!installed) {
            return null
        }
        return tickEventHandler?.dispatchInput(player, input)
    }

    public fun dispatchClientInput(player: EntityPlayer, input: WeaponInput): WeaponBehaviorResult? {
        val localResult = dispatchInput(player, input)
        LegacyNetworkHandler.sendWeaponInputToServer(input)
        return localResult
    }

    public fun animationSnapshotOrNull(sessionId: String): WeaponAnimationRuntimeSnapshot? {
        if (!installed) {
            return null
        }
        return WeaponAnimationRuntimeRegistry.snapshot(sessionId)
    }

    public fun reconcileClientSessionSnapshot(
        sessionId: String,
        gunId: String,
        snapshot: WeaponSnapshot
    ): Boolean {
        if (!installed) {
            return false
        }
        val handle = sessionService?.upsertAuthoritativeSnapshot(
            sessionId = sessionId,
            gunId = gunId,
            snapshot = snapshot,
            allowFallbackDefinition = true
        )
        return handle != null
    }

    public fun clearClientSession(sessionId: String): Boolean {
        if (!installed) {
            return false
        }

        WeaponAnimationRuntimeRegistry.removeSession(sessionId)
        return sessionService?.closeSession(sessionId) == true
    }

    public fun clientSessionIdForPlayer(playerUuid: String): String =
        "player:${playerUuid.trim()}:client"

    public fun serverSessionIdForPlayer(playerUuid: String): String =
        "player:${playerUuid.trim()}"


    public fun playGunDisplaySound(
        gunId: String,
        effectName: String,
        x: Float,
        y: Float,
        z: Float,
        volume: Float,
        pitch: Float
    ) {
        val display = com.tacz.legacy.common.application.gunpack.GunDisplayRuntime.registry().snapshot().findDefinition(gunId) ?: return

        val soundId = when (effectName.lowercase()) {
            "shoot" -> display.shootSoundId
            "shoot_3p" -> display.shootThirdPersonSoundId
            "draw" -> display.drawSoundId
            "put_away" -> display.putAwaySoundId
            "dry_fire" -> display.dryFireSoundId
            "inspect" -> display.inspectSoundId
            "inspect_empty" -> display.inspectEmptySoundId
            "reload_empty" -> display.reloadEmptySoundId
            "reload_tactical" -> display.reloadTacticalSoundId
            else -> effectName
        }

        if (soundId.isNullOrBlank()) return

        com.tacz.legacy.client.sound.ClientSoundPlaybackBridge.tryPlayRawSound(
            soundId = soundId,
            x = x,
            y = y,
            z = z,
            volume = volume,
            pitch = pitch,
            category = "PLAYERS"
        )
    }
}
