package com.tacz.legacy.client

import com.tacz.legacy.TACZLegacy
import com.tacz.legacy.client.command.TaczDebugUiClientCommand
import com.tacz.legacy.client.command.TaczMovementFovClientCommand
import com.tacz.legacy.client.command.TaczReloadGunPackClientCommand
import com.tacz.legacy.client.command.TaczSpecialBlockProbeClientCommand
import com.tacz.legacy.client.command.TaczWeaponDiagClientCommand
import com.tacz.legacy.client.gui.WeaponDebugWorkbenchGuiScreen
import com.tacz.legacy.client.gui.WeaponGunsmithImmersiveRuntime
import com.tacz.legacy.client.gui.WeaponImmersiveWorkbenchGuiScreen
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
import com.tacz.legacy.common.infrastructure.mc.registry.item.LegacyGunItem
import net.minecraft.client.Minecraft
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.util.text.TextComponentString
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

	override fun openWeaponWorkbenchScreen(player: EntityPlayer): Boolean {
		return openWorkbenchGui(player, expectedGunId = null, showReadyMessage = true)
	}

	override fun handleWeaponWorkbenchOpenPacket(gunId: String, blockX: Int, blockY: Int, blockZ: Int): Boolean {
		val mc = Minecraft.getMinecraft()
		val player = mc.player ?: return false
		return openWorkbenchGui(player, expectedGunId = gunId, showReadyMessage = true)
	}

	override fun handleWeaponWorkbenchRefreshPacket() {
		val mc = Minecraft.getMinecraft()
		val current = mc.currentScreen as? WeaponImmersiveWorkbenchGuiScreen ?: return
		current.initGui()
	}

	override fun handleWeaponWorkbenchClosePacket(reasonMessage: String) {
		val mc = Minecraft.getMinecraft()
		val current = mc.currentScreen
		if (current !is WeaponImmersiveWorkbenchGuiScreen && current !is WeaponDebugWorkbenchGuiScreen) {
			return
		}

		WeaponGunsmithImmersiveRuntime.deactivate()
		mc.displayGuiScreen(null)

		if (reasonMessage.isNotBlank()) {
			mc.player?.sendStatusMessage(TextComponentString("[TACZ] $reasonMessage"), true)
		}
	}

	private fun openWorkbenchGui(player: EntityPlayer, expectedGunId: String?, showReadyMessage: Boolean): Boolean {
		val mc = Minecraft.getMinecraft()
		if (mc.player !== player) {
			return false
		}

		val heldGunId = resolveHeldLegacyGunId(player)
		if (heldGunId.isNullOrBlank()) {
			player.sendStatusMessage(TextComponentString("[TACZ] 请先手持枪械后再使用改枪台"), true)
			return false
		}

		val normalizedExpected = expectedGunId?.trim()?.lowercase()?.ifBlank { null }
		if (normalizedExpected != null && normalizedExpected != heldGunId) {
			player.sendStatusMessage(TextComponentString("[TACZ] 当前手持枪械与服务端会话不一致，请重新交互工作台"), true)
			return false
		}

		WeaponGunsmithImmersiveRuntime.activate(normalizedExpected ?: heldGunId)
		mc.displayGuiScreen(WeaponImmersiveWorkbenchGuiScreen())
		if (showReadyMessage) {
			player.sendStatusMessage(TextComponentString("[TACZ] 已进入沉浸式改枪模式（实验阶段）"), true)
		}
		return true
	}

	private fun resolveHeldLegacyGunId(player: EntityPlayer): String? {
		val main = player.heldItemMainhand
		if (!main.isEmpty && main.item is LegacyGunItem) {
			return main.item.registryName?.path?.trim()?.lowercase()?.ifBlank { null }
		}

		val off = player.heldItemOffhand
		if (!off.isEmpty && off.item is LegacyGunItem) {
			return off.item.registryName?.path?.trim()?.lowercase()?.ifBlank { null }
		}

		return null
	}

}
