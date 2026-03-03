package com.tacz.legacy.client.render.item

import com.tacz.legacy.client.render.debug.ShellSpawnPerspective
import com.tacz.legacy.client.render.debug.ShellSpawnSource
import com.tacz.legacy.client.render.debug.WeaponConsistencyDiagnostics
import com.tacz.legacy.common.application.gunpack.GunDisplayDefinition
import com.tacz.legacy.common.application.gunpack.GunDisplayRuntime
import com.tacz.legacy.common.application.gunpack.GunDisplayStateMachineSemantics
import com.tacz.legacy.common.application.weapon.WeaponRuntime
import com.tacz.legacy.common.infrastructure.mc.weapon.WeaponAnimationClipType
import com.tacz.legacy.common.infrastructure.mc.weapon.WeaponAnimationRuntimeEvent
import com.tacz.legacy.common.infrastructure.mc.weapon.WeaponAnimationRuntimeEventType
import com.tacz.legacy.common.infrastructure.mc.weapon.WeaponRuntimeMcBridge
import net.minecraft.client.Minecraft
import net.minecraft.client.entity.AbstractClientPlayer
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.item.ItemStack
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly
import org.lwjgl.opengl.GL11
import kotlin.random.Random

@SideOnly(Side.CLIENT)
public object FirstPersonShellEjectRenderer {

    private val statesBySessionId: MutableMap<String, ShellRenderState> = linkedMapOf()

    public fun notifyContextSuspended(playerUniqueId: String?) {
        val normalized = playerUniqueId
            ?.trim()
            ?.ifBlank { null }
            ?: return
        val sessionId = WeaponRuntimeMcBridge.clientSessionIdForPlayer(normalized)
        statesBySessionId.remove(sessionId)
    }

