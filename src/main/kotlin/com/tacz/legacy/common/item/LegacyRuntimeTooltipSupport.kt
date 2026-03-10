package com.tacz.legacy.common.item

import com.google.gson.JsonObject
import com.tacz.legacy.api.DefaultAssets
import com.tacz.legacy.api.item.IAmmo
import com.tacz.legacy.api.item.IAmmoBox
import com.tacz.legacy.api.item.IGun
import com.tacz.legacy.api.item.gun.FireMode
import com.tacz.legacy.common.application.refit.LegacyGunRefitRuntime
import com.tacz.legacy.common.config.LegacyConfigManager
import com.tacz.legacy.common.resource.BoltType
import com.tacz.legacy.common.resource.GunCombatData
import com.tacz.legacy.common.resource.GunDataAccessor
import com.tacz.legacy.common.resource.TACZAmmoIndexDefinition
import com.tacz.legacy.common.resource.TACZAttachmentIndexDefinition
import com.tacz.legacy.common.resource.TACZBlockIndexDefinition
import com.tacz.legacy.common.resource.TACZGunIndexDefinition
import com.tacz.legacy.common.resource.TACZGunPackPresentation
import com.tacz.legacy.common.resource.TACZGunPackRuntimeRegistry
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.ResourceLocation
import net.minecraft.util.text.TextFormatting
import net.minecraft.util.text.translation.I18n
import java.text.DecimalFormat
import java.util.Locale

internal object LegacyRuntimeTooltipSupport {
    internal const val ATTACHMENT_ID_TAG: String = "AttachmentId"
    internal const val BLOCK_ID_TAG: String = "BlockId"
    internal const val GUN_DISPLAY_ID_TAG: String = "GunDisplayId"
    internal const val GUN_HEAT_AMOUNT_TAG: String = "HeatAmount"

    private val WEIGHT_FORMAT: DecimalFormat = DecimalFormat("0.##")
    private val DAMAGE_FORMAT: DecimalFormat = DecimalFormat("0.##")

    internal data class HeatInfo(
        val current: Float,
        val max: Float,
        val locked: Boolean,
    )

    private data class IndexedTooltipEntry(
        val id: ResourceLocation,
        val name: String,
        val tooltip: String?,
    )

    internal fun resolveDisplayName(stack: ItemStack, fallback: String): String {
        if (stack.isEmpty) {
            return fallback
        }
        val snapshot = TACZGunPackRuntimeRegistry.getSnapshot()
        val resolved = when (val item = stack.item) {
            is IGun -> {
                val gunId = item.getGunId(stack)
                snapshot.guns[gunId]?.index?.name
            }
            else -> resolveIndexedEntry(stack)?.name
        }
        return resolved?.let(::localizeOrRaw)?.takeIf { it.isNotBlank() } ?: fallback
    }

    internal fun appendTooltip(stack: ItemStack, tooltip: MutableList<String>, advanced: Boolean): Unit {
        if (stack.isEmpty) {
            return
        }
        when (val item = stack.item) {
            is IGun -> appendGunTooltip(stack, item, tooltip, advanced)
            is IAmmoBox -> {
                appendIndexedTooltip(resolveIndexedEntry(stack), tooltip)
                appendAmmoBoxTooltip(stack, item, tooltip)
                appendIdLine(advanced, tooltip, resolveAmmoId(stack))
            }
            else -> {
                appendIndexedTooltip(resolveIndexedEntry(stack), tooltip)
                appendIdLine(advanced, tooltip, resolveGenericId(stack))
            }
        }
    }

    internal fun getCurrentAmmoWithBarrel(stack: ItemStack, iGun: IGun, gunData: GunCombatData): Int {
        val barrelAmmo = if (iGun.hasBulletInBarrel(stack) && gunData.boltType != BoltType.OPEN_BOLT) 1 else 0
        return iGun.getCurrentAmmoCount(stack) + barrelAmmo
    }

    internal fun getMaxAmmoWithBarrel(gunData: GunCombatData): Int {
        return gunData.ammoAmount + if (gunData.boltType == BoltType.OPEN_BOLT) 0 else 1
    }

    internal fun getMaxAmmoWithBarrel(stack: ItemStack, gunData: GunCombatData): Int {
        val capacity = LegacyGunRefitRuntime.computeAmmoCapacity(stack).coerceAtLeast(gunData.ammoAmount)
        return capacity + if (gunData.boltType == BoltType.OPEN_BOLT) 0 else 1
    }

