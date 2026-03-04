package com.tacz.legacy.client.gui

import com.tacz.legacy.common.application.weapon.WeaponRuntime
import com.tacz.legacy.common.infrastructure.mc.network.LegacyNetworkHandler
import com.tacz.legacy.common.infrastructure.mc.registry.item.LegacyAttachmentItem
import com.tacz.legacy.common.infrastructure.mc.registry.item.LegacyGunItem
import com.tacz.legacy.common.infrastructure.mc.weapon.WeaponAttachmentSlot
import com.tacz.legacy.common.infrastructure.mc.weapon.WeaponItemStackRuntimeData
import net.minecraft.client.gui.Gui
import net.minecraft.client.gui.GuiButton
import net.minecraft.client.gui.GuiScreen
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation
import net.minecraftforge.fml.common.registry.ForgeRegistries

public class WeaponDebugWorkbenchGuiScreen : GuiScreen() {

    private var nextButtonId: Int = 1
    private val actionByButtonId: MutableMap<Int, ButtonAction> = linkedMapOf()
    private var attachmentPage: Int = 0

    override fun initGui() {
        super.initGui()
        buttonList.clear()
        actionByButtonId.clear()
        nextButtonId = 1

        val allAttachments = availableAttachmentEntries()
        val maxPage = maxAttachmentPage(allAttachments.size)
        attachmentPage = attachmentPage.coerceIn(0, maxPage)

        val left = width / 2 - PANEL_HALF_WIDTH
        var y = 26

        addButtonWithAction(
            x = left,
            y = y,
            width = 80,
            height = 20,
            label = "上一页",
            action = ButtonAction.ChangePage(-1)
        )
        addButtonWithAction(
            x = left + 86,
            y = y,
            width = 80,
            height = 20,
            label = "下一页",
            action = ButtonAction.ChangePage(1)
        )
        y += 24

        WeaponAttachmentSlot.values().forEach { slot ->
            addButtonWithAction(
                x = left,
                y = y,
                width = 166,
                height = 20,
                label = "卸下 ${slot.name}",
                action = ButtonAction.ClearAttachment(slot)
            )
            y += 22
        }

        y += 4
        val pagedAttachments = allAttachments
            .drop(attachmentPage * ATTACHMENTS_PER_PAGE)
            .take(ATTACHMENTS_PER_PAGE)

        pagedAttachments.forEachIndexed { index, item ->
            val column = index % 2
            val row = index / 2
            val x = left + column * 170
            val rowY = y + row * 22
            val shortName = item.attachmentId.substringAfter(':')
            addButtonWithAction(
                x = x,
                y = rowY,
                width = 166,
                height = 20,
                label = "装配 ${item.slot.name}: $shortName",
                action = ButtonAction.InstallAttachment(item.slot, item.attachmentId)
            )
        }

        val attachmentRows = (pagedAttachments.size + 1) / 2
        val ammoY = y + attachmentRows * 22 + 10

        addButtonWithAction(left, ammoY, 80, 20, "弹匣-10", ButtonAction.AdjustMagazine(-10))
        addButtonWithAction(left + 84, ammoY, 80, 20, "弹匣-1", ButtonAction.AdjustMagazine(-1))
        addButtonWithAction(left + 168, ammoY, 80, 20, "弹匣+1", ButtonAction.AdjustMagazine(1))
        addButtonWithAction(left + 252, ammoY, 80, 20, "弹匣+10", ButtonAction.AdjustMagazine(10))

        addButtonWithAction(left, ammoY + 22, 80, 20, "备弹-30", ButtonAction.AdjustReserve(-30))
        addButtonWithAction(left + 84, ammoY + 22, 80, 20, "备弹-1", ButtonAction.AdjustReserve(-1))
        addButtonWithAction(left + 168, ammoY + 22, 80, 20, "备弹+1", ButtonAction.AdjustReserve(1))
        addButtonWithAction(left + 252, ammoY + 22, 80, 20, "备弹+30", ButtonAction.AdjustReserve(30))

        addButtonWithAction(left, ammoY + 46, 166, 20, "补满弹匣", ButtonAction.RefillMagazine)
        addButtonWithAction(left + 170, ammoY + 46, 166, 20, "补满全部弹药", ButtonAction.RefillAllAmmo)
    }

