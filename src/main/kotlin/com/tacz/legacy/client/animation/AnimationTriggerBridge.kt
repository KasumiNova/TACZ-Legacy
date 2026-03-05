package com.tacz.legacy.client.animation

import com.tacz.legacy.client.animation.statemachine.GunAnimationConstant
import com.tacz.legacy.client.animation.statemachine.GunAnimationStateContext
import com.tacz.legacy.common.application.weapon.WeaponAnimationSignal
import com.tacz.legacy.common.application.weapon.WeaponBehaviorResult
import net.minecraft.entity.player.EntityPlayer
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly

/**
 * 将 TACZ-Legacy 行为引擎产出的 [WeaponAnimationSignal] 翻译为
 * 上游 state machine trigger 字符串 ([GunAnimationConstant])。
 *
 * 对标 TACZ 上游分散在 LocalPlayerShoot / LocalPlayerReload /
 * TickAnimationEvent 等处的 trigger 调用。
 */
@SideOnly(Side.CLIENT)
internal object AnimationTriggerBridge {

    /**
     * 在 PlayerTick END 阶段（客户端）调用。
     * 将行为信号翻译为状态机 trigger，并处理移动输入。
     */
    fun dispatchBehaviorSignals(
        instance: LegacyGunDisplayInstance,
        result: WeaponBehaviorResult?
    ) {
        if (result == null) return

        for (signal in result.animationSignals) {
            val input = mapSignalToInput(signal) ?: continue
            instance.animationStateMachine.trigger(input)
        }
    }

    /**
     * 移动触发器（对标 TACZ 上游 TickAnimationEvent.ClientTickEvent）。
     * 每 game tick 调用一次。
     */
    fun dispatchMovementTrigger(
        instance: LegacyGunDisplayInstance,
        player: EntityPlayer
    ) {
        val moveInput = (player as? net.minecraft.client.entity.EntityPlayerSP)?.movementInput ?: run {
            instance.animationStateMachine.trigger(GunAnimationConstant.INPUT_IDLE)
            return
        }

        val fwd = moveInput.moveForward
        val strafe = moveInput.moveStrafe
        val moveVecLengthSq: Float = fwd * fwd + strafe * strafe
        if (!player.isSneaking && player.isSprinting) {
            instance.animationStateMachine.trigger(GunAnimationConstant.INPUT_RUN)
        } else if (!player.isSneaking && moveVecLengthSq > 0.0001f) {
            instance.animationStateMachine.trigger(GunAnimationConstant.INPUT_WALK)
        } else {
            instance.animationStateMachine.trigger(GunAnimationConstant.INPUT_IDLE)
        }
    }

    private fun mapSignalToInput(signal: WeaponAnimationSignal): String? {
        return when (signal) {
            WeaponAnimationSignal.FIRE -> GunAnimationConstant.INPUT_SHOOT
            WeaponAnimationSignal.RELOAD_START -> GunAnimationConstant.INPUT_RELOAD
            WeaponAnimationSignal.INSPECT -> GunAnimationConstant.INPUT_INSPECT
            WeaponAnimationSignal.DRY_FIRE -> null // dry fire 不是上游 trigger，Lua 脚本自行检查
            WeaponAnimationSignal.RELOAD_COMPLETE -> null // reload complete 不是 trigger，Lua 状态自行退出
        }
    }
}
