package com.tacz.legacy.client.render.item

import com.tacz.legacy.api.client.animation.Animations
import com.tacz.legacy.api.client.animation.ObjectAnimation
import com.tacz.legacy.client.animation.GunAnimationControllerSession
import com.tacz.legacy.client.animation.SessionTrack
import com.tacz.legacy.client.animation.SessionTrackPlayMode
import com.tacz.legacy.client.resource.pojo.animation.bedrock.BedrockAnimationFile
import com.tacz.legacy.client.sound.SoundPlayManager
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.tacz.legacy.client.resource.serialize.AnimationKeyframesSerializer
import com.tacz.legacy.client.resource.serialize.SoundEffectKeyframesSerializer
import net.minecraft.util.ResourceLocation
import com.tacz.legacy.client.resource.pojo.animation.bedrock.AnimationKeyframes
import com.tacz.legacy.client.resource.pojo.animation.bedrock.SoundEffectKeyframes


import com.tacz.legacy.client.gui.WeaponGunsmithImmersiveRuntime
import com.tacz.legacy.client.render.texture.TaczTextureResourceResolver
import com.tacz.legacy.client.sound.TaczSoundEngine
import com.tacz.legacy.client.input.WeaponAimInputStateRegistry
import com.tacz.legacy.common.application.gunpack.GunDisplayDefinition
import com.tacz.legacy.common.application.gunpack.GunDisplayRuntime
import com.tacz.legacy.common.application.gunpack.GunPackRuntime
import com.tacz.legacy.common.infrastructure.mc.registry.item.LegacyGunItem
import com.tacz.legacy.common.infrastructure.mc.weapon.WeaponAnimationClipType
import com.tacz.legacy.common.infrastructure.mc.weapon.WeaponAnimationRuntimeSnapshot
import com.tacz.legacy.common.infrastructure.mc.weapon.WeaponRuntimeMcBridge
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.client.Minecraft
import net.minecraft.client.entity.AbstractClientPlayer
import net.minecraft.client.entity.EntityPlayerSP
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.Tessellator
import net.minecraft.client.model.ModelBiped
import net.minecraft.client.model.ModelPlayer
import net.minecraft.client.audio.ISound
import net.minecraft.client.audio.PositionedSoundRecord
import net.minecraft.client.renderer.entity.RenderPlayer
import net.minecraft.client.renderer.tileentity.TileEntityItemStackRenderer
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.EnumHandSide
import net.minecraft.util.SoundCategory
import net.minecraftforge.client.event.RenderSpecificHandEvent
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import org.lwjgl.input.Mouse
import java.nio.charset.StandardCharsets
import java.nio.FloatBuffer

@SideOnly(Side.CLIENT)
public object LegacyGunItemStackRenderer : TileEntityItemStackRenderer() {

    private val geoModelCache: MutableMap<String, LegacyGeoModel> = linkedMapOf()
    private val animationCache: MutableMap<String, LegacyAnimationSet> = linkedMapOf()
    private val renderAnimationStateBySessionId: MutableMap<String, RenderAnimationSessionState> = linkedMapOf()
    private val aimingBlendStateBySessionId: MutableMap<String, AimingBlendState> = linkedMapOf()
    private val firstPersonJumpSwayStateBySessionId: MutableMap<String, FirstPersonJumpSwayState> = linkedMapOf()
    private val firstPersonShootSwayStateBySessionId: MutableMap<String, FirstPersonShootSwayState> = linkedMapOf()
    private val clipPoseTransitionStateBySessionId: MutableMap<String, ClipPoseTransitionState> = linkedMapOf()
    private val refitViewContinuityByGunId: MutableMap<String, RefitViewContinuityCache> = linkedMapOf()
    private val animationSoundStateBySessionId: MutableMap<String, AnimationSoundPlaybackState> = linkedMapOf()
    private val animationSoundEventAvailabilityById: MutableMap<String, Boolean> = linkedMapOf()
    
    private val objectAnimationCache: MutableMap<String, List<com.tacz.legacy.api.client.animation.ObjectAnimation>> = java.util.concurrent.ConcurrentHashMap()
    private val animationSessions = java.util.concurrent.ConcurrentHashMap<String, com.tacz.legacy.client.animation.GunAnimationControllerSession>()

    @Volatile
    private var firstPersonReferenceOffsets: FirstPersonReferenceOffsets = FirstPersonReferenceOffsets.EMPTY

    private fun clientSessionId(playerUniqueId: String): String =
        WeaponRuntimeMcBridge.clientSessionIdForPlayer(playerUniqueId)

    /**
     * 由 [FirstPersonGunRenderEventHandler] 在 [RenderSpecificHandEvent] 中调用。
     * GL 矩阵此时仅包含摄像机旋转 + 手部惯性摆动，无原版物品变换。
     */
    public fun renderFirstPerson(itemStack: ItemStack, partialTicks: Float) {
        if (itemStack.isEmpty) {
            firstPersonReferenceOffsets = FirstPersonReferenceOffsets.EMPTY
            return
        }
        renderCommon(itemStack, firstPerson = true, partialTicks = partialTicks)
    }

    public fun latestFirstPersonReferenceOffsets(): FirstPersonReferenceOffsets = firstPersonReferenceOffsets

    public fun canRenderFirstPerson(
        itemStack: ItemStack,
        minecraft: Minecraft = Minecraft.getMinecraft()
    ): Boolean {
        if (itemStack.isEmpty || itemStack.item !is LegacyGunItem) {
            return false
        }

        val gunId = itemStack.item
            .registryName
            ?.path
            ?.trim()
            ?.lowercase()
            ?.ifBlank { null }
            ?: return false

        val display = GunDisplayRuntime.registry().snapshot().findDefinition(gunId) ?: return false
        return resolveGeoModel(display, minecraft) != null
    }

    public fun notifyFirstPersonGunContextSuspended(playerUniqueId: String?) {
        val normalizedPlayerId = playerUniqueId
            ?.trim()
            ?.ifBlank { null }
            ?: return
        val sessionId = clientSessionId(normalizedPlayerId)

        renderAnimationStateBySessionId[sessionId]?.let { state ->
            state.lastGunId = null
            state.drawStartedAtMillis = -1L
            state.putAwayStartedAtMillis = -1L
            state.putAwayDurationMillis = -1L
            state.putAwayRenderGunId = null
            state.activeLoopClipType = null
            state.activeLoopStartedAtMillis = 0L
        }
        clipPoseTransitionStateBySessionId.remove(sessionId)
        refitViewContinuityByGunId.clear()
        firstPersonShootSwayStateBySessionId.remove(sessionId)
        firstPersonReferenceOffsets = FirstPersonReferenceOffsets.EMPTY
    }

    public fun invalidateRuntimeCaches() {
        geoModelCache.clear()
        animationCache.clear()
        renderAnimationStateBySessionId.clear()
        aimingBlendStateBySessionId.clear()
        firstPersonJumpSwayStateBySessionId.clear()
        firstPersonShootSwayStateBySessionId.clear()
        clipPoseTransitionStateBySessionId.clear()
        refitViewContinuityByGunId.clear()
        animationSoundStateBySessionId.clear()
        animationSoundEventAvailabilityById.clear()
        LegacyBoneVisibilityPolicy.clearAttachmentCaches()
        firstPersonReferenceOffsets = FirstPersonReferenceOffsets.EMPTY
    }

    override fun renderByItem(itemStackIn: ItemStack, partialTicks: Float) {
        if (itemStackIn.isEmpty) {
            return
        }
        renderCommon(itemStackIn, firstPerson = false, partialTicks = partialTicks)
    }

    private fun renderCommon(itemStack: ItemStack, firstPerson: Boolean, partialTicks: Float) {
        val minecraft = Minecraft.getMinecraft()
        val gunId = itemStack.item
            .registryName
            ?.path
            ?.trim()
            ?.lowercase()
            ?.ifBlank { null }
            ?: return

        val display = GunDisplayRuntime.registry().snapshot().findDefinition(gunId)
        val resolvedAnimationPose = resolveAnimationPose(
            gunId = gunId,
            display = display,
            minecraft = minecraft,
            enableSoundDispatch = firstPerson,
            allowContextualFallback = firstPerson
        )
        val playbackGunId = resolvedAnimationPose?.playbackGunId ?: gunId
        val playbackDisplay = resolvedAnimationPose?.playbackDisplay ?: display
        val geoModel = resolveGeoModel(playbackDisplay, minecraft)
        val animationPose = resolvedAnimationPose?.pose
        val runtimeAnimationSnapshot = resolveRuntimeAnimationSnapshot(gunId, minecraft)
        val texture = TaczTextureResourceResolver.resolveForBind(
            rawPath = resolveRenderTexturePath(playbackDisplay),
            sourceId = playbackDisplay?.sourceId,
            fallback = DEFAULT_GUN_TEXTURE,
            minecraft = minecraft
        ) ?: DEFAULT_GUN_TEXTURE
        val handContext = if (firstPerson) {
            resolveFirstPersonHandContext(playbackGunId, texture, minecraft)
        } else {
            null
        }

        minecraft.textureManager.bindTexture(texture)

        GlStateManager.pushMatrix()
        GlStateManager.disableCull()
        GlStateManager.enableBlend()
        GlStateManager.disableLighting()
        GlStateManager.color(1f, 1f, 1f, 1f)

        if (geoModel != null) {
            renderGeoModel(
                model = geoModel,
                animationPose = animationPose,
                handContext = handContext,
                firstPerson = firstPerson,
                partialTicks = partialTicks,
                itemStack = itemStack,
                runtimeAnimationSnapshot = runtimeAnimationSnapshot
            )
        } else {
            renderFallbackQuad()
        }

        GlStateManager.enableLighting()
        GlStateManager.disableBlend()
        GlStateManager.enableCull()
        GlStateManager.popMatrix()
    }

    private fun resolveFirstPersonHandContext(
        gunId: String,
        gunTexture: ResourceLocation,
        minecraft: Minecraft
    ): FirstPersonHandContext? {
        if (!ENABLE_FIRST_PERSON_HANDS) {
            return null
        }

        if (minecraft.world == null || minecraft.currentScreen != null) {
            return null
        }

        if (minecraft.gameSettings.thirdPersonView != 0) {
            return null
        }

        val player = minecraft.player as? AbstractClientPlayer ?: return null
        if (minecraft.renderViewEntity !== player) {
            return null
        }

        val heldMainHand = player.heldItemMainhand
        if (heldMainHand.isEmpty || heldMainHand.item !is LegacyGunItem) {
            return null
        }

        val heldGunId = heldMainHand.item
            .registryName
            ?.path
            ?.trim()
            ?.lowercase()
            ?.ifBlank { null }
            ?: return null
        if (heldGunId != gunId) {
            return null
        }

        val renderPlayer = minecraft.renderManager
            .getEntityRenderObject<AbstractClientPlayer>(player) as? RenderPlayer
            ?: return null

        return FirstPersonHandContext(
            minecraft = minecraft,
            player = player,
            renderPlayer = renderPlayer,
            gunTexture = gunTexture
        )
    }

    private fun renderAnchoredHand(
        handContext: FirstPersonHandContext,
        side: EnumHandSide
    ) {
        val isRight = side == EnumHandSide.RIGHT
        if (isRight && handContext.rightRendered) {
            return
        }
        if (!isRight && handContext.leftRendered) {
            return
        }
        handContext.minecraft.textureManager.bindTexture(handContext.player.locationSkin)
        GlStateManager.pushMatrix()

        // 对齐 TACZ 上游 RightHandRender/LeftHandRender：在手部锚点再做一次 Z 轴 180°。
        // 这一步是将玩家手臂模型坐标系与枪模骨骼坐标系对齐，避免开火时出现“举手”姿态。
        GlStateManager.rotate(180f, 0f, 0f, 1f)

        GlStateManager.enableLighting()
        GlStateManager.disableCull()

        val unit = 0.0625f

        val modelPlayer = handContext.renderPlayer.mainModel
        if (modelPlayer != null) {
            val prevLeftPose = modelPlayer.leftArmPose
            val prevRightPose = modelPlayer.rightArmPose
            val prevSneak = modelPlayer.isSneak
            val prevSwing = modelPlayer.swingProgress

            modelPlayer.leftArmPose = ModelBiped.ArmPose.EMPTY
            modelPlayer.rightArmPose = ModelBiped.ArmPose.EMPTY
            modelPlayer.isSneak = false
            modelPlayer.swingProgress = 0.0f
            modelPlayer.setRotationAngles(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, unit, handContext.player)

            if (isRight) {
                modelPlayer.bipedRightArm.rotateAngleX = 0.0f
                modelPlayer.bipedRightArm.rotateAngleY = 0.0f
                modelPlayer.bipedRightArm.rotateAngleZ = 0.0f
                modelPlayer.bipedRightArm.render(unit)
                modelPlayer.bipedRightArmwear.rotateAngleX = 0.0f
                modelPlayer.bipedRightArmwear.rotateAngleY = 0.0f
                modelPlayer.bipedRightArmwear.rotateAngleZ = 0.0f
                modelPlayer.bipedRightArmwear.render(unit)
                handContext.rightRendered = true
            } else {
                modelPlayer.bipedLeftArm.rotateAngleX = 0.0f
                modelPlayer.bipedLeftArm.rotateAngleY = 0.0f
                modelPlayer.bipedLeftArm.rotateAngleZ = 0.0f
                modelPlayer.bipedLeftArm.render(unit)
                modelPlayer.bipedLeftArmwear.rotateAngleX = 0.0f
                modelPlayer.bipedLeftArmwear.rotateAngleY = 0.0f
                modelPlayer.bipedLeftArmwear.rotateAngleZ = 0.0f
                modelPlayer.bipedLeftArmwear.render(unit)
                handContext.leftRendered = true
            }

            modelPlayer.leftArmPose = prevLeftPose
            modelPlayer.rightArmPose = prevRightPose
            modelPlayer.isSneak = prevSneak
            modelPlayer.swingProgress = prevSwing
        } else {
            // 兜底：不做姿态修正，至少能渲染出来。
            if (isRight) {
                handContext.renderPlayer.renderRightArm(handContext.player)
                handContext.rightRendered = true
            } else {
                handContext.renderPlayer.renderLeftArm(handContext.player)
                handContext.leftRendered = true
            }
        }

        // 外层枪模渲染要求 disableCull + disableLighting，
        // 这里必须恢复，否则后续骨骼会被错误背面剔除。
        GlStateManager.disableCull()
        GlStateManager.disableLighting()
        GlStateManager.enableBlend()
        GlStateManager.color(1f, 1f, 1f, 1f)
        GlStateManager.popMatrix()
        handContext.minecraft.textureManager.bindTexture(handContext.gunTexture)
    }

    private fun renderGeoModel(
        model: LegacyGeoModel,
        animationPose: LegacyAnimationPose?,
        handContext: FirstPersonHandContext?,
        firstPerson: Boolean = false,
        partialTicks: Float,
        itemStack: ItemStack,
        runtimeAnimationSnapshot: WeaponAnimationRuntimeSnapshot?
    ) {
        val visibilityPolicy = LegacyBoneVisibilityPolicy.fromItemStack(itemStack, runtimeAnimationSnapshot)
        val normalizedNames = model.bonesByName.keys.map(::normalizeBoneName).toSet()
        val preferLeftHandPos = normalizedNames.contains(HAND_BONE_LEFT_POS)
        val preferRightHandPos = normalizedNames.contains(HAND_BONE_RIGHT_POS)
        val referenceOffsetCollector = if (firstPerson) linkedMapOf<String, FirstPersonReferenceOffset>() else null

        val gunId = itemStack.item
            .registryName
            ?.path
            ?.trim()
            ?.lowercase()
            ?.ifBlank { null }

        GlStateManager.pushMatrix()

        if (firstPerson) {
            val aimingProgress = resolveFirstPersonAimingProgress(itemStack, Minecraft.getMinecraft())
            val currentPlayer = handContext?.player
            if (currentPlayer != null) {
                applyFirstPersonShootSway(
                    runtimeAnimationSnapshot = runtimeAnimationSnapshot,
                    aimingProgress = aimingProgress
                )
                applyFirstPersonJumpingSway(
                    player = currentPlayer,
                    partialTicks = partialTicks,
                    gunId = gunId
                )
                applyFirstPersonHoldingSway(currentPlayer, partialTicks)
                applyFirstPersonIdleMicroJitter(
                    player = currentPlayer,
                    aimingProgress = aimingProgress
                )
            }

            // 对齐 TACZ 1.20 第一人称变换链:
            //   T(0, 1.5, 0) → Rz(180°) → T(0, 1.5, 0) → M_inv → T(0, -1.5, 0) → [bone tree]
            // 无额外缩放——模型以原生基岩比例（1像素=1/16方块）渲染。
            GlStateManager.translate(0.0, 1.5, 0.0)
            GlStateManager.rotate(180f, 0f, 0f, 1f)
            if (!applyFirstPersonPositioningBoneAlignment(model, animationPose, aimingProgress, gunId)) {
                GlStateManager.translate(0.0, 0.0, FIRST_PERSON_Z_OFFSET.toDouble())
            }
        } else {
            // TEISR（第三人称/GUI/地面）变换链：
            //   translate(0.5, 2, 0.5) → undo MC -0.5 + Bedrock Y=24 基线
            //   scale(-1, -1, 1) → 基岩版坐标翻转
            //   scale(MODEL_SCALE) around Y=1.5
            // 合并后等价于 translate(0.5, 0.5+1.5*s, 0.5) + scale(-s, -s, s)
            GlStateManager.translate(0.5, 0.5 + 1.5 * MODEL_SCALE, 0.5)
            GlStateManager.scale((-MODEL_SCALE).toDouble(), (-MODEL_SCALE).toDouble(), MODEL_SCALE.toDouble())
        }

        model.rootBones.forEach { rootName ->
            renderBone(
                model = model,
                boneName = rootName,
                depth = 0,
                isRootBone = true,
                visibilityPolicy = visibilityPolicy,
                animationPose = animationPose,
                handContext = handContext,
                preferLeftHandPos = preferLeftHandPos,
                preferRightHandPos = preferRightHandPos,
                hiddenByAncestor = false,
                referenceOffsetCollector = referenceOffsetCollector
            )
        }

        if (firstPerson) {
            firstPersonReferenceOffsets = FirstPersonReferenceOffsets(
                muzzlePos = referenceOffsetCollector?.get("muzzle_pos"),
                shell = referenceOffsetCollector?.get("shell"),
                muzzleFlash = referenceOffsetCollector?.get("muzzle_flash")
            )
        }

        GlStateManager.popMatrix()
    }

