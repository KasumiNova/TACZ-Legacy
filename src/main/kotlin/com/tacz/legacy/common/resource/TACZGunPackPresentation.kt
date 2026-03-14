package com.tacz.legacy.common.resource

import com.google.gson.JsonObject
import com.tacz.legacy.api.item.attachment.AttachmentType
import net.minecraft.util.ResourceLocation
import java.util.LinkedHashSet
import java.util.Locale

internal data class TACZAttachmentLaserConfigDefinition(
    val defaultColor: Int,
    val canEdit: Boolean,
    val length: Int,
    val width: Float,
    val thirdPersonLength: Float,
    val thirdPersonWidth: Float,
)

internal object TACZGunPackPresentation {
    @Volatile
    private var currentLanguageCodeResolverOverride: (() -> String?)? = null

    fun localeCandidates(): List<String> = localeCandidates(resolveCurrentClientLanguageCode(), Locale.getDefault())

    internal fun localeCandidates(currentLanguageCode: String?, fallbackLocale: Locale): List<String> {
        val candidates = LinkedHashSet<String>()
        if (currentLanguageCode.isNullOrBlank()) {
            addLocaleCandidates(candidates, fallbackLocale.toLanguageTag())
            addLocaleCandidates(candidates, fallbackLocale.toString())
        } else {
            addLocaleCandidates(candidates, currentLanguageCode)
        }
        candidates += DEFAULT_LOCALE
        return candidates.filter(String::isNotBlank).toList()
    }

    internal fun setCurrentLanguageCodeResolverForTests(resolver: (() -> String?)?) {
        currentLanguageCodeResolverOverride = resolver
    }

    fun localizedText(
        snapshot: TACZRuntimeSnapshot,
        key: String?,
        localeCandidates: Iterable<String> = localeCandidates(),
    ): String? {
        if (key.isNullOrBlank()) {
            return null
        }
        localeCandidates
            .map(::normalizeLocale)
            .forEach { locale ->
                snapshot.translations[locale]?.get(key)?.let { return it }
            }
        snapshot.translations["en_us"]?.get(key)?.let { return it }
        snapshot.translations.values.asSequence().mapNotNull { it[key] }.firstOrNull()?.let { return it }
        return null
    }

    fun localizedGunName(
        snapshot: TACZRuntimeSnapshot,
        gunId: ResourceLocation,
        fallback: String = prettyResourceName(gunId),
        localeCandidates: Iterable<String> = localeCandidates(),
    ): String = localizedText(snapshot, snapshot.guns[gunId]?.index?.name, localeCandidates) ?: fallback

    fun localizedGunTooltip(
        snapshot: TACZRuntimeSnapshot,
        gunId: ResourceLocation,
        localeCandidates: Iterable<String> = localeCandidates(),
    ): String? = localizedText(snapshot, snapshot.guns[gunId]?.index?.tooltip, localeCandidates)

    fun localizedAmmoName(
        snapshot: TACZRuntimeSnapshot,
        ammoId: ResourceLocation,
        fallback: String = prettyResourceName(ammoId),
        localeCandidates: Iterable<String> = localeCandidates(),
    ): String = localizedText(snapshot, snapshot.ammos[ammoId]?.name, localeCandidates) ?: fallback

    fun localizedAmmoTooltip(
        snapshot: TACZRuntimeSnapshot,
        ammoId: ResourceLocation,
        localeCandidates: Iterable<String> = localeCandidates(),
    ): String? = localizedText(snapshot, snapshot.ammos[ammoId]?.tooltip, localeCandidates)

    fun localizedAttachmentName(
        snapshot: TACZRuntimeSnapshot,
        attachmentId: ResourceLocation,
        fallback: String = prettyResourceName(attachmentId),
        localeCandidates: Iterable<String> = localeCandidates(),
    ): String = localizedText(snapshot, snapshot.attachments[attachmentId]?.index?.name, localeCandidates) ?: fallback

    fun localizedAttachmentTooltip(
        snapshot: TACZRuntimeSnapshot,
        attachmentId: ResourceLocation,
        localeCandidates: Iterable<String> = localeCandidates(),
    ): String? = localizedText(snapshot, snapshot.attachments[attachmentId]?.index?.tooltip, localeCandidates)

