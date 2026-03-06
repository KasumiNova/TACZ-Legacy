package com.tacz.legacy.api.event

import net.minecraft.entity.EntityLivingBase
import net.minecraft.item.ItemStack
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
