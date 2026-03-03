package com.tacz.legacy.common.application.gunpack

import com.tacz.legacy.common.domain.gunpack.GunData

public data class GunIdConflictEntry(
    val gunId: String,
    val winnerSourceId: String,
    val loserSourceIds: List<String>
)

public data class GunPackRuntimeSnapshot(
    val loadedAtEpochMillis: Long,
    val totalSources: Int,
    val loadedGunsBySourceId: Map<String, GunData>,
    val loadedGunsByAmmoId: Map<String, List<GunData>>,
    val sourceIdByGunId: Map<String, String>,
    val gunTypeByGunId: Map<String, String> = emptyMap(),
    val duplicateGunIdSources: Map<String, List<String>>,
    val failedSources: Set<String>,
    val warningSources: Set<String>,
    val issueHistogram: Map<String, Int>
) {

    public val loadedCount: Int
        get() = loadedGunsBySourceId.size

    public fun find(sourceId: String): GunData? = loadedGunsBySourceId[sourceId]

    public fun findBySourceId(sourceId: String): GunData? = loadedGunsBySourceId[sourceId]

    public fun findByAmmoId(ammoId: String): List<GunData> = loadedGunsByAmmoId[ammoId].orEmpty()

    public fun findByGunId(gunId: String): GunData? =
        sourceIdByGunId[gunId]?.let { sourceId -> loadedGunsBySourceId[sourceId] }

    public fun findGunType(gunId: String): String? = gunTypeByGunId[gunId]

    public fun hasDuplicateConflicts(): Boolean = duplicateGunIdSources.isNotEmpty()

    public fun conflictEntries(): List<GunIdConflictEntry> = duplicateGunIdSources.entries
        .mapNotNull { (gunId, sources) ->
            val winner = sourceIdByGunId[gunId] ?: sources.firstOrNull() ?: return@mapNotNull null
            GunIdConflictEntry(
                gunId = gunId,
                winnerSourceId = winner,
                loserSourceIds = sources.filterNot { it == winner }
            )
        }
        .sortedBy { it.gunId }

    public fun findConflict(gunId: String): GunIdConflictEntry? =
        conflictEntries().firstOrNull { it.gunId == gunId }

    public companion object {
        public fun empty(atEpochMillis: Long = System.currentTimeMillis()): GunPackRuntimeSnapshot =
            GunPackRuntimeSnapshot(
                loadedAtEpochMillis = atEpochMillis,
                totalSources = 0,
                loadedGunsBySourceId = emptyMap(),
                loadedGunsByAmmoId = emptyMap(),
                sourceIdByGunId = emptyMap(),
                gunTypeByGunId = emptyMap(),
                duplicateGunIdSources = emptyMap(),
                failedSources = emptySet(),
                warningSources = emptySet(),
                issueHistogram = emptyMap()
            )
    }

}

public class GunPackRuntimeRegistry {

    @Volatile
    private var latestSnapshot: GunPackRuntimeSnapshot = GunPackRuntimeSnapshot.empty()

    @Synchronized
    public fun replace(batchReport: GunPackCompatibilityBatchReport?): GunPackRuntimeSnapshot {
        val now = System.currentTimeMillis()
        if (batchReport == null) {
            latestSnapshot = GunPackRuntimeSnapshot.empty(now)
            return latestSnapshot
        }

        val loadedGunsBySourceId = linkedMapOf<String, GunData>()
        val loadedGunsByAmmoId = linkedMapOf<String, MutableList<GunData>>()
        val sourceIdByGunId = linkedMapOf<String, String>()
        val gunTypeByGunId = linkedMapOf<String, String>()
        val duplicateGunIdSources = linkedMapOf<String, List<String>>()
        val failedSources = linkedSetOf<String>()
        val warningSources = linkedSetOf<String>()

        val entriesWithData = batchReport.entries.filter { it.result.gunData != null }
        val gunIdGroups = entriesWithData
            .groupBy { it.result.gunData!!.gunId }
            .mapValues { (_, entries) -> entries.sortedBy { it.sourceId } }

        gunIdGroups.forEach { (gunId, entries) ->
            if (entries.size <= 1) {
                return@forEach
            }

            val allSources = entries.map { it.sourceId }
            duplicateGunIdSources[gunId] = allSources
            allSources.forEach { sourceId -> warningSources += sourceId }
        }

        val winnerSources = gunIdGroups
            .values
            .map { entries -> entries.first().sourceId }
            .toSet()

        batchReport.entries.forEach { entry ->
            val parsed = entry.result.gunData
            if (parsed != null && winnerSources.contains(entry.sourceId)) {
                loadedGunsBySourceId[entry.sourceId] = parsed
                sourceIdByGunId[parsed.gunId] = entry.sourceId
                loadedGunsByAmmoId
                    .computeIfAbsent(parsed.ammoId) { mutableListOf() }
                    .add(parsed)
            }

            if (parsed == null || entry.result.report.hasErrors()) {
                failedSources += entry.sourceId
            }
            if (entry.result.report.hasWarnings()) {
                warningSources += entry.sourceId
            }
        }

        sourceIdByGunId.keys.forEach { gunId ->
            val hintedType = batchReport.gunTypeHintsByGunId[gunId] ?: return@forEach
            normalizeGunType(hintedType)?.let { normalizedType ->
                gunTypeByGunId[gunId] = normalizedType
            }
        }

        val issueHistogram = linkedMapOf<String, Int>()
        issueHistogram.putAll(batchReport.issueCodeHistogram())
        val duplicateDropCount = duplicateGunIdSources.values.sumOf { sources -> sources.size - 1 }
        if (duplicateDropCount > 0) {
            issueHistogram[DUPLICATE_GUN_ID_CONFLICT] = duplicateDropCount
        }

        latestSnapshot = GunPackRuntimeSnapshot(
            loadedAtEpochMillis = now,
            totalSources = batchReport.total,
            loadedGunsBySourceId = loadedGunsBySourceId.toMap(),
            loadedGunsByAmmoId = loadedGunsByAmmoId.mapValues { (_, value) -> value.toList() },
            sourceIdByGunId = sourceIdByGunId.toMap(),
            gunTypeByGunId = gunTypeByGunId.toMap(),
            duplicateGunIdSources = duplicateGunIdSources.toMap(),
            failedSources = failedSources,
            warningSources = warningSources,
            issueHistogram = issueHistogram
        )
        return latestSnapshot
    }

    @Synchronized
    public fun clear(): GunPackRuntimeSnapshot {
        latestSnapshot = GunPackRuntimeSnapshot.empty()
        return latestSnapshot
    }

    public fun snapshot(): GunPackRuntimeSnapshot = latestSnapshot

}

public object GunPackRuntime {

    private val registry: GunPackRuntimeRegistry = GunPackRuntimeRegistry()

    public fun registry(): GunPackRuntimeRegistry = registry

}

private const val DUPLICATE_GUN_ID_CONFLICT: String = "DUPLICATE_GUN_ID_CONFLICT"

private fun normalizeGunType(raw: String): String? {
    val withoutNamespace = raw.trim().lowercase().substringAfter(':')
    val normalized = withoutNamespace
        .map { ch -> if (ch.isLetterOrDigit()) ch else '_' }
        .joinToString(separator = "")
        .replace("__+".toRegex(), "_")
        .trim('_')
    return normalized.ifBlank { null }
}