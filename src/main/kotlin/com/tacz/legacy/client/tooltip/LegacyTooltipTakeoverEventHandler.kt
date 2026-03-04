package com.tacz.legacy.client.tooltip

import com.tacz.legacy.TACZLegacy
import com.tacz.legacy.common.application.gunpack.GunPackTooltipIndexEntry
import com.tacz.legacy.common.infrastructure.mc.registry.item.LegacyAmmoBoxItem
import com.tacz.legacy.common.infrastructure.mc.registry.item.LegacyAmmoItem
import com.tacz.legacy.common.infrastructure.mc.registry.item.LegacyAttachmentItem
import com.tacz.legacy.common.infrastructure.mc.registry.item.LegacyGunItem
import com.tacz.legacy.common.infrastructure.mc.registry.item.LegacyIndexedTooltipSupport
import net.minecraft.item.ItemBlock
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation
import net.minecraftforge.event.entity.player.ItemTooltipEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.relauncher.Side

@Mod.EventBusSubscriber(modid = TACZLegacy.MOD_ID, value = [Side.CLIENT])
public object LegacyTooltipTakeoverEventHandler {

    @JvmStatic
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public fun onItemTooltip(event: ItemTooltipEvent) {
        val stack = event.itemStack
        if (stack.isEmpty) {
            return
        }

        val registryName = stack.item.registryName ?: return
        if (!registryName.namespace.equals(TACZLegacy.MOD_ID, ignoreCase = true)) {
            return
        }

        val tooltip = event.toolTip
        if (tooltip.isEmpty()) {
            return
        }

        val generated = buildGeneratedLines(stack, registryName) ?: return
        mergeTooltipLines(tooltip, generated)
    }

    internal fun buildGeneratedLines(stack: ItemStack, registryName: ResourceLocation): List<String>? {
        val primarySection = mutableListOf<String>()
        val secondarySection = mutableListOf<String>()
        when (val item = stack.item) {
            is LegacyGunItem -> {
                val indexedEntry = LegacyIndexedTooltipSupport.resolveGunEntry(stack)
                appendIndexedThenIcon(
                    tooltip = primarySection,
                    stackDisplayName = stack.displayName,
                    entry = indexedEntry,
                    iconTextureAssetPath = LegacyIndexedTooltipSupport.resolveGunIconTextureAssetPath(stack)
                )
                secondarySection += GUN_HINT_FIRE
                secondarySection += GUN_HINT_RELOAD
            }

            is LegacyAttachmentItem -> {
                val indexedEntry = LegacyIndexedTooltipSupport.resolveAttachmentEntry(item.attachmentId)
                appendIndexedThenIcon(
                    tooltip = primarySection,
                    stackDisplayName = stack.displayName,
                    entry = indexedEntry,
                    iconTextureAssetPath = item.iconTextureAssetPath
                )
            }

            is LegacyAmmoBoxItem -> {
                val indexedEntry = LegacyIndexedTooltipSupport.resolveAmmoEntry(item.ammoId)
                appendIndexedThenIcon(
                    tooltip = primarySection,
                    stackDisplayName = stack.displayName,
                    entry = indexedEntry,
                    iconTextureAssetPath = item.iconTextureAssetPath
                )
                secondarySection += "§8单次补充：+${item.roundsPerItem.coerceAtLeast(1)}"
                secondarySection += "§8剩余弹量：${readAmmoBoxRemainingRounds(stack, item.capacity)}/${item.capacity.coerceAtLeast(1)}"
            }

            is LegacyAmmoItem -> {
                val indexedEntry = LegacyIndexedTooltipSupport.resolveAmmoEntry(item.ammoId)
                appendIndexedThenIcon(
                    tooltip = primarySection,
                    stackDisplayName = stack.displayName,
                    entry = indexedEntry,
                    iconTextureAssetPath = item.iconTextureAssetPath
                )
                secondarySection += "§8单次补充：+${item.roundsPerItem.coerceAtLeast(1)}"
            }

            is ItemBlock -> {
                val indexedEntry = LegacyIndexedTooltipSupport.resolveBlockEntry(registryName.toString())
                appendIndexedThenIcon(
                    tooltip = primarySection,
                    stackDisplayName = stack.displayName,
                    entry = indexedEntry,
                    iconTextureAssetPath = LegacyIndexedTooltipSupport.resolveBlockIconTextureAssetPath(indexedEntry)
                )
            }

            else -> return null
        }

        return composeSections(primarySection, secondarySection)
    }

    internal fun mergeTooltipLines(target: MutableList<String>, additions: List<String>) {
        appendUniqueLines(target, additions)
    }

    private fun appendIndexedThenIcon(
        tooltip: MutableList<String>,
        stackDisplayName: String,
        entry: GunPackTooltipIndexEntry?,
        iconTextureAssetPath: String?
    ) {
        LegacyIndexedTooltipSupport.appendIndexedLines(
            tooltip = tooltip,
            stackDisplayName = stackDisplayName,
            entry = entry
        )
        LegacyIndexedTooltipSupport.appendIconTokenLine(
            tooltip = tooltip,
            iconTextureAssetPath = iconTextureAssetPath
        )
    }

    private fun composeSections(primary: List<String>, secondary: List<String>): List<String> {
        if (primary.isEmpty()) {
            return secondary
        }
        if (secondary.isEmpty()) {
            return primary
        }
        return buildList {
            addAll(primary)
            add(SECTION_SEPARATOR)
            addAll(secondary)
        }
    }

    private fun appendUniqueLines(target: MutableList<String>, additions: List<String>) {
        if (additions.isEmpty()) {
            return
        }

        val normalizedSeen = target.map(::normalizeLineForCompare).toMutableSet()
        additions.forEach { line ->
            val normalized = normalizeLineForCompare(line)
            if (normalized.isBlank()) {
                return@forEach
            }
            if (normalizedSeen.add(normalized)) {
                target += line
            }
        }
    }

    private fun normalizeLineForCompare(raw: String): String {
        return raw
            .replace(COLOR_CODE_REGEX, "")
            .trim()
            .lowercase()
    }

    private fun readAmmoBoxRemainingRounds(
        stack: net.minecraft.item.ItemStack,
        defaultCapacity: Int
    ): Int {
        val safeCapacity = defaultCapacity.coerceAtLeast(1)
        val tag = stack.tagCompound ?: return safeCapacity
        if (!tag.hasKey(AMMO_BOX_REMAINING_ROUNDS_TAG)) {
            return safeCapacity
        }
        return tag.getInteger(AMMO_BOX_REMAINING_ROUNDS_TAG).coerceAtLeast(0)
    }

    private const val GUN_HINT_FIRE: String = "§7左键：开火"
    private const val GUN_HINT_RELOAD: String = "§7潜行 + 右键：换弹"
    private const val AMMO_BOX_REMAINING_ROUNDS_TAG: String = "remaining_rounds"
    private const val SECTION_SEPARATOR: String = "§8────────"

    private val COLOR_CODE_REGEX: Regex = Regex("§.")
}
