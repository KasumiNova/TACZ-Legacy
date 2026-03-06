package com.tacz.legacy.api.item

import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation

/**
 * 弹药物品接口。与上游 TACZ IAmmo 一致。
 */
public interface IAmmo {
    public fun getAmmoId(ammo: ItemStack): ResourceLocation
    public fun setAmmoId(ammo: ItemStack, ammoId: ResourceLocation?)
    public fun isAmmoOfGun(gun: ItemStack, ammo: ItemStack): Boolean

    public companion object {
        @JvmStatic
        public fun getIAmmoOrNull(stack: ItemStack?): IAmmo? {
            if (stack == null || stack.isEmpty) return null
            val item = stack.item
            return item as? IAmmo
        }
    }
}