    override fun actionPerformed(button: GuiButton) {
        val action = actionByButtonId[button.id] ?: return
        when (action) {
            is ButtonAction.ChangePage -> {
                val allAttachments = availableAttachmentEntries()
                val maxPage = maxAttachmentPage(allAttachments.size)
                attachmentPage = (attachmentPage + action.delta).coerceIn(0, maxPage)
                initGui()
            }

            is ButtonAction.InstallAttachment -> {
                LegacyNetworkHandler.sendWeaponAttachmentInstallToServer(
                    slot = action.slot,
                    attachmentId = action.attachmentId
                )
            }

            is ButtonAction.ClearAttachment -> {
                LegacyNetworkHandler.sendWeaponAttachmentClearToServer(action.slot)
            }

            is ButtonAction.AdjustMagazine -> {
                val context = resolveCurrentContext() ?: return
                val targetMagazine = (context.currentMagazine + action.delta)
                    .coerceIn(0, context.maxMagazine)
                LegacyNetworkHandler.sendWeaponAmmoDebugStateToServer(
                    ammoInMagazine = targetMagazine,
                    ammoReserve = context.currentReserve
                )
            }

            is ButtonAction.AdjustReserve -> {
                val context = resolveCurrentContext() ?: return
                val targetReserve = (context.currentReserve + action.delta).coerceAtLeast(0)
                LegacyNetworkHandler.sendWeaponAmmoDebugStateToServer(
                    ammoInMagazine = context.currentMagazine,
                    ammoReserve = targetReserve
                )
            }

            ButtonAction.RefillMagazine -> {
                val context = resolveCurrentContext() ?: return
                LegacyNetworkHandler.sendWeaponAmmoDebugStateToServer(
                    ammoInMagazine = context.maxMagazine,
                    ammoReserve = context.currentReserve
                )
            }

            ButtonAction.RefillAllAmmo -> {
                val context = resolveCurrentContext() ?: return
                LegacyNetworkHandler.sendWeaponAmmoDebugStateToServer(
                    ammoInMagazine = context.maxMagazine,
                    ammoReserve = CREATIVE_DEBUG_RESERVE
                )
            }
        }
    }

    override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
        drawDefaultBackground()
        drawTaczWorkbenchBackdrop()
        drawCenteredString(fontRenderer, "TACZ-Legacy 武器调试台", width / 2, 8, 0xFFFFFF)

        val context = resolveCurrentContext()
        val allAttachments = availableAttachmentEntries()
        val maxPage = maxAttachmentPage(allAttachments.size)

        if (context == null) {
            drawCenteredString(
                fontRenderer,
                "请手持 TACZ 枪械（主手或副手）",
                width / 2,
                20,
                0xFF6666
            )
        } else {
            val gunInfo = "枪械: ${context.gunId} | 弹匣: ${context.currentMagazine}/${context.maxMagazine} | 备弹: ${context.currentReserve}"
            drawCenteredString(fontRenderer, gunInfo, width / 2, 20, 0xB0E0FF)

            val attachmentSummary = WeaponItemStackRuntimeData.readAttachmentSnapshot(context.stack).hudSummaryText()
            drawCenteredString(fontRenderer, attachmentSummary, width / 2, 32, 0xCCFFCC)

            if (mc.player?.capabilities?.isCreativeMode == true) {
                drawCenteredString(fontRenderer, "创意模式：仅备弹无限（弹匣不自动补满）", width / 2, 44, 0xFFE680)
            }
        }

        val pageInfo = "配件页 ${attachmentPage + 1}/${maxPage + 1}（共 ${allAttachments.size} 项）"
        drawCenteredString(fontRenderer, pageInfo, width / 2, 56, 0xAAAAAA)