    private fun resolveFirstPersonAimingProgress(itemStack: ItemStack, minecraft: Minecraft): Float {
        val player = minecraft.player as? AbstractClientPlayer ?: return 0f
        if (minecraft.renderViewEntity !== player) {
            return 0f
        }

        val gunId = itemStack.item
            .registryName
            ?.path
            ?.trim()
            ?.lowercase()
            ?.ifBlank { null }
            ?: return 0f

        return resolveAimingProgress(
            minecraft = minecraft,
            player = player,
            gunId = gunId
        ).coerceIn(0f, 1f)
    }

    internal fun resolveFirstPersonAimingProgressForFov(
        itemStack: ItemStack,
        minecraft: Minecraft,
        @Suppress("UNUSED_PARAMETER") partialTicks: Float
    ): Float {
        return resolveFirstPersonAimingProgress(
            itemStack = itemStack,
            minecraft = minecraft
        ).coerceIn(0f, 1f)
    }

    private fun applyFirstPersonPositioningBoneAlignment(
        model: LegacyGeoModel,
        animationPose: LegacyAnimationPose?,
        aimingProgress: Float,
        gunId: String?
    ): Boolean {
        val blendWeight = resolveFirstPersonPositioningBlendWeight(aimingProgress)
        val idleBone = findFirstPersonPositioningBoneByName(model, IDLE_VIEW_BONE)
        val ironBone = findFirstPersonPositioningBoneByName(model, IRON_VIEW_BONE)
        val fallbackBone = findFirstPersonPositioningBone(model, preferIronView = blendWeight >= 0.5f)

        val fromBone = idleBone ?: ironBone ?: fallbackBone ?: return false
        val toBone = ironBone ?: idleBone ?: fallbackBone ?: fromBone

        val fromTransform = resolveFirstPersonPositioningTransform(model, fromBone, animationPose)
        val toTransform = resolveFirstPersonPositioningTransform(model, toBone, animationPose)
        var blendedTransform = if (fromBone.name == toBone.name) {
            fromTransform
        } else {
            interpolateRigidTransform(fromTransform, toTransform, blendWeight)
        }

        // 对齐 TACZ 1.20 RefitTransform：
        //   base(idle/iron) -> refit_view(overview) -> refit_<slot>_view(close-up)
        val refit = WeaponGunsmithImmersiveRuntime.resolveRefitViewStateForGun(gunId)
        if (refit == null) {
            gunId?.let { refitViewContinuityByGunId.remove(it) }
        } else {
            val opening = refit.openingProgress.coerceIn(0.0f, 1.0f)
            if (opening > 0.0f) {
                val continuity = gunId?.let(refitViewContinuityByGunId::get)
                val shouldUseCachedFrom = continuity != null &&
                    refit.transformProgress.coerceIn(0.0f, 1.0f) <= 0.0001f &&
                    (continuity.lastCurrentSlot != refit.currentSlot || continuity.lastOldSlot != refit.oldSlot)

                val fromRefitBone = findFirstPersonPositioningBoneByName(model, refitViewBoneName(refit.oldSlot))
                    ?: findFirstPersonPositioningBoneByName(model, REFIT_VIEW_BONE)
                val toRefitBone = findFirstPersonPositioningBoneByName(model, refitViewBoneName(refit.currentSlot))
                    ?: fromRefitBone

                val cachedFromTransform = if (shouldUseCachedFrom) continuity.lastTransform else null
                val fromRefitTransform = cachedFromTransform
                    ?: fromRefitBone?.let { resolveFirstPersonPositioningTransform(model, it, animationPose) }
                val toRefitTransform = toRefitBone?.let { resolveFirstPersonPositioningTransform(model, it, animationPose) }

                if (fromRefitTransform != null) {
                    blendedTransform = interpolateRigidTransform(blendedTransform, fromRefitTransform, opening)
                }

                if (toRefitTransform != null) {
                    val eased = easeOutCubic(refit.transformProgress.coerceIn(0.0f, 1.0f))
                    blendedTransform = interpolateRigidTransform(blendedTransform, toRefitTransform, opening * eased)
                }

                gunId?.let {
                    refitViewContinuityByGunId[it] = RefitViewContinuityCache(
                        lastTransform = blendedTransform,
                        lastOpeningProgress = opening,
                        lastOldSlot = refit.oldSlot,
                        lastCurrentSlot = refit.currentSlot,
                        lastTransformProgress = refit.transformProgress
                    )
                }
            } else {
                gunId?.let { refitViewContinuityByGunId.remove(it) }
            }
        }

        // TACZ 1.20: T(0, 1.5, 0) * M_inv * T(0, -1.5, 0)
        // 围绕 Y=1.5（基岩模型原点 24/16）做逆变换
        GlStateManager.translate(0.0, 1.5, 0.0)

        applyRigidTransform(blendedTransform)

        GlStateManager.translate(0.0, -1.5, 0.0)
        return true
    }

    private fun easeOutCubic(t: Float): Float {
        val clamped = t.coerceIn(0.0f, 1.0f)
        val inv = 1.0f - clamped
        return 1.0f - inv * inv * inv
    }

    private fun refitViewBoneName(slot: com.tacz.legacy.common.infrastructure.mc.weapon.WeaponAttachmentSlot?): String {
        if (slot == null) {
            return REFIT_VIEW_BONE
        }
        return "$REFIT_VIEW_PREFIX${slot.name.lowercase()}$REFIT_VIEW_SUFFIX"
    }

    internal fun resolveFirstPersonPositioningBlendWeight(aimingProgress: Float): Float {
        return aimingProgress.coerceIn(0f, 1f)
    }

    private fun applyFirstPersonHoldingSway(player: AbstractClientPlayer, partialTicks: Float) {
        val clampedPartial = partialTicks.coerceIn(0f, 1f)
        val viewPitch = lerp(player.prevRotationPitch, player.rotationPitch, clampedPartial)
        val viewYaw = lerp(player.prevRotationYaw, player.rotationYaw, clampedPartial)
        val clientPlayer = player as? EntityPlayerSP
        val armPitch = clientPlayer
            ?.let { lerp(it.prevRenderArmPitch, it.renderArmPitch, clampedPartial) }
            ?: viewPitch
        val armYaw = clientPlayer
            ?.let { lerp(it.prevRenderArmYaw, it.renderArmYaw, clampedPartial) }
            ?: viewYaw
        val sway = resolveFirstPersonHoldingSwayTransform(
            viewPitchDeltaDegrees = resolveFirstPersonSwayDeltaAngle(viewPitch, armPitch),
            viewYawDeltaDegrees = resolveFirstPersonSwayDeltaAngle(viewYaw, armYaw)
        )

        if (kotlin.math.abs(sway.preRotatePitchDegrees) > CAMERA_EPSILON_DEGREES) {
            GlStateManager.rotate(sway.preRotatePitchDegrees, 1f, 0f, 0f)
        }
        if (kotlin.math.abs(sway.preRotateYawDegrees) > CAMERA_EPSILON_DEGREES) {
            GlStateManager.rotate(sway.preRotateYawDegrees, 0f, 1f, 0f)
        }
        if (kotlin.math.abs(sway.offsetX) > CAMERA_EPSILON_RADIANS || kotlin.math.abs(sway.offsetY) > CAMERA_EPSILON_RADIANS) {
            GlStateManager.translate(sway.offsetX.toDouble(), sway.offsetY.toDouble(), 0.0)
        }
        if (kotlin.math.abs(sway.postRotatePitchDegrees) > CAMERA_EPSILON_DEGREES) {
            GlStateManager.rotate(sway.postRotatePitchDegrees, 1f, 0f, 0f)
        }
        if (kotlin.math.abs(sway.postRotateYawDegrees) > CAMERA_EPSILON_DEGREES) {
            GlStateManager.rotate(sway.postRotateYawDegrees, 0f, 1f, 0f)
        }
    }

    private fun applyFirstPersonIdleMicroJitter(
        player: AbstractClientPlayer,
        aimingProgress: Float
    ) {
        if (!ENABLE_FIRST_PERSON_IDLE_MICRO_JITTER) {
            return
        }

        val intensity = (1f - aimingProgress.coerceIn(0f, 1f)).coerceIn(0f, 1f)
        if (intensity <= 0.0001f) {
            return
        }

        val uuid = player.uniqueID
        val seed = (uuid.mostSignificantBits xor uuid.leastSignificantBits).toInt()
        val t = (System.nanoTime().toDouble() / 1_000_000_000.0 + seed * 0.0001).toFloat()

        // 呼吸感：对齐 TACZ 数据驱动 idle 动画的幅度（约 2-3° 旋转，~0.5cm 平移）。
        val yaw = kotlin.math.sin(t * 0.65f + 0.7f) * 0.55f * intensity
        val pitch = kotlin.math.sin(t * 0.50f + 1.9f) * 0.40f * intensity
        val roll = kotlin.math.sin(t * 0.80f + 2.6f) * 0.25f * intensity

        if (kotlin.math.abs(roll) > CAMERA_EPSILON_DEGREES) {
            GlStateManager.rotate(roll, 0f, 0f, 1f)
        }
        if (kotlin.math.abs(yaw) > CAMERA_EPSILON_DEGREES) {
            GlStateManager.rotate(yaw, 0f, 1f, 0f)
        }
        if (kotlin.math.abs(pitch) > CAMERA_EPSILON_DEGREES) {
            GlStateManager.rotate(pitch, 1f, 0f, 0f)
        }

        val offsetX = kotlin.math.sin(t * 0.45f + 0.2f) * 0.008f * intensity
        val offsetY = kotlin.math.sin(t * 0.35f + 1.4f) * 0.006f * intensity
        if (kotlin.math.abs(offsetX) > CAMERA_EPSILON_RADIANS || kotlin.math.abs(offsetY) > CAMERA_EPSILON_RADIANS) {
            GlStateManager.translate(offsetX.toDouble(), offsetY.toDouble(), 0.0)
        }
    }

    private fun applyFirstPersonJumpingSway(
        player: AbstractClientPlayer,
        partialTicks: Float,
        gunId: String?,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        val sessionId = clientSessionId(player.uniqueID.toString())
        val state = firstPersonJumpSwayStateBySessionId.getOrPut(sessionId) {
            FirstPersonJumpSwayState(
                progress = 0f,
                smoothedOffsetY = 0f,
                lastUpdatedMillis = nowMillis,
                lastOnGround = player.onGround,
                lastVerticalVelocity = 0f,
                lastGunId = gunId
            )
        }

        if (state.lastGunId != gunId) {
            state.progress = 0f
            state.smoothedOffsetY = 0f
            state.lastVerticalVelocity = 0f
            state.lastOnGround = player.onGround
            state.lastGunId = gunId
            state.lastUpdatedMillis = nowMillis
        }

        if (state.lastUpdatedMillis <= 0L) {
            state.lastUpdatedMillis = nowMillis
        }

        val deltaMillis = (nowMillis - state.lastUpdatedMillis)
            .coerceIn(0L, MAX_JUMP_SWAY_DELTA_MILLIS)
        state.lastUpdatedMillis = nowMillis

        val verticalVelocity = resolveVerticalVelocityPerTick(player, partialTicks)
        val targetProgress = resolveFirstPersonJumpSwayTargetProgress(
            currentProgress = state.progress,
            wasOnGround = state.lastOnGround,
            isOnGround = player.onGround,
            verticalVelocity = verticalVelocity,
            previousVerticalVelocity = state.lastVerticalVelocity,
            deltaMillis = deltaMillis
        )
        state.progress = targetProgress

        val targetOffsetY = resolveFirstPersonJumpSwayOffsetY(targetProgress)
        state.smoothedOffsetY = resolveSmoothedValueStep(
            current = state.smoothedOffsetY,
            target = targetOffsetY,
            deltaMillis = deltaMillis,
            smoothTimeSeconds = FIRST_PERSON_JUMP_SWAY_SMOOTH_TIME_SECONDS
        )

        state.lastVerticalVelocity = verticalVelocity
        state.lastOnGround = player.onGround

        if (kotlin.math.abs(state.smoothedOffsetY) > CAMERA_EPSILON_RADIANS) {
            GlStateManager.translate(0.0, state.smoothedOffsetY.toDouble(), 0.0)
        }
    }

    private fun applyFirstPersonShootSway(
        runtimeAnimationSnapshot: WeaponAnimationRuntimeSnapshot?,
        aimingProgress: Float,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        val snapshot = runtimeAnimationSnapshot ?: return

        val state = firstPersonShootSwayStateBySessionId.getOrPut(snapshot.sessionId) {
            FirstPersonShootSwayState(
                fireStartedAtMillis = -1L,
                lastObservedFireElapsedMillis = Long.MAX_VALUE,
                lastWasFireClip = false,
                lastGunId = snapshot.gunId
            )
        }

        if (state.lastGunId != snapshot.gunId) {
            state.fireStartedAtMillis = -1L
            state.lastObservedFireElapsedMillis = Long.MAX_VALUE
            state.lastWasFireClip = false
            state.lastGunId = snapshot.gunId
        }

        val currentFireElapsed = snapshot.elapsedMillis.coerceAtLeast(0L)
        if (snapshot.clip == WeaponAnimationClipType.FIRE) {
            val fireRestarted = !state.lastWasFireClip ||
                currentFireElapsed + FIRE_RESTART_EPSILON_MILLIS < state.lastObservedFireElapsedMillis
            if (fireRestarted || state.fireStartedAtMillis <= 0L) {
                state.fireStartedAtMillis = nowMillis - currentFireElapsed
            }
            state.lastObservedFireElapsedMillis = currentFireElapsed
            state.lastWasFireClip = true
        } else {
            state.lastWasFireClip = false
        }

        if (state.fireStartedAtMillis <= 0L) {
            return
        }

        val elapsedSinceFire = (nowMillis - state.fireStartedAtMillis).coerceAtLeast(0L)
        if (elapsedSinceFire >= FIRST_PERSON_SHOOT_SWAY_TIME_MILLIS) {
            return
        }

        val fireProgress = (elapsedSinceFire.toFloat() / FIRST_PERSON_SHOOT_SWAY_TIME_MILLIS.toFloat())
            .coerceIn(0f, 1f)

        val sway = resolveFirstPersonShootSwayTransform(
            fireProgress = fireProgress,
            aimingProgress = aimingProgress,
            elapsedMillis = elapsedSinceFire,
            sessionSeed = snapshot.sessionId.hashCode()
        )

        if (kotlin.math.abs(sway.offsetX) > CAMERA_EPSILON_RADIANS || kotlin.math.abs(sway.offsetY) > CAMERA_EPSILON_RADIANS) {
            GlStateManager.translate(sway.offsetX.toDouble(), sway.offsetY.toDouble(), 0.0)
        }
        if (kotlin.math.abs(sway.yawDegrees) > CAMERA_EPSILON_DEGREES) {
            GlStateManager.rotate(sway.yawDegrees, 0f, 1f, 0f)
        }
    }

    private fun resolveVerticalVelocityPerTick(player: AbstractClientPlayer, partialTicks: Float): Float {
        val clampedPartial = partialTicks.coerceIn(0f, 1f).toDouble()
        val currentY = player.prevPosY + (player.posY - player.prevPosY) * clampedPartial
        val previousY = player.prevPosY
        return (currentY - previousY).toFloat()
    }

    internal fun resolveFirstPersonHoldingSwayTransform(
        viewPitchDeltaDegrees: Float,
        viewYawDeltaDegrees: Float
    ): FirstPersonHoldingSwayTransform {
        val xRot = wrapDegrees(viewPitchDeltaDegrees)
        val yRot = wrapDegrees(viewYawDeltaDegrees)
        val normalizedPitch = normalizeFirstPersonSwayAngle(xRot)
        val normalizedYaw = normalizeFirstPersonSwayAngle(yRot)
        return FirstPersonHoldingSwayTransform(
            preRotatePitchDegrees = xRot * -FIRST_PERSON_SWAY_PRE_ROTATE_SCALE,
            preRotateYawDegrees = yRot * FIRST_PERSON_SWAY_PRE_ROTATE_SCALE,
            offsetX = normalizedYaw * FIRST_PERSON_SWAY_TRANSLATION_SCALE,
            offsetY = -normalizedPitch * FIRST_PERSON_SWAY_TRANSLATION_SCALE,
            postRotatePitchDegrees = normalizedPitch * FIRST_PERSON_SWAY_POST_ROTATE_SCALE,
            postRotateYawDegrees = normalizedYaw * FIRST_PERSON_SWAY_POST_ROTATE_SCALE
        )
    }

