package com.tacz.legacy.client.gui

import com.tacz.legacy.TACZLegacy
import com.tacz.legacy.common.application.gunsmith.LegacyGunSmithIngredient
import com.tacz.legacy.common.application.gunsmith.LegacyGunSmithRecipe
import com.tacz.legacy.common.application.gunsmith.LegacyGunSmithTab
import com.tacz.legacy.common.application.gunsmith.LegacyGunSmithingRuntime
import com.tacz.legacy.common.inventory.GunSmithTableContainer
import com.tacz.legacy.common.network.TACZNetworkHandler
import com.tacz.legacy.common.network.message.client.ClientMessageGunSmithCraft
import com.tacz.legacy.common.resource.TACZGunPackRuntimeRegistry
import net.minecraft.client.gui.GuiButton
import net.minecraft.client.gui.GuiConfirmOpenLink
import net.minecraft.client.gui.GuiTextField
import net.minecraft.client.gui.inventory.GuiContainer
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.RenderHelper
import net.minecraft.client.resources.I18n
import net.minecraft.entity.player.InventoryPlayer
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation
import net.minecraft.util.text.TextFormatting
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly
import org.lwjgl.input.Keyboard
import org.lwjgl.input.Mouse
import java.awt.Desktop
import java.net.URI
import java.util.Locale