    fun localizedAttachmentTypeName(
        snapshot: TACZRuntimeSnapshot,
        attachmentId: ResourceLocation,
        localeCandidates: Iterable<String> = localeCandidates(),
    ): String? {
        val type = snapshot.attachments[attachmentId]?.index?.type?.takeIf(String::isNotBlank) ?: return null
        return localizedText(snapshot, "tacz.type.${type}.name", localeCandidates) ?: type
    }

    fun localizedBlockName(
        snapshot: TACZRuntimeSnapshot,
        blockId: ResourceLocation,
        fallback: String = prettyResourceName(blockId),
        localeCandidates: Iterable<String> = localeCandidates(),
    ): String = localizedText(snapshot, snapshot.blocks[blockId]?.index?.name, localeCandidates) ?: fallback

    fun localizedBlockTooltip(
        snapshot: TACZRuntimeSnapshot,
        blockId: ResourceLocation,
        localeCandidates: Iterable<String> = localeCandidates(),
    ): String? = localizedText(snapshot, snapshot.blocks[blockId]?.index?.tooltip, localeCandidates)

    fun localizedPackName(
        snapshot: TACZRuntimeSnapshot,
        id: ResourceLocation,
        fallback: String? = id.namespace,
        localeCandidates: Iterable<String> = localeCandidates(),
    ): String? {
        val key = snapshot.packInfos[id.namespace]?.name ?: return fallback
        return localizedText(snapshot, key, localeCandidates) ?: fallback
    }

    fun resolveGunDisplayId(snapshot: TACZRuntimeSnapshot, gunId: ResourceLocation): ResourceLocation? {
        val displayId = snapshot.guns[gunId]?.index?.display ?: return null
        return displayId.takeIf(snapshot.gunDisplays::containsKey)
    }

    fun resolveAmmoDisplayId(snapshot: TACZRuntimeSnapshot, ammoId: ResourceLocation): ResourceLocation? {
        val displayId = snapshot.ammos[ammoId]?.display ?: return null
        return displayId.takeIf(snapshot.ammoDisplays::containsKey)
    }

    fun resolveAttachmentDisplayId(snapshot: TACZRuntimeSnapshot, attachmentId: ResourceLocation): ResourceLocation? {
        val displayId = snapshot.attachments[attachmentId]?.index?.display ?: return null
        return displayId.takeIf(snapshot.attachmentDisplays::containsKey)
    }

    fun resolveBuiltinAttachmentId(
        snapshot: TACZRuntimeSnapshot,
        gunId: ResourceLocation,
        type: AttachmentType,
    ): ResourceLocation? {
        val builtin = snapshot.guns[gunId]?.data?.raw?.jsonObject("builtin_attachments") ?: return null
        val rawId = builtin.stringValue(type.serializedName) ?: return null
        return runCatching { ResourceLocation(rawId) }.getOrNull()
    }

    fun resolveGunIronZoom(snapshot: TACZRuntimeSnapshot, gunId: ResourceLocation): Float {
        val displayId = resolveGunDisplayId(snapshot, gunId) ?: return 1.0f
        return snapshot.gunDisplays[displayId]
            ?.raw
            ?.floatValue("iron_zoom")
            ?.coerceAtLeast(1.0f)
            ?: 1.0f
    }

    fun resolveGunLaserConfig(snapshot: TACZRuntimeSnapshot, gunId: ResourceLocation): TACZAttachmentLaserConfigDefinition? {
        val displayId = resolveGunDisplayId(snapshot, gunId) ?: return null
        val laser = snapshot.gunDisplays[displayId]?.raw?.jsonObject("laser") ?: return null
        return TACZAttachmentLaserConfigDefinition(
            defaultColor = parseLaserColor(laser.stringValue("default_color")),
            canEdit = laser.booleanValue("can_edit", defaultValue = true),
            length = laser.intValue("length")
                .takeIf { it > 0 }
                ?: 25,
            width = laser.floatValue("width")
                ?.takeIf { it > 0.0f }
                ?: 0.008f,
            thirdPersonLength = laser.floatValue("third_person_length")
                ?.takeIf { it > 0.0f }
                ?: 2.0f,
            thirdPersonWidth = laser.floatValue("third_person_width")
                ?.takeIf { it > 0.0f }
                ?: 0.008f,
        )
    }