        super.drawScreen(mouseX, mouseY, partialTicks)
    }

    private fun drawTaczWorkbenchBackdrop() {
        val originX = ((width - TACZ_WORKBENCH_TOTAL_WIDTH) / 2).coerceAtLeast(8)
        val originY = ((height - TACZ_WORKBENCH_TOTAL_HEIGHT) / 2).coerceAtLeast(12)

        drawTextureRegion(
            texture = TACZ_WORKBENCH_SIDE_TEXTURE,
            x = originX,
            y = originY,
            width = TACZ_WORKBENCH_SIDE_WIDTH,
            height = TACZ_WORKBENCH_TOTAL_HEIGHT,
            textureWidth = TACZ_WORKBENCH_SIDE_WIDTH.toFloat(),
            textureHeight = TACZ_WORKBENCH_TOTAL_HEIGHT.toFloat(),
            alpha = 0.24f
        )
        drawTextureRegion(
            texture = TACZ_WORKBENCH_MAIN_TEXTURE,
            x = originX + TACZ_WORKBENCH_MAIN_OFFSET_X,
            y = originY + TACZ_WORKBENCH_MAIN_OFFSET_Y,
            width = TACZ_WORKBENCH_MAIN_WIDTH,
            height = TACZ_WORKBENCH_MAIN_HEIGHT,
            textureWidth = TACZ_WORKBENCH_MAIN_WIDTH.toFloat(),
            textureHeight = TACZ_WORKBENCH_MAIN_HEIGHT.toFloat(),
            alpha = 0.2f
        )
    }

    private fun drawTextureRegion(
        texture: ResourceLocation,
        x: Int,
        y: Int,
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
            0f,
            0f,
            width,
            height,
            textureWidth,
            textureHeight
        )
        GlStateManager.color(1f, 1f, 1f, 1f)
    }

    override fun doesGuiPauseGame(): Boolean = false

    private fun addButtonWithAction(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        label: String,
        action: ButtonAction
    ) {
        val id = nextButtonId++
        buttonList.add(GuiButton(id, x, y, width, height, label))
        actionByButtonId[id] = action
    }

    private fun availableAttachmentEntries(): List<LegacyAttachmentItem> {
        return ForgeRegistries.ITEMS.valuesCollection
            .asSequence()
            .mapNotNull { it as? LegacyAttachmentItem }
            .sortedWith(compareBy({ it.slot.ordinal }, { it.attachmentId }))
            .toList()
    }

    private fun resolveCurrentContext(): WeaponDebugContext? {
        val player = mc.player ?: return null
        val stack = resolveHeldGunStack(player.heldItemMainhand, player.heldItemOffhand) ?: return null
        val gunId = stack.item.registryName
            ?.toString()
            ?.substringAfter(':')
            ?.trim()
            ?.lowercase()
            ?.ifBlank { null }
            ?: return null

        val definition = WeaponRuntime.registry().snapshot().findDefinition(gunId)
        val maxMagazine = definition?.spec?.magazineSize?.coerceAtLeast(1) ?: DEFAULT_MAGAZINE_SIZE
        val currentMagazine = WeaponItemStackRuntimeData.readAmmoInMagazine(stack, maxMagazine)
            .coerceIn(0, maxMagazine)
        val currentReserve = WeaponItemStackRuntimeData.readAmmoReserve(stack, 0).coerceAtLeast(0)

        return WeaponDebugContext(
            stack = stack,
            gunId = gunId,
            maxMagazine = maxMagazine,
            currentMagazine = currentMagazine,
            currentReserve = currentReserve
        )
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

    private fun maxAttachmentPage(total: Int): Int {
        if (total <= 0) {
            return 0
        }
        return (total - 1) / ATTACHMENTS_PER_PAGE
    }

    private data class WeaponDebugContext(
        val stack: ItemStack,
        val gunId: String,
        val maxMagazine: Int,
        val currentMagazine: Int,
        val currentReserve: Int
    )

    private sealed class ButtonAction {
        data class ChangePage(val delta: Int) : ButtonAction()
        data class InstallAttachment(val slot: WeaponAttachmentSlot, val attachmentId: String) : ButtonAction()
        data class ClearAttachment(val slot: WeaponAttachmentSlot) : ButtonAction()
        data class AdjustMagazine(val delta: Int) : ButtonAction()
        data class AdjustReserve(val delta: Int) : ButtonAction()
        object RefillMagazine : ButtonAction()
        object RefillAllAmmo : ButtonAction()
    }

    private companion object {
        private const val PANEL_HALF_WIDTH: Int = 170
        private const val ATTACHMENTS_PER_PAGE: Int = 10
        private const val DEFAULT_MAGAZINE_SIZE: Int = 30
        private const val CREATIVE_DEBUG_RESERVE: Int = 9999

        private const val TACZ_WORKBENCH_TOTAL_WIDTH: Int = 344
        private const val TACZ_WORKBENCH_TOTAL_HEIGHT: Int = 187
        private const val TACZ_WORKBENCH_SIDE_WIDTH: Int = 134
        private const val TACZ_WORKBENCH_MAIN_WIDTH: Int = 208
        private const val TACZ_WORKBENCH_MAIN_HEIGHT: Int = 160
        private const val TACZ_WORKBENCH_MAIN_OFFSET_X: Int = 136
        private const val TACZ_WORKBENCH_MAIN_OFFSET_Y: Int = 27

        private val TACZ_WORKBENCH_MAIN_TEXTURE: ResourceLocation =
            ResourceLocation("tacz", "textures/gui/gun_smith_table.png")
        private val TACZ_WORKBENCH_SIDE_TEXTURE: ResourceLocation =
            ResourceLocation("tacz", "textures/gui/gun_smith_table_side.png")
    }
}
