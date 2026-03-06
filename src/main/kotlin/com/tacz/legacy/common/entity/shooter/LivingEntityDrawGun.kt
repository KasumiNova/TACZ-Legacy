package com.tacz.legacy.common.entity.shooter

import com.tacz.legacy.api.event.GunDrawEvent
import com.tacz.legacy.api.item.IGun
import com.tacz.legacy.common.network.TACZNetworkHandler
import com.tacz.legacy.common.network.message.event.ServerMessageGunDraw
import com.tacz.legacy.common.resource.GunDataAccessor
import net.minecraft.entity.EntityLivingBase
import net.minecraft.item.ItemStack
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.fml.relauncher.Side
import java.util.function.Supplier

/**
 * 服务端切枪逻辑。与上游 TACZ LivingEntityDrawGun 行为一致。
 */
public class LivingEntityDrawGun(
    private val shooter: EntityLivingBase,
    private val data: ShooterDataHolder,
) {
    public fun draw(gunItemSupplier: Supplier<ItemStack>) {
        val previousGunItem = data.currentGunItem?.get() ?: ItemStack.EMPTY
        val newGunItem = gunItemSupplier.get()
        val logicalSide = if (shooter.world.isRemote) Side.CLIENT else Side.SERVER

        MinecraftForge.EVENT_BUS.post(GunDrawEvent(shooter, previousGunItem, newGunItem, logicalSide))
        if (!shooter.world.isRemote) {
            TACZNetworkHandler.sendToTrackingEntity(
                ServerMessageGunDraw(shooter.entityId, previousGunItem, newGunItem), shooter
            )
        }

        data.initialData()

        if (data.drawTimestamp == -1L) {
            data.drawTimestamp = System.currentTimeMillis()
        }

        val drawTime = System.currentTimeMillis() - data.drawTimestamp
        if (drawTime >= 0) {
            if (drawTime < data.currentPutAwayTimeS * 1000) {
                data.drawTimestamp = System.currentTimeMillis() + drawTime
            } else {
                data.drawTimestamp = System.currentTimeMillis() + (data.currentPutAwayTimeS * 1000).toLong()
            }
        }

        data.currentGunItem = gunItemSupplier
        updatePutAwayTime()
    }

    public fun getDrawCoolDown(): Long {
        val supplier = data.currentGunItem ?: return 0L
        val currentGunItem = supplier.get()
        val iGun = currentGunItem.item as? IGun ?: return 0L
        val gunId = iGun.getGunId(currentGunItem)
        val gunData = GunDataAccessor.getGunData(gunId) ?: return -1L
        var coolDown = (gunData.drawTimeS * 1000).toLong() - (System.currentTimeMillis() - data.drawTimestamp)
        coolDown -= 5 // 5ms window for latency
        return if (coolDown < 0) 0L else coolDown
    }

    private fun updatePutAwayTime() {
        val supplier = data.currentGunItem
        if (supplier == null) {
            data.currentPutAwayTimeS = 0f
            return
        }
        val gunItem = supplier.get()
        val iGun = gunItem.item as? IGun
        if (iGun != null) {
            val gunData = GunDataAccessor.getGunData(iGun.getGunId(gunItem))
            data.currentPutAwayTimeS = gunData?.putAwayTimeS ?: 0f
        } else {
            data.currentPutAwayTimeS = 0f
        }
    }
}