    internal fun resolveFirstPersonSwayDeltaAngle(viewDegrees: Float, armDegrees: Float): Float {
        return wrapDegrees(viewDegrees - armDegrees)
    }

    internal fun resolveFirstPersonShootSwayTransform(
        fireProgress: Float,
        aimingProgress: Float,
        elapsedMillis: Long,
        sessionSeed: Int
    ): FirstPersonShootSwayTransform {
        val clampedFireProgress = fireProgress.coerceIn(0f, 1f)
        val hipFactor = (1f - aimingProgress.coerceIn(0f, 1f)).coerceIn(0f, 1f)

        val intensity = easeOutCubic01(1f - clampedFireProgress)
        if (intensity <= 0f) {
            return FirstPersonShootSwayTransform.ZERO
        }

        val elapsed = elapsedMillis.coerceAtLeast(0L).toFloat()
        val xNoise = samplePerlinLikeNoise(
            elapsedMillis = elapsed,
            periodMillis = FIRST_PERSON_SHOOT_SWAY_X_NOISE_PERIOD_MILLIS,
            seed = sessionSeed xor FIRST_PERSON_SHOOT_X_SEED_SALT
        )
        val yawNoise = samplePerlinLikeNoise(
            elapsedMillis = elapsed,
            periodMillis = FIRST_PERSON_SHOOT_SWAY_YAW_NOISE_PERIOD_MILLIS,
            seed = sessionSeed xor FIRST_PERSON_SHOOT_YAW_SEED_SALT
        )

        val offsetX = (xNoise * FIRST_PERSON_SHOOT_SWAY_X_NOISE_RANGE_UNITS / MODEL_UNIT) * intensity * hipFactor
        val offsetY = (-FIRST_PERSON_SHOOT_SWAY_Y_UNITS / MODEL_UNIT) * intensity * hipFactor
        // 对齐 TACZ：偏航噪声不受 ADS 位移抑制，仅受后座时间曲线衰减。
        val yawRadians = (yawNoise * FIRST_PERSON_SHOOT_SWAY_YAW_NOISE_RANGE_RADIANS) * intensity
        val yawDegrees = Math.toDegrees(yawRadians.toDouble()).toFloat()

        return FirstPersonShootSwayTransform(
            offsetX = offsetX,
            offsetY = offsetY,
            yawDegrees = yawDegrees
        )
    }

    internal fun resolveFirstPersonJumpSwayTargetProgress(
        currentProgress: Float,
        wasOnGround: Boolean,
        isOnGround: Boolean,
        verticalVelocity: Float,
        previousVerticalVelocity: Float,
        deltaMillis: Long
    ): Float {
        val clampedCurrent = currentProgress.coerceIn(0f, 1f)
        if (deltaMillis <= 0L) {
            return clampedCurrent
        }

        if (isOnGround) {
            if (!wasOnGround) {
                val landingVelocity = kotlin.math.min(previousVerticalVelocity, verticalVelocity)
                return (landingVelocity / -FIRST_PERSON_LANDING_VELOCITY_NORMALIZER).coerceIn(0f, 1f)
            }

            val decay = deltaMillis.toFloat() / (FIRST_PERSON_LANDING_SWAY_TIME_SECONDS * MILLIS_PER_SECOND_FLOAT)
            return (clampedCurrent - decay).coerceAtLeast(0f)
        }

        if (wasOnGround) {
            return (verticalVelocity / FIRST_PERSON_JUMP_VELOCITY_NORMALIZER).coerceIn(0f, 1f)
        }

        val decay = deltaMillis.toFloat() / (FIRST_PERSON_JUMP_SWAY_TIME_SECONDS * MILLIS_PER_SECOND_FLOAT)
        return (clampedCurrent - decay).coerceAtLeast(0f)
    }

    internal fun resolveFirstPersonJumpSwayOffsetY(progress: Float): Float {
        val clampedProgress = progress.coerceIn(0f, 1f)
        return (-FIRST_PERSON_JUMP_SWAY_Y_UNITS * clampedProgress) / MODEL_UNIT
    }

    internal fun resolveSmoothedValueStep(
        current: Float,
        target: Float,
        deltaMillis: Long,
        smoothTimeSeconds: Float
    ): Float {
        if (deltaMillis <= 0L) {
            return current
        }
        if (smoothTimeSeconds <= 0f) {
            return target
        }
        val alpha = (deltaMillis.toFloat() / (smoothTimeSeconds * MILLIS_PER_SECOND_FLOAT))
            .coerceIn(0f, 1f)
        return current + (target - current) * alpha
    }

    internal fun easeOutCubic01(value: Float): Float {
        val x = value.coerceIn(0f, 1f)
        return 1f - (1f - x) * (1f - x) * (1f - x)
    }

    internal fun normalizeFirstPersonSwayAngle(angleDegrees: Float): Float {
        val clamped = angleDegrees.coerceIn(-FIRST_PERSON_SWAY_MAX_ANGLE_DEGREES, FIRST_PERSON_SWAY_MAX_ANGLE_DEGREES)
        return kotlin.math.tanh(clamped / FIRST_PERSON_SWAY_TANH_NORMALIZER) * FIRST_PERSON_SWAY_TANH_NORMALIZER
    }

    private fun wrapDegrees(angleDegrees: Float): Float {
        var wrapped = angleDegrees % 360f
        if (wrapped >= 180f) {
            wrapped -= 360f
        }
        if (wrapped < -180f) {
            wrapped += 360f
        }
        return wrapped
    }

    private fun samplePerlinLikeNoise(elapsedMillis: Float, periodMillis: Float, seed: Int): Float {
        if (periodMillis <= CAMERA_EPSILON_RADIANS) {
            return 0f
        }

        val timeline = (elapsedMillis / periodMillis).coerceAtLeast(0f)
        val baseIndex = kotlin.math.floor(timeline).toInt()
        val fraction = timeline - baseIndex.toFloat()
        val smooth = fraction * fraction * (3f - 2f * fraction)

        val left = hashNoiseUnit(seed, baseIndex)
        val right = hashNoiseUnit(seed, baseIndex + 1)
        return left + (right - left) * smooth
    }

    private fun hashNoiseUnit(seed: Int, index: Int): Float {
        var value = seed xor (index * 0x9E3779B9.toInt())
        value = value xor (value ushr 16)
        value *= 0x7FEB352D
        value = value xor (value ushr 15)
        value *= 0x846CA68B.toInt()
        value = value xor (value ushr 16)
        val normalized = ((value ushr 1).toFloat() / Int.MAX_VALUE.toFloat()).coerceIn(0f, 1f)
        return normalized * 2f - 1f
    }

    private fun resolveFirstPersonPositioningTransform(
        model: LegacyGeoModel,
        positioningBone: LegacyGeoBone,
        animationPose: LegacyAnimationPose?
    ): LegacyRigidTransform {
        var transform = LegacyRigidTransform.IDENTITY

        var current: LegacyGeoBone? = positioningBone
        var depth = 0
        while (current != null && depth <= MAX_BONE_DEPTH) {
            val animationTransform = animationPose?.boneTransformsByName?.get(current.name)
            val animatedRotation = current.rotation + (animationTransform?.rotationOffset ?: LegacyVec3.ZERO)

            // 逆旋转: Rx⁻¹ Ry⁻¹ Rz⁻¹（正向为 Rz Ry Rx）
            if (animatedRotation.x != 0f) {
                transform = appendRotation(transform, axisX = 1f, axisY = 0f, axisZ = 0f, angleDegrees = -animatedRotation.x)
            }
            if (animatedRotation.y != 0f) {
                transform = appendRotation(transform, axisX = 0f, axisY = 1f, axisZ = 0f, angleDegrees = -animatedRotation.y)
            }
            if (animatedRotation.z != 0f) {
                transform = appendRotation(transform, axisX = 0f, axisY = 0f, axisZ = 1f, angleDegrees = -animatedRotation.z)
            }

            // 使用 CONVERTED pivot（与 TACZ 1.20 的 BedrockPart.x/y/z 一致）
            // 不包含动画位移偏移（与 TACZ getPositioningNodeInverse 一致）
            val pivot = current.pivot
            val tx = -pivot.x / MODEL_UNIT
            val ty = if (current.parent != null) {
                -pivot.y / MODEL_UNIT
            } else {
                1.5f - pivot.y / MODEL_UNIT
            }
            val tz = -pivot.z / MODEL_UNIT
            transform = appendTranslation(transform, LegacyVec3(tx, ty, tz))

            current = current.parent?.let(model.bonesByName::get)
            depth += 1
        }

        return transform
    }

    private fun applyRigidTransform(transform: LegacyRigidTransform) {
        val translation = transform.translation
        GlStateManager.translate(translation.x.toDouble(), translation.y.toDouble(), translation.z.toDouble())

        val axisAngle = quaternionToAxisAngleDegrees(transform.rotation)
        if (kotlin.math.abs(axisAngle.angleDegrees) > CAMERA_EPSILON_DEGREES) {
            GlStateManager.rotate(axisAngle.angleDegrees, axisAngle.axisX, axisAngle.axisY, axisAngle.axisZ)
        }
    }

    private fun appendRotation(
        current: LegacyRigidTransform,
        axisX: Float,
        axisY: Float,
        axisZ: Float,
        angleDegrees: Float
    ): LegacyRigidTransform {
        val delta = quaternionFromAxisAngleDegrees(axisX, axisY, axisZ, angleDegrees)
        return current.copy(rotation = multiplyQuaternions(current.rotation, delta))
    }

    private fun appendTranslation(current: LegacyRigidTransform, delta: LegacyVec3): LegacyRigidTransform {
        val rotatedDelta = rotateVecByQuaternion(delta, current.rotation)
        return current.copy(translation = current.translation + rotatedDelta)
    }

    private fun interpolateRigidTransform(
        from: LegacyRigidTransform,
        to: LegacyRigidTransform,
        weight: Float
    ): LegacyRigidTransform {
        val t = weight.coerceIn(0f, 1f)
        return LegacyRigidTransform(
            translation = lerp(from.translation, to.translation, t),
            rotation = slerpQuaternion(from.rotation, to.rotation, t)
        )
    }

    private fun quaternionFromAxisAngleDegrees(
        axisX: Float,
        axisY: Float,
        axisZ: Float,
        angleDegrees: Float
    ): LegacyQuaternion {
        val angleRadians = Math.toRadians(angleDegrees.toDouble()).toFloat()
        val halfAngle = angleRadians * 0.5f
        val sinHalf = kotlin.math.sin(halfAngle)
        val cosHalf = kotlin.math.cos(halfAngle)
        return LegacyQuaternion(
            x = axisX * sinHalf,
            y = axisY * sinHalf,
            z = axisZ * sinHalf,
            w = cosHalf
        ).normalized()
    }

    private fun multiplyQuaternions(left: LegacyQuaternion, right: LegacyQuaternion): LegacyQuaternion {
        return LegacyQuaternion(
            x = left.w * right.x + left.x * right.w + left.y * right.z - left.z * right.y,
            y = left.w * right.y - left.x * right.z + left.y * right.w + left.z * right.x,
            z = left.w * right.z + left.x * right.y - left.y * right.x + left.z * right.w,
            w = left.w * right.w - left.x * right.x - left.y * right.y - left.z * right.z
        ).normalized()
    }

    private fun rotateVecByQuaternion(vector: LegacyVec3, quaternion: LegacyQuaternion): LegacyVec3 {
        val q = quaternion.normalized()
        val qv = LegacyVec3(q.x, q.y, q.z)
        val t = cross(qv, vector) * 2f
        return vector + t * q.w + cross(qv, t)
    }

    private fun slerpQuaternion(from: LegacyQuaternion, to: LegacyQuaternion, weight: Float): LegacyQuaternion {
        val t = weight.coerceIn(0f, 1f)
        var toQuat = to
        var dot = dot(from, toQuat)

        if (dot < 0f) {
            toQuat = LegacyQuaternion(-toQuat.x, -toQuat.y, -toQuat.z, -toQuat.w)
            dot = -dot
        }

        if (dot > 0.9995f) {
            return LegacyQuaternion(
                x = from.x + (toQuat.x - from.x) * t,
                y = from.y + (toQuat.y - from.y) * t,
                z = from.z + (toQuat.z - from.z) * t,
                w = from.w + (toQuat.w - from.w) * t
            ).normalized()
        }

        val theta0 = kotlin.math.acos(dot)
        val sinTheta0 = kotlin.math.sin(theta0)
        if (kotlin.math.abs(sinTheta0) <= CAMERA_EPSILON_RADIANS) {
            return from
        }

        val theta = theta0 * t
        val sinTheta = kotlin.math.sin(theta)
        val s0 = kotlin.math.cos(theta) - dot * sinTheta / sinTheta0
        val s1 = sinTheta / sinTheta0

        return LegacyQuaternion(
            x = from.x * s0 + toQuat.x * s1,
            y = from.y * s0 + toQuat.y * s1,
            z = from.z * s0 + toQuat.z * s1,
            w = from.w * s0 + toQuat.w * s1
        ).normalized()
    }

    private fun dot(left: LegacyQuaternion, right: LegacyQuaternion): Float {
        return left.x * right.x + left.y * right.y + left.z * right.z + left.w * right.w
    }

    private fun dot(left: LegacyVec3, right: LegacyVec3): Float {
        return left.x * right.x + left.y * right.y + left.z * right.z
    }

    private fun cross(left: LegacyVec3, right: LegacyVec3): LegacyVec3 {
        return LegacyVec3(
            x = left.y * right.z - left.z * right.y,
            y = left.z * right.x - left.x * right.z,
            z = left.x * right.y - left.y * right.x
        )
    }

    private operator fun LegacyVec3.times(scalar: Float): LegacyVec3 {
        return LegacyVec3(
            x = x * scalar,
            y = y * scalar,
            z = z * scalar
        )
    }

    private fun findFirstPersonPositioningBoneByName(model: LegacyGeoModel, normalizedTargetName: String): LegacyGeoBone? {
        return model.bonesByName.entries
            .firstOrNull { normalizeBoneName(it.key) == normalizedTargetName }
            ?.value
    }

    internal fun selectFirstPersonPositioningTargets(preferIronView: Boolean): List<String> {
        return if (preferIronView) {
            listOf(IRON_VIEW_BONE, IDLE_VIEW_BONE, CAMERA_BONE)
        } else {
            FIRST_PERSON_POSITIONING_BONES
        }
    }

    private fun findFirstPersonPositioningBone(
        model: LegacyGeoModel,
        preferIronView: Boolean
    ): LegacyGeoBone? {
        val preferredTargets = selectFirstPersonPositioningTargets(preferIronView)
        preferredTargets.forEach { target ->
            val hit = model.bonesByName.entries
                .firstOrNull { normalizeBoneName(it.key) == target }
                ?.value
            if (hit != null) {
                return hit
            }
        }

        return model.bonesByName.entries
            .firstOrNull { entry ->
                val normalized = normalizeBoneName(entry.key)
                FIRST_PERSON_POSITIONING_FUZZY.any { token -> normalized.contains(token) }
            }
            ?.value
    }

    private fun resolveRuntimeAnimationSnapshot(
        gunId: String,
        minecraft: Minecraft
    ): WeaponAnimationRuntimeSnapshot? {
        val player = minecraft.player as? AbstractClientPlayer ?: return null
        val sessionId = clientSessionId(player.uniqueID.toString())
        val snapshot = WeaponRuntimeMcBridge.animationSnapshotOrNull(sessionId) ?: return null

        return snapshot.takeIf {
            it.gunId.trim().lowercase() == gunId.trim().lowercase()
        }
    }

    internal fun resolveCameraAnimationDelta(itemStack: ItemStack, partialTicks: Float): CameraAnimationDelta? {
        if (itemStack.isEmpty || itemStack.item !is LegacyGunItem) {
            return null
        }

        val minecraft = Minecraft.getMinecraft()
        val gunId = itemStack.item
            .registryName
            ?.path
            ?.trim()
            ?.lowercase()
            ?.ifBlank { null }
            ?: return null

        val display = GunDisplayRuntime.registry().snapshot().findDefinition(gunId) ?: return null
        val animationPose = resolveAnimationPose(
            gunId = gunId,
            display = display,
            minecraft = minecraft,
            enableSoundDispatch = false,
            allowContextualFallback = true
        )?.pose ?: return null
        val cameraRotation = animationPose.boneTransformsByName.entries
            .firstOrNull { (name, _) -> normalizeBoneName(name) == CAMERA_BONE }
            ?.value
            ?.rotationOffset
            ?: return null
        val multiplier = resolveCameraAnimationMultiplier(
            itemStack = itemStack,
            partialTicks = partialTicks,
            display = display,
            minecraft = minecraft
        )

        return resolveCameraAnimationDeltaFromBoneRotation(
            cameraBoneRotationXDegrees = cameraRotation.x,
            cameraBoneRotationYDegrees = cameraRotation.y,
            cameraBoneRotationZDegrees = cameraRotation.z,
            multiplier = multiplier
        ).takeIf(::hasMeaningfulCameraDelta)
    }

