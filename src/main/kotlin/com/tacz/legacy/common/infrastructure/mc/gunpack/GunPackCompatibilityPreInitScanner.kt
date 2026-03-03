package com.tacz.legacy.common.infrastructure.mc.gunpack

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.tacz.legacy.common.application.gunpack.GunPackCompatibilityBatchAnalyzer
import com.tacz.legacy.common.application.gunpack.GunPackCompatibilityBatchReport
import com.tacz.legacy.common.application.gunpack.GunPackCompatibilitySource
import org.apache.logging.log4j.Logger
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.zip.ZipFile

public class GunPackCompatibilityPreInitScanner(
    private val analyzer: GunPackCompatibilityBatchAnalyzer = GunPackCompatibilityBatchAnalyzer()
) {

    public fun scanAndLog(configRoot: Path, logger: Logger): GunPackCompatibilityBatchReport? {
        val candidateRoots = candidateRelativeRoots
            .map { relative -> configRoot.resolve(relative).normalize() }
            .filter { Files.isDirectory(it) }

        val sources = mutableListOf<GunPackCompatibilitySource>()
        val gunTypeHintsByGunId = linkedMapOf<String, String>()
        candidateRoots.forEach { root ->
            collectJsonSources(root, sources, logger)
        }

        val taczRoots = resolveTaczPackRoots(configRoot)
        taczRoots.forEach { packRoot ->
            collectFromTaczPackRoot(packRoot, sources, gunTypeHintsByGunId, logger)
        }

        if (candidateRoots.isEmpty() && taczRoots.isEmpty()) {
            logger.info("[GunPackCompat] scan skipped (no candidate folders found under {}).", configRoot)
            return null
        }

        if (sources.isEmpty()) {
            logger.info("[GunPackCompat] scan finished: 0 json files discovered.")
            return GunPackCompatibilityBatchReport(
                entries = emptyList(),
                gunTypeHintsByGunId = gunTypeHintsByGunId.toMap()
            )
        }

        val report = analyzer.analyze(
            sources = sources,
            gunTypeHintsByGunId = gunTypeHintsByGunId.toMap()
        )
        val histogram = report.issueCodeHistogram()

        logger.info(
            "[GunPackCompat] scan finished: total={} success={} warning={} failed={}",
            report.total,
            report.successCount,
            report.warningCount,
            report.failedCount
        )

        if (histogram.isNotEmpty()) {
            logger.info("[GunPackCompat] issue histogram: {}", histogram)
        }

        if (report.gunTypeHintsByGunId.isNotEmpty()) {
            logger.info("[GunPackCompat] gun type hints loaded: {}", report.gunTypeHintsByGunId.size)
        }

        report.entries
            .flatMap { entry ->
                entry.result.report.allIssues().map { issue -> entry.sourceId to issue }
            }
            .take(LOG_DETAILS_LIMIT)
            .forEach { (sourceId, issue) ->
                when (issue.severity.name) {
                    "ERROR" -> logger.error(
                        "[GunPackCompat] ERROR source={} code={} field={} message={}",
                        sourceId,
                        issue.code,
                        issue.field,
                        issue.message
                    )

                    "WARNING" -> logger.warn(
                        "[GunPackCompat] WARN source={} code={} field={} message={}",
                        sourceId,
                        issue.code,
                        issue.field,
                        issue.message
                    )

                    else -> logger.info(
                        "[GunPackCompat] INFO source={} code={} field={} message={}",
                        sourceId,
                        issue.code,
                        issue.field,
                        issue.message
                    )
                }
            }

        return report
    }

    private fun collectJsonSources(root: Path, sink: MutableList<GunPackCompatibilitySource>, logger: Logger) {
        Files.walk(root).use { stream ->
            val iterator = stream.iterator()
            while (iterator.hasNext()) {
                val file = iterator.next()
                if (!Files.isRegularFile(file) || !file.fileName.toString().endsWith(".json")) {
                    continue
                }

                val json = runCatching {
                    String(Files.readAllBytes(file), StandardCharsets.UTF_8)
                }.getOrElse {
                    logger.warn("[GunPackCompat] Failed to read gun pack file: {}", file, it)
                    continue
                }

                val sourceId = root.relativize(file).toString().replace(File.separatorChar, '/')
                sink += GunPackCompatibilitySource(sourceId = sourceId, json = json)
            }
        }
    }

    private fun resolveTaczPackRoots(configRoot: Path): List<Path> {
        val normalizedConfigRoot = configRoot.toAbsolutePath().normalize()
        val candidates = linkedSetOf<Path>()
        candidates.add(normalizedConfigRoot.resolveSibling("tacz"))
        normalizedConfigRoot.parent?.let { parent ->
            candidates.add(parent.resolve("tacz"))
        }

        return candidates.filter { Files.isDirectory(it) }
    }

    private fun collectFromTaczPackRoot(
        packRoot: Path,
        sink: MutableList<GunPackCompatibilitySource>,
        gunTypeHintsByGunId: MutableMap<String, String>,
        logger: Logger
    ) {
        runCatching {
            Files.list(packRoot).use { children ->
                children.forEach { child ->
                    when {
                        Files.isDirectory(child) -> collectFromPackDirectory(child, sink, gunTypeHintsByGunId, logger)
                        child.fileName.toString().endsWith(".zip") -> collectFromPackZip(child, sink, gunTypeHintsByGunId, logger)
                    }
                }
            }
        }.onFailure {
            logger.warn("[GunPackCompat] Failed to list TACZ pack root: {}", packRoot, it)
        }
    }

    private fun collectFromPackDirectory(
        packDir: Path,
        sink: MutableList<GunPackCompatibilitySource>,
        gunTypeHintsByGunId: MutableMap<String, String>,
        logger: Logger
    ) {
        if (!Files.isRegularFile(packDir.resolve(PACK_META_FILE_NAME))) {
            return
        }

        val dataRoot = packDir.resolve("data")
        if (!Files.isDirectory(dataRoot)) {
            return
        }

        Files.walk(dataRoot).use { stream ->
            val iterator = stream.iterator()
            while (iterator.hasNext()) {
                val file = iterator.next()
                if (!Files.isRegularFile(file) || !file.fileName.toString().endsWith(".json")) {
                    continue
                }

                val relative = dataRoot.relativize(file).toString().replace(File.separatorChar, '/')
                if (!PACK_DATA_GUNS_RELATIVE_REGEX.matches(relative)) {
                    continue
                }

                val json = runCatching {
                    String(Files.readAllBytes(file), StandardCharsets.UTF_8)
                }.getOrElse {
                    logger.warn("[GunPackCompat] Failed to read gun pack file: {}", file, it)
                    continue
                }

                val sourceId = "${packDir.fileName}/data/$relative"
                sink += GunPackCompatibilitySource(sourceId = sourceId, json = json)
            }
        }

        Files.walk(dataRoot).use { stream ->
            val iterator = stream.iterator()
            while (iterator.hasNext()) {
                val file = iterator.next()
                if (!Files.isRegularFile(file) || !file.fileName.toString().endsWith(".json")) {
                    continue
                }

                val relative = dataRoot.relativize(file).toString().replace(File.separatorChar, '/')
                if (!PACK_INDEX_GUNS_RELATIVE_REGEX.matches(relative)) {
                    continue
                }

                val json = runCatching {
                    String(Files.readAllBytes(file), StandardCharsets.UTF_8)
                }.getOrElse {
                    logger.warn("[GunPackCompat] Failed to read gun index file: {}", file, it)
                    continue
                }

                val rawType = parseGunTypeFromIndexJson(json) ?: continue
                val rawGunId = file.fileName.toString().substringBeforeLast('.')
                val sourceId = "${packDir.fileName}/data/$relative"
                registerGunTypeHint(rawGunId, rawType, sourceId, gunTypeHintsByGunId, logger)
            }
        }
    }

    private fun collectFromPackZip(
        packZip: Path,
        sink: MutableList<GunPackCompatibilitySource>,
        gunTypeHintsByGunId: MutableMap<String, String>,
        logger: Logger
    ) {
        runCatching {
            ZipFile(packZip.toFile()).use { zip ->
                if (!hasPackMetaEntry(zip)) {
                    return
                }

                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.isDirectory || !entry.name.endsWith(".json")) {
                        continue
                    }

                    if (PACK_DATA_GUNS_ENTRY_REGEX.matches(entry.name)) {
                        val json = readZipEntry(zip, entry.name)
                        val sourceId = "${packZip.fileName}!/${entry.name}"
                        sink += GunPackCompatibilitySource(sourceId = sourceId, json = json)
                    }

                    if (PACK_INDEX_GUNS_ENTRY_REGEX.matches(entry.name)) {
                        val json = readZipEntry(zip, entry.name)
                        val rawType = parseGunTypeFromIndexJson(json) ?: continue
                        val rawGunId = entry.name.substringAfterLast('/').substringBeforeLast('.')
                        val sourceId = "${packZip.fileName}!/${entry.name}"
                        registerGunTypeHint(rawGunId, rawType, sourceId, gunTypeHintsByGunId, logger)
                    }
                }
            }
        }.onFailure {
            logger.warn("[GunPackCompat] Failed to read gun pack zip: {}", packZip, it)
        }
    }

    private fun hasPackMetaEntry(zip: ZipFile): Boolean {
        val entries = zip.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            if (entry.isDirectory) {
                continue
            }
            if (entry.name == PACK_META_FILE_NAME || entry.name.endsWith("/$PACK_META_FILE_NAME")) {
                return true
            }
        }
        return false
    }

    private fun parseGunTypeFromIndexJson(indexJson: String): String? {
        val root = parseJsonObject(indexJson) ?: return null
        val typeElement = root.get("type") ?: return null
        if (!typeElement.isJsonPrimitive || !typeElement.asJsonPrimitive.isString) {
            return null
        }
        return typeElement.asString
    }

    private fun registerGunTypeHint(
        rawGunId: String,
        rawType: String,
        sourceId: String,
        sink: MutableMap<String, String>,
        logger: Logger
    ) {
        val gunId = normalizeToken(rawGunId) ?: return
        val type = normalizeToken(rawType.substringAfter(':')) ?: return
        val previous = sink.putIfAbsent(gunId, type)
        if (previous != null && previous != type) {
            logger.warn(
                "[GunPackCompat] gun type hint conflict: gunId={} keep={} ignore={} source={}",
                gunId,
                previous,
                type,
                sourceId
            )
        }
    }

    private fun parseJsonObject(json: String): JsonObject? {
        val sanitized = stripJsonComments(json)
        val root = runCatching { JsonParser().parse(sanitized) }.getOrNull() ?: return null
        if (!root.isJsonObject) {
            return null
        }
        return root.asJsonObject
    }

    private fun stripJsonComments(json: String): String {
        val out = StringBuilder(json.length)
        var i = 0
        var inString = false
        var escaped = false
        var inLineComment = false
        var inBlockComment = false

        while (i < json.length) {
            val ch = json[i]
            val next = if (i + 1 < json.length) json[i + 1] else '\u0000'

            if (inLineComment) {
                if (ch == '\n' || ch == '\r') {
                    inLineComment = false
                    out.append(ch)
                }
                i += 1
                continue
            }

            if (inBlockComment) {
                if (ch == '*' && next == '/') {
                    inBlockComment = false
                    i += 2
                } else {
                    i += 1
                }
                continue
            }

            if (inString) {
                out.append(ch)
                if (escaped) {
                    escaped = false
                } else if (ch == '\\') {
                    escaped = true
                } else if (ch == '"') {
                    inString = false
                }
                i += 1
                continue
            }

            if (ch == '"') {
                inString = true
                out.append(ch)
                i += 1
                continue
            }

            if (ch == '/' && next == '/') {
                inLineComment = true
                i += 2
                continue
            }

            if (ch == '/' && next == '*') {
                inBlockComment = true
                i += 2
                continue
            }

            out.append(ch)
            i += 1
        }

        return out.toString()
    }

    private fun normalizeToken(raw: String): String? {
        val normalized = raw.trim().lowercase()
            .map { ch -> if (ch.isLetterOrDigit()) ch else '_' }
            .joinToString(separator = "")
            .replace("__+".toRegex(), "_")
            .trim('_')
        return normalized.ifBlank { null }
    }

    private fun readZipEntry(zip: ZipFile, entryName: String): String {
        val entry = zip.getEntry(entryName) ?: error("Missing zip entry: $entryName")
        zip.getInputStream(entry).use { stream ->
            return String(stream.readBytes(), StandardCharsets.UTF_8)
        }
    }

    private companion object {
        private val candidateRelativeRoots: List<Path> = listOf(
            Paths.get("tacz", "gunpack", "data", "guns"),
            Paths.get("tacz", "data", "guns")
        )

        private val PACK_DATA_GUNS_RELATIVE_REGEX: Regex = Regex("^[^/]+/data/guns/.+\\.json$")
        private val PACK_DATA_GUNS_ENTRY_REGEX: Regex = Regex("^(?:.*/)?data/[^/]+/data/guns/.+\\.json$")
        private val PACK_INDEX_GUNS_RELATIVE_REGEX: Regex = Regex("^[^/]+/index/guns/.+\\.json$")
        private val PACK_INDEX_GUNS_ENTRY_REGEX: Regex = Regex("^(?:.*/)?data/[^/]+/index/guns/.+\\.json$")
        private const val PACK_META_FILE_NAME: String = "gunpack.meta.json"

        private const val LOG_DETAILS_LIMIT: Int = 20
    }

}