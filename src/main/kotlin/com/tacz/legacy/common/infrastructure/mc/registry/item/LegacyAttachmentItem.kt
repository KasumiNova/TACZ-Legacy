package com.tacz.legacy.common.infrastructure.mc.registry.item

import com.tacz.legacy.common.infrastructure.mc.registry.LegacyCreativeTabs
import com.tacz.legacy.common.infrastructure.mc.weapon.WeaponAttachmentConflictRules
import com.tacz.legacy.common.infrastructure.mc.weapon.WeaponAttachmentSlot
import com.tacz.legacy.common.infrastructure.mc.weapon.WeaponItemStackRuntimeData
import com.tacz.legacy.common.application.weapon.WeaponRuntime
import net.minecraft.creativetab.CreativeTabs
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.util.ActionResult
import net.minecraft.util.EnumActionResult
import net.minecraft.util.EnumHand
import net.minecraft.util.text.TextComponentString
import net.minecraft.world.World

public class LegacyAttachmentItem(
    registryPath: String,
    public val slot: WeaponAttachmentSlot,
    public val attachmentId: String,
    public val sourceId: String? = null,
    public val iconTextureAssetPath: String? = null,
    creativeTab: CreativeTabs = LegacyCreativeTabs.OTHER
) : LegacySimpleItem(
    registryPath = registryPath,
    creativeTab = creativeTab,
    maxStackSize = 64
) {

    override fun onItemRightClick(worldIn: World, playerIn: EntityPlayer, handIn: EnumHand): ActionResult<ItemStack> {
        val attachmentStack = playerIn.getHeldItem(handIn)
        val gunStack = if (handIn == EnumHand.MAIN_HAND) playerIn.heldItemOffhand else playerIn.heldItemMainhand
        if (gunStack.isEmpty || gunStack.item !is LegacyGunItem) {
            return ActionResult(EnumActionResult.PASS, attachmentStack)
        }

        val gunId = gunStack.item.registryName
            ?.toString()
            ?.substringAfter(':')
            ?.trim()
            ?.lowercase()
            ?.ifBlank { null }
            ?: return ActionResult(EnumActionResult.PASS, attachmentStack)
        val definition = WeaponRuntime.registry().snapshot().findDefinition(gunId)
            ?: return ActionResult(EnumActionResult.PASS, attachmentStack)

        val snapshot = WeaponItemStackRuntimeData.readAttachmentSnapshot(gunStack)
        val check = WeaponAttachmentConflictRules.validateInstall(
            snapshot = snapshot,
            slot = slot,
            attachmentId = attachmentId,
            gunId = gunId,
            definition = definition
        )

        if (!check.accepted) {
            if (!worldIn.isRemote) {
                playerIn.sendStatusMessage(
                    TextComponentString(check.reasonMessage ?: "配件安装失败"),
                    true
                )
            }
            return ActionResult(EnumActionResult.FAIL, attachmentStack)
        }

        WeaponItemStackRuntimeData.writeAttachment(
            stack = gunStack,
            slot = slot,
            attachmentId = attachmentId
        )

        if (!playerIn.capabilities.isCreativeMode) {
            attachmentStack.shrink(1)
        }

        if (!worldIn.isRemote) {
            playerIn.sendStatusMessage(
                TextComponentString("已安装配件: ${slot.name} -> $attachmentId"),
                true
            )
        }

        return ActionResult(EnumActionResult.SUCCESS, attachmentStack)
    }
}
