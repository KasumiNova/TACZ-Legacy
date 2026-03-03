package com.tacz.legacy.client.render.item

import com.tacz.legacy.common.infrastructure.mc.registry.item.LegacyGunItem
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.util.EnumHand
import net.minecraftforge.client.event.RenderSpecificHandEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly

/**
 * 拦截 [RenderSpecificHandEvent]，跳过原版物品渲染管线，
 * 改用 TACZ 风格的变换链从摄像机空间直接渲染枪模。
 *
 * 原版 TEISR 管线会附加手部摆动、JSON display 变换等，
 * 导致枪模出现在"原版工具持握位置"而非 FPS 瞄准位置。
 */
@SideOnly(Side.CLIENT)
public class FirstPersonGunRenderEventHandler {

    @SubscribeEvent
    public fun onRenderSpecificHand(event: RenderSpecificHandEvent) {
        val stack = event.itemStack
        val minecraft = Minecraft.getMinecraft()
        val player = minecraft.player ?: return
        val customMainHandRenderEnabled = shouldUseCustomMainHandRendering(
            hasScreen = minecraft.currentScreen != null,
            thirdPersonView = minecraft.gameSettings.thirdPersonView,
            isRenderViewEntityPlayer = minecraft.renderViewEntity === player
        )

        if (event.hand == EnumHand.OFF_HAND) {
            // 主手持枪时隐藏副手（与 TACZ 行为一致）
            val mainStack = player.heldItemMainhand
            if (shouldHideOffHand(
                    mainHandHasLegacyGun = !mainStack.isEmpty && mainStack.item is LegacyGunItem,
                    customMainHandRenderEnabled = customMainHandRenderEnabled
                )
            ) {
                event.isCanceled = true
            }
            return
        }

        // 仅处理主手枪械
        if (stack.isEmpty || stack.item !is LegacyGunItem) {
            LegacyGunItemStackRenderer.notifyFirstPersonGunContextSuspended(player.uniqueID.toString())
            FirstPersonShellEjectRenderer.notifyContextSuspended(player.uniqueID.toString())
            FirstPersonTracerRenderer.notifyContextSuspended(player.uniqueID.toString())
            return
        }

        if (!customMainHandRenderEnabled) {
            LegacyGunItemStackRenderer.notifyFirstPersonGunContextSuspended(player.uniqueID.toString())
            FirstPersonShellEjectRenderer.notifyContextSuspended(player.uniqueID.toString())
            FirstPersonTracerRenderer.notifyContextSuspended(player.uniqueID.toString())
            return
        }

        if (!LegacyGunItemStackRenderer.canRenderFirstPerson(stack, minecraft)) {
            LegacyGunItemStackRenderer.notifyFirstPersonGunContextSuspended(player.uniqueID.toString())
            FirstPersonShellEjectRenderer.notifyContextSuspended(player.uniqueID.toString())
            FirstPersonTracerRenderer.notifyContextSuspended(player.uniqueID.toString())
            return
        }

        event.isCanceled = true

        GlStateManager.pushMatrix()
        try {
            // 1.12 ItemRenderer 在触发 RenderSpecificHandEvent 之前会先执行 rotateArm：
            //   R = Rx(pitchOffset) * Ry(yawOffset)
            // 逆矩阵需要按反向顺序应用：R^-1 = Ry(-yawOffset) * Rx(-pitchOffset)
            // 否则会残留姿态误差，表现为开火时手臂异常上抬/偏转。
            val armPitch = player.prevRenderArmPitch + (player.renderArmPitch - player.prevRenderArmPitch) * event.partialTicks
            val armYaw = player.prevRenderArmYaw + (player.renderArmYaw - player.prevRenderArmYaw) * event.partialTicks
            val pitchOffset = (player.rotationPitch - armPitch) * 0.1f
            val yawOffset = (player.rotationYaw - armYaw) * 0.1f
            GlStateManager.rotate(-yawOffset, 0.0f, 1.0f, 0.0f)
            GlStateManager.rotate(-pitchOffset, 1.0f, 0.0f, 0.0f)

            // 对齐 TACZ applyItemInHandCameraAnimation：camera 骨骼驱动的旋转同时作用于手持渲染矩阵。
            LegacyGunItemStackRenderer.resolveCameraAnimationDelta(stack, event.partialTicks)?.let { delta ->
                GlStateManager.rotate(delta.axisAngleDegrees, delta.axisX, delta.axisY, delta.axisZ)
            }

            LegacyGunItemStackRenderer.renderFirstPerson(stack, event.partialTicks)
            FirstPersonMuzzleFlashRenderer.renderForPlayer(
                player = player,
                itemStack = stack,
                partialTicks = event.partialTicks
            )
            FirstPersonShellEjectRenderer.renderForPlayer(
                player = player,
                itemStack = stack,
                partialTicks = event.partialTicks
            )
            FirstPersonTracerRenderer.renderForPlayer(
                player = player,
                itemStack = stack,
                partialTicks = event.partialTicks
            )
        } finally {
            GlStateManager.popMatrix()
        }
    }

    internal fun shouldUseCustomMainHandRendering(
        @Suppress("UNUSED_PARAMETER") hasScreen: Boolean,
        thirdPersonView: Int,
        isRenderViewEntityPlayer: Boolean
    ): Boolean {
        if (thirdPersonView != 0) {
            return false
        }
        return isRenderViewEntityPlayer
    }

    internal fun shouldHideOffHand(
        mainHandHasLegacyGun: Boolean,
        customMainHandRenderEnabled: Boolean
    ): Boolean {
        return mainHandHasLegacyGun && customMainHandRenderEnabled
    }
}
