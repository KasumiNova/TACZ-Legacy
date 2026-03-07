package com.tacz.legacy.client.event

import com.tacz.legacy.api.item.IGun
import com.tacz.legacy.api.item.gun.FireMode
import com.tacz.legacy.client.input.LegacyInputExtraCheck
import com.tacz.legacy.client.input.LegacyKeyBindings
import com.tacz.legacy.client.resource.TACZClientAssetManager
import com.tacz.legacy.common.config.LegacyConfigManager
import com.tacz.legacy.common.config.InteractKeyConfigRead
import com.tacz.legacy.common.item.LegacyRuntimeTooltipSupport
import com.tacz.legacy.common.resource.GunDataAccessor
import com.tacz.legacy.common.resource.TACZGunPackPresentation
import com.tacz.legacy.common.resource.TACZGunPackRuntimeRegistry
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Gui
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.entity.EntityList
import net.minecraft.util.ResourceLocation
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.RayTraceResult
import net.minecraft.util.text.TextFormatting
import net.minecraft.util.text.translation.I18n
import net.minecraftforge.client.event.RenderGameOverlayEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import java.text.DecimalFormat
import java.util.Locale

internal object LegacyClientOverlayEventHandler {
    private val SEMI: ResourceLocation = ResourceLocation("tacz", "textures/hud/fire_mode_semi.png")
    private val AUTO: ResourceLocation = ResourceLocation("tacz", "textures/hud/fire_mode_auto.png")
    private val BURST: ResourceLocation = ResourceLocation("tacz", "textures/hud/fire_mode_burst.png")
    private val HEAT_BASE: ResourceLocation = ResourceLocation("tacz", "textures/hud/heat_base.png")

    private val currentAmmoFormat: DecimalFormat = DecimalFormat("000")
    private val reserveAmmoFormat: DecimalFormat = DecimalFormat("0000")
    private val heatPercentFormat: DecimalFormat = DecimalFormat("0.0%")
    private var heatScale: Float = 0.75f

