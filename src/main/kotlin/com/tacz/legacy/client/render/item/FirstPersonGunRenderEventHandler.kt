package com.tacz.legacy.client.render.item

import com.tacz.legacy.client.gui.WeaponGunsmithImmersiveRuntime
import com.tacz.legacy.common.infrastructure.mc.registry.item.LegacyGunItem
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.util.EnumHand
import net.minecraft.util.math.MathHelper
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
            // 1.12 ItemRenderer.renderItemInFirstPerson(float) 在 RenderSpecificHandEvent
            // 触发之前会先叠加基于 distanceWalkedModified / cameraYaw 的行走摆动变换。
            // 逆变换：以相反顺序、取反参数撤销三个 GL 操作（rotate_x → rotate_z → translate）。
            undoVanillaWalkBobbing(player, event.partialTicks)

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

            // 沉浸式改枪：选择槽位后自动聚焦（叠加温和的平移/旋转/缩放）。
            WeaponGunsmithImmersiveRuntime.resolveModelFocusTransform()?.let { focus ->
                GlStateManager.translate(focus.translateX.toDouble(), focus.translateY.toDouble(), focus.translateZ.toDouble())
                if (focus.rotateXDegrees != 0.0f) {
                    GlStateManager.rotate(focus.rotateXDegrees, 1.0f, 0.0f, 0.0f)
                }
                if (focus.rotateYDegrees != 0.0f) {
                    GlStateManager.rotate(focus.rotateYDegrees, 0.0f, 1.0f, 0.0f)
                }
                if (focus.rotateZDegrees != 0.0f) {
                    GlStateManager.rotate(focus.rotateZDegrees, 0.0f, 0.0f, 1.0f)
                }
                if (focus.uniformScale != 1.0f) {
                    val s = focus.uniformScale.toDouble()
                    GlStateManager.scale(s, s, s)
                }
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

    /**
     * 撤销 1.12 [net.minecraft.client.renderer.ItemRenderer.renderItemInFirstPerson]
     * 在事件触发之前叠加的原版行走摆动变换。
     *
     * 原版变换链：
     * ```
     * float f1 = walked - prevWalked;
     * float f2 = -(walked + f1 * pt);
     * float f3 = prevCamYaw + (camYaw - prevCamYaw) * pt;
     * translate(sin(f2*PI)*f3*0.5, -|cos(f2*PI)*f3|, 0)
     * rotate(sin(f2*PI)*f3*3, 0, 0, 1)
     * rotate(|cos(f2*PI-0.2)*f3|*5, 1, 0, 0)
     * ```
     * 逆操作以相反顺序、取反参数执行。
     */
    private fun undoVanillaWalkBobbing(
        player: net.minecraft.client.entity.EntityPlayerSP,
        partialTicks: Float
    ) {
        val f1 = player.distanceWalkedModified - player.prevDistanceWalkedModified
        val f2 = -(player.distanceWalkedModified + f1 * partialTicks)
        val f3 = player.prevCameraYaw + (player.cameraYaw - player.prevCameraYaw) * partialTicks
        if (f3 < 0.0001f && f3 > -0.0001f) {
            return
        }
        val pi = Math.PI.toFloat()

        // 逆操作 — 与原版相反的参数 + 相反的顺序
        GlStateManager.rotate(
            -kotlin.math.abs(MathHelper.cos(f2 * pi - 0.2f) * f3) * 5.0f,
            1.0f, 0.0f, 0.0f
        )
        GlStateManager.rotate(
            -MathHelper.sin(f2 * pi) * f3 * 3.0f,
            0.0f, 0.0f, 1.0f
        )
        GlStateManager.translate(
            (-MathHelper.sin(f2 * pi) * f3 * 0.5f).toDouble(),
            kotlin.math.abs(MathHelper.cos(f2 * pi) * f3).toDouble(),
            0.0
        )
    }
}
