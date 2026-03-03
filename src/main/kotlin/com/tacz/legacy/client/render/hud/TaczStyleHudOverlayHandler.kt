package com.tacz.legacy.client.render.hud

import com.tacz.legacy.client.render.RenderPipelineRuntime
import com.tacz.legacy.client.render.weapon.WeaponVisualSampleRegistry
import com.tacz.legacy.client.render.texture.TaczTextureResourceResolver
import com.tacz.legacy.common.application.gunpack.GunDisplayRuntime
import com.tacz.legacy.common.application.weapon.WeaponRuntime
import com.tacz.legacy.common.domain.weapon.WeaponFireMode
import com.tacz.legacy.common.domain.weapon.WeaponState
import com.tacz.legacy.common.infrastructure.mc.weapon.WeaponRuntimeMcBridge
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Gui
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.stats.StatList
import net.minecraft.util.ResourceLocation
import net.minecraft.util.math.RayTraceResult
import net.minecraftforge.client.event.RenderGameOverlayEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import kotlin.math.max
import java.util.Locale

public class TaczStyleHudOverlayHandler(
    private val viewModelAssembler: WeaponHudViewModelAssembler = WeaponHudViewModelAssembler()
) {

    private var lastObservedShots: Int = 0
    private var shotMarkerTimestamp: Long = -1L
    private var lastKillAmount: Int = 0
    private var killOverlayTimestamp: Long = -1L

    @SubscribeEvent
    public fun onRenderCrosshairPre(event: RenderGameOverlayEvent.Pre) {
        if (event.type != RenderGameOverlayEvent.ElementType.CROSSHAIRS) {
            return
        }

        val config = WeaponHudUiRuntime.currentConfig()
        if (!config.gunHudEnabled || !config.replaceVanillaCrosshair) {
            return
        }

        val model = buildHudViewModel() ?: return
        event.isCanceled = true

        if (!model.showCrosshair) {
            return
        }

        val resolution = event.resolution
        val width = resolution.scaledWidth
        val height = resolution.scaledHeight

        val crosshair = ResourceLocation("tacz", model.crosshairType.texturePath)
        drawTexturedQuad(
            texture = crosshair,
            x = width / 2 - 8,
            y = height / 2 - 8,
            width = 16,
            height = 16
        )
    }

    @SubscribeEvent
    public fun onRenderHudPost(event: RenderGameOverlayEvent.Post) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL) {
            return
        }

        val config = WeaponHudUiRuntime.currentConfig()
        if (!config.gunHudEnabled) {
            return
        }

        val model = buildHudViewModel() ?: return
        renderAmmoHud(model, event.resolution.scaledWidth, event.resolution.scaledHeight)

        val width = event.resolution.scaledWidth
        val height = event.resolution.scaledHeight
        renderCenterProgress(model, width, height)
        updateShotMarker(model)
        renderShotMarker(width, height)
        renderAnimationDebug(model, width, height)

        if (config.interactHintEnabled) {
            renderInteractionHint(model, width, height)
        }

        if (config.killAmountEnabled) {
            updateKillCounter()
            renderKillAmount(width, height)
        }
    }

    private fun buildHudViewModel(): WeaponHudViewModel? {
        val minecraft = Minecraft.getMinecraft()
        val player = minecraft.player ?: return null
        if (minecraft.world == null) {
            return null
        }

        val gunId = player.heldItemMainhand
            .takeUnless { it.isEmpty }
            ?.item
            ?.registryName
            ?.toString()
            ?.substringAfter(':')
            ?.ifBlank { null }
            ?: return null

        val runtimeDefinition = WeaponRuntime.registry().snapshot().findDefinition(gunId) ?: return null
        val sessionId = WeaponRuntimeMcBridge.clientSessionIdForPlayer(player.uniqueID.toString())
        val sessionDebug = WeaponRuntimeMcBridge.sessionServiceOrNull()?.debugSnapshot(sessionId)
        val animationSnapshot = WeaponRuntimeMcBridge.animationSnapshotOrNull(sessionId)
        val displayDefinition = GunDisplayRuntime.registry().snapshot().findDefinition(gunId)
        val visualSample = WeaponVisualSampleRegistry.resolve(gunId, displayDefinition)

        return viewModelAssembler.assemble(
            fallbackGunId = gunId,
            definition = runtimeDefinition,
            sessionDebugSnapshot = sessionDebug,
            displayDefinition = displayDefinition,
            animationRuntimeSnapshot = animationSnapshot,
            visualSample = visualSample
        )
    }

    private fun renderAnimationDebug(model: WeaponHudViewModel, screenWidth: Int, screenHeight: Int) {
        if (!RenderPipelineRuntime.currentConfig().enableDebugHud) {
            return
        }

        val clipName = model.animationClip ?: return
        val minecraft = Minecraft.getMinecraft()
        val percent = String.format(Locale.ROOT, "%.0f", model.animationProgress * 100f)
        val text = "ANIM ${clipName.uppercase(Locale.ROOT)} ${percent}%"
        val x = (screenWidth - minecraft.fontRenderer.getStringWidth(text) - 8).toFloat()
        val y = (screenHeight - 56).toFloat()
        minecraft.fontRenderer.drawStringWithShadow(text, x, y, 0x77D1FF)
    }

    private fun renderAmmoHud(model: WeaponHudViewModel, screenWidth: Int, screenHeight: Int) {
        val minecraft = Minecraft.getMinecraft()

        Gui.drawRect(screenWidth - 75, screenHeight - 43, screenWidth - 74, screenHeight - 25, 0xFFFFFFFF.toInt())

        drawScaledString(
            text = model.currentAmmoText,
            x = screenWidth - 70f,
            y = screenHeight - 43f,
            color = model.currentAmmoColor,
            scale = 1.5f
        )

        val currentAmmoWidth = minecraft.fontRenderer.getStringWidth(model.currentAmmoText) * 1.5f
        drawScaledString(
            text = model.reserveAmmoText,
            x = screenWidth - 68f + currentAmmoWidth,
            y = screenHeight - 43f,
            color = model.reserveAmmoColor,
            scale = 0.8f
        )

        val fireModeTexture = when (model.fireMode) {
            WeaponFireMode.AUTO -> FIRE_MODE_AUTO
            WeaponFireMode.BURST -> FIRE_MODE_BURST
            else -> FIRE_MODE_SEMI
        }

        val weaponHudTexture = TaczTextureResourceResolver.resolveForBind(
            rawPath = model.hudTexturePath,
            sourceId = model.hudTextureSourceId,
            fallback = DEFAULT_WEAPON_HUD
        ) ?: DEFAULT_WEAPON_HUD

        val emptyHudTexture = TaczTextureResourceResolver.resolveForBind(
            rawPath = model.hudEmptyTexturePath,
            sourceId = model.hudTextureSourceId,
            fallback = null
        )

        val hudTexture = if (model.ammoInMagazine <= 0) {
            emptyHudTexture ?: weaponHudTexture
        } else {
            weaponHudTexture
        }

        drawTexturedQuad(
            texture = hudTexture,
            x = screenWidth - 117,
            y = screenHeight - 44,
            width = 39,
            height = 13,
            textureWidth = 39f,
            textureHeight = 13f
        )

        drawTexturedQuad(
            texture = fireModeTexture,
            x = (screenWidth - 68.5f + currentAmmoWidth).toInt(),
            y = screenHeight - 38,
            width = 10,
            height = 10,
            textureWidth = 10f,
            textureHeight = 10f
        )

        if (model.state == WeaponState.RELOADING) {
            val minecraft = Minecraft.getMinecraft()
            val text = "RELOADING"
            val x = (screenWidth - minecraft.fontRenderer.getStringWidth(text)) / 2
            minecraft.fontRenderer.drawStringWithShadow(text, x.toFloat(), (screenHeight / 2f) + 20f, 0xFFFF55)
        }
    }

    private fun renderCenterProgress(model: WeaponHudViewModel, screenWidth: Int, screenHeight: Int) {
        val progress = max(model.reloadProgress, model.cooldownProgress)
        if (progress <= 0f) {
            return
        }

        val color = heatColor(progress)
        Gui.drawRect(
            screenWidth / 2 - 30,
            screenHeight / 2 + 30,
            screenWidth / 2 - 30 + (progress * 60f).toInt(),
            screenHeight / 2 + 34,
            color
        )

        drawTexturedQuad(
            texture = HEAT_BASE,
            x = screenWidth / 2 - 64,
            y = screenHeight / 2 - 44,
            width = 128,
            height = 128,
            textureWidth = 128f,
            textureHeight = 128f
        )

        val minecraft = Minecraft.getMinecraft()
        val percent = String.format("%.1f%%", progress * 100f)
        val colorText = if (model.state == WeaponState.RELOADING) 0xFFFFFF else 0xAAAAAA
        minecraft.fontRenderer.drawStringWithShadow(
            percent,
            (screenWidth / 2f) - (minecraft.fontRenderer.getStringWidth(percent) / 2f),
            (screenHeight / 2f) + 38f,
            colorText
        )
    }

    private fun updateShotMarker(model: WeaponHudViewModel) {
        if (model.totalShotsFired > lastObservedShots) {
            shotMarkerTimestamp = System.currentTimeMillis()
        }
        lastObservedShots = model.totalShotsFired
    }

    private fun renderShotMarker(screenWidth: Int, screenHeight: Int) {
        if (shotMarkerTimestamp < 0L) {
            return
        }

        val elapsed = System.currentTimeMillis() - shotMarkerTimestamp
        if (elapsed > SHOT_MARKER_KEEP_MS) {
            return
        }

        val alpha = (1f - (elapsed.toFloat() / SHOT_MARKER_KEEP_MS.toFloat())).coerceIn(0f, 1f)
        GlStateManager.color(1f, 1f, 1f, alpha)

        val x = screenWidth / 2 - 8
        val y = screenHeight / 2 - 8
        drawTexturedRegion(HIT_MARKER, x - 4, y - 4, 0f, 0f, 8, 8, 16f, 16f)
        drawTexturedRegion(HIT_MARKER, x + 12, y - 4, 8f, 0f, 8, 8, 16f, 16f)
        drawTexturedRegion(HIT_MARKER, x - 4, y + 12, 0f, 8f, 8, 8, 16f, 16f)
        drawTexturedRegion(HIT_MARKER, x + 12, y + 12, 8f, 8f, 8, 8, 16f, 16f)
        GlStateManager.color(1f, 1f, 1f, 1f)
    }

    private fun renderInteractionHint(model: WeaponHudViewModel, screenWidth: Int, screenHeight: Int) {
        val minecraft = Minecraft.getMinecraft()
        val player = minecraft.player ?: return

        val hit = player.rayTrace(INTERACT_HINT_DISTANCE, 1.0f)
        if (hit?.typeOfHit != RayTraceResult.Type.BLOCK) {
            return
        }

        val hint = if (model.state == WeaponState.RELOADING) {
            "正在换弹..."
        } else {
            "潜行 + 右键：换弹"
        }

        val textWidth = minecraft.fontRenderer.getStringWidth(hint)
        minecraft.fontRenderer.drawStringWithShadow(
            hint,
            (screenWidth - textWidth).toFloat() / 2f,
            (screenHeight / 2f) + 50f,
            0xFFE9A93A.toInt()
        )
    }

    private fun updateKillCounter() {
        val player = Minecraft.getMinecraft().player ?: return
        val killCount = player.statFileWriter.readStat(StatList.PLAYER_KILLS).coerceAtLeast(0)

        if (killCount > lastKillAmount) {
            killOverlayTimestamp = System.currentTimeMillis()
        }

        lastKillAmount = killCount
    }

    private fun renderKillAmount(screenWidth: Int, screenHeight: Int) {
        if (lastKillAmount <= 0 || killOverlayTimestamp < 0L) {
            return
        }

        val elapsed = System.currentTimeMillis() - killOverlayTimestamp
        if (elapsed > KILL_OVERLAY_KEEP_MS) {
            return
        }

        val alpha = (1f - (elapsed.toFloat() / KILL_OVERLAY_KEEP_MS.toFloat())).coerceIn(0f, 1f)
        val alphaChannel = ((alpha * 255f).toInt().coerceIn(0, 255) shl 24)
        val color = alphaChannel or KILL_TEXT_COLOR_BASE

        val text = "KILL x$lastKillAmount"
        val minecraft = Minecraft.getMinecraft()
        val width = minecraft.fontRenderer.getStringWidth(text)
        minecraft.fontRenderer.drawStringWithShadow(
            text,
            (screenWidth - width - 8).toFloat(),
            (screenHeight * 0.15f),
            color
        )
    }

    private fun drawScaledString(
        text: String,
        x: Float,
        y: Float,
        color: Int,
        scale: Float
    ) {
        val minecraft = Minecraft.getMinecraft()

        GlStateManager.pushMatrix()
        GlStateManager.scale(scale.toDouble(), scale.toDouble(), 1.0)
        minecraft.fontRenderer.drawStringWithShadow(text, x / scale, y / scale, color)
        GlStateManager.popMatrix()
    }

    private fun drawTexturedQuad(
        texture: ResourceLocation,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        textureWidth: Float = width.toFloat(),
        textureHeight: Float = height.toFloat()
    ) {
        drawTexturedRegion(
            texture = texture,
            x = x,
            y = y,
            u = 0f,
            v = 0f,
            width = width,
            height = height,
            textureWidth = textureWidth,
            textureHeight = textureHeight
        )
    }

    private fun drawTexturedRegion(
        texture: ResourceLocation,
        x: Int,
        y: Int,
        u: Float,
        v: Float,
        width: Int,
        height: Int,
        textureWidth: Float,
        textureHeight: Float
    ) {
        val minecraft = Minecraft.getMinecraft()
        minecraft.textureManager.bindTexture(texture)

        GlStateManager.enableBlend()
        GlStateManager.color(1f, 1f, 1f, 1f)

        Gui.drawModalRectWithCustomSizedTexture(
            x,
            y,
            u,
            v,
            width,
            height,
            textureWidth,
            textureHeight
        )
    }

    private fun heatColor(percent: Float): Int {
        return when {
            percent < 0.4f -> 0x9FFFFFFF.toInt()
            percent <= 0.65f -> 0x9FFFFF00.toInt()
            else -> 0x9FFF0000.toInt()
        }
    }

    private companion object {
        private val FIRE_MODE_SEMI: ResourceLocation = ResourceLocation("tacz", "textures/hud/fire_mode_semi.png")
        private val FIRE_MODE_AUTO: ResourceLocation = ResourceLocation("tacz", "textures/hud/fire_mode_auto.png")
        private val FIRE_MODE_BURST: ResourceLocation = ResourceLocation("tacz", "textures/hud/fire_mode_burst.png")
        private val DEFAULT_WEAPON_HUD: ResourceLocation = ResourceLocation("tacz", "textures/hud/heat_bar.png")
        private val HEAT_BASE: ResourceLocation = ResourceLocation("tacz", "textures/hud/heat_base.png")
        private val HIT_MARKER: ResourceLocation = ResourceLocation("tacz", "textures/crosshair/hit/hit_marker.png")

        private const val SHOT_MARKER_KEEP_MS: Long = 300L
        private const val KILL_OVERLAY_KEEP_MS: Long = 1_500L
        private const val KILL_TEXT_COLOR_BASE: Int = 0x00FF5555
        private const val INTERACT_HINT_DISTANCE: Double = 4.5
    }

}
