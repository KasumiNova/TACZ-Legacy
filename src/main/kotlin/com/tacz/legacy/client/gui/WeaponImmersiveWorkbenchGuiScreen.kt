package com.tacz.legacy.client.gui

import com.tacz.legacy.client.gui.widget.FlatTextButton
import com.tacz.legacy.common.application.weapon.WeaponDefinition
import com.tacz.legacy.common.application.weapon.WeaponRuntime
import com.tacz.legacy.common.application.gunpack.WeaponAttachmentCompatibilityRuntime
import com.tacz.legacy.common.domain.weapon.WeaponFireMode
import com.tacz.legacy.common.infrastructure.mc.network.LegacyNetworkHandler
import com.tacz.legacy.common.infrastructure.mc.registry.item.LegacyAttachmentItem
import com.tacz.legacy.common.infrastructure.mc.registry.item.LegacyGunItem
import com.tacz.legacy.common.infrastructure.mc.weapon.WeaponAttachmentConflictRules
import com.tacz.legacy.common.infrastructure.mc.weapon.WeaponAttachmentModifierResolver
import com.tacz.legacy.common.infrastructure.mc.weapon.WeaponAttachmentSlot
import com.tacz.legacy.common.infrastructure.mc.weapon.WeaponAttachmentSnapshot
import com.tacz.legacy.common.infrastructure.mc.weapon.WeaponItemStackRuntimeData
import net.minecraft.client.gui.Gui
import net.minecraft.client.gui.GuiButton
import net.minecraft.client.gui.GuiScreen
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.RenderHelper
import net.minecraft.client.settings.KeyBinding
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation
import net.minecraft.util.text.TextComponentString
import net.minecraftforge.fml.common.registry.ForgeRegistries
import org.lwjgl.input.Keyboard
import org.lwjgl.input.Mouse
import kotlin.math.abs

public class WeaponImmersiveWorkbenchGuiScreen : GuiScreen() {

    private var showStatsPanel: Boolean = true
    private var selectedSlot: WeaponAttachmentSlot = WeaponAttachmentSlot.SCOPE
    private var attachmentPage: Int = 0

    private var lockedYaw: Float? = null
    private var lockedPitch: Float? = null
    private var originalThirdPersonView: Int? = null

    private var nextButtonId: Int = 1
    private val actionByButtonId: MutableMap<Int, ButtonAction> = linkedMapOf()
    private val slotByButtonId: MutableMap<Int, WeaponAttachmentSlot> = linkedMapOf()
    private val candidateByButtonId: MutableMap<Int, LegacyAttachmentItem> = linkedMapOf()
    private var workbenchClosePacketSent: Boolean = false

    override fun initGui() {
        super.initGui()
        Keyboard.enableRepeatEvents(true)
        buttonList.clear()
        actionByButtonId.clear()
        slotByButtonId.clear()
        candidateByButtonId.clear()
        nextButtonId = 1

        val context = resolveCurrentContext()
        captureAndApplyViewFocusLock(context)
        WeaponGunsmithImmersiveRuntime.setModelFocusSlot(selectedSlot)

        val attachLeft = attachmentPanelLeft()
        val attachRight = attachmentPanelRight()
        val attachTop = attachmentPanelTop()

        // 右侧面板布局：避免标题/按钮/提示文案互相遮挡
        val attachControlsY = attachTop + 26
        val attachListStartY = attachTop + 52

        addButtonWithAction(
            x = 20,
            y = 18,
            width = 120,
            height = 20,
            label = if (showStatsPanel) "隐藏图表" else "显示图表",
            action = ButtonAction.ToggleStatsPanel
        )
        addButtonWithAction(
            x = 144,
            y = 18,
            width = 96,
            height = 20,
            label = "调试台(B)",
            action = ButtonAction.OpenDebugWorkbench
        )
        addButtonWithAction(
            x = 244,
            y = 18,
            width = 72,
            height = 20,
            label = "退出(G)",
            action = ButtonAction.CloseWorkbench
        )

        val allowedSlots = resolveAllowedSlots(context?.definition)
        // 如果当前选中的槽位不在允许列表中，自动切到第一个允许的槽位。
        if (selectedSlot !in allowedSlots && allowedSlots.isNotEmpty()) {
            selectedSlot = allowedSlots.first()
        }
        val slotOrder = slotDisplayOrder()
        var slotX = width - SLOT_BAR_RIGHT_MARGIN - SLOT_TEXTURE_SIZE
        slotOrder.forEach { slot ->
            val id = addButtonWithAction(
                x = slotX,
                y = SLOT_BAR_Y,
                width = SLOT_TEXTURE_SIZE,
                height = SLOT_TEXTURE_SIZE,
                label = "",
                action = ButtonAction.SelectSlot(slot)
            )
            val button = buttonList.firstOrNull { it.id == id }
            if (button != null) {
                button.enabled = slot in allowedSlots
            }
            slotByButtonId[id] = slot
            slotX -= SLOT_TEXTURE_SIZE
        }

        if (context == null) {
            return
        }

        addButtonWithAction(
            x = attachLeft + 4,
            y = attachControlsY,
            width = 74,
            height = 20,
            label = "卸下槽位",
            action = ButtonAction.ClearSlot(selectedSlot)
        )
        addButtonWithAction(
            x = attachLeft + 82,
            y = attachControlsY,
            width = 34,
            height = 20,
            label = "<",
            action = ButtonAction.ChangePage(-1)
        )
        addButtonWithAction(
            x = attachLeft + 120,
            y = attachControlsY,
            width = 34,
            height = 20,
            label = ">",
            action = ButtonAction.ChangePage(1)
        )

        val candidates = availableAttachmentEntries(selectedSlot)
        val maxPage = maxAttachmentPage(candidates.size)
        attachmentPage = attachmentPage.coerceIn(0, maxPage)

        val pagedCandidates = candidates
            .drop(attachmentPage * CANDIDATES_PER_PAGE)
            .take(CANDIDATES_PER_PAGE)

        pagedCandidates.forEachIndexed { index, item ->
            val buttonY = attachListStartY + index * 22
            val shortName = item.attachmentId.substringAfter(':')
            val buttonId = addButtonWithAction(
                x = attachLeft + 4,
                y = buttonY,
                width = (attachRight - attachLeft - 8).coerceAtLeast(40),
                height = 20,
                label = shortName,
                action = ButtonAction.Install(item),
                align = FlatTextButton.Align.LEFT
            )
            candidateByButtonId[buttonId] = item
        }
    }

