package com.tacz.legacy.common.application.resource

public enum class ResourceMappingAction {
    DIRECT_COPY,
    COPY_TO_GUNPACK,
    CONVERT_LANG_JSON_TO_LANG,
    IGNORE,
    MANUAL_REVIEW
}

public data class TaczResourceMappingDecision(
    val sourcePath: String,
    val targetPath: String?,
    val action: ResourceMappingAction,
    val note: String
)

public data class TaczResourceMappingSummary(
    val totalFiles: Int,
    val directCopyCount: Int,
    val gunpackCopyCount: Int,
    val langConvertCount: Int,
    val ignoredCount: Int,
    val manualReviewCount: Int,
    val manualReviewSamples: List<String>
) {

    public val mappedCount: Int
        get() = totalFiles - manualReviewCount

    public val coverageRatio: Double
        get() = if (totalFiles == 0) 1.0 else mappedCount.toDouble() / totalFiles.toDouble()

}

public class TaczResourceMappingManifestBuilder(
    private val mapper: TaczResourcePathMapper = TaczResourcePathMapper()
) {

    public fun build(relativePaths: Collection<String>): TaczResourceMappingSummary {
        val decisions = relativePaths.map { path -> mapper.map(path) }
        return TaczResourceMappingSummary(
            totalFiles = decisions.size,
            directCopyCount = decisions.count { it.action == ResourceMappingAction.DIRECT_COPY },
            gunpackCopyCount = decisions.count { it.action == ResourceMappingAction.COPY_TO_GUNPACK },
            langConvertCount = decisions.count { it.action == ResourceMappingAction.CONVERT_LANG_JSON_TO_LANG },
            ignoredCount = decisions.count { it.action == ResourceMappingAction.IGNORE },
            manualReviewCount = decisions.count { it.action == ResourceMappingAction.MANUAL_REVIEW },
            manualReviewSamples = decisions
                .asSequence()
                .filter { it.action == ResourceMappingAction.MANUAL_REVIEW }
                .map { it.sourcePath }
                .take(MANUAL_SAMPLE_LIMIT)
                .toList()
        )
    }

    private companion object {
        private const val MANUAL_SAMPLE_LIMIT: Int = 20
    }

}

public class TaczResourcePathMapper {

    public fun map(path: String): TaczResourceMappingDecision {
        val normalized = normalize(path)
        if (normalized.isBlank()) {
            return TaczResourceMappingDecision(
                sourcePath = normalized,
                targetPath = null,
                action = ResourceMappingAction.MANUAL_REVIEW,
                note = "Empty path after normalization."
            )
        }

        if (normalized == "sounds.json") {
            return directCopy(normalized)
        }

        if (normalized.startsWith("custom/")) {
            return mapCustomPackFile(normalized)
        }

        if (normalized.startsWith("lang/")) {
            return mapLanguageFile(normalized)
        }

        if (DIRECT_COPY_PREFIXES.any { prefix -> normalized.startsWith(prefix) }) {
            return directCopy(normalized)
        }

        return TaczResourceMappingDecision(
            sourcePath = normalized,
            targetPath = null,
            action = ResourceMappingAction.MANUAL_REVIEW,
            note = "No mapping rule matched top-level path."
        )
    }

    private fun mapCustomPackFile(normalized: String): TaczResourceMappingDecision {
        val segments = normalized.split('/')
        if (segments.size < 3) {
            return TaczResourceMappingDecision(
                sourcePath = normalized,
                targetPath = null,
                action = ResourceMappingAction.MANUAL_REVIEW,
                note = "Custom pack path is incomplete."
            )
        }

        val packId = segments[1]
        val packRelative = segments.drop(2).joinToString("/")

        if (packRelative.equals("README.txt", ignoreCase = true)) {
            return TaczResourceMappingDecision(
                sourcePath = normalized,
                targetPath = null,
                action = ResourceMappingAction.IGNORE,
                note = "Pack readme is documentation-only and not runtime data."
            )
        }

        return TaczResourceMappingDecision(
            sourcePath = normalized,
            targetPath = "run/tacz/$packId/$packRelative",
            action = ResourceMappingAction.COPY_TO_GUNPACK,
            note = "Copy as TACZ-compatible external gunpack resource."
        )
    }

    private fun mapLanguageFile(normalized: String): TaczResourceMappingDecision {
        if (normalized.endsWith(".json")) {
            val fileName = normalized.substringAfterLast('/').substringBeforeLast('.')
            return TaczResourceMappingDecision(
                sourcePath = normalized,
                targetPath = "$TARGET_ASSET_ROOT/lang/$fileName.lang",
                action = ResourceMappingAction.CONVERT_LANG_JSON_TO_LANG,
                note = "Convert 1.20 json language file into 1.12 .lang format."
            )
        }

        return directCopy(normalized)
    }

    private fun directCopy(normalized: String): TaczResourceMappingDecision =
        TaczResourceMappingDecision(
            sourcePath = normalized,
            targetPath = "$TARGET_ASSET_ROOT/$normalized",
            action = ResourceMappingAction.DIRECT_COPY,
            note = "Path can be copied directly into Legacy assets."
        )

    private fun normalize(path: String): String {
        val slashNormalized = path.trim().replace('\\', '/')
        val trimmedPrefix = slashNormalized
            .removePrefix("src/main/resources/assets/tacz/")
            .removePrefix("assets/tacz/")
            .trimStart('/')
        return trimmedPrefix
    }

    private companion object {
        private const val TARGET_ASSET_ROOT: String = "src/main/resources/assets/tacz"

        private val DIRECT_COPY_PREFIXES: Set<String> = setOf(
            "textures/",
            "models/",
            "sounds/",
            "particles/",
            "blockstates/",
            "animations/"
        )
    }

}
