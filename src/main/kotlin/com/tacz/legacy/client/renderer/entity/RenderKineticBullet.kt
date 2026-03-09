package com.tacz.legacy.client.renderer.entity

import com.google.gson.GsonBuilder
import com.google.gson.stream.JsonReader
import com.tacz.legacy.TACZLegacy
import com.tacz.legacy.client.event.FirstPersonRenderGunEvent
import com.tacz.legacy.client.model.BedrockAmmoModel
import com.tacz.legacy.client.model.bedrock.BedrockModel
import com.tacz.legacy.client.resource.TACZClientAssetManager
import com.tacz.legacy.client.resource.pojo.display.ammo.AmmoDisplay
import com.tacz.legacy.client.resource.pojo.model.BedrockModelPOJO
import com.tacz.legacy.client.resource.pojo.model.BedrockVersion
import com.tacz.legacy.client.resource.pojo.model.CubesItem
import com.tacz.legacy.common.entity.EntityKineticBullet
import com.tacz.legacy.common.foundation.FocusedSmokeRuntime
import com.tacz.legacy.common.resource.TACZGunPackPresentation
import com.tacz.legacy.common.resource.TACZGunPackRuntimeRegistry
import com.tacz.legacy.util.ColorHex
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.BufferBuilder
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.Tessellator
import net.minecraft.client.renderer.culling.ICamera
import net.minecraft.client.renderer.entity.Render
import net.minecraft.client.renderer.entity.RenderManager
import net.minecraft.client.renderer.texture.TextureMap
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import net.minecraft.util.ResourceLocation
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.Vec3d
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f
import org.lwjgl.opengl.GL11
import java.io.StringReader
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.nio.charset.StandardCharsets
import java.util.LinkedHashMap
import org.lwjgl.BufferUtils
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

@SideOnly(Side.CLIENT)
internal class RenderKineticBullet(renderManager: RenderManager) : Render<EntityKineticBullet>(renderManager) {
    companion object {
        private const val CAMERA_SETUP_YAW_OFFSET = 180.0f

        private val tracerDebugEnabled: Boolean
            get() = java.lang.Boolean.getBoolean("tacz.tracerDebug") ||
                java.lang.Boolean.parseBoolean(System.getProperty("tacz.focusedSmoke.tracerDebug", "false"))

        private val ammoModelCache = LinkedHashMap<ResourceLocation, BedrockAmmoModel?>()
        private val focusedSmokeTracerLogged = HashSet<Int>()
        private val focusedSmokeBulletRenderLogged = HashSet<Int>()
        private val focusedSmokeTracerSkipLogged = HashSet<Int>()
        private val focusedSmokeTracerFrameLogged = HashSet<Int>()
        private val tracerDebugProjectionLogged = HashSet<Int>()
        private val tracerDebugFirstPersonLatchLogged = HashSet<Int>()
        private val internalModelGson = GsonBuilder()
            .registerTypeAdapter(CubesItem::class.java, CubesItem.Deserializer())
            .create()
        private val defaultTracerTexture = ResourceLocation(TACZLegacy.MOD_ID, "textures/entity/basic_bullet.png")
        private var defaultTracerModel: BedrockModel? = null
        private var defaultTracerModelResolved = false
        private val tracerModelViewBuffer: FloatBuffer = BufferUtils.createFloatBuffer(16)
        private val tracerProjectionBuffer: FloatBuffer = BufferUtils.createFloatBuffer(16)
        private val tracerViewportBuffer: IntBuffer = BufferUtils.createIntBuffer(16)
    }

