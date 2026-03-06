package com.tacz.legacy.common.application.gunsmith

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.tacz.legacy.TACZLegacy
import com.tacz.legacy.api.DefaultAssets
import com.tacz.legacy.api.item.IAttachment
import com.tacz.legacy.api.item.IAmmo
import com.tacz.legacy.api.item.IGun
import com.tacz.legacy.api.item.attachment.AttachmentType
import com.tacz.legacy.api.item.gun.FireMode
import com.tacz.legacy.common.config.LegacyConfigManager
import com.tacz.legacy.common.item.LegacyBlockItem
import com.tacz.legacy.common.item.LegacyItems
import com.tacz.legacy.common.resource.GunDataAccessor
import com.tacz.legacy.common.resource.TACZGunPackPresentation
import com.tacz.legacy.common.resource.TACZGunPackRuntimeRegistry
import com.tacz.legacy.common.resource.TACZRecipeDefinition
import com.tacz.legacy.common.resource.TACZRecipeFilterDefinition
import com.tacz.legacy.common.resource.TACZRuntimeSnapshot
import com.tacz.legacy.common.resource.TACZWorkbenchTabDefinition
import net.minecraft.block.Block
import net.minecraft.entity.item.EntityItem
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.init.Blocks
import net.minecraft.init.Items
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.item.crafting.Ingredient
import net.minecraft.nbt.JsonToNBT
import net.minecraft.nbt.NBTException
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.JsonUtils
import net.minecraft.util.ResourceLocation
import net.minecraftforge.common.crafting.CraftingHelper
import net.minecraftforge.common.crafting.JsonContext
import net.minecraftforge.oredict.OreDictionary
import net.minecraftforge.fml.common.registry.ForgeRegistries
import java.util.Locale

internal data class LegacyGunSmithIngredient(
    val ingredient: Ingredient,
    val count: Int,
)

internal data class LegacyGunSmithRecipe(
    val id: ResourceLocation,
    val group: ResourceLocation,
    val result: ItemStack,
    val materials: List<LegacyGunSmithIngredient>,
    val sourceNamespace: String,
)

internal data class LegacyGunSmithTab(
    val id: ResourceLocation,
    val displayName: String,
    val icon: ItemStack,
)

internal object LegacyGunSmithingRuntime {
    private var cachedSnapshot: TACZRuntimeSnapshot? = null
    private var cachedRecipes: Map<ResourceLocation, LegacyGunSmithRecipe> = emptyMap()

    fun recipes(snapshot: TACZRuntimeSnapshot = TACZGunPackRuntimeRegistry.getSnapshot()): Map<ResourceLocation, LegacyGunSmithRecipe> {
        synchronized(this) {
            if (cachedSnapshot !== snapshot) {
                cachedSnapshot = snapshot
                cachedRecipes = snapshot.recipes.values.asSequence()
                    .mapNotNull { parseRecipe(it, snapshot) }
                    .associateBy(LegacyGunSmithRecipe::id)
            }
            return cachedRecipes
        }
    }

    fun recipeNamespacesForBlock(
        blockId: ResourceLocation,
        snapshot: TACZRuntimeSnapshot = TACZGunPackRuntimeRegistry.getSnapshot(),
    ): List<String> = visibleRecipes(
        blockId = blockId,
        selectedTab = null,
        selectedNamespaces = emptySet(),
        searchText = "",
        heldStack = ItemStack.EMPTY,
        byHandOnly = false,
        snapshot = snapshot,
    ).map(LegacyGunSmithRecipe::sourceNamespace).distinct()

    fun tabsForBlock(
        blockId: ResourceLocation,
        snapshot: TACZRuntimeSnapshot = TACZGunPackRuntimeRegistry.getSnapshot(),
    ): List<LegacyGunSmithTab> {
        val configuredTabs = snapshot.blocks[blockId]?.data?.tabs.orEmpty()
        val tabs = if (configuredTabs.isNotEmpty()) configuredTabs else deriveFallbackTabs(blockId, snapshot)
        return tabs.map { definition ->
            LegacyGunSmithTab(
                id = definition.id,
                displayName = TACZGunPackPresentation.localizedText(snapshot, definition.name)
                    ?: TACZGunPackPresentation.prettyResourceName(definition.id),
                icon = buildTabIcon(definition, snapshot),
            )
        }
    }

