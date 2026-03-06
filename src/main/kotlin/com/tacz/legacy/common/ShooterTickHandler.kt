package com.tacz.legacy.common

import com.tacz.legacy.api.entity.IGunOperator
import com.tacz.legacy.api.item.IGun
import net.minecraft.entity.EntityLivingBase
import net.minecraftforge.event.entity.living.LivingEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

/**
 * 每 tick 驱动所有 EntityLivingBase 上的枪械子系统更新。
 * 注册到 Forge EVENT_BUS。
 */
internal object ShooterTickHandler {

    @SubscribeEvent
    fun onLivingTick(event: LivingEvent.LivingUpdateEvent) {
        val entity: EntityLivingBase = event.entityLiving ?: return
        // 只在服务端 tick
        if (entity.world.isRemote) return

        val operator = entity as IGunOperator
        val holder = operator.getDataHolder() ?: return

        // 仅在手持枪械时 tick 子系统
        val mainHand = entity.heldItemMainhand
        if (mainHand.isEmpty || mainHand.item !is IGun) {
            // 非持枪：如果之前有持枪状态需要清理
            if (holder.currentGunItem != null) {
                operator.initialData()
                holder.currentGunItem = null
            }
            return
        }

        operator.tick()
    }
}