    override fun doRender(entity: EntityKineticBullet, x: Double, y: Double, z: Double, entityYaw: Float, partialTicks: Float) {
        val snapshot = TACZGunPackRuntimeRegistry.getSnapshot()
        val ammoDisplayId = TACZGunPackPresentation.resolveAmmoDisplayId(snapshot, entity.ammoId)
        val ammoDisplay = ammoDisplayId?.let(TACZClientAssetManager::getAmmoDisplay)
        val gunDisplay = TACZClientAssetManager.getGunDisplay(entity.gunDisplayId)

        if (java.lang.Boolean.getBoolean("tacz.focusedSmoke") && focusedSmokeBulletRenderLogged.add(entity.entityId)) {
            TACZLegacy.logger.info(
                "[FocusedSmoke] BULLET_RENDERER_ACTIVE entityId={} gun={} ammo={} tracer={} ammoDisplay={} gunDisplay={}",
                entity.entityId,
                entity.gunId,
                entity.ammoId,
                entity.isTracerAmmo(),
                ammoDisplayId,
                entity.gunDisplayId,
            )
        }

        GlStateManager.pushMatrix()
        GlStateManager.translate(x.toFloat(), y.toFloat(), z.toFloat())

        renderAmmoModel(entity, ammoDisplay, partialTicks)
        if (entity.isTracerAmmo()) {
            val tracerColorText = gunDisplay?.gunAmmo?.tracerColor ?: ammoDisplay?.tracerColor
            renderTracer(entity, tracerColorText, partialTicks, x, y, z)
        }

        GlStateManager.popMatrix()
    }

    override fun shouldRender(livingEntity: EntityKineticBullet, camera: ICamera, cameraX: Double, cameraY: Double, cameraZ: Double): Boolean {
        var aabb = livingEntity.renderBoundingBox.grow(0.5)
        if (aabb.hasNaN() || aabb.averageEdgeLength == 0.0) {
            aabb = AxisAlignedBB(
                livingEntity.posX - 2.0,
                livingEntity.posY - 2.0,
                livingEntity.posZ - 2.0,
                livingEntity.posX + 2.0,
                livingEntity.posY + 2.0,
                livingEntity.posZ + 2.0,
            )
        }
        return livingEntity.isInRangeToRender3d(cameraX, cameraY, cameraZ) &&
            (livingEntity.ignoreFrustumCheck || camera.isBoundingBoxInFrustum(aabb))
    }

    override fun getEntityTexture(entity: EntityKineticBullet): ResourceLocation = TextureMap.LOCATION_MISSING_TEXTURE

    private fun renderAmmoModel(entity: EntityKineticBullet, ammoDisplay: AmmoDisplay?, partialTicks: Float) {
        val display = ammoDisplay ?: return
        val entityDisplay = display.ammoEntity ?: return
        val modelLocation = entityDisplay.modelLocation ?: return
        val modelData = TACZClientAssetManager.getModel(modelLocation) ?: return
        val ammoModel = ammoModelCache.getOrPut(modelLocation) { BedrockAmmoModel(modelData.pojo, modelData.version) } ?: return
        val textureLocation = entityDisplay.modelTexture?.let(TACZClientAssetManager::getTextureLocation) ?: return

        val yaw = entity.prevRotationYaw + (entity.rotationYaw - entity.prevRotationYaw) * partialTicks - 180.0f
        val pitch = entity.prevRotationPitch + (entity.rotationPitch - entity.prevRotationPitch) * partialTicks

        GlStateManager.pushMatrix()
        Minecraft.getMinecraft().textureManager.bindTexture(textureLocation)
        GlStateManager.rotate(yaw, 0.0f, 1.0f, 0.0f)
        GlStateManager.rotate(pitch, 1.0f, 0.0f, 0.0f)
        GlStateManager.translate(0.0f, 1.5f, 0.0f)
        GlStateManager.scale(-1.0f, -1.0f, 1.0f)
        GlStateManager.enableLighting()
        GlStateManager.enableRescaleNormal()
        GlStateManager.enableBlend()
        GlStateManager.tryBlendFuncSeparate(
            GlStateManager.SourceFactor.SRC_ALPHA,
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
            GlStateManager.SourceFactor.ONE,
            GlStateManager.DestFactor.ZERO,
        )
        ammoModel.render()
        GlStateManager.disableBlend()
        GlStateManager.disableRescaleNormal()
        GlStateManager.popMatrix()
    }