    fun visibleRecipes(
        blockId: ResourceLocation,
        selectedTab: ResourceLocation?,
        selectedNamespaces: Set<String>,
        searchText: String,
        heldStack: ItemStack,
        byHandOnly: Boolean,
        snapshot: TACZRuntimeSnapshot = TACZGunPackRuntimeRegistry.getSnapshot(),
    ): List<LegacyGunSmithRecipe> {
        val availableTabIds = tabsForBlock(blockId, snapshot).mapTo(linkedSetOf(), LegacyGunSmithTab::id)
        val normalizedSearch = searchText.trim().lowercase(Locale.ROOT)
        return recipes(snapshot).values.asSequence()
            .filter { recipe -> isVisibleForWorkbench(recipe, blockId, availableTabIds, snapshot) }
            .filter { recipe -> selectedTab == null || recipe.group == selectedTab }
            .filter { recipe -> selectedNamespaces.isEmpty() || recipe.sourceNamespace in selectedNamespaces }
            .filter { recipe -> normalizedSearch.isBlank() || matchesSearch(recipe, normalizedSearch) }
            .filter { recipe -> !byHandOnly || matchesHeldItem(recipe.result, heldStack) }
            .sortedWith(compareBy<LegacyGunSmithRecipe>({ it.sourceNamespace }, { it.id.toString() }))
            .toList()
    }

    fun isVisibleForWorkbench(
        recipe: LegacyGunSmithRecipe,
        blockId: ResourceLocation,
        availableTabIds: Set<ResourceLocation> = emptySet(),
        snapshot: TACZRuntimeSnapshot = TACZGunPackRuntimeRegistry.getSnapshot(),
    ): Boolean {
        val resolvedTabIds = if (availableTabIds.isEmpty()) {
            tabsForBlock(blockId, snapshot).mapTo(linkedSetOf(), LegacyGunSmithTab::id)
        } else {
            availableTabIds
        }
        if (resolvedTabIds.isNotEmpty() && recipe.group !in resolvedTabIds) {
            return false
        }
        val filter = activeRecipeFilter(blockId, snapshot)
        return filter?.allows(recipe.id) ?: true
    }

    fun ingredientCount(player: EntityPlayer, ingredient: LegacyGunSmithIngredient): Int {
        return player.inventory.mainInventory.sumOfMatching(ingredient.ingredient)
    }

    fun canCraft(player: EntityPlayer, recipe: LegacyGunSmithRecipe): Boolean {
        if (player.capabilities.isCreativeMode) {
            return true
        }
        return buildConsumptionPlan(player, recipe) != null
    }

    fun craft(player: EntityPlayer, blockId: ResourceLocation, recipeId: ResourceLocation): ItemStack? {
        val snapshot = TACZGunPackRuntimeRegistry.getSnapshot()
        val recipe = recipes(snapshot)[recipeId] ?: return null
        if (!isVisibleForWorkbench(recipe, blockId, snapshot = snapshot)) {
            return null
        }
        val plan = if (player.capabilities.isCreativeMode) emptyMap() else buildConsumptionPlan(player, recipe) ?: return null
        applyConsumptionPlan(player, plan)
        val output = recipe.result.copy()
        if (!player.world.isRemote) {
            player.world.spawnEntity(
                EntityItem(player.world, player.posX, player.posY + 0.5, player.posZ, output.copy()),
            )
        }
        player.inventory.markDirty()
        player.inventoryContainer.detectAndSendChanges()
        player.openContainer.detectAndSendChanges()
        return output
    }

    fun packName(snapshot: TACZRuntimeSnapshot, recipe: LegacyGunSmithRecipe): String? {
        val key = snapshot.packInfos[recipe.sourceNamespace]?.name ?: return recipe.sourceNamespace
        return TACZGunPackPresentation.localizedText(snapshot, key) ?: recipe.sourceNamespace
    }

