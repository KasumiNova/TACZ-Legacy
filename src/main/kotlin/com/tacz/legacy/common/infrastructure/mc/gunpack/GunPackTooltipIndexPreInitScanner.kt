package com.tacz.legacy.common.infrastructure.mc.gunpack

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.tacz.legacy.common.application.gunpack.GunPackTooltipIndexEntry
import com.tacz.legacy.common.application.gunpack.GunPackTooltipIndexSnapshot
import com.tacz.legacy.common.application.gunpack.GunPackTooltipIndexSnapshot.Companion.normalizePath
import com.tacz.legacy.common.application.gunpack.GunPackTooltipIndexSnapshot.Companion.normalizeResourceIdOrNull
import org.apache.logging.log4j.Logger
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile

public class GunPackTooltipIndexPreInitScanner {

    public fun scan(configRoot: Path, logger: Logger): GunPackTooltipIndexSnapshot {
        val normalizedConfigRoot = configRoot.toAbsolutePath().normalize()
        val taczRoots = resolveTaczPackRoots(normalizedConfigRoot)

        if (taczRoots.isEmpty()) {
            logger.info("[TooltipIndex] scan skipped (no TACZ pack root found under {}).", normalizedConfigRoot)
            return GunPackTooltipIndexSnapshot.empty()
        }

        val gunsById = linkedMapOf<String, GunPackTooltipIndexEntry>()
        val attachmentsById = linkedMapOf<String, GunPackTooltipIndexEntry>()
        val ammoById = linkedMapOf<String, GunPackTooltipIndexEntry>()
        val blocksById = linkedMapOf<String, GunPackTooltipIndexEntry>()
        val failedSources = linkedSetOf<String>()

        taczRoots.forEach { packRoot ->
            collectFromTaczPackRoot(
                packRoot = packRoot,
                gunsById = gunsById,
                attachmentsById = attachmentsById,
                ammoById = ammoById,
                blocksById = blocksById,
                failedSources = failedSources,
                logger = logger
            )
        }

        val snapshot = GunPackTooltipIndexSnapshot(
            loadedAtEpochMillis = System.currentTimeMillis(),
            gunEntriesById = gunsById.toSortedMap(),
            attachmentEntriesById = attachmentsById.toSortedMap(),
            ammoEntriesById = ammoById.toSortedMap(),
            blockEntriesById = blocksById.toSortedMap(),
            failedSources = failedSources.toSortedSet()
        )

        logger.info(
            "[TooltipIndex] scan finished: guns={} attachments={} ammo={} blocks={} total={} failed={}",
            snapshot.gunEntriesById.size,
            snapshot.attachmentEntriesById.size,
            snapshot.ammoEntriesById.size,
            snapshot.blockEntriesById.size,
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
        gunsById: MutableMap<String, GunPackTooltipIndexEntry>,
        attachmentsById: MutableMap<String, GunPackTooltipIndexEntry>,
        ammoById: MutableMap<String, GunPackTooltipIndexEntry>,
        blocksById: MutableMap<String, GunPackTooltipIndexEntry>,
        failedSources: MutableSet<String>,
        logger: Logger
    ) {
        runCatching {
            Files.list(packRoot).use { children ->
                children.forEach { child ->
                    when {
                        Files.isDirectory(child) -> collectFromPackDirectory(
                            packDir = child,
                            gunsById = gunsById,
                            attachmentsById = attachmentsById,
                            ammoById = ammoById,
                            blocksById = blocksById,
                            failedSources = failedSources,
                            logger = logger
                        )

                        child.fileName.toString().endsWith(".zip") -> collectFromPackZip(
                            packZip = child,
                            gunsById = gunsById,
                            attachmentsById = attachmentsById,
                            ammoById = ammoById,
                            blocksById = blocksById,
                            failedSources = failedSources,
                            logger = logger
                        )
                    }
                }
            }
        }.onFailure {
            logger.warn("[TooltipIndex] Failed to list TACZ pack root: {}", packRoot, it)
        }
    }

    private fun collectFromPackDirectory(
        packDir: Path,
        gunsById: MutableMap<String, GunPackTooltipIndexEntry>,
        attachmentsById: MutableMap<String, GunPackTooltipIndexEntry>,
        ammoById: MutableMap<String, GunPackTooltipIndexEntry>,
        blocksById: MutableMap<String, GunPackTooltipIndexEntry>,
        failedSources: MutableSet<String>,
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
                val sourceId = "${packDir.fileName}/data/$relative"
                val json = runCatching {
                    String(Files.readAllBytes(file), StandardCharsets.UTF_8)
                }.getOrElse {
                    failedSources += sourceId
                    logger.warn("[TooltipIndex] Failed to read index file: {}", file, it)
                    continue
                }

                when {
                    INDEX_GUNS_RELATIVE_REGEX.matches(relative) -> parseAndPut(
                        rawPath = relative,
                        sourceId = sourceId,
                        json = json,
                        category = Category.GUN,
                        sink = gunsById,
                        failedSources = failedSources,
                        logger = logger
                    )

                    INDEX_ATTACHMENTS_RELATIVE_REGEX.matches(relative) -> parseAndPut(
                        rawPath = relative,
                        sourceId = sourceId,
                        json = json,
                        category = Category.ATTACHMENT,
                        sink = attachmentsById,
                        failedSources = failedSources,
                        logger = logger
                    )

                    INDEX_AMMO_RELATIVE_REGEX.matches(relative) -> parseAndPut(
                        rawPath = relative,
                        sourceId = sourceId,
                        json = json,
                        category = Category.AMMO,
                        sink = ammoById,
                        failedSources = failedSources,
                        logger = logger
                    )

                    INDEX_BLOCKS_RELATIVE_REGEX.matches(relative) -> parseAndPut(
                        rawPath = relative,
                        sourceId = sourceId,
                        json = json,
                        category = Category.BLOCK,
                        sink = blocksById,
                        failedSources = failedSources,
                        logger = logger
                    )
                }
            }
        }
    }

    private fun collectFromPackZip(
        packZip: Path,
        gunsById: MutableMap<String, GunPackTooltipIndexEntry>,
        attachmentsById: MutableMap<String, GunPackTooltipIndexEntry>,
        ammoById: MutableMap<String, GunPackTooltipIndexEntry>,
        blocksById: MutableMap<String, GunPackTooltipIndexEntry>,
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
                    if (entry.isDirectory || !entry.name.endsWith(".json")) {
                        continue
                    }

                    val sourceId = "${packZip.fileName}!/${entry.name}"
                    when {
                        INDEX_GUNS_ENTRY_REGEX.matches(entry.name) -> {
                            val json = readZipEntry(zip, entry.name)
                            parseAndPut(
                                rawPath = entry.name,
                                sourceId = sourceId,
                                json = json,
                                category = Category.GUN,
                                sink = gunsById,
                                failedSources = failedSources,
                                logger = logger
                            )
                        }

                        INDEX_ATTACHMENTS_ENTRY_REGEX.matches(entry.name) -> {
                            val json = readZipEntry(zip, entry.name)
                            parseAndPut(
                                rawPath = entry.name,
                                sourceId = sourceId,
                                json = json,
                                category = Category.ATTACHMENT,
                                sink = attachmentsById,
                                failedSources = failedSources,
                                logger = logger
                            )
                        }

                        INDEX_AMMO_ENTRY_REGEX.matches(entry.name) -> {
                            val json = readZipEntry(zip, entry.name)
                            parseAndPut(
                                rawPath = entry.name,
                                sourceId = sourceId,
                                json = json,
                                category = Category.AMMO,
                                sink = ammoById,
                                failedSources = failedSources,
                                logger = logger
                            )
                        }

                        INDEX_BLOCKS_ENTRY_REGEX.matches(entry.name) -> {
                            val json = readZipEntry(zip, entry.name)
                            parseAndPut(
                                rawPath = entry.name,
                                sourceId = sourceId,
                                json = json,
                                category = Category.BLOCK,
                                sink = blocksById,
                                failedSources = failedSources,
                                logger = logger
                            )
                        }
                    }
                }
            }
        }.onFailure {
            logger.warn("[TooltipIndex] Failed to read pack zip: {}", packZip, it)
        }
    }

    private fun parseAndPut(
        rawPath: String,
        sourceId: String,
        json: String?,
        category: Category,
        sink: MutableMap<String, GunPackTooltipIndexEntry>,
        failedSources: MutableSet<String>,
        logger: Logger
    ) {
        val indexInfo = extractIndexInfo(rawPath, category) ?: return
        val root = parseJsonObject(json)
        if (root == null) {
            failedSources += sourceId
            logger.warn("[TooltipIndex] Invalid index json: {}", sourceId)
            return
        }

        val normalizedId = normalizeResourceIdOrNull("${indexInfo.namespace}:${indexInfo.path}", indexInfo.namespace)
            ?: return

        val entry = GunPackTooltipIndexEntry(
            itemId = normalizedId,
            sourceId = sourceId,
            nameKey = root.readString("name"),
            tooltipKey = root.readString("tooltip"),
            displayId = root.readString("display")
                ?.let { normalizeResourceIdOrNull(it, indexInfo.namespace) },
            type = root.readString("type")
                ?.let(::normalizeTypeToken)
        )

        val previous = sink.putIfAbsent(normalizedId, entry)
        if (previous != null && previous.sourceId != sourceId) {
            logger.warn(
                "[TooltipIndex] duplicate {} id={} keep={} ignore={}",
                category.logName,
                normalizedId,
                previous.sourceId,
                sourceId
            )
        }
    }

    private fun extractIndexInfo(rawPath: String, category: Category): IndexInfo? {
        val match = category.relativeRegex.find(rawPath)
            ?: category.entryRegex.find(rawPath)
            ?: return null
        val namespace = match.groupValues[1].ifBlank { DEFAULT_NAMESPACE }
        val path = normalizePath(match.groupValues[2].substringBeforeLast('.')) ?: return null
        return IndexInfo(namespace = namespace, path = path)
    }

    private fun normalizeTypeToken(raw: String): String {
        return raw.trim().lowercase()
            .map { ch -> if (ch.isLetterOrDigit()) ch else '_' }
            .joinToString(separator = "")
            .replace("__+".toRegex(), "_")
            .trim('_')
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

    private fun parseJsonObject(json: String?): JsonObject? {
        val content = json ?: return null
        val sanitized = stripJsonComments(content)
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

    private fun readZipEntry(zip: ZipFile, entryName: String): String? {
        val entry = zip.getEntry(entryName) ?: return null
        zip.getInputStream(entry).use { stream ->
            return String(stream.readBytes(), StandardCharsets.UTF_8)
        }
    }

    private fun JsonObject.readString(key: String): String? {
        val element = get(key) ?: return null
        if (!element.isJsonPrimitive || !element.asJsonPrimitive.isString) {
            return null
        }
        return element.asString.trim().ifBlank { null }
    }

    private data class IndexInfo(
        val namespace: String,
        val path: String
    )

    private enum class Category(
        val logName: String,
        val relativeRegex: Regex,
        val entryRegex: Regex
    ) {
        GUN(
            logName = "gun",
            relativeRegex = INDEX_GUNS_RELATIVE_REGEX,
            entryRegex = INDEX_GUNS_ENTRY_REGEX
        ),
        ATTACHMENT(
            logName = "attachment",
            relativeRegex = INDEX_ATTACHMENTS_RELATIVE_REGEX,
            entryRegex = INDEX_ATTACHMENTS_ENTRY_REGEX
        ),
        AMMO(
            logName = "ammo",
            relativeRegex = INDEX_AMMO_RELATIVE_REGEX,
            entryRegex = INDEX_AMMO_ENTRY_REGEX
        ),
        BLOCK(
            logName = "block",
            relativeRegex = INDEX_BLOCKS_RELATIVE_REGEX,
            entryRegex = INDEX_BLOCKS_ENTRY_REGEX
        )
    }

    private companion object {
        private const val DEFAULT_NAMESPACE: String = "tacz"
        private const val PACK_META_FILE_NAME: String = "gunpack.meta.json"

        private val INDEX_GUNS_RELATIVE_REGEX: Regex = Regex("^([^/]+)/index/guns/(.+)\\.json$")
        private val INDEX_ATTACHMENTS_RELATIVE_REGEX: Regex = Regex("^([^/]+)/index/attachments/(.+)\\.json$")
        private val INDEX_AMMO_RELATIVE_REGEX: Regex = Regex("^([^/]+)/index/ammo/(.+)\\.json$")
        private val INDEX_BLOCKS_RELATIVE_REGEX: Regex = Regex("^([^/]+)/index/blocks/(.+)\\.json$")

        private val INDEX_GUNS_ENTRY_REGEX: Regex = Regex("^(?:.*/)?data/([^/]+)/index/guns/(.+)\\.json$")
        private val INDEX_ATTACHMENTS_ENTRY_REGEX: Regex = Regex("^(?:.*/)?data/([^/]+)/index/attachments/(.+)\\.json$")
        private val INDEX_AMMO_ENTRY_REGEX: Regex = Regex("^(?:.*/)?data/([^/]+)/index/ammo/(.+)\\.json$")
        private val INDEX_BLOCKS_ENTRY_REGEX: Regex = Regex("^(?:.*/)?data/([^/]+)/index/blocks/(.+)\\.json$")
    }
}
