package com.tacz.legacy.client.render.item

import com.tacz.legacy.common.infrastructure.mc.weapon.WeaponAnimationClipType
import com.tacz.legacy.common.infrastructure.mc.weapon.WeaponRuntimeMcBridge
import net.minecraft.client.Minecraft
import net.minecraft.client.entity.AbstractClientPlayer
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.item.ItemStack
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly
import org.lwjgl.opengl.GL11
import kotlin.math.sqrt

@SideOnly(Side.CLIENT)
public object FirstPersonTracerRenderer {

    private val statesBySessionId: MutableMap<String, TracerRenderState> = linkedMapOf()

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
        val state = statesBySessionId.getOrPut(sessionId) { TracerRenderState() }

        val clip = snapshot?.clip
        val elapsedMillis = snapshot?.elapsedMillis ?: Long.MAX_VALUE
        val shouldSpawn = shouldSpawnTracerInCurrentFrame(
            previousClipType = state.lastClipType,
            previousElapsedMillis = state.lastElapsedMillis,
            currentClipType = clip,
            currentElapsedMillis = elapsedMillis,
            spawnedInCurrentFire = state.spawnedInCurrentFire
        )

        if (clip != WeaponAnimationClipType.FIRE) {
            state.spawnedInCurrentFire = false
        }

        val offsets = LegacyGunItemStackRenderer.latestFirstPersonReferenceOffsets()
        val anchor = offsets.muzzlePos ?: offsets.muzzleFlash
        if (shouldSpawn && anchor != null) {
            val compensatedAnchor = FirstPersonFovCompensation.applyScale(
                x = anchor.x,
                y = anchor.y,
                z = anchor.z,
                scale = FirstPersonFovCompensation.currentScale()
            )
            val minecraft = Minecraft.getMinecraft()
            val aimingProgress = LegacyGunItemStackRenderer.resolveFirstPersonAimingProgressForFov(
                itemStack = itemStack,
                minecraft = minecraft,
                partialTicks = partialTicks
            )
            val segment = resolveTracerSegment(
                anchor = TracerVec3(compensatedAnchor.x, compensatedAnchor.y, compensatedAnchor.z),
                aimingProgress = aimingProgress
            )
            state.liveTracers += LiveTracer(
                start = segment.start,
                end = segment.end,
                lifeMillis = TRACER_LIFETIME_MILLIS
            )
            state.spawnedInCurrentFire = true
            if (state.liveTracers.size > MAX_LIVE_TRACERS) {
                val overflow = state.liveTracers.size - MAX_LIVE_TRACERS
                repeat(overflow) {
                    state.liveTracers.removeAt(0)
                }
            }
        }

        val deltaMillis = resolveDeltaMillis(nowMillis = nowMillis, previousMillis = state.lastRenderAtMillis)
        state.lastRenderAtMillis = nowMillis
        decayTracers(state, deltaMillis)
        renderTracers(state)