    private fun parseRecipe(definition: TACZRecipeDefinition, snapshot: TACZRuntimeSnapshot): LegacyGunSmithRecipe? {
        val root = definition.raw
        val resultObject = root.getAsJsonObject("result") ?: return null
        val context = JsonContext(definition.id.namespace)
        return runCatching {
            val result = buildResultStack(resultObject, context)
            val materials = root.getAsJsonArray("materials")
                ?.mapNotNull { materialElement -> parseMaterial(materialElement, context) }
                .orEmpty()
            LegacyGunSmithRecipe(
                id = definition.id,
                group = resolveResultGroup(definition.id, resultObject, result, snapshot),
                result = result,
                materials = materials,
                sourceNamespace = definition.id.namespace,
            )
        }.onFailure { throwable ->
            TACZLegacy.logger.warn("Failed to parse gunsmith recipe {}", definition.id, throwable)
        }.getOrNull()
    }

    private fun parseMaterial(element: JsonElement, context: JsonContext): LegacyGunSmithIngredient? {
        if (!element.isJsonObject) {
            return null
        }
        val obj = element.asJsonObject
        val itemElement = obj.get("item") ?: return null
        val ingredient = parseIngredient(itemElement, context)
        val count = JsonUtils.getInt(obj, "count", 1).coerceAtLeast(1)
        return LegacyGunSmithIngredient(ingredient = ingredient, count = count)
    }

    private fun parseIngredient(element: JsonElement, context: JsonContext): Ingredient {
        if (element.isJsonArray) {
            val merged = element.asJsonArray.flatMap { child -> parseIngredient(child, context).matchingStacks.map(ItemStack::copy) }
            return if (merged.isEmpty()) Ingredient.EMPTY else Ingredient.fromStacks(*merged.toTypedArray())
        }
        if (!element.isJsonObject) {
            return CraftingHelper.getIngredient(element, context)
        }
        val obj = element.asJsonObject
        if (obj.has("tag")) {
            val tagId = JsonUtils.getString(obj, "tag")
            return ingredientFromTag(tagId)
        }
        return CraftingHelper.getIngredient(obj, context)
    }

    private fun ingredientFromTag(tagId: String): Ingredient {
        val stacks = resolveTagStacks(tagId)
        return if (stacks.isEmpty()) Ingredient.EMPTY else Ingredient.fromStacks(*stacks.toTypedArray())
    }

    private fun resolveTagStacks(tagId: String): List<ItemStack> {
        val oreName = oreDictionaryName(tagId)
        if (oreName != null) {
            val ores = OreDictionary.getOres(oreName)
            if (!ores.isEmpty()) {
                return ores.map(ItemStack::copy)
            }
        }
        return when (tagId) {
            "forge:glass" -> listOf(
                ItemStack(Item.getItemFromBlock(Blocks.GLASS)),
                ItemStack(Item.getItemFromBlock(Blocks.STAINED_GLASS), 1, OreDictionary.WILDCARD_VALUE),
            )
            "forge:gunpowder" -> listOf(ItemStack(Items.GUNPOWDER))
            "forge:leather" -> listOf(ItemStack(Items.LEATHER))
            "forge:rods/blaze" -> listOf(ItemStack(Items.BLAZE_ROD))
            "minecraft:logs" -> listOf(
                ItemStack(Item.getItemFromBlock(Blocks.LOG), 1, OreDictionary.WILDCARD_VALUE),
                ItemStack(Item.getItemFromBlock(Blocks.LOG2), 1, OreDictionary.WILDCARD_VALUE),
            )
            "forge:dusts/redstone" -> listOf(ItemStack(Items.REDSTONE))
            "forge:dusts/glowstone" -> listOf(ItemStack(Items.GLOWSTONE_DUST))
            "forge:gems/diamond" -> listOf(ItemStack(Items.DIAMOND))
            "forge:gems/quartz" -> listOf(ItemStack(Items.QUARTZ))
            "forge:gems/lapis" -> listOf(ItemStack(Items.DYE, 1, 4))
            "forge:ingots/iron" -> listOf(ItemStack(Items.IRON_INGOT))
            "forge:ingots/gold" -> listOf(ItemStack(Items.GOLD_INGOT))
            else -> emptyList()
        }
    }

