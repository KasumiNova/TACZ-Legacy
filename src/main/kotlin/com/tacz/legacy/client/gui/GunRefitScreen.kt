package com.tacz.legacy.client.gui

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.tacz.legacy.TACZLegacy
import com.tacz.legacy.api.DefaultAssets
import com.tacz.legacy.api.entity.IGunOperator
import com.tacz.legacy.api.item.IAttachment
import com.tacz.legacy.api.item.IGun
import com.tacz.legacy.api.item.attachment.AttachmentType
import com.tacz.legacy.api.item.gun.FireMode
import com.tacz.legacy.api.modifier.Modifier
import com.tacz.legacy.client.foundation.TACZAsciiFontHelper
import com.tacz.legacy.client.animation.screen.RefitTransform
import com.tacz.legacy.client.input.LegacyKeyBindings
import com.tacz.legacy.common.application.refit.LegacyGunRefitRuntime
import com.tacz.legacy.common.application.refit.LegacyRefitInventorySlot
import com.tacz.legacy.common.network.TACZNetworkHandler
import com.tacz.legacy.common.network.message.client.ClientMessageLaserColor
import com.tacz.legacy.common.network.message.client.ClientMessagePlayerFireSelect
import com.tacz.legacy.common.network.message.client.ClientMessageRefitGun
import com.tacz.legacy.common.network.message.client.ClientMessageRefitGunCreative
import com.tacz.legacy.common.network.message.client.ClientMessageUnloadAttachment
import com.tacz.legacy.common.resource.TACZAttachmentLaserConfigDefinition
import com.tacz.legacy.common.resource.TACZAttachmentModifierRegistry
import com.tacz.legacy.common.resource.TACZGunPackPresentation
import com.tacz.legacy.common.resource.TACZGunPackRuntimeRegistry
import com.tacz.legacy.common.resource.TACZRecoilModifierValue
import com.tacz.legacy.common.resource.GunDataAccessor
import net.minecraft.client.Minecraft
import net.minecraft.client.entity.EntityPlayerSP
import net.minecraft.client.gui.Gui
import net.minecraft.client.gui.GuiButton
import net.minecraft.client.gui.GuiScreen
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.RenderHelper
import net.minecraft.client.resources.I18n
import net.minecraft.client.settings.KeyBinding
import net.minecraft.client.util.ITooltipFlag
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation
import net.minecraft.util.text.TextComponentTranslation
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly
import org.lwjgl.input.Keyboard
import org.lwjgl.input.Mouse
import java.awt.Color
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

@SideOnly(Side.CLIENT)
internal class GunRefitScreen : GuiScreen() {
	private var currentPage: Int = 0
	private var selectedType: AttachmentType = AttachmentType.NONE
	private var gunOverviewSelected: Boolean = true

	private val slotButtons: MutableList<AttachmentSlotButton> = mutableListOf()
	private val inventoryButtons: MutableList<InventoryAttachmentButton> = mutableListOf()
	private var unloadButton: UnloadButton? = null
	private var pageUpButton: TurnPageButton? = null
	private var pageDownButton: TurnPageButton? = null
	private var hueSlider: ColorSliderButton? = null
	private var saturationSlider: ColorSliderButton? = null
	private var lockedYaw: Float? = null
	private var lockedPitch: Float? = null
	private var originalThirdPersonView: Int? = null

	init {
		RefitTransform.init()
	}

	override fun initGui() {
		super.initGui()
		buttonList.clear()
		slotButtons.clear()
		inventoryButtons.clear()
		unloadButton = null
		pageUpButton = null
		pageDownButton = null
		hueSlider = null
		saturationSlider = null

		if (!canStayOpen()) {
			restoreViewFocusLock()
			mc.displayGuiScreen(null)
			return
		}

		captureAndApplyViewFocusLock()
		syncSelectionFromTransform()
		normalizeSelection()
		addTopButtons()
		addSlotButtons()
		addInventoryButtons()
		addLaserControls()
	}

	override fun doesGuiPauseGame(): Boolean = false

	override fun updateScreen() {
		super.updateScreen()
		if (!canStayOpen()) {
			restoreViewFocusLock()
			mc.displayGuiScreen(null)
			return
		}
		enforceViewAndInputFocusLock()
	}

	override fun handleMouseInput() {
		super.handleMouseInput()
		val wheel = Mouse.getEventDWheel()
		if (wheel == 0) {
			return
		}

		val mouseX = Mouse.getEventX() * width / mc.displayWidth
		val mouseY = height - Mouse.getEventY() * height / mc.displayHeight - 1
		val delta = if (wheel > 0) -1 else 1
		if (isMouseInAttachmentPanel(mouseX, mouseY)) {
			currentPage = (currentPage + delta).coerceIn(0, inventoryMaxPage())
			initGui()
			return
		}
		if (isMouseInSlotBar(mouseX, mouseY)) {
			cycleSlot(delta)
		}
	}

	override fun keyTyped(typedChar: Char, keyCode: Int) {
		when (keyCode) {
			Keyboard.KEY_ESCAPE, LegacyKeyBindings.REFIT.keyCode -> {
				mc.displayGuiScreen(null)
				return
			}

			Keyboard.KEY_TAB -> {
				HIDE_GUN_PROPERTY_PANEL = !HIDE_GUN_PROPERTY_PANEL
				initGui()
				return
			}
		}
		super.keyTyped(typedChar, keyCode)
	}

	override fun onGuiClosed() {
		val player = currentPlayer()
		val gunStack = currentGunStack()
		if (player != null && !gunStack.isEmpty && gunStack.item is IGun) {
			TACZNetworkHandler.sendToServer(ClientMessageLaserColor(gunStack, player.inventory.currentItem))
		}
		restoreViewFocusLock()
		super.onGuiClosed()
	}

	override fun actionPerformed(button: GuiButton) {
		when (button.id) {
			BUTTON_TOGGLE_PROPERTIES -> {
				HIDE_GUN_PROPERTY_PANEL = !HIDE_GUN_PROPERTY_PANEL
				initGui()
			}

			BUTTON_FIRE_SELECT -> {
				val player = currentPlayer() ?: return
				val gunStack = currentGunStack()
				val iGun = gunStack.item as? IGun ?: return
				val before = iGun.getFireMode(gunStack)
				IGunOperator.fromLivingEntity(player).fireSelect()
				if (before != iGun.getFireMode(gunStack)) {
					TACZNetworkHandler.sendToServer(ClientMessagePlayerFireSelect())
				}
				initGui()
			}
			BUTTON_PAGE_UP -> {
				currentPage = (currentPage - 1).coerceAtLeast(0)
				initGui()
			}

			BUTTON_PAGE_DOWN -> {
				currentPage = (currentPage + 1).coerceAtMost(inventoryMaxPage())
				initGui()
			}

			BUTTON_UNLOAD -> tryUnloadSelectedAttachment()
			else -> {
				slotButtons.firstOrNull { it.id == button.id }?.let { slotButton ->
					if (!slotButton.isAllowed()) {
						changeSelectedType(AttachmentType.NONE)
					} else if (selectedType == slotButton.type) {
						changeSelectedType(AttachmentType.NONE)
					} else {
						changeSelectedType(slotButton.type)
					}
					return
				}
				inventoryButtons.firstOrNull { it.id == button.id }?.let { inventoryButton ->
					val player = currentPlayer() ?: return
					val slotIndex = inventoryButton.candidate.inventorySlotIndex
					if (slotIndex != null) {
						TACZNetworkHandler.sendToServer(
							ClientMessageRefitGun(
								slotIndex,
								player.inventory.currentItem,
								selectedType,
							),
						)
						return
					}
					val attachmentId = (inventoryButton.candidate.stack.item as? IAttachment)?.getAttachmentId(inventoryButton.candidate.stack) ?: return
					TACZNetworkHandler.sendToServer(
						ClientMessageRefitGunCreative(
							attachmentId,
							player.inventory.currentItem,
							selectedType,
						),
					)
				}
			}
		}
	}

	override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
		val gunStack = currentGunStack()

		if (gunStack.isEmpty) {
			TACZAsciiFontHelper.drawCenteredStringWithShadow(fontRenderer, I18n.format("gui.tacz.gun_smith_table.refit.unavailable"), width / 2, 60, 0xFF6666)
			super.drawScreen(mouseX, mouseY, partialTicks)
			return
		}

		if (!HIDE_GUN_PROPERTY_PANEL) {
			drawStatsPanel(mouseX, mouseY)
		}

