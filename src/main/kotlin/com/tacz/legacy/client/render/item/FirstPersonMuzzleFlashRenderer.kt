package com.tacz.legacy.client.render.item

import com.tacz.legacy.common.infrastructure.mc.weapon.WeaponAnimationClipType
import com.tacz.legacy.common.infrastructure.mc.weapon.WeaponRuntimeMcBridge
import net.minecraft.client.Minecraft
import net.minecraft.client.entity.AbstractClientPlayer
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.item.ItemStack
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly
import org.lwjgl.opengl.GL11

@SideOnly(Side.CLIENT)
public object FirstPersonMuzzleFlashRenderer {

    public fun renderForPlayer(
        player: AbstractClientPlayer,
        itemStack: ItemStack,
        partialTicks: Float
    ) {
        if (itemStack.isEmpty) {
            return
        }

        val sessionId = WeaponRuntimeMcBridge.clientSessionIdForPlayer(player.uniqueID.toString())
        val snapshot = WeaponRuntimeMcBridge.animationSnapshotOrNull(sessionId)
        val alpha = resolveFlashAlpha(
            clipType = snapshot?.clip,
            elapsedMillis = snapshot?.elapsedMillis,
            durationMillis = snapshot?.durationMillis
        )
        if (alpha <= 0f) {
            return
        }

        val offsets = LegacyGunItemStackRenderer.latestFirstPersonReferenceOffsets()
        val anchor = offsets.muzzleFlash ?: offsets.muzzlePos ?: return
        val compensatedAnchor = FirstPersonFovCompensation.applyScale(
            x = anchor.x,
            y = anchor.y,
            z = anchor.z,
            scale = FirstPersonFovCompensation.currentScale()
        )

        val minecraft = Minecraft.getMinecraft()
        val aimingProgress = LegacyGunItemStackRenderer.resolveFirstPersonAimingProgressForFov(
            itemStack = itemStack,
            minecraft = minecraft,
            partialTicks = partialTicks
        )
        val size = resolveFlashSize(aimingProgress)

        renderFlashAt(
            x = compensatedAnchor.x,
            y = compensatedAnchor.y,
            z = compensatedAnchor.z,
            size = size,
            alpha = alpha
        )
    }

    internal fun resolveFlashAlpha(
        clipType: WeaponAnimationClipType?,
        elapsedMillis: Long?,
        durationMillis: Long?
    ): Float {
        if (clipType != WeaponAnimationClipType.FIRE) {
            return 0f
        }

        val elapsed = elapsedMillis?.coerceAtLeast(0L) ?: return 0f
        val duration = durationMillis
            ?.takeIf { it > 0L }
            ?.coerceAtMost(MAX_FLASH_DURATION_MILLIS)
            ?: DEFAULT_FLASH_DURATION_MILLIS

        if (elapsed >= duration) {
            return 0f
        }

        val progress = (elapsed.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
        val envelope = when {
            progress <= FLASH_ATTACK_PROGRESS -> {
                (progress / FLASH_ATTACK_PROGRESS).coerceIn(0f, 1f)
            }
            else -> {
                val decayT = ((1f - progress) / (1f - FLASH_ATTACK_PROGRESS)).coerceIn(0f, 1f)
                decayT * decayT
            }
        }

        return (envelope * FLASH_MAX_ALPHA).coerceIn(0f, 1f)
    }

    internal fun resolveFlashSize(aimingProgress: Float): Float {
        val ads = aimingProgress.coerceIn(0f, 1f)
        return FLASH_SIZE_HIP + (FLASH_SIZE_ADS - FLASH_SIZE_HIP) * ads
    }

    private fun renderFlashAt(
        x: Float,
        y: Float,
        z: Float,
        size: Float,
        alpha: Float
    ) {
        val safeSize = size.coerceAtLeast(0.001f)
        val safeAlpha = alpha.coerceIn(0f, 1f)
        if (safeAlpha <= 0f) {
            return
        }

        GlStateManager.pushMatrix()
        try {
            GlStateManager.translate(x.toDouble(), y.toDouble(), z.toDouble())
            GlStateManager.disableLighting()
            GlStateManager.disableTexture2D()
            GlStateManager.disableCull()
            GlStateManager.disableDepth()
            GlStateManager.enableBlend()
            GlStateManager.depthMask(false)
            GlStateManager.tryBlendFuncSeparate(
                GL11.GL_SRC_ALPHA,
                GL11.GL_ONE,
                GL11.GL_ONE,
                GL11.GL_ZERO
            )

            repeat(2) { layer ->
                if (layer == 1) {
                    GlStateManager.rotate(90f, 0f, 0f, 1f)
                }
                drawQuad(size = safeSize, alpha = safeAlpha)
            }
        } finally {
            GlStateManager.depthMask(true)
            GlStateManager.disableBlend()
            GlStateManager.enableDepth()
            GlStateManager.enableCull()
            GlStateManager.enableTexture2D()
            GlStateManager.enableLighting()
            GlStateManager.color(1f, 1f, 1f, 1f)
            GlStateManager.popMatrix()
        }
    }

    private fun drawQuad(size: Float, alpha: Float) {
        GL11.glBegin(GL11.GL_QUADS)
        GL11.glColor4f(FLASH_COLOR_R, FLASH_COLOR_G, FLASH_COLOR_B, alpha)
        GL11.glVertex3f(-size, -size, 0f)
        GL11.glVertex3f(size, -size, 0f)
        GL11.glVertex3f(size, size, 0f)
        GL11.glVertex3f(-size, size, 0f)
        GL11.glEnd()
    }

    private const val DEFAULT_FLASH_DURATION_MILLIS: Long = 90L
    private const val MAX_FLASH_DURATION_MILLIS: Long = 150L
    private const val FLASH_ATTACK_PROGRESS: Float = 0.2f
    private const val FLASH_MAX_ALPHA: Float = 0.92f
    private const val FLASH_SIZE_HIP: Float = 0.075f
    private const val FLASH_SIZE_ADS: Float = 0.05f
    private const val FLASH_COLOR_R: Float = 1f
    private const val FLASH_COLOR_G: Float = 0.86f
    private const val FLASH_COLOR_B: Float = 0.45f
}