    internal fun countInventoryAmmo(player: EntityPlayer, gunStack: ItemStack): Int {
        val iGun = gunStack.item as? IGun ?: return 0
        if (iGun.useDummyAmmo(gunStack)) {
            return iGun.getDummyAmmoAmount(gunStack)
        }
        var total = 0
        for (slot in 0 until player.inventory.sizeInventory) {
            val inventoryStack = player.inventory.getStackInSlot(slot)
            if (inventoryStack.isEmpty) {
                continue
            }
            val ammo = inventoryStack.item as? IAmmo
            if (ammo != null && ammo.isAmmoOfGun(gunStack, inventoryStack)) {
                total += inventoryStack.count
                continue
            }
            val ammoBox = inventoryStack.item as? IAmmoBox
            if (ammoBox != null && ammoBox.isAmmoBoxOfGun(gunStack, inventoryStack)) {
                if (ammoBox.isAllTypeCreative(inventoryStack) || ammoBox.isCreative(inventoryStack)) {
                    return 9999
                }
                total += ammoBox.getAmmoCount(inventoryStack)
            }
        }
        return total.coerceAtMost(9999)
    }

    internal fun resolveHeatInfo(stack: ItemStack, gunId: ResourceLocation, iGun: IGun): HeatInfo? {
        val raw = TACZGunPackRuntimeRegistry.getSnapshot().guns[gunId]?.data?.raw ?: return null
        val heat = raw.get("heat")?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        val max = heat.floatValue("max")
        if (max <= 0f) {
            return null
        }
        val current = (stack.tagCompound?.getFloat(GUN_HEAT_AMOUNT_TAG) ?: 0f).coerceAtLeast(0f)
        return HeatInfo(current = current, max = max, locked = iGun.isOverheatLocked(stack))
    }

    internal fun resolveGunDisplayId(stack: ItemStack, iGun: IGun? = stack.item as? IGun): ResourceLocation? {
        val gun = iGun ?: return null
        val snapshot = TACZGunPackRuntimeRegistry.getSnapshot()
        val fallbackDisplayId = TACZGunPackPresentation.resolveGunDisplayId(snapshot, gun.getGunId(stack))
        val rawDisplayId = stack.tagCompound
            ?.getString(GUN_DISPLAY_ID_TAG)
            ?.takeIf { it.isNotBlank() }
            ?.let(::safeResourceLocation)

        return when {
            rawDisplayId == null || rawDisplayId == DefaultAssets.DEFAULT_GUN_DISPLAY_ID -> fallbackDisplayId
            snapshot.gunDisplays.containsKey(rawDisplayId) -> rawDisplayId
            else -> fallbackDisplayId
        }
    }

    internal fun isContinuousBurst(gunId: ResourceLocation): Boolean {
        val raw = TACZGunPackRuntimeRegistry.getSnapshot().guns[gunId]?.data?.raw ?: return false
        val burst = raw.get("burst_data")?.takeIf { it.isJsonObject }?.asJsonObject ?: return false
        return burst.booleanValue("continuous_shoot")
    }

    private fun appendGunTooltip(stack: ItemStack, iGun: IGun, tooltip: MutableList<String>, advanced: Boolean): Unit {
        val gunId = iGun.getGunId(stack)
        val snapshot = TACZGunPackRuntimeRegistry.getSnapshot()
        val loadedGun = snapshot.guns[gunId]
        val gunData = GunDataAccessor.getGunData(gunId)
        val hideFlags = GunTooltipPart.getHideFlags(stack)

        if (loadedGun != null && !isHidden(hideFlags, GunTooltipPart.DESCRIPTION)) {
            appendDescription(tooltip, loadedGun.index.tooltip)
        }
        if (loadedGun != null && gunData != null && !isHidden(hideFlags, GunTooltipPart.AMMO_INFO)) {
            appendGunAmmoInfo(stack, iGun, gunData, tooltip)
        }
        if (loadedGun != null && !isHidden(hideFlags, GunTooltipPart.BASE_INFO)) {
            appendGunBaseInfo(stack, iGun, loadedGun.index, loadedGun.data.raw, tooltip)
        }
        if (loadedGun != null && !isHidden(hideFlags, GunTooltipPart.EXTRA_DAMAGE_INFO)) {
            appendExtraDamageInfo(loadedGun.data.raw, tooltip)
        }
        if (loadedGun != null && !isHidden(hideFlags, GunTooltipPart.PACK_INFO)) {
            appendPackInfo(gunId, tooltip)
        }
        appendIdLine(advanced, tooltip, gunId)
    }