		super.drawScreen(mouseX, mouseY, partialTicks)
		drawColorPreview()
		renderHoveredTooltips(mouseX, mouseY)
	}

	fun refreshFromServer() {
		syncSelectionFromTransform()
		initGui()
	}

	fun triggerFocusedSmokeSelectType(): AttachmentType? {
		val player = currentPlayer() ?: return null
		val allowed = allowedAttachmentTypes()
		if (allowed.isEmpty()) {
			return null
		}
		val gunStack = currentGunStack()
		val preferred = focusedSmokePreferredAttachmentType()?.takeIf { type ->
			type != AttachmentType.NONE && allowed.contains(type) && (
				!LegacyGunRefitRuntime.displayedAttachment(gunStack, type).isEmpty || compatibleCandidates(player, type).isNotEmpty()
			)
		}
		val currentValid = selectedType.takeIf { type ->
			type != AttachmentType.NONE && (
				!LegacyGunRefitRuntime.displayedAttachment(gunStack, type).isEmpty || compatibleCandidates(player, type).isNotEmpty()
				)
		}
		val chosen = preferred
			?: currentValid
			?: allowed.firstOrNull { type ->
				!LegacyGunRefitRuntime.displayedAttachment(gunStack, type).isEmpty || compatibleCandidates(player, type).isNotEmpty()
			}
			?: allowed.firstOrNull()
			?: return null
		if (!changeSelectedType(chosen)) {
			return null
		}
		return chosen
	}

	fun triggerFocusedSmokeToggleProperties(): Boolean {
		val toggleButton = buttonList.firstOrNull { it.id == BUTTON_TOGGLE_PROPERTIES } ?: return HIDE_GUN_PROPERTY_PANEL
		actionPerformed(toggleButton)
		return HIDE_GUN_PROPERTY_PANEL
	}

	fun triggerFocusedSmokeInstallFirstCandidate(): ResourceLocation? {
		val button = inventoryButtons.firstOrNull() ?: return null
		val attachmentId = (button.candidate.stack.item as? IAttachment)?.getAttachmentId(button.candidate.stack) ?: return null
		actionPerformed(button)
		return attachmentId
	}

	fun focusedSmokeSelectedAttachmentId(): ResourceLocation? {
		if (selectedType == AttachmentType.NONE) {
			return null
		}
		return currentIGun()?.getAttachmentId(currentGunStack(), selectedType)
	}

	private fun canStayOpen(): Boolean {
		val player = currentPlayer() ?: return false
		return !player.isSpectator && LegacyGunRefitRuntime.canOpenRefit(player.heldItemMainhand)
	}

	private fun currentPlayer(): EntityPlayerSP? = mc.player

	private fun currentGunStack(): ItemStack = currentPlayer()?.heldItemMainhand ?: ItemStack.EMPTY

	private fun currentIGun(): IGun? = IGun.getIGunOrNull(currentGunStack())

	private fun focusedSmokePreferredAttachmentType(): AttachmentType? {
		val raw = System.getProperty("tacz.focusedSmoke.refitType") ?: return null
		return AttachmentType.values().firstOrNull { type ->
			type.serializedName.equals(raw, ignoreCase = true) || type.name.equals(raw, ignoreCase = true)
		}
	}

	private fun syncSelectionFromTransform() {
		val transformType = RefitTransform.getCurrentTransformType()
		if (transformType == AttachmentType.NONE) {
			selectedType = AttachmentType.NONE
			gunOverviewSelected = true
		} else {
			selectedType = transformType
			gunOverviewSelected = false
		}
	}

	private fun normalizeSelection() {
		val allowedTypes = allowedAttachmentTypes()
		if (allowedTypes.isEmpty()) {
			selectedType = AttachmentType.NONE
			gunOverviewSelected = true
			currentPage = 0
			return
		}
		if (selectedType != AttachmentType.NONE && selectedType !in allowedTypes) {
			selectedType = AttachmentType.NONE
			gunOverviewSelected = true
		}
		if (selectedType == AttachmentType.NONE) {
			gunOverviewSelected = true
		}
		currentPage = currentPage.coerceIn(0, inventoryMaxPage())
	}

	private fun allowedAttachmentTypes(): List<AttachmentType> {
		val gunStack = currentGunStack()
		val iGun = currentIGun() ?: return emptyList()
		return SLOT_DISPLAY_ORDER.filter { type -> iGun.allowAttachmentType(gunStack, type) }
	}

	private fun cycleSlot(delta: Int) {
		val allowed = allowedAttachmentTypes()
		if (allowed.isEmpty()) {
			return
		}
		val currentIndex = allowed.indexOf(selectedType)
		val nextIndex = when {
			currentIndex < 0 -> if (delta > 0) 0 else allowed.lastIndex
			else -> floorMod(currentIndex + delta, allowed.size)
		}
		changeSelectedType(allowed[nextIndex])
	}

	private fun changeSelectedType(type: AttachmentType): Boolean {
		val targetType = if (type in allowedAttachmentTypes() || type == AttachmentType.NONE) type else AttachmentType.NONE
		if (selectedType == targetType && RefitTransform.getCurrentTransformType() == targetType) {
			gunOverviewSelected = targetType == AttachmentType.NONE
			currentPage = 0
			initGui()
			return true
		}
		if (!RefitTransform.changeRefitScreenView(targetType)) {
			return false
		}
		selectedType = targetType
		gunOverviewSelected = targetType == AttachmentType.NONE
		currentPage = 0
		initGui()
		return true
	}

	private fun addTopButtons() {
		if (HIDE_GUN_PROPERTY_PANEL) {
			addButton(
				RefitFlatButton(
					BUTTON_TOGGLE_PROPERTIES,
					11,
					11,
					288,
					16,
					I18n.format("gui.tacz.gun_refit.property_diagrams.show"),
				),
			)
		} else {
			addButton(
				RefitFlatButton(
					BUTTON_FIRE_SELECT,
					14,
					14,
					12,
					12,
					"S",
					listOf(I18n.format("gui.tacz.gun_refit.property_diagrams.fire_mode.switch")),
				),
			)
			addButton(
				RefitFlatButton(
					BUTTON_TOGGLE_PROPERTIES,
					11,
					hidePropertiesButtonY(),
					288,
					12,
					I18n.format("gui.tacz.gun_refit.property_diagrams.hide"),
				),
			)
		}
	}

	private fun addSlotButtons() {
		var x = width - SLOT_BAR_RIGHT_MARGIN - SLOT_SIZE
		SLOT_DISPLAY_ORDER.forEach { type ->
			val button = AttachmentSlotButton(BUTTON_SLOT_BASE + type.ordinal, x, SLOT_BAR_Y, type)
			slotButtons += button
			addButton(button)
			x -= SLOT_SIZE
		}
	}

	private fun addInventoryButtons() {
		if (selectedType == AttachmentType.NONE) {
			return
		}
		val player = currentPlayer() ?: return
		val allCandidates = compatibleCandidates(player)
		val pageStart = currentPage * CANDIDATES_PER_PAGE
		val pageItems = allCandidates.drop(pageStart).take(CANDIDATES_PER_PAGE)
		val startX = attachmentColumnLeft()
		val startY = attachmentColumnTop()

		if (currentPage > 0) {
			pageUpButton = addButton(
				TurnPageButton(
					BUTTON_PAGE_UP,
					startX,
					startY - 10,
					true,
					listOf(I18n.format("tooltip.tacz.page.previous")),
				),
			)
		}
		if (currentPage < inventoryMaxPage()) {
			pageDownButton = addButton(
				TurnPageButton(
					BUTTON_PAGE_DOWN,
					startX,
					startY + SLOT_SIZE * CANDIDATES_PER_PAGE + 2,
					false,
					listOf(I18n.format("tooltip.tacz.page.next")),
				),
			)
		}

		val iGun = currentIGun() ?: return
		val installed = iGun.getAttachment(currentGunStack(), selectedType)
		if (!installed.isEmpty) {
			slotButtons.firstOrNull { it.type == selectedType }?.let { slotButton ->
				unloadButton = addButton(UnloadButton(BUTTON_UNLOAD, slotButton.x + 5, slotButton.y + SLOT_SIZE + 2))
			}
		}

		pageItems.forEachIndexed { index, candidate ->
			val button = InventoryAttachmentButton(
				BUTTON_INVENTORY_BASE + index,
				startX,
				startY + index * SLOT_SIZE,
				candidate,
			)
			inventoryButtons += button
			addButton(button)
		}
	}

	private fun addLaserControls() {
		val currentColor = currentEditableLaserColor() ?: return
		val hsb = Color.RGBtoHSB(
			(currentColor shr 16) and 0xFF,
			(currentColor shr 8) and 0xFF,
			currentColor and 0xFF,
			null,
		)
		hueSlider = addButton(
			ColorSliderButton(BUTTON_HUE_SLIDER, width - 140, height - 64, 120, hsb[0].toDouble()) {
				applyLaserPreview()
			},
		)
		saturationSlider = addButton(
			ColorSliderButton(BUTTON_SATURATION_SLIDER, width - 140, height - 48, 120, hsb[1].toDouble()) {
				applyLaserPreview()
			},
		)
	}

	private fun drawTaczWorkbenchBackdrop() {
		val originX = ((width - TACZ_WORKBENCH_TOTAL_WIDTH) / 2).coerceAtLeast(UI_MARGIN)
		val originY = ((height - TACZ_WORKBENCH_TOTAL_HEIGHT) / 2).coerceAtLeast(10)
		drawTextureRegion(
			texture = TACZ_WORKBENCH_SIDE_TEXTURE,
			x = originX,
			y = originY,
			width = TACZ_WORKBENCH_SIDE_WIDTH,
			height = TACZ_WORKBENCH_TOTAL_HEIGHT,
			textureWidth = TACZ_WORKBENCH_SIDE_WIDTH.toFloat(),
			textureHeight = TACZ_WORKBENCH_TOTAL_HEIGHT.toFloat(),
			alpha = 0.30f,
		)
		drawTextureRegion(
			texture = TACZ_WORKBENCH_MAIN_TEXTURE,
			x = originX + TACZ_WORKBENCH_MAIN_OFFSET_X,
			y = originY + TACZ_WORKBENCH_MAIN_OFFSET_Y,
			width = TACZ_WORKBENCH_MAIN_WIDTH,
			height = TACZ_WORKBENCH_MAIN_HEIGHT,
			textureWidth = TACZ_WORKBENCH_MAIN_WIDTH.toFloat(),
			textureHeight = TACZ_WORKBENCH_MAIN_HEIGHT.toFloat(),
			alpha = 0.26f,
		)
	}

	private fun drawWorkbenchInfo() {
		val gunStack = currentGunStack()
		val fireModeText = I18n.format("gui.tacz.gun_refit.property_diagrams.fire_mode") + fireModeText(currentIGun()?.getFireMode(gunStack) ?: FireMode.UNKNOWN)
		val currentAttachment = if (selectedType == AttachmentType.NONE) ItemStack.EMPTY else LegacyGunRefitRuntime.displayedAttachment(gunStack, selectedType)
		val currentAttachmentName = when {
			selectedType == AttachmentType.NONE -> I18n.format("tooltip.tacz.attachment.none")
			currentAttachment.isEmpty -> I18n.format("tooltip.tacz.attachment.none")
			else -> currentAttachment.displayName
		}
		val slotLabel = if (selectedType == AttachmentType.NONE) I18n.format("tooltip.tacz.attachment.none") else attachmentTypeLabel(selectedType)
		TACZAsciiFontHelper.drawStringWithShadow(fontRenderer, fireModeText, 24f, 50f, 0xEAEAEA)
		TACZAsciiFontHelper.drawStringWithShadow(fontRenderer, "$slotLabel: $currentAttachmentName", 24f, 62f, 0xD7E6FF)
	}

	private fun drawAttachmentPanelBackdrop() {
		val left = attachmentPanelLeft()
		val right = attachmentPanelRight()
		val top = attachmentPanelTop()
		val bottom = attachmentPanelBottom()
		drawPanel(left, top, right, bottom)

		val header = if (selectedType == AttachmentType.NONE) I18n.format("key.tacz.refit.desc") else attachmentTypeLabel(selectedType)
		val pageText = if (selectedType == AttachmentType.NONE) "" else "${currentPage + 1}/${inventoryMaxPage() + 1}"
		TACZAsciiFontHelper.drawStringWithShadow(fontRenderer, header, (left + 8).toFloat(), (top + 8).toFloat(), 0xE9E9E9)
		if (pageText.isNotBlank()) {
			TACZAsciiFontHelper.drawStringWithShadow(fontRenderer, pageText, (right - 64).toFloat(), (top + 8).toFloat(), 0xC7D3EA)
		}

		if (selectedType != AttachmentType.NONE) {
			val attachment = LegacyGunRefitRuntime.displayedAttachment(currentGunStack(), selectedType)
			if (!attachment.isEmpty) {
				RenderHelper.enableGUIStandardItemLighting()
				itemRender.renderItemAndEffectIntoGUI(attachment, left + 8, top + 24)
				TACZAsciiFontHelper.renderItemOverlayIntoGUI(itemRender, fontRenderer, attachment, left + 8, top + 24, null)
				RenderHelper.disableStandardItemLighting()
			}
			val currentAttachmentName = if (attachment.isEmpty) I18n.format("tooltip.tacz.attachment.none") else attachment.displayName
			TACZAsciiFontHelper.drawStringWithShadow(
				fontRenderer,
				TACZAsciiFontHelper.trimStringToWidth(fontRenderer, currentAttachmentName, ATTACHMENT_PANEL_WIDTH - 36),
				(left + 30).toFloat(),
				(top + 29).toFloat(),
				0xD7E6FF,
			)
		}

		if (selectedType != AttachmentType.NONE && inventoryButtons.isEmpty()) {
			TACZAsciiFontHelper.drawCenteredStringWithShadow(fontRenderer, I18n.format("tooltip.tacz.attachment.none"), left + ATTACHMENT_PANEL_WIDTH / 2, top + 82, 0xFF8888)
		}
	}

	private fun drawStatsPanel(mouseX: Int, mouseY: Int) {
		val currentStats = computeStats(currentGunStack()) ?: return
		val previewStats = computeStats(hoveredPreviewGunStack(mouseX, mouseY)) ?: currentStats
		val panelLeft = 11
		val panelTop = 11
		val panelWidth = 288
		val nameTextX = panelLeft + 5
		val barX = panelLeft + 83
		val barWidth = 120
		val valueX = panelLeft + 210

		val rows = buildStatRows(currentStats, previewStats)
		val panelHeight = propertyDiagramHeight(rows.size)
		drawRect(panelLeft, panelTop, panelLeft + panelWidth, panelTop + panelHeight, 0xAF222222.toInt())
		val fireModeLine = I18n.format("gui.tacz.gun_refit.property_diagrams.fire_mode") + fireModeText(currentStats.fireMode)
		TACZAsciiFontHelper.drawStringWithShadow(fontRenderer, fireModeLine, (nameTextX + 12).toFloat(), (panelTop + 5).toFloat(), 0xCCCCCC)
		rows.forEachIndexed { index, row ->
			drawStatRow(row, nameTextX, panelTop + 15 + index * 10, barX, barWidth, valueX)
		}
	}

	private fun buildStatRows(current: ComputedStats, preview: ComputedStats): List<StatRow> {
		return listOf(
			statRow("gui.tacz.gun_refit.property_diagrams.ammo_capacity", current.ammoCapacity, preview.ammoCapacity, false, "", 100f),
			statRow("gui.tacz.gun_refit.property_diagrams.sprint_time", current.sprintTime, preview.sprintTime, true, "s", 1.0f),
			statRow("gui.tacz.gun_refit.property_diagrams.ads", current.adsTime, preview.adsTime, true, "s", 1.0f),
			statRow("gui.tacz.gun_refit.property_diagrams.rpm", current.rpm, preview.rpm, false, "", 1400f),
			statRow("gui.tacz.gun_refit.property_diagrams.damage", current.damage, preview.damage, false, "", 30f),
			statRow("gui.tacz.gun_refit.property_diagrams.ammo_speed", current.ammoSpeed, preview.ammoSpeed, false, "m/s", 1000f),
			statRow("gui.tacz.gun_refit.property_diagrams.armor_ignore", current.armorIgnorePercent, preview.armorIgnorePercent, false, "%", 100f),
			statRow("gui.tacz.gun_refit.property_diagrams.head_shot", current.headShotMultiplier, preview.headShotMultiplier, false, "x", 4f),
			statRow("gui.tacz.gun_refit.property_diagrams.hipfire_inaccuracy", current.hipfireInaccuracy, preview.hipfireInaccuracy, true, "", 10f),
			statRow("gui.tacz.gun_refit.property_diagrams.aim_inaccuracy", current.aimInaccuracy, preview.aimInaccuracy, true, "", 4f),
			statRow("gui.tacz.gun_refit.property_diagrams.pitch", current.recoilPitch, preview.recoilPitch, true, "", 2f),
			statRow("gui.tacz.gun_refit.property_diagrams.yaw", current.recoilYaw, preview.recoilYaw, true, "", 2f),
			statRow("gui.tacz.gun_refit.property_diagrams.weight", current.weight, preview.weight, true, "", 12f),
		)
	}

	private fun statRow(labelKey: String, base: Float, current: Float, lowerIsBetter: Boolean, unit: String, floorMax: Float): StatRow {
		return StatRow(
			label = I18n.format(labelKey),
			base = base,
			current = current,
			maxValue = max(floorMax, max(base, current).coerceAtLeast(0.001f)),
			lowerIsBetter = lowerIsBetter,
			unit = unit,
		)
	}

	private fun drawStatRow(row: StatRow, x: Int, y: Int, barX: Int, barWidth: Int, valueX: Int) {
		TACZAsciiFontHelper.drawStringWithShadow(fontRenderer, row.label, x.toFloat(), y.toFloat(), 0xCCCCCC)
		val barY = y + 2
		val baseRatio = normalizedRatio(row.base, row.maxValue, row.lowerIsBetter)
		val currentRatio = normalizedRatio(row.current, row.maxValue, row.lowerIsBetter)
		val baseFill = (barWidth * baseRatio).toInt().coerceIn(0, barWidth)
		val currentFill = (barWidth * currentRatio).toInt().coerceIn(0, barWidth)

		drawRect(barX, barY, barX + barWidth, barY + 4, 0xFF000000.toInt())
		drawRect(barX, barY, barX + baseFill, barY + 4, 0xFFFFFFFF.toInt())
		if (currentFill != baseFill) {
			val start = minOf(baseFill, currentFill)
			val end = maxOf(baseFill, currentFill)
			val improved = currentFill > baseFill
			val deltaColor = if (improved) 0xFF55FF55.toInt() else 0xFFFF5555.toInt()
			drawRect(barX + start, barY, barX + end, barY + 4, deltaColor)
		}

		val delta = row.current - row.base
		val valueText = when {
			abs(delta) < 0.0001f -> "${formatNumber(row.current)}${row.unit}"
			else -> {
				val sign = if (delta > 0f) "+" else ""
				val colorCode = if (isPositiveDelta(row.lowerIsBetter, delta)) "§a" else "§c"
				"${formatNumber(row.current)}${row.unit} ${colorCode}(${sign}${formatNumber(delta)}${row.unit})"
			}
		}
		TACZAsciiFontHelper.drawStringWithShadow(fontRenderer, valueText, valueX.toFloat(), y.toFloat(), 0xCCCCCC)
	}

	private fun drawColorPreview() {
		val currentColor = currentEditableLaserColor() ?: return
		val left = width - 140
		val top = height - 66
		TACZAsciiFontHelper.drawStringWithShadow(fontRenderer, "H", (left - 12).toFloat(), (top + 4).toFloat(), 0xF3EFE0)
		TACZAsciiFontHelper.drawStringWithShadow(fontRenderer, "S", (left - 12).toFloat(), (top + 20).toFloat(), 0xF3EFE0)
		drawRect(left + 124, top + 2, left + 140, top + 18, 0xFF000000.toInt() or (currentColor and 0xFFFFFF))
	}

	private fun renderHoveredTooltips(mouseX: Int, mouseY: Int) {
		buttonList.asSequence()
			.filterIsInstance<RefitTooltipButton>()
			.firstOrNull { it.contains(mouseX, mouseY) && it.tooltipLines.isNotEmpty() }
			?.let {
				TACZAsciiFontHelper.runWithTemporaryUnicodeFlagDisabled(fontRenderer, it.tooltipLines, Runnable { drawHoveringText(it.tooltipLines, mouseX, mouseY) })
				return
			}

		slotButtons.firstOrNull { it.contains(mouseX, mouseY) }?.let { button ->
			val stack = button.displayedAttachment()
			if (!stack.isEmpty) {
				val tooltipFlag = if (mc.gameSettings.advancedItemTooltips) ITooltipFlag.TooltipFlags.ADVANCED else ITooltipFlag.TooltipFlags.NORMAL
				val tooltipLines = stack.getTooltip(mc.player, tooltipFlag)
				TACZAsciiFontHelper.runWithTemporaryUnicodeFlagDisabled(fontRenderer, tooltipLines, Runnable { renderToolTip(stack, mouseX, mouseY) })
			} else {
				val lines = listOf(attachmentTypeLabel(button.type))
				TACZAsciiFontHelper.runWithTemporaryUnicodeFlagDisabled(fontRenderer, lines, Runnable { drawHoveringText(lines, mouseX, mouseY) })
			}
			return
		}

		inventoryButtons.firstOrNull { it.contains(mouseX, mouseY) }?.let { button ->
			val lines = buildCandidateTooltip(button.candidate)
			TACZAsciiFontHelper.runWithTemporaryUnicodeFlagDisabled(fontRenderer, lines, Runnable { drawHoveringText(lines, mouseX, mouseY) })
			return
		}
	}

	private fun buildCandidateTooltip(candidate: RefitCandidate): List<String> {
		val player = currentPlayer()
		val lines = candidate.stack.getTooltip(player, ITooltipFlag.TooltipFlags.NORMAL).toMutableList()
		val currentStats = computeStats(currentGunStack()) ?: return lines
		val previewStats = computeStats(buildPreviewGunStack(candidate.stack)) ?: return lines
		appendDeltaLine(lines, I18n.format("gui.tacz.gun_refit.property_diagrams.ammo_capacity"), currentStats.ammoCapacity, previewStats.ammoCapacity, "")
		appendDeltaLine(lines, I18n.format("gui.tacz.gun_refit.property_diagrams.damage"), currentStats.damage, previewStats.damage, "")
		appendDeltaLine(lines, I18n.format("gui.tacz.gun_refit.property_diagrams.ammo_speed"), currentStats.ammoSpeed, previewStats.ammoSpeed, "m/s")
		appendDeltaLine(lines, I18n.format("gui.tacz.gun_refit.property_diagrams.armor_ignore"), currentStats.armorIgnorePercent, previewStats.armorIgnorePercent, "%")
		appendDeltaLine(lines, I18n.format("gui.tacz.gun_refit.property_diagrams.head_shot"), currentStats.headShotMultiplier, previewStats.headShotMultiplier, "x")
		appendDeltaLine(lines, I18n.format("gui.tacz.gun_refit.property_diagrams.hipfire_inaccuracy"), currentStats.hipfireInaccuracy, previewStats.hipfireInaccuracy, "", lowerIsBetter = true)
		appendDeltaLine(lines, I18n.format("gui.tacz.gun_refit.property_diagrams.aim_inaccuracy"), currentStats.aimInaccuracy, previewStats.aimInaccuracy, "", lowerIsBetter = true)
		appendDeltaLine(lines, I18n.format("gui.tacz.gun_refit.property_diagrams.pitch"), currentStats.recoilPitch, previewStats.recoilPitch, "", lowerIsBetter = true)
		appendDeltaLine(lines, I18n.format("gui.tacz.gun_refit.property_diagrams.yaw"), currentStats.recoilYaw, previewStats.recoilYaw, "", lowerIsBetter = true)
		appendDeltaLine(lines, I18n.format("gui.tacz.gun_refit.property_diagrams.weight"), currentStats.weight, previewStats.weight, "", lowerIsBetter = true)
		return lines
	}

	private fun appendDeltaLine(lines: MutableList<String>, label: String, before: Float, after: Float, unit: String, lowerIsBetter: Boolean = false) {
		val delta = after - before
		if (abs(delta) < 0.0001f) {
			return
		}
		val positive = isPositiveDelta(lowerIsBetter, delta)
		val sign = if (delta > 0f) "+" else ""
		val color = if (positive) "§a" else "§c"
		lines += "$color$label $sign${formatNumber(delta)}$unit"
	}

	private fun hoveredPreviewGunStack(mouseX: Int, mouseY: Int): ItemStack {
		val hoveredAttachment = inventoryButtons.firstOrNull { it.contains(mouseX, mouseY) }?.candidate?.stack
		return hoveredAttachment?.let(::buildPreviewGunStack) ?: currentGunStack()
	}

	private fun buildPreviewGunStack(attachmentStack: ItemStack): ItemStack {
		val gunStack = currentGunStack()
		val iGun = currentIGun() ?: return gunStack
		if (selectedType == AttachmentType.NONE || !iGun.allowAttachment(gunStack, attachmentStack)) {
			return gunStack
		}
		val preview = gunStack.copy()
		iGun.installAttachment(preview, attachmentStack.copy())
		return preview
	}

	private fun compatibleCandidates(player: EntityPlayerSP, type: AttachmentType = selectedType): List<RefitCandidate> {
		if (type == AttachmentType.NONE) {
			return emptyList()
		}
		if (player.capabilities.isCreativeMode) {
			return LegacyGunRefitRuntime.compatibleCreativeAttachments(currentGunStack(), type)
				.map { stack -> RefitCandidate(stack = stack) }
		}
		val slots = (0 until player.inventory.sizeInventory)
			.asSequence()
			.filter { slotIndex -> slotIndex != player.inventory.currentItem }
			.map { slotIndex -> LegacyRefitInventorySlot(slotIndex, player.inventory.getStackInSlot(slotIndex)) }
			.toList()
		return LegacyGunRefitRuntime.compatibleInventorySlots(currentGunStack(), type, slots)
			.map { slot -> RefitCandidate(stack = slot.stack, inventorySlotIndex = slot.slotIndex) }
	}

	private fun inventoryMaxPage(): Int {
		val player = currentPlayer() ?: return 0
		val total = compatibleCandidates(player).size
		return ((total - 1).coerceAtLeast(0)) / CANDIDATES_PER_PAGE
	}

	private fun tryUnloadSelectedAttachment() {
		val player = currentPlayer() ?: return
		val gunStack = currentGunStack()
		val iGun = currentIGun() ?: return
		val attachment = iGun.getAttachment(gunStack, selectedType)
		if (attachment.isEmpty) {
			return
		}
		if (player.inventory.getFirstEmptyStack() == -1) {
			player.sendMessage(TextComponentTranslation("gui.tacz.gun_refit.unload.no_space"))
			return
		}
		TACZNetworkHandler.sendToServer(ClientMessageUnloadAttachment(player.inventory.currentItem, selectedType))
	}

	private fun currentLaserConfig(): TACZAttachmentLaserConfigDefinition? {
		val gunStack = currentGunStack()
		val iGun = currentIGun() ?: return null
		val snapshot = TACZGunPackRuntimeRegistry.getSnapshot()
		if (selectedType == AttachmentType.NONE) {
			return TACZGunPackPresentation.resolveGunLaserConfig(snapshot, iGun.getGunId(gunStack))
		}
		val attachment = iGun.getAttachment(gunStack, selectedType)
		val iAttachment = IAttachment.getIAttachmentOrNull(attachment) ?: return null
		return TACZGunPackPresentation.resolveAttachmentLaserConfig(snapshot, iAttachment.getAttachmentId(attachment))
	}

	private fun currentEditableLaserColor(): Int? {
		val config = currentLaserConfig()?.takeIf(TACZAttachmentLaserConfigDefinition::canEdit) ?: return null
		val gunStack = currentGunStack()
		val iGun = currentIGun() ?: return null
		if (selectedType == AttachmentType.NONE) {
			return if (iGun.hasCustomLaserColor(gunStack)) iGun.getLaserColor(gunStack) else config.defaultColor
		}
		val attachment = iGun.getAttachment(gunStack, selectedType)
		val iAttachment = IAttachment.getIAttachmentOrNull(attachment) ?: return null
		return if (iAttachment.hasCustomLaserColor(attachment)) iAttachment.getLaserColor(attachment) else config.defaultColor
	}

	private fun applyLaserPreview() {
		val gunStack = currentGunStack()
		val iGun = currentIGun() ?: return
		val hue = hueSlider?.sliderValue ?: return
		val saturation = saturationSlider?.sliderValue ?: return
		val color = Color.HSBtoRGB(hue.toFloat(), saturation.toFloat(), 1.0f)
		if (selectedType == AttachmentType.NONE) {
			iGun.setLaserColor(gunStack, color)
			return
		}
		val attachment = iGun.getAttachment(gunStack, selectedType)
		val iAttachment = IAttachment.getIAttachmentOrNull(attachment) ?: return
		iAttachment.setLaserColor(attachment, color)
		iGun.installAttachment(gunStack, attachment)
	}

	private fun computeStats(gunStack: ItemStack): ComputedStats? {
		val iGun = IGun.getIGunOrNull(gunStack) ?: return null
		val gunId = iGun.getGunId(gunStack)
		val snapshot = TACZGunPackRuntimeRegistry.getSnapshot()
		val gunEntry = snapshot.guns[gunId] ?: return null
		val gunData = GunDataAccessor.getGunData(gunId) ?: return null
		val fireMode = iGun.getFireMode(gunStack)
		val adjust = parseFireModeAdjust(gunEntry.data.raw, fireMode)
		val attachmentIds = SLOT_DISPLAY_ORDER.mapNotNull { type ->
			val installed = iGun.getAttachmentId(gunStack, type)
			when {
				installed != DefaultAssets.EMPTY_ATTACHMENT_ID -> installed
				else -> iGun.getBuiltInAttachmentId(gunStack, type).takeIf { it != DefaultAssets.EMPTY_ATTACHMENT_ID }
			}
		}

		val adsModifiers = collectNumericModifiers(snapshot, attachmentIds, "ads")
		val rpmModifiers = collectNumericModifiers(snapshot, attachmentIds, "rpm")
		val damageModifiers = collectNumericModifiers(snapshot, attachmentIds, "damage")
		val ammoSpeedModifiers = collectNumericModifiers(snapshot, attachmentIds, "ammo_speed")
		val armorIgnoreModifiers = collectNumericModifiers(snapshot, attachmentIds, "armor_ignore")
		val headShotModifiers = collectNumericModifiers(snapshot, attachmentIds, "head_shot")
		val weightModifiers = collectNumericModifiers(snapshot, attachmentIds, "weight_modifier")
		val inaccuracyModifiers = collectInaccuracyModifiers(snapshot, attachmentIds)
		val recoilModifiers = collectRecoilModifiers(snapshot, attachmentIds)
		val inaccuracyDefaults = parseInaccuracyDefaults(gunEntry.data.raw, adjust)
		val recoilDefaults = parseRecoilDefaults(gunEntry.data.raw)
		val recoilCache = TACZAttachmentModifierRegistry.evalRecoil(recoilModifiers, recoilDefaults.first, recoilDefaults.second)
		val finalInaccuracy = TACZAttachmentModifierRegistry.evalInaccuracy(inaccuracyModifiers, inaccuracyDefaults)

		val ammoSpeedBase = gunData.bulletData.speed + adjust.ammoSpeed
		val damageBase = gunData.bulletData.damage + adjust.damage
		val armorIgnoreBase = (gunData.bulletData.extraDamageData?.armorIgnore ?: 0f) + adjust.armorIgnore
		val headShotBase = (gunData.bulletData.extraDamageData?.headShotMultiplier ?: 1.0f) + adjust.headShot
		val rpmBase = (gunData.roundsPerMinute + adjust.rpm).coerceAtLeast(0)

		return ComputedStats(
			fireMode = fireMode,
			ammoCapacity = LegacyGunRefitRuntime.computeAmmoCapacity(gunStack).toFloat(),
			sprintTime = gunData.sprintTimeS.coerceAtLeast(0f),
			adsTime = TACZAttachmentModifierRegistry.evalNumeric(adsModifiers, gunData.aimTimeS.toDouble()).toFloat().coerceAtLeast(0f),
			rpm = TACZAttachmentModifierRegistry.evalNumeric(rpmModifiers, rpmBase.toDouble()).toFloat().coerceAtLeast(0f),
			damage = TACZAttachmentModifierRegistry.evalNumeric(damageModifiers, damageBase.toDouble()).toFloat().coerceAtLeast(0f),
			ammoSpeed = TACZAttachmentModifierRegistry.evalNumeric(ammoSpeedModifiers, ammoSpeedBase.toDouble()).toFloat().coerceAtLeast(0f),
			armorIgnorePercent = (TACZAttachmentModifierRegistry.evalNumeric(armorIgnoreModifiers, armorIgnoreBase.toDouble()).toFloat() * 100.0f).coerceAtLeast(0f),
			headShotMultiplier = TACZAttachmentModifierRegistry.evalNumeric(headShotModifiers, headShotBase.toDouble()).toFloat().coerceAtLeast(0f),
			hipfireInaccuracy = (finalInaccuracy["stand"] ?: 0f).coerceAtLeast(0f),
			aimInaccuracy = (finalInaccuracy["aim"] ?: 0f).coerceAtLeast(0f),
			recoilPitch = recoilCache.left().eval(recoilDefaults.first.toDouble()).toFloat().coerceAtLeast(0f),
			recoilYaw = recoilCache.right().eval(recoilDefaults.second.toDouble()).toFloat().coerceAtLeast(0f),
			weight = TACZAttachmentModifierRegistry.evalNumeric(weightModifiers, gunEntry.data.weight.toDouble()).toFloat().coerceAtLeast(0f),
		)
	}

	private fun collectNumericModifiers(
		snapshot: com.tacz.legacy.common.resource.TACZRuntimeSnapshot,
		attachmentIds: List<ResourceLocation>,
		key: String,
	): List<Modifier> {
		return attachmentIds.mapNotNull { attachmentId ->
			snapshot.attachments[attachmentId]?.data?.modifiers?.get(key)?.getValue() as? Modifier
		}
	}

	private fun collectInaccuracyModifiers(
		snapshot: com.tacz.legacy.common.resource.TACZRuntimeSnapshot,
		attachmentIds: List<ResourceLocation>,
	): List<Map<String, Modifier>> {
		return attachmentIds.mapNotNull { attachmentId ->
			@Suppress("UNCHECKED_CAST")
			snapshot.attachments[attachmentId]?.data?.modifiers?.get("inaccuracy")?.getValue() as? Map<String, Modifier>
		}
	}

	private fun collectRecoilModifiers(
		snapshot: com.tacz.legacy.common.resource.TACZRuntimeSnapshot,
		attachmentIds: List<ResourceLocation>,
	): List<TACZRecoilModifierValue> {
		return attachmentIds.mapNotNull { attachmentId ->
			snapshot.attachments[attachmentId]?.data?.modifiers?.get("recoil")?.getValue() as? TACZRecoilModifierValue
		}
	}

	private fun parseFireModeAdjust(raw: JsonObject, fireMode: FireMode): FireModeAdjust {
		val modeKey = when (fireMode) {
			FireMode.AUTO -> "auto"
			FireMode.SEMI -> "semi"
			FireMode.BURST -> "burst"
			FireMode.UNKNOWN -> return FireModeAdjust()
		}
		val adjustObject = raw.jsonObject("fire_mode_adjust")?.jsonObject(modeKey) ?: return FireModeAdjust()
		return FireModeAdjust(
			damage = adjustObject.floatValue("damage"),
			rpm = adjustObject.intValue("rpm"),
			ammoSpeed = adjustObject.floatValue("speed"),
			armorIgnore = adjustObject.floatValue("armor_ignore"),
			headShot = adjustObject.floatValue("head_shot_multiplier"),
			aimInaccuracy = adjustObject.floatValue("aim_inaccuracy"),
			otherInaccuracy = adjustObject.floatValue("other_inaccuracy"),
		)
	}

	private fun parseInaccuracyDefaults(raw: JsonObject, adjust: FireModeAdjust): Map<String, Float> {
		val inaccuracy = raw.jsonObject("inaccuracy")
		val standBase = inaccuracy?.floatValue("stand") ?: 0f
		val stand = standBase + adjust.otherInaccuracy
		return linkedMapOf(
			"stand" to stand.coerceAtLeast(0f),
			"move" to ((inaccuracy?.floatValue("move") ?: standBase) + adjust.otherInaccuracy).coerceAtLeast(0f),
			"sneak" to ((inaccuracy?.floatValue("sneak") ?: standBase) + adjust.otherInaccuracy).coerceAtLeast(0f),
			"lie" to ((inaccuracy?.floatValue("lie") ?: standBase) + adjust.otherInaccuracy).coerceAtLeast(0f),
			"aim" to ((inaccuracy?.floatValue("aim") ?: 0f) + adjust.aimInaccuracy).coerceAtLeast(0f),
		)
	}

	private fun parseRecoilDefaults(raw: JsonObject): Pair<Float, Float> {
		val recoilObject = raw.jsonObject("recoil") ?: return 0f to 0f
		return curveMagnitude(recoilObject.getAsJsonArray("pitch")) to curveMagnitude(recoilObject.getAsJsonArray("yaw"))
	}

	private fun curveMagnitude(curve: JsonArray?): Float {
		val first = curve?.firstOrNull()?.takeIf(JsonElement::isJsonObject)?.asJsonObject ?: return 0f
		val values = first.getAsJsonArray("value")?.mapNotNull { value -> runCatching { abs(value.asFloat) }.getOrNull() }.orEmpty()
		return values.maxOrNull() ?: 0f
	}

	private fun attachmentTypeLabel(type: AttachmentType): String {
		return if (type == AttachmentType.NONE) {
			I18n.format("tooltip.tacz.attachment.none")
		} else {
			I18n.format("tooltip.tacz.attachment.${type.serializedName}")
		}
	}

	private fun fireModeText(fireMode: FireMode): String {
		val key = when (fireMode) {
			FireMode.AUTO -> "gui.tacz.gun_refit.property_diagrams.auto"
			FireMode.BURST -> "gui.tacz.gun_refit.property_diagrams.burst"
			FireMode.SEMI -> "gui.tacz.gun_refit.property_diagrams.semi"
			FireMode.UNKNOWN -> "gui.tacz.gun_refit.property_diagrams.unknown"
		}
		return I18n.format(key)
	}

	private fun statsPanelRight(): Int {
		val maxRight = attachmentPanelLeft() - 8
		return minOf(maxRight, 20 + STATS_PANEL_WIDTH)
	}

	private fun attachmentPanelLeft(): Int = width - UI_MARGIN - ATTACHMENT_PANEL_WIDTH

	private fun attachmentColumnLeft(): Int = width - 30

	private fun attachmentColumnTop(): Int = 50

	private fun attachmentPanelRight(): Int = width - UI_MARGIN

	private fun attachmentPanelTop(): Int = ATTACH_PANEL_TOP

	private fun attachmentPanelBottom(): Int = height - ATTACH_PANEL_BOTTOM_MARGIN

	private fun isMouseInAttachmentPanel(mouseX: Int, mouseY: Int): Boolean =
		mouseX in attachmentColumnLeft() until (attachmentColumnLeft() + SLOT_SIZE) &&
			mouseY in (attachmentColumnTop() - 10) until (attachmentColumnTop() + SLOT_SIZE * CANDIDATES_PER_PAGE + 10)

	private fun isMouseInSlotBar(mouseX: Int, mouseY: Int): Boolean {
		val left = width - SLOT_BAR_RIGHT_MARGIN - SLOT_SIZE * SLOT_DISPLAY_ORDER.size
		val right = width - SLOT_BAR_RIGHT_MARGIN
		return mouseX in left until right && mouseY in SLOT_BAR_Y until (SLOT_BAR_Y + SLOT_SIZE)
	}

	private fun drawPanel(left: Int, top: Int, right: Int, bottom: Int, fillColor: Int = 0x55191E2B) {
		drawRect(left, top, right, bottom, fillColor)
		drawHorizontalLine(left, right - 1, top, 0xFFF3EFE0.toInt())
		drawHorizontalLine(left, right - 1, bottom - 1, 0xAA4A4A4A.toInt())
		drawVerticalLine(left, top, bottom - 1, 0xFFF3EFE0.toInt())
		drawVerticalLine(right - 1, top, bottom - 1, 0xAA4A4A4A.toInt())
	}

	private fun captureAndApplyViewFocusLock() {
		val player = currentPlayer() ?: return
		if (originalThirdPersonView == null) {
			originalThirdPersonView = mc.gameSettings.thirdPersonView
		}
		mc.gameSettings.thirdPersonView = 0
		if (lockedYaw == null || lockedPitch == null) {
			lockedYaw = player.rotationYaw
			lockedPitch = player.rotationPitch
		}
		enforceViewAndInputFocusLock()
	}

	private fun enforceViewAndInputFocusLock() {
		val player = currentPlayer()
		val yaw = lockedYaw
		val pitch = lockedPitch
		if (player != null && yaw != null && pitch != null) {
			player.rotationYaw = yaw
			player.prevRotationYaw = yaw
			player.rotationYawHead = yaw
			player.renderYawOffset = yaw
			player.rotationPitch = pitch
			player.prevRotationPitch = pitch
		}
		releaseGameplayKeys()
	}

	private fun restoreViewFocusLock() {
		originalThirdPersonView?.let { mc.gameSettings.thirdPersonView = it }
		originalThirdPersonView = null
		lockedYaw = null
		lockedPitch = null
		releaseGameplayKeys()
	}

	private fun releaseGameplayKeys() {
		setKeyReleased(mc.gameSettings.keyBindForward)
		setKeyReleased(mc.gameSettings.keyBindBack)
		setKeyReleased(mc.gameSettings.keyBindLeft)
		setKeyReleased(mc.gameSettings.keyBindRight)
		setKeyReleased(mc.gameSettings.keyBindJump)
		setKeyReleased(mc.gameSettings.keyBindSneak)
		setKeyReleased(mc.gameSettings.keyBindSprint)
		setKeyReleased(mc.gameSettings.keyBindAttack)
		setKeyReleased(mc.gameSettings.keyBindUseItem)
	}

	private fun setKeyReleased(binding: KeyBinding?) {
		if (binding == null) {
			return
		}
		KeyBinding.setKeyBindState(binding.keyCode, false)
	}

	private fun drawTextureRegion(
		texture: ResourceLocation,
		x: Int,
		y: Int,
		width: Int,
		height: Int,
		textureWidth: Float,
		textureHeight: Float,
		alpha: Float,
	) {
		mc.textureManager.bindTexture(texture)
		GlStateManager.enableBlend()
		GlStateManager.color(1f, 1f, 1f, alpha)
		Gui.drawModalRectWithCustomSizedTexture(x, y, 0f, 0f, width, height, textureWidth, textureHeight)
		GlStateManager.color(1f, 1f, 1f, 1f)
	}

	private fun normalizedRatio(value: Float, maxValue: Float, lowerIsBetter: Boolean): Float {
		if (maxValue <= 0f) {
			return 0f
		}
		val ratio = (value / maxValue).coerceIn(0f, 1f)
		return if (lowerIsBetter) 1f - ratio else ratio
	}

	private fun isPositiveDelta(lowerIsBetter: Boolean, delta: Float): Boolean =
		if (lowerIsBetter) delta < 0f else delta > 0f

	private fun formatNumber(value: Float): String {
		val rounded = String.format(Locale.ROOT, "%.2f", value)
		return rounded.replace(Regex("\\.00$"), "").replace(Regex("(\\.[0-9])0$"), "$1")
	}

	private fun floorMod(value: Int, mod: Int): Int {
		val remainder = value % mod
		return if (remainder < 0) remainder + mod else remainder
	}

	private fun propertyDiagramHeight(rowCount: Int): Int = 10 + (rowCount + 1) * 10 + 4

	private fun hidePropertiesButtonY(): Int {
		val stats = computeStats(currentGunStack()) ?: return 11
		return 11 + propertyDiagramHeight(buildStatRows(stats, stats).size) - 1
	}

	private data class FireModeAdjust(
		val damage: Float = 0f,
		val rpm: Int = 0,
		val ammoSpeed: Float = 0f,
		val armorIgnore: Float = 0f,
		val headShot: Float = 0f,
		val aimInaccuracy: Float = 0f,
		val otherInaccuracy: Float = 0f,
	)

	private data class ComputedStats(
		val fireMode: FireMode,
		val ammoCapacity: Float,
		val sprintTime: Float,
		val adsTime: Float,
		val rpm: Float,
		val damage: Float,
		val ammoSpeed: Float,
		val armorIgnorePercent: Float,
		val headShotMultiplier: Float,
		val hipfireInaccuracy: Float,
		val aimInaccuracy: Float,
		val recoilPitch: Float,
		val recoilYaw: Float,
		val weight: Float,
	)

	private data class StatRow(
		val label: String,
		val base: Float,
		val current: Float,
		val maxValue: Float,
		val lowerIsBetter: Boolean,
		val unit: String,
	)

	private data class RefitCandidate(
		val stack: ItemStack,
		val inventorySlotIndex: Int? = null,
	)

	private interface RefitTooltipButton {
		val tooltipLines: List<String>
		fun contains(mouseX: Int, mouseY: Int): Boolean
	}

	private inner class AttachmentSlotButton(id: Int, x: Int, y: Int, val type: AttachmentType) : GuiButton(id, x, y, SLOT_SIZE, SLOT_SIZE, "") {
		override fun drawButton(mc: Minecraft, mouseX: Int, mouseY: Int, partialTicks: Float) {
			if (!visible) {
				return
			}
			hovered = contains(mouseX, mouseY)
			GlStateManager.color(1f, 1f, 1f, 1f)
			mc.textureManager.bindTexture(TACZ_REFIT_SLOT_TEXTURE)
			if (hovered || selectedType == type) {
				drawTexturedModalRect(x, y, 0, 0, width, height)
			} else {
				drawTexturedModalRect(x + 1, y + 1, 1, 1, width - 2, height - 2)
			}
			val displayStack = displayedAttachment()
			if (!displayStack.isEmpty) {
				RenderHelper.enableGUIStandardItemLighting()
				itemRender.renderItemAndEffectIntoGUI(displayStack, x + 1, y + 1)
				TACZAsciiFontHelper.renderItemOverlayIntoGUI(itemRender, fontRenderer, displayStack, x + 1, y + 1, null)
				RenderHelper.disableStandardItemLighting()
			} else {
				mc.textureManager.bindTexture(TACZ_REFIT_SLOT_ICONS_TEXTURE)
				GlStateManager.disableLighting()
				GlStateManager.color(1f, 1f, 1f, if (isAllowed()) 0.96f else 0.72f)
				drawModalRectWithCustomSizedTexture(
					x + 2,
					y + 2,
					slotIconU(type, isAllowed()).toFloat(),
					0f,
					SLOT_ICON_DRAW_SIZE,
					SLOT_ICON_DRAW_SIZE,
					SLOT_ICON_TEXTURE_WIDTH.toFloat(),
					SLOT_ICON_UV_SIZE.toFloat(),
				)
				GlStateManager.color(1f, 1f, 1f, 1f)
			}
			if (hovered) {
				TACZAsciiFontHelper.drawCenteredStringWithShadow(fontRenderer, attachmentTypeLabel(type), x + width / 2, y + if (!displayStack.isEmpty && selectedType == type) 30 else 20, 0xFFFFFF)
			}
		}

		fun displayedAttachment(): ItemStack = LegacyGunRefitRuntime.displayedAttachment(currentGunStack(), type)

		fun isAllowed(): Boolean = type in allowedAttachmentTypes()

		fun contains(mouseX: Int, mouseY: Int): Boolean = mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height
	}

	private inner class InventoryAttachmentButton(
		id: Int,
		x: Int,
		y: Int,
		val candidate: RefitCandidate,
	) : GuiButton(id, x, y, SLOT_SIZE, SLOT_SIZE, "") {
		override fun drawButton(mc: Minecraft, mouseX: Int, mouseY: Int, partialTicks: Float) {
			if (!visible) {
				return
			}
			hovered = contains(mouseX, mouseY)
			GlStateManager.color(1f, 1f, 1f, 1f)
			mc.textureManager.bindTexture(TACZ_REFIT_SLOT_TEXTURE)
			if (hovered) {
				drawTexturedModalRect(x, y, 0, 0, width, height)
			} else {
				drawTexturedModalRect(x + 1, y + 1, 1, 1, width - 2, height - 2)
			}
			RenderHelper.enableGUIStandardItemLighting()
			itemRender.renderItemAndEffectIntoGUI(candidate.stack, x + 1, y + 1)
			TACZAsciiFontHelper.renderItemOverlayIntoGUI(itemRender, fontRenderer, candidate.stack, x + 1, y + 1, null)
			RenderHelper.disableStandardItemLighting()
		}

		fun contains(mouseX: Int, mouseY: Int): Boolean = mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height
	}

	private inner class RefitFlatButton(
		id: Int,
		x: Int,
		y: Int,
		width: Int,
		height: Int,
		displayString: String,
		override val tooltipLines: List<String> = emptyList(),
	) : GuiButton(id, x, y, width, height, displayString), RefitTooltipButton {
		override fun drawButton(mc: Minecraft, mouseX: Int, mouseY: Int, partialTicks: Float) {
			if (!visible) {
				return
			}
			hovered = contains(mouseX, mouseY)
			val fill = if (hovered) 0xAF303030.toInt() else 0xAF222222.toInt()
			drawRect(x, y, x + width, y + height, fill)
			val border = if (hovered) 0xFFF3EFE0.toInt() else 0xFF5A5A5A.toInt()
			drawHorizontalLine(x, x + width - 1, y, border)
			drawHorizontalLine(x, x + width - 1, y + height - 1, border)
			drawVerticalLine(x, y, y + height - 1, border)
			drawVerticalLine(x + width - 1, y, y + height - 1, border)
			TACZAsciiFontHelper.drawCenteredStringWithShadow(fontRenderer, displayString, x + width / 2, y + (height - 8) / 2, 0xF3EFE0)
		}

		override fun contains(mouseX: Int, mouseY: Int): Boolean = mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height
	}

	private inner class UnloadButton(id: Int, x: Int, y: Int) : GuiButton(id, x, y, 8, 8, ""), RefitTooltipButton {
		override val tooltipLines: List<String> = listOf(I18n.format("tooltip.tacz.refit.unload"))

		override fun drawButton(mc: Minecraft, mouseX: Int, mouseY: Int, partialTicks: Float) {
			if (!visible) {
				return
			}
			hovered = contains(mouseX, mouseY)
			GlStateManager.color(1f, 1f, 1f, 1f)
			mc.textureManager.bindTexture(TACZ_REFIT_UNLOAD_TEXTURE)
			val u = if (hovered) 0f else 80f
			drawModalRectWithCustomSizedTexture(x, y, u, 0f, width, height, 160f, 80f)
		}

		override fun contains(mouseX: Int, mouseY: Int): Boolean = mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height
	}

	private inner class TurnPageButton(
		id: Int,
		x: Int,
		y: Int,
		private val upPage: Boolean,
		override val tooltipLines: List<String>,
	) : GuiButton(id, x, y, 18, 8, ""), RefitTooltipButton {
		override fun drawButton(mc: Minecraft, mouseX: Int, mouseY: Int, partialTicks: Float) {
			if (!visible) {
				return
			}
			hovered = contains(mouseX, mouseY)
			GlStateManager.color(1f, 1f, 1f, 1f)
			mc.textureManager.bindTexture(TACZ_REFIT_TURN_PAGE_TEXTURE)
			val yOffset = if (upPage) 0 else 80
			if (hovered) {
				drawModalRectWithCustomSizedTexture(x, y, 0f, yOffset.toFloat(), width, height, 180f, 160f)
			} else {
				drawModalRectWithCustomSizedTexture(x + 1, y + 1, 10f, (yOffset + 10).toFloat(), width - 2, height - 2, 180f, 160f)
			}
		}

		override fun contains(mouseX: Int, mouseY: Int): Boolean = mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height
	}

	private inner class ColorSliderButton(
		id: Int,
		x: Int,
		y: Int,
		width: Int,
		value: Double,
		private val onValueChanged: () -> Unit,
	) : GuiButton(id, x, y, width, 12, "") {
		var sliderValue: Double = value.coerceIn(0.0, 1.0)
			private set
		private var dragging: Boolean = false

		override fun drawButton(mc: Minecraft, mouseX: Int, mouseY: Int, partialTicks: Float) {
			if (!visible) {
				return
			}
			hovered = contains(mouseX, mouseY)
			drawRect(x, y + 5, x + width, y + 7, 0x66FFFFFF)
			val knobX = x + (sliderValue * (width - 6)).roundToInt()
			drawRect(knobX, y + 2, knobX + 6, y + 10, if (hovered || dragging) 0xFFF3EFE0.toInt() else 0xFFAAAAAA.toInt())
		}

		override fun mousePressed(mc: Minecraft, mouseX: Int, mouseY: Int): Boolean {
			val pressed = super.mousePressed(mc, mouseX, mouseY)
			if (pressed) {
				dragging = true
				updateValue(mouseX)
			}
			return pressed
		}

		override fun mouseDragged(mc: Minecraft, mouseX: Int, mouseY: Int) {
			if (visible && dragging) {
				updateValue(mouseX)
			}
			super.mouseDragged(mc, mouseX, mouseY)
		}

		override fun mouseReleased(mouseX: Int, mouseY: Int) {
			dragging = false
			super.mouseReleased(mouseX, mouseY)
		}

		private fun updateValue(mouseX: Int) {
			sliderValue = ((mouseX - x).toDouble() / (width - 6).toDouble()).coerceIn(0.0, 1.0)
			onValueChanged()
		}

		private fun contains(mouseX: Int, mouseY: Int): Boolean = mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height
	}

	private companion object {
		private const val BUTTON_TOGGLE_PROPERTIES: Int = 1
		private const val BUTTON_FIRE_SELECT: Int = 2
		private const val BUTTON_PAGE_UP: Int = 4
		private const val BUTTON_PAGE_DOWN: Int = 5
		private const val BUTTON_UNLOAD: Int = 6
		private const val BUTTON_HUE_SLIDER: Int = 7
		private const val BUTTON_SATURATION_SLIDER: Int = 8
		private const val BUTTON_SLOT_BASE: Int = 1000
		private const val BUTTON_INVENTORY_BASE: Int = 2000

		private const val CANDIDATES_PER_PAGE: Int = 8
		private const val SLOT_SIZE: Int = 18
		private const val SLOT_ICON_UV_SIZE: Int = 32
		private const val SLOT_ICON_DRAW_SIZE: Int = 14
		private const val SLOT_ICON_TEXTURE_WIDTH: Int = SLOT_ICON_UV_SIZE * 7

		private const val TACZ_WORKBENCH_TOTAL_WIDTH: Int = 344
		private const val TACZ_WORKBENCH_TOTAL_HEIGHT: Int = 187
		private const val TACZ_WORKBENCH_SIDE_WIDTH: Int = 134
		private const val TACZ_WORKBENCH_MAIN_WIDTH: Int = 208
		private const val TACZ_WORKBENCH_MAIN_HEIGHT: Int = 160
		private const val TACZ_WORKBENCH_MAIN_OFFSET_X: Int = 136
		private const val TACZ_WORKBENCH_MAIN_OFFSET_Y: Int = 27

		private const val UI_MARGIN: Int = 16
		private const val TOP_BAR_TOP: Int = 16
		private const val TOP_BAR_BOTTOM: Int = 42
		private const val SLOT_BAR_Y: Int = 10
		private const val SLOT_BAR_RIGHT_MARGIN: Int = 12
		private const val ATTACH_PANEL_TOP: Int = 46
		private const val ATTACH_PANEL_BOTTOM_MARGIN: Int = 24
		private const val ATTACHMENT_PANEL_WIDTH: Int = 220
		private const val STATS_PANEL_WIDTH: Int = 320

		private val SLOT_DISPLAY_ORDER: List<AttachmentType> = listOf(
			AttachmentType.SCOPE,
			AttachmentType.MUZZLE,
			AttachmentType.STOCK,
			AttachmentType.GRIP,
			AttachmentType.LASER,
			AttachmentType.EXTENDED_MAG,
		)

		private val TACZ_WORKBENCH_MAIN_TEXTURE: ResourceLocation = ResourceLocation(TACZLegacy.MOD_ID, "textures/gui/gun_smith_table.png")
		private val TACZ_WORKBENCH_SIDE_TEXTURE: ResourceLocation = ResourceLocation(TACZLegacy.MOD_ID, "textures/gui/gun_smith_table_side.png")
		private val TACZ_REFIT_SLOT_TEXTURE: ResourceLocation = ResourceLocation(TACZLegacy.MOD_ID, "textures/gui/refit_slot.png")
		private val TACZ_REFIT_SLOT_ICONS_TEXTURE: ResourceLocation = ResourceLocation(TACZLegacy.MOD_ID, "textures/gui/refit_slot_icons.png")
		private val TACZ_REFIT_TURN_PAGE_TEXTURE: ResourceLocation = ResourceLocation(TACZLegacy.MOD_ID, "textures/gui/refit_turn_page.png")
		private val TACZ_REFIT_UNLOAD_TEXTURE: ResourceLocation = ResourceLocation(TACZLegacy.MOD_ID, "textures/gui/refit_unload.png")

		private var HIDE_GUN_PROPERTY_PANEL: Boolean = true

		private fun slotIconU(type: AttachmentType, allowed: Boolean): Int {
			if (!allowed) {
				return SLOT_ICON_UV_SIZE * 6
			}
			return when (type) {
				AttachmentType.GRIP -> SLOT_ICON_UV_SIZE * 0
				AttachmentType.LASER -> SLOT_ICON_UV_SIZE * 1
				AttachmentType.MUZZLE -> SLOT_ICON_UV_SIZE * 2
				AttachmentType.SCOPE -> SLOT_ICON_UV_SIZE * 3
				AttachmentType.STOCK -> SLOT_ICON_UV_SIZE * 4
				AttachmentType.EXTENDED_MAG -> SLOT_ICON_UV_SIZE * 5
				AttachmentType.NONE -> SLOT_ICON_UV_SIZE * 6
			}
		}

		private fun JsonObject.jsonObject(key: String): JsonObject? = get(key)?.takeIf { it.isJsonObject }?.asJsonObject

		private fun JsonObject.floatValue(key: String): Float = get(key)?.takeIf { !it.isJsonNull }?.asFloat ?: 0f

		private fun JsonObject.intValue(key: String): Int = get(key)?.takeIf { !it.isJsonNull }?.asInt ?: 0
	}
}