    private fun renderTracer(entity: EntityKineticBullet, tracerColorText: String?, partialTicks: Float, renderX: Double, renderY: Double, renderZ: Double) {
        val shooter = entity.getShooterForRender() ?: return
        val mc = Minecraft.getMinecraft()
        val isFirstPerson = shooter == mc.player && mc.gameSettings.thirdPersonView == 0
        val bulletPosition = entity.interpolatePosition(partialTicks)
        val eyePosition = shooter.getPositionEyes(partialTicks)
        val disToEye = bulletPosition.distanceTo(eyePosition)
        val tracerLengthMultiplier = FocusedSmokeRuntime.tracerLengthMultiplier.toDouble()
        val tracerSizeMultiplier = FocusedSmokeRuntime.tracerSizeMultiplier
        var trailLength = 0.85 * sqrt(entity.motionX * entity.motionX + entity.motionY * entity.motionY + entity.motionZ * entity.motionZ)
        trailLength = min(trailLength, disToEye * 0.8)
        trailLength *= tracerLengthMultiplier
        if (trailLength <= 1.0E-4) {
            return
        }

        val yaw = entity.prevRotationYaw + (entity.rotationYaw - entity.prevRotationYaw) * partialTicks - 180.0f
        val pitch = entity.prevRotationPitch + (entity.rotationPitch - entity.prevRotationPitch) * partialTicks
        val width = (0.005f * max(1.0, disToEye / 3.5)).toFloat() * tracerSizeMultiplier
        val rgb = tracerColorText.toRgbColor()
        if (entity.ticksExisted < 5 && disToEye <= 2.0) {
            if (java.lang.Boolean.getBoolean("tacz.focusedSmoke") && focusedSmokeTracerSkipLogged.add(entity.entityId)) {
                TACZLegacy.logger.info(
                    "[FocusedSmoke] TRACER_SKIP_CLOSE_RANGE entityId={} gun={} ammo={} ticks={} distance={} length={}",
                    entity.entityId,
                    entity.gunId,
                    entity.ammoId,
                    entity.ticksExisted,
                    disToEye,
                    trailLength,
                )
            }
            return
        }

        if (java.lang.Boolean.getBoolean("tacz.focusedSmoke") && focusedSmokeTracerLogged.add(entity.entityId)) {
            TACZLegacy.logger.info(
                "[FocusedSmoke] TRACER_RENDERED gun={} ammo={} firstPerson={} color={} distance={} length={}",
                entity.gunId,
                entity.ammoId,
                isFirstPerson,
                tracerColorText ?: "#FFFFFF",
                disToEye,
                trailLength,
            )
        }

        GlStateManager.pushMatrix()
        var debugFirstPersonOffset: Vector3f? = null
        var debugOffsetReducer = 0.0
        val debugOriginBeforeOffset = projectCurrentPoint(0.0f, 0.0f, 0.0f)
        var debugOriginAfterOffset = debugOriginBeforeOffset
        if (isFirstPerson) {
            var firstPersonOffset = entity.firstPersonRenderOffset
            if (firstPersonOffset == null) {
                firstPersonOffset = FirstPersonRenderGunEvent.getCachedMuzzleRenderOffset()
                val player = mc.player
                if (firstPersonOffset != null && player != null) {
                    val muzzleCameraPitch = FirstPersonRenderGunEvent.getCachedMuzzleCameraPitch()
                    val muzzleCameraYawRaw = FirstPersonRenderGunEvent.getCachedMuzzleCameraYaw()
                    val muzzleCameraYaw = normalizeTracerCameraYaw(muzzleCameraYawRaw)
                    val currentCachedCameraPitch = FirstPersonRenderGunEvent.getCachedCameraPitch()
                    val currentCachedCameraYawRaw = FirstPersonRenderGunEvent.getCachedCameraYaw()
                    val currentCachedCameraYaw = normalizeTracerCameraYaw(currentCachedCameraYawRaw)
                    entity.firstPersonRenderOffset = Vector3f(firstPersonOffset)
                    // Use the CURRENT frame's camera angle for the sandwich rotation.
                    // The GL modelview has the current frame's orientCamera rotation baked in;
                    // the sandwich must undo exactly that rotation, not the previous frame's.
                    // Upstream uses camera.getYRot() which is also the current frame's angle.
                    entity.firstPersonCameraPitch = currentCachedCameraPitch
                        ?: muzzleCameraPitch
                        ?: (player.prevRotationPitch + (player.rotationPitch - player.prevRotationPitch) * partialTicks)
                    entity.firstPersonCameraYaw = currentCachedCameraYaw
                        ?: muzzleCameraYaw
                        ?: (player.prevRotationYaw + (player.rotationYaw - player.prevRotationYaw) * partialTicks)
                    if (tracerDebugEnabled && tracerDebugFirstPersonLatchLogged.add(entity.entityId)) {
                        TACZLegacy.logger.info(
                            "[TracerDebug] FIRST_PERSON_LATCH entityId={} gun={} muzzleFrameId={} muzzleOffset=({},{},{}) muzzleCameraYawRaw={} muzzleCameraYaw={} muzzleCameraPitch={} currentCameraYawRaw={} currentCameraYaw={} currentCameraPitch={} playerYaw={} playerPitch={}",
                            entity.entityId,
                            entity.gunId,
                            FirstPersonRenderGunEvent.getCachedMuzzleFrameId() ?: -1L,
                            "%.4f".format(firstPersonOffset.x),
                            "%.4f".format(firstPersonOffset.y),
                            "%.4f".format(firstPersonOffset.z),
                            "%.4f".format(muzzleCameraYawRaw ?: Float.NaN),
                            "%.4f".format(muzzleCameraYaw ?: Float.NaN),
                            "%.4f".format(muzzleCameraPitch ?: Float.NaN),
                            "%.4f".format(currentCachedCameraYawRaw ?: Float.NaN),
                            "%.4f".format(currentCachedCameraYaw ?: Float.NaN),
                            "%.4f".format(currentCachedCameraPitch ?: Float.NaN),
                            "%.4f".format(player.rotationYaw),
                            "%.4f".format(player.rotationPitch),
                        )
                    }
                }
            }
            if (firstPersonOffset != null) {
                val offsetReducer = max(0.0, (50.0 - disToEye)) / 50.0
                debugOffsetReducer = offsetReducer
                debugFirstPersonOffset = Vector3f(firstPersonOffset)
                GlStateManager.rotate(-(entity.firstPersonCameraYaw + 180.0f), 0.0f, 1.0f, 0.0f)
                GlStateManager.rotate(-entity.firstPersonCameraPitch, 1.0f, 0.0f, 0.0f)
                GlStateManager.translate(
                    firstPersonOffset.x * offsetReducer.toFloat(),
                    firstPersonOffset.y * offsetReducer.toFloat(),
                    firstPersonOffset.z * offsetReducer.toFloat(),
                )
                GlStateManager.rotate(entity.firstPersonCameraPitch, 1.0f, 0.0f, 0.0f)
                GlStateManager.rotate(entity.firstPersonCameraYaw + 180.0f, 0.0f, 1.0f, 0.0f)
                debugOriginAfterOffset = projectCurrentPoint(0.0f, 0.0f, 0.0f)
            }
        }
        GlStateManager.rotate(yaw, 0.0f, 1.0f, 0.0f)
        GlStateManager.rotate(pitch, 1.0f, 0.0f, 0.0f)
        GlStateManager.translate(0.0f, if (isFirstPerson) 0.0f else -0.2f, (trailLength / 2.0).toFloat())
        GlStateManager.scale(width, width, trailLength.toFloat())
        logTracerDebugProjection(
            entity = entity,
            isFirstPerson = isFirstPerson,
            renderX = renderX,
            renderY = renderY,
            renderZ = renderZ,
            bulletPosition = bulletPosition,
            trailLength = trailLength,
            width = width,
            firstPersonOffset = debugFirstPersonOffset,
            offsetReducer = debugOffsetReducer,
            originScreenBeforeOffset = debugOriginBeforeOffset,
            originScreenAfterOffset = debugOriginAfterOffset,
        )
        if (!drawTracerModel(rgb[0], rgb[1], rgb[2])) {
            drawTracerBeam(rgb[0], rgb[1], rgb[2])
        }
        if (java.lang.Boolean.getBoolean("tacz.focusedSmoke") && focusedSmokeTracerFrameLogged.add(entity.entityId)) {
            TACZLegacy.logger.info(
                "[FocusedSmoke] TRACER_FRAME_DRAWN gun={} ammo={} firstPerson={} color={} distance={} length={}",
                entity.gunId,
                entity.ammoId,
                isFirstPerson,
                tracerColorText ?: "#FFFFFF",
                disToEye,
                trailLength,
            )
            FocusedSmokeRuntime.markTracerFrameObserved(
                "gun=${entity.gunId} ammo=${entity.ammoId} firstPerson=$isFirstPerson distance=$disToEye length=$trailLength"
            )
        }
        GlStateManager.popMatrix()
    }

