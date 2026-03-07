package com.tacz.legacy.client.event

import com.tacz.legacy.TACZLegacy
import com.tacz.legacy.api.client.animation.statemachine.AnimationStateMachine
import com.tacz.legacy.api.client.animation.statemachine.LuaAnimationStateMachine
import com.tacz.legacy.api.entity.IGunOperator
import com.tacz.legacy.api.item.IGun
import com.tacz.legacy.client.animation.statemachine.GunAnimationConstant
import com.tacz.legacy.client.animation.statemachine.GunAnimationStateContext
import com.tacz.legacy.client.gameplay.LegacyClientGunAnimationDriver
import com.tacz.legacy.client.model.BedrockAnimatedModel
import com.tacz.legacy.client.model.BedrockGunModel
import com.tacz.legacy.client.model.bedrock.BedrockPart
import com.tacz.legacy.client.resource.GunDisplayInstance
import com.tacz.legacy.client.resource.TACZClientAssetManager
import com.tacz.legacy.common.resource.TACZGunPackPresentation
import com.tacz.legacy.common.resource.TACZGunPackRuntimeRegistry
import com.tacz.legacy.util.math.Easing
import com.tacz.legacy.util.math.MathUtil
import com.tacz.legacy.util.math.PerlinNoise
import com.tacz.legacy.util.math.SecondOrderDynamics
import net.minecraft.client.Minecraft
import net.minecraft.client.entity.EntityPlayerSP
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.util.EnumHand
import net.minecraft.util.EnumHandSide
import net.minecraft.util.ResourceLocation
import net.minecraft.util.math.MathHelper
import net.minecraftforge.client.event.EntityViewRenderEvent
import net.minecraftforge.client.event.RenderSpecificHandEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import java.nio.FloatBuffer

/**
 * 第一人称枪械渲染事件处理器。
 * 拦截 RenderSpecificHandEvent，如果主手持有枪械，则取消默认手部渲染并替换为
 * 基岩版模型 + 动画状态机驱动的第一人称渲染。
 *
 * Port of upstream TACZ FirstPersonRenderEvent + AnimateGeoItemRenderer.renderFirstPerson.
 */
@SideOnly(Side.CLIENT)
internal object FirstPersonRenderGunEvent {
    private var lastStateMachine: AnimationStateMachine<*>? = null
    private var lastRenderedModel: BedrockAnimatedModel? = null
    private val positioningMatrixBuffer: FloatBuffer = BufferUtils.createFloatBuffer(16)
    private var loggedFirstPersonRender = false

    // --- Procedural animation state (port of upstream FirstPersonRenderGunEvent) ---
    // SecondOrderDynamics for smooth aim transition
    private val aimingDynamics = SecondOrderDynamics(1.2f, 1.2f, 0.5f, 0f)
    // Jumping sway dynamics
    private val jumpingDynamics = SecondOrderDynamics(0.28f, 1f, 0.65f, 0f)
    private const val JUMPING_Y_SWAY = -2f
    private const val JUMPING_SWAY_TIME = 0.3f
    private const val LANDING_SWAY_TIME = 0.15f
    private var jumpingSwayProgress = 0f
    private var lastOnGround = false
    private var jumpingTimeStamp = -1L

    // Shoot recoil/sway state
    private val shootXSwayNoise = PerlinNoise(-0.2f, 0.2f, 400)
    private val shootYRotationNoise = PerlinNoise(-0.0136f, 0.0136f, 100)
    private const val SHOOT_Y_SWAY = -0.1f
    private const val SHOOT_ANIMATION_TIME = 0.3f
    @JvmStatic @Volatile
    internal var shootTimeStamp = -1L
        private set

    /**
     * Called when the local player fires. Records the timestamp so the shoot
     * procedural-recoil animation can phase in.
     */
    @JvmStatic
    fun onShoot() {
        shootTimeStamp = System.currentTimeMillis()
    }

