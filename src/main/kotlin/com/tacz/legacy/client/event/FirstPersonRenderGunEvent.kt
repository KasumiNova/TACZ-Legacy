package com.tacz.legacy.client.event

import com.tacz.legacy.TACZLegacy
import com.tacz.legacy.api.client.animation.statemachine.AnimationStateMachine
import com.tacz.legacy.api.client.animation.statemachine.LuaAnimationStateMachine
import com.tacz.legacy.api.client.other.KeepingItemRenderer
import com.tacz.legacy.api.entity.IGunOperator
import com.tacz.legacy.api.item.IAttachment
import com.tacz.legacy.api.item.IGun
import com.tacz.legacy.api.item.attachment.AttachmentType
import com.tacz.legacy.client.animation.statemachine.GunAnimationConstant
import com.tacz.legacy.client.animation.statemachine.GunAnimationStateContext
import com.tacz.legacy.client.animation.screen.RefitTransform
import com.tacz.legacy.client.gameplay.LegacyClientGunAnimationDriver
import com.tacz.legacy.client.model.BedrockAnimatedModel
import com.tacz.legacy.client.model.BedrockGunModel
import com.tacz.legacy.client.model.bedrock.BedrockPart
import com.tacz.legacy.client.model.functional.BeamRenderer
import com.tacz.legacy.client.model.functional.MuzzleFlashRender
import com.tacz.legacy.client.model.functional.ShellRender
import com.tacz.legacy.client.renderer.bloom.TACZBloomBridge
import com.tacz.legacy.client.resource.GunDisplayInstance
import com.tacz.legacy.client.resource.TACZClientAssetManager
import com.tacz.legacy.client.resource.index.ClientAttachmentIndex
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
import org.joml.Vector4f
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import java.nio.FloatBuffer
import java.nio.IntBuffer
import kotlin.math.abs
import kotlin.math.tan

/**
 * 第一人称枪械渲染事件处理器。
 * 拦截 RenderSpecificHandEvent，如果主手持有枪械，则取消默认手部渲染并替换为
 * 基岩版模型 + 动画状态机驱动的第一人称渲染。
 *
 * Port of upstream TACZ FirstPersonRenderEvent + AnimateGeoItemRenderer.renderFirstPerson.
 */
@SideOnly(Side.CLIENT)
internal object FirstPersonRenderGunEvent {
    private data class PreparedFirstPersonRenderContext(
        val frameId: Long,
        val gunId: ResourceLocation,
        val displayId: ResourceLocation,
        val stack: net.minecraft.item.ItemStack,
        val displayInstance: GunDisplayInstance,
        val model: BedrockGunModel,
        val registeredTexture: ResourceLocation,
        val partialTicks: Float,
        val aimingProgress: Float,
        val refitScreenOpeningProgress: Float,
    )

    private data class RootNodeRenderState(
        val offsetX: Float,
        val offsetY: Float,
        val offsetZ: Float,
        val additionalQuaternion: Quaternionf,
    )

    private data class ResolvedAimingView(
        val path: List<BedrockPart>?,
        val viewIndex: Int,
        val transitionKey: String?,
    )

    private val tracerDebugEnabled: Boolean
        get() = java.lang.Boolean.getBoolean("tacz.tracerDebug") ||
            java.lang.Boolean.parseBoolean(System.getProperty("tacz.focusedSmoke.tracerDebug", "false"))

    private var lastStateMachine: AnimationStateMachine<*>? = null
    private var lastRenderedModel: BedrockAnimatedModel? = null
    private val positioningMatrixBuffer: FloatBuffer = BufferUtils.createFloatBuffer(16)
    private val muzzleMatrixBuffer: FloatBuffer = BufferUtils.createFloatBuffer(16)
    private val muzzleProjectionBuffer: FloatBuffer = BufferUtils.createFloatBuffer(16)
    private val muzzleViewportBuffer: IntBuffer = BufferUtils.createIntBuffer(16)
    private var loggedFirstPersonRender = false
    private var cachedMuzzleRenderOffset: Vector3f? = null
    private var cachedCameraPitch: Float? = null
    private var cachedCameraYaw: Float? = null
    private var cachedMuzzleCameraPitch: Float? = null
    private var cachedMuzzleCameraYaw: Float? = null
    private var cachedMuzzleFrameId: Long? = null
    private var cachedMuzzleGunId: ResourceLocation? = null
    private var lastCameraAnimationLoggedShootTimestamp: Long = Long.MIN_VALUE
    private var renderFrameId: Long = 0L
    private var preparedRenderContext: PreparedFirstPersonRenderContext? = null
    private val positioningFallbackWarnings = hashSetOf<String>()
    private val exitingFirstPersonUpdateLogs = hashSetOf<String>()
    private val firstPersonBloomLogs = hashSetOf<String>()
    private val tracerDebugMuzzleLogs = hashSetOf<String>()