    internal fun resolveCameraAnimationMultiplier(
        itemStack: ItemStack,
        @Suppress("UNUSED_PARAMETER") partialTicks: Float
    ): Float {
        if (itemStack.isEmpty || itemStack.item !is LegacyGunItem) {
            return 1f
        }

        val minecraft = Minecraft.getMinecraft()
        val gunId = itemStack.item
            .registryName
            ?.path
            ?.trim()
            ?.lowercase()
            ?.ifBlank { null }
            ?: return 1f
        val display = GunDisplayRuntime.registry().snapshot().findDefinition(gunId)
        return resolveCameraAnimationMultiplier(
            itemStack = itemStack,
            partialTicks = partialTicks,
            display = display,
            minecraft = minecraft
        )
    }

    internal fun resolveCameraAnimationMultiplierFromAiming(
        aimingProgress: Float,
        aimingZoom: Float
    ): Float {
        val clampedProgress = aimingProgress.coerceIn(0f, 1f)
        val normalizedZoom = aimingZoom.takeIf { it > 0f }?.coerceAtLeast(1f) ?: 1f
        val zoomScale = 1f / kotlin.math.sqrt(normalizedZoom)
        return (1f - clampedProgress + clampedProgress * zoomScale)
            .coerceAtLeast(0f)
    }

    private fun resolveCameraAnimationMultiplier(
        itemStack: ItemStack,
        @Suppress("UNUSED_PARAMETER") partialTicks: Float,
        display: GunDisplayDefinition?,
        minecraft: Minecraft
    ): Float {
        val player = minecraft.player as? AbstractClientPlayer ?: return 1f
        if (minecraft.renderViewEntity !== player) {
            return 1f
        }

        val gunId = itemStack.item
            .registryName
            ?.path
            ?.trim()
            ?.lowercase()
            ?.ifBlank { null }
            ?: return 1f

        val aimingProgress = resolveAimingProgress(
            minecraft = minecraft,
            player = player,
            gunId = gunId
        )
        val aimingZoom = resolveAimingZoom(itemStack, display)
        return resolveCameraAnimationMultiplierFromAiming(
            aimingProgress = aimingProgress,
            aimingZoom = aimingZoom
        )
    }

    internal fun resolveAimingProgressStep(
        currentProgress: Float,
        isAiming: Boolean,
        deltaMillis: Long,
        aimTimeSeconds: Float
    ): Float {
        val clampedCurrent = currentProgress.coerceIn(0f, 1f)
        if (deltaMillis <= 0L) {
            return clampedCurrent
        }

        if (aimTimeSeconds <= 0f) {
            return if (isAiming) 1f else 0f
        }

        val alphaProgress = (deltaMillis.toFloat() / (aimTimeSeconds * MILLIS_PER_SECOND_FLOAT))
            .coerceAtLeast(0f)

        return if (isAiming) {
            (clampedCurrent + alphaProgress).coerceAtMost(1f)
        } else {
            (clampedCurrent - alphaProgress).coerceAtLeast(0f)
        }
    }

    private fun resolveAimingProgress(
        minecraft: Minecraft,
        player: AbstractClientPlayer,
        gunId: String,
        nowMillis: Long = System.currentTimeMillis()
    ): Float {
        val sessionId = clientSessionId(player.uniqueID.toString())
        val state = aimingBlendStateBySessionId.getOrPut(sessionId) {
            AimingBlendState(progress = 0f, lastUpdatedMillis = nowMillis)
        }

        if (state.lastUpdatedMillis <= 0L) {
            state.lastUpdatedMillis = nowMillis
        }

        val deltaMillis = (nowMillis - state.lastUpdatedMillis)
            .coerceIn(0L, MAX_AIMING_DELTA_MILLIS)
        state.lastUpdatedMillis = nowMillis

        if (state.lastGunId != gunId) {
            state.lastGunId = gunId
        }

        if (deltaMillis <= 0L) {
            return state.progress.coerceIn(0f, 1f)
        }

        val aimTimeSeconds = resolveAimingTimeSeconds(gunId)
        state.progress = resolveAimingProgressStep(
            currentProgress = state.progress,
            isAiming = com.tacz.legacy.client.input.WeaponAimInputStateRegistry.resolve(player.cachedUniqueIdString) == true,
            deltaMillis = deltaMillis,
            aimTimeSeconds = aimTimeSeconds
        )
        return state.progress
    }

    internal fun shouldUseAimLoopClip(aimingProgress: Float): Boolean {
        return shouldUseAimLoopClipWithHysteresis(
            aimingProgress = aimingProgress,
            wasUsingAimLoopClip = false
        )
    }

    internal fun shouldUseAimLoopClipWithHysteresis(
        aimingProgress: Float,
        wasUsingAimLoopClip: Boolean
    ): Boolean {
        val clamped = aimingProgress.coerceIn(0f, 1f)
        return if (wasUsingAimLoopClip) {
            clamped >= AIM_LOOP_RELEASE_THRESHOLD
        } else {
            clamped >= AIM_LOOP_PROGRESS_THRESHOLD
        }
    }

    internal fun shouldPreferRuntimePlaybackClip(clipType: WeaponAnimationClipType?): Boolean {
        if (clipType == null) {
            return false
        }
        return clipType in RUNTIME_PRIORITY_CLIP_TYPES
    }

    internal fun resolveAimingIntent(
        bridgedAiming: Boolean?,
        useDown: Boolean,
        isSwinging: Boolean
    ): Boolean {
        return bridgedAiming ?: resolveAimingIntentFromInputs(
            useDown = useDown,
            isSwinging = isSwinging
        )
    }

    internal fun resolveAimingIntentFromInputs(useDown: Boolean, isSwinging: Boolean): Boolean {
        if (!useDown) {
            return false
        }
        if (isSwinging) {
            return false
        }
        return true
    }

    private fun resolveAimingTimeSeconds(gunId: String): Float {
        val aimTime = GunPackRuntime.registry().snapshot()
            .findByGunId(gunId)
            ?.aimTimeSeconds
            ?: DEFAULT_AIM_TIME_SECONDS
        return aimTime.coerceAtLeast(0f)
    }

    private fun resolveAimingZoom(itemStack: ItemStack, display: GunDisplayDefinition?): Float {
        val rootTag = itemStack.tagCompound
        val scopeAttachmentId = rootTag?.let {
            LegacyBoneVisibilityPolicy.readAttachmentId(it, "SCOPE")
        }
        val scopeZoomIndex = rootTag?.let {
            LegacyBoneVisibilityPolicy.readAttachmentZoomNumber(it, "SCOPE")
        } ?: 0

        val scopeZoom = LegacyBoneVisibilityPolicy.resolveAttachmentZoomLevels(scopeAttachmentId)
            ?.takeIf { it.isNotEmpty() }
            ?.let { zoomLevels ->
                val zoomIndex = Math.floorMod(scopeZoomIndex, zoomLevels.size)
                zoomLevels[zoomIndex]
            }

        val baseZoom = scopeZoom
            ?: display?.ironZoom
            ?: 1f
        return baseZoom
            .takeIf { it > 0f }
            ?.coerceAtLeast(1f)
            ?: 1f
    }

    internal fun resolveAimingZoomForFov(itemStack: ItemStack): Float {
        if (itemStack.isEmpty || itemStack.item !is LegacyGunItem) {
            return 1f
        }

        val gunId = itemStack.item
            .registryName
            ?.path
            ?.trim()
            ?.lowercase()
            ?.ifBlank { null }
            ?: return 1f
        val display = GunDisplayRuntime.registry().snapshot().findDefinition(gunId)
        return resolveAimingZoom(itemStack, display)
    }

    internal fun resolveModelFovForFov(itemStack: ItemStack, defaultFov: Float): Float {
        if (itemStack.isEmpty || itemStack.item !is LegacyGunItem) {
            return defaultFov
        }

        val rootTag = itemStack.tagCompound
        val scopeAttachmentId = rootTag?.let {
            LegacyBoneVisibilityPolicy.readAttachmentId(it, "SCOPE")
        }
        val scopeZoomIndex = rootTag?.let {
            LegacyBoneVisibilityPolicy.readAttachmentZoomNumber(it, "SCOPE")
        } ?: 0

        val scopeModelFov = LegacyBoneVisibilityPolicy.resolveAttachmentViewsFov(scopeAttachmentId)
            ?.takeIf { it.isNotEmpty() }
            ?.let { viewsFov ->
                val zoomIndex = Math.floorMod(scopeZoomIndex, viewsFov.size)
                viewsFov[zoomIndex]
            }

        val gunId = itemStack.item
            .registryName
            ?.path
            ?.trim()
            ?.lowercase()
            ?.ifBlank { null }
            ?: return defaultFov
        val display = GunDisplayRuntime.registry().snapshot().findDefinition(gunId)

        val resolved = scopeModelFov
            ?: display?.zoomModelFov
            ?: defaultFov

        return resolved
            .takeIf { it > 0f }
            ?.coerceIn(1f, 179f)
            ?: defaultFov
    }

    internal fun resolveCameraAnimationDeltaFromBoneRotation(
        cameraBoneRotationXDegrees: Float,
        cameraBoneRotationYDegrees: Float,
        cameraBoneRotationZDegrees: Float,
        multiplier: Float = 1f
    ): CameraAnimationDelta {
        val pitchRadians = Math.toRadians(cameraBoneRotationXDegrees.toDouble()).toFloat()
        val yawRadians = Math.toRadians(cameraBoneRotationYDegrees.toDouble()).toFloat()
        // 对齐 TACZ CameraRotateListener：z 轴取反
        val rollRadians = Math.toRadians((-cameraBoneRotationZDegrees).toDouble()).toFloat()

        val rawQuaternion = eulerRadiansToQuaternion(
            pitchRadians = pitchRadians,
            yawRadians = yawRadians,
            rollRadians = rollRadians
        )
        val scaledQuaternion = multiplyQuaternionByScale(
            quaternion = rawQuaternion,
            multiplier = multiplier.coerceAtLeast(0f)
        )
        val euler = quaternionToEulerDegrees(scaledQuaternion)
        val axisAngle = quaternionToAxisAngleDegrees(scaledQuaternion)

        return CameraAnimationDelta(
            pitchDegrees = euler.x,
            yawDegrees = euler.y,
            rollDegrees = euler.z,
            axisX = axisAngle.axisX,
            axisY = axisAngle.axisY,
            axisZ = axisAngle.axisZ,
            axisAngleDegrees = axisAngle.angleDegrees
        )
    }

    private fun hasMeaningfulCameraDelta(delta: CameraAnimationDelta): Boolean {
        if (kotlin.math.abs(delta.axisAngleDegrees) > CAMERA_EPSILON_DEGREES) {
            return true
        }
        if (kotlin.math.abs(delta.pitchDegrees) > CAMERA_EPSILON_DEGREES) {
            return true
        }
        if (kotlin.math.abs(delta.yawDegrees) > CAMERA_EPSILON_DEGREES) {
            return true
        }
        return kotlin.math.abs(delta.rollDegrees) > CAMERA_EPSILON_DEGREES
    }

    private fun eulerRadiansToQuaternion(
        pitchRadians: Float,
        yawRadians: Float,
        rollRadians: Float
    ): LegacyQuaternion {
        val cy = kotlin.math.cos(rollRadians * 0.5f)
        val sy = kotlin.math.sin(rollRadians * 0.5f)
        val cp = kotlin.math.cos(yawRadians * 0.5f)
        val sp = kotlin.math.sin(yawRadians * 0.5f)
        val cr = kotlin.math.cos(pitchRadians * 0.5f)
        val sr = kotlin.math.sin(pitchRadians * 0.5f)

        return LegacyQuaternion(
            x = cy * cp * sr - sy * sp * cr,
            y = sy * cp * sr + cy * sp * cr,
            z = sy * cp * cr - cy * sp * sr,
            w = cy * cp * cr + sy * sp * sr
        ).normalized()
    }

    private fun multiplyQuaternionByScale(quaternion: LegacyQuaternion, multiplier: Float): LegacyQuaternion {
        if (multiplier == 1f) {
            return quaternion
        }
        if (multiplier <= 0f) {
            return LegacyQuaternion.IDENTITY
        }

        val axisAngle = quaternionToAxisAngleDegrees(quaternion)
        if (kotlin.math.abs(axisAngle.angleDegrees) <= CAMERA_EPSILON_DEGREES) {
            return LegacyQuaternion.IDENTITY
        }

        val scaledAngleRadians = Math.toRadians((axisAngle.angleDegrees * multiplier).toDouble())
        val sinHalf = kotlin.math.sin((scaledAngleRadians / 2.0).toFloat())
        val cosHalf = kotlin.math.cos((scaledAngleRadians / 2.0).toFloat())
        return LegacyQuaternion(
            x = axisAngle.axisX * sinHalf,
            y = axisAngle.axisY * sinHalf,
            z = axisAngle.axisZ * sinHalf,
            w = cosHalf
        ).normalized()
    }

    private fun quaternionToEulerDegrees(quaternion: LegacyQuaternion): LegacyVec3 {
        val q = quaternion.normalized()

        val sinPitchCos = 2f * (q.w * q.x + q.y * q.z)
        val cosPitchCos = 1f - 2f * (q.x * q.x + q.y * q.y)
        val pitch = kotlin.math.atan2(sinPitchCos, cosPitchCos)

        val sinYawRaw = (2f * (q.w * q.y - q.x * q.z)).coerceIn(-1f, 1f)
        val yaw = kotlin.math.asin(sinYawRaw)

        val sinRollCos = 2f * (q.w * q.z + q.x * q.y)
        val cosRollCos = 1f - 2f * (q.y * q.y + q.z * q.z)
        val roll = kotlin.math.atan2(sinRollCos, cosRollCos)

        return LegacyVec3(
            x = Math.toDegrees(pitch.toDouble()).toFloat(),
            y = Math.toDegrees(yaw.toDouble()).toFloat(),
            z = Math.toDegrees(roll.toDouble()).toFloat()
        )
    }

    private fun quaternionToAxisAngleDegrees(quaternion: LegacyQuaternion): LegacyAxisAngle {
        val normalized = quaternion.normalized()
        val w = normalized.w.coerceIn(-1f, 1f)
        val angleRadians = (2.0 * kotlin.math.acos(w.toDouble())).toFloat()
        if (kotlin.math.abs(angleRadians) <= CAMERA_EPSILON_RADIANS) {
            return LegacyAxisAngle(
                axisX = 0f,
                axisY = 0f,
                axisZ = 1f,
                angleDegrees = 0f
            )
        }

        val sinHalf = kotlin.math.sin(angleRadians / 2f)
        if (kotlin.math.abs(sinHalf) <= CAMERA_EPSILON_RADIANS) {
            return LegacyAxisAngle(
                axisX = 0f,
                axisY = 0f,
                axisZ = 1f,
                angleDegrees = 0f
            )
        }

        val axisX = normalized.x / sinHalf
        val axisY = normalized.y / sinHalf
        val axisZ = normalized.z / sinHalf
        val axisLength = kotlin.math.sqrt(axisX * axisX + axisY * axisY + axisZ * axisZ)
        if (axisLength <= CAMERA_EPSILON_RADIANS) {
            return LegacyAxisAngle(
                axisX = 0f,
                axisY = 0f,
                axisZ = 1f,
                angleDegrees = 0f
            )
        }

        return LegacyAxisAngle(
            axisX = axisX / axisLength,
            axisY = axisY / axisLength,
            axisZ = axisZ / axisLength,
            angleDegrees = Math.toDegrees(angleRadians.toDouble()).toFloat()
        )
    }

    private fun renderBone(
        model: LegacyGeoModel,
        boneName: String,
        depth: Int,
        isRootBone: Boolean,
        visibilityPolicy: LegacyBoneVisibilityPolicy,
        animationPose: LegacyAnimationPose?,
        handContext: FirstPersonHandContext?,
        preferLeftHandPos: Boolean,
        preferRightHandPos: Boolean,
        hiddenByAncestor: Boolean,
        referenceOffsetCollector: MutableMap<String, FirstPersonReferenceOffset>?
    ) {
        if (depth > MAX_BONE_DEPTH) {
            return
        }

        if (hiddenByAncestor) {
            return
        }

        val bone = model.bonesByName[boneName] ?: return
        val shouldRenderSelf = visibilityPolicy.shouldRenderBone(bone.name)
        val hideChildrenByPolicy = visibilityPolicy.shouldHideDescendants(
            name = bone.name,
            shouldRenderSelf = shouldRenderSelf
        )
        val nextHiddenByAncestor = hiddenByAncestor || hideChildrenByPolicy

        val animationTransform = animationPose?.boneTransformsByName?.get(bone.name)
        val animatedPivot = bone.pivot + (animationTransform?.positionOffset ?: LegacyVec3.ZERO)
        val animatedRotation = bone.rotation + (animationTransform?.rotationOffset ?: LegacyVec3.ZERO)

        GlStateManager.pushMatrix()
        applyNodeTransform(animatedPivot, animatedRotation, isRootBone)

        captureReferenceOffsetIfPresent(
            boneName = bone.name,
            collector = referenceOffsetCollector
        )

        renderHandByBoneAnchor(
            boneName = bone.name,
            handContext = handContext,
            preferLeftHandPos = preferLeftHandPos,
            preferRightHandPos = preferRightHandPos
        )

        if (shouldRenderSelf) {
            bone.cubes
                .take(MAX_CUBES_PER_BONE)
                .forEach { cube -> renderCube(model, cube) }

            if (normalizeBoneName(bone.name) == ADDITIONAL_MAGAZINE_BONE) {
                renderMagazineReplicaAtAdditionalNode(
                    model = model,
                    visibilityPolicy = visibilityPolicy,
                    animationPose = animationPose,
                    depth = depth
                )
            }
        }

        model.childrenByParent[boneName]
            .orEmpty()
            .take(MAX_CHILD_BONES)
            .forEach { child ->
                renderBone(
                    model = model,
                    boneName = child,
                    depth = depth + 1,
                    isRootBone = false,
                    visibilityPolicy = visibilityPolicy,
                    animationPose = animationPose,
                    handContext = handContext,
                    preferLeftHandPos = preferLeftHandPos,
                    preferRightHandPos = preferRightHandPos,
                    hiddenByAncestor = nextHiddenByAncestor,
                    referenceOffsetCollector = referenceOffsetCollector
                )
            }

        GlStateManager.popMatrix()
    }

