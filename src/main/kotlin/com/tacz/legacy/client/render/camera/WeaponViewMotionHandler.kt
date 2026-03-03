package com.tacz.legacy.client.render.camera

import com.tacz.legacy.client.input.WeaponAimInputStateRegistry
import com.tacz.legacy.client.render.item.LegacyGunItemStackRenderer
import com.tacz.legacy.common.application.gunpack.GunPackRuntime
import com.tacz.legacy.common.domain.gunpack.GunData
import com.tacz.legacy.common.infrastructure.mc.registry.item.LegacyGunItem
import com.tacz.legacy.common.infrastructure.mc.weapon.WeaponAnimationClipType
import com.tacz.legacy.common.infrastructure.mc.weapon.WeaponRuntimeMcBridge
import net.minecraft.client.Minecraft
import net.minecraft.client.entity.EntityPlayerSP
import net.minecraft.item.ItemStack
import net.minecraft.util.EnumHand
import net.minecraftforge.client.event.EntityViewRenderEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

public class WeaponViewMotionHandler {

    private var hasShotBaseline: Boolean = false
    private var lastObservedShots: Int = 0
    private var shootTimestampMillis: Long = -1L
    private var shotNoisePhase: Float = 0f
    private var recoilPitchCurve: WeaponRecoilCurve? = null
    private var recoilYawCurve: WeaponRecoilCurve? = null
    private var recoilPitchLastValue: Float = 0f
    private var recoilYawLastValue: Float = 0f

    private var lastOnGround: Boolean = false
    private var jumpingTimestampMillis: Long = -1L
    private var jumpingSwayProgress: Float = 0f
    private var jumpingSwayCurrent: Float = 0f

    private var swayTimeSeconds: Float = 0f
    private var lastUpdateNanos: Long = 0L
    private var aimingBlend: Float = 0f

    @SubscribeEvent
    public fun onRenderTick(event: TickEvent.RenderTickEvent) {
        if (event.phase != TickEvent.Phase.START) {
            return
        }

        val mc = Minecraft.getMinecraft()
        val activePlayer = activePlayerOrNull(mc)
        val dt = computeDeltaSeconds()
        val nowMillis = System.currentTimeMillis()

        if (activePlayer == null) {
            hasShotBaseline = false
            jumpingSwayProgress = 0f
            jumpingSwayCurrent = damp(jumpingSwayCurrent, JUMP_SWAY_RECOVER_DAMPING, dt)
            aimingBlend = damp(aimingBlend, AIM_BLEND_RECOVER_DAMPING, dt)
            recoilPitchCurve = null
            recoilYawCurve = null
            recoilPitchLastValue = 0f
            recoilYawLastValue = 0f
            return
        }

        val shots = resolveTotalShots(activePlayer)
        if (!hasShotBaseline) {
            lastObservedShots = shots
            hasShotBaseline = true
        } else if (shots > lastObservedShots) {
            onShotTriggered(
                shotDelta = shots - lastObservedShots,
                nowMillis = nowMillis,
                player = activePlayer,
                minecraft = mc
            )
        }
        lastObservedShots = shots

        suppressVanillaSwingAnimation(activePlayer)
        updateAimBlend(activePlayer, dt)
        updateJumpingSway(activePlayer, dt, nowMillis)
        swayTimeSeconds += dt
    }

