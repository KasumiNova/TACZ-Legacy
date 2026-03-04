package com.tacz.legacy.common.infrastructure.mc.registry.item

import com.tacz.legacy.common.application.gunpack.GunPackTooltipIndexEntry
import com.tacz.legacy.common.application.gunpack.GunPackTooltipIndexRuntime
import com.tacz.legacy.common.application.gunpack.GunPackTooltipLangRuntime
import com.tacz.legacy.common.application.gunpack.GunPackTooltipLangSnapshot
import com.tacz.legacy.common.application.gunpack.GunDisplayRuntime
import net.minecraft.item.ItemStack
import net.minecraft.util.text.translation.I18n

internal object LegacyIndexedTooltipSupport {

    fun resolveGunEntry(stack: ItemStack): GunPackTooltipIndexEntry? {
        val itemId = stack.item.registryName?.toString() ?: return null
        return GunPackTooltipIndexRuntime.registry().snapshot().findGunEntry(itemId)
    }

    fun resolveGunIconTextureAssetPath(stack: ItemStack): String? {
        val gunId = stack.item.registryName?.path?.trim()?.lowercase()?.ifBlank { null } ?: return null
        return GunDisplayRuntime.registry().snapshot().findDefinition(gunId)?.slotTexturePath
    }

    fun resolveAttachmentEntry(attachmentId: String): GunPackTooltipIndexEntry? {
        return GunPackTooltipIndexRuntime.registry().snapshot().findAttachmentEntry(attachmentId)
    }

    fun resolveAmmoEntry(ammoId: String): GunPackTooltipIndexEntry? {
        return GunPackTooltipIndexRuntime.registry().snapshot().findAmmoEntry(ammoId)
    }

    fun resolveBlockEntry(blockId: String): GunPackTooltipIndexEntry? {
        return GunPackTooltipIndexRuntime.registry().snapshot().findBlockEntry(blockId)
    }

    fun resolveBlockIconTextureAssetPath(entry: GunPackTooltipIndexEntry?): String? {
        val resourceId = entry?.displayId ?: entry?.itemId ?: return null
        val namespace = resourceId.substringBefore(':', missingDelimiterValue = "").trim().lowercase().ifBlank { return null }
        val path = resourceId.substringAfter(':', missingDelimiterValue = "").trim().lowercase().ifBlank { return null }
        if (!BLOCK_ICON_PATH_REGEX.matches(path)) {
            return null
        }
        return "assets/$namespace/textures/block/$path.png"
    }

    fun appendIndexedLines(
        tooltip: MutableList<String>,
        stackDisplayName: String,
        entry: GunPackTooltipIndexEntry?
    ) {
        if (entry == null) {
            return
        }

        val localizedName = localize(entry.nameKey)
        if (!localizedName.isNullOrBlank() && !isSameText(stackDisplayName, localizedName)) {
            tooltip += withDefaultColor(localizedName, NAME_COLOR)
        }

        localizeMultiline(entry.tooltipKey).forEach { line ->
            tooltip += withDefaultColor(line, DESC_COLOR)
        }
    }

    fun appendIconTokenLine(
        tooltip: MutableList<String>,
        iconTextureAssetPath: String?,
        size: Int = DEFAULT_ICON_TOKEN_SIZE
    ) {
        val tokenResource = toIconTokenResourceOrNull(iconTextureAssetPath) ?: return
        val normalizedSize = size.coerceIn(MIN_ICON_TOKEN_SIZE, MAX_ICON_TOKEN_SIZE)
        tooltip += "[icon:$tokenResource,$normalizedSize]"
    }

    internal fun toIconTokenResourceOrNull(iconTextureAssetPath: String?): String? {
        val normalized = iconTextureAssetPath
            ?.trim()
            ?.replace('\\', '/')
            ?.ifBlank { null }
            ?: return null

        val match = ASSET_TEXTURE_PATH_REGEX.find(normalized) ?: return null
        val namespace = match.groupValues[1].trim().lowercase().ifBlank { return null }
        val texturePath = match.groupValues[2]
            .trim()
            .lowercase()
            .ifBlank { return null }

        return "$namespace:$texturePath"
    }

    @Suppress("DEPRECATION")
    private fun localize(key: String?): String? {
        val normalizedKey = key?.trim()?.ifBlank { null } ?: return null
        if (I18n.canTranslate(normalizedKey)) {
            return I18n.translateToLocal(normalizedKey)
                .trim()
                .ifBlank { null }
        }

        return GunPackTooltipLangRuntime.registry().snapshot()
            .resolvePreferred(
                key = normalizedKey,
                preferredLocales = GunPackTooltipLangSnapshot.DEFAULT_LOCALE_FALLBACK
            )
            ?.trim()
            ?.ifBlank { null }
    }

    private fun localizeMultiline(key: String?): List<String> {
        val localized = localize(key) ?: return emptyList()
        val normalized = localized
            .replace("\\r\\n", "\n")
            .replace("\\n", "\n")
        return normalized
            .split(NEWLINE_REGEX)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    private fun withDefaultColor(line: String, defaultColor: String): String {
        val trimmedStart = line.trimStart()
        return if (line.startsWith(COLOR_PREFIX) || trimmedStart.startsWith(ICON_TOKEN_PREFIX)) {
            line
        } else {
            "$defaultColor$line"
        }
    }

    private fun isSameText(left: String, right: String): Boolean {
        return normalizeText(left) == normalizeText(right)
    }

    private fun normalizeText(raw: String): String {
        return raw
            .replace(COLOR_CODE_REGEX, "")
            .trim()
            .lowercase()
    }

    private const val COLOR_PREFIX: String = "§"
    private const val NAME_COLOR: String = "§e"
    private const val DESC_COLOR: String = "§7"
    private const val ICON_TOKEN_PREFIX: String = "[icon:"
    private const val DEFAULT_ICON_TOKEN_SIZE: Int = 11
    private const val MIN_ICON_TOKEN_SIZE: Int = 6
    private const val MAX_ICON_TOKEN_SIZE: Int = 32
    private val NEWLINE_REGEX: Regex = Regex("\\r?\\n")
    private val COLOR_CODE_REGEX: Regex = Regex("§.")
    private val BLOCK_ICON_PATH_REGEX: Regex = Regex("^[a-z0-9_\\-/]+$")
    private val ASSET_TEXTURE_PATH_REGEX: Regex = Regex("^assets/([^/]+)/textures/(.+)\\.(png|jpg|jpeg)$", RegexOption.IGNORE_CASE)
}