    private fun appendGunAmmoInfo(
        stack: ItemStack,
        iGun: IGun,
        gunData: GunCombatData,
        tooltip: MutableList<String>,
    ): Unit {
        val snapshot = TACZGunPackRuntimeRegistry.getSnapshot()
        val ammoName = gunData.ammoId
            ?.let(snapshot.ammos::get)
            ?.let(TACZAmmoIndexDefinition::name)
            ?.let(::localizeOrRaw)
            ?.takeIf { it.isNotBlank() }
            ?: localizedLabel("config.tacz.common.ammo", "Ammo")
        val currentAmmo = getCurrentAmmoWithBarrel(stack, iGun, gunData)
        val maxAmmo = getMaxAmmoWithBarrel(stack, gunData)
        tooltip += "${TextFormatting.GRAY}$ammoName${TextFormatting.DARK_GRAY}: ${TextFormatting.WHITE}$currentAmmo/$maxAmmo"
    }

    private fun appendGunBaseInfo(
        stack: ItemStack,
        iGun: IGun,
        gunIndex: TACZGunIndexDefinition,
        raw: JsonObject,
        tooltip: MutableList<String>,
    ): Unit {
        val fireModeLabel = localizedLabel("gui.tacz.gun_refit.property_diagrams.fire_mode", "Fire Mode: ")
        val rpmLabel = localizedLabel("gui.tacz.gun_refit.property_diagrams.rpm", "RPM")
        val weightLabel = localizedLabel("gui.tacz.gun_refit.property_diagrams.weight", "Weight")
        val typeText = gunTypeText(gunIndex.type)
        val currentFireMode = fireModeText(iGun.getFireMode(stack))
        tooltip += "${TextFormatting.DARK_GRAY}$fireModeLabel$currentFireMode"
        raw.intValue("rpm").takeIf { it > 0 }?.let { rpm ->
            tooltip += "${TextFormatting.DARK_GRAY}$rpmLabel: ${TextFormatting.GRAY}$rpm"
        }
        raw.floatValue("weight").takeIf { it > 0f }?.let { weight ->
            tooltip += "${TextFormatting.DARK_GRAY}$weightLabel: ${TextFormatting.GRAY}${WEIGHT_FORMAT.format(weight)}"
        }
        if (typeText.isNotBlank()) {
            tooltip += "${TextFormatting.DARK_GRAY}$typeText"
        }
    }

    private fun appendExtraDamageInfo(raw: JsonObject, tooltip: MutableList<String>): Unit {
        val bullet = raw.get("bullet")?.takeIf { it.isJsonObject }?.asJsonObject ?: return
        val damage = bullet.floatValue("damage")
        val extraDamage = bullet.get("extra_damage")?.takeIf { it.isJsonObject }?.asJsonObject
        val armorIgnore = extraDamage?.floatValue("armor_ignore") ?: 0f
        val headShot = extraDamage?.floatValue("head_shot_multiplier") ?: 0f
        val damageLabel = localizedLabel("gui.tacz.gun_refit.property_diagrams.damage", "Damage")
        val armorIgnoreLabel = localizedLabel("gui.tacz.gun_refit.property_diagrams.armor_ignore", "AP Ratio")
        val headShotLabel = localizedLabel("gui.tacz.gun_refit.property_diagrams.head_shot", "Headshot")

        if (damage > 0f) {
            tooltip += "${TextFormatting.DARK_GRAY}$damageLabel: ${TextFormatting.GRAY}${DAMAGE_FORMAT.format(damage)}"
        }
        if (armorIgnore > 0f) {
            tooltip += "${TextFormatting.DARK_GRAY}$armorIgnoreLabel: ${TextFormatting.GRAY}${DAMAGE_FORMAT.format(armorIgnore * 100f)}%"
        }
        if (headShot > 0f) {
            tooltip += "${TextFormatting.DARK_GRAY}$headShotLabel: ${TextFormatting.GRAY}x${DAMAGE_FORMAT.format(headShot)}"
        }
    }

