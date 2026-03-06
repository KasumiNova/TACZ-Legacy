package com.tacz.legacy.api.item

import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation

/**
 * 子弹盒接口。与上游 TACZ IAmmoBox 一致。
 */
public interface IAmmoBox {
    public fun getAmmoId(ammoBox: ItemStack): ResourceLocation
    public fun setAmmoId(ammoBox: ItemStack, ammoId: ResourceLocation)
    public fun getAmmoCount(ammoBox: ItemStack): Int
    public fun setAmmoCount(ammoBox: ItemStack, count: Int)
    public fun isAmmoBoxOfGun(gun: ItemStack, ammoBox: ItemStack): Boolean
    public fun isCreative(ammoBox: ItemStack): Boolean
    public fun isAllTypeCreative(ammoBox: ItemStack): Boolean
}
