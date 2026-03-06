package com.tacz.legacy.common.item

import com.tacz.legacy.api.item.IGun
import com.tacz.legacy.api.item.gun.FireMode
import net.minecraft.init.Bootstrap
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test

/**
 * ModernKineticGunItem 的 IGun NBT 实现回归测试。
 * 验证所有 NBT 读写方法行为与上游 TACZ 一致。
 */
class ModernKineticGunItemNBTTest {

    companion object {
        private lateinit var gunItem: ModernKineticGunItem

        @JvmStatic
        @BeforeClass
        fun setup() {
            Bootstrap.register()
            gunItem = ModernKineticGunItem()
        }
    }

    private fun newGunStack(): ItemStack = ItemStack(gunItem)

    @Test
    fun `fire mode defaults to UNKNOWN for missing tag`() {
        val stack = newGunStack()
        assertEquals(FireMode.UNKNOWN, gunItem.getFireMode(stack))
    }

    @Test
    fun `fire mode round trips correctly`() {
        val stack = newGunStack()
        gunItem.setFireMode(stack, FireMode.AUTO)
        assertEquals(FireMode.AUTO, gunItem.getFireMode(stack))
        gunItem.setFireMode(stack, FireMode.SEMI)
        assertEquals(FireMode.SEMI, gunItem.getFireMode(stack))
        gunItem.setFireMode(stack, FireMode.BURST)
        assertEquals(FireMode.BURST, gunItem.getFireMode(stack))
    }

    @Test
    fun `ammo count defaults to 0 for missing tag`() {
        val stack = newGunStack()
        assertEquals(0, gunItem.getCurrentAmmoCount(stack))
    }

    @Test
    fun `ammo count set and get round trips`() {
        val stack = newGunStack()
        gunItem.setCurrentAmmoCount(stack, 30)
        assertEquals(30, gunItem.getCurrentAmmoCount(stack))
    }

    @Test
    fun `ammo count negative clamped to 0`() {
        val stack = newGunStack()
        gunItem.setCurrentAmmoCount(stack, -5)
        assertEquals(0, gunItem.getCurrentAmmoCount(stack))
    }

    @Test
    fun `reduce current ammo count decrements by 1`() {
        val stack = newGunStack()
        gunItem.setCurrentAmmoCount(stack, 10)
        gunItem.reduceCurrentAmmoCount(stack)
        assertEquals(9, gunItem.getCurrentAmmoCount(stack))
    }

    @Test
    fun `bullet in barrel defaults to false`() {
        val stack = newGunStack()
        assertFalse(gunItem.hasBulletInBarrel(stack))
    }

    @Test
    fun `bullet in barrel round trips`() {
        val stack = newGunStack()
        gunItem.setBulletInBarrel(stack, true)
        assertTrue(gunItem.hasBulletInBarrel(stack))
        gunItem.setBulletInBarrel(stack, false)
        assertFalse(gunItem.hasBulletInBarrel(stack))
    }

    @Test
    fun `dummy ammo not used when tag absent`() {
        val stack = newGunStack()
        assertFalse(gunItem.useDummyAmmo(stack))
    }

    @Test
    fun `dummy ammo amount get and set`() {
        val stack = newGunStack()
        gunItem.setDummyAmmoAmount(stack, 100)
        assertTrue(gunItem.useDummyAmmo(stack))
        assertEquals(100, gunItem.getDummyAmmoAmount(stack))
    }

    @Test
    fun `dummy ammo amount add increments`() {
        val stack = newGunStack()
        gunItem.setDummyAmmoAmount(stack, 50)
        gunItem.addDummyAmmoAmount(stack, 25)
        assertEquals(75, gunItem.getDummyAmmoAmount(stack))
    }

    @Test
    fun `dummy ammo amount add clamps to 0`() {
        val stack = newGunStack()
        gunItem.setDummyAmmoAmount(stack, 10)
        gunItem.addDummyAmmoAmount(stack, -50)
        assertEquals(0, gunItem.getDummyAmmoAmount(stack))
    }

    @Test
    fun `attachment lock defaults to false`() {
        val stack = newGunStack()
        assertFalse(gunItem.hasAttachmentLock(stack))
    }

    @Test
    fun `attachment lock round trips`() {
        val stack = newGunStack()
        gunItem.setAttachmentLock(stack, true)
        assertTrue(gunItem.hasAttachmentLock(stack))
    }

    @Test
    fun `overheat lock defaults to false`() {
        val stack = newGunStack()
        assertFalse(gunItem.isOverheatLocked(stack))
    }

    @Test
    fun `overheat lock round trips`() {
        val stack = newGunStack()
        gunItem.setOverheatLocked(stack, true)
        assertTrue(gunItem.isOverheatLocked(stack))
        gunItem.setOverheatLocked(stack, false)
        assertFalse(gunItem.isOverheatLocked(stack))
    }

    @Test
    fun `NBT tag constants match upstream`() {
        assertEquals("GunId", IGun.GUN_ID_TAG)
        assertEquals("GunFireMode", IGun.FIRE_MODE_TAG)
        assertEquals("GunCurrentAmmoCount", IGun.AMMO_COUNT_TAG)
        assertEquals("HasBulletInBarrel", IGun.BULLET_IN_BARREL_TAG)
        assertEquals("DummyAmmo", IGun.DUMMY_AMMO_TAG)
        assertEquals("AttachmentLock", IGun.ATTACHMENT_LOCK_TAG)
        assertEquals("OverHeated", IGun.OVERHEAT_LOCK_TAG)
    }
}