    override fun onGuiClosed() {
        super.onGuiClosed()
        Keyboard.enableRepeatEvents(false)
        notifyServerWorkbenchSessionClosed()
        // 关闭 GUI 时保留 closing 过渡：恢复输入，但让枪模做一段“退出动画”
        WeaponGunsmithImmersiveRuntime.requestClose()
        restoreViewFocusLock()
    }

    override fun updateScreen() {
        super.updateScreen()
        val context = resolveCurrentContext()
        if (context == null) {
            WeaponGunsmithImmersiveRuntime.deactivate()
            mc.displayGuiScreen(null)
            return
        }

        enforceViewAndInputFocusLock()
        WeaponGunsmithImmersiveRuntime.tickModelFocus()
    }

    override fun keyTyped(typedChar: Char, keyCode: Int) {
        when (keyCode) {
            Keyboard.KEY_G, Keyboard.KEY_ESCAPE -> {
                WeaponGunsmithImmersiveRuntime.requestClose()
                mc.displayGuiScreen(null)
                return
            }

            Keyboard.KEY_B -> {
                WeaponGunsmithImmersiveRuntime.deactivate()
                mc.displayGuiScreen(WeaponDebugWorkbenchGuiScreen())
                return
            }

            Keyboard.KEY_TAB -> {
                showStatsPanel = !showStatsPanel
                initGui()
                return
            }
        }
        super.keyTyped(typedChar, keyCode)
    }

    override fun handleMouseInput() {
        super.handleMouseInput()

        val dWheel = Mouse.getEventDWheel()
        if (dWheel == 0) {
            return
        }

        val mouseX = Mouse.getEventX() * width / mc.displayWidth
        val mouseY = height - Mouse.getEventY() * height / mc.displayHeight - 1
        val delta = if (dWheel > 0) -1 else 1

        if (isMouseInAttachmentPanel(mouseX, mouseY)) {
            changeAttachmentPage(delta)
            return
        }

        if (isMouseInSlotBar(mouseX, mouseY)) {
            cycleSlot(delta)
            return
        }
    }

    override fun actionPerformed(button: GuiButton) {
        val action = actionByButtonId[button.id] ?: return
        val context = resolveCurrentContext()

        when (action) {
            ButtonAction.ToggleStatsPanel -> {
                showStatsPanel = !showStatsPanel
                initGui()
            }

            ButtonAction.OpenDebugWorkbench -> {
                WeaponGunsmithImmersiveRuntime.deactivate()
                mc.displayGuiScreen(WeaponDebugWorkbenchGuiScreen())
            }

            ButtonAction.CloseWorkbench -> {
                WeaponGunsmithImmersiveRuntime.requestClose()
                mc.displayGuiScreen(null)
            }

            is ButtonAction.SelectSlot -> {
                selectedSlot = action.slot
                attachmentPage = 0
                WeaponGunsmithImmersiveRuntime.setModelFocusSlot(selectedSlot)
                initGui()
            }

            is ButtonAction.ClearSlot -> {
                if (context == null) {
                    return
                }
                LegacyNetworkHandler.sendWeaponAttachmentClearToServer(action.slot)
            }

            is ButtonAction.ChangePage -> {
                val maxPage = maxAttachmentPage(availableAttachmentEntries(selectedSlot).size)
                attachmentPage = (attachmentPage + action.delta).coerceIn(0, maxPage)
                initGui()
            }

            is ButtonAction.Install -> {
                if (context == null) {
                    return
                }

                val check = WeaponAttachmentConflictRules.validateInstall(
                    snapshot = context.attachmentSnapshot,
                    slot = selectedSlot,
                    attachmentId = action.item.attachmentId,
                    gunId = context.gunId,
                    definition = context.definition
                )
                if (!check.accepted) {
                    mc.player?.sendStatusMessage(
                        TextComponentString("[TACZ] ${check.reasonMessage ?: "配件安装失败"}"),
                        true
                    )
                    return
                }

                LegacyNetworkHandler.sendWeaponAttachmentInstallToServer(
                    slot = selectedSlot,
                    attachmentId = action.item.attachmentId
                )
            }
        }
    }

    override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
        val context = resolveCurrentContext()
        // TACZ 原生工作台纹理底图 + 轻微暗角
        drawTaczWorkbenchBackdrop()
        drawRect(0, 0, width, height, 0x12000000)
        drawRect(UI_MARGIN, TOP_BAR_TOP, width - UI_MARGIN, TOP_BAR_BOTTOM, 0x662F3C58)
        drawCenteredString(fontRenderer, "沉浸式改枪（实验）", width / 2, 24, 0xF0F0F0)

        if (context == null) {
            drawCenteredString(fontRenderer, "请手持 TACZ 枪械", width / 2, 60, 0xFF6666)
            super.drawScreen(mouseX, mouseY, partialTicks)
            return
        }

        drawWorkbenchInfo(context)
        if (showStatsPanel) {
            drawStatsPanel(context, mouseX, mouseY)
        }
        drawAttachmentPanel(context)
        drawTaczSlotDecorations(context, mouseX, mouseY)

