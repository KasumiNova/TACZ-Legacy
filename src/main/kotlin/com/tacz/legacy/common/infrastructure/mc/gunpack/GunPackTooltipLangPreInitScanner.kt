package com.tacz.legacy.common.infrastructure.mc.gunpack

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.tacz.legacy.common.application.gunpack.GunPackTooltipLangSnapshot
import org.apache.logging.log4j.Logger
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile

public class GunPackTooltipLangPreInitScanner {

    public fun scan(configRoot: Path, logger: Logger): GunPackTooltipLangSnapshot {
        val normalizedConfigRoot = configRoot.toAbsolutePath().normalize()
        val taczRoots = resolveTaczPackRoots(normalizedConfigRoot)

        if (taczRoots.isEmpty()) {
            logger.info("[TooltipLang] scan skipped (no TACZ pack root found under {}).", normalizedConfigRoot)
            return GunPackTooltipLangSnapshot.empty()
        }

        val localizedValuesByLocale = linkedMapOf<String, MutableMap<String, String>>()
        val failedSources = linkedSetOf<String>()

        taczRoots.forEach { packRoot ->
            collectFromTaczPackRoot(
                packRoot = packRoot,
                localizedValuesByLocale = localizedValuesByLocale,
                failedSources = failedSources,
                logger = logger
            )
        }

        val snapshot = GunPackTooltipLangSnapshot(
            loadedAtEpochMillis = System.currentTimeMillis(),
            valuesByLocale = localizedValuesByLocale
                .mapValues { (_, values) -> values.toSortedMap() }
                .toSortedMap(),
            failedSources = failedSources.toSortedSet()
        )

        logger.info(
            "[TooltipLang] scan finished: locales={} entries={} failed={}",
            snapshot.totalLocales,
            snapshot.totalEntries,
            snapshot.failedSources.size
        )

        return snapshot
    }

    private fun resolveTaczPackRoots(configRoot: Path): List<Path> {
        val candidates = linkedSetOf<Path>()
        candidates.add(configRoot.resolveSibling("tacz"))
        configRoot.parent?.let { parent -> candidates.add(parent.resolve("tacz")) }
        return candidates
            .map { it.toAbsolutePath().normalize() }
            .filter { Files.isDirectory(it) }
    }

    private fun collectFromTaczPackRoot(
        packRoot: Path,
        localizedValuesByLocale: MutableMap<String, MutableMap<String, String>>,
        failedSources: MutableSet<String>,
        logger: Logger
    ) {
        runCatching {
            Files.list(packRoot).use { children ->
                children.forEach { child ->
                    when {
                        Files.isDirectory(child) -> collectFromPackDirectory(
                            packDir = child,
                            localizedValuesByLocale = localizedValuesByLocale,
                            failedSources = failedSources,
                            logger = logger
                        )

                        child.fileName.toString().endsWith(".zip") -> collectFromPackZip(
                            packZip = child,
                            localizedValuesByLocale = localizedValuesByLocale,
                            failedSources = failedSources,
                            logger = logger
                        )
                    }
                }
            }
        }.onFailure {
            logger.warn("[TooltipLang] Failed to list TACZ pack root: {}", packRoot, it)
        }
    }

    private fun collectFromPackDirectory(
        packDir: Path,
        localizedValuesByLocale: MutableMap<String, MutableMap<String, String>>,
        failedSources: MutableSet<String>,
        logger: Logger
    ) {
        if (!Files.isRegularFile(packDir.resolve(PACK_META_FILE_NAME))) {
            return
        }

        val assetsRoot = packDir.resolve("assets")
        if (!Files.isDirectory(assetsRoot)) {
            return
        }

        Files.walk(assetsRoot).use { stream ->
            val iterator = stream.iterator()
            while (iterator.hasNext()) {
                val file = iterator.next()
                if (!Files.isRegularFile(file)) {
                    continue
                }

                val fileName = file.fileName.toString().lowercase()
                if (!fileName.endsWith(".json") && !fileName.endsWith(".lang")) {
                    continue
                }

                val relative = assetsRoot.relativize(file).toString().replace(File.separatorChar, '/')
                val info = extractLangInfo(relative) ?: continue
                val sourceId = "${packDir.fileName}/assets/$relative"

                val raw = runCatching {
                    String(Files.readAllBytes(file), StandardCharsets.UTF_8)
                }.getOrElse {
                    failedSources += sourceId
                    logger.warn("[TooltipLang] Failed to read lang file: {}", file, it)
                    continue
                }

                val entries = parseEntries(raw = raw, extension = info.extension)
                if (entries == null) {
                    failedSources += sourceId
                    logger.warn("[TooltipLang] Failed to parse lang file: {}", sourceId)
                    continue
                }

                mergeEntries(
                    locale = info.locale,
                    sourceId = sourceId,
                    entries = entries,
                    localizedValuesByLocale = localizedValuesByLocale,
                    logger = logger
                )
            }
        }
    }