    private fun renderMagazineReplicaAtAdditionalNode(
        model: LegacyGeoModel,
        visibilityPolicy: LegacyBoneVisibilityPolicy,
        animationPose: LegacyAnimationPose?,
        depth: Int
    ) {
        val magazineBoneName = model.bonesByName.keys
            .firstOrNull { normalizeBoneName(it) == MAGAZINE_BONE }
            ?: return
        val magazineBone = model.bonesByName[magazineBoneName] ?: return

        val shouldRenderMagazineSelf = visibilityPolicy.shouldRenderBone(magazineBone.name)
        val hideMagazineChildren = visibilityPolicy.shouldHideDescendants(
            name = magazineBone.name,
            shouldRenderSelf = shouldRenderMagazineSelf
        )

        if (shouldRenderMagazineSelf) {
            magazineBone.cubes
                .take(MAX_CUBES_PER_BONE)
                .forEach { cube -> renderCube(model, cube) }
        }

        if (hideMagazineChildren) {
            return
        }

        model.childrenByParent[magazineBoneName]
            .orEmpty()
            .take(MAX_CHILD_BONES)
            .forEach { childName ->
                renderReplicaChildBone(
                    model = model,
                    boneName = childName,
                    depth = depth + 1,
                    visibilityPolicy = visibilityPolicy,
                    animationPose = animationPose
                )
            }
    }

    private fun renderReplicaChildBone(
        model: LegacyGeoModel,
        boneName: String,
        depth: Int,
        visibilityPolicy: LegacyBoneVisibilityPolicy,
        animationPose: LegacyAnimationPose?
    ) {
        if (depth > MAX_BONE_DEPTH) {
            return
        }

        val bone = model.bonesByName[boneName] ?: return
        val shouldRenderSelf = visibilityPolicy.shouldRenderBone(bone.name)
        val hideChildrenByPolicy = visibilityPolicy.shouldHideDescendants(
            name = bone.name,
            shouldRenderSelf = shouldRenderSelf
        )

        val animationTransform = animationPose?.boneTransformsByName?.get(bone.name)
        val animatedPivot = bone.pivot + (animationTransform?.positionOffset ?: LegacyVec3.ZERO)
        val animatedRotation = bone.rotation + (animationTransform?.rotationOffset ?: LegacyVec3.ZERO)

        GlStateManager.pushMatrix()
        applyNodeTransform(animatedPivot, animatedRotation, isRootBone = false)

        if (shouldRenderSelf) {
            bone.cubes
                .take(MAX_CUBES_PER_BONE)
                .forEach { cube -> renderCube(model, cube) }
        }

        if (!hideChildrenByPolicy) {
            model.childrenByParent[boneName]
                .orEmpty()
                .take(MAX_CHILD_BONES)
                .forEach { childName ->
                    renderReplicaChildBone(
                        model = model,
                        boneName = childName,
                        depth = depth + 1,
                        visibilityPolicy = visibilityPolicy,
                        animationPose = animationPose
                    )
                }
        }

        GlStateManager.popMatrix()
    }

    private fun renderHandByBoneAnchor(
        boneName: String,
        handContext: FirstPersonHandContext?,
        preferLeftHandPos: Boolean,
        preferRightHandPos: Boolean
    ) {
        if (handContext == null) {
            return
        }

        val normalized = normalizeBoneName(boneName)
        val renderRight = normalized == HAND_BONE_RIGHT_POS ||
            (normalized == HAND_BONE_RIGHT && !preferRightHandPos)
        val renderLeft = normalized == HAND_BONE_LEFT_POS ||
            (normalized == HAND_BONE_LEFT && !preferLeftHandPos)

        if (renderRight) {
            renderAnchoredHand(handContext, EnumHandSide.RIGHT)
        }
        if (renderLeft) {
            renderAnchoredHand(handContext, EnumHandSide.LEFT)
        }
    }

    private fun captureReferenceOffsetIfPresent(
        boneName: String,
        collector: MutableMap<String, FirstPersonReferenceOffset>?
    ) {
        if (collector == null) {
            return
        }

        val normalized = normalizeBoneName(boneName)
        if (normalized !in REFERENCE_OFFSET_BONE_NAMES) {
            return
        }
        if (collector.containsKey(normalized)) {
            return
        }

        val captured = captureCurrentModelViewOffset()
            ?: return
        collector[normalized] = captured
    }

    private fun captureCurrentModelViewOffset(): FirstPersonReferenceOffset? {
        val matrix = MODEL_VIEW_MATRIX_BUFFER.get()
        matrix.clear()
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, matrix)

        val x = matrix.get(12)
        val y = matrix.get(13)
        val z = matrix.get(14)
        if (!x.isFinite() || !y.isFinite() || !z.isFinite()) {
            return null
        }

