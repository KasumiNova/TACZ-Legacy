package com.tacz.legacy.common.infrastructure.mc.registry.item

import com.tacz.legacy.common.application.weapon.WeaponRuntime
import com.tacz.legacy.common.infrastructure.mc.registry.LegacyCreativeTabs
import com.tacz.legacy.common.infrastructure.mc.weapon.WeaponItemStackRuntimeData
import net.minecraft.creativetab.CreativeTabs
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.util.ActionResult
import net.minecraft.util.EnumActionResult
import net.minecraft.util.EnumHand
import net.minecraft.world.World

public open class LegacyAmmoItem(
    registryPath: String,
    public val ammoId: String,
    public val roundsPerItem: Int,
    public val sourceId: String? = null,
    public val iconTextureAssetPath: String? = null,
    creativeTab: CreativeTabs = LegacyCreativeTabs.OTHER
) : LegacySimpleItem(
    registryPath = registryPath,
    creativeTab = creativeTab,
    maxStackSize = 64
) {

    override fun onItemRightClick(worldIn: World, playerIn: EntityPlayer, handIn: EnumHand): ActionResult<ItemStack> {
        val ammoStack = playerIn.getHeldItem(handIn)
        val gunStack = if (handIn == EnumHand.MAIN_HAND) playerIn.heldItemOffhand else playerIn.heldItemMainhand
        if (gunStack.isEmpty || gunStack.item !is LegacyGunItem) {
            return ActionResult(EnumActionResult.PASS, ammoStack)
        }

        val gunId = gunStack.item.registryName
            ?.toString()
            ?.substringAfter(':')
            ?.trim()
            ?.lowercase()
            ?.ifBlank { null }
            ?: return ActionResult(EnumActionResult.PASS, ammoStack)

        val definition = WeaponRuntime.registry().snapshot().findDefinition(gunId)
            ?: return ActionResult(EnumActionResult.PASS, ammoStack)

        if (!matchesAmmo(definition.ammoId)) {
            return ActionResult(EnumActionResult.PASS, ammoStack)
        }

        val currentReserve = WeaponItemStackRuntimeData.readAmmoReserve(gunStack, 0)
        val added = roundsPerItem.coerceAtLeast(1)
        WeaponItemStackRuntimeData.writeAmmoState(
            stack = gunStack,
            ammoInMagazine = WeaponItemStackRuntimeData.readAmmoInMagazine(gunStack, definition.spec.magazineSize),
            ammoReserve = (currentReserve + added).coerceAtMost(MAX_RESERVE_CAPACITY),
            hasBulletInBarrel = WeaponItemStackRuntimeData.readHasBulletInBarrel(gunStack)
        )

        if (!playerIn.capabilities.isCreativeMode && shouldConsumeStackOnUse()) {
            ammoStack.shrink(1)
        }

        return ActionResult(EnumActionResult.SUCCESS, ammoStack)
    }

    protected fun matchesAmmo(targetAmmoId: String): Boolean {
        val normalizedTarget = targetAmmoId.trim().lowercase()
        val normalizedSelf = ammoId.trim().lowercase()
        return normalizedTarget == normalizedSelf
    }

    protected open fun shouldConsumeStackOnUse(): Boolean = true

    private companion object {
        private const val MAX_RESERVE_CAPACITY: Int = 9_999
    }
}