    private fun collectFromPackZip(
        packZip: Path,
        localizedValuesByLocale: MutableMap<String, MutableMap<String, String>>,
        failedSources: MutableSet<String>,
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
                    if (entry.isDirectory) {
                        continue
                    }

                    val info = extractLangInfoFromZipEntry(entry.name) ?: continue
                    val sourceId = "${packZip.fileName}!/${entry.name}"
                    val raw = readZipEntry(zip, entry.name)
                    val parsed = parseEntries(raw = raw, extension = info.extension)
                    if (parsed == null) {
                        failedSources += sourceId
                        logger.warn("[TooltipLang] Failed to parse zip lang file: {}", sourceId)
                        continue
                    }

                    mergeEntries(
                        locale = info.locale,
                        sourceId = sourceId,
                        entries = parsed,
                        localizedValuesByLocale = localizedValuesByLocale,
                        logger = logger
                    )
                }
            }
        }.onFailure {
            logger.warn("[TooltipLang] Failed to read pack zip: {}", packZip, it)
        }
    }

    private fun parseEntries(raw: String?, extension: String): Map<String, String>? {
        val content = raw ?: return null
        return when (extension.lowercase()) {
            "json" -> parseJsonLangEntries(content)
            "lang" -> parseLegacyLangEntries(content)
            else -> null
        }
    }

    private fun parseJsonLangEntries(content: String): Map<String, String>? {
        val sanitized = stripJsonComments(content)
        val root = runCatching { JsonParser().parse(sanitized) }.getOrNull() ?: return null
        if (!root.isJsonObject) {
            return null
        }
        val map = linkedMapOf<String, String>()
        root.asJsonObject.entrySet().forEach { (key, value) ->
            if (value != null && value.isJsonPrimitive && value.asJsonPrimitive.isString) {
                val normalizedKey = key.trim().ifBlank { return@forEach }
                val normalizedValue = unescapeLangValue(value.asString.trim()).ifBlank { return@forEach }
                map[normalizedKey] = normalizedValue
            }
        }
        return map
    }

    private fun parseLegacyLangEntries(content: String): Map<String, String> {
        val map = linkedMapOf<String, String>()
        content
            .lineSequence()
            .forEach { rawLine ->
                val line = rawLine.trim()
                if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) {
                    return@forEach
                }

                val splitIndex = line.indexOf('=').takeIf { it >= 0 }
                    ?: line.indexOf(':').takeIf { it >= 0 }
                    ?: return@forEach

                val key = line.substring(0, splitIndex).trim().ifBlank { return@forEach }
                val value = line.substring(splitIndex + 1).trim().ifBlank { return@forEach }
                map[key] = unescapeLangValue(value)
            }
        return map
    }

    private fun mergeEntries(
        locale: String,
        sourceId: String,
        entries: Map<String, String>,
        localizedValuesByLocale: MutableMap<String, MutableMap<String, String>>,
        logger: Logger
    ) {
        val localeMap = localizedValuesByLocale.getOrPut(locale) { linkedMapOf() }
        entries.forEach { (key, value) ->
            val previous = localeMap.putIfAbsent(key, value)
            if (previous != null && previous != value) {
                logger.debug(
                    "[TooltipLang] duplicate key={} locale={} keep-first from source={}",
                    key,
                    locale,
                    sourceId
                )
            }
        }
    }

    private fun extractLangInfo(relativeAssetPath: String): LangInfo? {
        val match = DIRECTORY_LANG_RELATIVE_REGEX.find(relativeAssetPath) ?: return null
        val locale = GunPackTooltipLangSnapshot.normalizeLocale(match.groupValues[2]) ?: return null
        val extension = match.groupValues[3].trim().lowercase().ifBlank { return null }
        return LangInfo(locale = locale, extension = extension)
    }

    private fun extractLangInfoFromZipEntry(zipEntryName: String): LangInfo? {
        val match = ZIP_LANG_ENTRY_REGEX.find(zipEntryName) ?: return null
        val locale = GunPackTooltipLangSnapshot.normalizeLocale(match.groupValues[2]) ?: return null
        val extension = match.groupValues[3].trim().lowercase().ifBlank { return null }
        return LangInfo(locale = locale, extension = extension)
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

    private fun readZipEntry(zip: ZipFile, entryName: String): String? {
        val entry = zip.getEntry(entryName) ?: return null
        zip.getInputStream(entry).use { stream ->
            return String(stream.readBytes(), StandardCharsets.UTF_8)
        }
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

    private fun unescapeLangValue(raw: String): String {
        return buildString(raw.length) {
            var i = 0
            while (i < raw.length) {
                val ch = raw[i]
                if (ch == '\\' && i + 1 < raw.length) {
                    val next = raw[i + 1]
                    when (next) {
                        'n' -> {
                            append('\n')
                            i += 2
                            continue
                        }

                        'r' -> {
                            append('\r')
                            i += 2
                            continue
                        }

                        't' -> {
                            append('\t')
                            i += 2
                            continue
                        }

                        '\\' -> {
                            append('\\')
                            i += 2
                            continue
                        }
                    }
                }

                append(ch)
                i += 1
            }
        }
    }

    private data class LangInfo(
        val locale: String,
        val extension: String
    )

    private companion object {
        private const val PACK_META_FILE_NAME: String = "gunpack.meta.json"
        private val DIRECTORY_LANG_RELATIVE_REGEX: Regex = Regex("^([^/]+)/lang/([^/.]+)\\.(json|lang)$", RegexOption.IGNORE_CASE)
        private val ZIP_LANG_ENTRY_REGEX: Regex = Regex("^(?:.*/)?assets/([^/]+)/lang/([^/.]+)\\.(json|lang)$", RegexOption.IGNORE_CASE)
    }
}