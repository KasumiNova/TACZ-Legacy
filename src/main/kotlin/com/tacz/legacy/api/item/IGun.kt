package com.tacz.legacy.api.item

import com.tacz.legacy.api.item.attachment.AttachmentType
import com.tacz.legacy.api.item.gun.FireMode
import net.minecraft.entity.EntityLivingBase
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.ResourceLocation

/**
 * 枪械 NBT 访问接口。不包含枪械逻辑，仅提供数据访问。
 * 与上游 TACZ IGun 行为保持一致。
 */
public interface IGun {
    public fun getGunId(stack: ItemStack): ResourceLocation
    public fun setGunId(stack: ItemStack, gunId: ResourceLocation?)

    public fun getFireMode(stack: ItemStack): FireMode
    public fun setFireMode(stack: ItemStack, fireMode: FireMode?)

    public fun getCurrentAmmoCount(stack: ItemStack): Int
    public fun setCurrentAmmoCount(stack: ItemStack, ammoCount: Int)
    public fun reduceCurrentAmmoCount(stack: ItemStack)

    public fun hasBulletInBarrel(stack: ItemStack): Boolean
    public fun setBulletInBarrel(stack: ItemStack, bulletInBarrel: Boolean)

    public fun useInventoryAmmo(stack: ItemStack): Boolean
    public fun hasInventoryAmmo(shooter: EntityLivingBase, stack: ItemStack, needCheckAmmo: Boolean): Boolean

    public fun useDummyAmmo(stack: ItemStack): Boolean
    public fun getDummyAmmoAmount(stack: ItemStack): Int
    public fun setDummyAmmoAmount(stack: ItemStack, amount: Int)
    public fun addDummyAmmoAmount(stack: ItemStack, amount: Int)

    public fun getAttachmentTag(stack: ItemStack, type: AttachmentType): NBTTagCompound?
    public fun getAttachment(stack: ItemStack, type: AttachmentType): ItemStack
    public fun getAttachmentId(stack: ItemStack, type: AttachmentType): ResourceLocation
    public fun installAttachment(gun: ItemStack, attachment: ItemStack)
    public fun unloadAttachment(gun: ItemStack, type: AttachmentType)
    public fun allowAttachment(gun: ItemStack, attachmentItem: ItemStack): Boolean
    public fun allowAttachmentType(gun: ItemStack, type: AttachmentType): Boolean

    public fun hasAttachmentLock(stack: ItemStack): Boolean
    public fun setAttachmentLock(stack: ItemStack, locked: Boolean)

    public fun isOverheatLocked(stack: ItemStack): Boolean
    public fun setOverheatLocked(stack: ItemStack, locked: Boolean)

    public companion object {
        public const val ATTACHMENT_BASE_TAG: String = "Attachment"
        public const val ATTACHMENT_LOCK_TAG: String = "AttachmentLock"
        public const val DUMMY_AMMO_TAG: String = "DummyAmmo"
        public const val GUN_ID_TAG: String = "GunId"
        public const val FIRE_MODE_TAG: String = "GunFireMode"
        public const val AMMO_COUNT_TAG: String = "GunCurrentAmmoCount"
        public const val BULLET_IN_BARREL_TAG: String = "HasBulletInBarrel"
        public const val OVERHEAT_LOCK_TAG: String = "OverHeated"

        @JvmStatic
        public fun getIGunOrNull(stack: ItemStack): IGun? = stack.item as? IGun

        @JvmStatic
        public fun mainHandHoldGun(entity: EntityLivingBase): Boolean =
            entity.heldItemMainhand.item is IGun

        @JvmStatic
        public fun getMainHandFireMode(entity: EntityLivingBase): FireMode {
            val stack = entity.heldItemMainhand
            val iGun = stack.item as? IGun ?: return FireMode.UNKNOWN
            return iGun.getFireMode(stack)
        }
    }
}