    private fun logTracerDebugProjection(
        entity: EntityKineticBullet,
        isFirstPerson: Boolean,
        renderX: Double,
        renderY: Double,
        renderZ: Double,
        bulletPosition: Vec3d,
        trailLength: Double,
        width: Float,
        firstPersonOffset: Vector3f?,
        offsetReducer: Double,
        originScreenBeforeOffset: String,
        originScreenAfterOffset: String,
    ) {
        if (!tracerDebugEnabled) {
            return
        }
        if (!tracerDebugProjectionLogged.add(entity.entityId)) {
            return
        }
        val center = projectCurrentPoint(0.0f, 0.0f, 0.0f)
        val tail = projectCurrentPoint(0.0f, 0.0f, -0.5f)
        val head = projectCurrentPoint(0.0f, 0.0f, 0.5f)
        val gunId = FirstPersonRenderGunEvent.getCachedMuzzleGunId() ?: entity.gunId
        val muzzleOffset = firstPersonOffset ?: Vector3f()
        TACZLegacy.logger.info(
            "[TracerDebug] TRACER_PROJECTION entityId={} gun={} firstPerson={} baseRender=({},{},{}) bulletPos=({},{},{}) muzzleOffset=({},{},{}) reducer={} width={} length={} originBeforeOffset={} originAfterOffset={} centerScreen={} tailScreen={} headScreen={} cameraYaw={} cameraPitch={}",
            entity.entityId,
            gunId,
            isFirstPerson,
            "%.4f".format(renderX),
            "%.4f".format(renderY),
            "%.4f".format(renderZ),
            "%.4f".format(bulletPosition.x),
            "%.4f".format(bulletPosition.y),
            "%.4f".format(bulletPosition.z),
            "%.4f".format(muzzleOffset.x),
            "%.4f".format(muzzleOffset.y),
            "%.4f".format(muzzleOffset.z),
            "%.4f".format(offsetReducer),
            "%.5f".format(width),
            "%.4f".format(trailLength),
            originScreenBeforeOffset,
            originScreenAfterOffset,
            center,
            tail,
            head,
            "%.4f".format(entity.firstPersonCameraYaw),
            "%.4f".format(entity.firstPersonCameraPitch),
        )
    }