    @SubscribeEvent
    fun onRenderOverlay(event: RenderGameOverlayEvent.Post): Unit {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL) {
            return
        }
        renderGunHud(event)
        renderInteractPrompt(event)
    }

    private fun renderGunHud(event: RenderGameOverlayEvent.Post): Unit {
        if (!LegacyConfigManager.client.gunHudEnable || !LegacyInputExtraCheck.isInGame()) {
            return
        }
        val mc = Minecraft.getMinecraft()
        val player = mc.player ?: return
        if (player.isSpectator || !IGun.mainHandHoldGun(player)) {
            return
        }
        val stack = player.heldItemMainhand
        val iGun = stack.item as? IGun ?: return
        val gunId = iGun.getGunId(stack)
        val gunData = GunDataAccessor.getGunData(gunId) ?: return

        val heatInfo = LegacyRuntimeTooltipSupport.resolveHeatInfo(stack, gunId, iGun)
        val overheatLocked = heatInfo?.locked == true
        val useInventoryAmmo = iGun.useInventoryAmmo(stack)
        val useDummyAmmo = iGun.useDummyAmmo(stack)
        val currentAmmo = if (useInventoryAmmo) {
            val reserve = LegacyRuntimeTooltipSupport.countInventoryAmmo(player, stack)
            reserve + if (iGun.hasBulletInBarrel(stack) && gunData.boltType != com.tacz.legacy.common.resource.BoltType.OPEN_BOLT) 1 else 0
        } else {
            LegacyRuntimeTooltipSupport.getCurrentAmmoWithBarrel(stack, iGun, gunData)
        }.coerceIn(0, 9999)
        val maxAmmo = LegacyRuntimeTooltipSupport.getMaxAmmoWithBarrel(stack, gunData).coerceAtLeast(1)
        val reserveAmmo = when {
            useInventoryAmmo -> 0
            gunData.isReloadInfinite -> Int.MAX_VALUE
            useDummyAmmo -> iGun.getDummyAmmoAmount(stack)
            else -> LegacyRuntimeTooltipSupport.countInventoryAmmo(player, stack)
        }.coerceIn(0, 9999)

        val ammoCountColor = when {
            overheatLocked || (currentAmmo < maxAmmo * 0.25f && currentAmmo < 10) -> 0xFF5555
            useInventoryAmmo && useDummyAmmo -> 0x55FFFF
            useInventoryAmmo -> 0xFFFF55
            else -> 0xFFFFFF
        }
        val reserveColor = if (!useInventoryAmmo && useDummyAmmo) 0x55FFFF else 0xAAAAAA
        val currentAmmoText = currentAmmoFormat.format(currentAmmo)
        val reserveText = when {
            useInventoryAmmo -> ""
            gunData.isReloadInfinite -> "∞"
            else -> reserveAmmoFormat.format(reserveAmmo)
        }

        val width = event.resolution.scaledWidth
        val height = event.resolution.scaledHeight
        val font = mc.fontRenderer

        Gui.drawRect(width - 75, height - 43, width - 74, height - 25, 0xFFFFFFFF.toInt())

        GlStateManager.pushMatrix()
        GlStateManager.scale(1.5f, 1.5f, 1f)
        font.drawString(currentAmmoText, ((width - 70) / 1.5f).toInt(), ((height - 43) / 1.5f).toInt(), ammoCountColor)
        GlStateManager.popMatrix()

        GlStateManager.pushMatrix()
        GlStateManager.scale(0.8f, 0.8f, 1f)
        font.drawString(
            reserveText,
            ((width - 68 + font.getStringWidth(currentAmmoText) * 1.5f) / 0.8f).toInt(),
            ((height - 43) / 0.8f).toInt(),
            reserveColor,
        )
        GlStateManager.popMatrix()

        // Resolve HUD textures through the client asset runtime (same path slot
        // textures use), not by hand-crafting paths from raw display JSON.
        val snapshot = TACZGunPackRuntimeRegistry.getSnapshot()
        val displayId = TACZGunPackPresentation.resolveGunDisplayId(snapshot, gunId)
        val gunDisplay = displayId?.let(TACZClientAssetManager::getGunDisplay)
        val hudPrimary = gunDisplay?.hudTextureLocation?.let(TACZClientAssetManager::getTextureLocation)
        val hudEmpty = gunDisplay?.hudEmptyTextureLocation?.let(TACZClientAssetManager::getTextureLocation)
        val hudTexture = when {
            currentAmmo <= 0 || overheatLocked -> hudEmpty ?: hudPrimary
            else -> hudPrimary
        }
        if (hudTexture != null) {
            mc.textureManager.bindTexture(hudTexture)
            if ((currentAmmo <= 0 || overheatLocked) && hudEmpty == null) {
                GlStateManager.color(1f, 0.3f, 0.3f, 1f)
            } else {
                GlStateManager.color(1f, 1f, 1f, 1f)
            }
            Gui.drawModalRectWithCustomSizedTexture(width - 117, height - 44, 0f, 0f, 39, 13, 39f, 13f)
            GlStateManager.color(1f, 1f, 1f, 1f)
        }

        val fireModeTexture = when (iGun.getFireMode(stack)) {
            FireMode.AUTO -> AUTO
            FireMode.BURST -> BURST
            else -> SEMI
        }
        mc.textureManager.bindTexture(fireModeTexture)
        Gui.drawModalRectWithCustomSizedTexture(
            (width - 68.5 + font.getStringWidth(currentAmmoText) * 1.5).toInt(),
            height - 38,
            0f,
            0f,
            10,
            10,
            10f,
            10f,
        )

        if (heatInfo != null) {
            renderHeatBar(mc, width, height, heatInfo.current / heatInfo.max, heatInfo.locked)
        }
    }

    private fun renderHeatBar(mc: Minecraft, width: Int, height: Int, rawPercent: Float, locked: Boolean): Unit {
        val percent = rawPercent.coerceIn(0f, 1f)
        val scaleTarget = percent / 8f + 0.75f
        when {
            heatScale < scaleTarget -> heatScale += 0.05f
            heatScale > scaleTarget -> heatScale -= 0.025f
        }
        if (heatScale > scaleTarget - 0.03f && heatScale < scaleTarget + 0.055f) {
            heatScale = scaleTarget
        }

        val scaledWidth = (width / heatScale).toInt()
        val scaledHeight = (height / heatScale).toInt()
        val tick = mc.player?.ticksExisted ?: 0
        val color = heatColor(percent, locked, tick)

        GlStateManager.pushMatrix()
        GlStateManager.scale(heatScale, heatScale, 1f)
        Gui.drawRect(
            scaledWidth / 2 - 30,
            scaledHeight / 2 + 30,
            scaledWidth / 2 - 30 + (percent * 60f).toInt(),
            scaledHeight / 2 + 34,
            color,
        )
        mc.textureManager.bindTexture(HEAT_BASE)
        if (locked) {
            if (tick % 20 < 10) {
                GlStateManager.color(1f, 0.1f, 0.1f, 1f)
            } else {
                GlStateManager.color(1f, 1f, 0.1f, 1f)
            }
        }
        Gui.drawModalRectWithCustomSizedTexture(scaledWidth / 2 - 64, scaledHeight / 2 - 44, 0f, 0f, 128, 128, 128f, 128f)
        GlStateManager.color(1f, 1f, 1f, 1f)
        val percentText = if (locked) I18n.translateToLocal("hud.tacz.heat.overheat") else heatPercentFormat.format(percent.toDouble())
        val textColor = if (locked) {
            if (tick % 20 < 10) 0xFFFF0000.toInt() else 0xFFFFFF00.toInt()
        } else {
            0xFFFFFFFF.toInt()
        }
        mc.fontRenderer.drawStringWithShadow(percentText, (scaledWidth / 2f - mc.fontRenderer.getStringWidth(percentText) / 2f), (scaledHeight / 2f + 38f), textColor)
        GlStateManager.popMatrix()
    }

    private fun renderInteractPrompt(event: RenderGameOverlayEvent.Post): Unit {
        if (LegacyConfigManager.client.disableInteractHudText || !LegacyInputExtraCheck.isInGame()) {
            return
        }
        val mc = Minecraft.getMinecraft()
        val player = mc.player ?: return
        if (player.isSpectator || !IGun.mainHandHoldGun(player)) {
            return
        }
        val hit = mc.objectMouseOver ?: return
        val shouldRender = when (hit.typeOfHit) {
            RayTraceResult.Type.BLOCK -> hit.blockPos != null && player.world.getBlockState(hit.blockPos).block.registryName?.let(InteractKeyConfigRead::canInteractBlock) == true
            RayTraceResult.Type.ENTITY -> hit.entityHit != null && EntityList.getKey(hit.entityHit)?.let(InteractKeyConfigRead::canInteractEntity) == true
            else -> false
        }
        if (!shouldRender) {
            return
        }
        val keyName = prettyKeyName(LegacyKeyBindings.INTERACT.displayName)
        val text = I18n.translateToLocalFormatted("gui.tacz.interact_key.text.desc", keyName)
        val font = mc.fontRenderer
        val width = event.resolution.scaledWidth
        val height = event.resolution.scaledHeight
        font.drawStringWithShadow(text, ((width - font.getStringWidth(text)) / 2f), (height / 2f - 25f), TextFormatting.YELLOW.colorIndex?.let { 0xFFFF55 } ?: 0xFFFF55)
    }

    private fun prettyKeyName(keyName: String): String {
        if (keyName.isBlank()) {
            return keyName
        }
        return keyName.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) }
    }

    private fun heatColor(percent: Float, locked: Boolean, tickCount: Int): Int {
        if (locked) {
            return if (tickCount % 20 < 10) 0x9FFF0000.toInt() else 0x9FFFFF00.toInt()
        }
        if (percent < 0.4f) {
            return 0x9FFFFFFF.toInt()
        }
        return if (percent <= 0.65f) {
            lerpColor((percent * 4f - 1.6f).coerceIn(0f, 1f), 0x9FFFFFFF.toInt(), 0x9FFFFF00.toInt())
        } else {
            lerpColor(((percent - 0.65f) / 0.35f).coerceIn(0f, 1f), 0x9FFFFF00.toInt(), 0x9FFF0000.toInt())
        }
    }

    private fun lerpColor(progress: Float, start: Int, end: Int): Int {
        val clamped = MathHelper.clamp(progress, 0f, 1f)
        val a = ((start ushr 24 and 0xFF) + ((end ushr 24 and 0xFF) - (start ushr 24 and 0xFF)) * clamped).toInt()
        val r = ((start ushr 16 and 0xFF) + ((end ushr 16 and 0xFF) - (start ushr 16 and 0xFF)) * clamped).toInt()
        val g = ((start ushr 8 and 0xFF) + ((end ushr 8 and 0xFF) - (start ushr 8 and 0xFF)) * clamped).toInt()
        val b = ((start and 0xFF) + ((end and 0xFF) - (start and 0xFF)) * clamped).toInt()
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }
}