    private fun appendPackInfo(gunId: ResourceLocation, tooltip: MutableList<String>): Unit {
        val packName = TACZGunPackRuntimeRegistry.getSnapshot().packInfos[gunId.namespace]?.name?.let(::localizeOrRaw)
            ?.takeIf { it.isNotBlank() }
            ?: gunId.namespace
        val label = localizedLabel("tooltip.tacz.gun.pack", "Pack: ")
        tooltip += "${TextFormatting.DARK_GRAY}$label$packName"
    }

    private fun appendIndexedTooltip(entry: IndexedTooltipEntry?, tooltip: MutableList<String>): Unit {
        appendDescription(tooltip, entry?.tooltip)
    }

    private fun appendAmmoBoxTooltip(stack: ItemStack, ammoBox: IAmmoBox, tooltip: MutableList<String>): Unit {
        when {
            ammoBox.isAllTypeCreative(stack) -> {
                tooltip += "${TextFormatting.GRAY}${localizedLabel("tooltip.tacz.ammo_box.usage.all_type_creative", "Supplies unlimited ammo for every type")}" 
            }
            ammoBox.isCreative(stack) -> {
                tooltip += "${TextFormatting.GRAY}${localizedLabel("tooltip.tacz.ammo_box.usage.creative.1", "Pick up the box in inventory and right-click the ammo")}" 
                tooltip += "${TextFormatting.GRAY}${localizedLabel("tooltip.tacz.ammo_box.usage.creative.2", "Then it will provide unlimited ammo of that type")}" 
            }
            else -> {
                tooltip += "${TextFormatting.DARK_GRAY}${localizedFormattedLabel("tooltip.tacz.ammo_box.count", "Count: %d", ammoBox.getAmmoCount(stack))}"
                tooltip += "${TextFormatting.GRAY}${localizedLabel("tooltip.tacz.ammo_box.usage.deposit", "- Deposit Ammo: Pick up the box in inventory and right-click the ammo")}" 
                tooltip += "${TextFormatting.GRAY}${localizedLabel("tooltip.tacz.ammo_box.usage.remove", "- Remove Ammo: Pick up the box in inventory and right-click an empty slot")}" 
            }
        }
    }

    private fun appendDescription(tooltip: MutableList<String>, tooltipKeyOrText: String?): Unit {
        val description = tooltipKeyOrText?.takeIf { it.isNotBlank() }?.let(::localizeOrRaw)?.trim().orEmpty()
        if (description.isBlank()) {
            return
        }
        description.lines()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .forEach { line -> tooltip += "${TextFormatting.GRAY}$line" }
    }

    private fun appendIdLine(advanced: Boolean, tooltip: MutableList<String>, id: ResourceLocation?): Unit {
        if (!advanced || !LegacyConfigManager.client.enableTaczIdInTooltip || id == null) {
            return
        }
        val idText = if (I18n.canTranslate("gui.tacz.gun_smith_table.error.id")) {
            I18n.translateToLocalFormatted("gui.tacz.gun_smith_table.error.id", id)
        } else {
            "ID: $id"
        }
        tooltip += "${TextFormatting.DARK_GRAY}$idText"
    }

    private fun resolveIndexedEntry(stack: ItemStack): IndexedTooltipEntry? {
        val snapshot = TACZGunPackRuntimeRegistry.getSnapshot()
        resolveAmmoId(stack)?.let { ammoId ->
            val ammo = snapshot.ammos[ammoId] ?: return@let
            return IndexedTooltipEntry(id = ammoId, name = ammo.name, tooltip = ammo.tooltip)
        }
        resolveAttachmentId(stack)?.let { attachmentId ->
            val attachment = snapshot.attachments[attachmentId] ?: return@let
            return IndexedTooltipEntry(id = attachmentId, name = attachment.index.name, tooltip = attachment.index.tooltip)
        }
        resolveBlockId(stack)?.let { blockId ->
            val block = snapshot.blocks[blockId] ?: return@let
            return IndexedTooltipEntry(id = blockId, name = block.index.name, tooltip = block.index.tooltip)
        }
        return null
    }

    private fun resolveAmmoId(stack: ItemStack): ResourceLocation? {
        val item = stack.item
        if (item is IAmmo) {
            return item.getAmmoId(stack).takeUnless { it.path == "empty" }
        }
        if (item is IAmmoBox) {
            return item.getAmmoId(stack).takeUnless { it.path == "empty" }
        }
        return stack.tagCompound?.getString(AmmoItem.AMMO_ID_TAG)?.takeIf { it.isNotBlank() }?.let(::safeResourceLocation)
    }