    public fun renderForPlayer(
        player: AbstractClientPlayer,
        itemStack: ItemStack,
        partialTicks: Float
    ) {
        if (itemStack.isEmpty) {
            return
        }

        val sessionId = WeaponRuntimeMcBridge.clientSessionIdForPlayer(player.uniqueID.toString())
        val nowMillis = System.currentTimeMillis()
        val snapshot = WeaponRuntimeMcBridge.animationSnapshotOrNull(sessionId)
        WeaponConsistencyDiagnostics.observeAnimationSnapshot(sessionId, snapshot)
        val state = statesBySessionId.getOrPut(sessionId) { ShellRenderState() }
        val gunId = itemStack.item
            .registryName
            ?.path
            ?.trim()
            ?.lowercase()
            ?.ifBlank { null }
        val displayDefinition = gunId
            ?.let { normalizedGunId ->
                GunDisplayRuntime.registry().snapshot().findDefinition(normalizedGunId)
            }
        val gunScriptParams = gunId
            ?.let { normalizedGunId ->
                WeaponRuntime.registry().snapshot().findDefinition(normalizedGunId)?.scriptParams
            }
            .orEmpty()

        val clip = snapshot?.clip
        val elapsedMillis = snapshot?.elapsedMillis ?: Long.MAX_VALUE
        val restarted = hasClipElapsedRestarted(
            previousElapsedMillis = state.lastElapsedMillis,
            currentElapsedMillis = elapsedMillis,
            epsilonMillis = SHELL_RESTART_EPSILON_MILLIS
        )
        if (clip != state.lastClipType || restarted) {
            state.spawnedInCurrentFire = false
        }

        val shellEvents = resolveUnconsumedShellEjectEvents(
            transientEvents = snapshot?.transientEvents.orEmpty(),
            lastConsumedSequence = state.lastConsumedShellEventSequence
        )
        if (shellEvents.isNotEmpty()) {
            state.lastConsumedShellEventSequence = shellEvents.last().sequence
        }

        val spawnDirective = resolveShellSpawnDirective(displayDefinition, gunScriptParams, clip)
        val shouldSpawnByFallback = if (shellEvents.isEmpty()) {
            spawnDirective?.let { directive ->
                shouldSpawnShellAtTriggerInCurrentFrame(
                    previousClipType = state.lastClipType,
                    previousElapsedMillis = state.lastElapsedMillis,
                    currentClipType = clip,
                    currentElapsedMillis = elapsedMillis,
                    spawnedInCurrentCycle = state.spawnedInCurrentFire,
                    targetClipType = directive.clipType,
                    triggerMillis = directive.triggerMillis,
                    triggerWindowMillis = directive.triggerWindowMillis
                )
            } ?: false
        } else {
            false
        }

        val offsets = LegacyGunItemStackRenderer.latestFirstPersonReferenceOffsets()
        val shellAnchor = offsets.shell ?: offsets.muzzlePos
        val spawnCount = when {
            shellEvents.isNotEmpty() -> shellEvents.size
            shouldSpawnByFallback -> 1
            else -> 0
        }
        if (spawnCount > 0 && shellAnchor != null) {
            val compensatedAnchor = FirstPersonFovCompensation.applyScale(
                x = shellAnchor.x,
                y = shellAnchor.y,
                z = shellAnchor.z,
                scale = FirstPersonFovCompensation.currentScale()
            )
            val minecraft = Minecraft.getMinecraft()
            val aimingProgress = LegacyGunItemStackRenderer.resolveFirstPersonAimingProgressForFov(
                itemStack = itemStack,
                minecraft = minecraft,
                partialTicks = partialTicks
            )
            repeat(spawnCount) {
                spawnShellParticle(
                    state = state,
                    anchor = LegacyGunItemStackRenderer.FirstPersonReferenceOffset(
                        x = compensatedAnchor.x,
                        y = compensatedAnchor.y,
                        z = compensatedAnchor.z
                    ),
                    aimingProgress = aimingProgress
                )
            }
            state.spawnedInCurrentFire = true

            val spawnSource = if (shellEvents.isNotEmpty()) {
                ShellSpawnSource.EVENT
            } else {
                ShellSpawnSource.FALLBACK
            }
            WeaponConsistencyDiagnostics.recordShellSpawn(
                sessionId = sessionId,
                perspective = ShellSpawnPerspective.FIRST_PERSON,
                source = spawnSource,
                count = spawnCount
            )
        }

        val deltaMillis = resolveDeltaMillis(nowMillis = nowMillis, previousMillis = state.lastRenderAtMillis)
        state.lastRenderAtMillis = nowMillis
        simulateShells(state, deltaMillis)
        renderShells(state)

        state.lastClipType = clip
        state.lastElapsedMillis = elapsedMillis
    }

    internal fun shouldSpawnShellInCurrentFrame(
        previousClipType: WeaponAnimationClipType?,
        previousElapsedMillis: Long,
        currentClipType: WeaponAnimationClipType?,
        currentElapsedMillis: Long,
        spawnedInCurrentFire: Boolean
    ): Boolean = shouldSpawnShellAtTriggerInCurrentFrame(
        previousClipType = previousClipType,
        previousElapsedMillis = previousElapsedMillis,
        currentClipType = currentClipType,
        currentElapsedMillis = currentElapsedMillis,
        spawnedInCurrentCycle = spawnedInCurrentFire,
        targetClipType = WeaponAnimationClipType.FIRE,
        triggerMillis = 0L,
        triggerWindowMillis = SHELL_SPAWN_WINDOW_MILLIS
    )

    internal fun shouldSpawnShellAtTriggerInCurrentFrame(
        previousClipType: WeaponAnimationClipType?,
        previousElapsedMillis: Long,
        currentClipType: WeaponAnimationClipType?,
        currentElapsedMillis: Long,
        spawnedInCurrentCycle: Boolean,
        targetClipType: WeaponAnimationClipType,
        triggerMillis: Long,
        triggerWindowMillis: Long
    ): Boolean {
        if (currentClipType != targetClipType) {
            return false
        }

        val currentElapsed = currentElapsedMillis.coerceAtLeast(0L)
        val trigger = triggerMillis.coerceAtLeast(0L)
        if (currentElapsed < trigger) {
            return false
        }

        if (previousClipType != targetClipType) {
            return true
        }

        if (hasClipElapsedRestarted(previousElapsedMillis, currentElapsed, SHELL_RESTART_EPSILON_MILLIS)) {
            return true
        }

        if (spawnedInCurrentCycle) {
            return false
        }

        val window = triggerWindowMillis.coerceAtLeast(0L)
        val triggerEnd = if (Long.MAX_VALUE - trigger < window) {
            Long.MAX_VALUE
        } else {
            trigger + window
        }
        return currentElapsed <= triggerEnd
    }

