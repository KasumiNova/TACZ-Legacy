package com.tacz.legacy.client.render.item

import com.tacz.legacy.client.render.texture.TaczTextureResourceResolver
import com.tacz.legacy.common.application.gunpack.GunDisplayRuntime
import com.tacz.legacy.common.infrastructure.mc.registry.item.LegacyAmmoBoxItem
import com.tacz.legacy.common.infrastructure.mc.registry.item.LegacyAmmoItem
import com.tacz.legacy.common.infrastructure.mc.registry.item.LegacyAttachmentItem
import com.tacz.legacy.common.infrastructure.mc.registry.item.LegacyGunItem
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.Tessellator
import net.minecraft.client.renderer.tileentity.TileEntityItemStackRenderer
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation
import org.lwjgl.opengl.GL11

public object LegacyNonGunItemStackRenderer : TileEntityItemStackRenderer() {

    override fun renderByItem(itemStackIn: ItemStack, partialTicks: Float) {
        if (itemStackIn.isEmpty) {
            return
        }

        val minecraft = Minecraft.getMinecraft()
        val texture = resolveTexture(itemStackIn, minecraft) ?: DEFAULT_FALLBACK_TEXTURE
        minecraft.textureManager.bindTexture(texture)

        GlStateManager.pushMatrix()
        GlStateManager.disableCull()
        GlStateManager.enableBlend()
        GlStateManager.disableLighting()
        GlStateManager.color(1f, 1f, 1f, 1f)

        val tessellator = Tessellator.getInstance()
        val buffer = tessellator.buffer
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX)
        buffer.pos(0.0, 0.0, 0.0).tex(0.0, 1.0).endVertex()
        buffer.pos(1.0, 0.0, 0.0).tex(1.0, 1.0).endVertex()
        buffer.pos(1.0, 1.0, 0.0).tex(1.0, 0.0).endVertex()
        buffer.pos(0.0, 1.0, 0.0).tex(0.0, 0.0).endVertex()
        tessellator.draw()

        GlStateManager.enableLighting()
        GlStateManager.disableBlend()
        GlStateManager.enableCull()
        GlStateManager.popMatrix()
    }

    private fun resolveTexture(stack: ItemStack, minecraft: Minecraft): ResourceLocation? {
        return when (val item = stack.item) {
            is LegacyGunItem -> {
                val gunId = item.registryName
                    ?.path
                    ?.trim()
                    ?.lowercase()
                    ?.ifBlank { null }
                    ?: return DEFAULT_FALLBACK_TEXTURE

                val display = GunDisplayRuntime.registry().snapshot().findDefinition(gunId)
                val rawPath = display?.slotTexturePath
                    ?: display?.hudTexturePath
                    ?: display?.modelTexturePath
                    ?: display?.lodTexturePath

                TaczTextureResourceResolver.resolveForBind(
                    rawPath = rawPath,
                    sourceId = display?.sourceId,
                    fallback = DEFAULT_FALLBACK_TEXTURE,
                    minecraft = minecraft
                )
            }

            is LegacyAttachmentItem -> TaczTextureResourceResolver.resolveForBind(
                rawPath = item.iconTextureAssetPath,
                sourceId = item.sourceId,
                fallback = DEFAULT_FALLBACK_TEXTURE,
                minecraft = minecraft
            )

            is LegacyAmmoBoxItem -> TaczTextureResourceResolver.resolveForBind(
                rawPath = item.iconTextureAssetPath,
                sourceId = item.sourceId,
                fallback = DEFAULT_FALLBACK_TEXTURE,
                minecraft = minecraft
            )

            is LegacyAmmoItem -> TaczTextureResourceResolver.resolveForBind(
                rawPath = item.iconTextureAssetPath,
                sourceId = item.sourceId,
                fallback = DEFAULT_FALLBACK_TEXTURE,
                minecraft = minecraft
            )

            else -> DEFAULT_FALLBACK_TEXTURE
        }
    }

    private val DEFAULT_FALLBACK_TEXTURE: ResourceLocation = ResourceLocation("minecraft", "textures/items/barrier.png")
}
