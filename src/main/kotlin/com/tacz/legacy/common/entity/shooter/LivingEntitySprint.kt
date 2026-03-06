package com.tacz.legacy.common.entity.shooter

import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.SharedMonsterAttributes
import net.minecraft.entity.ai.attributes.AttributeModifier
import java.util.UUID

/**
 * 冲刺状态处理。与上游 TACZ LivingEntitySprint 行为一致。
 * 持枪时的冲刺需要平滑过渡到非冲刺（受 sprintTimeS 控制）。
 */
public class LivingEntitySprint(
    private val shooter: EntityLivingBase,
    private val data: ShooterDataHolder,
) {
    /**
     * 返回 processed sprint 状态。
     * 0 = 非冲刺；正值 = 冲刺过渡进度（秒）。
     */
    public fun getProcessedSprintStatus(): Float {
        return data.sprintTimeS
    }

    /**
     * 每 tick 更新冲刺过渡状态。
     * 如果实体正在冲刺 + 手持枪，平滑递增；否则平滑递减。
     */
    public fun tickSprint() {
        val supplier = data.currentGunItem
        if (supplier == null) {
            if (data.sprintTimeS > 0) {
                data.sprintTimeS = (data.sprintTimeS - SPRINT_DECAY_PER_TICK).coerceAtLeast(0f)
            }
            return
        }
        val isSprinting = shooter.isSprinting
        if (isSprinting) {
            data.sprintTimeS = (data.sprintTimeS + SPRINT_RAMP_PER_TICK).coerceAtMost(MAX_SPRINT_TIME)
        } else {
            if (data.sprintTimeS > 0) {
                data.sprintTimeS = (data.sprintTimeS - SPRINT_DECAY_PER_TICK).coerceAtLeast(0f)
            }
        }
    }

    private companion object {
        const val SPRINT_RAMP_PER_TICK = 0.05f   // 50ms per tick
        const val SPRINT_DECAY_PER_TICK = 0.05f
        const val MAX_SPRINT_TIME = 0.3f          // 上游默认
    }
}
