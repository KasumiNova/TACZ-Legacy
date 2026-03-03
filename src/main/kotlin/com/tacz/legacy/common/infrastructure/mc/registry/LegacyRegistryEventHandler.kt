package com.tacz.legacy.common.infrastructure.mc.registry

import com.tacz.legacy.TACZLegacy
import net.minecraft.block.Block
import net.minecraft.creativetab.CreativeTabs
import net.minecraft.item.Item
import net.minecraft.item.ItemBlock
import net.minecraftforge.event.RegistryEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

@Mod.EventBusSubscriber(modid = TACZLegacy.MOD_ID)
public object LegacyRegistryEventHandler {

    @JvmStatic
    @SubscribeEvent
    public fun onRegisterBlocks(event: RegistryEvent.Register<Block>) {
        LegacyBlocks.all().forEach { block ->
            event.registry.register(block)
        }

        TACZLegacy.logger.info(
            "[Registry] Registered blocks: {}",
            LegacyBlocks.all().mapNotNull { it.registryName?.toString() }
        )
    }

    @JvmStatic
    @SubscribeEvent
    public fun onRegisterItems(event: RegistryEvent.Register<Item>) {
        val standaloneItems = LegacyItems.prepareRegisteredStandalone()
        standaloneItems.forEach { item ->
            event.registry.register(item)
        }

        LegacyBlocks.all().forEach { block ->
            registerItemBlock(event, block)
        }

        TACZLegacy.logger.info(
            "[Registry] Registered items: {}",
            standaloneItems.mapNotNull { it.registryName?.toString() }
        )
    }

    private fun registerItemBlock(event: RegistryEvent.Register<Item>, block: Block) {
        val blockName = requireNotNull(block.registryName) {
            "Block registryName is null for ${block.javaClass.simpleName}."
        }

        val itemBlock = ItemBlock(block)
        itemBlock.registryName = blockName
        itemBlock.translationKey = "${TACZLegacy.MOD_ID}.${blockName.path}"
        itemBlock.setCreativeTab(CreativeTabs.DECORATIONS)
        event.registry.register(itemBlock)
    }

}