    @SubscribeEvent
    @JvmStatic
    internal fun onRenderHand(event: RenderSpecificHandEvent) {
        val player = Minecraft.getMinecraft().player ?: return

        // Only handle main hand
        if (event.hand != EnumHand.MAIN_HAND) {
            val mainItem = player.heldItemMainhand
            if (mainItem.item is IGun) {
                event.isCanceled = true
            }
            return
        }

        val stack = event.itemStack
        val iGun = stack.item as? IGun ?: run {
            lastStateMachine = null
            lastRenderedModel = null
            return
        }
        val gunId = iGun.getGunId(stack)
        val handSide = player.primaryHand

        // Resolve display instance
        val snapshot = TACZGunPackRuntimeRegistry.getSnapshot()
        val displayId = TACZGunPackPresentation.resolveGunDisplayId(snapshot, gunId) ?: return
        val displayInstance = TACZClientAssetManager.getGunDisplayInstance(displayId) ?: return

        val model: BedrockGunModel = displayInstance.gunModel ?: return

        // Resolve texture
        val textureLoc: ResourceLocation = displayInstance.modelTexture ?: return
        val registeredTexture: ResourceLocation = TACZClientAssetManager.getTextureLocation(textureLoc) ?: return

        val partialTicks = event.partialTicks

        // --- State machine lifecycle ---
        val sm: LuaAnimationStateMachine<GunAnimationStateContext>? = displayInstance.animationStateMachine

        if (sm != lastStateMachine) {
            lastStateMachine = sm
        }

        // Initialize state machine if needed
        if (sm != null && !sm.isInitialized && sm.exitingTime < System.currentTimeMillis()) {
            LegacyClientGunAnimationDriver.prepareContext(sm, stack, displayInstance, partialTicks)
            sm.initialize()
            sm.trigger(GunAnimationConstant.INPUT_DRAW)
        }

        // Update context and state machine
        if (sm != null && sm.isInitialized) {
            LegacyClientGunAnimationDriver.prepareContext(sm, stack, displayInstance, partialTicks)
            sm.update()
        }

        // --- Compute aiming progress with SecondOrderDynamics smoothing ---
        val rawAimingProgress = IGunOperator.fromLivingEntity(player).getSynAimingProgress()
        val aimingProgress = aimingDynamics.update(rawAimingProgress)

        // --- Apply procedural gun movements (shoot sway + jump sway) to root node ---
        applyGunMovements(model, aimingProgress, partialTicks)

        // --- Render ---
        lastRenderedModel = model
        GlStateManager.pushMatrix()
        applyVanillaFirstPersonTransform(handSide, event.equipProgress, event.swingProgress)

        // Apply view bob compensation — upstream subtracts xBob/yBob lerp from
        // raw view angles; on 1.12 the equivalents are renderArmPitch / renderArmYaw.
        val xBob = player.prevRenderArmPitch + (player.renderArmPitch - player.prevRenderArmPitch) * partialTicks
        val yBob = player.prevRenderArmYaw + (player.renderArmYaw - player.prevRenderArmYaw) * partialTicks
        val xRot = player.prevRotationPitch + (player.rotationPitch - player.prevRotationPitch) * partialTicks - xBob
        val yRot = player.prevRotationYaw + (player.rotationYaw - player.prevRotationYaw) * partialTicks - yBob

        // Apply view-rotation-driven tilt to poseStack (matches upstream poseStack.mulPose step)
        GlStateManager.rotate(xRot * -0.1f, 1.0f, 0.0f, 0.0f)
        GlStateManager.rotate(yRot * -0.1f, 0.0f, 1.0f, 0.0f)

        val rootNode: BedrockPart? = model.rootNode
        if (rootNode != null) {
            val clampedXRot = Math.tanh((xRot / 25).toDouble()).toFloat() * 25f
            val clampedYRot = Math.tanh((yRot / 25).toDouble()).toFloat() * 25f
            rootNode.offsetX += clampedYRot * 0.1f / 16f / 3f
            rootNode.offsetY += -clampedXRot * 0.1f / 16f / 3f
            rootNode.additionalQuaternion.mul(
                Quaternionf().rotateX(Math.toRadians((clampedXRot * 0.05f).toDouble()).toFloat())
            )
            rootNode.additionalQuaternion.mul(
                Quaternionf().rotateY(Math.toRadians((clampedYRot * 0.05f).toDouble()).toFloat())
            )
        }

        // Move from render origin (0, 24, 0) to model origin (0, 0, 0)
        GlStateManager.translate(0.0f, 1.5f, 0.0f)
        // Bedrock models are upside-down, flip
        GlStateManager.rotate(180.0f, 0.0f, 0.0f, 1.0f)
        // Apply idle/aiming positioning + animation constraint
        applyFirstPersonPositioningTransform(model, stack, aimingProgress)
        applyAnimationConstraintTransform(model, aimingProgress)

        // Bind gun texture and render
        Minecraft.getMinecraft().textureManager.bindTexture(registeredTexture)
        displayInstance.setActiveGunTexture(registeredTexture)
        model.renderHand = true
        GlStateManager.enableLighting()
        GlStateManager.enableRescaleNormal()
        GlStateManager.enableBlend()
        GlStateManager.tryBlendFuncSeparate(
            GlStateManager.SourceFactor.SRC_ALPHA,
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
            GlStateManager.SourceFactor.ONE,
            GlStateManager.DestFactor.ZERO,
        )

        if (!loggedFirstPersonRender) {
            TACZLegacy.logger.info("[FirstPersonRenderGunEvent] First-person render hook active for {}", gunId)
            loggedFirstPersonRender = true
        }

        model.render(stack)
        model.renderHand = false

        GlStateManager.disableBlend()
        GlStateManager.disableRescaleNormal()

        // Clean animation transforms after render
        model.cleanAnimationTransform()
        model.cleanCameraAnimationTransform()

        GlStateManager.popMatrix()

        // Cancel vanilla rendering
        event.isCanceled = true
    }

