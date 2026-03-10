package com.tacz.legacy.client

import com.tacz.legacy.TACZLegacy
import com.tacz.legacy.client.animation.screen.RefitTransform
import com.tacz.legacy.client.audio.TACZClientAudioHooks
import com.tacz.legacy.client.event.FirstPersonRenderGunEvent
import com.tacz.legacy.client.event.LegacyClientHitMarkHandler
import com.tacz.legacy.client.event.LegacyClientInputEventHandler
import com.tacz.legacy.client.event.LegacyClientOverlayEventHandler
import com.tacz.legacy.client.event.TACZCameraRecoilHandler
import com.tacz.legacy.client.foundation.FocusedSmokeClientHooks
import com.tacz.legacy.client.gui.GunSmithTableScreen
import com.tacz.legacy.client.input.LegacyKeyBindings
import com.tacz.legacy.client.renderer.block.GunSmithTableTileEntityRenderer
import com.tacz.legacy.client.renderer.bloom.TACZBloomBridge
import com.tacz.legacy.client.renderer.entity.RenderKineticBullet
import com.tacz.legacy.client.renderer.item.TACZAmmoItemRenderer
import com.tacz.legacy.client.renderer.item.TACZAttachmentItemRenderer
import com.tacz.legacy.client.renderer.item.TACZBlockItemRenderer
import com.tacz.legacy.client.renderer.item.TACZGunItemRenderer
import com.tacz.legacy.client.resource.TACZClientAssetManager
import com.tacz.legacy.common.CommonProxy
import com.tacz.legacy.common.block.entity.GunSmithTableTileEntity
import com.tacz.legacy.common.foundation.BootstrapDiagnostics
import com.tacz.legacy.common.foundation.BootstrapStep
import com.tacz.legacy.common.gui.LegacyGuiIds
import com.tacz.legacy.common.inventory.GunSmithTableContainer
import com.tacz.legacy.common.entity.EntityKineticBullet
import com.tacz.legacy.common.item.LegacyItems
import com.tacz.legacy.client.particle.LegacyParticleFactoryRegistry
import com.tacz.legacy.client.registry.ModelRegisterer
import com.tacz.legacy.common.resource.TACZGunPackRuntimeRegistry
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.fml.client.registry.ClientRegistry
import net.minecraftforge.fml.client.registry.IRenderFactory
import net.minecraftforge.fml.client.registry.RenderingRegistry

internal class ClientProxy : CommonProxy() {
    init {
        MinecraftForge.EVENT_BUS.register(ModelRegisterer)
        MinecraftForge.EVENT_BUS.register(ClientConfigEventHandler)
        MinecraftForge.EVENT_BUS.register(TACZClientAudioHooks)
        MinecraftForge.EVENT_BUS.register(LegacyClientHitMarkHandler)
        MinecraftForge.EVENT_BUS.register(LegacyClientInputEventHandler)
        MinecraftForge.EVENT_BUS.register(LegacyClientOverlayEventHandler)
        MinecraftForge.EVENT_BUS.register(FocusedSmokeClientHooks)
        // RefitTransform also uses @JvmStatic handlers on a Kotlin object.
        MinecraftForge.EVENT_BUS.register(RefitTransform::class.java)
        // FirstPersonRenderGunEvent uses @JvmStatic handlers on a Kotlin object.
        // Forge 1.12 only scans static subscribers when registering the class itself.
        MinecraftForge.EVENT_BUS.register(FirstPersonRenderGunEvent::class.java)
        MinecraftForge.EVENT_BUS.register(TACZCameraRecoilHandler::class.java)
    }

    override fun preInit(): Unit {
        super.preInit()
        RenderingRegistry.registerEntityRenderingHandler(
            EntityKineticBullet::class.java,
            IRenderFactory { manager -> RenderKineticBullet(manager) },
        )
    }

    override fun init(): Unit {
        super.init()
        LegacyKeyBindings.registerAll()
        LegacyParticleFactoryRegistry.register()

        // Wire TEISR for gun items (requires "parent": "builtin/entity" in item model JSON)
        LegacyItems.MODERN_KINETIC_GUN.setTileEntityItemStackRenderer(TACZGunItemRenderer)
        LegacyItems.AMMO.setTileEntityItemStackRenderer(TACZAmmoItemRenderer)
        LegacyItems.ATTACHMENT.setTileEntityItemStackRenderer(TACZAttachmentItemRenderer)
        LegacyItems.GUN_SMITH_TABLE.setTileEntityItemStackRenderer(TACZBlockItemRenderer)
        LegacyItems.WORKBENCH_A.setTileEntityItemStackRenderer(TACZBlockItemRenderer)
        LegacyItems.WORKBENCH_B.setTileEntityItemStackRenderer(TACZBlockItemRenderer)
        LegacyItems.WORKBENCH_C.setTileEntityItemStackRenderer(TACZBlockItemRenderer)

        // World-space TESR for gun smith table / workbench blocks
        ClientRegistry.bindTileEntitySpecialRenderer(GunSmithTableTileEntity::class.java, GunSmithTableTileEntityRenderer())

        // Load client-side assets (models, textures) from the already-loaded gun pack snapshot
        val snapshot = TACZGunPackRuntimeRegistry.getSnapshot()
        TACZClientAssetManager.reload(snapshot)
        TACZBloomBridge.initIfPresent()

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