    // --- Procedural animation state (port of upstream FirstPersonRenderGunEvent) ---
    // SecondOrderDynamics for smooth aim transition
    private val aimingDynamics = SecondOrderDynamics(1.2f, 1.2f, 0.5f, 0f)
    // Refit opening smoothing
    private val refitOpeningDynamics = SecondOrderDynamics(1f, 1.2f, 0.5f, 0f)
    private var switchViewDynamics: SecondOrderDynamics? = null
    private var oldAimingViewMatrix: Matrix4f? = null
    private var oldViewIndex = 0f
    private var currentViewIndex = -1
    private var scopeViewTransitionKey: String? = null
    // Jumping sway dynamics
    private val jumpingDynamics = SecondOrderDynamics(0.28f, 1f, 0.65f, 0f)
    private const val JUMPING_Y_SWAY = -2f
    private const val JUMPING_SWAY_TIME = 0.3f
    private const val LANDING_SWAY_TIME = 0.15f
    private var jumpingSwayProgress = 0f
    private var lastPreparedAimingProgress: Float = 0.0f
    private var lastOnGround = false
    private var jumpingTimeStamp = -1L


    @JvmStatic
    fun getPreparedAimingProgress(): Float? = preparedRenderContext?.aimingProgress

    @JvmStatic
    fun getLastPreparedAimingProgress(): Float = lastPreparedAimingProgress
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
        MuzzleFlashRender.onShoot()
    }

    @JvmStatic
    internal fun getCachedMuzzleRenderOffset(): Vector3f? = cachedMuzzleRenderOffset?.let(::Vector3f)

    @JvmStatic
    internal fun getCachedCameraPitch(): Float? = cachedCameraPitch

    @JvmStatic
    internal fun getCachedCameraYaw(): Float? = cachedCameraYaw

    @JvmStatic
    internal fun getCachedMuzzleCameraPitch(): Float? = cachedMuzzleCameraPitch

    @JvmStatic
    internal fun getCachedMuzzleCameraYaw(): Float? = cachedMuzzleCameraYaw

    @JvmStatic
    internal fun getCachedMuzzleFrameId(): Long? = cachedMuzzleFrameId

    @JvmStatic
    internal fun getCachedMuzzleGunId(): ResourceLocation? = cachedMuzzleGunId

    @SubscribeEvent
    @JvmStatic
    internal fun onRenderHand(event: RenderSpecificHandEvent) {
        val player = Minecraft.getMinecraft().player ?: return
        val renderedMainItem = KeepingItemRenderer.getRenderer()?.currentItem ?: player.heldItemMainhand

        // Only handle main hand
        if (event.hand != EnumHand.MAIN_HAND) {
            if (renderedMainItem.item is IGun) {
                event.isCanceled = true
            }
            return
        }

        val prepared = prepareFirstPersonRenderContext(event.partialTicks) ?: return
        lastRenderedModel = prepared.model
        renderPreparedFirstPersonModel(prepared, bloomOnly = false, inlineBloom = true)
        prepared.model.cleanAnimationTransform()
        preparedRenderContext = null
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
        renderFrameId += 1L
        preparedRenderContext?.model?.cleanAnimationTransform()
        preparedRenderContext = null
        TACZBloomBridge.beginRenderFrame()
        val mc = Minecraft.getMinecraft()
        val player = mc.player ?: return
        val renderedMainItem = KeepingItemRenderer.getRenderer()?.currentItem ?: player.heldItemMainhand
        if (mc.gameSettings.thirdPersonView == 0 && renderedMainItem.item !is IGun) {
            lastRenderedModel = null
            cachedMuzzleRenderOffset = null
            cachedMuzzleCameraPitch = null
            cachedMuzzleCameraYaw = null
            cachedMuzzleFrameId = null
            resetScopeViewTransition()
            lastPreparedAimingProgress = 0.0f
        }
        if (mc.gameSettings.thirdPersonView != 0) {
            resetScopeViewTransition()
            lastPreparedAimingProgress = 0.0f
            LegacyClientGunAnimationDriver.visualUpdateHeldGun(player, event.renderTickTime)
        }
        LegacyClientGunAnimationDriver.visualUpdateExitingAnimation(event.renderTickTime)
    }

    private fun prepareFirstPersonRenderContext(partialTicks: Float): PreparedFirstPersonRenderContext? {
        preparedRenderContext?.let { cached ->
            if (cached.frameId == renderFrameId) {
                return cached
            }
        }

        val player = Minecraft.getMinecraft().player ?: return null
        val stack = KeepingItemRenderer.getRenderer()?.currentItem ?: player.heldItemMainhand
        val iGun = stack.item as? IGun ?: run {
            lastStateMachine = null
            lastRenderedModel = null
            cachedMuzzleRenderOffset = null
            cachedMuzzleCameraPitch = null
            cachedMuzzleCameraYaw = null
            cachedMuzzleFrameId = null
            resetScopeViewTransition()
            lastPreparedAimingProgress = 0.0f
            return null
        }
        val gunId = iGun.getGunId(stack)

        val snapshot = TACZGunPackRuntimeRegistry.getSnapshot()
        val displayId = TACZGunPackPresentation.resolveGunDisplayId(snapshot, gunId) ?: return null
        val displayInstance = TACZClientAssetManager.getGunDisplayInstance(displayId) ?: return null
        val model: BedrockGunModel = displayInstance.gunModel ?: return null

        val textureLoc: ResourceLocation = displayInstance.modelTexture ?: return null
        val registeredTexture: ResourceLocation = TACZClientAssetManager.getTextureLocation(textureLoc) ?: return null

        val sm: LuaAnimationStateMachine<GunAnimationStateContext>? = displayInstance.animationStateMachine
        val now = System.currentTimeMillis()

        if (sm != lastStateMachine) {
            lastStateMachine = sm
        }

        if (sm != null) {
            LegacyClientGunAnimationDriver.prepareContext(sm, stack, displayInstance, partialTicks)
            if (!sm.isInitialized && sm.exitingTime < now) {
                sm.initialize()
                sm.trigger(GunAnimationConstant.INPUT_DRAW)
            } else if (!sm.isInitialized && sm.exitingTime >= now) {
                logFocusedSmokeExitingFirstPersonUpdate(gunId, sm.exitingTime - now)
            }
            sm.update()
        }

        val refitScreenOpeningProgress = refitOpeningDynamics.update(RefitTransform.getOpeningProgress())
        val rawAimingProgress = IGunOperator.fromLivingEntity(player).getSynAimingProgress()
        val aimingProgress = aimingDynamics.update(rawAimingProgress)
        lastPreparedAimingProgress = aimingProgress
        applyGunMovements(model, aimingProgress, partialTicks)

        return PreparedFirstPersonRenderContext(
            frameId = renderFrameId,
            gunId = gunId,
            displayId = displayId,
            stack = stack,
            displayInstance = displayInstance,
            model = model,
            registeredTexture = registeredTexture,
            partialTicks = partialTicks,
            aimingProgress = aimingProgress,
            refitScreenOpeningProgress = refitScreenOpeningProgress,
        ).also {
            preparedRenderContext = it
        }
    }

    private fun renderPreparedFirstPersonModel(
        prepared: PreparedFirstPersonRenderContext,
        bloomOnly: Boolean,
        inlineBloom: Boolean = false,
    ) {
        val player = Minecraft.getMinecraft().player ?: return
        val model = prepared.model
        val gunId = prepared.gunId
        val partialTicks = prepared.partialTicks

        GlStateManager.pushMatrix()
        val xBob = player.prevRenderArmPitch + (player.renderArmPitch - player.prevRenderArmPitch) * partialTicks
        val yBob = player.prevRenderArmYaw + (player.renderArmYaw - player.prevRenderArmYaw) * partialTicks
        val xRot = player.prevRotationPitch + (player.rotationPitch - player.prevRotationPitch) * partialTicks - xBob
        val yRot = player.prevRotationYaw + (player.rotationYaw - player.prevRotationYaw) * partialTicks - yBob

        GlStateManager.rotate(xRot * -0.1f, 1.0f, 0.0f, 0.0f)
        GlStateManager.rotate(yRot * -0.1f, 0.0f, 1.0f, 0.0f)

        val rootNode: BedrockPart? = model.rootNode
        val rootSnapshot = rootNode?.let {
            RootNodeRenderState(it.offsetX, it.offsetY, it.offsetZ, Quaternionf(it.additionalQuaternion))
        }
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

        try {
            GlStateManager.translate(0.0f, 1.5f, 0.0f)
            GlStateManager.rotate(180.0f, 0.0f, 0.0f, 1.0f)
            applyFirstPersonPositioningTransform(
                model,
                prepared.stack,
                gunId,
                prepared.aimingProgress,
                prepared.refitScreenOpeningProgress,
            )
            applyAnimationConstraintTransform(model, prepared.aimingProgress * (1.0f - prepared.refitScreenOpeningProgress))

            Minecraft.getMinecraft().textureManager.bindTexture(prepared.registeredTexture)
            prepared.displayInstance.setActiveGunTexture(prepared.registeredTexture)
            val renderHand = model.renderHand
            model.renderHand = RefitTransform.getOpeningProgress() == 0.0f

            if (!bloomOnly) {
                val muzzleFlashRender = model.muzzleFlashRender
                if (muzzleFlashRender != null) {
                    MuzzleFlashRender.isSelf = true
                    ShellRender.isSelf = true
                    val gunDisplay = TACZClientAssetManager.getGunDisplay(prepared.displayId)
                    muzzleFlashRender.setActiveMuzzleFlash(gunDisplay?.getMuzzleFlash())
                }
            }

            GlStateManager.enableLighting()
            GlStateManager.enableRescaleNormal()
            GlStateManager.enableBlend()
            GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO,
            )

            try {
                if (!bloomOnly && !loggedFirstPersonRender) {
                    TACZLegacy.logger.info("[FirstPersonRenderGunEvent] First-person render hook active for {}", gunId)
                    loggedFirstPersonRender = true
                }

                if (bloomOnly) {
                    logFocusedSmokeFirstPersonBloom(gunId)
                }

                if (!bloomOnly) {
                    val previousBeamContext = BeamRenderer.pushRenderContext(BeamRenderer.RenderContext.FIRST_PERSON)
                    try {
                        model.render(prepared.stack)
                    } finally {
                        BeamRenderer.popRenderContext(previousBeamContext)
                    }
                    cacheMuzzleRenderOffset(model, partialTicks)
                    if (inlineBloom) {
                        val renderedInlineBloom = TACZBloomBridge.renderInlineFirstPersonBloom(prepared.registeredTexture) {
                            model.renderBloom(prepared.stack)
                        }
                        if (renderedInlineBloom) {
                            logFocusedSmokeFirstPersonBloom(gunId)
                        }
                    }
                } else {
                    model.renderBloom(prepared.stack)
                }
            } finally {
                model.renderHand = renderHand
                if (!bloomOnly) {
                    MuzzleFlashRender.isSelf = false
                    ShellRender.isSelf = false
                }
                GlStateManager.disableBlend()
                GlStateManager.disableRescaleNormal()
            }
        } finally {
            restoreRootNodeRenderState(rootNode, rootSnapshot)
            GlStateManager.popMatrix()
        }
    }

    private fun restoreRootNodeRenderState(rootNode: BedrockPart?, snapshot: RootNodeRenderState?) {
        if (rootNode == null || snapshot == null) {
            return
        }
        rootNode.offsetX = snapshot.offsetX
        rootNode.offsetY = snapshot.offsetY
        rootNode.offsetZ = snapshot.offsetZ
        rootNode.additionalQuaternion.set(snapshot.additionalQuaternion)
    }

    private fun applyFirstPersonPositioningTransform(
        model: BedrockGunModel,
        stack: net.minecraft.item.ItemStack,
        gunId: ResourceLocation,
        aimingProgress: Float,
        refitScreenOpeningProgress: Float,
    ) {
        val rawIdlePath = FirstPersonRenderMatrices.fromBedrockPath(model.idleSightPath)
        val resolvedAimingView = resolveAimingView(model, stack, gunId)
        val rawAimingPath = FirstPersonRenderMatrices.fromBedrockPath(resolvedAimingView.path)
        val resolvedPaths = FirstPersonRenderMatrices.resolvePositioningPaths(rawIdlePath, rawAimingPath)
        logPositioningFallbacks(gunId, resolvedPaths)

        val idlePath = resolvedPaths.idlePath
            ?: failFirstPersonPositioning(gunId, "Missing both idle_view and aiming first-person positioning paths")
        val aimingPath = resolvedPaths.aimingPath
            ?: failFirstPersonPositioning(gunId, "Missing both idle_view and aiming first-person positioning paths")

        val baseBlendWeight = (1.0f - refitScreenOpeningProgress).coerceIn(0.0f, 1.0f)
        val clampedAimingProgress = aimingProgress.coerceIn(0.0f, 1.0f)
        val idleViewMatrix = FirstPersonRenderMatrices.buildPositioningNodeInverse(idlePath)
        val aimingViewMatrix = buildScopeAwareAimingMatrix(gunId, aimingPath, resolvedAimingView)
        val refitTransformProgress = Easing.easeOutCubic(RefitTransform.getTransformProgress().toDouble()).toFloat()
        val fromRefitMatrix = FirstPersonRenderMatrices.buildPositioningNodeInverse(
            FirstPersonRenderMatrices.fromBedrockPath(model.getRefitAttachmentViewPath(RefitTransform.getOldTransformType())),
        )
        val toRefitMatrix = FirstPersonRenderMatrices.buildPositioningNodeInverse(
            FirstPersonRenderMatrices.fromBedrockPath(model.getRefitAttachmentViewPath(RefitTransform.getCurrentTransformType())),
        )

        fun applyRefitPositioning(matrix: Matrix4f) {
            applyFiniteMatrixLerp(
                gunId = gunId,
                fromMatrix = matrix,
                toMatrix = fromRefitMatrix,
                resultMatrix = matrix,
                alpha = refitScreenOpeningProgress.coerceIn(0.0f, 1.0f),
                reason = "refit opening positioning lerp",
            )
            applyFiniteMatrixLerp(
                gunId = gunId,
                fromMatrix = matrix,
                toMatrix = toRefitMatrix,
                resultMatrix = matrix,
                alpha = (refitScreenOpeningProgress * refitTransformProgress).coerceIn(0.0f, 1.0f),
                reason = "refit focus positioning lerp",
            )
        }

        fun buildStagedFallbackMatrix(): Matrix4f {
            val fallbackMatrix = Matrix4f().identity()
            applyFiniteMatrixLerp(
                gunId = gunId,
                fromMatrix = fallbackMatrix,
                toMatrix = idleViewMatrix,
                resultMatrix = fallbackMatrix,
                alpha = baseBlendWeight,
                reason = "staged idle positioning lerp",
            )
            applyFiniteMatrixLerp(
                gunId = gunId,
                fromMatrix = fallbackMatrix,
                toMatrix = aimingViewMatrix,
                resultMatrix = fallbackMatrix,
                alpha = (baseBlendWeight * clampedAimingProgress).coerceIn(0.0f, 1.0f),
                reason = "staged aiming positioning lerp",
            )
            applyRefitPositioning(fallbackMatrix)
            return fallbackMatrix
        }

        val baseAimingMatrix = when {
            clampedAimingProgress <= 0.0f -> idleViewMatrix
            clampedAimingProgress >= 1.0f -> aimingViewMatrix
            else -> FirstPersonRenderMatrices.interpolateMatrix(idleViewMatrix, aimingViewMatrix, clampedAimingProgress)
        }
        val transformMatrix = Matrix4f().identity()
        if (FirstPersonRenderMatrices.isFinite(baseAimingMatrix)) {
            applyFiniteMatrixLerp(
                gunId = gunId,
                fromMatrix = transformMatrix,
                toMatrix = baseAimingMatrix,
                resultMatrix = transformMatrix,
                alpha = baseBlendWeight,
                reason = "primary base positioning lerp",
            )
            applyRefitPositioning(transformMatrix)
        } else {
            logPositioningFallback(
                gunId,
                "primary aiming interpolation produced non-finite matrix, falling back to staged lerp",
            )
            transformMatrix.set(buildStagedFallbackMatrix())
        }

        if (!FirstPersonRenderMatrices.isFinite(transformMatrix)) {
            logPositioningFallback(
                gunId,
                "final first-person positioning matrix was non-finite, retrying with staged lerp fallback",
            )
            transformMatrix.set(buildStagedFallbackMatrix())
        }
        if (!FirstPersonRenderMatrices.isFinite(transformMatrix)) {
            failFirstPersonPositioning(gunId, "Computed non-finite first-person positioning matrix after staged fallback")
        }

        GlStateManager.translate(0.0f, 1.5f, 0.0f)
        positioningMatrixBuffer.clear()
        transformMatrix.get(positioningMatrixBuffer)
        positioningMatrixBuffer.rewind()
        GL11.glMultMatrix(positioningMatrixBuffer)
        GlStateManager.translate(0.0f, -1.5f, 0.0f)
    }

    private fun resolveAimingView(
        model: BedrockGunModel,
        stack: net.minecraft.item.ItemStack,
        gunId: ResourceLocation,
    ): ResolvedAimingView {
        val iGun = stack.item as? IGun ?: return ResolvedAimingView(model.ironSightPath, 0, null)
        var scopeItem = iGun.getAttachment(stack, AttachmentType.SCOPE)
        if (scopeItem.isEmpty) {
            scopeItem = iGun.getBuiltinAttachment(stack, AttachmentType.SCOPE)
        }
        if (scopeItem.isEmpty) {
            return ResolvedAimingView(model.ironSightPath, 0, null)
        }

        val scopePosPath = model.scopePosPath ?: return ResolvedAimingView(model.ironSightPath, 0, null)
        val iAttachment = IAttachment.getIAttachmentOrNull(scopeItem) ?: return ResolvedAimingView(scopePosPath, 0, null)
        val attachmentIndex: ClientAttachmentIndex = TACZClientAssetManager.getAttachmentIndex(iAttachment.getAttachmentId(scopeItem))
            ?: return ResolvedAimingView(scopePosPath, 0, null)
        val attachmentModel = attachmentIndex.attachmentModel ?: return ResolvedAimingView(scopePosPath, 0, null)
        val views = attachmentIndex.views
        val viewIndex = FirstPersonRenderMatrices.resolveScopeViewSwitchIndex(views, iAttachment.getZoomNumber(scopeItem))
        val transitionKey = if (views.size > 1) {
            "$gunId|${iAttachment.getAttachmentId(scopeItem)}|${views.joinToString(",")}"
        } else {
            null
        }

        if (transitionKey != null && scopeViewTransitionKey != transitionKey) {
            resetScopeViewTransition()
        }
        val selectedViewIndex = if (transitionKey != null && currentViewIndex != -1) currentViewIndex else viewIndex
        val scopeViewPath = attachmentModel.getScopeViewPath(selectedViewIndex)
        if (scopeViewPath.isNullOrEmpty()) {
            return ResolvedAimingView(scopePosPath, viewIndex, null)
        }
        return ResolvedAimingView(
            path = ArrayList<BedrockPart>(scopePosPath.size + scopeViewPath.size).apply {
                addAll(scopePosPath)
                addAll(scopeViewPath)
            },
            viewIndex = viewIndex,
            transitionKey = transitionKey,
        )
    }

    private fun buildScopeAwareAimingMatrix(
        gunId: ResourceLocation,
        aimingPath: List<FirstPersonRenderMatrices.PositioningNode>?,
        resolvedAimingView: ResolvedAimingView,
    ): Matrix4f {
        val aimingMatrix = FirstPersonRenderMatrices.buildPositioningNodeInverse(aimingPath)
        val transitionKey = resolvedAimingView.transitionKey ?: run {
            resetScopeViewTransition()
            return aimingMatrix
        }

        if (scopeViewTransitionKey != transitionKey) {
            resetScopeViewTransition()
            scopeViewTransitionKey = transitionKey
        }

        val previousAimingMatrix = oldAimingViewMatrix
        val dynamics = switchViewDynamics
        if (currentViewIndex == -1 || previousAimingMatrix == null || dynamics == null) {
            currentViewIndex = resolvedAimingView.viewIndex
            oldViewIndex = resolvedAimingView.viewIndex.toFloat()
            oldAimingViewMatrix = Matrix4f(aimingMatrix)
            switchViewDynamics = SecondOrderDynamics(0.35f, 1.2f, 0.3f, resolvedAimingView.viewIndex.toFloat())
            return aimingMatrix
        }

        val interpretedView = dynamics.update(resolvedAimingView.viewIndex.toFloat())
        val span = currentViewIndex.toFloat() - oldViewIndex
        val switchingProgress = if (abs(span) < 0.05f) {
            1.0f
        } else {
            ((interpretedView - oldViewIndex) / span).coerceIn(0.0f, 1.0f)
        }

        val interpolatedMatrix = Matrix4f(aimingMatrix)
        val candidate = Matrix4f(interpolatedMatrix)
        MathUtil.applyMatrixLerp(aimingMatrix, previousAimingMatrix, candidate, 1.0f - switchingProgress)
        if (FirstPersonRenderMatrices.isFinite(candidate)) {
            interpolatedMatrix.set(candidate)
        } else {
            logPositioningFallback(gunId, "scope view switching interpolation produced non-finite matrix, preserving current aiming matrix")
        }

        if (currentViewIndex != resolvedAimingView.viewIndex) {
            oldAimingViewMatrix = Matrix4f(interpolatedMatrix)
            oldViewIndex = interpretedView
            currentViewIndex = resolvedAimingView.viewIndex
        }
        return interpolatedMatrix
    }

    private fun resetScopeViewTransition() {
        switchViewDynamics = null
        oldAimingViewMatrix = null
        oldViewIndex = 0.0f
        currentViewIndex = -1
        scopeViewTransitionKey = null
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
        cachedCameraYaw = yaw
        cachedCameraPitch = pitch
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
        if (System.getProperty("tacz.focusedSmoke", "false").toBoolean()
            && shootTimeStamp > 0L
            && lastCameraAnimationLoggedShootTimestamp != shootTimeStamp
            && (abs(yawDelta) > 0.01f || abs(pitchDelta) > 0.01f || abs(rollDelta) > 0.01f)
        ) {
            lastCameraAnimationLoggedShootTimestamp = shootTimeStamp
            TACZLegacy.logger.info(
                "[FocusedSmoke] CAMERA_ANIMATION_APPLIED shootTimestamp={} yawDelta={} pitchDelta={} rollDelta={}",
                shootTimeStamp,
                "%.3f".format(yawDelta),
                "%.3f".format(pitchDelta),
                "%.3f".format(rollDelta),
            )
        }
        model.cleanCameraAnimationTransform()
        return Triple(yaw + yawDelta, pitch + pitchDelta, roll + rollDelta)
    }

    private fun cacheMuzzleRenderOffset(model: BedrockGunModel, partialTicks: Float) {
        val muzzlePath = model.muzzleFlashPosPath
        if (muzzlePath == null || muzzlePath.isEmpty()) {
            cachedMuzzleRenderOffset = null
            cachedMuzzleCameraPitch = null
            cachedMuzzleCameraYaw = null
            cachedMuzzleFrameId = null
            cachedMuzzleGunId = null
            return
        }
        GlStateManager.pushMatrix()
        try {
            muzzlePath.forEach(BedrockPart::translateAndRotateAndScale)
            muzzleMatrixBuffer.clear()
            GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, muzzleMatrixBuffer)
            muzzleMatrixBuffer.rewind()
            val poseMatrix = Matrix4f()
            poseMatrix.set(muzzleMatrixBuffer)
            val zFovScale = resolveItemToWorldFovScale(partialTicks)
            cachedMuzzleRenderOffset = Vector3f(poseMatrix.m30(), poseMatrix.m31(), poseMatrix.m32() * zFovScale)
            cachedMuzzleCameraYaw = cachedCameraYaw
            cachedMuzzleCameraPitch = cachedCameraPitch
            cachedMuzzleFrameId = renderFrameId
            cachedMuzzleGunId = model.currentGunItem.takeIf { !it.isEmpty }?.let {
                (it.item as? IGun)?.getGunId(it)
            }
            logTracerDebugMuzzleOffset(partialTicks, zFovScale)
            logTracerDebugMuzzleScreenPosition()
        } finally {
            GlStateManager.popMatrix()
        }
    }

    private fun logTracerDebugMuzzleOffset(partialTicks: Float, zFovScale: Float) {
        if (!tracerDebugEnabled) {
            return
        }
        val offset = cachedMuzzleRenderOffset ?: return
        val gunId = cachedMuzzleGunId ?: ResourceLocation(TACZLegacy.MOD_ID, "unknown")
        val key = buildString {
            append(gunId)
            append('|')
            append("x=")
            append("%.3f".format(offset.x))
            append('|')
            append("y=")
            append("%.3f".format(offset.y))
            append('|')
            append("z=")
            append("%.3f".format(offset.z))
            append('|')
            append("pitch=")
            append("%.3f".format(cachedCameraPitch ?: 0.0f))
            append('|')
            append("yaw=")
            append("%.3f".format(cachedCameraYaw ?: 0.0f))
        }
        if (!tracerDebugMuzzleLogs.add(key)) {
            return
        }
        val itemRenderFov = FirstPersonFovHooks.getLastItemModelFov() ?: 0.0f
        val worldRenderFov = FirstPersonFovHooks.getLastWorldFov() ?: 0.0f
        TACZLegacy.logger.info(
            "[TracerDebug] MUZZLE_OFFSET gun={} offset=({},{},{}) cameraYaw={} cameraPitch={} frameId={} itemFov={} worldFov={} zScale={}",
            gunId,
            "%.4f".format(offset.x),
            "%.4f".format(offset.y),
            "%.4f".format(offset.z),
            "%.4f".format(cachedMuzzleCameraYaw ?: 0.0f),
            "%.4f".format(cachedMuzzleCameraPitch ?: 0.0f),
            cachedMuzzleFrameId ?: -1L,
            "%.4f".format(itemRenderFov),
            "%.4f".format(worldRenderFov),
            "%.4f".format(zFovScale),
        )
    }

    private fun logTracerDebugMuzzleScreenPosition() {
        if (!tracerDebugEnabled) {
            return
        }
        val gunId = cachedMuzzleGunId ?: return
        TACZLegacy.logger.info(
            "[TracerDebug] MUZZLE_SCREEN gun={} frameId={} screen={} cameraYaw={} cameraPitch={}",
            gunId,
            cachedMuzzleFrameId ?: -1L,
            projectCurrentPoint(0.0f, 0.0f, 0.0f),
            "%.4f".format(cachedMuzzleCameraYaw ?: 0.0f),
            "%.4f".format(cachedMuzzleCameraPitch ?: 0.0f),
        )
    }

    private fun projectCurrentPoint(x: Float, y: Float, z: Float): String {
        muzzleMatrixBuffer.clear()
        muzzleProjectionBuffer.clear()
        muzzleViewportBuffer.clear()
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, muzzleMatrixBuffer)
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, muzzleProjectionBuffer)
        GL11.glGetInteger(GL11.GL_VIEWPORT, muzzleViewportBuffer)
        muzzleMatrixBuffer.rewind()
        muzzleProjectionBuffer.rewind()
        muzzleViewportBuffer.rewind()

        val modelView = Matrix4f().set(muzzleMatrixBuffer)
        val projection = Matrix4f().set(muzzleProjectionBuffer)
        val viewportX = muzzleViewportBuffer.get(0)
        val viewportY = muzzleViewportBuffer.get(1)
        val viewportWidth = muzzleViewportBuffer.get(2)
        val viewportHeight = muzzleViewportBuffer.get(3)

        val clip = Vector4f(x, y, z, 1.0f)
        modelView.transform(clip)
        projection.transform(clip)
        if (abs(clip.w) <= 1.0E-6f) {
            return "null"
        }
        val invW = 1.0f / clip.w
        val ndcX = clip.x * invW
        val ndcY = clip.y * invW
        val ndcZ = clip.z * invW
        val screenX = viewportX + (ndcX + 1.0f) * 0.5f * viewportWidth
        val screenY = viewportY + (ndcY + 1.0f) * 0.5f * viewportHeight
        return "(%.1f,%.1f,%.4f)".format(screenX, screenY, ndcZ)
    }

    private fun resolveItemToWorldFovScale(partialTicks: Float): Float {
        val itemRenderFov = (FirstPersonFovHooks.getLastItemModelFov() ?: return 1.0f).toDouble()
        val worldRenderFov = (FirstPersonFovHooks.getLastWorldFov() ?: return 1.0f).toDouble()
        if (itemRenderFov <= 0.0 || worldRenderFov <= 0.0) {
            return 1.0f
        }
        val worldTan = tan(Math.toRadians(worldRenderFov / 2.0))
        if (abs(worldTan) <= 1.0E-6) {
            return 1.0f
        }
        return (tan(Math.toRadians(itemRenderFov / 2.0)) / worldTan).toFloat()
    }

    private fun logPositioningFallbacks(
        gunId: ResourceLocation,
        resolvedPaths: FirstPersonRenderMatrices.ResolvedPositioningPaths,
    ) {
        if (resolvedPaths.usedIdleFallback) {
            logPositioningFallback(gunId, "idle_view missing, reusing aiming positioning path")
        }
        if (resolvedPaths.usedAimingFallback) {
            logPositioningFallback(gunId, "aiming positioning path missing, reusing idle_view path")
        }
    }

    private fun logPositioningFallback(gunId: ResourceLocation, reason: String) {
        val key = "$gunId|$reason"
        if (!positioningFallbackWarnings.add(key)) {
            return
        }
        TACZLegacy.logger.warn("[FirstPersonRenderGunEvent] {} for {}", reason, gunId)
        if (System.getProperty("tacz.focusedSmoke", "false").toBoolean()) {
            TACZLegacy.logger.info("[FocusedSmoke] POSITIONING_FALLBACK gun={} reason={}", gunId, reason)
        }
    }

    private fun applyFiniteMatrixLerp(
        gunId: ResourceLocation,
        fromMatrix: Matrix4f,
        toMatrix: Matrix4f,
        resultMatrix: Matrix4f,
        alpha: Float,
        reason: String,
    ): Boolean {
        val clampedAlpha = alpha.coerceIn(0.0f, 1.0f)
        if (!FirstPersonRenderMatrices.isFinite(fromMatrix) || !FirstPersonRenderMatrices.isFinite(toMatrix) || !FirstPersonRenderMatrices.isFinite(resultMatrix)) {
            logPositioningFallback(gunId, "$reason skipped because an input matrix was already non-finite")
            return false
        }
        val candidate = Matrix4f(resultMatrix)
        MathUtil.applyMatrixLerp(fromMatrix, toMatrix, candidate, clampedAlpha)
        if (!FirstPersonRenderMatrices.isFinite(candidate)) {
            logPositioningFallback(gunId, "$reason produced non-finite output, preserving previous matrix")
            return false
        }
        resultMatrix.set(candidate)
        return true
    }

    private fun failFirstPersonPositioning(gunId: ResourceLocation, reason: String): Nothing {
        throw IllegalStateException("[FirstPersonRenderGunEvent] $reason for $gunId")
    }

    private fun logFocusedSmokeExitingFirstPersonUpdate(gunId: ResourceLocation, remainingMs: Long) {
        if (!System.getProperty("tacz.focusedSmoke", "false").toBoolean()) {
            return
        }
        val key = "$gunId:${remainingMs.coerceAtLeast(0L) / 50L}"
        if (!exitingFirstPersonUpdateLogs.add(key)) {
            return
        }
        TACZLegacy.logger.info(
            "[FocusedSmoke] EXITING_FIRST_PERSON_UPDATE gun={} remainingMs={}",
            gunId,
            remainingMs.coerceAtLeast(0L),
        )
    }

    private fun logFocusedSmokeFirstPersonBloom(gunId: ResourceLocation) {
        if (!System.getProperty("tacz.focusedSmoke", "false").toBoolean()) {
            return
        }
        val key = gunId.toString()
        if (!firstPersonBloomLogs.add(key)) {
            return
        }
        TACZLegacy.logger.info("[FocusedSmoke] FIRST_PERSON_BLOOM_RENDERED gun={}", gunId)
    }
}
