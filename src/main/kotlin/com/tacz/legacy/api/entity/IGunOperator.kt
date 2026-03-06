package com.tacz.legacy.api.entity

import com.tacz.legacy.common.entity.shooter.ShooterDataHolder
import net.minecraft.entity.EntityLivingBase
import net.minecraft.item.ItemStack
import java.util.function.Supplier

/**
 * 枪械操作者接口，由 LivingEntity 通过 Mixin 实现。
 * 与上游 TACZ IGunOperator 行为保持一致。
 */
public interface IGunOperator {
    public fun getSynShootCoolDown(): Long
    public fun getSynMeleeCoolDown(): Long
    public fun getSynDrawCoolDown(): Long
    public fun getSynIsBolting(): Boolean
    public fun getSynReloadState(): ReloadState
    public fun getSynAimingProgress(): Float
    public fun getSynIsAiming(): Boolean
    public fun getSynSprintTime(): Float

    public fun initialData()
    public fun draw(itemStackSupplier: Supplier<ItemStack>)
    public fun bolt()
    public fun reload()
    public fun cancelReload()
    public fun fireSelect()
    public fun melee()
    public fun shoot(pitch: Supplier<Float>, yaw: Supplier<Float>, timestamp: Long): ShootResult

    public fun needCheckAmmo(): Boolean
    public fun consumesAmmoOrNot(): Boolean
    public fun getProcessedSprintStatus(): Float
    public fun aim(isAim: Boolean)

    public fun getDataHolder(): ShooterDataHolder

    /**
     * 服务端每 tick 调用，驱动所有枪械子系统更新。
     */
    public fun tick()

    public companion object {
        @JvmStatic
        public fun fromLivingEntity(entity: EntityLivingBase): IGunOperator = entity as IGunOperator
    }
}