    fun resolveAttachmentZoomLevels(snapshot: TACZRuntimeSnapshot, attachmentId: ResourceLocation): FloatArray? {
        val displayId = resolveAttachmentDisplayId(snapshot, attachmentId) ?: return null
        val zoomValues = snapshot.attachmentDisplays[displayId]
            ?.raw
            ?.getAsJsonArray("zoom")
            ?.mapNotNull { value -> runCatching { value.asFloat.coerceAtLeast(1.0f) }.getOrNull() }
            .orEmpty()
        return zoomValues.takeIf(List<Float>::isNotEmpty)?.toFloatArray()
    }

    fun resolveAttachmentLaserConfig(snapshot: TACZRuntimeSnapshot, attachmentId: ResourceLocation): TACZAttachmentLaserConfigDefinition? {
        val displayId = resolveAttachmentDisplayId(snapshot, attachmentId) ?: return null
        val laser = snapshot.attachmentDisplays[displayId]?.raw?.jsonObject("laser") ?: return null
        return TACZAttachmentLaserConfigDefinition(
            defaultColor = parseLaserColor(laser.stringValue("default_color")),
            canEdit = laser.booleanValue("can_edit", defaultValue = true),
            length = laser.intValue("length")
                .takeIf { it > 0 }
                ?: 25,
            width = laser.floatValue("width")
                ?.takeIf { it > 0.0f }
                ?: 0.008f,
            thirdPersonLength = laser.floatValue("third_person_length")
                ?.takeIf { it > 0.0f }
                ?: 2.0f,
            thirdPersonWidth = laser.floatValue("third_person_width")
                ?.takeIf { it > 0.0f }
                ?: 0.008f,
        )
    }

    fun resolveBlockDisplayId(snapshot: TACZRuntimeSnapshot, blockId: ResourceLocation): ResourceLocation? {
        val displayId = snapshot.blocks[blockId]?.index?.display ?: return null
        return displayId.takeIf(snapshot.blockDisplays::containsKey)
    }

    fun sortedGuns(snapshot: TACZRuntimeSnapshot): List<TACZLoadedGun> =
        snapshot.guns.values.sortedWith(compareBy<TACZLoadedGun> { it.index.sort }.thenBy { it.id.toString() })

    fun sortedAttachments(snapshot: TACZRuntimeSnapshot, includeHidden: Boolean = false): List<TACZLoadedAttachment> =
        snapshot.attachments.values
            .asSequence()
            .filter { includeHidden || !it.index.hidden }
            .sortedWith(compareBy<TACZLoadedAttachment> { it.index.sort }.thenBy { it.id.toString() })
            .toList()

    fun sortedAmmos(snapshot: TACZRuntimeSnapshot): List<Map.Entry<ResourceLocation, TACZAmmoIndexDefinition>> =
        snapshot.ammos.entries.sortedBy { it.key.toString() }

    fun sortedBlocksForItem(snapshot: TACZRuntimeSnapshot, itemId: ResourceLocation): List<TACZLoadedBlock> =
        snapshot.blocks.values
            .asSequence()
            .filter { it.index.id == itemId }
            .sortedWith(compareBy<TACZLoadedBlock> { it.index.sort }.thenBy { it.id.toString() })
            .toList()

    fun workbenchTabs(snapshot: TACZRuntimeSnapshot, blockId: ResourceLocation): List<TACZWorkbenchTabDefinition> =
        snapshot.blocks[blockId]?.data?.tabs.orEmpty()

    fun localizedWorkbenchTabs(
        snapshot: TACZRuntimeSnapshot,
        blockId: ResourceLocation,
        localeCandidates: Iterable<String> = localeCandidates(),
    ): List<String> = workbenchTabs(snapshot, blockId).map { tab ->
        localizedText(snapshot, tab.name, localeCandidates) ?: prettyResourceName(tab.id)
    }

    fun resolveRecipeFilter(snapshot: TACZRuntimeSnapshot, blockId: ResourceLocation): TACZRecipeFilterDefinition? {
        val filterId = snapshot.blocks[blockId]?.data?.filter ?: return null
        return snapshot.recipeFilters[filterId]
    }

    fun visibleRecipeCount(snapshot: TACZRuntimeSnapshot, blockId: ResourceLocation): Int {
        val block = snapshot.blocks[blockId] ?: return 0
        val filter = resolveRecipeFilter(snapshot, blockId)
        if (snapshot.recipes.isEmpty()) {
            return 0
        }
        if (filter == null) {
            return 0
        }
        return snapshot.recipes.keys.count { recipeId -> filter.allows(recipeId) }
    }