    internal fun hasClipElapsedRestarted(
        previousElapsedMillis: Long,
        currentElapsedMillis: Long,
        epsilonMillis: Long
    ): Boolean {
        if (previousElapsedMillis == Long.MAX_VALUE || currentElapsedMillis == Long.MAX_VALUE) {
            return false
        }

        val epsilon = epsilonMillis.coerceAtLeast(0L)
        if (previousElapsedMillis <= epsilon) {
            return false
        }

        return currentElapsedMillis < previousElapsedMillis - epsilon
    }

    internal fun shouldSuppressFireShellSpawn(stateMachinePath: String?): Boolean {
        return shouldSuppressFireShellSpawn(stateMachinePath = stateMachinePath, stateMachineParams = emptyMap())
    }

    internal fun shouldSuppressFireShellSpawn(displayDefinition: GunDisplayDefinition?): Boolean {
        return shouldSuppressFireShellSpawn(displayDefinition, emptyMap())
    }

    internal fun shouldSuppressFireShellSpawn(
        displayDefinition: GunDisplayDefinition?,
        gunScriptParams: Map<String, Float>
    ): Boolean {
        return GunDisplayStateMachineSemantics.shouldPreferBoltCycleAfterFire(displayDefinition, gunScriptParams)
    }

    internal fun shouldSuppressFireShellSpawn(
        stateMachinePath: String?,
        stateMachineParams: Map<String, Float>
    ): Boolean {
        if (GunDisplayStateMachineSemantics.hasBoltShellEjectingTime(stateMachineParams)) {
            return true
        }

        return GunDisplayStateMachineSemantics.isManualBoltStateMachine(stateMachinePath)
    }

    internal fun resolveStateMachineParamMillis(valueSeconds: Float?): Long? {
        return GunDisplayStateMachineSemantics.resolveStateMachineParamMillis(valueSeconds)
    }

    internal fun resolveBoltShellTriggerMillis(stateMachineParams: Map<String, Float>): Long {
        return GunDisplayStateMachineSemantics.resolveBoltShellEjectingTimeMillis(stateMachineParams) ?: 0L
    }

    internal fun resolveUnconsumedShellEjectEvents(
        transientEvents: List<WeaponAnimationRuntimeEvent>,
        lastConsumedSequence: Long
    ): List<WeaponAnimationRuntimeEvent> {
        return transientEvents
            .asSequence()
            .filter { event ->
                event.type == WeaponAnimationRuntimeEventType.SHELL_EJECT && event.sequence > lastConsumedSequence
            }
            .sortedBy { event -> event.sequence }
            .toList()
    }

    private fun resolveShellSpawnDirective(
        displayDefinition: GunDisplayDefinition?,
        gunScriptParams: Map<String, Float>,
        currentClipType: WeaponAnimationClipType?
    ): ShellSpawnDirective? {
        val clipType = currentClipType ?: return null
        val params = displayDefinition?.stateMachineParams.orEmpty()

        return when (clipType) {
            WeaponAnimationClipType.FIRE -> {
                if (shouldSuppressFireShellSpawn(displayDefinition, gunScriptParams)) {
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
                    triggerMillis = resolveBoltShellTriggerMillis(params),
                    triggerWindowMillis = SHELL_TIMED_TRIGGER_WINDOW_MILLIS
                )
            }

            else -> null
        }
    }

    internal fun resolveShellScale(aimingProgress: Float): Float {
        val ads = aimingProgress.coerceIn(0f, 1f)
        return SHELL_SCALE_HIP + (SHELL_SCALE_ADS - SHELL_SCALE_HIP) * ads
    }

