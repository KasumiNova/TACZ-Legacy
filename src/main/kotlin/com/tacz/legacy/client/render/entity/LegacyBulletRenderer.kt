package com.tacz.legacy.client.render.entity

import com.tacz.legacy.common.infrastructure.mc.entity.LegacyBulletEntity
import net.minecraft.client.renderer.BufferBuilder
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.Tessellator
import net.minecraft.client.renderer.entity.Render
import net.minecraft.client.renderer.entity.RenderManager
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import net.minecraft.util.ResourceLocation
import org.lwjgl.opengl.GL11

internal class LegacyBulletRenderer(renderManager: RenderManager) : Render<LegacyBulletEntity>(renderManager) {

    override fun doRender(
        entity: LegacyBulletEntity,
        x: Double,
        y: Double,
        z: Double,
        entityYaw: Float,
        partialTicks: Float
    ) {
        bindEntityTexture(entity)

        GlStateManager.pushMatrix()
        GlStateManager.translate(x, y, z)
        GlStateManager.rotate(lerpAngle(entity.prevRotationYaw, entity.rotationYaw, partialTicks) - 90.0f, 0.0f, 1.0f, 0.0f)
        GlStateManager.rotate(lerpAngle(entity.prevRotationPitch, entity.rotationPitch, partialTicks), 0.0f, 0.0f, 1.0f)
        GlStateManager.enableRescaleNormal()
        GlStateManager.disableLighting()

        val scale = 0.08f
        GlStateManager.scale(scale, scale, scale)

        val tessellator = Tessellator.getInstance()
        val buffer = tessellator.buffer
        renderCrossedQuads(buffer)
        tessellator.draw()

        GlStateManager.enableLighting()
        GlStateManager.disableRescaleNormal()
        GlStateManager.popMatrix()

        super.doRender(entity, x, y, z, entityYaw, partialTicks)
    }

    override fun getEntityTexture(entity: LegacyBulletEntity): ResourceLocation = BULLET_TEXTURE

    private fun renderCrossedQuads(buffer: BufferBuilder) {
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX)
        addXYQuad(buffer)
        addXZQuad(buffer)
    }

    private fun addXYQuad(buffer: BufferBuilder) {
        buffer.pos(-0.5, -0.15, 0.0).tex(0.0, 1.0).endVertex()
        buffer.pos(0.5, -0.15, 0.0).tex(1.0, 1.0).endVertex()
        buffer.pos(0.5, 0.15, 0.0).tex(1.0, 0.0).endVertex()
        buffer.pos(-0.5, 0.15, 0.0).tex(0.0, 0.0).endVertex()
    }

    private fun addXZQuad(buffer: BufferBuilder) {
        buffer.pos(-0.5, 0.0, -0.15).tex(0.0, 1.0).endVertex()
        buffer.pos(0.5, 0.0, -0.15).tex(1.0, 1.0).endVertex()
        buffer.pos(0.5, 0.0, 0.15).tex(1.0, 0.0).endVertex()
        buffer.pos(-0.5, 0.0, 0.15).tex(0.0, 0.0).endVertex()
    }

    private fun lerpAngle(prev: Float, current: Float, partialTicks: Float): Float {
        var delta = current - prev
        while (delta < -180.0f) {
            delta += 360.0f
        }
        while (delta >= 180.0f) {
            delta -= 360.0f
        }
        return prev + partialTicks * delta
    }

    private companion object {
        private val BULLET_TEXTURE: ResourceLocation = ResourceLocation("tacz", "textures/entity/basic_bullet.png")
    }
}