        state.lastClipType = clip
        state.lastElapsedMillis = elapsedMillis
    }

    internal fun shouldSpawnTracerInCurrentFrame(
        previousClipType: WeaponAnimationClipType?,
        previousElapsedMillis: Long,
        currentClipType: WeaponAnimationClipType?,
        currentElapsedMillis: Long,
        spawnedInCurrentFire: Boolean
    ): Boolean {
        if (currentClipType != WeaponAnimationClipType.FIRE) {
            return false
        }

        val currentElapsed = currentElapsedMillis.coerceAtLeast(0L)
        if (previousClipType != WeaponAnimationClipType.FIRE) {
            return true
        }

        if (currentElapsed + TRACER_RESTART_EPSILON_MILLIS < previousElapsedMillis) {
            return true
        }

        if (spawnedInCurrentFire) {
            return false
        }

        return currentElapsed <= TRACER_SPAWN_WINDOW_MILLIS
    }

    internal fun resolveTracerSegment(anchor: TracerVec3, aimingProgress: Float): TracerSegment {
        val ads = aimingProgress.coerceIn(0f, 1f)
        val length = TRACER_LENGTH_HIP + (TRACER_LENGTH_ADS - TRACER_LENGTH_HIP) * ads
        val dir = normalizeDirection(anchor)

        val start = TracerVec3(
            x = anchor.x + dir.x * TRACER_START_BIAS,
            y = anchor.y + dir.y * TRACER_START_BIAS,
            z = anchor.z + dir.z * TRACER_START_BIAS
        )
        val end = TracerVec3(
            x = start.x + dir.x * length,
            y = start.y + dir.y * length,
            z = start.z + dir.z * length
        )
        return TracerSegment(start = start, end = end)
    }

    internal fun resolveTracerAlpha(lifeMillis: Long): Float {
        val life = lifeMillis.coerceIn(0L, TRACER_LIFETIME_MILLIS)
        val t = life.toFloat() / TRACER_LIFETIME_MILLIS.toFloat()
        return (t * t * TRACER_MAX_ALPHA).coerceIn(0f, 1f)
    }

    private fun normalizeDirection(anchor: TracerVec3): TracerVec3 {
        val lengthSq = anchor.x * anchor.x + anchor.y * anchor.y + anchor.z * anchor.z
        if (lengthSq <= 1e-8f) {
            return TracerVec3(0f, 0f, -1f)
        }
        val inv = 1f / sqrt(lengthSq)
        return TracerVec3(
            x = anchor.x * inv,
            y = anchor.y * inv,
            z = anchor.z * inv
        )
    }

    private fun resolveDeltaMillis(nowMillis: Long, previousMillis: Long): Long {
        if (previousMillis <= 0L || nowMillis < previousMillis) {
            return DEFAULT_DELTA_MILLIS
        }
        return (nowMillis - previousMillis).coerceIn(1L, MAX_DELTA_MILLIS)
    }

    private fun decayTracers(state: TracerRenderState, deltaMillis: Long) {
        if (state.liveTracers.isEmpty()) {
            return
        }

        val iter = state.liveTracers.iterator()
        while (iter.hasNext()) {
            val tracer = iter.next()
            tracer.lifeMillis -= deltaMillis
            if (tracer.lifeMillis <= 0L) {
                iter.remove()
            }
        }
    }

    private fun renderTracers(state: TracerRenderState) {
        if (state.liveTracers.isEmpty()) {
            return
        }

        GlStateManager.pushMatrix()
        try {
            GlStateManager.disableLighting()
            GlStateManager.disableTexture2D()
            GlStateManager.disableCull()
            GlStateManager.disableDepth()
            GlStateManager.enableBlend()
            GlStateManager.tryBlendFuncSeparate(
                GL11.GL_SRC_ALPHA,
                GL11.GL_ONE,
                GL11.GL_ONE,
                GL11.GL_ZERO
            )

            GL11.glLineWidth(TRACER_LINE_WIDTH)
            GL11.glBegin(GL11.GL_LINES)
            state.liveTracers.forEach { tracer ->
                val alpha = resolveTracerAlpha(tracer.lifeMillis)
                GL11.glColor4f(TRACER_COLOR_R, TRACER_COLOR_G, TRACER_COLOR_B, alpha)
                GL11.glVertex3f(tracer.start.x, tracer.start.y, tracer.start.z)
                GL11.glVertex3f(tracer.end.x, tracer.end.y, tracer.end.z)
            }
            GL11.glEnd()
            GL11.glLineWidth(1f)
        } finally {
            GlStateManager.disableBlend()
            GlStateManager.enableDepth()
            GlStateManager.enableCull()
            GlStateManager.enableTexture2D()
            GlStateManager.enableLighting()
            GlStateManager.color(1f, 1f, 1f, 1f)
            GlStateManager.popMatrix()
        }
    }

    internal data class TracerVec3(
        val x: Float,
        val y: Float,
        val z: Float
    )

    internal data class TracerSegment(
        val start: TracerVec3,
        val end: TracerVec3
    )

    private data class LiveTracer(
        val start: TracerVec3,
        val end: TracerVec3,
        var lifeMillis: Long
    )

    private data class TracerRenderState(
        var lastClipType: WeaponAnimationClipType? = null,
        var lastElapsedMillis: Long = Long.MAX_VALUE,
        var spawnedInCurrentFire: Boolean = false,
        var lastRenderAtMillis: Long = 0L,
        val liveTracers: MutableList<LiveTracer> = mutableListOf()
    )

    private const val TRACER_SPAWN_WINDOW_MILLIS: Long = 30L
    private const val TRACER_RESTART_EPSILON_MILLIS: Long = 2L
    private const val TRACER_LIFETIME_MILLIS: Long = 80L
    private const val TRACER_LENGTH_HIP: Float = 1.4f
    private const val TRACER_LENGTH_ADS: Float = 1.0f
    private const val TRACER_START_BIAS: Float = 0.02f
    private const val TRACER_MAX_ALPHA: Float = 0.95f
    private const val TRACER_LINE_WIDTH: Float = 2f
    private const val TRACER_COLOR_R: Float = 1f
    private const val TRACER_COLOR_G: Float = 0.9f
    private const val TRACER_COLOR_B: Float = 0.6f
    private const val MAX_LIVE_TRACERS: Int = 8
    private const val DEFAULT_DELTA_MILLIS: Long = 16L
    private const val MAX_DELTA_MILLIS: Long = 100L
}