    internal fun integrateShellStep(
        position: ShellVec3,
        velocity: ShellVec3,
        deltaSeconds: Float,
        gravityPerSecond: Float
    ): Pair<ShellVec3, ShellVec3> {
        val dt = deltaSeconds.coerceAtLeast(0f)
        if (dt <= 0f) {
            return position to velocity
        }

        val nextVelocity = ShellVec3(
            x = velocity.x,
            y = velocity.y - gravityPerSecond * dt,
            z = velocity.z
        )
        val nextPosition = ShellVec3(
            x = position.x + nextVelocity.x * dt,
            y = position.y + nextVelocity.y * dt,
            z = position.z + nextVelocity.z * dt
        )
        return nextPosition to nextVelocity
    }

    private fun resolveDeltaMillis(nowMillis: Long, previousMillis: Long): Long {
        if (previousMillis <= 0L || nowMillis < previousMillis) {
            return DEFAULT_DELTA_MILLIS
        }
        return (nowMillis - previousMillis).coerceIn(1L, MAX_DELTA_MILLIS)
    }

    private fun spawnShellParticle(
        state: ShellRenderState,
        anchor: LegacyGunItemStackRenderer.FirstPersonReferenceOffset,
        aimingProgress: Float
    ) {
        val scale = resolveShellScale(aimingProgress)
        val speedScale = 1f - aimingProgress.coerceIn(0f, 1f) * 0.2f
        val velocity = ShellVec3(
            x = (SHELL_VELOCITY_X_MIN + Random.nextFloat() * (SHELL_VELOCITY_X_MAX - SHELL_VELOCITY_X_MIN)) * speedScale,
            y = (SHELL_VELOCITY_Y_MIN + Random.nextFloat() * (SHELL_VELOCITY_Y_MAX - SHELL_VELOCITY_Y_MIN)) * speedScale,
            z = (SHELL_VELOCITY_Z_MIN + Random.nextFloat() * (SHELL_VELOCITY_Z_MAX - SHELL_VELOCITY_Z_MIN)) * speedScale
        )

        state.liveShells += LiveShellParticle(
            position = ShellVec3(anchor.x, anchor.y, anchor.z),
            velocity = velocity,
            lifeMillis = SHELL_LIFETIME_MILLIS,
            scale = scale,
            spinDegrees = Random.nextFloat() * 360f,
            spinSpeedDegreesPerSecond = SHELL_SPIN_DEGREES_PER_SECOND
        )

        if (state.liveShells.size > MAX_LIVE_SHELLS) {
            val overflow = state.liveShells.size - MAX_LIVE_SHELLS
            repeat(overflow) {
                state.liveShells.removeAt(0)
            }
        }
    }

    private fun simulateShells(state: ShellRenderState, deltaMillis: Long) {
        if (state.liveShells.isEmpty()) {
            return
        }

        val dt = (deltaMillis.toFloat() / 1000f).coerceIn(0f, 0.25f)
        val iter = state.liveShells.iterator()
        while (iter.hasNext()) {
            val shell = iter.next()
            val (nextPos, nextVel) = integrateShellStep(
                position = shell.position,
                velocity = shell.velocity,
                deltaSeconds = dt,
                gravityPerSecond = SHELL_GRAVITY_PER_SECOND
            )
            shell.position = nextPos
            shell.velocity = nextVel
            shell.lifeMillis -= deltaMillis
            shell.spinDegrees += shell.spinSpeedDegreesPerSecond * dt
            if (shell.lifeMillis <= 0L) {
                iter.remove()
            }
        }
    }

    private fun renderShells(state: ShellRenderState) {
        if (state.liveShells.isEmpty()) {
            return
        }

        GlStateManager.pushMatrix()
        try {
            GlStateManager.disableLighting()
            GlStateManager.disableTexture2D()
            GlStateManager.disableCull()
            GlStateManager.enableBlend()
            GlStateManager.tryBlendFuncSeparate(
                GL11.GL_SRC_ALPHA,
                GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE,
                GL11.GL_ZERO
            )

            state.liveShells.forEach { shell ->
                val alpha = (shell.lifeMillis.toFloat() / SHELL_LIFETIME_MILLIS.toFloat()).coerceIn(0f, 1f)
                drawShell(shell, alpha)
            }
        } finally {
            GlStateManager.disableBlend()
            GlStateManager.enableCull()
            GlStateManager.enableTexture2D()
            GlStateManager.enableLighting()
            GlStateManager.color(1f, 1f, 1f, 1f)
            GlStateManager.popMatrix()
        }
    }