    private fun oreDictionaryName(tagId: String): String? {
        if (tagId == "minecraft:logs") {
            return "logWood"
        }
        if (!tagId.startsWith("forge:")) {
            return null
        }
        val path = tagId.removePrefix("forge:")
        if ('/' !in path) {
            return when (path) {
                "glass" -> "blockGlass"
                "gunpowder" -> "gunpowder"
                "leather" -> "leather"
                else -> path
            }
        }
        val prefix = path.substringBefore('/')
        val material = path.substringAfter('/').split('/', '_', '-')
            .filter(String::isNotBlank)
            .joinToString(separator = "") { segment -> segment.replaceFirstChar { it.titlecase(Locale.ROOT) } }
        val singular = when {
            prefix.endsWith("ies") -> prefix.dropLast(3) + "y"
            prefix.endsWith('s') -> prefix.dropLast(1)
            else -> prefix
        }
        return singular + material
    }

    private fun buildResultStack(result: JsonObject, context: JsonContext): ItemStack {
        val type = JsonUtils.getString(result, "type", "custom").trim().lowercase(Locale.ROOT)
        val stack = when (type) {
            "gun" -> buildGunResult(result, context)
            "ammo" -> buildAmmoResult(result, context)
            "attachment" -> buildAttachmentResult(result, context)
            "custom" -> CraftingHelper.getItemStack(JsonUtils.getJsonObject(result, "item"), context)
            else -> throw IllegalArgumentException("Unsupported gunsmith result type: $type")
        }
        applyExtraNbt(stack, result.get("nbt"))
        return if (stack.isEmpty) ItemStack(LegacyItems.GUN_SMITH_TABLE) else stack
    }

    private fun buildGunResult(result: JsonObject, context: JsonContext): ItemStack {
        val gunId = contextualId(context, result, "id")
        val stack = ItemStack(LegacyItems.MODERN_KINETIC_GUN, JsonUtils.getInt(result, "count", 1).coerceAtLeast(1))
        LegacyItems.MODERN_KINETIC_GUN.setGunId(stack, gunId)
        val firstFireMode = GunDataAccessor.getGunData(gunId)?.fireModesSet?.firstOrNull()?.let(::parseFireMode)
        LegacyItems.MODERN_KINETIC_GUN.setFireMode(stack, firstFireMode ?: FireMode.UNKNOWN)
        LegacyItems.MODERN_KINETIC_GUN.setCurrentAmmoCount(stack, JsonUtils.getInt(result, "ammo_count", 0).coerceAtLeast(0))
        LegacyItems.MODERN_KINETIC_GUN.setBulletInBarrel(stack, false)
        result.getAsJsonObject("attachments")?.entrySet()?.forEach { (typeKey, rawId) ->
            val type = AttachmentType.fromSerializedName(typeKey)
            val attachmentId = rawId.asString.takeIf(String::isNotBlank)?.let { appendNamespace(context.modId, it) } ?: return@forEach
            val attachmentStack = ItemStack(LegacyItems.ATTACHMENT)
            LegacyItems.ATTACHMENT.setAttachmentId(attachmentStack, attachmentId)
            if (type != AttachmentType.NONE && LegacyItems.MODERN_KINETIC_GUN.allowAttachmentType(stack, type)) {
                LegacyItems.MODERN_KINETIC_GUN.installAttachment(stack, attachmentStack)
                if (LegacyItems.MODERN_KINETIC_GUN.getAttachmentId(stack, type) == attachmentId) {
                    return@forEach
                }
            }
            if (type != AttachmentType.NONE) {
                ensureTag(stack).setTag("${IGun.ATTACHMENT_BASE_TAG}${type.name}", attachmentStack.writeToNBT(NBTTagCompound()))
            }
        }
        return stack
    }

    private fun buildAmmoResult(result: JsonObject, context: JsonContext): ItemStack {
        val ammoId = contextualId(context, result, "id")
        val stack = ItemStack(LegacyItems.AMMO, JsonUtils.getInt(result, "count", 1).coerceAtLeast(1))
        LegacyItems.AMMO.setAmmoId(stack, ammoId)
        return stack
    }

