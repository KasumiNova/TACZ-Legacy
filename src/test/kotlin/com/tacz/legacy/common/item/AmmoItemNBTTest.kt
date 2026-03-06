package com.tacz.legacy.common.item

import com.tacz.legacy.api.DefaultAssets
import com.tacz.legacy.api.item.IAmmo
import com.tacz.legacy.api.item.IAmmoBox
import net.minecraft.init.Bootstrap
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test

/**
 * AmmoItem / AmmoBoxItem NBT accessor 回归测试。
 */
class AmmoItemNBTTest {

    companion object {
        @JvmStatic
        @BeforeClass
        fun setup() {
            Bootstrap.register()
        }
    }

    // --- AmmoItem ---

    @Test
    fun `AmmoItem implements IAmmo`() {
        val ammoItem = AmmoItem()
        assertTrue(ammoItem is IAmmo)
    }

    @Test
    fun `getAmmoId returns EMPTY_AMMO_ID for empty tag`() {
        val ammoItem = AmmoItem()
        val stack = ItemStack(ammoItem)
        assertEquals(DefaultAssets.EMPTY_AMMO_ID, ammoItem.getAmmoId(stack))
    }

    @Test
    fun `setAmmoId and getAmmoId round trip`() {
        val ammoItem = AmmoItem()
        val stack = ItemStack(ammoItem)
        val testId = ResourceLocation("tacz", "9mm")
        ammoItem.setAmmoId(stack, testId)
        assertEquals(testId, ammoItem.getAmmoId(stack))
    }

    @Test
    fun `setAmmoId null sets DEFAULT_AMMO_ID`() {
        val ammoItem = AmmoItem()
        val stack = ItemStack(ammoItem)
        ammoItem.setAmmoId(stack, null)
        assertEquals(DefaultAssets.DEFAULT_AMMO_ID, ammoItem.getAmmoId(stack))
    }

    @Test
    fun `IAmmo companion getIAmmoOrNull works`() {
        val ammoItem = AmmoItem()
        val stack = ItemStack(ammoItem)
        assertNotNull(IAmmo.getIAmmoOrNull(stack))
        assertNull(IAmmo.getIAmmoOrNull(null))
        assertNull(IAmmo.getIAmmoOrNull(ItemStack.EMPTY))
    }

    // --- AmmoBoxItem ---

    @Test
    fun `AmmoBoxItem implements IAmmoBox`() {
        val boxItem = AmmoBoxItem()
        assertTrue(boxItem is IAmmoBox)
    }

    @Test
    fun `AmmoBoxItem ammoId defaults to EMPTY_AMMO_ID`() {
        val boxItem = AmmoBoxItem()
        val stack = ItemStack(boxItem)
        assertEquals(DefaultAssets.EMPTY_AMMO_ID, boxItem.getAmmoId(stack))
    }

    @Test
    fun `AmmoBoxItem ammo count defaults to 0`() {
        val boxItem = AmmoBoxItem()
        val stack = ItemStack(boxItem)
        assertEquals(0, boxItem.getAmmoCount(stack))
    }

    @Test
    fun `AmmoBoxItem setAmmoCount and getAmmoCount round trip`() {
        val boxItem = AmmoBoxItem()
        val stack = ItemStack(boxItem)
        boxItem.setAmmoCount(stack, 120)
        assertEquals(120, boxItem.getAmmoCount(stack))
    }

    @Test
    fun `AmmoBoxItem creative defaults to false`() {
        val boxItem = AmmoBoxItem()
        val stack = ItemStack(boxItem)
        assertFalse(boxItem.isCreative(stack))
        assertFalse(boxItem.isAllTypeCreative(stack))
    }

    @Test
    fun `AmmoBoxItem creative returns MAX_VALUE ammo count`() {
        val boxItem = AmmoBoxItem()
        val stack = ItemStack(boxItem)
        // Manually set creative tag
        val tag = stack.tagCompound ?: net.minecraft.nbt.NBTTagCompound().also { stack.tagCompound = it }
        tag.setBoolean("Creative", true)
        assertEquals(Int.MAX_VALUE, boxItem.getAmmoCount(stack))
    }

    @Test
    fun `AmmoBoxItem allTypeCreative returns MAX_VALUE ammo count`() {
        val boxItem = AmmoBoxItem()
        val stack = ItemStack(boxItem)
        val tag = stack.tagCompound ?: net.minecraft.nbt.NBTTagCompound().also { stack.tagCompound = it }
        tag.setBoolean("AllTypeCreative", true)
        assertEquals(Int.MAX_VALUE, boxItem.getAmmoCount(stack))
    }

    @Test
    fun `AmmoBoxItem setAmmoId and getAmmoId round trip`() {
        val boxItem = AmmoBoxItem()
        val stack = ItemStack(boxItem)
        val testId = ResourceLocation("tacz", "762mm")
        boxItem.setAmmoId(stack, testId)
        assertEquals(testId, boxItem.getAmmoId(stack))
    }

    @Test
    fun `AmmoBoxItem setAmmoCount for creative forces MAX_VALUE`() {
        val boxItem = AmmoBoxItem()
        val stack = ItemStack(boxItem)
        val tag = stack.tagCompound ?: net.minecraft.nbt.NBTTagCompound().also { stack.tagCompound = it }
        tag.setBoolean("Creative", true)
        boxItem.setAmmoCount(stack, 50)
        assertEquals(Int.MAX_VALUE, boxItem.getAmmoCount(stack))
    }
}