    private fun projectCurrentPoint(x: Float, y: Float, z: Float): String {
        tracerModelViewBuffer.clear()
        tracerProjectionBuffer.clear()
        tracerViewportBuffer.clear()
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, tracerModelViewBuffer)
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, tracerProjectionBuffer)
        GL11.glGetInteger(GL11.GL_VIEWPORT, tracerViewportBuffer)
        tracerModelViewBuffer.rewind()
        tracerProjectionBuffer.rewind()
        tracerViewportBuffer.rewind()

        val modelView = Matrix4f().set(tracerModelViewBuffer)
        val projection = Matrix4f().set(tracerProjectionBuffer)
        val viewportX = tracerViewportBuffer.get(0)
        val viewportY = tracerViewportBuffer.get(1)
        val viewportWidth = tracerViewportBuffer.get(2)
        val viewportHeight = tracerViewportBuffer.get(3)

        val clip = Vector4f(x, y, z, 1.0f)
        modelView.transform(clip)
        projection.transform(clip)
        if (kotlin.math.abs(clip.w) <= 1.0E-6f) {
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

    private fun drawTracerModel(red: Float, green: Float, blue: Float): Boolean {
        val tracerModel = resolveDefaultTracerModel() ?: return false
        val mc = Minecraft.getMinecraft()
        mc.textureManager.bindTexture(defaultTracerTexture)
        GlStateManager.enableTexture2D()
        GlStateManager.disableLighting()
        GlStateManager.disableCull()
        GlStateManager.enableBlend()
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE)
        GlStateManager.depthMask(false)
        GlStateManager.color(red, green, blue, 1.0f)
        tracerModel.render()
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f)
        GlStateManager.depthMask(true)
        GlStateManager.disableBlend()
        GlStateManager.enableCull()
        GlStateManager.enableLighting()
        return true
    }

    private fun resolveDefaultTracerModel(): BedrockModel? {
        if (defaultTracerModelResolved) {
            return defaultTracerModel
        }
        defaultTracerModelResolved = true
        val resourcePath = "assets/${TACZLegacy.MOD_ID}/models/bedrock/basic_bullet.json"
        val json = RenderKineticBullet::class.java.classLoader
            .getResourceAsStream(resourcePath)
            ?.bufferedReader(StandardCharsets.UTF_8)
            ?.use { it.readText() }
        if (json == null) {
            TACZLegacy.logger.warn("[RenderKineticBullet] Failed to open bundled tracer model: {}", resourcePath)
            return null
        }
        return runCatching {
            val reader = JsonReader(StringReader(json))
            reader.isLenient = true
            val pojo = internalModelGson.fromJson<BedrockModelPOJO>(reader, BedrockModelPOJO::class.java)
            val version = BedrockVersion.fromPojo(pojo)
                ?: error("unknown bedrock format version ${pojo.formatVersion}")
            BedrockModel(pojo, version)
        }.onFailure { error ->
            TACZLegacy.logger.warn("[RenderKineticBullet] Failed to parse bundled tracer model: {}", error.message)
        }.getOrNull().also {
            defaultTracerModel = it
        }
    }

    private fun drawTracerBeam(red: Float, green: Float, blue: Float) {
        GlStateManager.disableTexture2D()
        GlStateManager.disableLighting()
        GlStateManager.disableCull()
        GlStateManager.enableBlend()
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE)
        GlStateManager.depthMask(false)

        val tessellator = Tessellator.getInstance()
        val buffer = tessellator.buffer
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR)
        addCrossQuad(buffer, red, green, blue, 0.85f, 0.1f)
        tessellator.draw()

        GlStateManager.depthMask(true)
        GlStateManager.disableBlend()
        GlStateManager.enableCull()
        GlStateManager.enableLighting()
        GlStateManager.enableTexture2D()
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f)
    }

    private fun addCrossQuad(buffer: BufferBuilder, red: Float, green: Float, blue: Float, headAlpha: Float, tailAlpha: Float) {
        addQuad(buffer, -0.5f, 0.0f, -0.5f, 0.5f, 0.0f, 0.5f, red, green, blue, headAlpha, tailAlpha)
        addQuad(buffer, 0.0f, -0.5f, -0.5f, 0.0f, 0.5f, 0.5f, red, green, blue, headAlpha, tailAlpha)
    }

    private fun addQuad(
        buffer: BufferBuilder,
        fromX: Float,
        fromY: Float,
        fromZ: Float,
        toX: Float,
        toY: Float,
        toZ: Float,
        red: Float,
        green: Float,
        blue: Float,
        headAlpha: Float,
        tailAlpha: Float,
    ) {
        buffer.pos(fromX.toDouble(), fromY.toDouble(), fromZ.toDouble()).color(red, green, blue, tailAlpha).endVertex()
        buffer.pos(toX.toDouble(), toY.toDouble(), fromZ.toDouble()).color(red, green, blue, tailAlpha).endVertex()
        buffer.pos(toX.toDouble(), toY.toDouble(), toZ.toDouble()).color(red, green, blue, headAlpha).endVertex()
        buffer.pos(fromX.toDouble(), fromY.toDouble(), toZ.toDouble()).color(red, green, blue, headAlpha).endVertex()
    }

    private fun EntityKineticBullet.interpolatePosition(partialTicks: Float): Vec3d {
        val x = lastTickPosX + (posX - lastTickPosX) * partialTicks
        val y = lastTickPosY + (posY - lastTickPosY) * partialTicks
        val z = lastTickPosZ + (posZ - lastTickPosZ) * partialTicks
        return Vec3d(x, y, z)
    }

    private fun String?.toRgbColor(): FloatArray {
        val rgb = ColorHex.colorTextToRgbInt(this ?: "#FFFFFF")
        return floatArrayOf(
            ((rgb shr 16) and 0xFF) / 255.0f,
            ((rgb shr 8) and 0xFF) / 255.0f,
            (rgb and 0xFF) / 255.0f,
        )
    }

    private fun normalizeTracerCameraYaw(cameraYaw: Float?): Float? = cameraYaw?.minus(CAMERA_SETUP_YAW_OFFSET)
}