    private fun buildAttachmentResult(result: JsonObject, context: JsonContext): ItemStack {
        val attachmentId = contextualId(context, result, "id")
        val stack = ItemStack(LegacyItems.ATTACHMENT, JsonUtils.getInt(result, "count", 1).coerceAtLeast(1))
        LegacyItems.ATTACHMENT.setAttachmentId(stack, attachmentId)
        return stack
    }

    private fun resolveResultGroup(
        recipeId: ResourceLocation,
        result: JsonObject,
        resultStack: ItemStack,
        snapshot: TACZRuntimeSnapshot,
    ): ResourceLocation {
        result.get("group")?.let { groupValue ->
            groupValue.asString.takeIf(String::isNotBlank)?.let { return appendNamespace(recipeId.namespace, it) }
        }
        return when (JsonUtils.getString(result, "type", "custom").trim().lowercase(Locale.ROOT)) {
            "gun" -> {
                val gunId = (resultStack.item as? IGun)?.getGunId(resultStack) ?: DefaultAssets.EMPTY_GUN_ID
                val type = snapshot.guns[gunId]?.index?.type.orEmpty().ifBlank { "misc" }
                ResourceLocation(TACZLegacy.MOD_ID, type)
            }
            "ammo" -> ResourceLocation(TACZLegacy.MOD_ID, "ammo")
            "attachment" -> {
                val attachmentId = (resultStack.item as? IAttachment)?.getAttachmentId(resultStack) ?: DefaultAssets.EMPTY_ATTACHMENT_ID
                val type = snapshot.attachments[attachmentId]?.index?.type.orEmpty().ifBlank { "misc" }
                ResourceLocation(TACZLegacy.MOD_ID, type)
            }
            else -> ResourceLocation(TACZLegacy.MOD_ID, "misc")
        }
    }

    private fun activeRecipeFilter(blockId: ResourceLocation, snapshot: TACZRuntimeSnapshot): TACZRecipeFilterDefinition? {
        val shouldApplyFilter = LegacyConfigManager.server.enableTableFilter || blockId != DefaultAssets.DEFAULT_BLOCK_ID
        return if (shouldApplyFilter) TACZGunPackPresentation.resolveRecipeFilter(snapshot, blockId) else null
    }

    private fun deriveFallbackTabs(blockId: ResourceLocation, snapshot: TACZRuntimeSnapshot): List<TACZWorkbenchTabDefinition> {
        val visibleRecipes = recipes(snapshot).values.filter { recipe ->
            val filter = activeRecipeFilter(blockId, snapshot)
            filter?.allows(recipe.id) ?: true
        }
        return visibleRecipes
            .groupBy(LegacyGunSmithRecipe::group)
            .entries
            .sortedBy { it.key.toString() }
            .map { (groupId, _) ->
                TACZWorkbenchTabDefinition(
                    id = groupId,
                    name = null,
                    icon = null,
                ).copy()
            }
    }

    private fun buildTabIcon(definition: TACZWorkbenchTabDefinition, snapshot: TACZRuntimeSnapshot): ItemStack {
        val iconDef = definition.icon
        if (iconDef?.itemId != null) {
            val item = ForgeRegistries.ITEMS.getValue(iconDef.itemId)
            if (item != null) {
                val stack = ItemStack(item)
                applyExtraNbt(stack, iconDef.nbt)
                return stack
            }
        }
        val fallback = recipes(snapshot).values.firstOrNull { it.group == definition.id }?.result?.copy()
        if (fallback != null && !fallback.isEmpty) {
            return fallback
        }
        return createBlockIcon(snapshot, DefaultAssets.DEFAULT_BLOCK_ID)
    }

    private fun createBlockIcon(snapshot: TACZRuntimeSnapshot, blockId: ResourceLocation): ItemStack {
        val blockEntry = snapshot.blocks[blockId]
        val itemId = blockEntry?.index?.id ?: DefaultAssets.DEFAULT_BLOCK_ID
        val item = ForgeRegistries.ITEMS.getValue(itemId) ?: return ItemStack(LegacyItems.GUN_SMITH_TABLE)
        val stack = ItemStack(item)
        if (item is LegacyBlockItem) {
            item.setBlockId(stack, blockId)
        }
        return stack
    }