    fun allowsAttachment(snapshot: TACZRuntimeSnapshot, gunId: ResourceLocation, attachmentId: ResourceLocation): Boolean {
        val allowEntries = snapshot.allowAttachmentTags[gunId] ?: return false
        return matchesAttachmentEntries(snapshot, allowEntries, attachmentId, mutableSetOf())
    }

    fun compatibleGunCount(snapshot: TACZRuntimeSnapshot, attachmentId: ResourceLocation): Int =
        snapshot.guns.keys.count { gunId -> allowsAttachment(snapshot, gunId, attachmentId) }

    fun prettyResourceName(id: ResourceLocation): String {
        val path = id.path.substringAfterLast('/')
        return path
            .split('_', '-')
            .filter(String::isNotBlank)
            .joinToString(" ") { segment -> segment.replaceFirstChar { char -> char.titlecase(Locale.ROOT) } }
            .ifBlank { id.toString() }
    }

    private fun matchesAttachmentEntries(
        snapshot: TACZRuntimeSnapshot,
        entries: Set<String>,
        attachmentId: ResourceLocation,
        visitedTags: MutableSet<ResourceLocation>,
    ): Boolean {
        entries.forEach { value ->
            if (value.startsWith(TAG_PREFIX)) {
                val tagId = runCatching { ResourceLocation(value.substring(TAG_PREFIX.length)) }.getOrNull() ?: return@forEach
                if (!visitedTags.add(tagId)) {
                    return@forEach
                }
                val tagEntries = snapshot.attachmentTags[tagId] ?: return@forEach
                if (matchesAttachmentEntries(snapshot, tagEntries, attachmentId, visitedTags)) {
                    return true
                }
                return@forEach
            }
            val targetId = runCatching { ResourceLocation(value) }.getOrNull() ?: return@forEach
            if (targetId == attachmentId) {
                return true
            }
        }
        return false
    }

    private fun resolveCurrentClientLanguageCode(): String? {
        currentLanguageCodeResolverOverride?.invoke()?.takeIf(String::isNotBlank)?.let { return it }
        return runCatching {
            val minecraftClass = Class.forName("net.minecraft.client.Minecraft")
            val minecraft = minecraftClass.getMethod("getMinecraft").invoke(null) ?: return null
            val gameSettings = minecraftClass.getField("gameSettings").get(minecraft) ?: return null
            gameSettings.javaClass.getField("language").get(gameSettings) as? String
        }.getOrNull()?.takeIf(String::isNotBlank)
    }

    private fun addLocaleCandidates(target: MutableSet<String>, rawLocale: String?) {
        val normalized = normalizeLocale(rawLocale.orEmpty())
        if (normalized.isBlank()) {
            return
        }
        target += normalized
        normalized.substringBefore('_').takeIf { it.isNotBlank() && it != normalized }?.let(target::add)
    }

    private fun normalizeLocale(locale: String): String = locale.lowercase(Locale.ROOT).replace('-', '_')

    private fun parseLaserColor(rawValue: String?): Int {
        val normalized = rawValue?.trim().orEmpty()
        if (normalized.isBlank()) {
            return DEFAULT_LASER_COLOR
        }
        return runCatching { Integer.decode(normalized) and 0xFFFFFF }.getOrDefault(INVALID_LASER_COLOR)
    }

    private const val TAG_PREFIX: String = "#"
    private const val DEFAULT_LASER_COLOR: Int = 0xFF0000
    private const val INVALID_LASER_COLOR: Int = 0xFFFFFF
    private const val DEFAULT_LOCALE: String = "en_us"
}

private fun JsonObject.jsonObject(key: String): JsonObject? =
    get(key)?.takeIf { it.isJsonObject }?.asJsonObject

private fun JsonObject.stringValue(key: String): String? =
    get(key)?.takeIf { !it.isJsonNull }?.asString?.trim()?.takeIf(String::isNotBlank)

private fun JsonObject.floatValue(key: String): Float? =
    get(key)?.takeIf { !it.isJsonNull }?.asFloat

private fun JsonObject.booleanValue(key: String, defaultValue: Boolean): Boolean =
    get(key)?.takeIf { !it.isJsonNull }?.asBoolean ?: defaultValue

private fun JsonObject.intValue(key: String): Int =
    get(key)?.takeIf { !it.isJsonNull }?.asInt ?: 0
