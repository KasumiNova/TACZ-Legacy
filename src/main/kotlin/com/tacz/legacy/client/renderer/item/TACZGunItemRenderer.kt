package com.tacz.legacy.client.renderer.item

import com.tacz.legacy.TACZLegacy
import com.tacz.legacy.api.item.IGun
import com.tacz.legacy.client.model.bedrock.BedrockModel
import com.tacz.legacy.client.resource.TACZClientAssetManager
import com.tacz.legacy.client.resource.pojo.display.gun.GunDisplay
import com.tacz.legacy.client.resource.pojo.display.gun.GunTransform
import com.tacz.legacy.common.resource.TACZGunPackPresentation
import com.tacz.legacy.common.resource.TACZGunPackRuntimeRegistry
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.block.model.ItemCameraTransforms
import net.minecraft.client.renderer.tileentity.TileEntityItemStackRenderer
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly

/**
 * TEISR (TileEntityItemStackRenderer) for gun items.
 * Renders bedrock geometry models loaded from gun packs.
 *
 * In 1.12.2, custom 3D item rendering is done via TEISR which is set
 * on the Item via [net.minecraft.item.Item.setTileEntityItemStackRenderer].
 * The item model JSON must specify `"builtin/entity"` as the parent
 * for this renderer to be invoked.
 */
@SideOnly(Side.CLIENT)
internal object TACZGunItemRenderer : TileEntityItemStackRenderer() {

    override fun renderByItem(stack: ItemStack) {
        renderByItem(stack, 1.0f)
    }

    override fun renderByItem(stack: ItemStack, partialTicks: Float) {
        val item = stack.item
        if (item !is IGun) return

        val snapshot = TACZGunPackRuntimeRegistry.getSnapshot()
        val gunId = item.getGunId(stack)

        // Resolve display definition
        val displayId = TACZGunPackPresentation.resolveGunDisplayId(snapshot, gunId) ?: return
        val display: GunDisplay = TACZClientAssetManager.getGunDisplay(displayId) ?: return

        // Resolve model
        val modelLocation: ResourceLocation = display.modelLocation ?: return
        val model: BedrockModel = TACZClientAssetManager.getModel(modelLocation) ?: return

        // Resolve texture
        val textureLocation: ResourceLocation = display.modelTexture ?: return
        val registeredTexture: ResourceLocation = TACZClientAssetManager.getTextureLocation(textureLocation) ?: return

        // Bind texture
        Minecraft.getMinecraft().textureManager.bindTexture(registeredTexture)

        // Apply transforms based on display context
        val transform: GunTransform = display.transform ?: GunTransform.getDefault()

        GlStateManager.pushMatrix()

        // Apply basic transform to center model
        GlStateManager.translate(0.5f, 0.5f, 0.5f)

        // Enable lighting and correct GL state
        GlStateManager.enableLighting()
        GlStateManager.enableRescaleNormal()
        GlStateManager.enableBlend()
        GlStateManager.tryBlendFuncSeparate(
            GlStateManager.SourceFactor.SRC_ALPHA,
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
            GlStateManager.SourceFactor.ONE,
            GlStateManager.DestFactor.ZERO,
        )

        // Render model
        model.render()

        GlStateManager.disableBlend()
        GlStateManager.disableRescaleNormal()

        GlStateManager.popMatrix()
    }
}