    private fun matchesSearch(recipe: LegacyGunSmithRecipe, normalizedSearch: String): Boolean {
        if (normalizedSearch.isBlank()) {
            return true
        }
        return recipe.result.displayName.lowercase(Locale.ROOT).contains(normalizedSearch) ||
            recipe.id.toString().lowercase(Locale.ROOT).contains(normalizedSearch)
    }

    private fun matchesHeldItem(result: ItemStack, heldStack: ItemStack): Boolean {
        if (heldStack.isEmpty) {
            return false
        }
        val heldGun = heldStack.item as? IGun
        val heldAmmo = heldStack.item as? IAmmo
        val heldAttachment = heldStack.item as? IAttachment
        val resultGun = result.item as? IGun
        val resultAmmo = result.item as? IAmmo
        val resultAttachment = result.item as? IAttachment
        return when {
            heldGun != null && resultAmmo != null -> resultAmmo.isAmmoOfGun(heldStack, result)
            heldGun != null && resultAttachment != null -> heldGun.allowAttachment(heldStack, result)
            heldAmmo != null && resultGun != null -> heldAmmo.isAmmoOfGun(result, heldStack)
            heldAttachment != null && resultGun != null -> resultGun.allowAttachment(result, heldStack)
            else -> false
        }
    }

    private fun buildConsumptionPlan(player: EntityPlayer, recipe: LegacyGunSmithRecipe): Map<Int, Int>? {
        val available = player.inventory.mainInventory.map(ItemStack::getCount).toMutableList()
        val plan = linkedMapOf<Int, Int>()
        recipe.materials.forEach { material ->
            var remaining = material.count
            player.inventory.mainInventory.forEachIndexed { slot, stack ->
                if (remaining <= 0) {
                    return@forEachIndexed
                }
                if (stack.isEmpty || !material.ingredient.apply(stack)) {
                    return@forEachIndexed
                }
                val canTake = minOf(remaining, available[slot])
                if (canTake <= 0) {
                    return@forEachIndexed
                }
                plan[slot] = (plan[slot] ?: 0) + canTake
                available[slot] -= canTake
                remaining -= canTake
            }
            if (remaining > 0) {
                return null
            }
        }
        return plan
    }

    private fun applyConsumptionPlan(player: EntityPlayer, plan: Map<Int, Int>) {
        plan.forEach { (slot, amount) ->
            player.inventory.decrStackSize(slot, amount)
        }
    }

    private fun applyExtraNbt(stack: ItemStack, element: JsonElement?) {
        if (element == null || element.isJsonNull) {
            return
        }
        val extra = parseCompoundTag(element) ?: return
        val existing = ensureTag(stack)
        existing.merge(extra)
        stack.tagCompound = existing
    }

    private fun parseCompoundTag(element: JsonElement): NBTTagCompound? = try {
        if (element.isJsonObject) {
            JsonToNBT.getTagFromJson(CraftingHelper.GSON.toJson(element))
        } else {
            JsonToNBT.getTagFromJson(element.asString)
        }
    } catch (_: NBTException) {
        null
    }

    private fun appendNamespace(defaultNamespace: String, value: String): ResourceLocation {
        return if (':' in value) ResourceLocation(value) else ResourceLocation(defaultNamespace, value)
    }

    private fun contextualId(context: JsonContext, result: JsonObject, member: String): ResourceLocation =
        appendNamespace(context.modId, JsonUtils.getString(result, member))

    private fun parseFireMode(rawValue: String): FireMode =
        runCatching { FireMode.valueOf(rawValue.uppercase(Locale.ROOT)) }.getOrDefault(FireMode.UNKNOWN)

    private fun ensureTag(stack: ItemStack): NBTTagCompound {
        val existing = stack.tagCompound
        if (existing != null) {
            return existing
        }
        val created = NBTTagCompound()
        stack.tagCompound = created
        return created
    }

    private fun List<ItemStack>.sumOfMatching(ingredient: Ingredient): Int = fold(0) { acc, stack ->
        if (stack.isEmpty || !ingredient.apply(stack)) acc else acc + stack.count
    }
}
