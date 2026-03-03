package com.tacz.legacy.client

import com.tacz.legacy.TACZLegacy
import com.tacz.legacy.client.command.TaczDebugUiClientCommand
import com.tacz.legacy.client.command.TaczMovementFovClientCommand
import com.tacz.legacy.client.command.TaczReloadGunPackClientCommand
import com.tacz.legacy.client.command.TaczSpecialBlockProbeClientCommand
import com.tacz.legacy.client.command.TaczWeaponDiagClientCommand
import com.tacz.legacy.client.input.WeaponKeyBindings
import com.tacz.legacy.client.input.WeaponKeyInputEventHandler
import com.tacz.legacy.client.resource.GunPackExternalResourcePackManager
import com.tacz.legacy.client.render.block.LegacySpecialBlockModelRegistry
import com.tacz.legacy.client.render.camera.WeaponMovementFovHandler
import com.tacz.legacy.client.render.camera.WeaponViewMotionHandler
import com.tacz.legacy.client.render.entity.LegacyEntityRenderRegistrar
import com.tacz.legacy.client.render.item.FirstPersonGunRenderEventHandler
import com.tacz.legacy.client.render.item.ThirdPersonShellEjectEventHandler
import com.tacz.legacy.client.render.RenderPipelineRuntime
import com.tacz.legacy.client.render.debug.RenderDebugOverlayHandler
import com.tacz.legacy.client.render.debug.WeaponConsistencyDriftSampler
import com.tacz.legacy.client.render.execution.RenderFrameExecutionHandler
import com.tacz.legacy.client.render.hud.TaczStyleHudOverlayHandler
import com.tacz.legacy.common.CommonProxy
import net.minecraftforge.client.ClientCommandHandler
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent

public class ClientProxy : CommonProxy() {

	private val debugOverlayHandler: RenderDebugOverlayHandler = RenderDebugOverlayHandler()
	private val renderFrameExecutionHandler: RenderFrameExecutionHandler = RenderFrameExecutionHandler()
	private val taczStyleHudOverlayHandler: TaczStyleHudOverlayHandler = TaczStyleHudOverlayHandler()
	private val weaponKeyInputEventHandler: WeaponKeyInputEventHandler = WeaponKeyInputEventHandler()
	private val weaponViewMotionHandler: WeaponViewMotionHandler = WeaponViewMotionHandler()
	private val weaponMovementFovHandler: WeaponMovementFovHandler = WeaponMovementFovHandler()
	private val firstPersonGunRenderEventHandler: FirstPersonGunRenderEventHandler = FirstPersonGunRenderEventHandler()
	private val thirdPersonShellEjectEventHandler: ThirdPersonShellEjectEventHandler = ThirdPersonShellEjectEventHandler()
	private val weaponConsistencyDriftSampler: WeaponConsistencyDriftSampler = WeaponConsistencyDriftSampler()

	override fun preInit(event: FMLPreInitializationEvent) {
		super.preInit(event)
		LegacyEntityRenderRegistrar.registerAll()
		GunPackExternalResourcePackManager.installOrRefresh(
			logger = TACZLegacy.logger,
			forceRefreshResources = true
		)
		RenderPipelineRuntime.initialize(TACZLegacy.logger)
		val specialModelStats = LegacySpecialBlockModelRegistry.adaptationStats()
		TACZLegacy.logger.info(
			"[SpecialBlockModel] adapters={} translucent={} blocks={}",
			specialModelStats.adapterCount,
			specialModelStats.translucentAdapterCount,
			specialModelStats.blockRegistryPaths
		)
		val specialModelValidation = LegacySpecialBlockModelRegistry.validateModelResources()
		TACZLegacy.logger.info(
			"[SpecialBlockModel] model validation: total={} valid={} missing={}",
			specialModelValidation.total,
			specialModelValidation.valid,
			specialModelValidation.missing
		)
		if (specialModelValidation.missing > 0) {
			TACZLegacy.logger.warn(
				"[SpecialBlockModel] missing model resource paths={}",
				specialModelValidation.missingModelJsonClasspathPaths
			)
		}
		WeaponKeyBindings.registerAll()
		ClientCommandHandler.instance.registerCommand(TaczDebugUiClientCommand)
		ClientCommandHandler.instance.registerCommand(TaczMovementFovClientCommand)
		ClientCommandHandler.instance.registerCommand(TaczReloadGunPackClientCommand)
		ClientCommandHandler.instance.registerCommand(TaczSpecialBlockProbeClientCommand)
		ClientCommandHandler.instance.registerCommand(TaczWeaponDiagClientCommand)
		MinecraftForge.EVENT_BUS.register(renderFrameExecutionHandler)
		MinecraftForge.EVENT_BUS.register(debugOverlayHandler)
		MinecraftForge.EVENT_BUS.register(taczStyleHudOverlayHandler)
		MinecraftForge.EVENT_BUS.register(weaponKeyInputEventHandler)
		MinecraftForge.EVENT_BUS.register(weaponViewMotionHandler)
		MinecraftForge.EVENT_BUS.register(weaponMovementFovHandler)
		MinecraftForge.EVENT_BUS.register(firstPersonGunRenderEventHandler)
		MinecraftForge.EVENT_BUS.register(thirdPersonShellEjectEventHandler)
		MinecraftForge.EVENT_BUS.register(weaponConsistencyDriftSampler)
	}

}