    // ---- Procedural animation helpers (port of upstream) ----

    private fun applyGunMovements(model: BedrockGunModel, aimingProgress: Float, partialTicks: Float) {
        applyShootSwayAndRotation(model, aimingProgress)
        applyJumpingSway(model, partialTicks)
    }

    /**
     * Port of upstream applyShootSwayAndRotation — adds horizontal noise offset and vertical
     * kick plus yaw rotation noise to the root node when the player fires.
     */
    private fun applyShootSwayAndRotation(model: BedrockGunModel, aimingProgress: Float) {
        val rootNode = model.rootNode ?: return
        var progress = 1f - (System.currentTimeMillis() - shootTimeStamp) / (SHOOT_ANIMATION_TIME * 1000f)
        if (progress < 0f) progress = 0f
        progress = Easing.easeOutCubic(progress.toDouble()).toFloat()
        rootNode.offsetX += shootXSwayNoise.value / 16f * progress * (1f - aimingProgress)
        // Bedrock model Y axis is inverted, negate sway
        rootNode.offsetY += -SHOOT_Y_SWAY / 16f * progress * (1f - aimingProgress)
        rootNode.additionalQuaternion.mul(
            Quaternionf().rotateY(shootYRotationNoise.value * progress)
        )
    }

    /**
     * Port of upstream applyJumpingSway — smoothed vertical root node offset when
     * jumping/landing.
     */
    private fun applyJumpingSway(model: BedrockGunModel, partialTicks: Float) {
        if (jumpingTimeStamp == -1L) {
            jumpingTimeStamp = System.currentTimeMillis()
        }
        val player = Minecraft.getMinecraft().player
        if (player != null) {
            val posY = MathHelper.clampedLerp(player.lastTickPosY, player.posY, partialTicks.toDouble())
            val velocityY = ((posY - player.lastTickPosY) / partialTicks).toFloat()
            if (player.onGround) {
                if (!lastOnGround) {
                    jumpingSwayProgress = velocityY / -0.1f
                    if (jumpingSwayProgress > 1f) jumpingSwayProgress = 1f
                    lastOnGround = true
                } else {
                    jumpingSwayProgress -= (System.currentTimeMillis() - jumpingTimeStamp) / (LANDING_SWAY_TIME * 1000f)
                    if (jumpingSwayProgress < 0f) jumpingSwayProgress = 0f
                }
            } else {
                if (lastOnGround) {
                    // 0.42 is vanilla jump velocity
                    jumpingSwayProgress = velocityY / 0.42f
                    if (jumpingSwayProgress > 1f) jumpingSwayProgress = 1f
                    lastOnGround = false
                } else {
                    jumpingSwayProgress -= (System.currentTimeMillis() - jumpingTimeStamp) / (JUMPING_SWAY_TIME * 1000f)
                    if (jumpingSwayProgress < 0f) jumpingSwayProgress = 0f
                }
            }
        }
        jumpingTimeStamp = System.currentTimeMillis()
        val ySway = jumpingDynamics.update(JUMPING_Y_SWAY * jumpingSwayProgress)
        val rootNode = model.rootNode
        if (rootNode != null) {
            // Bedrock model Y axis is inverted, negate sway
            rootNode.offsetY += -ySway / 16f
        }
    }