        return FirstPersonReferenceOffset(
            x = x,
            y = y,
            z = z
        )
    }

    private fun normalizeBoneName(raw: String): String =
        raw.trim().lowercase()

    internal fun resolveAdditionalMagazineVisibility(
        clipType: WeaponAnimationClipType?,
        progress: Float?
    ): Boolean {
        if (clipType != WeaponAnimationClipType.RELOAD) {
            return false
        }

        val normalizedProgress = progress?.coerceIn(0f, 1f) ?: return true
        return normalizedProgress in ADDITIONAL_MAGAZINE_SHOW_PROGRESS_MIN..ADDITIONAL_MAGAZINE_SHOW_PROGRESS_MAX
    }

    internal fun resolveDefaultAttachmentBoneVisibility(
        boneName: String,
        hasScope: Boolean,
        hasMuzzle: Boolean,
        hasStock: Boolean,
        hasGrip: Boolean,
        hasLaser: Boolean,
        hasExtendedMag: Boolean
    ): Boolean? {
        val normalized = normalizeBoneName(boneName)
        if (!normalized.contains("_default")) {
            return null
        }

        return when {
            normalized.startsWith("scope_default") -> !hasScope
            normalized.startsWith("muzzle_default") -> !hasMuzzle
            normalized.startsWith("stock_default") -> !hasStock
            normalized.startsWith("grip_default") -> !hasGrip
            normalized.startsWith("laser_default") -> !hasLaser
            normalized.startsWith("extended_mag_default") -> !hasExtendedMag
            else -> null
        }
    }

    private fun renderCube(model: LegacyGeoModel, cube: LegacyGeoCube) {
        val cubePivot = cube.pivot
        val cubeRotation = cube.rotation
        val hasCubeTransform = cubePivot != null && cubeRotation != null

        if (hasCubeTransform) {
            GlStateManager.pushMatrix()
            applyNodeTransform(cubePivot, cubeRotation, isRootBone = false)
        }

        var x1 = (cube.origin.x - cube.inflate) / MODEL_UNIT
        var y1 = (cube.origin.y - cube.inflate) / MODEL_UNIT
        var z1 = (cube.origin.z - cube.inflate) / MODEL_UNIT
        var x2 = (cube.origin.x + cube.size.x + cube.inflate) / MODEL_UNIT
        var y2 = (cube.origin.y + cube.size.y + cube.inflate) / MODEL_UNIT
        var z2 = (cube.origin.z + cube.size.z + cube.inflate) / MODEL_UNIT

        if (cube.mirror) {
            val swap = x1
            x1 = x2
            x2 = swap
        }

        val faces = if (cube.faces.isNotEmpty()) {
            cube.faces
        } else {
            deriveBoxUvFaces(cube)
        }

        faces.forEach { (face, uv) ->
            when (face) {
                LegacyCubeFace.NORTH -> drawFace(
                    model,
                    uv,
                    normalX = 0f,
                    normalY = 0f,
                    normalZ = -1f,
                    p1 = LegacyVec3(x1, y2, z1),
                    p2 = LegacyVec3(x2, y2, z1),
                    p3 = LegacyVec3(x2, y1, z1),
                    p4 = LegacyVec3(x1, y1, z1)
                )

                LegacyCubeFace.SOUTH -> drawFace(
                    model,
                    uv,
                    normalX = 0f,
                    normalY = 0f,
                    normalZ = 1f,
                    p1 = LegacyVec3(x2, y2, z2),
                    p2 = LegacyVec3(x1, y2, z2),
                    p3 = LegacyVec3(x1, y1, z2),
                    p4 = LegacyVec3(x2, y1, z2)
                )

                LegacyCubeFace.WEST -> drawFace(
                    model,
                    uv,
                    normalX = -1f,
                    normalY = 0f,
                    normalZ = 0f,
                    p1 = LegacyVec3(x1, y2, z2),
                    p2 = LegacyVec3(x1, y2, z1),
                    p3 = LegacyVec3(x1, y1, z1),
                    p4 = LegacyVec3(x1, y1, z2)
                )

                LegacyCubeFace.EAST -> drawFace(
                    model,
                    uv,
                    normalX = 1f,
                    normalY = 0f,
                    normalZ = 0f,
                    p1 = LegacyVec3(x2, y2, z1),
                    p2 = LegacyVec3(x2, y2, z2),
                    p3 = LegacyVec3(x2, y1, z2),
                    p4 = LegacyVec3(x2, y1, z1)
                )

                LegacyCubeFace.UP -> drawFace(
                    model,
                    uv,
                    normalX = 0f,
                    normalY = 1f,
                    normalZ = 0f,
                    p1 = LegacyVec3(x1, y2, z2),
                    p2 = LegacyVec3(x2, y2, z2),
                    p3 = LegacyVec3(x2, y2, z1),
                    p4 = LegacyVec3(x1, y2, z1)
                )

                LegacyCubeFace.DOWN -> drawFace(
                    model,
                    uv,
                    normalX = 0f,
                    normalY = -1f,
                    normalZ = 0f,
                    p1 = LegacyVec3(x1, y1, z1),
                    p2 = LegacyVec3(x2, y1, z1),
                    p3 = LegacyVec3(x2, y1, z2),
                    p4 = LegacyVec3(x1, y1, z2)
                )
            }
        }

        if (hasCubeTransform) {
            GlStateManager.popMatrix()
        }
    }

    private fun drawFace(
        model: LegacyGeoModel,
        uv: LegacyFaceUv,
        normalX: Float,
        normalY: Float,
        normalZ: Float,
        p1: LegacyVec3,
        p2: LegacyVec3,
        p3: LegacyVec3,
        p4: LegacyVec3
    ) {
        val uMin = uv.u / model.textureWidth.toFloat()
        val vMin = uv.v / model.textureHeight.toFloat()
        val uMax = (uv.u + uv.uvSizeU) / model.textureWidth.toFloat()
        val vMax = (uv.v + uv.uvSizeV) / model.textureHeight.toFloat()

        val tessellator = Tessellator.getInstance()
        val buffer = tessellator.buffer
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_NORMAL)
        // 对齐 TACZ BedrockPolygon 的顶点 UV remap：v0->uMax,vMin; v1->uMin,vMin; v2->uMin,vMax; v3->uMax,vMax
        buffer.pos(p1.x.toDouble(), p1.y.toDouble(), p1.z.toDouble()).tex(uMax.toDouble(), vMin.toDouble())
            .normal(normalX, normalY, normalZ).endVertex()
        buffer.pos(p2.x.toDouble(), p2.y.toDouble(), p2.z.toDouble()).tex(uMin.toDouble(), vMin.toDouble())
            .normal(normalX, normalY, normalZ).endVertex()
        buffer.pos(p3.x.toDouble(), p3.y.toDouble(), p3.z.toDouble()).tex(uMin.toDouble(), vMax.toDouble())
            .normal(normalX, normalY, normalZ).endVertex()
        buffer.pos(p4.x.toDouble(), p4.y.toDouble(), p4.z.toDouble()).tex(uMax.toDouble(), vMax.toDouble())
            .normal(normalX, normalY, normalZ).endVertex()
        tessellator.draw()
    }

    private fun applyNodeTransform(pivot: LegacyVec3, rotation: LegacyVec3, @Suppress("UNUSED_PARAMETER") isRootBone: Boolean) {
        val px = pivot.x / MODEL_UNIT
        val py = pivot.y / MODEL_UNIT
        val pz = pivot.z / MODEL_UNIT
        GlStateManager.translate(px.toDouble(), py.toDouble(), pz.toDouble())
        if (rotation.z != 0f) {
            GlStateManager.rotate(rotation.z, 0f, 0f, 1f)
        }
        if (rotation.y != 0f) {
            GlStateManager.rotate(rotation.y, 0f, 1f, 0f)
        }
        if (rotation.x != 0f) {
            GlStateManager.rotate(rotation.x, 1f, 0f, 0f)
        }
    }

    private fun deriveBoxUvFaces(cube: LegacyGeoCube): Map<LegacyCubeFace, LegacyFaceUv> {
        val u = cube.defaultUv?.x ?: return emptyMap()
        val v = cube.defaultUv.y
        val sx = cube.size.x
        val sy = cube.size.y
        val sz = cube.size.z

        return linkedMapOf(
            LegacyCubeFace.WEST to LegacyFaceUv(u, v + sz, sz, sy),
            LegacyCubeFace.NORTH to LegacyFaceUv(u + sz, v + sz, sx, sy),
            LegacyCubeFace.EAST to LegacyFaceUv(u + sz + sx, v + sz, sz, sy),
            LegacyCubeFace.SOUTH to LegacyFaceUv(u + sz + sx + sz, v + sz, sx, sy),
            LegacyCubeFace.UP to LegacyFaceUv(u + sz, v, sx, sz),
            LegacyCubeFace.DOWN to LegacyFaceUv(u + sz + sx, v, sx, sz)
        )
    }

    private fun resolveGeoModel(display: GunDisplayDefinition?, minecraft: Minecraft): LegacyGeoModel? {
        val resource = TaczTextureResourceResolver.resolveExisting(
            rawPath = resolveRenderModelPath(display),
            sourceId = display?.sourceId,
            minecraft = minecraft
        ) ?: return null

        val cacheKey = resource.toString()
        geoModelCache[cacheKey]?.let { return it }

        val json = runCatching {
            minecraft.resourceManager.getResource(resource).use { res ->
                String(res.inputStream.readBytes(), StandardCharsets.UTF_8)
            }
        }.getOrNull() ?: return null

        val parsed = parseGeoModel(json) ?: return null
        geoModelCache[cacheKey] = parsed
        return parsed
    }


    private fun resolveAnimationPose(
        gunId: String,
        display: GunDisplayDefinition?,
        minecraft: Minecraft,
        enableSoundDispatch: Boolean,
        allowContextualFallback: Boolean
    ): ResolvedAnimationPose? {
        if (display == null) {
            return null
        }
        val player = minecraft.player as? AbstractClientPlayer ?: return null
        val sessionId = clientSessionId(player.uniqueID.toString())

        val cacheKey = "${sessionId}_${gunId}"
        var session = animationSessions[cacheKey]

        if (session == null) {
            val defaults = resolveObjectAnimations(display.defaultAnimationPath, display.useDefaultAnimation, true, minecraft) ?: emptyList()
            val primary = resolveObjectAnimations(display.animationPath, null, false, minecraft) ?: emptyList()

            val combined = mutableListOf<ObjectAnimation>()
            combined.addAll(defaults)
            combined.addAll(primary)

            if (combined.isEmpty()) return null
            val allBones = combined.flatMap { it.channels.keys }.toSet()
            session = GunAnimationControllerSession(combined, allBones)
            animationSessions[cacheKey] = session
        }

        val runtimeSnapshot = WeaponRuntimeMcBridge.animationSnapshotOrNull(sessionId)

        val state = renderAnimationStateBySessionId.getOrPut(sessionId) { RenderAnimationSessionState() }
        var actualRenderGunId = gunId
        var actualDisplay = display

        if (allowContextualFallback && state.lastGunId != gunId) {
            val prev = state.lastGunId
            state.lastGunId = gunId
            state.putAwayRenderGunId = prev
            state.putAwayStartedAtMillis = System.currentTimeMillis()

            if (prev != null) {
                val prevSession = animationSessions["${sessionId}_${prev}"]
                prevSession?.let {
                    it.syncFromSnapshots(listOf(
                        SessionTrack(
                            trackKey = "0:0",
                            animationName = "put_away",
                            playMode = SessionTrackPlayMode.PLAY_ONCE_STOP,
                            progress = 0f
                        )
                    ))
                }
            }
        }

        val now = System.currentTimeMillis()
        if (state.putAwayRenderGunId != null && now - state.putAwayStartedAtMillis < 500L) {
             actualRenderGunId = state.putAwayRenderGunId!!
             actualDisplay = GunDisplayRuntime.registry().snapshot().findDefinition(actualRenderGunId) ?: display
        } else {
             state.putAwayRenderGunId = null
        }

        val activeSession = if (actualRenderGunId == gunId) session else (animationSessions["${sessionId}_${actualRenderGunId}"] ?: session)

        if (actualRenderGunId == gunId && runtimeSnapshot != null && runtimeSnapshot.gunId.trim().lowercase() == gunId.lowercase()) {
            val tracks = mutableListOf<SessionTrack>()
            tracks.add(SessionTrack("0:0", "static_idle", SessionTrackPlayMode.LOOP, 0f))
            
            val clipName = mapClipTypeToAnimName(runtimeSnapshot.clip)
            if (clipName != null) {
                tracks.add(SessionTrack("1:0", clipName, SessionTrackPlayMode.PLAY_ONCE_HOLD, runtimeSnapshot.progress))
            }
            activeSession.syncFromSnapshots(tracks)
        }

        val prevRenderGunId = SoundPlayManager.currentRenderGunId
        if (enableSoundDispatch) {
            SoundPlayManager.currentRenderGunId = actualRenderGunId
        } else {
            SoundPlayManager.currentRenderGunId = ""
        }

        val pose = activeSession.updateAndCollectPose()

        SoundPlayManager.currentRenderGunId = prevRenderGunId

        return ResolvedAnimationPose(
            pose = pose,
            playbackGunId = actualRenderGunId,
            playbackDisplay = actualDisplay
        )
    }


    private fun mapClipTypeToAnimName(clip: WeaponAnimationClipType): String? {
        return when (clip) {
            WeaponAnimationClipType.FIRE -> "shoot"
            WeaponAnimationClipType.RELOAD -> "reload"
            WeaponAnimationClipType.DRAW -> "draw"
            WeaponAnimationClipType.PUT_AWAY -> "put_away"
            WeaponAnimationClipType.INSPECT -> "inspect"
            WeaponAnimationClipType.DRY_FIRE -> "dry_fire"
            WeaponAnimationClipType.BOLT -> "bolt"
            else -> null
        }
    }

    private fun resolveObjectAnimations(
        path: String?,
        useDefaultParam: String?,
        isDefaultState: Boolean,
        minecraft: Minecraft
    ): List<ObjectAnimation>? {
        if (path == null && !isDefaultState) return null

        val rawPath = if (isDefaultState) {
            resolveDefaultAnimationAssetPath(path, useDefaultParam) ?: return null
        } else {
            path!!
        }

        val resource = TaczTextureResourceResolver.resolveExisting(
            rawPath = rawPath,
            sourceId = null,
            minecraft = minecraft
        ) ?: return null

        val cacheKey = resource.toString()
        objectAnimationCache[cacheKey]?.let { return it }

        val json = runCatching {
            minecraft.resourceManager.getResource(resource).use { res ->
                String(res.inputStream.readBytes(), java.nio.charset.StandardCharsets.UTF_8)
            }
        }.getOrNull() ?: return null

        val bedrockFile = runCatching { com.google.gson.Gson().fromJson(json, BedrockAnimationFile::class.java) }.getOrNull() ?: return null
        val list = Animations.createAnimationFromBedrock(bedrockFile)
        objectAnimationCache[cacheKey] = list
        return list
    }
    internal fun resolveDefaultAnimationAssetPath(
        defaultAnimationPath: String?,
        useDefaultAnimation: String?
    ): String? {
        val explicit = defaultAnimationPath
            ?.trim()
            ?.ifBlank { null }
        if (explicit != null) {
            return explicit
        }
        return resolveDefaultAnimationAssetPath(useDefaultAnimation)
    }

    internal fun resolveDefaultAnimationAssetPath(useDefaultAnimation: String?): String? {
        val profile = useDefaultAnimation
            ?.trim()
            ?.lowercase()
            ?.ifBlank { null }
            ?: return null

        val fileName = when (profile) {
            "pistol" -> "pistol_default.animation.json"
            "rifle" -> "rifle_default.animation.json"
            else -> "rifle_default.animation.json"
        }

        return "assets/tacz/animations/$fileName"
    }


    private fun parseGeoModel(json: String): LegacyGeoModel? {
        val root = runCatching { JsonParser().parse(json) }.getOrNull()?.takeIf { it.isJsonObject }?.asJsonObject
            ?: return null
        val geometryArray = root.get("minecraft:geometry")
            ?.takeIf { it.isJsonArray }
            ?.asJsonArray
            ?: return null
        if (geometryArray.size() <= 0) {
            return null
        }

        val geometry = geometryArray[0].takeIf { it.isJsonObject }?.asJsonObject ?: return null
        val description = geometry.get("description")?.takeIf { it.isJsonObject }?.asJsonObject
        val textureWidth = description?.readFloat("texture_width")?.toInt()?.coerceAtLeast(1) ?: 64
        val textureHeight = description?.readFloat("texture_height")?.toInt()?.coerceAtLeast(1) ?: 64

        val bonesArray = geometry.get("bones")
            ?.takeIf { it.isJsonArray }
            ?.asJsonArray
            ?: return null

        val rawBones = mutableListOf<RawGeoBone>()

        bonesArray.forEach { element ->
            val boneObj = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
            val name = boneObj.readString("name")?.trim()?.ifBlank { null } ?: return@forEach
            val parent = boneObj.readString("parent")?.trim()?.ifBlank { null }
            val pivot = boneObj.readVec3("pivot") ?: LegacyVec3.ZERO
            val rotation = boneObj.readVec3("rotation") ?: LegacyVec3.ZERO
            val boneMirror = boneObj.readBoolean("mirror") ?: false

            val cubes = mutableListOf<RawGeoCube>()
            val cubesArray = boneObj.get("cubes")?.takeIf { it.isJsonArray }?.asJsonArray
            cubesArray?.forEach { cubeElement ->
                val cubeObj = cubeElement.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
                val origin = cubeObj.readVec3("origin") ?: return@forEach
                val size = cubeObj.readVec3("size") ?: return@forEach
                val cubePivot = cubeObj.readVec3("pivot")
                val cubeRotation = cubeObj.readVec3("rotation")
                val defaultUv = cubeObj.readVec2("uv")
                val faces = parseCubeFaces(cubeObj.get("uv"))
                val inflate = cubeObj.readFloat("inflate") ?: 0f
                val mirror = cubeObj.readBoolean("mirror") ?: boneMirror

                cubes += RawGeoCube(
                    origin = origin,
                    size = size,
                    pivot = cubePivot,
                    rotation = cubeRotation,
                    defaultUv = defaultUv,
                    faces = faces,
                    inflate = inflate,
                    mirror = mirror
                )
            }

            rawBones += RawGeoBone(
                name = name,
                parent = parent,
                pivot = pivot,
                rotation = rotation,
                cubes = cubes
            )
        }

        val rawBonesByName = rawBones.associateBy { it.name }
        val bonesByName = linkedMapOf<String, LegacyGeoBone>()
        val childrenByParent = linkedMapOf<String, MutableList<String>>()

        rawBones.forEach { rawBone ->
            val parentRawPivot = rawBone.parent
                ?.let(rawBonesByName::get)
                ?.pivot

            val convertedPivot = convertBonePivot(rawBone.pivot, parentRawPivot)
            val convertedCubes = rawBone.cubes.map { cube ->
                val hasCubeTransform = cube.pivot != null && cube.rotation != null
                val originPivot = if (hasCubeTransform) cube.pivot else rawBone.pivot
                LegacyGeoCube(
                    origin = convertCubeOrigin(cube.origin, requireNotNull(originPivot), cube.size),
                    size = cube.size,
                    pivot = cube.pivot
                        ?.takeIf { hasCubeTransform }
                        ?.let { rawPivot -> convertCubePivot(rawPivot, rawBone.pivot) },
                    rotation = cube.rotation?.takeIf { hasCubeTransform },
                    defaultUv = cube.defaultUv,
                    faces = cube.faces,
                    inflate = cube.inflate,
                    mirror = cube.mirror
                )
            }

            bonesByName[rawBone.name] = LegacyGeoBone(
                name = rawBone.name,
                parent = rawBone.parent,
                rawPivot = rawBone.pivot,
                pivot = convertedPivot,
                rotation = rawBone.rotation,
                cubes = convertedCubes
            )

            if (rawBone.parent != null) {
                childrenByParent.getOrPut(rawBone.parent) { mutableListOf() }.add(rawBone.name)
            }
        }

        val rootBones = bonesByName.values
            .filter { bone -> bone.parent == null || !bonesByName.containsKey(bone.parent) }
            .map { bone -> bone.name }

        return LegacyGeoModel(
            textureWidth = textureWidth,
            textureHeight = textureHeight,
            bonesByName = bonesByName,
            childrenByParent = childrenByParent,
            rootBones = rootBones
        )
    }

    private fun parseCubeFaces(uvElement: JsonElement?): Map<LegacyCubeFace, LegacyFaceUv> {
        val uvObj = uvElement?.takeIf { it.isJsonObject }?.asJsonObject ?: return emptyMap()
        val out = linkedMapOf<LegacyCubeFace, LegacyFaceUv>()
        LegacyCubeFace.values().forEach { face ->
            val sourceKey = when (face) {
                // Bedrock 每面 UV 数据在东/西方向上需要交换匹配到 Java 渲染方向。
                LegacyCubeFace.EAST -> LegacyCubeFace.WEST.key
                LegacyCubeFace.WEST -> LegacyCubeFace.EAST.key
                else -> face.key
            }

            val faceObj = uvObj.get(sourceKey)?.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
            val uv = faceObj.readVec2("uv") ?: return@forEach
            val uvSize = faceObj.readVec2("uv_size") ?: return@forEach
            out[face] = LegacyFaceUv(
                u = uv.x,
                v = uv.y,
                uvSizeU = uvSize.x,
                uvSizeV = uvSize.y
            )
        }
        return out
    }

    private fun renderFallbackQuad() {
        val tessellator = Tessellator.getInstance()
        val buffer = tessellator.buffer
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX)
        buffer.pos(0.0, 0.0, 0.0).tex(0.0, 1.0).endVertex()
        buffer.pos(1.0, 0.0, 0.0).tex(1.0, 1.0).endVertex()
        buffer.pos(1.0, 1.0, 0.0).tex(1.0, 0.0).endVertex()
        buffer.pos(0.0, 1.0, 0.0).tex(0.0, 0.0).endVertex()
        tessellator.draw()
    }

    internal fun resolveRenderTexturePath(display: GunDisplayDefinition?): String? {
        return display?.modelTexturePath
            ?: display?.lodTexturePath
            ?: display?.slotTexturePath
            ?: display?.hudTexturePath
    }

    internal fun resolveRenderModelPath(display: GunDisplayDefinition?): String? {
        return display?.modelPath ?: display?.lodModelPath
    }

    private fun JsonObject.readString(name: String): String? {
        val element = get(name) ?: return null
        if (!element.isJsonPrimitive || !element.asJsonPrimitive.isString) {
            return null
        }
        return element.asString
    }

    private fun JsonObject.readFloat(name: String): Float? {
        val element = get(name) ?: return null
        if (!element.isJsonPrimitive || !element.asJsonPrimitive.isNumber) {
            return null
        }
        return element.asFloat
    }

    private fun JsonObject.readBoolean(name: String): Boolean? {
        val element = get(name) ?: return null
        if (!element.isJsonPrimitive || !element.asJsonPrimitive.isBoolean) {
            return null
        }
        return element.asBoolean
    }

    private fun JsonObject.readVec3(name: String): LegacyVec3? {
        val element = get(name) ?: return null
        if (!element.isJsonArray) {
            return null
        }
        return parseVec3(element.asJsonArray)
    }

    private fun JsonObject.readVec2(name: String): LegacyVec2? {
        val element = get(name) ?: return null
        if (!element.isJsonArray) {
            return null
        }
        return parseVec2(element.asJsonArray)
    }

    private fun parseVec3(array: JsonArray): LegacyVec3? {
        if (array.size() < 3) {
            return null
        }

        val x = array[0].takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asFloat ?: return null
        val y = array[1].takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asFloat ?: return null
        val z = array[2].takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asFloat ?: return null
        return LegacyVec3(x, y, z)
    }

    private fun parseVec2(array: JsonArray): LegacyVec2? {
        if (array.size() < 2) {
            return null
        }

        val x = array[0].takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asFloat ?: return null
        val y = array[1].takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asFloat ?: return null
        return LegacyVec2(x, y)
    }

    private fun convertBonePivot(rawPivot: LegacyVec3, parentRawPivot: LegacyVec3?): LegacyVec3 {
        return if (parentRawPivot == null) {
            LegacyVec3(rawPivot.x, 24f - rawPivot.y, rawPivot.z)
        } else {
            LegacyVec3(
                rawPivot.x - parentRawPivot.x,
                parentRawPivot.y - rawPivot.y,
                rawPivot.z - parentRawPivot.z
            )
        }
    }

    private fun convertCubeOrigin(rawOrigin: LegacyVec3, boneRawPivot: LegacyVec3, size: LegacyVec3): LegacyVec3 {
        return LegacyVec3(
            rawOrigin.x - boneRawPivot.x,
            boneRawPivot.y - rawOrigin.y - size.y,
            rawOrigin.z - boneRawPivot.z
        )
    }

    private fun convertCubePivot(rawCubePivot: LegacyVec3, boneRawPivot: LegacyVec3): LegacyVec3 {
        return LegacyVec3(
            rawCubePivot.x - boneRawPivot.x,
            boneRawPivot.y - rawCubePivot.y,
            rawCubePivot.z - boneRawPivot.z
        )
    }

    private val DEFAULT_GUN_TEXTURE: ResourceLocation = ResourceLocation("tacz", "textures/hud/heat_bar.png")

    private data class LegacyAnimationSet(
        val clipsByName: Map<String, LegacyAnimationClip>,
        val normalizedNamesToClipNames: Map<String, String>
    )

    private data class LegacyAnimationClip(
        val name: String,
        val durationSeconds: Float,
        val loopMode: LegacyAnimationLoopMode,
        val boneTracksByName: Map<String, LegacyBoneAnimationTrack>,
        val soundEffects: List<LegacyAnimationSoundEffect>
    )

    private data class LegacyAnimationSoundEffect(
        val timeSeconds: Float,
        val effectId: String,
        val volume: Float,
        val pitch: Float
    )

    internal data class ParsedAnimationSoundEffectPayload(
        val effectId: String,
        val volume: Float,
        val pitch: Float
    )

    private data class LegacyBoneAnimationTrack(
        val rotationKeyframes: List<LegacyAnimationKeyframe>,
        val positionKeyframes: List<LegacyAnimationKeyframe>
    )

    private data class LegacyAnimationKeyframe(
        val timeSeconds: Float,
        val value: LegacyVec3
    )


    private data class ResolvedAnimationPose(
        val pose: LegacyAnimationPose,
        val playbackGunId: String,
        val playbackDisplay: GunDisplayDefinition
    )

    private data class ResolvedPlaybackClip(
        val clipType: WeaponAnimationClipType,
        val clipName: String,
        val progress: Float,
        val elapsedMillis: Long,
        val playbackGunId: String? = null
    )

    private data class RenderAnimationSessionState(
        var lastGunId: String? = null,
        var drawStartedAtMillis: Long = -1L,
        var putAwayStartedAtMillis: Long = -1L,
        var putAwayDurationMillis: Long = -1L,
        var putAwayRenderGunId: String? = null,
        var activeLoopClipType: WeaponAnimationClipType? = null,
        var activeLoopStartedAtMillis: Long = 0L
    )

    private data class AnimationSoundPlaybackState(
        var lastGunId: String,
        var lastClipName: String,
        var lastSampleSeconds: Float?,
        var lastPlayedSoundId: String?,
        var lastPlayedAtMillis: Long,
        var lastPlayedClipName: String?,
        var lastPlayedReplayKey: String?
    )

    private data class ResolvedAnimationSoundEvent(
        val soundId: String,
        val soundLocation: ResourceLocation
    )

    private data class AimingBlendState(
        var progress: Float,
        var lastUpdatedMillis: Long,
        var lastGunId: String? = null
    )

    private data class FirstPersonJumpSwayState(
        var progress: Float,
        var smoothedOffsetY: Float,
        var lastUpdatedMillis: Long,
        var lastOnGround: Boolean,
        var lastVerticalVelocity: Float,
        var lastGunId: String? = null
    )

    private data class FirstPersonShootSwayState(
        var fireStartedAtMillis: Long,
        var lastObservedFireElapsedMillis: Long,
        var lastWasFireClip: Boolean,
        var lastGunId: String? = null
    )

    private data class ClipPoseTransitionState(
        var lastGunId: String? = null,
        var lastClipKey: String? = null,
        var lastClipType: WeaponAnimationClipType? = null,
        var lastPose: com.tacz.legacy.client.render.item.LegacyAnimationPose? = null,
        var transitionFromPose: LegacyAnimationPose? = null,
        var transitionStartedAtMillis: Long = -1L,
        var transitionDurationMillis: Long = 0L
    )

    private data class RefitViewContinuityCache(
        var lastTransform: LegacyRigidTransform,
        var lastOpeningProgress: Float,
        var lastOldSlot: com.tacz.legacy.common.infrastructure.mc.weapon.WeaponAttachmentSlot?,
        var lastCurrentSlot: com.tacz.legacy.common.infrastructure.mc.weapon.WeaponAttachmentSlot?,
        var lastTransformProgress: Float
    )

    private data class FirstPersonHandContext(
        val minecraft: Minecraft,
        val player: AbstractClientPlayer,
        val renderPlayer: RenderPlayer,
        val gunTexture: ResourceLocation,
        var rightRendered: Boolean = false,
        var leftRendered: Boolean = false
    )

    internal data class CameraAnimationDelta(
        val pitchDegrees: Float,
        val yawDegrees: Float,
        val rollDegrees: Float,
        val axisX: Float,
        val axisY: Float,
        val axisZ: Float,
        val axisAngleDegrees: Float
    )

    public data class FirstPersonReferenceOffset(
        val x: Float,
        val y: Float,
        val z: Float
    )

    public data class FirstPersonReferenceOffsets(
        val muzzlePos: FirstPersonReferenceOffset?,
        val shell: FirstPersonReferenceOffset?,
        val muzzleFlash: FirstPersonReferenceOffset?
    ) {
        public companion object {
            public val EMPTY: FirstPersonReferenceOffsets = FirstPersonReferenceOffsets(
                muzzlePos = null,
                shell = null,
                muzzleFlash = null
            )
        }
    }

    internal data class FirstPersonHoldingSwayTransform(
        val preRotatePitchDegrees: Float,
        val preRotateYawDegrees: Float,
        val offsetX: Float,
        val offsetY: Float,
        val postRotatePitchDegrees: Float,
        val postRotateYawDegrees: Float
    )

    internal data class FirstPersonShootSwayTransform(
        val offsetX: Float,
        val offsetY: Float,
        val yawDegrees: Float
    ) {
        companion object {
            val ZERO: FirstPersonShootSwayTransform = FirstPersonShootSwayTransform(
                offsetX = 0f,
                offsetY = 0f,
                yawDegrees = 0f
            )
        }
    }

    private enum class LegacyAnimationLoopMode {
        NONE,
        LOOP,
        HOLD_ON_LAST_FRAME
    }

    internal data class LegacyBoneVisibilityPolicy(
        val strictAttachmentFiltering: Boolean,
        val hasScope: Boolean,
        val hasMuzzle: Boolean,
        val hasStock: Boolean,
        val hasGrip: Boolean,
        val hasLaser: Boolean,
        val hasGripOrLaser: Boolean,
        val extendedMagLevel: Int,
        val hasExtendedMag: Boolean,
        val showAttachmentAdapter: Boolean,
        val activeAttachmentAdapters: Set<String>,
        val allowAnyAdapterVariant: Boolean,
        val showAdditionalMagazine: Boolean,
        val showBulletInBarrel: Boolean,
        val showBulletInMag: Boolean
    ) {

        fun shouldRenderBone(name: String): Boolean {
            val normalized = name.trim().lowercase()
            if (normalized.isBlank()) {
                return true
            }

            // 通用定位组：*_pos 通常是配件/手臂挂点，*_view 通常是第一人称/改装界面视角挂点。
            if (normalized.endsWith("_pos") || normalized.endsWith("_view")) {
                return false
            }

            if (normalized in REFERENCE_LOCATOR_BONES) {
                return false
            }

            if (normalized == BULLET_IN_BARREL_BONE) {
                return showBulletInBarrel
            }

            if (normalized == BULLET_IN_MAG_BONE || normalized == BULLET_CHAIN_BONE) {
                return showBulletInMag
            }

            val defaultAttachmentVisibility = LegacyGunItemStackRenderer.resolveDefaultAttachmentBoneVisibility(
                boneName = normalized,
                hasScope = hasScope,
                hasMuzzle = hasMuzzle,
                hasStock = hasStock,
                hasGrip = hasGrip,
                hasLaser = hasLaser,
                hasExtendedMag = hasExtendedMag
            )
            if (defaultAttachmentVisibility != null) {
                return defaultAttachmentVisibility
            }

            if (!strictAttachmentFiltering) {
                return true
            }

            if (normalized == ADDITIONAL_MAGAZINE_BONE) {
                return showAdditionalMagazine
            }

            if (normalized == "attachment_adapter") {
                return showAttachmentAdapter
            }

            if (normalized == "mount") {
                return hasScope
            }

            if (normalized == "sight_folded") {
                return hasScope
            }

            if (normalized == "sight") {
                return !hasScope
            }

            if (normalized == "carry") {
                return !hasScope
            }

            if (normalized == "handguard_tactical") {
                return hasGripOrLaser
            }

            if (normalized == "handguard_default") {
                return !hasGripOrLaser
            }

            if (normalized == "mag_standard") {
                return extendedMagLevel == 0
            }

            if (normalized.startsWith("mag_extended_")) {
                val level = normalized.substringAfterLast('_').toIntOrNull() ?: return false
                return extendedMagLevel == level
            }

            if (normalized in ADAPTER_VARIANT_BONES) {
                if (!showAttachmentAdapter) {
                    return false
                }
                if (allowAnyAdapterVariant) {
                    return true
                }
                return normalized in activeAttachmentAdapters
            }

            return true
        }

        fun shouldHideDescendants(name: String, shouldRenderSelf: Boolean): Boolean {
            val normalized = name.trim().lowercase()
            if (!strictAttachmentFiltering) {
                return false
            }

            if (shouldRenderSelf) {
                return false
            }

            // 参考/定位骨骼只负责挂点，不应因为自身不渲染而吞掉子节点。
            if (normalized.endsWith("_pos") || normalized.endsWith("_view")) {
                return false
            }
            if (normalized in PASS_THROUGH_HIDDEN_BONES) {
                return false
            }

            // 对齐 TACZ：attachment_adapter 下通常是各类互斥转接件。
            // 当转接件整体不启用时，整棵子树都应隐藏，避免出现“悬空装饰件”。
            if (normalized == "attachment_adapter" && !showAttachmentAdapter) {
                return true
            }

            // 其余“被策略隐藏”的条件骨骼，默认隐藏整棵子树，
            // 避免 mag_extended_x / mount / oem_stock_xxx 等父骨隐藏但子网格仍渲染。
            return true
        }

        companion object {
            private val REFERENCE_LOCATOR_BONES: Set<String> = setOf(
                // 手部/动画参考定位组（上游由功能渲染器消费，不直接画立方体）
                "lefthand",
                "lefthand_pos",
                "righthand",
                "righthand_pos",

                // 视角/抛壳/枪口等定位组
                "camera",
                "constraint",
                "stock_pos",
                "muzzle_pos",
                "shell",
                "scope_pos",
                "muzzle_flash",

                // item display / refit 定位组
                "positioning",
                "positioning2",
                "thirdperson_hand",
                "fixed",
                "ground",
                "views",
                "refit_view",
                "refit_muzzle_view",
                "refit_stock_view",
                "refit_scope_view",
                "refit_extended_mag_view"
            )

            private val ADAPTER_VARIANT_BONES: Set<String> = setOf(
                "oem_stock_heavy",
                "oem_stock_light",
                "oem_stock_tactical",
                "ar_stock_adapter"
            )

            private val PASS_THROUGH_HIDDEN_BONES: Set<String> = setOf(
                // 手臂挂点链路：父节点隐藏但需要继续遍历到 *_pos 才能渲染手臂
                "lefthand",
                "righthand"
            )

            private const val ATTACHMENT_KEY_BASE: String = "Attachment"
            private val ATTACHMENT_KEY_CANDIDATES: Map<String, List<String>> = mapOf(
                "SCOPE" to listOf("AttachmentSCOPE", "Attachment_SCOPE", "attachment_scope"),
                "MUZZLE" to listOf("AttachmentMUZZLE", "Attachment_MUZZLE", "attachment_muzzle"),
                "EXTENDED_MAG" to listOf("AttachmentEXTENDED_MAG", "Attachment_EXTENDED_MAG", "attachment_extended_mag"),
                "STOCK" to listOf("AttachmentSTOCK", "Attachment_STOCK", "attachment_stock"),
                "GRIP" to listOf("AttachmentGRIP", "Attachment_GRIP", "attachment_grip"),
                "LASER" to listOf("AttachmentLASER", "Attachment_LASER", "attachment_laser")
            )

            private const val GUN_CURRENT_AMMO_COUNT_TAG: String = "GunCurrentAmmoCount"
            private const val GUN_HAS_BULLET_IN_BARREL_TAG: String = "HasBulletInBarrel"

            private val ATTACHMENT_INDEX_JSON_CACHE: MutableMap<String, JsonObject?> = linkedMapOf()
            private val ATTACHMENT_DISPLAY_JSON_CACHE: MutableMap<String, JsonObject?> = linkedMapOf()
            private val ATTACHMENT_DATA_JSON_CACHE: MutableMap<String, JsonObject?> = linkedMapOf()
            private val ATTACHMENT_ADAPTER_NODE_CACHE: MutableMap<String, String?> = linkedMapOf()
            private val ATTACHMENT_EXTENDED_MAG_LEVEL_CACHE: MutableMap<String, Int> = linkedMapOf()
            private val ATTACHMENT_ZOOM_LEVELS_CACHE: MutableMap<String, List<Float>?> = linkedMapOf()
            private val ATTACHMENT_VIEWS_FOV_CACHE: MutableMap<String, List<Float>?> = linkedMapOf()

            fun fromItemStack(
                itemStack: ItemStack,
                runtimeAnimationSnapshot: WeaponAnimationRuntimeSnapshot?
            ): LegacyBoneVisibilityPolicy {
                val rootTag = itemStack.tagCompound

                val scopeAttachmentId = rootTag?.let { readAttachmentId(it, "SCOPE") }
                val muzzleAttachmentId = rootTag?.let { readAttachmentId(it, "MUZZLE") }
                val extendedMagAttachmentId = rootTag?.let { readAttachmentId(it, "EXTENDED_MAG") }
                val stockAttachmentId = rootTag?.let { readAttachmentId(it, "STOCK") }
                val gripAttachmentId = rootTag?.let { readAttachmentId(it, "GRIP") }
                val laserAttachmentId = rootTag?.let { readAttachmentId(it, "LASER") }
                val hasStockAttachment = stockAttachmentId != null
                val stockAdapterNode = resolveAttachmentAdapterNode(stockAttachmentId)
                val activeAttachmentAdapters = stockAdapterNode
                    ?.let { setOf(it) }
                    ?: emptySet()
                val allowAnyAdapterVariant = hasStockAttachment && activeAttachmentAdapters.isEmpty()

                val currentAmmoCount = rootTag
                    ?.takeIf { it.hasKey(GUN_CURRENT_AMMO_COUNT_TAG) }
                    ?.getInteger(GUN_CURRENT_AMMO_COUNT_TAG)
                    ?: 0
                val hasBulletInBarrel = rootTag
                    ?.takeIf { it.hasKey(GUN_HAS_BULLET_IN_BARREL_TAG) }
                    ?.getBoolean(GUN_HAS_BULLET_IN_BARREL_TAG)
                    ?: false

                return LegacyBoneVisibilityPolicy(
                    strictAttachmentFiltering = true,
                    hasScope = scopeAttachmentId != null,
                    hasMuzzle = muzzleAttachmentId != null,
                    hasStock = stockAttachmentId != null,
                    hasGrip = gripAttachmentId != null,
                    hasLaser = laserAttachmentId != null,
                    hasGripOrLaser = gripAttachmentId != null || laserAttachmentId != null,
                    extendedMagLevel = resolveExtendedMagLevel(extendedMagAttachmentId),
                    hasExtendedMag = extendedMagAttachmentId != null,
                    showAttachmentAdapter = hasStockAttachment,
                    activeAttachmentAdapters = activeAttachmentAdapters,
                    allowAnyAdapterVariant = allowAnyAdapterVariant,
                    showAdditionalMagazine = LegacyGunItemStackRenderer.resolveAdditionalMagazineVisibility(
                        clipType = runtimeAnimationSnapshot?.clip,
                        progress = runtimeAnimationSnapshot?.progress
                    ),
                    showBulletInBarrel = hasBulletInBarrel,
                    showBulletInMag = currentAmmoCount > 0
                )
            }

            fun default(): LegacyBoneVisibilityPolicy = LegacyBoneVisibilityPolicy(
                strictAttachmentFiltering = true,
                hasScope = false,
                hasMuzzle = false,
                hasStock = false,
                hasGrip = false,
                hasLaser = false,
                hasGripOrLaser = false,
                extendedMagLevel = 0,
                hasExtendedMag = false,
                showAttachmentAdapter = false,
                activeAttachmentAdapters = emptySet(),
                allowAnyAdapterVariant = false,
                showAdditionalMagazine = false,
                showBulletInBarrel = false,
                showBulletInMag = false
            )

            internal fun clearAttachmentCaches() {
                ATTACHMENT_INDEX_JSON_CACHE.clear()
                ATTACHMENT_DISPLAY_JSON_CACHE.clear()
                ATTACHMENT_DATA_JSON_CACHE.clear()
                ATTACHMENT_ADAPTER_NODE_CACHE.clear()
                ATTACHMENT_EXTENDED_MAG_LEVEL_CACHE.clear()
                ATTACHMENT_ZOOM_LEVELS_CACHE.clear()
                ATTACHMENT_VIEWS_FOV_CACHE.clear()
            }

            internal fun readAttachmentId(rootTag: NBTTagCompound, type: String): String? {
                val candidates = ATTACHMENT_KEY_CANDIDATES[type]
                    ?: listOf("$ATTACHMENT_KEY_BASE$type", "${ATTACHMENT_KEY_BASE}_${type}")

                for (key in candidates) {
                    if (!rootTag.hasKey(key)) {
                        continue
                    }

                    val payload = rootTag.getCompoundTag(key)
                    val directId = payload.getString("id")
                    if (isValidAttachmentId(directId)) {
                        return directId.trim()
                    }

                    if (payload.hasKey("tag")) {
                        val itemTag = payload.getCompoundTag("tag")
                        val attachmentId = itemTag.getString("AttachmentId")
                        if (isValidAttachmentId(attachmentId)) {
                            return attachmentId.trim()
                        }
                    }
                }

                return null
            }

            internal fun readAttachmentZoomNumber(rootTag: NBTTagCompound, type: String): Int {
                val candidates = ATTACHMENT_KEY_CANDIDATES[type]
                    ?: listOf("$ATTACHMENT_KEY_BASE$type", "${ATTACHMENT_KEY_BASE}_${type}")

                for (key in candidates) {
                    if (!rootTag.hasKey(key)) {
                        continue
                    }

                    val payload = rootTag.getCompoundTag(key)
                    if (payload.hasKey("tag")) {
                        val itemTag = payload.getCompoundTag("tag")
                        if (itemTag.hasKey("ZoomNumber")) {
                            return itemTag.getInteger("ZoomNumber")
                        }
                    }
                }

                return 0
            }

            private fun isValidAttachmentId(value: String?): Boolean {
                val normalized = value?.trim()?.lowercase() ?: return false
                if (normalized.isBlank()) {
                    return false
                }
                if (normalized == "minecraft:air" || normalized == "air" || normalized == "tacz:empty") {
                    return false
                }
                if (normalized.endsWith(":air")) {
                    return false
                }
                return true
            }

            private fun resolveExtendedMagLevel(extendedMagAttachmentId: String?): Int {
                val normalized = normalizeAttachmentId(extendedMagAttachmentId) ?: return 0
                ATTACHMENT_EXTENDED_MAG_LEVEL_CACHE[normalized]?.let { return it }

                val levelFromData = readExtendedMagLevelFromData(normalized)
                val resolved = levelFromData ?: resolveExtendedMagLevelByHeuristic(normalized)
                ATTACHMENT_EXTENDED_MAG_LEVEL_CACHE[normalized] = resolved
                return resolved
            }

            private fun resolveExtendedMagLevelByHeuristic(normalizedAttachmentId: String): Int {
                return when {
                    normalizedAttachmentId.contains("level_3") || normalizedAttachmentId.contains("extended_mag_3") || normalizedAttachmentId.endsWith("iii") || normalizedAttachmentId.contains("_iii") -> 3
                    normalizedAttachmentId.contains("level_2") || normalizedAttachmentId.contains("extended_mag_2") || normalizedAttachmentId.endsWith("ii") || normalizedAttachmentId.contains("_ii") -> 2
                    else -> 1
                }
            }

            private fun readExtendedMagLevelFromData(attachmentId: String): Int? {
                val indexObject = readAttachmentIndexJson(attachmentId) ?: return null
                val dataId = readStringField(indexObject, "data") ?: return null
                val dataObject = readAttachmentDataJson(dataId) ?: return null
                val levelElement = dataObject.get("extended_mag_level") ?: return null
                if (!levelElement.isJsonPrimitive) {
                    return null
                }
                val primitive = levelElement.asJsonPrimitive
                val level = when {
                    primitive.isNumber -> primitive.asInt
                    primitive.isString -> primitive.asString.trim().toIntOrNull()
                    else -> null
                } ?: return null
                return level.coerceAtLeast(1)
            }

            private fun resolveAttachmentAdapterNode(attachmentId: String?): String? {
                val normalizedAttachmentId = normalizeAttachmentId(attachmentId) ?: return null
                return ATTACHMENT_ADAPTER_NODE_CACHE.getOrPut(normalizedAttachmentId) {
                    val indexObject = readAttachmentIndexJson(normalizedAttachmentId)
                    val displayId = readStringField(indexObject, "display")
                        ?: defaultDisplayIdForAttachment(normalizedAttachmentId)
                        ?: return@getOrPut null
                    val displayObject = readAttachmentDisplayJson(displayId) ?: return@getOrPut null
                    readStringField(displayObject, "adapter")
                        ?.trim()
                        ?.lowercase()
                        ?.ifBlank { null }
                }
            }

            internal fun resolveAttachmentZoomLevels(attachmentId: String?): List<Float>? {
                val normalizedAttachmentId = normalizeAttachmentId(attachmentId) ?: return null
                return ATTACHMENT_ZOOM_LEVELS_CACHE.getOrPut(normalizedAttachmentId) {
                    val indexObject = readAttachmentIndexJson(normalizedAttachmentId)
                    val displayId = readStringField(indexObject, "display")
                        ?: defaultDisplayIdForAttachment(normalizedAttachmentId)
                        ?: return@getOrPut null
                    val displayObject = readAttachmentDisplayJson(displayId) ?: return@getOrPut null
                    val zoomElement = displayObject.get("zoom")
                        ?.takeIf { it.isJsonArray }
                        ?.asJsonArray
                        ?: return@getOrPut null
                    val zoomLevels = zoomElement
                        .mapNotNull { element ->
                            if (!element.isJsonPrimitive) {
                                return@mapNotNull null
                            }
                            val primitive = element.asJsonPrimitive
                            when {
                                primitive.isNumber -> primitive.asFloat
                                primitive.isString -> primitive.asString.trim().toFloatOrNull()
                                else -> null
                            }
                        }
                        .map { zoom -> zoom.coerceAtLeast(1f) }
                    zoomLevels.takeIf { it.isNotEmpty() }
                }
            }

            internal fun resolveAttachmentViewsFov(attachmentId: String?): List<Float>? {
                val normalizedAttachmentId = normalizeAttachmentId(attachmentId) ?: return null
                return ATTACHMENT_VIEWS_FOV_CACHE.getOrPut(normalizedAttachmentId) {
                    val indexObject = readAttachmentIndexJson(normalizedAttachmentId)
                    val displayId = readStringField(indexObject, "display")
                        ?: defaultDisplayIdForAttachment(normalizedAttachmentId)
                        ?: return@getOrPut null
                    val displayObject = readAttachmentDisplayJson(displayId) ?: return@getOrPut null
                    val fovElement = displayObject.get("views_fov")
                        ?.takeIf { it.isJsonArray }
                        ?.asJsonArray
                        ?: return@getOrPut null
                    val values = fovElement
                        .mapNotNull { element ->
                            if (!element.isJsonPrimitive) {
                                return@mapNotNull null
                            }
                            val primitive = element.asJsonPrimitive
                            when {
                                primitive.isNumber -> primitive.asFloat
                                primitive.isString -> primitive.asString.trim().toFloatOrNull()
                                else -> null
                            }
                        }
                        .map { fov -> fov.coerceIn(1f, 179f) }
                    values.takeIf { it.isNotEmpty() }
                }
            }

            private fun readAttachmentIndexJson(attachmentId: String): JsonObject? {
                val normalizedAttachmentId = normalizeAttachmentId(attachmentId) ?: return null
                return ATTACHMENT_INDEX_JSON_CACHE.getOrPut(normalizedAttachmentId) {
                    val (namespace, path) = splitResourceId(normalizedAttachmentId) ?: return@getOrPut null
                    val resources = buildIndexResourceCandidates(namespace, path)
                    readJsonObjectFromCandidates(resources)
                }
            }

            private fun readAttachmentDisplayJson(displayId: String): JsonObject? {
                val normalizedDisplayId = normalizeAttachmentId(displayId) ?: return null
                return ATTACHMENT_DISPLAY_JSON_CACHE.getOrPut(normalizedDisplayId) {
                    val (namespace, path) = splitResourceId(normalizedDisplayId) ?: return@getOrPut null
                    val resources = buildDisplayResourceCandidates(namespace, path)
                    readJsonObjectFromCandidates(resources)
                }
            }

            private fun readAttachmentDataJson(dataId: String): JsonObject? {
                val normalizedDataId = normalizeAttachmentId(dataId) ?: return null
                return ATTACHMENT_DATA_JSON_CACHE.getOrPut(normalizedDataId) {
                    val (namespace, path) = splitResourceId(normalizedDataId) ?: return@getOrPut null
                    val resources = buildDataResourceCandidates(namespace, path)
                    readJsonObjectFromCandidates(resources)
                }
            }

            private fun buildIndexResourceCandidates(namespace: String, path: String): List<ResourceLocation> {
                val candidates = linkedSetOf<ResourceLocation>()
                attachmentPackIds().forEach { packId ->
                    appendResourceCandidate(
                        candidates = candidates,
                        namespace = namespace,
                        path = "custom/$packId/data/$namespace/index/attachments/$path.json"
                    )
                }
                appendResourceCandidate(candidates, namespace, "index/attachments/$path.json")
                appendResourceCandidate(candidates, namespace, "data/$namespace/index/attachments/$path.json")
                return candidates.toList()
            }

            private fun buildDisplayResourceCandidates(namespace: String, path: String): List<ResourceLocation> {
                val candidates = linkedSetOf<ResourceLocation>()
                attachmentPackIds().forEach { packId ->
                    appendResourceCandidate(
                        candidates = candidates,
                        namespace = namespace,
                        path = "custom/$packId/assets/$namespace/display/attachments/$path.json"
                    )
                }
                appendResourceCandidate(candidates, namespace, "display/attachments/$path.json")
                return candidates.toList()
            }

            private fun buildDataResourceCandidates(namespace: String, path: String): List<ResourceLocation> {
                val candidates = linkedSetOf<ResourceLocation>()
                attachmentPackIds().forEach { packId ->
                    appendResourceCandidate(
                        candidates = candidates,
                        namespace = namespace,
                        path = "custom/$packId/data/$namespace/data/attachments/$path.json"
                    )
                }
                appendResourceCandidate(candidates, namespace, "data/$namespace/data/attachments/$path.json")
                return candidates.toList()
            }

            private fun appendResourceCandidate(
                candidates: MutableSet<ResourceLocation>,
                namespace: String,
                path: String
            ) {
                runCatching { ResourceLocation(namespace, path) }
                    .getOrNull()
                    ?.let(candidates::add)
            }

            private fun attachmentPackIds(): List<String> {
                val snapshot = GunDisplayRuntime.registry().snapshot()
                val candidates = linkedSetOf<String>()
                snapshot.loadedDefinitionsByGunId.values.forEach { definition ->
                    extractPackId(definition.sourceId)?.let { candidates += it }
                }
                candidates += "tacz_default_gun"
                return candidates.toList()
            }

            private fun extractPackId(sourceId: String?): String? {
                val trimmed = sourceId?.trim().orEmpty()
                if (trimmed.isBlank()) {
                    return null
                }

                val rawPackId = trimmed
                    .substringBefore("!/")
                    .substringBefore('/')
                    .trim()
                if (rawPackId.isBlank()) {
                    return null
                }

                val withoutZip = if (rawPackId.endsWith(".zip", ignoreCase = true)) {
                    rawPackId.dropLast(4)
                } else {
                    rawPackId
                }

                val sanitized = withoutZip
                    .trim()
                    .lowercase()
                    .map { ch -> if (ch.isLetterOrDigit() || ch == '_' || ch == '-' || ch == '.') ch else '_' }
                    .joinToString(separator = "")
                    .replace("__+".toRegex(), "_")
                    .trim('_')

                return sanitized.ifBlank { null }
            }

            private fun readJsonObjectFromCandidates(resources: List<ResourceLocation>): JsonObject? {
                resources.forEach { resource ->
                    val json = readTextResource(resource) ?: return@forEach
                    val parsed = parseJsonObjectLenient(json) ?: return@forEach
                    return parsed
                }
                return null
            }

            private fun readTextResource(resource: ResourceLocation): String? {
                val minecraft = runCatching { Minecraft.getMinecraft() }.getOrNull() ?: return null
                return runCatching {
                    minecraft.resourceManager.getResource(resource).use { resolved ->
                        String(resolved.inputStream.readBytes(), StandardCharsets.UTF_8)
                    }
                }.getOrNull()
            }

            private fun parseJsonObjectLenient(rawJson: String): JsonObject? {
                val sanitizedJson = rawJson
                    .lineSequence()
                    .joinToString(separator = "\n") { line ->
                        line.substringBefore("//")
                    }
                val element = runCatching { JsonParser().parse(sanitizedJson) }.getOrNull() ?: return null
                return element.takeIf { it.isJsonObject }?.asJsonObject
            }

            private fun readStringField(json: JsonObject?, key: String): String? {
                val element = json?.get(key) ?: return null
                if (!element.isJsonPrimitive || !element.asJsonPrimitive.isString) {
                    return null
                }
                return element.asString.trim().ifBlank { null }
            }

            private fun splitResourceId(resourceId: String): Pair<String, String>? {
                val normalized = normalizeAttachmentId(resourceId) ?: return null
                val namespace = normalized.substringBefore(':')
                val path = normalized.substringAfter(':')
                if (namespace.isBlank() || path.isBlank()) {
                    return null
                }
                return namespace to path
            }

            private fun defaultDisplayIdForAttachment(attachmentId: String): String? {
                val (namespace, path) = splitResourceId(attachmentId) ?: return null
                return "$namespace:${path}_display"
            }

            private fun normalizeAttachmentId(value: String?): String? {
                val trimmed = value?.trim()?.lowercase()?.ifBlank { null } ?: return null
                val withNamespace = if (trimmed.contains(':')) trimmed else "tacz:$trimmed"
                return withNamespace
            }
        }
    }


    private const val MODEL_UNIT: Float = 16f
    private const val MAX_BONE_DEPTH: Int = 64
    private const val MAX_CHILD_BONES: Int = 64
    private const val MAX_CUBES_PER_BONE: Int = 256
    private const val MODEL_SCALE: Float = 0.6f
    private const val FIRST_PERSON_Z_OFFSET: Float = -0.72f
    private val FIRST_PERSON_POSITIONING_BONES: List<String> = listOf(
        IDLE_VIEW_BONE,
        IRON_VIEW_BONE,
        CAMERA_BONE
    )
    private val FIRST_PERSON_POSITIONING_FUZZY: List<String> = listOf(IDLE_VIEW_BONE, IRON_VIEW_BONE, CAMERA_BONE, "sight")
    private const val ENABLE_FIRST_PERSON_HANDS: Boolean = true
    private const val HAND_BONE_LEFT: String = "lefthand"
    private const val HAND_BONE_LEFT_POS: String = "lefthand_pos"
    private const val HAND_BONE_RIGHT: String = "righthand"
    private const val HAND_BONE_RIGHT_POS: String = "righthand_pos"
    private const val IDLE_VIEW_BONE: String = "idle_view"
    private const val IRON_VIEW_BONE: String = "iron_view"
    private const val CAMERA_BONE: String = "camera"
    private const val REFIT_VIEW_BONE: String = "refit_view"
    private const val REFIT_VIEW_PREFIX: String = "refit_"
    private const val REFIT_VIEW_SUFFIX: String = "_view"
    private const val MAGAZINE_BONE: String = "magazine"
    private const val ADDITIONAL_MAGAZINE_BONE: String = "additional_magazine"
    private const val BULLET_IN_BARREL_BONE: String = "bullet_in_barrel"
    private const val BULLET_IN_MAG_BONE: String = "bullet_in_mag"
    private const val BULLET_CHAIN_BONE: String = "bullet_chain"
    private const val ADDITIONAL_MAGAZINE_SHOW_PROGRESS_MIN: Float = 0.08f
    private const val ADDITIONAL_MAGAZINE_SHOW_PROGRESS_MAX: Float = 0.92f
    private const val CAMERA_EPSILON_DEGREES: Float = 1e-4f
    private const val CAMERA_EPSILON_RADIANS: Float = 1e-6f
    private const val FIRST_PERSON_SWAY_PRE_ROTATE_SCALE: Float = 0.1f
    private const val FIRST_PERSON_SWAY_POST_ROTATE_SCALE: Float = 0.05f
    private const val FIRST_PERSON_SWAY_TRANSLATION_SCALE: Float = FIRST_PERSON_SWAY_PRE_ROTATE_SCALE / MODEL_UNIT / 3f
    private const val FIRST_PERSON_SWAY_TANH_NORMALIZER: Float = 25f
    private const val FIRST_PERSON_SWAY_MAX_ANGLE_DEGREES: Float = 89f
    private const val FIRST_PERSON_JUMP_SWAY_Y_UNITS: Float = -2f
    private const val FIRST_PERSON_JUMP_VELOCITY_NORMALIZER: Float = 0.42f
    private const val FIRST_PERSON_LANDING_VELOCITY_NORMALIZER: Float = 0.1f
    private const val FIRST_PERSON_JUMP_SWAY_TIME_SECONDS: Float = 0.3f
    private const val FIRST_PERSON_LANDING_SWAY_TIME_SECONDS: Float = 0.15f
    private const val FIRST_PERSON_JUMP_SWAY_SMOOTH_TIME_SECONDS: Float = 0.08f
    private const val FIRST_PERSON_SHOOT_SWAY_X_NOISE_RANGE_UNITS: Float = 0.2f
    private const val FIRST_PERSON_SHOOT_SWAY_Y_UNITS: Float = -0.1f
    private const val FIRST_PERSON_SHOOT_SWAY_YAW_NOISE_RANGE_RADIANS: Float = 0.0136f
    private const val FIRST_PERSON_SHOOT_SWAY_TIME_MILLIS: Long = 300L
    private const val FIRST_PERSON_SHOOT_SWAY_X_NOISE_PERIOD_MILLIS: Float = 400f
    private const val FIRST_PERSON_SHOOT_SWAY_YAW_NOISE_PERIOD_MILLIS: Float = 100f
    private const val FIRE_RESTART_EPSILON_MILLIS: Long = 2L
    private const val FIRST_PERSON_SHOOT_X_SEED_SALT: Int = 0x51F15EED
    private const val FIRST_PERSON_SHOOT_YAW_SEED_SALT: Int = 0x2D7A2F1B
    private const val MILLIS_PER_SECOND_FLOAT: Float = 1000f
    private const val MICROS_PER_SECOND_DOUBLE: Double = 1_000_000.0
    private const val ENABLE_FIRST_PERSON_IDLE_MICRO_JITTER: Boolean = false
    private const val DEFAULT_AIM_TIME_SECONDS: Float = 0.2f
    private const val AIM_LOOP_PROGRESS_THRESHOLD: Float = 0.02f
    private const val AIM_LOOP_RELEASE_THRESHOLD: Float = 0.01f
    private const val MAX_AIMING_DELTA_MILLIS: Long = 250L
    private const val MAX_JUMP_SWAY_DELTA_MILLIS: Long = 250L
    private const val DEFAULT_ANIMATION_SOUND_NAMESPACE: String = "tacz"
    private const val MINECRAFT_NAMESPACE: String = "minecraft"
    private const val ANIMATION_SOUND_REPLAY_GUARD_MILLIS: Long = 20L
    private val REFERENCE_OFFSET_BONE_NAMES: Set<String> = setOf("muzzle_pos", "shell", "muzzle_flash")
    private val MODEL_VIEW_MATRIX_BUFFER: ThreadLocal<FloatBuffer> = ThreadLocal.withInitial {
        BufferUtils.createFloatBuffer(16)
    }
    private val RUNTIME_PRIORITY_CLIP_TYPES: Set<WeaponAnimationClipType> = setOf(
        WeaponAnimationClipType.DRAW,
        WeaponAnimationClipType.PUT_AWAY,
        WeaponAnimationClipType.FIRE,
        WeaponAnimationClipType.RELOAD,
        WeaponAnimationClipType.INSPECT,
        WeaponAnimationClipType.DRY_FIRE,
        WeaponAnimationClipType.BOLT
    )
    private val STATIC_BASE_CLIP_CANDIDATES: List<String> = listOf(
        "static_idle",
        "idle_static",
        "base_idle"
    )
    private val AIM_CONTEXTUAL_CLIP_KEYWORDS: List<String> = listOf(
        "aim",
        "ads",
        "aiming",
        "aim_idle"
    )
    private const val WALK_AIMING_PROGRESS_THRESHOLD: Float = 0.5f
    private const val MOVEMENT_INPUT_EPSILON: Float = 0.01f


    private fun lerp(fraction: Float, start: Float, end: Float): Float {
        return start + fraction * (end - start)
    }

    private fun parseVec2(data: Any?): FloatArray? {
        if (data is List<*>) {
            if (data.size >= 2) {
                val array = FloatArray(2)
                for (i in 0..1) {
                    val p = data[i]
                    array[i] = if (p is Number) p.toFloat() else 0f
                }
                return array
            }
        } else if (data is FloatArray && data.size >= 2) {
            return data
        } else if (data is DoubleArray && data.size >= 2) {
            return floatArrayOf(data[0].toFloat(), data[1].toFloat())
        }
        return null
    }
    private fun lerp(vec1: LegacyVec3, vec2: LegacyVec3, fraction: Float): LegacyVec3 {
        return LegacyVec3(
            lerp(fraction, vec1.x, vec2.x),
            lerp(fraction, vec1.y, vec2.y),
            lerp(fraction, vec1.z, vec2.z)
        )
    }
}
