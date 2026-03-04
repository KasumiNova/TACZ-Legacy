package com.tacz.legacy.common.application.gunpack

public data class GunPackTooltipIndexEntry(
    val itemId: String,
    val sourceId: String,
    val nameKey: String?,
    val tooltipKey: String?,
    val displayId: String?,
    val type: String?
)

public data class GunPackTooltipIndexSnapshot(
    val loadedAtEpochMillis: Long,
    val gunEntriesById: Map<String, GunPackTooltipIndexEntry>,
    val attachmentEntriesById: Map<String, GunPackTooltipIndexEntry>,
    val ammoEntriesById: Map<String, GunPackTooltipIndexEntry>,
    val blockEntriesById: Map<String, GunPackTooltipIndexEntry>,
    val failedSources: Set<String> = emptySet()
) {

    public val totalEntries: Int
        get() = gunEntriesById.size + attachmentEntriesById.size + ammoEntriesById.size + blockEntriesById.size

    public fun findGunEntry(gunId: String): GunPackTooltipIndexEntry? =
        gunEntriesById[normalizeResourceIdOrNull(gunId)]

    public fun findAttachmentEntry(attachmentId: String): GunPackTooltipIndexEntry? =
        attachmentEntriesById[normalizeResourceIdOrNull(attachmentId)]

    public fun findAmmoEntry(ammoId: String): GunPackTooltipIndexEntry? =
        ammoEntriesById[normalizeResourceIdOrNull(ammoId)]

    public fun findBlockEntry(blockId: String): GunPackTooltipIndexEntry? =
        blockEntriesById[normalizeResourceIdOrNull(blockId)]

    public fun resolveGunTooltipKey(gunId: String): String? = findGunEntry(gunId)?.tooltipKey

    public fun resolveAttachmentTooltipKey(attachmentId: String): String? = findAttachmentEntry(attachmentId)?.tooltipKey

    public fun resolveAmmoTooltipKey(ammoId: String): String? = findAmmoEntry(ammoId)?.tooltipKey

    public fun resolveBlockTooltipKey(blockId: String): String? = findBlockEntry(blockId)?.tooltipKey

    public companion object {
        private const val DEFAULT_NAMESPACE: String = "tacz"

        public fun empty(atEpochMillis: Long = System.currentTimeMillis()): GunPackTooltipIndexSnapshot =
            GunPackTooltipIndexSnapshot(
                loadedAtEpochMillis = atEpochMillis,
                gunEntriesById = emptyMap(),
                attachmentEntriesById = emptyMap(),
                ammoEntriesById = emptyMap(),
                blockEntriesById = emptyMap(),
                failedSources = emptySet()
            )

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

public class GunPackTooltipIndexRuntimeRegistry {

    @Volatile
    private var latestSnapshot: GunPackTooltipIndexSnapshot = GunPackTooltipIndexSnapshot.empty()

    @Synchronized
    public fun replace(snapshot: GunPackTooltipIndexSnapshot): GunPackTooltipIndexSnapshot {
        latestSnapshot = snapshot
        return latestSnapshot
    }

    @Synchronized
    public fun clear(): GunPackTooltipIndexSnapshot {
        latestSnapshot = GunPackTooltipIndexSnapshot.empty()
        return latestSnapshot
    }

    public fun snapshot(): GunPackTooltipIndexSnapshot = latestSnapshot
}

public object GunPackTooltipIndexRuntime {

    private val registry: GunPackTooltipIndexRuntimeRegistry = GunPackTooltipIndexRuntimeRegistry()

    public fun registry(): GunPackTooltipIndexRuntimeRegistry = registry
}