    // ---- Animation constraint transform (port of upstream) ----

    /**
     * Port of upstream applyAnimationConstraintTransform — uses the constraint node path
     * and constraint coefficients to counteract animation-driven movement, keeping the gun
     * stable when aiming.
     */
    private fun applyAnimationConstraintTransform(model: BedrockGunModel, aimingProgress: Float) {
        val nodePath = model.constraintPath ?: return
        val constraintObj = model.constraintObject ?: return
        val weight = aimingProgress

        val originTranslation = Vector3f()
        val animatedTranslation = Vector3f()
        val rotation = Vector3f()
        getAnimationConstraintTransform(nodePath, originTranslation, animatedTranslation, rotation)

        val translationICA = constraintObj.translationConstraint
        val rotationICA = constraintObj.rotationConstraint

        // Compute inverse translation needed to counteract constraint movement
        val inverseTranslation = Vector3f(originTranslation).sub(animatedTranslation)
        // We need to transform through the current GL matrix. Since we're using
        // old-style GL, we read the current modelview matrix and apply mulDirection.
        val mvBuf = BufferUtils.createFloatBuffer(16)
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, mvBuf)
        val mvMatrix = Matrix4f()
        mvMatrix.set(mvBuf)
        inverseTranslation.mulDirection(mvMatrix)
        // Bedrock model xy are inverted for rotation, so flip
        inverseTranslation.mul(translationICA.x() - 1f, translationICA.y() - 1f, 1f - translationICA.z())

        // Compute inverse rotation
        val inverseRotation = Vector3f(rotation)
        inverseRotation.mul(rotationICA.x() - 1f, rotationICA.y() - 1f, rotationICA.z() - 1f)

        // Apply constraint rotation
        GlStateManager.translate(animatedTranslation.x(), animatedTranslation.y() + 1.5f, animatedTranslation.z())
        GlStateManager.rotate(Math.toDegrees(inverseRotation.x().toDouble()).toFloat() * weight, 1f, 0f, 0f)
        GlStateManager.rotate(Math.toDegrees(inverseRotation.y().toDouble()).toFloat() * weight, 0f, 1f, 0f)
        GlStateManager.rotate(Math.toDegrees(inverseRotation.z().toDouble()).toFloat() * weight, 0f, 0f, 1f)
        GlStateManager.translate(-animatedTranslation.x(), -animatedTranslation.y() - 1.5f, -animatedTranslation.z())