    @SubscribeEvent
    public fun onCameraSetup(event: EntityViewRenderEvent.CameraSetup) {
        val mc = Minecraft.getMinecraft()
        if (!mc.gameSettings.viewBobbing) {
            return
        }

        val player = activePlayerOrNull(mc) ?: return

        val nowMillis = System.currentTimeMillis()
        val fireBoost = resolveFireClipBoost(player)
        val hasCurveRecoil = recoilPitchCurve != null || recoilYawCurve != null

        val shotProgress = resolveShootAnimationProgress(nowMillis)
        val shotPitch = if (hasCurveRecoil) {
            0f
        } else {
            SHOT_PITCH_DEGREES * shotProgress * (0.7f + fireBoost * 0.3f)
        }
        val shotYaw = if (hasCurveRecoil) {
            0f
        } else {
            sin((nowMillis * SHOT_YAW_NOISE_FREQ + shotNoisePhase).toDouble()).toFloat() *
                SHOT_YAW_NOISE_DEGREES * shotProgress
        }
        val shotRoll = if (hasCurveRecoil) {
            0f
        } else {
            sin((nowMillis * SHOT_ROLL_NOISE_FREQ + shotNoisePhase * 1.7f).toDouble()).toFloat() *
                SHOT_ROLL_NOISE_DEGREES * shotProgress
        }

        val movementDelta = abs(player.distanceWalkedModified - player.prevDistanceWalkedModified)
        val horizontalSpeed = kotlin.math.sqrt((player.motionX * player.motionX + player.motionZ * player.motionZ).toFloat())
        val walkingFactor = (movementDelta * 5.2f + horizontalSpeed * 2.4f).coerceIn(0f, 1f)
        val sprintBoost = if (player.isSprinting) 1.35f else 1.0f
        val movementFactor = (0.30f + walkingFactor * sprintBoost).coerceIn(0.25f, 1.45f)
        val movementFrequencyScale = if (player.isSprinting) 1.25f else 1.0f

        val aimSuppression = (1f - aimingBlend * AIM_CAMERA_SUPPRESSION).coerceIn(MIN_AIM_SUPPRESSION, 1f)

        val swayYaw = sin((swayTimeSeconds * SWAY_YAW_FREQ_HZ * movementFrequencyScale * TWO_PI).toDouble()).toFloat() *
            SWAY_YAW_DEGREES * movementFactor * aimSuppression
        val swayPitch = cos((swayTimeSeconds * SWAY_PITCH_FREQ_HZ * movementFrequencyScale * TWO_PI).toDouble()).toFloat() *
            SWAY_PITCH_DEGREES * movementFactor * aimSuppression
        val swayRoll = sin((swayTimeSeconds * SWAY_ROLL_FREQ_HZ * movementFrequencyScale * TWO_PI).toDouble()).toFloat() *
            SWAY_ROLL_DEGREES * movementFactor * aimSuppression

        val shotSuppression = (1f - aimingBlend * AIM_RECOIL_SUPPRESSION).coerceIn(MIN_AIM_SUPPRESSION, 1f)

        val partialTicks = event.renderPartialTicks.toFloat()
        val cameraAnimationDelta = LegacyGunItemStackRenderer.resolveCameraAnimationDelta(
            itemStack = player.heldItemMainhand,
            partialTicks = partialTicks
        )

        // TACZ 风格：开火冲击 + 细微呼吸摆动 + 跳跃/落地纵向摇摆
        event.pitch = event.pitch + swayPitch - (shotPitch * shotSuppression) +
            (jumpingSwayCurrent * JUMPING_PITCH_SCALE * aimSuppression) + (cameraAnimationDelta?.pitchDegrees
            ?: 0f)
        event.yaw = event.yaw + swayYaw + (shotYaw * shotSuppression) + (cameraAnimationDelta?.yawDegrees ?: 0f)
        event.roll = event.roll + swayRoll + (shotRoll * shotSuppression) + (cameraAnimationDelta?.rollDegrees ?: 0f)

        applyCurveRecoil(event, nowMillis)
    }

    private fun activePlayerOrNull(mc: Minecraft): EntityPlayerSP? {
        val player = mc.player ?: return null
        if (mc.world == null) {
            return null
        }
        if (mc.currentScreen != null) {
            return null
        }
        if (mc.gameSettings.thirdPersonView != 0) {
            return null
        }
        if (mc.renderViewEntity !== player) {
            return null
        }

        val held = player.heldItemMainhand
        if (held.isEmpty || held.item !is LegacyGunItem) {
            return null
        }

        return player
    }

    private fun resolveTotalShots(player: EntityPlayerSP): Int {
        val sessionId = sessionId(player)
        return WeaponRuntimeMcBridge.sessionServiceOrNull()
            ?.debugSnapshot(sessionId)
            ?.snapshot
            ?.totalShotsFired
            ?: 0
    }

    private fun resolveFireClipBoost(player: EntityPlayerSP): Float {
        val sessionId = sessionId(player)
        val snapshot = WeaponRuntimeMcBridge.animationSnapshotOrNull(sessionId) ?: return 0f
        if (snapshot.clip != WeaponAnimationClipType.FIRE) {
            return 0f
        }

        return (1f - snapshot.progress.coerceIn(0f, 1f)).coerceIn(0f, 1f)
    }

    private fun updateAimBlend(player: EntityPlayerSP, dt: Float) {
        val sessionId = sessionId(player)
        val target = if (WeaponAimInputStateRegistry.resolve(sessionId) == true) 1f else 0f
        val damping = if (target > aimingBlend) AIM_BLEND_IN_DAMPING else AIM_BLEND_OUT_DAMPING
        aimingBlend = approach(aimingBlend, target, dt, damping).coerceIn(0f, 1f)
    }

    private fun sessionId(player: EntityPlayerSP): String =
        WeaponRuntimeMcBridge.clientSessionIdForPlayer(player.uniqueID.toString())

