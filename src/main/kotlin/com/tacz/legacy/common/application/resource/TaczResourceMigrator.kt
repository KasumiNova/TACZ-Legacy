package com.tacz.legacy.common.application.resource

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

public data class TaczResourceMigrationEntry(
    val sourcePath: String,
    val targetPath: String?,
    val action: ResourceMappingAction,
    val note: String
)

public data class TaczResourceMigrationManifest(
    val schemaVersion: Int,
    val generatedAtEpochMillis: Long,
    val sourceRoot: String,
    val totalFiles: Int,
    val mappedCount: Int,
    val coverageRatio: Double,
    val actionCounts: Map<ResourceMappingAction, Int>,
    val manualReviewSamples: List<String>,
    val entries: List<TaczResourceMigrationEntry>
)

public class TaczResourceMigrator(
    private val mapper: TaczResourcePathMapper = TaczResourcePathMapper(),
    private val summaryBuilder: TaczResourceMappingManifestBuilder = TaczResourceMappingManifestBuilder(mapper)
) {

    public fun buildManifestFromSourceRoot(sourceRoot: Path): TaczResourceMigrationManifest {
        val normalizedRoot = sourceRoot.toAbsolutePath().normalize()
        val relativePaths = collectRelativePaths(normalizedRoot)
        return buildManifestFromRelativePaths(relativePaths, normalizedRoot.toString())
    }

    public fun buildManifestFromRelativePaths(
        relativePaths: Collection<String>,
        sourceRootLabel: String = "assets/tacz"
    ): TaczResourceMigrationManifest {
        val normalizedPaths = relativePaths
            .map { path -> path.trim() }
            .filter { path -> path.isNotEmpty() }
            .sorted()

        val summary = summaryBuilder.build(normalizedPaths)
        val decisions = normalizedPaths.map { path -> mapper.map(path) }
        val actionCounts = ResourceMappingAction.entries
            .associateWith { action -> decisions.count { decision -> decision.action == action } }
            .toMap()

        return TaczResourceMigrationManifest(
            schemaVersion = MANIFEST_SCHEMA_VERSION,
            generatedAtEpochMillis = System.currentTimeMillis(),
            sourceRoot = sourceRootLabel,
            totalFiles = summary.totalFiles,
            mappedCount = summary.mappedCount,
            coverageRatio = summary.coverageRatio,
            actionCounts = actionCounts,
            manualReviewSamples = summary.manualReviewSamples,
            entries = decisions.map { decision ->
                TaczResourceMigrationEntry(
                    sourcePath = decision.sourcePath,
                    targetPath = decision.targetPath,
                    action = decision.action,
                    note = decision.note
                )
            }
        )
    }

    private fun collectRelativePaths(sourceRoot: Path): List<String> {
        val result = mutableListOf<String>()
        Files.walk(sourceRoot).use { stream ->
            stream
                .filter { path -> Files.isRegularFile(path) }
                .forEach { path ->
                    result += sourceRoot
                        .relativize(path)
                        .toString()
                        .replace('\\', '/')
                }
        }
        return result
    }

    private companion object {
        private const val MANIFEST_SCHEMA_VERSION: Int = 1
    }

}

public class TaczResourceMigrationManifestWriter(
    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .create()
) {

    public fun toJson(manifest: TaczResourceMigrationManifest): String = gson.toJson(manifest)

    public fun writeToFile(manifest: TaczResourceMigrationManifest, outputFile: Path): Path {
        outputFile.parent?.let { parent ->
            Files.createDirectories(parent)
        }
        Files.write(outputFile, toJson(manifest).toByteArray(StandardCharsets.UTF_8))
        return outputFile
    }

}
