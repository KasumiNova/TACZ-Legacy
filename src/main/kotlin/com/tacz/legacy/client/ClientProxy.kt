package com.tacz.legacy.client

import com.tacz.legacy.TACZLegacy
import com.tacz.legacy.client.event.LegacyClientInputEventHandler
import com.tacz.legacy.client.event.LegacyClientOverlayEventHandler
import com.tacz.legacy.client.gui.GunSmithTableScreen
import com.tacz.legacy.client.input.LegacyKeyBindings
import com.tacz.legacy.client.renderer.item.TACZGunItemRenderer
import com.tacz.legacy.client.resource.TACZClientAssetManager
import com.tacz.legacy.common.CommonProxy
import com.tacz.legacy.common.foundation.BootstrapDiagnostics
import com.tacz.legacy.common.foundation.BootstrapStep
import com.tacz.legacy.common.gui.LegacyGuiIds
import com.tacz.legacy.common.inventory.GunSmithTableContainer
import com.tacz.legacy.common.item.LegacyItems
import com.tacz.legacy.client.particle.LegacyParticleFactoryRegistry
import com.tacz.legacy.client.registry.ModelRegisterer
import com.tacz.legacy.common.resource.TACZGunPackRuntimeRegistry
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import net.minecraftforge.common.MinecraftForge

internal class ClientProxy : CommonProxy() {
    init {
        MinecraftForge.EVENT_BUS.register(ModelRegisterer)
        MinecraftForge.EVENT_BUS.register(ClientConfigEventHandler)
        MinecraftForge.EVENT_BUS.register(LegacyClientInputEventHandler)
        MinecraftForge.EVENT_BUS.register(LegacyClientOverlayEventHandler)
    }

    override fun init(): Unit {
        super.init()
        LegacyKeyBindings.registerAll()
        LegacyParticleFactoryRegistry.register()

        // Wire TEISR for gun items (requires "parent": "builtin/entity" in item model JSON)
        LegacyItems.MODERN_KINETIC_GUN.setTileEntityItemStackRenderer(TACZGunItemRenderer)

        // Load client-side assets (models, textures) from the already-loaded gun pack snapshot
        val snapshot = TACZGunPackRuntimeRegistry.getSnapshot()
        TACZClientAssetManager.reload(snapshot)

        BootstrapDiagnostics.record(BootstrapStep.CLIENT_RUNTIME_READY)
        TACZLegacy.logger.info("[FoundationSmoke] CLIENT runtime hooks ready.")
    }

    override fun createClientGuiElement(id: Int, player: EntityPlayer, world: World, pos: BlockPos): Any? {
        return when (id) {
            LegacyGuiIds.GUN_SMITH_TABLE -> GunSmithTableScreen(GunSmithTableContainer(player.inventory, world, pos), player.inventory)
            else -> null
        }
    }
}
