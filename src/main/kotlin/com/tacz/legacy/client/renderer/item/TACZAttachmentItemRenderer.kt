package com.tacz.legacy.client.renderer.item

import com.tacz.legacy.api.item.IAttachment
import com.tacz.legacy.client.model.SlotModel
import com.tacz.legacy.client.model.bedrock.BedrockModel
import com.tacz.legacy.client.resource.TACZClientAssetManager
import com.tacz.legacy.client.resource.pojo.display.attachment.AttachmentDisplay
import com.tacz.legacy.common.resource.TACZGunPackPresentation
import com.tacz.legacy.common.resource.TACZGunPackRuntimeRegistry
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.tileentity.TileEntityItemStackRenderer
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly

/**
 * TEISR for attachment items.
 * Renders either 3D bedrock model or flat slot texture (for GUI).
 * Port of upstream TACZ AttachmentItemRenderer for 1.12.2.
 */
@SideOnly(Side.CLIENT)
internal object TACZAttachmentItemRenderer : TileEntityItemStackRenderer() {
    private val SLOT_MODEL = SlotModel()

    override fun renderByItem(stack: ItemStack) {
        renderByItem(stack, 1.0f)
    }

    override fun renderByItem(stack: ItemStack, partialTicks: Float) {
        val item = stack.item
        if (item !is IAttachment) return

        val attachmentId = item.getAttachmentId(stack)
        val snapshot = TACZGunPackRuntimeRegistry.getSnapshot()

        val displayId = TACZGunPackPresentation.resolveAttachmentDisplayId(snapshot, attachmentId) ?: return
        val display: AttachmentDisplay = TACZClientAssetManager.getAttachmentDisplay(displayId) ?: return

        GlStateManager.pushMatrix()

        // Resolve 3D model
        val modelLoc: ResourceLocation? = display.model
        val textureLoc: ResourceLocation? = display.texture
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

    private fun renderSlotFallback(display: AttachmentDisplay) {
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
}
