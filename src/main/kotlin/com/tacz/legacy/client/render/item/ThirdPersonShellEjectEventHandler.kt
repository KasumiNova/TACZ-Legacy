package com.tacz.legacy.client.render.item

import com.tacz.legacy.client.render.debug.ShellSpawnPerspective
import com.tacz.legacy.client.render.debug.ShellSpawnSource
import com.tacz.legacy.client.render.debug.WeaponConsistencyDiagnostics
import com.tacz.legacy.common.application.gunpack.GunDisplayDefinition
import com.tacz.legacy.common.application.gunpack.GunDisplayRuntime
import com.tacz.legacy.common.application.gunpack.GunDisplayStateMachineSemantics
import com.tacz.legacy.common.infrastructure.mc.registry.item.LegacyGunItem
import com.tacz.legacy.common.infrastructure.mc.weapon.WeaponAnimationClipType
import com.tacz.legacy.common.infrastructure.mc.weapon.WeaponAnimationRuntimeEvent
import com.tacz.legacy.common.infrastructure.mc.weapon.WeaponRuntimeMcBridge
import net.minecraft.client.Minecraft
import net.minecraft.client.entity.AbstractClientPlayer
import net.minecraft.util.EnumParticleTypes
import net.minecraft.world.World
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

@SideOnly(Side.CLIENT)
public class ThirdPersonShellEjectEventHandler {

    private val statesBySessionId: MutableMap<String, ThirdPersonShellState> = linkedMapOf()

    @SubscribeEvent
    public fun onClientTick(event: TickEvent.ClientTickEvent) {
        if (event.phase != TickEvent.Phase.END) {
            return
        }

        val minecraft = Minecraft.getMinecraft()
        val player = minecraft.player as? AbstractClientPlayer ?: run {
            statesBySessionId.clear()
            return
        }
        val world = minecraft.world ?: run {
            statesBySessionId.clear()
            return
        }

        if (minecraft.gameSettings.thirdPersonView == 0) {
            notifyContextSuspended(player.uniqueID.toString())
            return
        }

        val mainHand = player.heldItemMainhand
        if (mainHand.isEmpty || mainHand.item !is LegacyGunItem) {
            notifyContextSuspended(player.uniqueID.toString())
            return
        }

        val sessionId = WeaponRuntimeMcBridge.clientSessionIdForPlayer(player.uniqueID.toString())
        val snapshot = WeaponRuntimeMcBridge.animationSnapshotOrNull(sessionId)
        WeaponConsistencyDiagnostics.observeAnimationSnapshot(sessionId, snapshot)
        val state = statesBySessionId.getOrPut(sessionId) { ThirdPersonShellState() }

        if (!state.primedFromSnapshot) {
            state.lastConsumedShellEventSequence = resolveInitialLastConsumedSequence(snapshot?.transientEvents.orEmpty())
            state.lastClipType = snapshot?.clip
            state.lastElapsedMillis = snapshot?.elapsedMillis ?: Long.MAX_VALUE
            state.primedFromSnapshot = true
            return
        }

        val clip = snapshot?.clip
        val elapsedMillis = snapshot?.elapsedMillis ?: Long.MAX_VALUE
        val restarted = FirstPersonShellEjectRenderer.hasClipElapsedRestarted(
            previousElapsedMillis = state.lastElapsedMillis,
            currentElapsedMillis = elapsedMillis,
            epsilonMillis = SHELL_RESTART_EPSILON_MILLIS
        )
        if (clip != state.lastClipType || restarted) {
            state.spawnedInCurrentCycle = false
        }

        val shellEvents = FirstPersonShellEjectRenderer.resolveUnconsumedShellEjectEvents(
            transientEvents = snapshot?.transientEvents.orEmpty(),
            lastConsumedSequence = state.lastConsumedShellEventSequence
        )
        if (shellEvents.isNotEmpty()) {
            state.lastConsumedShellEventSequence = shellEvents.last().sequence
        }

        val gunId = mainHand.item
            .registryName
            ?.path
            ?.trim()
            ?.lowercase()
            ?.ifBlank { null }
        val displayDefinition = gunId
            ?.let { normalizedGunId ->
                GunDisplayRuntime.registry().snapshot().findDefinition(normalizedGunId)
            }

        val spawnDirective = resolveShellSpawnDirective(displayDefinition, clip)
        val shouldSpawnByFallback = if (shellEvents.isEmpty()) {
            spawnDirective?.let { directive ->
                FirstPersonShellEjectRenderer.shouldSpawnShellAtTriggerInCurrentFrame(
                    previousClipType = state.lastClipType,
                    previousElapsedMillis = state.lastElapsedMillis,
                    currentClipType = clip,
                    currentElapsedMillis = elapsedMillis,
                    spawnedInCurrentCycle = state.spawnedInCurrentCycle,
                    targetClipType = directive.clipType,
                    triggerMillis = directive.triggerMillis,
                    triggerWindowMillis = directive.triggerWindowMillis
                )
            } ?: false
        } else {
            false
        }

        val spawnCount = when {
            shellEvents.isNotEmpty() -> shellEvents.size
            shouldSpawnByFallback -> 1
            else -> 0
        }
        repeat(spawnCount) {
            spawnShellParticle(world, player)
        }
        if (spawnCount > 0) {
            state.spawnedInCurrentCycle = true

            val spawnSource = if (shellEvents.isNotEmpty()) {
                ShellSpawnSource.EVENT
            } else {
                ShellSpawnSource.FALLBACK
            }
            WeaponConsistencyDiagnostics.recordShellSpawn(
                sessionId = sessionId,
                perspective = ShellSpawnPerspective.THIRD_PERSON,
                source = spawnSource,
                count = spawnCount
            )
        }

        state.lastClipType = clip
        state.lastElapsedMillis = elapsedMillis
    }

