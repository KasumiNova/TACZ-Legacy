package com.tacz.legacy.api.event

import net.minecraft.entity.Entity
import net.minecraft.entity.EntityLivingBase
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation
import net.minecraftforge.fml.common.eventhandler.Cancelable
import net.minecraftforge.fml.common.eventhandler.Event
import net.minecraftforge.fml.relauncher.Side

/**
 * 扣动扳机时触发（每次扳机仅触发一次，与 [GunFireEvent] 不同）。
 * 与上游 TACZ GunShootEvent 行为保持一致。
 */
@Cancelable
public class GunShootEvent(
    public val shooter: EntityLivingBase,
    public val gunItemStack: ItemStack,
    public val logicalSide: Side,
) : Event()

/**
 * 每发子弹击发时触发（Burst 模式一次扳机可多次触发）。
 * 与上游 TACZ GunFireEvent 行为保持一致。
 */
@Cancelable
public class GunFireEvent(
    public val shooter: EntityLivingBase,
    public val gunItemStack: ItemStack,
    public val logicalSide: Side,
) : Event()

/**
 * 开始换弹时触发。
 * 与上游 TACZ GunReloadEvent 行为保持一致。
 */
@Cancelable
public class GunReloadEvent(
    public val entity: EntityLivingBase,
    public val gunItemStack: ItemStack,
    public val logicalSide: Side,
) : Event()

/**
 * 近战时触发。
 * 与上游 TACZ GunMeleeEvent 行为保持一致。
 */
@Cancelable
public class GunMeleeEvent(
    public val shooter: EntityLivingBase,
    public val gunItemStack: ItemStack,
    public val logicalSide: Side,
) : Event()

/**
 * 切枪时触发。
 * 与上游 TACZ GunDrawEvent 行为保持一致。
 */
public class GunDrawEvent(
    public val entity: EntityLivingBase,
    public val previousGunItem: ItemStack,
    public val currentGunItem: ItemStack,
    public val logicalSide: Side,
) : Event()

/**
 * 实体被枪械子弹命中后的事件。
 * 当前 Legacy 迁移以 HUD / 音效反馈为主，暴露 Post 事件供客户端与服务端复用。
 */
public open class EntityHurtByGunEvent(
    public val bullet: Entity?,
    public val hurtEntity: Entity?,
    public val attacker: EntityLivingBase?,
    public val gunId: ResourceLocation,
    public val gunDisplayId: ResourceLocation,
    public val baseAmount: Float,
    public val isHeadShot: Boolean,
    public val headShotMultiplier: Float,
    public val logicalSide: Side,
) : Event() {
    public val amount: Float
        get() = if (isHeadShot) baseAmount * headShotMultiplier else baseAmount

    public class Post(
        bullet: Entity?,
        hurtEntity: Entity?,
        attacker: EntityLivingBase?,
        gunId: ResourceLocation,
        gunDisplayId: ResourceLocation,
        baseAmount: Float,
        isHeadShot: Boolean,
        headShotMultiplier: Float,
        logicalSide: Side,
    ) : EntityHurtByGunEvent(
        bullet = bullet,
        hurtEntity = hurtEntity,
        attacker = attacker,
        gunId = gunId,
        gunDisplayId = gunDisplayId,
        baseAmount = baseAmount,
        isHeadShot = isHeadShot,
        headShotMultiplier = headShotMultiplier,
        logicalSide = logicalSide,
    )
}

/**
 * 实体被枪械子弹击杀时触发。
 */
public class EntityKillByGunEvent(
    public val bullet: Entity?,
    public val killedEntity: EntityLivingBase?,
    public val attacker: EntityLivingBase?,
    public val gunId: ResourceLocation,
    public val gunDisplayId: ResourceLocation,
    public val baseDamage: Float,
    public val isHeadShot: Boolean,
    public val headShotMultiplier: Float,
    public val logicalSide: Side,
) : Event() {
    public val amount: Float
        get() = if (isHeadShot) baseDamage * headShotMultiplier else baseDamage
}
