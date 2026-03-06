package com.tacz.legacy.common.item

import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound

public enum class GunTooltipPart {
    DESCRIPTION,
    AMMO_INFO,
    BASE_INFO,
    EXTRA_DAMAGE_INFO,
    UPGRADES_TIP,
    PACK_INFO;

    public val mask: Int = 1 shl ordinal

    public companion object {
        public const val HIDE_FLAGS_TAG: String = "HideFlags"

        @JvmStatic
        public fun setHideFlags(stack: ItemStack, mask: Int): Unit {
            ensureTag(stack).setInteger(HIDE_FLAGS_TAG, mask)
        }

        @JvmStatic
        public fun getHideFlags(stack: ItemStack): Int =
            stack.tagCompound?.takeIf { it.hasKey(HIDE_FLAGS_TAG) }?.getInteger(HIDE_FLAGS_TAG) ?: 0

        private fun ensureTag(stack: ItemStack): NBTTagCompound {
            val existing: NBTTagCompound? = stack.tagCompound
            if (existing != null) {
                return existing
            }
            val created = NBTTagCompound()
            stack.tagCompound = created
            return created
        }
    }
}