    public fun notifyContextSuspended(playerUniqueId: String?) {
        val normalized = playerUniqueId
            ?.trim()
            ?.ifBlank { null }
            ?: return
        val sessionId = WeaponRuntimeMcBridge.clientSessionIdForPlayer(normalized)
        statesBySessionId.remove(sessionId)
    }

    internal fun resolveInitialLastConsumedSequence(
        transientEvents: List<WeaponAnimationRuntimeEvent>
    ): Long {
        return transientEvents
            .asSequence()
            .filter { event -> event.type == com.tacz.legacy.common.infrastructure.mc.weapon.WeaponAnimationRuntimeEventType.SHELL_EJECT }
            .map { event -> event.sequence }
            .maxOrNull()
            ?: 0L
    }

    internal fun resolveThirdPersonShellAnchor(
        originX: Double,
        originY: Double,
        originZ: Double,
        eyeHeight: Float,
        yawDegrees: Float
    ): Vec3d {
        val yawRadians = Math.toRadians(yawDegrees.toDouble())
        val forwardX = -sin(yawRadians)
        val forwardZ = cos(yawRadians)
        val rightX = cos(yawRadians)
        val rightZ = sin(yawRadians)

        return Vec3d(
            x = originX + rightX * SHELL_ANCHOR_RIGHT_OFFSET + forwardX * SHELL_ANCHOR_FORWARD_OFFSET,
            y = originY + eyeHeight.toDouble() + SHELL_ANCHOR_Y_FROM_EYE,
            z = originZ + rightZ * SHELL_ANCHOR_RIGHT_OFFSET + forwardZ * SHELL_ANCHOR_FORWARD_OFFSET
        )
    }

    private fun resolveShellSpawnDirective(
        displayDefinition: GunDisplayDefinition?,
        currentClipType: WeaponAnimationClipType?
    ): ShellSpawnDirective? {
        val clipType = currentClipType ?: return null
        val params = displayDefinition?.stateMachineParams.orEmpty()

        return when (clipType) {
            WeaponAnimationClipType.FIRE -> {
                if (FirstPersonShellEjectRenderer.shouldSuppressFireShellSpawn(displayDefinition)) {
                    null
                } else {
                    ShellSpawnDirective(
                        clipType = WeaponAnimationClipType.FIRE,
                        triggerMillis = 0L,
                        triggerWindowMillis = SHELL_SPAWN_WINDOW_MILLIS
                    )
                }
            }

            WeaponAnimationClipType.RELOAD -> {
                GunDisplayStateMachineSemantics.resolveIntroShellEjectingTimeMillis(params)
                    ?.let { introMillis ->
                        ShellSpawnDirective(
                            clipType = WeaponAnimationClipType.RELOAD,
                            triggerMillis = introMillis,
                            triggerWindowMillis = SHELL_TIMED_TRIGGER_WINDOW_MILLIS
                        )
                    }
            }

            WeaponAnimationClipType.BOLT -> {
                ShellSpawnDirective(
                    clipType = WeaponAnimationClipType.BOLT,
                    triggerMillis = FirstPersonShellEjectRenderer.resolveBoltShellTriggerMillis(params),
                    triggerWindowMillis = SHELL_TIMED_TRIGGER_WINDOW_MILLIS
                )
            }

            else -> null
        }
    }

