package com.tacz.legacy.common.application.gunpack

public data class GunPackCompatibilitySource(
    val sourceId: String,
    val json: String
)

public data class GunPackCompatibilityEntry(
    val sourceId: String,
    val result: GunPackParseResult
)

public data class GunPackCompatibilityBatchReport(
    val entries: List<GunPackCompatibilityEntry>,
    val gunTypeHintsByGunId: Map<String, String> = emptyMap()
) {

    public val total: Int
        get() = entries.size

    public val successCount: Int
        get() = entries.count { it.result.gunData != null }

    public val failedCount: Int
        get() = entries.count { it.result.gunData == null || it.result.report.hasErrors() }

    public val warningCount: Int
        get() = entries.count { it.result.report.hasWarnings() }

    public fun issueCodeHistogram(): Map<String, Int> {
        val histogram = linkedMapOf<String, Int>()
        entries.flatMap { it.result.report.allIssues() }.forEach { issue ->
            histogram[issue.code] = (histogram[issue.code] ?: 0) + 1
        }
        return histogram
    }

}

public class GunPackCompatibilityBatchAnalyzer(
    private val parser: GunPackCompatibilityParser = GunPackCompatibilityParser()
) {

    public fun analyze(
        sources: Collection<GunPackCompatibilitySource>,
        gunTypeHintsByGunId: Map<String, String> = emptyMap()
    ): GunPackCompatibilityBatchReport {
        val entries = sources.map { source ->
            GunPackCompatibilityEntry(
                sourceId = source.sourceId,
                result = parser.parseGunDataJson(source.json, source.sourceId)
            )
        }
        return GunPackCompatibilityBatchReport(
            entries = entries,
            gunTypeHintsByGunId = gunTypeHintsByGunId
        )
    }

}