    private fun resolveAttachmentId(stack: ItemStack): ResourceLocation? {
        return stack.tagCompound?.getString(ATTACHMENT_ID_TAG)?.takeIf { it.isNotBlank() }?.let(::safeResourceLocation)
    }

    private fun resolveBlockId(stack: ItemStack): ResourceLocation? {
        return stack.tagCompound?.getString(BLOCK_ID_TAG)?.takeIf { it.isNotBlank() }?.let(::safeResourceLocation)
    }

    private fun resolveGenericId(stack: ItemStack): ResourceLocation? {
        return resolveAmmoId(stack) ?: resolveAttachmentId(stack) ?: resolveBlockId(stack)
    }

    private fun fireModeText(fireMode: FireMode): String {
        val key = when (fireMode) {
            FireMode.AUTO -> "gui.tacz.gun_refit.property_diagrams.auto"
            FireMode.BURST -> "gui.tacz.gun_refit.property_diagrams.burst"
            FireMode.SEMI -> "gui.tacz.gun_refit.property_diagrams.semi"
            FireMode.UNKNOWN -> "gui.tacz.gun_refit.property_diagrams.unknown"
        }
        val localized = TACZGunPackPresentation.localizedText(TACZGunPackRuntimeRegistry.getSnapshot(), key)
        return localized ?: if (I18n.canTranslate(key)) I18n.translateToLocal(key) else fireMode.name
    }

    private fun gunTypeText(type: String): String {
        if (type.isBlank()) {
            return ""
        }
        val normalized = type.lowercase(Locale.ROOT)
        val knownKey = when (normalized) {
            "pistol" -> "itemGroup.tacz.guns.pistol"
            "sniper" -> "itemGroup.tacz.guns.sniper"
            "rifle" -> "itemGroup.tacz.guns.rifle"
            "shotgun" -> "itemGroup.tacz.guns.shotgun"
            "smg" -> "itemGroup.tacz.guns.smg"
            "rpg" -> "itemGroup.tacz.guns.rpg"
            "mg" -> "itemGroup.tacz.guns.mg"
            else -> null
        }
        return knownKey?.let(::localizeOrRaw)?.takeIf { it.isNotBlank() } ?: normalized.replace('_', ' ').replaceFirstChar { it.titlecase(Locale.ROOT) }
    }

    private fun isHidden(mask: Int, part: GunTooltipPart): Boolean {
        return mask and part.mask != 0
    }

    private fun localizeOrRaw(keyOrText: String): String {
        if (keyOrText.isBlank()) {
            return ""
        }
        TACZGunPackPresentation.localizedText(TACZGunPackRuntimeRegistry.getSnapshot(), keyOrText)?.let { return it }
        return if (I18n.canTranslate(keyOrText)) I18n.translateToLocal(keyOrText) else keyOrText
    }

    private fun localizedLabel(key: String, fallback: String): String {
        return TACZGunPackPresentation.localizedText(TACZGunPackRuntimeRegistry.getSnapshot(), key)
            ?: if (I18n.canTranslate(key)) I18n.translateToLocal(key) else fallback
    }

    private fun localizedFormattedLabel(key: String, fallback: String, vararg args: Any): String {
        val template = TACZGunPackPresentation.localizedText(TACZGunPackRuntimeRegistry.getSnapshot(), key)
            ?: if (I18n.canTranslate(key)) I18n.translateToLocal(key) else fallback
        return runCatching { String.format(Locale.ROOT, template, *args) }.getOrElse { template }
    }

    private fun safeResourceLocation(path: String): ResourceLocation? {
        return runCatching { ResourceLocation(path) }.getOrNull()
    }

    private fun ensureTag(stack: ItemStack): NBTTagCompound {
        val existing = stack.tagCompound
        if (existing != null) {
            return existing
        }
        val created = NBTTagCompound()
        stack.tagCompound = created
        return created
    }

    private fun JsonObject.intValue(memberName: String): Int =
        get(memberName)?.takeIf { it.isJsonPrimitive }?.asInt ?: 0

    private fun JsonObject.floatValue(memberName: String): Float =
        get(memberName)?.takeIf { it.isJsonPrimitive }?.asFloat ?: 0f

    private fun JsonObject.booleanValue(memberName: String): Boolean =
        get(memberName)?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false
}
