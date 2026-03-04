package com.tacz.legacy.common.application.gunpack

public data class WeaponAttachmentDefinition(
    val attachmentId: String,
    val attachmentType: String?,
    val sourceId: String,
    val displayId: String?,
    val iconTextureAssetPath: String?
)

public data class WeaponAttachmentCompatibilitySnapshot(
    val loadedAtEpochMillis: Long,
    val attachmentsById: Map<String, WeaponAttachmentDefinition>,
    val allowEntriesByGunId: Map<String, Set<String>>,
    val tagsByTagId: Map<String, Set<String>>,
    val ammoIconTextureByAmmoId: Map<String, String>,
    val failedSources: Set<String> = emptySet()
) {

    public val totalAttachments: Int
        get() = attachmentsById.size

    public fun findAttachmentDefinition(attachmentId: String): WeaponAttachmentDefinition? {
        return attachmentsById[normalizeResourceIdOrNull(attachmentId)]
    }

    public fun resolveAttachmentType(attachmentId: String): String? {
        return findAttachmentDefinition(attachmentId)?.attachmentType
    }

    public fun resolveAmmoIconTexturePath(ammoId: String): String? {
        return ammoIconTextureByAmmoId[normalizeResourceIdOrNull(ammoId)]
    }

    public fun resolveAllowedAttachmentIds(
        gunId: String,
        definitionAllowEntries: Set<String> = emptySet()
    ): Set<String>? {
        val normalizedGunPath = normalizeGunIdToPath(gunId)
        val fromTags = allowEntriesByGunId[normalizedGunPath].orEmpty()
        val mergedEntries = linkedSetOf<String>()
        mergedEntries.addAll(fromTags)
        definitionAllowEntries
            .mapNotNull { normalizeAllowEntryOrNull(it, defaultNamespace = DEFAULT_NAMESPACE) }
            .forEach(mergedEntries::add)

        if (mergedEntries.isEmpty()) {
            return null
        }

        val expanded = expandAllowEntries(mergedEntries)
        if (expanded.isEmpty()) {
            return null
        }

        val knownAttachmentIds = attachmentsById.keys
        val filtered = expanded.filterTo(linkedSetOf()) { id -> knownAttachmentIds.contains(id) }
        if (filtered.isEmpty()) {
            return null
        }

        return filtered
    }

    public fun isAttachmentAllowed(
        gunId: String,
        attachmentId: String,
        definitionAllowEntries: Set<String> = emptySet()
    ): Boolean {
        val normalizedAttachmentId = normalizeResourceIdOrNull(attachmentId) ?: return false
        val allowSet = resolveAllowedAttachmentIds(
            gunId = gunId,
            definitionAllowEntries = definitionAllowEntries
        ) ?: return true
        return allowSet.contains(normalizedAttachmentId)
    }

    private fun expandAllowEntries(entries: Set<String>): Set<String> {
        val resolved = linkedSetOf<String>()
        val visitedTags = linkedSetOf<String>()
        entries.forEach { entry ->
            expandAllowEntry(entry, resolved, visitedTags)
        }
        return resolved
    }

    private fun expandAllowEntry(
        entry: String,
        resolved: MutableSet<String>,
        visitedTags: MutableSet<String>
    ) {
        if (entry.startsWith("#")) {
            val normalizedTag = normalizeTagIdOrNull(entry) ?: return
            if (!visitedTags.add(normalizedTag)) {
                return
            }
            tagsByTagId[normalizedTag].orEmpty().forEach { child ->
                expandAllowEntry(child, resolved, visitedTags)
            }
            return
        }

        normalizeResourceIdOrNull(entry)?.let(resolved::add)
    }

    public companion object {
        private const val DEFAULT_NAMESPACE: String = "tacz"

        public fun empty(atEpochMillis: Long = System.currentTimeMillis()): WeaponAttachmentCompatibilitySnapshot {
            return WeaponAttachmentCompatibilitySnapshot(
                loadedAtEpochMillis = atEpochMillis,
                attachmentsById = emptyMap(),
                allowEntriesByGunId = emptyMap(),
                tagsByTagId = emptyMap(),
                ammoIconTextureByAmmoId = emptyMap(),
                failedSources = emptySet()
            )
        }

        internal fun normalizeAllowEntryOrNull(raw: String?, defaultNamespace: String): String? {
            val trimmed = raw?.trim()?.ifBlank { null } ?: return null
            return if (trimmed.startsWith("#")) {
                normalizeTagIdOrNull(trimmed, defaultNamespace)
            } else {
                normalizeResourceIdOrNull(trimmed, defaultNamespace)
            }
        }

        internal fun normalizeTagIdOrNull(raw: String?, defaultNamespace: String = DEFAULT_NAMESPACE): String? {
            val trimmed = raw?.trim()?.ifBlank { null } ?: return null
            val withoutPrefix = trimmed.removePrefix("#")
            val normalized = normalizeResourceIdOrNull(withoutPrefix, defaultNamespace) ?: return null
            return "#$normalized"
        }

        internal fun normalizeResourceIdOrNull(raw: String?, defaultNamespace: String = DEFAULT_NAMESPACE): String? {
            val trimmed = raw?.trim()?.ifBlank { null } ?: return null
            val namespace = trimmed.substringBefore(':').takeIf { trimmed.contains(':') }?.trim()?.lowercase()
                ?: defaultNamespace
            val pathRaw = if (trimmed.contains(':')) trimmed.substringAfter(':') else trimmed
            val path = normalizePath(pathRaw) ?: return null
            if (namespace.isBlank()) {
                return null
            }
            return "$namespace:$path"
        }

        internal fun normalizeGunIdToPath(raw: String?): String {
            val normalizedResource = normalizeResourceIdOrNull(raw)
            if (normalizedResource != null) {
                return normalizedResource.substringAfter(':')
            }
            return normalizePath(raw).orEmpty()
        }

        internal fun normalizePath(raw: String?): String? {
            val normalized = raw
                ?.trim()
                ?.lowercase()
                ?.map { ch -> if (ch.isLetterOrDigit() || ch == '_' || ch == '/' || ch == '-') ch else '_' }
                ?.joinToString(separator = "")
                ?.replace("__+".toRegex(), "_")
                ?.replace("//+".toRegex(), "/")
                ?.trim('_', '/')
            return normalized?.ifBlank { null }
        }
    }
}

public class WeaponAttachmentCompatibilityRuntimeRegistry {

    @Volatile
    private var latestSnapshot: WeaponAttachmentCompatibilitySnapshot = WeaponAttachmentCompatibilitySnapshot.empty()

    @Synchronized
    public fun replace(snapshot: WeaponAttachmentCompatibilitySnapshot): WeaponAttachmentCompatibilitySnapshot {
        latestSnapshot = snapshot
        return latestSnapshot
    }

    @Synchronized
    public fun clear(): WeaponAttachmentCompatibilitySnapshot {
        latestSnapshot = WeaponAttachmentCompatibilitySnapshot.empty()
        return latestSnapshot
    }

    public fun snapshot(): WeaponAttachmentCompatibilitySnapshot = latestSnapshot
}

public object WeaponAttachmentCompatibilityRuntime {

    private val registry: WeaponAttachmentCompatibilityRuntimeRegistry = WeaponAttachmentCompatibilityRuntimeRegistry()

    public fun registry(): WeaponAttachmentCompatibilityRuntimeRegistry = registry
}