        super.drawScreen(mouseX, mouseY, partialTicks)
        drawHoverTips(context, mouseX, mouseY)
    }

    private fun drawTaczWorkbenchBackdrop() {
        val originX = ((width - TACZ_WORKBENCH_TOTAL_WIDTH) / 2).coerceAtLeast(UI_MARGIN)
        val originY = ((height - TACZ_WORKBENCH_TOTAL_HEIGHT) / 2).coerceAtLeast(10)

        drawTextureRegion(
            texture = TACZ_WORKBENCH_SIDE_TEXTURE,
            x = originX,
            y = originY,
            u = 0f,
            v = 0f,
            width = TACZ_WORKBENCH_SIDE_WIDTH,
            height = TACZ_WORKBENCH_TOTAL_HEIGHT,
            textureWidth = TACZ_WORKBENCH_SIDE_WIDTH.toFloat(),
            textureHeight = TACZ_WORKBENCH_TOTAL_HEIGHT.toFloat(),
            alpha = 0.30f
        )
        drawTextureRegion(
            texture = TACZ_WORKBENCH_MAIN_TEXTURE,
            x = originX + TACZ_WORKBENCH_MAIN_OFFSET_X,
            y = originY + TACZ_WORKBENCH_MAIN_OFFSET_Y,
            u = 0f,
            v = 0f,
            width = TACZ_WORKBENCH_MAIN_WIDTH,
            height = TACZ_WORKBENCH_MAIN_HEIGHT,
            textureWidth = TACZ_WORKBENCH_MAIN_WIDTH.toFloat(),
            textureHeight = TACZ_WORKBENCH_MAIN_HEIGHT.toFloat(),
            alpha = 0.26f
        )
    }

    private fun drawTaczSlotDecorations(context: WorkbenchContext, mouseX: Int, mouseY: Int) {
        slotByButtonId.forEach { (buttonId, slot) ->
            val button = buttonList.firstOrNull { it.id == buttonId } ?: return@forEach
            val slotX = button.x + (button.width - SLOT_TEXTURE_SIZE) / 2
            val slotY = button.y + (button.height - SLOT_TEXTURE_SIZE) / 2
            val selected = slot == selectedSlot
            val hovered =
                mouseX >= button.x && mouseX < button.x + button.width &&
                    mouseY >= button.y && mouseY < button.y + button.height
            val allowed = isSlotAllowed(context.definition, slot)

            if (selected || hovered) {
                drawTextureRegion(
                    texture = TACZ_REFIT_SLOT_TEXTURE,
                    x = slotX,
                    y = slotY,
                    u = 0f,
                    v = 0f,
                    width = SLOT_TEXTURE_SIZE,
                    height = SLOT_TEXTURE_SIZE,
                    textureWidth = SLOT_TEXTURE_SIZE.toFloat(),
                    textureHeight = SLOT_TEXTURE_SIZE.toFloat(),
                    alpha = 1f
                )
            } else {
                drawTextureRegion(
                    texture = TACZ_REFIT_SLOT_TEXTURE,
                    x = slotX + 1,
                    y = slotY + 1,
                    u = 1f,
                    v = 1f,
                    width = SLOT_TEXTURE_SIZE - 2,
                    height = SLOT_TEXTURE_SIZE - 2,
                    textureWidth = SLOT_TEXTURE_SIZE.toFloat(),
                    textureHeight = SLOT_TEXTURE_SIZE.toFloat(),
                    alpha = 0.95f
                )
            }

            val installedStack = resolveInstalledAttachmentStack(context.attachmentSnapshot, slot)
            if (installedStack != null && !installedStack.isEmpty) {
                RenderHelper.enableGUIStandardItemLighting()
                GlStateManager.enableRescaleNormal()
                itemRender.renderItemAndEffectIntoGUI(installedStack, slotX + 1, slotY + 1)
                itemRender.renderItemOverlays(fontRenderer, installedStack, slotX + 1, slotY + 1)
                GlStateManager.disableRescaleNormal()
                RenderHelper.disableStandardItemLighting()
            } else {
                val iconX = slotX + 2
                val iconY = slotY + 2
                drawTextureRegion(
                    texture = TACZ_REFIT_SLOT_ICONS_TEXTURE,
                    x = iconX,
                    y = iconY,
                    u = slotIconU(slot, allowed).toFloat(),
                    v = 0f,
                    width = SLOT_ICON_DRAW_SIZE,
                    height = SLOT_ICON_DRAW_SIZE,
                    textureWidth = SLOT_ICON_TEXTURE_WIDTH.toFloat(),
                    textureHeight = SLOT_ICON_UV_SIZE.toFloat(),
                    alpha = if (allowed) 0.96f else 0.72f
                )
            }
        }
    }

    private fun slotIconU(slot: WeaponAttachmentSlot, allowed: Boolean): Int {
        if (!allowed) {
            return SLOT_ICON_UV_SIZE * 6
        }
        return when (slot) {
        WeaponAttachmentSlot.GRIP -> SLOT_ICON_UV_SIZE * 0
        WeaponAttachmentSlot.LASER -> SLOT_ICON_UV_SIZE * 1
        WeaponAttachmentSlot.MUZZLE -> SLOT_ICON_UV_SIZE * 2
        WeaponAttachmentSlot.SCOPE -> SLOT_ICON_UV_SIZE * 3
        WeaponAttachmentSlot.STOCK -> SLOT_ICON_UV_SIZE * 4
        WeaponAttachmentSlot.EXTENDED_MAG -> SLOT_ICON_UV_SIZE * 5
        }
    }

    private fun isSlotAllowed(definition: WeaponDefinition, slot: WeaponAttachmentSlot): Boolean {
        val allowed = definition.allowAttachmentTypes
        return allowed.isEmpty() || slot.slotKey in allowed
    }

    private fun resolveInstalledAttachmentStack(
        snapshot: WeaponAttachmentSnapshot,
        slot: WeaponAttachmentSlot
    ): ItemStack? {
        val attachmentId = installedAttachmentId(snapshot, slot)?.trim().orEmpty()
        if (attachmentId.isBlank()) {
            return null
        }
        return try {
            val item = ForgeRegistries.ITEMS.getValue(ResourceLocation(attachmentId))
            if (item is LegacyAttachmentItem) ItemStack(item) else null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun drawTextureRegion(
        texture: ResourceLocation,
        x: Int,
        y: Int,
        u: Float,
        v: Float,
        width: Int,
        height: Int,
        textureWidth: Float,
        textureHeight: Float,
        alpha: Float
    ) {
        mc.textureManager.bindTexture(texture)
        GlStateManager.enableBlend()
        GlStateManager.color(1f, 1f, 1f, alpha)
        Gui.drawModalRectWithCustomSizedTexture(
            x,
            y,
            u,
            v,
            width,
            height,
            textureWidth,
            textureHeight
        )
        GlStateManager.color(1f, 1f, 1f, 1f)
    }

    override fun doesGuiPauseGame(): Boolean = false

    private fun drawWorkbenchInfo(context: WorkbenchContext) {
        val fireModeText = when (context.definition.spec.fireMode) {
            WeaponFireMode.AUTO -> "开火模式：全自动"
            WeaponFireMode.SEMI -> "开火模式：半自动"
            WeaponFireMode.BURST -> "开火模式：点射"
        }

        val selectedInstalled = installedAttachmentId(context.attachmentSnapshot, selectedSlot)
            ?: "无"

        drawString(fontRenderer, "$fireModeText    枪械：${context.gunId}", 24, 50, 0xEAEAEA)
        drawString(
            fontRenderer,
            "当前槽位：${selectedSlot.name}    已装配：${selectedInstalled.substringAfter(':')}",
            24,
            62,
            0xD7E6FF
        )
    }

    private fun drawAttachmentPanel(context: WorkbenchContext) {
        val left = attachmentPanelLeft()
        val right = attachmentPanelRight()
        val top = attachmentPanelTop()
        val bottom = attachmentPanelBottom()

        // 背景按内容分块绘制，减少不必要遮挡
        val headerY = top + 4
        val installedY = headerY + 12
        val controlsY = top + 26
        val listStartY = top + 52

        val all = availableAttachmentEntries(selectedSlot)
        val maxPage = maxAttachmentPage(all.size)
        val pageText = "${selectedSlot.name}  ${attachmentPage + 1}/${maxPage + 1}"

        // 主块：标题 + 控件 + 列表区域
        val shownCount = all
            .drop(attachmentPage * CANDIDATES_PER_PAGE)
            .take(CANDIDATES_PER_PAGE)
            .size
        val listHeight = if (all.isEmpty()) 28 else (shownCount.coerceAtLeast(1) * 22 + 6)
        val mainBottom = (listStartY + listHeight).coerceAtMost(bottom)
        drawRect(left, top, right, mainBottom, 0x77191E2B)

        drawString(fontRenderer, pageText, left + 8, headerY, 0xE9E9E9)

        val hintY = height - 36
        val hint1 = "G 退出沉浸式  |  B 调试台"
        val hint2 = if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)) {
            "按键提示：点击候选配件直接装配"
        } else {
            "按下 Shift 查看详细提示"
        }
        val hint3 = "切换槽位将自动聚焦枪械部位"
        val hintWidth = listOf(hint1, hint2, hint3)
            .maxOfOrNull { fontRenderer.getStringWidth(it) }
            ?.coerceAtLeast(0)
            ?: 0
        val hintRight = (left + 12 + hintWidth).coerceAtMost(right)
        drawRect(left, hintY - 14, hintRight, hintY + 22, 0x55191E2B)

        drawString(fontRenderer, hint1, left + 8, hintY, 0xC7C7C7)

        drawString(fontRenderer, hint2, left + 8, hintY + 10, 0x8FA8D6)
        drawString(fontRenderer, hint3, left + 8, hintY - 10, 0x9BC2FF)

        if (all.isEmpty()) {
            drawString(fontRenderer, "该槽位无可用配件", left + 8, listStartY + 6, 0xFF8888)
            return
        }

        val currentInstalled = installedAttachmentId(context.attachmentSnapshot, selectedSlot)
        if (!currentInstalled.isNullOrBlank()) {
            drawString(
                fontRenderer,
                "已装配：${currentInstalled.substringAfter(':')}",
                left + 8,
                installedY,
                0xA8FFC8
            )
        }
    }

    private fun drawStatsPanel(context: WorkbenchContext, mouseX: Int, mouseY: Int) {
        val panelLeft = 20
        val panelTop = 76
        val maxRight = attachmentPanelLeft() - 8

        val panelWidth = (minOf(maxRight, panelLeft + STATS_PANEL_WIDTH) - panelLeft)
            .coerceAtLeast(180)
        val contentX = panelLeft + 12
        val contentWidth = (panelWidth - 24).coerceAtLeast(160)

        val hoveredCandidate = resolveHoveredCandidate(mouseX, mouseY)
        val effectiveSnapshot = if (hoveredCandidate != null) {
            val check = WeaponAttachmentConflictRules.validateInstall(
                snapshot = context.attachmentSnapshot,
                slot = selectedSlot,
                attachmentId = hoveredCandidate.attachmentId,
                gunId = context.gunId,
                definition = context.definition
            )
            if (check.accepted) {
                withAttachment(context.attachmentSnapshot, selectedSlot, hoveredCandidate.attachmentId)
            } else {
                context.attachmentSnapshot
            }
        } else {
            context.attachmentSnapshot
        }

        val baseStats = computeStats(context.definition, context.attachmentSnapshot)
        val effectiveStats = computeStats(context.definition, effectiveSnapshot)

        val header = "S 图表（悬停配件可预览变化）"
        val headerY = panelTop + 10

        val rows = listOf(
            StatRow("弹匣容量", baseStats.magazineCapacity, effectiveStats.magazineCapacity, 120f, false, ""),
            StatRow("跑射延迟", baseStats.reloadSeconds, effectiveStats.reloadSeconds, 4.0f, true, "s"),
            StatRow("弹速", baseStats.bulletSpeedMps, effectiveStats.bulletSpeedMps, 1000f, false, "m/s"),
            StatRow("伤害", baseStats.damage, effectiveStats.damage, 30f, false, ""),
            StatRow("穿甲倍率", baseStats.armorIgnore * 100f, effectiveStats.armorIgnore * 100f, 100f, false, "%"),
            StatRow("头部倍率", baseStats.headShotMultiplier, effectiveStats.headShotMultiplier, 4f, false, "x"),
            StatRow("站立扩散", baseStats.standInaccuracy, effectiveStats.standInaccuracy, 4f, true, ""),
            StatRow("瞄准扩散", baseStats.aimInaccuracy, effectiveStats.aimInaccuracy, 4f, true, ""),
            StatRow("垂直后坐力", baseStats.knockback, effectiveStats.knockback, 2f, true, "")
        )

        fun buildValueText(row: StatRow): String {
            val delta = row.current - row.base
            val deltaText = if (abs(delta) < 0.0001f) {
                ""
            } else {
                val sign = if (delta > 0f) "+" else ""
                val colorCode = if (isDeltaPositive(row.lowerIsBetter, delta)) "§a" else "§c"
                " $colorCode(${sign}${formatNumber(delta)}${row.unit})"
            }
            return "${formatNumber(row.current)}${row.unit}$deltaText"
        }

        val valueTexts = rows.associateWith(::buildValueText)
        val maxValueTextWidth = valueTexts.values
            .maxOfOrNull { fontRenderer.getStringWidth(it) }
            ?.coerceAtLeast(0)
            ?: 0

        val labelColumnWidth = 86
        val gapLabelToBar = 6
        val gapBarToValue = 10
        val minValueColumnWidth = 60
        val minBarWidth = 80

        val maxValueColumnWidth = (contentWidth - labelColumnWidth - gapLabelToBar - gapBarToValue - minBarWidth)
            .coerceAtLeast(minValueColumnWidth)
        val valueColumnWidth = (maxValueTextWidth + 4)
            .coerceIn(minValueColumnWidth, maxValueColumnWidth)
        val barWidth = (contentWidth - labelColumnWidth - gapLabelToBar - gapBarToValue - valueColumnWidth)
            .coerceAtLeast(60)
            .coerceAtMost(240)

        val rowStartY = panelTop + 26
        val rowStep = 16
        val panelHeight = (26 + rows.size * rowStep + 10).coerceAtLeast(50)
        drawRect(panelLeft, panelTop, panelLeft + panelWidth, panelTop + panelHeight, 0x55191E2B)
        drawString(fontRenderer, header, contentX, headerY, 0xF4F4F4)
        // 细分隔线：让标题与数据区更清晰
        drawRect(panelLeft + 8, panelTop + 22, panelLeft + panelWidth - 8, panelTop + 23, 0x332D3547)

        val barX = contentX + labelColumnWidth + gapLabelToBar
        val valueX = barX + barWidth + gapBarToValue

        rows.forEachIndexed { index, row ->
            drawStatRow(
                row = row,
                x = contentX,
                y = rowStartY + index * rowStep,
                barX = barX,
                barWidth = barWidth,
                valueX = valueX,
                valueText = valueTexts[row].orEmpty()
            )
        }
    }

    private fun drawStatRow(
        row: StatRow,
        x: Int,
        y: Int,
        barX: Int,
        barWidth: Int,
        valueX: Int,
        valueText: String
    ) {
        drawString(fontRenderer, row.label, x, y, 0xEAEAEA)

        val barY = y + 4

        val baseRatio = normalizedRatio(
            value = row.base,
            maxValue = row.maxValue,
            lowerIsBetter = row.lowerIsBetter
        )
        val currentRatio = normalizedRatio(
            value = row.current,
            maxValue = row.maxValue,
            lowerIsBetter = row.lowerIsBetter
        )

        val baseFill = (barWidth * baseRatio).toInt().coerceIn(0, barWidth)
        val currentFill = (barWidth * currentRatio).toInt().coerceIn(0, barWidth)

        drawRect(barX, barY, barX + barWidth, barY + 8, 0xDD05070A.toInt())
        // 基准值：白条
        drawRect(barX, barY, barX + baseFill, barY + 8, 0xDDF1F1F1.toInt())

        // 变化段：增益绿 / 减益红（基于 normalizedRatio，使“更好”的方向永远是更长）
        if (currentFill != baseFill) {
            val start = minOf(baseFill, currentFill)
            val end = maxOf(baseFill, currentFill)
            val improved = currentFill > baseFill
            val deltaColor = if (improved) 0xCC2BFF3A.toInt() else 0xCCFF3A3A.toInt()
            drawRect(barX + start, barY, barX + end, barY + 8, deltaColor)

            // 当前值游标
            val cursorX = (barX + currentFill).coerceIn(barX + 1, barX + barWidth - 1)
            drawRect(cursorX - 1, barY - 1, cursorX, barY + 9, 0xAAFFFFFF.toInt())
        }

        // 数值列固定左对齐，避免“每行长度不同”造成错位。
        drawString(fontRenderer, valueText, valueX, y, 0xF5F5F5)
    }

    private fun drawHoverTips(context: WorkbenchContext, mouseX: Int, mouseY: Int) {
        val hoveredButton = findHoveredButton(mouseX, mouseY) ?: return

        slotByButtonId[hoveredButton.id]?.let { slot ->
            val installed = installedAttachmentId(context.attachmentSnapshot, slot)
            val lines = mutableListOf<String>()
            lines += "§b${slot.name}"
            lines += if (installed.isNullOrBlank()) "§7未安装配件" else "§a已安装：${installed.substringAfter(':')}"
            drawHoveringText(lines, mouseX, mouseY, fontRenderer)
            return
        }

        candidateByButtonId[hoveredButton.id]?.let { candidate ->
            val lines = buildCandidateTooltip(context, candidate)
            drawHoveringText(lines, mouseX, mouseY, fontRenderer)
        }
    }

    private fun buildCandidateTooltip(context: WorkbenchContext, candidate: LegacyAttachmentItem): List<String> {
        val lines = mutableListOf<String>()
        lines += "§d${candidateDisplayName(candidate)}"
        lines += "§7${candidate.attachmentId}"

        val check = WeaponAttachmentConflictRules.validateInstall(
            snapshot = context.attachmentSnapshot,
            slot = selectedSlot,
            attachmentId = candidate.attachmentId,
            gunId = context.gunId,
            definition = context.definition
        )
        if (!check.accepted) {
            lines += "§c${check.reasonMessage ?: "配件冲突"}"
        } else {
            lines += "§a可安装"
        }

        val currentStats = computeStats(context.definition, context.attachmentSnapshot)
        val previewStats = computeStats(
            definition = context.definition,
            snapshot = withAttachment(context.attachmentSnapshot, selectedSlot, candidate.attachmentId)
        )

        appendDeltaLine(lines, "弹匣容量", currentStats.magazineCapacity, previewStats.magazineCapacity, "")
        appendDeltaLine(lines, "伤害", currentStats.damage, previewStats.damage, "")
        appendDeltaLine(lines, "穿甲倍率", currentStats.armorIgnore * 100f, previewStats.armorIgnore * 100f, "%")
        appendDeltaLine(lines, "头部倍率", currentStats.headShotMultiplier, previewStats.headShotMultiplier, "x")
        appendDeltaLine(lines, "站立扩散", currentStats.standInaccuracy, previewStats.standInaccuracy, "", lowerIsBetter = true)
        appendDeltaLine(lines, "瞄准扩散", currentStats.aimInaccuracy, previewStats.aimInaccuracy, "", lowerIsBetter = true)
        appendDeltaLine(lines, "垂直后坐力", currentStats.knockback, previewStats.knockback, "", lowerIsBetter = true)

        if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)) {
            lines += "§9应用军械师"
            lines += "§7适配：${context.gunId}"
        } else {
            lines += "§7按下 [Shift] 查看支持枪械"
        }

        return lines
    }

    private fun appendDeltaLine(
        lines: MutableList<String>,
        label: String,
        current: Float,
        next: Float,
        unit: String,
        lowerIsBetter: Boolean = false
    ) {
        val delta = next - current
        if (abs(delta) < 0.0001f) {
            return
        }

        val sign = if (delta > 0f) "+" else ""
        val positive = isDeltaPositive(lowerIsBetter, delta)
        val color = if (positive) "§a" else "§c"
        lines += "$color$label ${sign}${formatNumber(delta)}$unit"
    }

    private fun isDeltaPositive(lowerIsBetter: Boolean, delta: Float): Boolean {
        return if (lowerIsBetter) delta < 0f else delta > 0f
    }

    private fun normalizedRatio(value: Float, maxValue: Float, lowerIsBetter: Boolean): Float {
        if (maxValue <= 0f) {
            return 0f
        }
        val ratio = (value / maxValue).coerceIn(0f, 1f)
        return if (lowerIsBetter) 1f - ratio else ratio
    }

    private fun computeStats(definition: WeaponDefinition, snapshot: WeaponAttachmentSnapshot): ComputedStats {
        val mods = WeaponAttachmentModifierResolver.resolve(snapshot)
        return ComputedStats(
            magazineCapacity = (definition.spec.magazineSize + mods.bonusMagazineSize).toFloat(),
            reloadSeconds = definition.spec.reloadTicks.toFloat() / 20f,
            bulletSpeedMps = definition.ballistics.speed * 20f,
            damage = (definition.ballistics.damage + mods.damageAdd).coerceAtLeast(0f),
            armorIgnore = (definition.ballistics.armorIgnore + mods.armorIgnoreAdd).coerceIn(0f, 1f),
            headShotMultiplier = (definition.ballistics.headShotMultiplier + mods.headShotMultiplierAdd).coerceAtLeast(0f),
            standInaccuracy = (definition.ballistics.inaccuracy.stand + mods.standInaccuracyAdd).coerceAtLeast(0f),
            aimInaccuracy = (definition.ballistics.inaccuracy.aim + mods.aimInaccuracyAdd).coerceAtLeast(0f),
            knockback = (definition.ballistics.knockback + mods.knockbackAdd).coerceAtLeast(0f)
        )
    }

    private fun resolveCurrentContext(): WorkbenchContext? {
        val player = mc.player ?: return null
        val stack = resolveHeldGunStack(player.heldItemMainhand, player.heldItemOffhand) ?: return null
        val gunId = stack.item.registryName
            ?.toString()
            ?.substringAfter(':')
            ?.trim()
            ?.lowercase()
            ?.ifBlank { null }
            ?: return null
        val definition = WeaponRuntime.registry().snapshot().findDefinition(gunId) ?: return null
        val attachmentSnapshot = WeaponItemStackRuntimeData.readAttachmentSnapshot(stack)
        return WorkbenchContext(
            gunId = gunId,
            stack = stack,
            definition = definition,
            attachmentSnapshot = attachmentSnapshot
        )
    }

    private fun availableAttachmentEntries(slot: WeaponAttachmentSlot): List<LegacyAttachmentItem> {
        val context = resolveCurrentContext()
        val definition = context?.definition
        val gunId = context?.gunId
        val compatSnapshot = WeaponAttachmentCompatibilityRuntime.registry().snapshot()

        return ForgeRegistries.ITEMS.valuesCollection
            .asSequence()
            .mapNotNull { it as? LegacyAttachmentItem }
            .filter { it.slot == slot }
            .filter { item ->
                if (definition != null && definition.allowAttachmentTypes.isNotEmpty() && slot.slotKey !in definition.allowAttachmentTypes) {
                    return@filter false
                }
                if (gunId.isNullOrBlank()) {
                    return@filter true
                }
                compatSnapshot.isAttachmentAllowed(
                    gunId = gunId,
                    attachmentId = item.attachmentId,
                    definitionAllowEntries = definition?.allowAttachments.orEmpty()
                )
            }
            .sortedBy { it.attachmentId }
            .toList()
    }

    private fun resolveAllowedSlots(definition: WeaponDefinition?): List<WeaponAttachmentSlot> {
        val allowed = definition?.allowAttachmentTypes ?: emptySet()
        if (allowed.isEmpty()) return slotDisplayOrder()
        return slotDisplayOrder().filter { it.slotKey in allowed }
    }

    private fun resolveHeldGunStack(mainHand: ItemStack, offHand: ItemStack): ItemStack? {
        if (!mainHand.isEmpty && mainHand.item is LegacyGunItem) {
            return mainHand
        }
        if (!offHand.isEmpty && offHand.item is LegacyGunItem) {
            return offHand
        }
        return null
    }

    private fun slotDisplayOrder(): List<WeaponAttachmentSlot> {
        return listOf(
            WeaponAttachmentSlot.SCOPE,
            WeaponAttachmentSlot.MUZZLE,
            WeaponAttachmentSlot.STOCK,
            WeaponAttachmentSlot.GRIP,
            WeaponAttachmentSlot.LASER,
            WeaponAttachmentSlot.EXTENDED_MAG
        )
    }

    private fun withAttachment(
        snapshot: WeaponAttachmentSnapshot,
        slot: WeaponAttachmentSlot,
        attachmentId: String
    ): WeaponAttachmentSnapshot {
        val normalizedId = attachmentId.trim().ifBlank { return snapshot }
        return when (slot) {
            WeaponAttachmentSlot.SCOPE -> snapshot.copy(scopeId = normalizedId)
            WeaponAttachmentSlot.MUZZLE -> snapshot.copy(muzzleId = normalizedId)
            WeaponAttachmentSlot.EXTENDED_MAG -> snapshot.copy(
                extendedMagId = normalizedId,
                extendedMagLevel = resolveExtendedMagLevel(normalizedId)
            )
            WeaponAttachmentSlot.STOCK -> snapshot.copy(stockId = normalizedId)
            WeaponAttachmentSlot.GRIP -> snapshot.copy(gripId = normalizedId)
            WeaponAttachmentSlot.LASER -> snapshot.copy(laserId = normalizedId)
        }
    }

    private fun installedAttachmentId(snapshot: WeaponAttachmentSnapshot, slot: WeaponAttachmentSlot): String? {
        return when (slot) {
            WeaponAttachmentSlot.SCOPE -> snapshot.scopeId
            WeaponAttachmentSlot.MUZZLE -> snapshot.muzzleId
            WeaponAttachmentSlot.EXTENDED_MAG -> snapshot.extendedMagId
            WeaponAttachmentSlot.STOCK -> snapshot.stockId
            WeaponAttachmentSlot.GRIP -> snapshot.gripId
            WeaponAttachmentSlot.LASER -> snapshot.laserId
        }
    }

    private fun resolveExtendedMagLevel(attachmentId: String): Int {
        val normalized = attachmentId.trim().lowercase()
        return when {
            normalized.contains("level_3") || normalized.contains("extended_mag_3") || normalized.endsWith("iii") || normalized.contains("_iii") -> 3
            normalized.contains("level_2") || normalized.contains("extended_mag_2") || normalized.endsWith("ii") || normalized.contains("_ii") -> 2
            else -> 1
        }
    }

    private fun resolveHoveredCandidate(mouseX: Int, mouseY: Int): LegacyAttachmentItem? {
        val hoveredButton = findHoveredButton(mouseX, mouseY) ?: return null
        return candidateByButtonId[hoveredButton.id]
    }

    private fun isMouseInAttachmentPanel(mouseX: Int, mouseY: Int): Boolean {
        return mouseX in attachmentPanelLeft() until attachmentPanelRight() && mouseY in attachmentPanelTop() until attachmentPanelBottom()
    }

    private fun isMouseInSlotBar(mouseX: Int, mouseY: Int): Boolean {
        val slotCount = slotDisplayOrder().size
        val left = width - SLOT_BAR_RIGHT_MARGIN - SLOT_TEXTURE_SIZE * slotCount
        val right = width - SLOT_BAR_RIGHT_MARGIN
        return mouseX in left until right && mouseY in SLOT_BAR_Y until (SLOT_BAR_Y + SLOT_TEXTURE_SIZE)
    }

    private fun attachmentPanelLeft(): Int = width - UI_MARGIN - ATTACHMENT_PANEL_WIDTH
    private fun attachmentPanelRight(): Int = width - UI_MARGIN
    private fun attachmentPanelTop(): Int = ATTACH_PANEL_TOP
    private fun attachmentPanelBottom(): Int = height - ATTACH_PANEL_BOTTOM_MARGIN

    private fun changeAttachmentPage(delta: Int) {
        val maxPage = maxAttachmentPage(availableAttachmentEntries(selectedSlot).size)
        val newPage = (attachmentPage + delta).coerceIn(0, maxPage)
        if (newPage != attachmentPage) {
            attachmentPage = newPage
            initGui()
        }
    }

    private fun cycleSlot(delta: Int) {
        val slots = slotDisplayOrder().toTypedArray()
        val index = slots.indexOf(selectedSlot)
        if (index < 0) {
            return
        }
        val size = slots.size
        val newIndex = floorMod(index + delta, size)
        if (newIndex != index) {
            selectedSlot = slots[newIndex]
            attachmentPage = 0
            WeaponGunsmithImmersiveRuntime.setModelFocusSlot(selectedSlot)
            initGui()
        }
    }

    private fun floorMod(value: Int, mod: Int): Int {
        val r = value % mod
        return if (r < 0) r + mod else r
    }

    private fun captureAndApplyViewFocusLock(context: WorkbenchContext?) {
        if (context == null) {
            return
        }

        if (originalThirdPersonView == null) {
            originalThirdPersonView = mc.gameSettings.thirdPersonView
        }
        mc.gameSettings.thirdPersonView = 0

        val player = mc.player ?: return
        if (lockedYaw == null || lockedPitch == null) {
            lockedYaw = player.rotationYaw
            lockedPitch = player.rotationPitch
        }
        enforceViewAndInputFocusLock()
    }

    private fun enforceViewAndInputFocusLock() {
        val player = mc.player
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

    private fun notifyServerWorkbenchSessionClosed() {
        if (workbenchClosePacketSent) {
            return
        }
        LegacyNetworkHandler.sendWeaponWorkbenchSessionCloseToServer()
        workbenchClosePacketSent = true
    }

    private fun findHoveredButton(mouseX: Int, mouseY: Int): GuiButton? {
        return buttonList.firstOrNull { button ->
            button.visible &&
                mouseX >= button.x && mouseX < button.x + button.width &&
                mouseY >= button.y && mouseY < button.y + button.height
        }
    }

    private fun candidateDisplayName(item: LegacyAttachmentItem): String {
        return ItemStack(item).displayName.ifBlank { item.attachmentId.substringAfter(':') }
    }

    private fun maxAttachmentPage(total: Int): Int {
        if (total <= 0) {
            return 0
        }
        return (total - 1) / CANDIDATES_PER_PAGE
    }

    private fun formatNumber(value: Float): String {
        val rounded2 = String.format(java.util.Locale.ROOT, "%.2f", value)
        return rounded2
            .replace(Regex("\\.00$"), "")
            .replace(Regex("(\\.[0-9])0$"), "$1")
    }

    private fun addButtonWithAction(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        label: String,
        action: ButtonAction,
        align: FlatTextButton.Align = FlatTextButton.Align.CENTER
    ): Int {
        val id = nextButtonId++
        buttonList.add(
            FlatTextButton(
                buttonId = id,
                x = x,
                y = y,
                widthIn = width,
                heightIn = height,
                buttonText = label,
                align = align
            )
        )
        actionByButtonId[id] = action
        return id
    }

    private data class WorkbenchContext(
        val gunId: String,
        val stack: ItemStack,
        val definition: WeaponDefinition,
        val attachmentSnapshot: WeaponAttachmentSnapshot
    )

    private data class ComputedStats(
        val magazineCapacity: Float,
        val reloadSeconds: Float,
        val bulletSpeedMps: Float,
        val damage: Float,
        val armorIgnore: Float,
        val headShotMultiplier: Float,
        val standInaccuracy: Float,
        val aimInaccuracy: Float,
        val knockback: Float
    )

    private data class StatRow(
        val label: String,
        val base: Float,
        val current: Float,
        val maxValue: Float,
        val lowerIsBetter: Boolean,
        val unit: String
    )

    private sealed class ButtonAction {
        object ToggleStatsPanel : ButtonAction()
        object OpenDebugWorkbench : ButtonAction()
        object CloseWorkbench : ButtonAction()
        data class SelectSlot(val slot: WeaponAttachmentSlot) : ButtonAction()
        data class ClearSlot(val slot: WeaponAttachmentSlot) : ButtonAction()
        data class ChangePage(val delta: Int) : ButtonAction()
        data class Install(val item: LegacyAttachmentItem) : ButtonAction()
    }

    private companion object {
        private const val CANDIDATES_PER_PAGE: Int = 12
        private const val LEFT_MOUSE_BUTTON: Int = 0

        private const val TACZ_WORKBENCH_TOTAL_WIDTH: Int = 344
        private const val TACZ_WORKBENCH_TOTAL_HEIGHT: Int = 187
        private const val TACZ_WORKBENCH_SIDE_WIDTH: Int = 134
        private const val TACZ_WORKBENCH_MAIN_WIDTH: Int = 208
        private const val TACZ_WORKBENCH_MAIN_HEIGHT: Int = 160
        private const val TACZ_WORKBENCH_MAIN_OFFSET_X: Int = 136
        private const val TACZ_WORKBENCH_MAIN_OFFSET_Y: Int = 27

        private const val SLOT_TEXTURE_SIZE: Int = 18
        private const val SLOT_ICON_UV_SIZE: Int = 32
        private const val SLOT_ICON_DRAW_SIZE: Int = 14
        private const val SLOT_ICON_TEXTURE_WIDTH: Int = SLOT_ICON_UV_SIZE * 7

        private const val UI_MARGIN: Int = 16
        private const val TOP_BAR_TOP: Int = 16
        private const val TOP_BAR_BOTTOM: Int = 42
        private const val SLOT_BAR_Y: Int = 10
        private const val SLOT_BAR_RIGHT_MARGIN: Int = 12
        private const val ATTACH_PANEL_TOP: Int = 46
        private const val ATTACH_PANEL_BOTTOM_MARGIN: Int = 24

        private val TACZ_WORKBENCH_MAIN_TEXTURE: ResourceLocation =
            ResourceLocation("tacz", "textures/gui/gun_smith_table.png")
        private val TACZ_WORKBENCH_SIDE_TEXTURE: ResourceLocation =
            ResourceLocation("tacz", "textures/gui/gun_smith_table_side.png")
        private val TACZ_REFIT_SLOT_TEXTURE: ResourceLocation =
            ResourceLocation("tacz", "textures/gui/refit_slot.png")
        private val TACZ_REFIT_SLOT_ICONS_TEXTURE: ResourceLocation =
            ResourceLocation("tacz", "textures/gui/refit_slot_icons.png")

        // 让枪模留出更多可视面积：右侧候选面板更窄，左侧图表面板为固定宽度。
        private const val ATTACHMENT_PANEL_WIDTH: Int = 220
        private const val STATS_PANEL_WIDTH: Int = 320
    }
}
