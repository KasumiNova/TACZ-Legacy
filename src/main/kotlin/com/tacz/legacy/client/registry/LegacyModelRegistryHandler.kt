package com.tacz.legacy.client.registry

import com.tacz.legacy.TACZLegacy
import com.tacz.legacy.client.render.item.LegacyGunItemStackRenderer
import com.tacz.legacy.common.infrastructure.mc.registry.LegacyBlocks
import com.tacz.legacy.common.infrastructure.mc.registry.LegacyItems
import com.tacz.legacy.common.infrastructure.mc.registry.item.LegacyGunItem
import net.minecraft.init.Items
import net.minecraft.item.Item
import net.minecraft.util.ResourceLocation
import net.minecraftforge.client.event.ModelRegistryEvent
import net.minecraft.client.renderer.block.model.ModelResourceLocation
import net.minecraftforge.client.model.ModelLoader
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.relauncher.Side

@Mod.EventBusSubscriber(modid = TACZLegacy.MOD_ID, value = [Side.CLIENT])
public object LegacyModelRegistryHandler {

    @JvmStatic
    @SubscribeEvent
    public fun onModelRegistry(event: ModelRegistryEvent) {
        LegacyItems.registeredStandalone()
            .filterIsInstance<LegacyGunItem>()
            .forEach { gunItem ->
                gunItem.setTileEntityItemStackRenderer(LegacyGunItemStackRenderer)
            }

        LegacyItems.registeredStandalone().forEach { item ->
            registerInventoryModel(item)
        }

        LegacyBlocks.all().forEach { block ->
            val blockItem = Item.getItemFromBlock(block)
            if (blockItem != Items.AIR) {
                registerInventoryModel(blockItem)
            }
        }
    }

    private fun registerInventoryModel(item: Item) {
        val registryName = requireNotNull(item.registryName) {
            "Item registryName is null for ${item.javaClass.simpleName}."
        }

        val modelResource = if (item is LegacyGunItem) {
            ModelResourceLocation(
                ResourceLocation(TACZLegacy.MOD_ID, MODERN_GUN_ITEM_MODEL_PATH),
                "inventory"
            )
        } else {
            ModelResourceLocation(registryName, "inventory")
        }

        ModelLoader.setCustomModelResourceLocation(
            item,
            0,
            modelResource
        )
    }

    private const val MODERN_GUN_ITEM_MODEL_PATH: String = "modern_kinetic_gun"

}