        // Apply constraint translation — modify the current modelview matrix directly
        val mvBuf2 = BufferUtils.createFloatBuffer(16)
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, mvBuf2)
        val poseMatrix = Matrix4f()
        poseMatrix.set(mvBuf2)
        poseMatrix.m30(poseMatrix.m30() - inverseTranslation.x() * weight)
        poseMatrix.m31(poseMatrix.m31() - inverseTranslation.y() * weight)
        poseMatrix.m32(poseMatrix.m32() + inverseTranslation.z() * weight)
        GL11.glLoadIdentity()
        val outBuf = BufferUtils.createFloatBuffer(16)
        poseMatrix.get(outBuf)
        outBuf.rewind()
        GL11.glMultMatrix(outBuf)
    }

    private fun getAnimationConstraintTransform(
        nodePath: List<BedrockPart>,
        originTranslation: Vector3f,
        animatedTranslation: Vector3f,
        rotation: Vector3f,
    ) {
        val animeMatrix = Matrix4f().identity()
        val originMatrix = Matrix4f().identity()
        val constrainNode = nodePath[nodePath.size - 1]
        for (part in nodePath) {
            // Animated translation (skip constraint node itself)
            if (part !== constrainNode) {
                animeMatrix.translate(part.offsetX, part.offsetY, part.offsetZ)
            }
            // Group translation
            if (part.parent != null) {
                animeMatrix.translate(part.x / 16.0f, part.y / 16.0f, part.z / 16.0f)
            } else {
                animeMatrix.translate(part.x / 16.0f, part.y / 16.0f - 1.5f, part.z / 16.0f)
            }
            // Animated rotation (skip constraint node itself)
            if (part !== constrainNode) {
                animeMatrix.rotate(part.additionalQuaternion)
            }
            // Group rotation
            animeMatrix.rotateZ(part.zRot)
            animeMatrix.rotateY(part.yRot)
            animeMatrix.rotateX(part.xRot)

            // Origin matrix (no animation offsets)
            if (part.parent != null) {
                originMatrix.translate(part.x / 16.0f, part.y / 16.0f, part.z / 16.0f)
            } else {
                originMatrix.translate(part.x / 16.0f, part.y / 16.0f - 1.5f, part.z / 16.0f)
            }
            originMatrix.rotateZ(part.zRot)
            originMatrix.rotateY(part.yRot)
            originMatrix.rotateX(part.xRot)
        }
        animeMatrix.getTranslation(animatedTranslation)
        originMatrix.getTranslation(originTranslation)
        val animatedRotation = MathUtil.getEulerAngles(animeMatrix)
        val originRotation = MathUtil.getEulerAngles(originMatrix)
        animatedRotation.sub(originRotation)
        rotation.set(animatedRotation.x(), animatedRotation.y(), animatedRotation.z())
    }

    @SubscribeEvent
    @JvmStatic
    internal fun onRenderTick(event: TickEvent.RenderTickEvent) {
        if (event.phase != TickEvent.Phase.START) {
            return
        }
        val mc = Minecraft.getMinecraft()
        val player = mc.player ?: return
        if (mc.gameSettings.thirdPersonView != 0) {
            LegacyClientGunAnimationDriver.visualUpdateHeldGun(player, event.renderTickTime)
        }
        LegacyClientGunAnimationDriver.visualUpdateExitingAnimation(event.renderTickTime)
    }

    private fun applyVanillaFirstPersonTransform(handSide: EnumHandSide, equipProgress: Float, swingProgress: Float) {
        val side = if (handSide == EnumHandSide.RIGHT) 1 else -1
        val swingRoot = MathHelper.sqrt(swingProgress)
        val swayX = -0.4f * MathHelper.sin(swingRoot * Math.PI.toFloat())
        val swayY = 0.2f * MathHelper.sin(swingRoot * ((Math.PI * 2.0).toFloat()))
        val swayZ = -0.2f * MathHelper.sin(swingProgress * Math.PI.toFloat())
        GlStateManager.translate(side * swayX, swayY, swayZ)
        transformSideFirstPerson(handSide, equipProgress)
        transformFirstPerson(handSide, swingProgress)
    }

    private fun transformSideFirstPerson(handSide: EnumHandSide, equipProgress: Float) {
        val side = if (handSide == EnumHandSide.RIGHT) 1 else -1
        GlStateManager.translate(side * 0.56f, -0.52f + equipProgress * -0.6f, -0.72f)
    }

    private fun transformFirstPerson(handSide: EnumHandSide, swingProgress: Float) {
        val side = if (handSide == EnumHandSide.RIGHT) 1 else -1
        val swingSin = MathHelper.sin(swingProgress * swingProgress * Math.PI.toFloat())
        GlStateManager.rotate(side * (45.0f + swingSin * -20.0f), 0.0f, 1.0f, 0.0f)
        val swingRootSin = MathHelper.sin(MathHelper.sqrt(swingProgress) * Math.PI.toFloat())
        GlStateManager.rotate(side * swingRootSin * -20.0f, 0.0f, 0.0f, 1.0f)
        GlStateManager.rotate(swingRootSin * -80.0f, 1.0f, 0.0f, 0.0f)
        GlStateManager.rotate(side * -45.0f, 0.0f, 1.0f, 0.0f)
    }

    private fun applyFirstPersonPositioningTransform(model: BedrockGunModel, stack: net.minecraft.item.ItemStack, aimingProgress: Float) {
        val transformMatrix = Matrix4f().identity()
        val idlePath = model.idleSightPath
        val aimingPath = model.resolveAimingViewPath(stack)

        val idleViewMatrix = FirstPersonRenderMatrices.buildPositioningNodeInverse(
            FirstPersonRenderMatrices.fromBedrockPath(idlePath)
        )
        val aimingViewMatrix = FirstPersonRenderMatrices.buildPositioningNodeInverse(
            FirstPersonRenderMatrices.fromBedrockPath(aimingPath)
        )
        // Apply idle positioning (weight = 1)
        MathUtil.applyMatrixLerp(transformMatrix, idleViewMatrix, transformMatrix, 1f)
        // Blend towards aiming positioning
        MathUtil.applyMatrixLerp(transformMatrix, aimingViewMatrix, transformMatrix, aimingProgress)

        GlStateManager.translate(0.0f, 1.5f, 0.0f)
        positioningMatrixBuffer.clear()
        transformMatrix.get(positioningMatrixBuffer)
        positioningMatrixBuffer.rewind()
        GL11.glMultMatrix(positioningMatrixBuffer)
        GlStateManager.translate(0.0f, -1.5f, 0.0f)
    }

    /**
     * Apply animation-driven camera rotation to the world camera.
     * Port of upstream TACZ CameraSetupEvent.applyLevelCameraAnimation.
     */
    @SubscribeEvent
    @JvmStatic
    internal fun onCameraSetup(event: EntityViewRenderEvent.CameraSetup) {
        val player = Minecraft.getMinecraft().player ?: return
        val stack = player.heldItemMainhand
        if (stack.item !is IGun) return
        val model = lastRenderedModel ?: return
        val (yaw, pitch, roll) = applyCameraAnimation(model, event.yaw, event.pitch, event.roll)
        event.yaw = yaw
        event.pitch = pitch
        event.roll = roll
    }

    /**
     * Apply camera animation from the state machine to the world camera.
     * Called from an EntityViewRenderEvent hook (e.g. CameraSetup).
     */
    internal fun applyCameraAnimation(model: BedrockAnimatedModel, yaw: Float, pitch: Float, roll: Float): Triple<Float, Float, Float> {
        val q: Quaternionf = MathUtil.multiplyQuaternion(model.cameraAnimationObject.rotationQuaternion, 1f)
        val yawDelta = Math.toDegrees(Math.asin((2 * (q.w() * q.y() - q.x() * q.z())).toDouble())).toFloat()
        val pitchDelta = Math.toDegrees(
            Math.atan2(
                (2 * (q.w() * q.x() + q.y() * q.z())).toDouble(),
                (1 - 2 * (q.x() * q.x() + q.y() * q.y())).toDouble()
            )
        ).toFloat()
        val rollDelta = Math.toDegrees(
            Math.atan2(
                (2 * (q.w() * q.z() + q.x() * q.y())).toDouble(),
                (1 - 2 * (q.y() * q.y() + q.z() * q.z())).toDouble()
            )
        ).toFloat()
        return Triple(yaw + yawDelta, pitch + pitchDelta, roll + rollDelta)
    }
}
