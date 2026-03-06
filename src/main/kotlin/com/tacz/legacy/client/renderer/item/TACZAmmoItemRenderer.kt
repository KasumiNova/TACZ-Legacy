package com.tacz.legacy.client.renderer.item

import com.tacz.legacy.api.item.IAmmo
import com.tacz.legacy.client.model.BedrockAmmoModel
import com.tacz.legacy.client.model.SlotModel
import com.tacz.legacy.client.model.bedrock.BedrockModel
import com.tacz.legacy.client.model.bedrock.BedrockPart
import com.tacz.legacy.client.resource.TACZClientAssetManager
import com.tacz.legacy.client.resource.pojo.TransformScale
import com.tacz.legacy.client.resource.pojo.display.ammo.AmmoDisplay
import com.tacz.legacy.client.resource.pojo.display.ammo.AmmoTransform
import com.tacz.legacy.common.resource.TACZGunPackPresentation
import com.tacz.legacy.common.resource.TACZGunPackRuntimeRegistry
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.tileentity.TileEntityItemStackRenderer
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly
import org.joml.Vector3f

/**
 * TEISR for ammo items.
 * Renders either 3D bedrock model or flat slot texture (for GUI).
 * Port of upstream TACZ AmmoItemRenderer for 1.12.2.
 */
@SideOnly(Side.CLIENT)
internal object TACZAmmoItemRenderer : TileEntityItemStackRenderer() {
    private val SLOT_MODEL = SlotModel()

    override fun renderByItem(stack: ItemStack) {
        renderByItem(stack, 1.0f)
    }

    override fun renderByItem(stack: ItemStack, partialTicks: Float) {
        val item = stack.item
        if (item !is IAmmo) return

        val ammoId = item.getAmmoId(stack)
        val snapshot = TACZGunPackRuntimeRegistry.getSnapshot()

        val displayId = TACZGunPackPresentation.resolveAmmoDisplayId(snapshot, ammoId) ?: return
        val display: AmmoDisplay = TACZClientAssetManager.getAmmoDisplay(displayId) ?: return

        GlStateManager.pushMatrix()

        // Resolve 3D model
        val modelLoc: ResourceLocation? = display.modelLocation
        val textureLoc: ResourceLocation? = display.modelTexture
        val model: BedrockModel? = if (modelLoc != null) TACZClientAssetManager.getModel(modelLoc) else null
        val registeredTexture: ResourceLocation? = if (textureLoc != null) TACZClientAssetManager.getTextureLocation(textureLoc) else null

        if (model == null || registeredTexture == null) {
            // Fallback: render flat slot texture
            renderSlotFallback(display)
            GlStateManager.popMatrix()
            return
        }

        // 3D model rendering
        Minecraft.getMinecraft().textureManager.bindTexture(registeredTexture)

        GlStateManager.translate(0.5f, 2.0f, 0.5f)
        GlStateManager.scale(-1f, -1f, 1f)

        // Apply scale transform
        val transform: AmmoTransform = display.transform ?: AmmoTransform.getDefault()
        val scale: TransformScale? = transform.scale
        applyScaleTransform(scale)

        GlStateManager.enableLighting()
        GlStateManager.enableRescaleNormal()
        GlStateManager.enableBlend()
        GlStateManager.tryBlendFuncSeparate(
            GlStateManager.SourceFactor.SRC_ALPHA,
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
            GlStateManager.SourceFactor.ONE,
            GlStateManager.DestFactor.ZERO,
        )

        model.render()

        GlStateManager.disableBlend()
        GlStateManager.disableRescaleNormal()
        GlStateManager.popMatrix()
    }

    private fun renderSlotFallback(display: AmmoDisplay) {
        val slotTexLoc = display.slotTextureLocation ?: return
        val registeredSlot = TACZClientAssetManager.getTextureLocation(slotTexLoc) ?: return
        Minecraft.getMinecraft().textureManager.bindTexture(registeredSlot)

        GlStateManager.translate(0.5f, 1.5f, 0.5f)
        GlStateManager.rotate(180f, 0f, 0f, 1f)

        GlStateManager.enableLighting()
        GlStateManager.enableRescaleNormal()
        GlStateManager.enableBlend()
        GlStateManager.tryBlendFuncSeparate(
            GlStateManager.SourceFactor.SRC_ALPHA,
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
            GlStateManager.SourceFactor.ONE,
            GlStateManager.DestFactor.ZERO,
        )

        SLOT_MODEL.render()

        GlStateManager.disableBlend()
        GlStateManager.disableRescaleNormal()
    }

    private fun applyScaleTransform(scale: TransformScale?) {
        if (scale == null) return
        // Use thirdPerson scale as default for now (ItemDisplayContext isn't available in 1.12.2 TEISR)
        val v: Vector3f = scale.thirdPerson ?: return
        GlStateManager.translate(0f, 1.5f, 0f)
        GlStateManager.scale(v.x(), v.y(), v.z())
        GlStateManager.translate(0f, -1.5f, 0f)
    }
}
