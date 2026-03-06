package com.tacz.legacy.common.entity.shooter

import com.tacz.legacy.api.entity.ReloadState
import com.tacz.legacy.api.entity.ShootResult
import com.tacz.legacy.api.item.IGun
import com.tacz.legacy.api.item.gun.FireMode
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * ShooterDataHolder 状态管理的单元回归测试。
 */
class ShooterDataHolderTest {

    private lateinit var holder: ShooterDataHolder

    @Before
    fun setUp() {
        holder = ShooterDataHolder()
    }

    @Test
    fun `initial state has expected defaults`() {
        assertEquals(-1L, holder.shootTimestamp)
        assertEquals(-1L, holder.lastShootTimestamp)
        assertEquals(-1L, holder.meleeTimestamp)
        assertEquals(-1, holder.meleePrepTickCount)
        assertEquals(-1L, holder.drawTimestamp)
        assertEquals(-1L, holder.boltTimestamp)
        assertFalse(holder.isBolting)
        assertEquals(0f, holder.aimingProgress, 0.001f)
        assertFalse(holder.isAiming)
        assertEquals(ReloadState.StateType.NOT_RELOADING, holder.reloadStateType)
        assertNull(holder.currentGunItem)
        assertEquals(0f, holder.sprintTimeS, 0.001f)
        assertEquals(-1.0, holder.knockbackStrength, 0.001)
        assertFalse(holder.isCrawling)
    }

    @Test
    fun `initialData resets all combat state`() {
        holder.shootTimestamp = 100L
        holder.lastShootTimestamp = 50L
        holder.isAiming = true
        holder.aimingProgress = 0.5f
        holder.reloadStateType = ReloadState.StateType.TACTICAL_RELOAD_FEEDING
        holder.isBolting = true
        holder.sprintTimeS = 0.2f

        holder.initialData()

        assertEquals(-1L, holder.shootTimestamp)
        assertEquals(-1L, holder.lastShootTimestamp)
        assertFalse(holder.isAiming)
        assertEquals(0f, holder.aimingProgress, 0.001f)
        assertEquals(ReloadState.StateType.NOT_RELOADING, holder.reloadStateType)
        assertFalse(holder.isBolting)
        assertEquals(0f, holder.sprintTimeS, 0.001f)
    }

    @Test
    fun `reload state type enum isReloading tracks correctly`() {
        assertFalse(ReloadState.StateType.NOT_RELOADING.isReloading())
        assertTrue(ReloadState.StateType.EMPTY_RELOAD_FEEDING.isReloading())
        assertTrue(ReloadState.StateType.EMPTY_RELOAD_FINISHING.isReloading())
        assertTrue(ReloadState.StateType.TACTICAL_RELOAD_FEEDING.isReloading())
        assertTrue(ReloadState.StateType.TACTICAL_RELOAD_FINISHING.isReloading())
    }

    @Test
    fun `ReloadState helper methods`() {
        val empty = ReloadState(ReloadState.StateType.EMPTY_RELOAD_FEEDING, 500L)
        assertTrue(empty.isReloading())
        assertTrue(empty.isReloadingEmpty())
        assertFalse(empty.isReloadingTactical())

        val tactical = ReloadState(ReloadState.StateType.TACTICAL_RELOAD_FINISHING, 200L)
        assertTrue(tactical.isReloading())
        assertFalse(tactical.isReloadingEmpty())
        assertTrue(tactical.isReloadingTactical())
        assertTrue(tactical.isReloadFinishing())

        val notReloading = ReloadState()
        assertFalse(notReloading.isReloading())
        assertEquals(ReloadState.NOT_RELOADING_COUNTDOWN, notReloading.getEffectiveCountDown())
    }

    @Test
    fun `ShootResult enum values match upstream`() {
        val expected = setOf(
            "SUCCESS", "UNKNOWN_FAIL", "COOL_DOWN", "NO_AMMO", "NOT_DRAW",
            "NOT_GUN", "ID_NOT_EXIST", "NEED_BOLT", "IS_RELOADING",
            "IS_DRAWING", "IS_BOLTING", "IS_MELEE", "IS_SPRINTING",
            "NETWORK_FAIL", "FORGE_EVENT_CANCEL", "OVERHEATED",
        )
        val actual = ShootResult.entries.map { it.name }.toSet()
        assertEquals(expected, actual)
    }

    @Test
    fun `FireMode enum values and serialization names`() {
        assertEquals(4, FireMode.entries.size)
        assertNotNull(FireMode.valueOf("AUTO"))
        assertNotNull(FireMode.valueOf("SEMI"))
        assertNotNull(FireMode.valueOf("BURST"))
        assertNotNull(FireMode.valueOf("UNKNOWN"))
    }
}