@SideOnly(Side.CLIENT)
internal class GunSmithTableScreen(
    private val menu: GunSmithTableContainer,
    playerInventory: InventoryPlayer,
) : GuiContainer(menu) {
    private val blockId: ResourceLocation = menu.blockId
    private var typePage: Int = 0
    private var selectedType: ResourceLocation? = null
    private var selectedRecipeList: List<LegacyGunSmithRecipe> = emptyList()
    private var indexPage: Int = 0
    private var selectedRecipe: LegacyGunSmithRecipe? = null
    private var ingredientCounts: MutableMap<Int, Int> = linkedMapOf()

    private var previewScale: Int = 70
    private var filterPanelVisible: Boolean = false
    private var byHandOnly: Boolean = false
    private var packPage: Int = 0
    private var namespaceSelectionInitialized: Boolean = false
    private var pendingUrl: String? = null

    private var allNamespaces: List<String> = emptyList()
    private val selectedNamespaces: LinkedHashSet<String> = linkedSetOf()
    private var visibleTabs: List<LegacyGunSmithTab> = emptyList()

    private lateinit var searchField: GuiTextField
    private val tabButtons: MutableList<TabButton> = mutableListOf()
    private val resultButtons: MutableList<ResultButton> = mutableListOf()

    init {
        xSize = 344
        ySize = 186
    }

    override fun initGui() {
        val previousSearch = if (this::searchField.isInitialized) searchField.text else ""
        super.initGui()
        Keyboard.enableRepeatEvents(true)
        searchField = GuiTextField(SEARCH_FIELD_ID, fontRenderer, guiLeft + 6, guiTop + 20, 122, 14).apply {
            maxStringLength = 64
            text = previousSearch
            isFocused = filterPanelVisible
            enableBackgroundDrawing = true
        }
        allNamespaces = LegacyGunSmithingRuntime.recipeNamespacesForBlock(blockId)
        if (!namespaceSelectionInitialized) {
            selectedNamespaces.clear()
            selectedNamespaces.addAll(allNamespaces)
            namespaceSelectionInitialized = true
        } else {
            selectedNamespaces.retainAll(allNamespaces.toSet())
        }
        refreshRecipeState(preserveSelection = true)
        rebuildWidgets()
    }

    override fun onGuiClosed() {
        Keyboard.enableRepeatEvents(false)
        super.onGuiClosed()
    }

    override fun updateScreen() {
        super.updateScreen()
        searchField.updateCursorCounter()
        updateIngredientCounts()
    }

    override fun handleMouseInput() {
        super.handleMouseInput()
        val wheel = Mouse.getEventDWheel()
        if (wheel == 0) {
            return
        }
        val mouseX = Mouse.getEventX() * width / mc.displayWidth
        val mouseY = height - Mouse.getEventY() * height / mc.displayHeight - 1
        if (isInsideResultArea(mouseX, mouseY)) {
            scrollResults(wheel)
        } else if (filterPanelVisible && isInsidePackArea(mouseX, mouseY)) {
            scrollPacks(wheel)
        }
    }

    override fun keyTyped(typedChar: Char, keyCode: Int) {
        if (searchField.textboxKeyTyped(typedChar, keyCode)) {
            refreshRecipeState(preserveSelection = true)
            rebuildWidgets()
            return
        }
        super.keyTyped(typedChar, keyCode)
    }

    override fun mouseClicked(mouseX: Int, mouseY: Int, mouseButton: Int) {
        searchField.mouseClicked(mouseX, mouseY, mouseButton)
        super.mouseClicked(mouseX, mouseY, mouseButton)
    }

    override fun actionPerformed(button: GuiButton) {
        when {
            button.id == BUTTON_FILTER -> {
                filterPanelVisible = !filterPanelVisible
                initGui()
            }
            button.id == BUTTON_CRAFT -> tryCraft()
            button.id == BUTTON_TYPE_PREV -> {
                if (typePage > 0) {
                    typePage--
                    rebuildWidgets()
                }
            }
            button.id == BUTTON_TYPE_NEXT -> {
                val maxPage = ((visibleTabs.size - 1).coerceAtLeast(0)) / TABS_PER_PAGE
                if (typePage < maxPage) {
                    typePage++
                    rebuildWidgets()
                }
            }
            button.id == BUTTON_RESULT_PREV -> {
                if (indexPage > 0) {
                    indexPage--
                    rebuildWidgets()
                }
            }
            button.id == BUTTON_RESULT_NEXT -> {
                val maxPage = ((selectedRecipeList.size - 1).coerceAtLeast(0)) / RESULTS_PER_PAGE
                if (indexPage < maxPage) {
                    indexPage++
                    rebuildWidgets()
                }
            }
            button.id == BUTTON_SCALE_UP -> previewScale = (previewScale + 20).coerceAtMost(200)
            button.id == BUTTON_SCALE_DOWN -> previewScale = (previewScale - 20).coerceAtLeast(10)
            button.id == BUTTON_SCALE_RESET -> previewScale = 70
            button.id == BUTTON_BY_HAND -> {
                byHandOnly = !byHandOnly
                refreshRecipeState(preserveSelection = true)
                rebuildWidgets()
            }
            button.id == BUTTON_SELECT_ALL -> {
                if (selectedNamespaces.size == allNamespaces.size) {
                    selectedNamespaces.clear()
                } else {
                    selectedNamespaces.clear()
                    selectedNamespaces.addAll(allNamespaces)
                }
                packPage = 0
                refreshRecipeState(preserveSelection = true)
                rebuildWidgets()
            }
            button.id == BUTTON_PACK_PREV -> {
                if (packPage > 0) {
                    packPage--
                    rebuildWidgets()
                }
            }
            button.id == BUTTON_PACK_NEXT -> {
                val maxPage = ((allNamespaces.size - 1).coerceAtLeast(0)) / PACKS_PER_PAGE
                if (packPage < maxPage) {
                    packPage++
                    rebuildWidgets()
                }
            }
            button.id == BUTTON_URL -> openSelectedPackUrl()
            button.id in BUTTON_TAB_BASE until BUTTON_RESULT_BASE -> {
                val index = button.id - BUTTON_TAB_BASE + typePage * TABS_PER_PAGE
                val tab = visibleTabs.getOrNull(index) ?: return
                selectedType = tab.id
                indexPage = 0
                selectedRecipe = null
                refreshRecipeState(preserveSelection = false)
                rebuildWidgets()
            }
            button.id in BUTTON_RESULT_BASE until BUTTON_PACK_BASE -> {
                val index = button.id - BUTTON_RESULT_BASE + indexPage * RESULTS_PER_PAGE
                val recipe = selectedRecipeList.getOrNull(index) ?: return
                selectedRecipe = recipe
                updateIngredientCounts()
                rebuildWidgets()
            }
            button.id in BUTTON_PACK_BASE until BUTTON_PACK_BASE + PACKS_PER_PAGE -> {
                val index = button.id - BUTTON_PACK_BASE + packPage * PACKS_PER_PAGE
                val namespace = allNamespaces.getOrNull(index) ?: return
                if (!selectedNamespaces.add(namespace)) {
                    selectedNamespaces.remove(namespace)
                }
                refreshRecipeState(preserveSelection = true)
                rebuildWidgets()
            }
        }
    }

    override fun confirmClicked(result: Boolean, id: Int) {
        if (id == URL_CONFIRM_ID) {
            val url = pendingUrl
            pendingUrl = null
            if (result && !url.isNullOrBlank()) {
                runCatching { Desktop.getDesktop().browse(URI(url)) }
                    .onFailure { throwable -> TACZLegacy.logger.warn("Failed to open pack url {}", url, throwable) }
            }
            mc.displayGuiScreen(this)
            return
        }
        super.confirmClicked(result, id)
    }

    override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
        drawDefaultBackground()
        super.drawScreen(mouseX, mouseY, partialTicks)
        searchField.drawTextBox()
        renderHoveredTooltips(mouseX, mouseY)
    }

    override fun drawGuiContainerBackgroundLayer(partialTicks: Float, mouseX: Int, mouseY: Int) {
        GlStateManager.color(1f, 1f, 1f, 1f)
        mc.textureManager.bindTexture(SIDE_TEXTURE)
        drawTexturedModalRect(guiLeft, guiTop, 0, 0, 134, 187)
        mc.textureManager.bindTexture(TEXTURE)
        drawTexturedModalRect(guiLeft + 136, guiTop + 27, 0, 0, 208, 160)

        if (!filterPanelVisible) {
            renderPreviewItem()
            renderPackInfo()
        } else {
            drawPackFilterPanel()
        }
        renderIngredients()
    }

    override fun drawGuiContainerForegroundLayer(mouseX: Int, mouseY: Int) {
        fontRenderer.drawString(I18n.format("gui.tacz.gun_smith_table.preview"), 54, 6, TITLE_COLOR)
        selectedType?.let { typeId ->
            visibleTabs.firstOrNull { it.id == typeId }?.let { tab ->
                fontRenderer.drawString(tab.displayName, 150, 32, TITLE_COLOR)
            }
        }
        fontRenderer.drawString(I18n.format("gui.tacz.gun_smith_table.ingredient"), 254, 50, TITLE_COLOR)
        fontRenderer.drawString(I18n.format("gui.tacz.gun_smith_table.craft"), 296, 167, 0xFFFFFF)

        if (selectedRecipe == null) {
            fontRenderer.drawString(NO_RECIPE_TEXT, 151, 67, 0xAA4444)
        } else if (!filterPanelVisible) {
            fontRenderer.drawString(
                I18n.format("gui.tacz.gun_smith_table.count", selectedRecipe!!.result.count),
                254,
                140,
                TITLE_COLOR,
            )
        }
    }

    private fun refreshRecipeState(preserveSelection: Boolean) {
        visibleTabs = LegacyGunSmithingRuntime.tabsForBlock(blockId)
        val previousType = if (preserveSelection) selectedType else null
        val previousRecipeId = if (preserveSelection) selectedRecipe?.id else null
        val recipes = if (allNamespaces.isNotEmpty() && selectedNamespaces.isEmpty()) {
            emptyList()
        } else {
            val namespaceFilter = if (selectedNamespaces.size == allNamespaces.size) emptySet() else selectedNamespaces.toSet()
            LegacyGunSmithingRuntime.visibleRecipes(
                blockId = blockId,
                selectedTab = null,
                selectedNamespaces = namespaceFilter,
                searchText = searchField.text,
                heldStack = mc.player?.heldItemMainhand ?: ItemStack.EMPTY,
                byHandOnly = byHandOnly,
            )
        }
        val recipeGroups = recipes.groupBy(LegacyGunSmithRecipe::group)
        visibleTabs = visibleTabs.filter { tab -> recipeGroups[tab.id]?.isNotEmpty() == true }
        if (visibleTabs.isEmpty()) {
            selectedType = null
            selectedRecipeList = emptyList()
            selectedRecipe = null
            ingredientCounts.clear()
            typePage = 0
            indexPage = 0
            return
        }
        if (previousType != null && visibleTabs.any { it.id == previousType }) {
            selectedType = previousType
        } else {
            selectedType = visibleTabs.first().id
        }
        selectedRecipeList = recipeGroups[selectedType].orEmpty()
        val maxIndexPage = ((selectedRecipeList.size - 1).coerceAtLeast(0)) / RESULTS_PER_PAGE
        if (indexPage > maxIndexPage) {
            indexPage = maxIndexPage
        }
        selectedRecipe = previousRecipeId?.let { id -> selectedRecipeList.firstOrNull { it.id == id } } ?: selectedRecipeList.firstOrNull()
        val maxTypePage = ((visibleTabs.size - 1).coerceAtLeast(0)) / TABS_PER_PAGE
        if (typePage > maxTypePage) {
            typePage = maxTypePage
        }
        updateIngredientCounts()
    }

    private fun rebuildWidgets() {
        buttonList.clear()
        labelList.clear()
        tabButtons.clear()
        resultButtons.clear()

        addButton(GuiButton(BUTTON_FILTER, guiLeft - 10, guiTop, 12, 12, "F"))
        addButton(GuiButton(BUTTON_TYPE_PREV, guiLeft + 136, guiTop + 3, 18, 20, "<"))
        addButton(GuiButton(BUTTON_TYPE_NEXT, guiLeft + 327, guiTop + 3, 18, 20, ">"))
        addButton(GuiButton(BUTTON_RESULT_PREV, guiLeft + 143, guiTop + 55, 44, 12, "˄"))
        addButton(GuiButton(BUTTON_RESULT_NEXT, guiLeft + 193, guiTop + 55, 44, 12, "˅"))
        addButton(GuiButton(BUTTON_CRAFT, guiLeft + 289, guiTop + 160, 48, 20, I18n.format("gui.tacz.gun_smith_table.craft")))

        if (filterPanelVisible) {
            addButton(GuiButton(BUTTON_BY_HAND, guiLeft + 6, guiTop + 40, 58, 14, toggleLabel("By hand", byHandOnly)))
            addButton(
                GuiButton(
                    BUTTON_SELECT_ALL,
                    guiLeft + 68,
                    guiTop + 40,
                    60,
                    14,
                    if (selectedNamespaces.size == allNamespaces.size) "Clear" else "All",
                ),
            )
            if (allNamespaces.size > PACKS_PER_PAGE) {
                addButton(GuiButton(BUTTON_PACK_PREV, guiLeft + 6, guiTop + 56, 20, 12, "<"))
                addButton(GuiButton(BUTTON_PACK_NEXT, guiLeft + 108, guiTop + 56, 20, 12, ">"))
            }
            addPackButtons()
        } else {
            addButton(GuiButton(BUTTON_SCALE_UP, guiLeft + 5, guiTop + 5, 14, 14, "+"))
            addButton(GuiButton(BUTTON_SCALE_DOWN, guiLeft + 21, guiTop + 5, 14, 14, "-"))
            addButton(GuiButton(BUTTON_SCALE_RESET, guiLeft + 37, guiTop + 5, 18, 14, "R"))
            val hasUrl = selectedRecipe?.let { recipe ->
                TACZGunPackRuntimeRegistry.getSnapshot().packInfos[recipe.sourceNamespace]?.url?.isNotBlank() == true
            } ?: false
            if (hasUrl) {
                addButton(GuiButton(BUTTON_URL, guiLeft + 103, guiTop + 164, 26, 18, "URL"))
            }
        }

        val visibleTabSlice = visibleTabs.drop(typePage * TABS_PER_PAGE).take(TABS_PER_PAGE)
        visibleTabSlice.forEachIndexed { index, tab ->
            val button = TabButton(
                id = BUTTON_TAB_BASE + index,
                x = guiLeft + 157 + 24 * index,
                y = guiTop + 2,
                tab = tab,
                selected = selectedType == tab.id,
            )
            tabButtons += button
            addButton(button)
        }

        val visibleResultSlice = selectedRecipeList.drop(indexPage * RESULTS_PER_PAGE).take(RESULTS_PER_PAGE)
        visibleResultSlice.forEachIndexed { index, recipe ->
            val button = ResultButton(
                id = BUTTON_RESULT_BASE + index,
                x = guiLeft + 144,
                y = guiTop + 66 + 17 * index,
                recipe = recipe,
                selected = selectedRecipe?.id == recipe.id,
            )
            resultButtons += button
            addButton(button)
        }
    }

    private fun addPackButtons() {
        val visibleNamespaces = allNamespaces.drop(packPage * PACKS_PER_PAGE).take(PACKS_PER_PAGE)
        visibleNamespaces.forEachIndexed { index, namespace ->
            val packName = namespaceDisplayName(namespace)
            addButton(
                GuiButton(
                    BUTTON_PACK_BASE + index,
                    guiLeft + 6,
                    guiTop + 70 + 14 * index,
                    122,
                    12,
                    toggleLabel(packName, namespace in selectedNamespaces),
                ),
            )
        }
    }

    private fun updateIngredientCounts() {
        val recipe = selectedRecipe ?: run {
            ingredientCounts.clear()
            return
        }
        ingredientCounts.clear()
        recipe.materials.forEachIndexed { index, ingredient ->
            ingredientCounts[index] = LegacyGunSmithingRuntime.ingredientCount(mc.player ?: return@forEachIndexed, ingredient)
        }
    }

    private fun tryCraft() {
        val recipe = selectedRecipe ?: return
        val player = mc.player ?: return
        if (!LegacyGunSmithingRuntime.canCraft(player, recipe)) {
            return
        }
        TACZNetworkHandler.sendToServer(ClientMessageGunSmithCraft(menu.windowId, recipe.id))
    }

    private fun openSelectedPackUrl() {
        val recipe = selectedRecipe ?: return
        val url = TACZGunPackRuntimeRegistry.getSnapshot().packInfos[recipe.sourceNamespace]?.url ?: return
        if (url.isBlank()) {
            return
        }
        pendingUrl = url
        mc.displayGuiScreen(GuiConfirmOpenLink(this, url, URL_CONFIRM_ID, false))
    }

    private fun renderPreviewItem() {
        val recipe = selectedRecipe ?: return
        val scale = previewScale / 20f
        GlStateManager.pushMatrix()
        GlStateManager.translate((guiLeft + 60).toFloat(), (guiTop + 60).toFloat(), 250f)
        GlStateManager.scale(scale, scale, 1f)
        RenderHelper.enableGUIStandardItemLighting()
        itemRender.renderItemAndEffectIntoGUI(recipe.result, -8, -8)
        itemRender.renderItemOverlayIntoGUI(fontRenderer, recipe.result, -8, -8, null)
        RenderHelper.disableStandardItemLighting()
        GlStateManager.popMatrix()
    }

    private fun renderPackInfo() {
        val recipe = selectedRecipe ?: return
        val snapshot = TACZGunPackRuntimeRegistry.getSnapshot()
        val packInfo = snapshot.packInfos[recipe.sourceNamespace]
        val x = guiLeft + 6
        var y = guiTop + 120
        if (packInfo == null) {
            fontRenderer.drawString(I18n.format("gui.tacz.gun_smith_table.error"), x, y, 0xAF0000)
            return
        }
        fontRenderer.drawString(namespaceDisplayName(recipe.sourceNamespace), x, y, 0x555555)
        y += 10
        fontRenderer.drawString("v${packInfo.version}", x, y, 0x555555)
        y += 10
        val description = com.tacz.legacy.common.resource.TACZGunPackPresentation.localizedText(snapshot, packInfo.description)
            ?: packInfo.description
        if (description.isNotBlank()) {
            fontRenderer.listFormattedStringToWidth(description, 122).take(3).forEach { line ->
                fontRenderer.drawString(line, x, y, 0x555555)
                y += 10
            }
        }
        fontRenderer.drawString(
            I18n.format("gui.tacz.gun_smith_table.license") + packInfo.license,
            x,
            y,
            0x555555,
        )
        y += 10
        if (packInfo.authors.isNotEmpty()) {
            fontRenderer.drawString(
                I18n.format("gui.tacz.gun_smith_table.authors") + packInfo.authors.joinToString(", "),
                x,
                y,
                0x555555,
            )
            y += 10
        }
        fontRenderer.drawString(
            I18n.format("gui.tacz.gun_smith_table.date") + packInfo.date,
            x,
            y,
            0x555555,
        )
    }

    private fun drawPackFilterPanel() {
        drawRect(guiLeft + 4, guiTop + 18, guiLeft + 130, guiTop + 180, 0x44000000)
    }

    private fun renderIngredients() {
        val recipe = selectedRecipe ?: return
        recipe.materials.take(MAX_INGREDIENTS).forEachIndexed { index, ingredient ->
            val column = index % 2
            val row = index / 2
            val x = guiLeft + 254 + 45 * column
            val y = guiTop + 62 + 17 * row
            val matchingStacks = ingredient.ingredient.matchingStacks
            if (matchingStacks.isNotEmpty()) {
                val stack = matchingStacks[(System.currentTimeMillis() / 1_000L % matchingStacks.size.toLong()).toInt()].copy()
                itemRender.renderItemAndEffectIntoGUI(stack, x, y)
            }
            val need = ingredient.count
            val has = ingredientCounts[index] ?: 0
            val color = if (mc.player?.capabilities?.isCreativeMode == true || has >= need) 0xFFFFFF else 0xFF0000
            GlStateManager.pushMatrix()
            GlStateManager.translate(0f, 0f, 200f)
            GlStateManager.scale(0.5f, 0.5f, 1f)
            val label = if (mc.player?.capabilities?.isCreativeMode == true) "$need/∞" else "$need/$has"
            fontRenderer.drawString(label, (x + 17) * 2, (y + 10) * 2, color)
            GlStateManager.popMatrix()
        }
    }

    private fun renderHoveredTooltips(mouseX: Int, mouseY: Int) {
        tabButtons.firstOrNull { it.isMouseOver(mouseX, mouseY) }?.let { button ->
            drawHoveringText(listOf(button.tab.displayName), mouseX, mouseY)
            return
        }
        resultButtons.firstOrNull { it.isMouseOver(mouseX, mouseY) }?.let { button ->
            renderToolTip(button.recipe.result, mouseX, mouseY)
            return
        }
        selectedRecipe?.materials?.take(MAX_INGREDIENTS)?.forEachIndexed { index, ingredient ->
            val column = index % 2
            val row = index / 2
            val x = guiLeft + 254 + 45 * column
            val y = guiTop + 62 + 17 * row
            if (mouseX in x until (x + 16) && mouseY in y until (y + 16)) {
                val stacks = ingredient.ingredient.matchingStacks
                if (stacks.isNotEmpty()) {
                    renderToolTip(stacks.first(), mouseX, mouseY)
                } else {
                    drawHoveringText(listOf("Missing tag ingredient"), mouseX, mouseY)
                }
                return
            }
        }
    }

    private fun scrollResults(wheel: Int) {
        val maxPage = ((selectedRecipeList.size - 1).coerceAtLeast(0)) / RESULTS_PER_PAGE
        if (wheel > 0) {
            indexPage = (indexPage - 1).coerceAtLeast(0)
        } else if (wheel < 0) {
            indexPage = (indexPage + 1).coerceAtMost(maxPage)
        }
        rebuildWidgets()
    }

    private fun scrollPacks(wheel: Int) {
        val maxPage = ((allNamespaces.size - 1).coerceAtLeast(0)) / PACKS_PER_PAGE
        if (wheel > 0) {
            packPage = (packPage - 1).coerceAtLeast(0)
        } else if (wheel < 0) {
            packPage = (packPage + 1).coerceAtMost(maxPage)
        }
        rebuildWidgets()
    }

    private fun isInsideResultArea(mouseX: Int, mouseY: Int): Boolean =
        mouseX in (guiLeft + 143)..(guiLeft + 237) && mouseY in (guiTop + 66)..(guiTop + 151)

    private fun isInsidePackArea(mouseX: Int, mouseY: Int): Boolean =
        mouseX in (guiLeft + 6)..(guiLeft + 128) && mouseY in (guiTop + 70)..(guiTop + 168)

    private fun namespaceDisplayName(namespace: String): String {
        val snapshot = TACZGunPackRuntimeRegistry.getSnapshot()
        val key = snapshot.packInfos[namespace]?.name
        return com.tacz.legacy.common.resource.TACZGunPackPresentation.localizedText(snapshot, key)
            ?: namespace
    }

    private fun toggleLabel(label: String, selected: Boolean): String = if (selected) "§a✓ §r$label" else "§7☐ §r$label"

    private inner class TabButton(
        id: Int,
        x: Int,
        y: Int,
        val tab: LegacyGunSmithTab,
        private val selected: Boolean,
    ) : GuiButton(id, x, y, 22, 20, "") {
        override fun drawButton(mc: net.minecraft.client.Minecraft, mouseX: Int, mouseY: Int, partialTicks: Float) {
            if (!visible) {
                return
            }
            hovered = isMouseOver(mouseX, mouseY)
            val bg = when {
                selected -> 0xAA8C6B3B.toInt()
                hovered -> 0xAA555555.toInt()
                else -> 0x66333333
            }
            drawRect(x, y, x + width, y + height, bg)
            RenderHelper.enableGUIStandardItemLighting()
            itemRender.renderItemAndEffectIntoGUI(tab.icon, x + 3, y + 2)
            RenderHelper.disableStandardItemLighting()
        }

        fun isMouseOver(mouseX: Int, mouseY: Int): Boolean = mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height
    }

    private inner class ResultButton(
        id: Int,
        x: Int,
        y: Int,
        val recipe: LegacyGunSmithRecipe,
        private val selected: Boolean,
    ) : GuiButton(id, x, y, 94, 16, "") {
        override fun drawButton(mc: net.minecraft.client.Minecraft, mouseX: Int, mouseY: Int, partialTicks: Float) {
            if (!visible) {
                return
            }
            hovered = isMouseOver(mouseX, mouseY)
            val bg = when {
                selected -> 0xAA7E6140.toInt()
                hovered -> 0xAA4B4B4B.toInt()
                else -> 0x66333333
            }
            drawRect(x, y, x + width, y + height, bg)
            RenderHelper.enableGUIStandardItemLighting()
            itemRender.renderItemAndEffectIntoGUI(recipe.result, x + 1, y)
            RenderHelper.disableStandardItemLighting()
            val name = fontRenderer.trimStringToWidth(recipe.result.displayName, 68)
            fontRenderer.drawString(name, x + 20, y + 4, 0xFFFFFF)
            if (recipe.result.count > 1) {
                fontRenderer.drawString("x${recipe.result.count}", x + 76, y + 4, 0xDDDDDD)
            }
        }

        fun isMouseOver(mouseX: Int, mouseY: Int): Boolean = mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height
    }

    private companion object {
        val TEXTURE: ResourceLocation = ResourceLocation(TACZLegacy.MOD_ID, "textures/gui/gun_smith_table.png")
        val SIDE_TEXTURE: ResourceLocation = ResourceLocation(TACZLegacy.MOD_ID, "textures/gui/gun_smith_table_side.png")

        const val TITLE_COLOR: Int = 0x555555
        const val SEARCH_FIELD_ID: Int = 99
        const val BUTTON_FILTER: Int = 1
        const val BUTTON_CRAFT: Int = 2
        const val BUTTON_TYPE_PREV: Int = 3
        const val BUTTON_TYPE_NEXT: Int = 4
        const val BUTTON_RESULT_PREV: Int = 5
        const val BUTTON_RESULT_NEXT: Int = 6
        const val BUTTON_SCALE_UP: Int = 7
        const val BUTTON_SCALE_DOWN: Int = 8
        const val BUTTON_SCALE_RESET: Int = 9
        const val BUTTON_BY_HAND: Int = 10
        const val BUTTON_SELECT_ALL: Int = 11
        const val BUTTON_URL: Int = 12
        const val BUTTON_PACK_PREV: Int = 13
        const val BUTTON_PACK_NEXT: Int = 14
        const val BUTTON_TAB_BASE: Int = 1000
        const val BUTTON_RESULT_BASE: Int = 2000
        const val BUTTON_PACK_BASE: Int = 3000
        const val URL_CONFIRM_ID: Int = 77
        const val TABS_PER_PAGE: Int = 7
        const val RESULTS_PER_PAGE: Int = 6
        const val PACKS_PER_PAGE: Int = 7
        const val MAX_INGREDIENTS: Int = 12
        const val NO_RECIPE_TEXT: String = "No matching recipes"
    }
}