    private fun onShotTriggered(
        shotDelta: Int,
        nowMillis: Long,
        player: EntityPlayerSP,
        minecraft: Minecraft
    ) {
        val clampedDelta = shotDelta.coerceIn(1, MAX_SHOT_IMPULSE_COUNT)
        shotNoisePhase += clampedDelta * SHOT_NOISE_PHASE_STEP
        shootTimestampMillis = nowMillis
        rebuildRecoilCurves(
            player = player,
            minecraft = minecraft
        )
    }

    private fun rebuildRecoilCurves(player: EntityPlayerSP, minecraft: Minecraft) {
        val mainHand = player.heldItemMainhand
        val gunData = resolveHeldGunData(mainHand) ?: run {
            recoilPitchCurve = null
            recoilYawCurve = null
            recoilPitchLastValue = 0f
            recoilYawLastValue = 0f
            return
        }

        val aimingProgress = LegacyGunItemStackRenderer.resolveFirstPersonAimingProgressForFov(
            itemStack = mainHand,
            minecraft = minecraft,
            partialTicks = 1f
        ).coerceIn(0f, 1f)
        val aimingZoom = LegacyGunItemStackRenderer.resolveAimingZoomForFov(mainHand)
        val recoilModifier = WeaponRecoilCurves.resolveRecoilModifier(
            aimingProgress = aimingProgress,
            aimingZoom = aimingZoom,
            crawlRecoilMultiplier = gunData.crawlRecoilMultiplier,
            isCrawlingLike = player.isPlayerSleeping
        )

        recoilPitchCurve = WeaponRecoilCurves.buildCurve(
            keyframes = gunData.recoil.pitch,
            modifier = recoilModifier
        )
        recoilYawCurve = WeaponRecoilCurves.buildCurve(
            keyframes = gunData.recoil.yaw,
            modifier = recoilModifier
        )
        recoilPitchLastValue = 0f
        recoilYawLastValue = 0f
    }

    private fun resolveHeldGunData(itemStack: ItemStack): GunData? {
        if (itemStack.isEmpty || itemStack.item !is LegacyGunItem) {
            return null
        }

        val gunId = itemStack.item
            .registryName
            ?.path
            ?.trim()
            ?.lowercase()
            ?.ifBlank { null }
            ?: return null

        return GunPackRuntime.registry().snapshot().findByGunId(gunId)
    }

    private fun applyCurveRecoil(event: EntityViewRenderEvent.CameraSetup, nowMillis: Long) {
        if (shootTimestampMillis < 0L) {
            return
        }

        val elapsedMillis = (nowMillis - shootTimestampMillis).coerceAtLeast(0L).toFloat()

        val pitchValue = recoilPitchCurve?.sample(elapsedMillis)
        if (pitchValue == null) {
            recoilPitchCurve = null
            recoilPitchLastValue = 0f
        } else {
            val delta = pitchValue - recoilPitchLastValue
            recoilPitchLastValue = pitchValue
            event.pitch -= delta
        }

        val yawValue = recoilYawCurve?.sample(elapsedMillis)
        if (yawValue == null) {
            recoilYawCurve = null
            recoilYawLastValue = 0f
        } else {
            val delta = yawValue - recoilYawLastValue
            recoilYawLastValue = yawValue
            event.yaw -= delta
        }
    }

    private fun resolveShootAnimationProgress(nowMillis: Long): Float {
        if (shootTimestampMillis < 0L) {
            return 0f
        }

        val elapsed = (nowMillis - shootTimestampMillis).coerceAtLeast(0L)
        val raw = 1f - elapsed.toFloat() / SHOOT_ANIMATION_TIME_MILLIS.toFloat()
        val clamped = raw.coerceIn(0f, 1f)
        return easeOutCubic(clamped)
    }

    private fun updateJumpingSway(player: EntityPlayerSP, dt: Float, nowMillis: Long) {
        if (jumpingTimestampMillis < 0L) {
            jumpingTimestampMillis = nowMillis
            lastOnGround = player.onGround
        }

        if (player.onGround) {
            if (!lastOnGround) {
                val landingStrength = ((-player.motionY).toFloat() / LANDING_VELOCITY_NORMALIZER)
                    .coerceIn(0f, 1f)
                jumpingSwayProgress = landingStrength
                lastOnGround = true
            } else {
                val elapsed = (nowMillis - jumpingTimestampMillis).coerceAtLeast(0L)
                jumpingSwayProgress -= elapsed.toFloat() / (LANDING_SWAY_TIME_SECONDS * 1000f)
            }
        } else {
            if (lastOnGround) {
                val jumpStrength = (player.motionY.toFloat() / JUMP_VELOCITY_NORMALIZER)
                    .coerceIn(0f, 1f)
                jumpingSwayProgress = jumpStrength
                lastOnGround = false
            } else {
                val elapsed = (nowMillis - jumpingTimestampMillis).coerceAtLeast(0L)
                jumpingSwayProgress -= elapsed.toFloat() / (JUMPING_SWAY_TIME_SECONDS * 1000f)
            }
        }

        jumpingSwayProgress = jumpingSwayProgress.coerceIn(0f, 1f)
        val target = JUMPING_Y_SWAY_DEGREES * jumpingSwayProgress
        jumpingSwayCurrent = approach(jumpingSwayCurrent, target, dt, JUMP_SWAY_FOLLOW_DAMPING)
        jumpingTimestampMillis = nowMillis
    }