    private fun spawnShellParticle(world: World, player: AbstractClientPlayer) {
        val anchor = resolveThirdPersonShellAnchor(
            originX = player.posX,
            originY = player.posY,
            originZ = player.posZ,
            eyeHeight = player.eyeHeight,
            yawDegrees = player.rotationYawHead
        )
        val velocity = resolveThirdPersonShellVelocity(player.rotationYawHead)

        world.spawnParticle(
            EnumParticleTypes.CRIT,
            anchor.x,
            anchor.y,
            anchor.z,
            velocity.x,
            velocity.y,
            velocity.z
        )
        world.spawnParticle(
            EnumParticleTypes.SMOKE_NORMAL,
            anchor.x,
            anchor.y,
            anchor.z,
            velocity.x * 0.25,
            velocity.y * 0.15,
            velocity.z * 0.25
        )
    }

    private fun resolveThirdPersonShellVelocity(yawDegrees: Float): Vec3d {
        val yawRadians = Math.toRadians(yawDegrees.toDouble())
        val forwardX = -sin(yawRadians)
        val forwardZ = cos(yawRadians)
        val rightX = cos(yawRadians)
        val rightZ = sin(yawRadians)

        val lateral = SHELL_VELOCITY_RIGHT_MIN + Random.nextDouble() * (SHELL_VELOCITY_RIGHT_MAX - SHELL_VELOCITY_RIGHT_MIN)
        val forward = SHELL_VELOCITY_FORWARD_MIN + Random.nextDouble() * (SHELL_VELOCITY_FORWARD_MAX - SHELL_VELOCITY_FORWARD_MIN)
        val upward = SHELL_VELOCITY_UP_MIN + Random.nextDouble() * (SHELL_VELOCITY_UP_MAX - SHELL_VELOCITY_UP_MIN)

        return Vec3d(
            x = rightX * lateral + forwardX * forward,
            y = upward,
            z = rightZ * lateral + forwardZ * forward
        )
    }

    internal data class Vec3d(
        val x: Double,
        val y: Double,
        val z: Double
    )

    private data class ThirdPersonShellState(
        var lastClipType: WeaponAnimationClipType? = null,
        var lastElapsedMillis: Long = Long.MAX_VALUE,
        var spawnedInCurrentCycle: Boolean = false,
        var lastConsumedShellEventSequence: Long = 0L,
        var primedFromSnapshot: Boolean = false
    )

    private data class ShellSpawnDirective(
        val clipType: WeaponAnimationClipType,
        val triggerMillis: Long,
        val triggerWindowMillis: Long
    )

    private companion object {
        private const val SHELL_RESTART_EPSILON_MILLIS: Long = 2L
        private const val SHELL_SPAWN_WINDOW_MILLIS: Long = 45L
        private const val SHELL_TIMED_TRIGGER_WINDOW_MILLIS: Long = 220L

        private const val SHELL_ANCHOR_RIGHT_OFFSET: Double = 0.24
        private const val SHELL_ANCHOR_FORWARD_OFFSET: Double = 0.15
        private const val SHELL_ANCHOR_Y_FROM_EYE: Double = -0.30

        private const val SHELL_VELOCITY_RIGHT_MIN: Double = 0.08
        private const val SHELL_VELOCITY_RIGHT_MAX: Double = 0.18
        private const val SHELL_VELOCITY_FORWARD_MIN: Double = 0.02
        private const val SHELL_VELOCITY_FORWARD_MAX: Double = 0.09
        private const val SHELL_VELOCITY_UP_MIN: Double = 0.07
        private const val SHELL_VELOCITY_UP_MAX: Double = 0.15
    }
}