    private fun drawShell(shell: LiveShellParticle, alpha: Float) {
        val halfWidth = shell.scale * 0.5f
        val halfHeight = shell.scale

        GlStateManager.pushMatrix()
        try {
            GlStateManager.translate(shell.position.x.toDouble(), shell.position.y.toDouble(), shell.position.z.toDouble())
            GlStateManager.rotate(shell.spinDegrees, 0f, 0f, 1f)

            GL11.glBegin(GL11.GL_QUADS)
            GL11.glColor4f(SHELL_COLOR_R, SHELL_COLOR_G, SHELL_COLOR_B, alpha * SHELL_MAX_ALPHA)
            GL11.glVertex3f(-halfWidth, -halfHeight, 0f)
            GL11.glVertex3f(halfWidth, -halfHeight, 0f)
            GL11.glVertex3f(halfWidth, halfHeight, 0f)
            GL11.glVertex3f(-halfWidth, halfHeight, 0f)
            GL11.glEnd()
        } finally {
            GlStateManager.popMatrix()
        }
    }

    internal data class ShellVec3(
        val x: Float,
        val y: Float,
        val z: Float
    )

    private data class LiveShellParticle(
        var position: ShellVec3,
        var velocity: ShellVec3,
        var lifeMillis: Long,
        val scale: Float,
        var spinDegrees: Float,
        val spinSpeedDegreesPerSecond: Float
    )

    private data class ShellRenderState(
        var lastClipType: WeaponAnimationClipType? = null,
        var lastElapsedMillis: Long = Long.MAX_VALUE,
        var spawnedInCurrentFire: Boolean = false,
        var lastConsumedShellEventSequence: Long = 0L,
        var lastRenderAtMillis: Long = 0L,
        val liveShells: MutableList<LiveShellParticle> = mutableListOf()
    )

    private data class ShellSpawnDirective(
        val clipType: WeaponAnimationClipType,
        val triggerMillis: Long,
        val triggerWindowMillis: Long
    )

    private const val SHELL_SPAWN_WINDOW_MILLIS: Long = 45L
    private const val SHELL_TIMED_TRIGGER_WINDOW_MILLIS: Long = 220L
    private const val SHELL_RESTART_EPSILON_MILLIS: Long = 2L
    private const val DEFAULT_DELTA_MILLIS: Long = 16L
    private const val MAX_DELTA_MILLIS: Long = 100L

    private const val SHELL_LIFETIME_MILLIS: Long = 420L
    private const val MAX_LIVE_SHELLS: Int = 12
    private const val SHELL_GRAVITY_PER_SECOND: Float = 1.9f
    private const val SHELL_SPIN_DEGREES_PER_SECOND: Float = 850f

    private const val SHELL_VELOCITY_X_MIN: Float = 0.45f
    private const val SHELL_VELOCITY_X_MAX: Float = 0.95f
    private const val SHELL_VELOCITY_Y_MIN: Float = 0.28f
    private const val SHELL_VELOCITY_Y_MAX: Float = 0.62f
    private const val SHELL_VELOCITY_Z_MIN: Float = -0.20f
    private const val SHELL_VELOCITY_Z_MAX: Float = 0.08f

    private const val SHELL_SCALE_HIP: Float = 0.02f
    private const val SHELL_SCALE_ADS: Float = 0.015f

    private const val SHELL_COLOR_R: Float = 0.92f
    private const val SHELL_COLOR_G: Float = 0.76f
    private const val SHELL_COLOR_B: Float = 0.34f
    private const val SHELL_MAX_ALPHA: Float = 0.95f
}