    private fun suppressVanillaSwingAnimation(player: EntityPlayerSP) {
        if (!player.isSwingInProgress && player.swingProgress == 0f && player.prevSwingProgress == 0f) {
            return
        }

        player.isSwingInProgress = false
        player.swingProgressInt = 0
        player.swingProgress = 0f
        player.prevSwingProgress = 0f
        player.swingingHand = EnumHand.MAIN_HAND
    }

    private fun easeOutCubic(value: Float): Float {
        val x = value.coerceIn(0f, 1f)
        val p = 1f - x
        return 1f - p * p * p
    }

    private fun approach(current: Float, target: Float, dt: Float, damping: Float): Float {
        val blend = 1f - exp((-damping * dt).toDouble()).toFloat()
        return current + (target - current) * blend
    }

    private fun damp(value: Float, damping: Float, deltaSeconds: Float): Float {
        if (value == 0f) {
            return 0f
        }
        val decay = exp((-damping * deltaSeconds).toDouble()).toFloat()
        return value * decay
    }

    private fun computeDeltaSeconds(): Float {
        val now = System.nanoTime()
        val delta = if (lastUpdateNanos == 0L) {
            DEFAULT_DELTA_SECONDS
        } else {
            ((now - lastUpdateNanos).toFloat() / NANOS_PER_SECOND).coerceIn(0f, MAX_DELTA_SECONDS)
        }
        lastUpdateNanos = now
        return delta
    }

    private companion object {
        private const val NANOS_PER_SECOND: Float = 1_000_000_000f
        private const val DEFAULT_DELTA_SECONDS: Float = 1f / 20f
        private const val MAX_DELTA_SECONDS: Float = 0.25f
        private const val TWO_PI: Float = 6.2831855f

        private const val SHOOT_ANIMATION_TIME_MILLIS: Long = 300L
        private const val SHOT_PITCH_DEGREES: Float = 0.72f
        private const val SHOT_YAW_NOISE_DEGREES: Float = 0.14f
        private const val SHOT_ROLL_NOISE_DEGREES: Float = 0.10f
        private const val SHOT_YAW_NOISE_FREQ: Float = 0.021f
        private const val SHOT_ROLL_NOISE_FREQ: Float = 0.017f
        private const val SHOT_NOISE_PHASE_STEP: Float = 0.87f

        private const val SWAY_YAW_DEGREES: Float = 0.16f
        private const val SWAY_PITCH_DEGREES: Float = 0.11f
        private const val SWAY_ROLL_DEGREES: Float = 0.08f

        private const val SWAY_YAW_FREQ_HZ: Float = 0.60f
        private const val SWAY_PITCH_FREQ_HZ: Float = 0.44f
        private const val SWAY_ROLL_FREQ_HZ: Float = 0.52f

        private const val JUMPING_Y_SWAY_DEGREES: Float = -1.8f
        private const val JUMPING_PITCH_SCALE: Float = 0.35f
        private const val JUMPING_SWAY_TIME_SECONDS: Float = 0.30f
        private const val LANDING_SWAY_TIME_SECONDS: Float = 0.15f
        private const val JUMP_VELOCITY_NORMALIZER: Float = 0.42f
        private const val LANDING_VELOCITY_NORMALIZER: Float = 0.10f
        private const val JUMP_SWAY_FOLLOW_DAMPING: Float = 10f
        private const val JUMP_SWAY_RECOVER_DAMPING: Float = 14f
        private const val AIM_BLEND_IN_DAMPING: Float = 14f
        private const val AIM_BLEND_OUT_DAMPING: Float = 11f
        private const val AIM_BLEND_RECOVER_DAMPING: Float = 12f
        private const val AIM_CAMERA_SUPPRESSION: Float = 0.65f
        private const val AIM_RECOIL_SUPPRESSION: Float = 0.45f
        private const val MIN_AIM_SUPPRESSION: Float = 0.2f

        private const val MAX_SHOT_IMPULSE_COUNT: Int = 6
    }

}
