package com.tacz.legacy.common.infrastructure.mc.gunpack

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.tacz.legacy.common.application.gunpack.WeaponAttachmentCompatibilitySnapshot
import com.tacz.legacy.common.application.gunpack.WeaponAttachmentDefinition
import com.tacz.legacy.common.application.gunpack.WeaponAttachmentCompatibilitySnapshot.Companion.normalizeAllowEntryOrNull
import com.tacz.legacy.common.application.gunpack.WeaponAttachmentCompatibilitySnapshot.Companion.normalizePath
import com.tacz.legacy.common.application.gunpack.WeaponAttachmentCompatibilitySnapshot.Companion.normalizeResourceIdOrNull
import org.apache.logging.log4j.Logger
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile

public class WeaponAttachmentPreInitScanner {

    public fun scan(configRoot: Path, logger: Logger): WeaponAttachmentCompatibilitySnapshot {
        val normalizedConfigRoot = configRoot.toAbsolutePath().normalize()
        val taczRoots = resolveTaczPackRoots(normalizedConfigRoot)

        if (taczRoots.isEmpty()) {
            logger.info("[AttachmentCompat] scan skipped (no TACZ pack root found under {}).", normalizedConfigRoot)
            return WeaponAttachmentCompatibilitySnapshot.empty()
        }

        val attachmentsById = linkedMapOf<String, WeaponAttachmentDefinition>()
        val allowEntriesByGunId = linkedMapOf<String, MutableSet<String>>()
        val tagsByTagId = linkedMapOf<String, MutableSet<String>>()
        val ammoIconByAmmoId = linkedMapOf<String, String>()
        val failedSources = linkedSetOf<String>()

        taczRoots.forEach { packRoot ->
            collectFromTaczPackRoot(
                packRoot = packRoot,
                attachmentsById = attachmentsById,
                allowEntriesByGunId = allowEntriesByGunId,
                tagsByTagId = tagsByTagId,
                ammoIconByAmmoId = ammoIconByAmmoId,
                failedSources = failedSources,
                logger = logger
            )
        }

        val snapshot = WeaponAttachmentCompatibilitySnapshot(
            loadedAtEpochMillis = System.currentTimeMillis(),
            attachmentsById = attachmentsById.toSortedMap(),
            allowEntriesByGunId = allowEntriesByGunId
                .mapValues { (_, entries) -> entries.toSortedSet() }
                .toSortedMap(),
            tagsByTagId = tagsByTagId
                .mapValues { (_, entries) -> entries.toSortedSet() }
                .toSortedMap(),
            ammoIconTextureByAmmoId = ammoIconByAmmoId.toSortedMap(),
            failedSources = failedSources.toSortedSet()
        )

        logger.info(
            "[AttachmentCompat] scan finished: attachments={} allowRules={} tags={} ammoIcons={} failed={}",
            snapshot.attachmentsById.size,
            snapshot.allowEntriesByGunId.size,
            snapshot.tagsByTagId.size,
            snapshot.ammoIconTextureByAmmoId.size,
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
        attachmentsById: MutableMap<String, WeaponAttachmentDefinition>,
        allowEntriesByGunId: MutableMap<String, MutableSet<String>>,
        tagsByTagId: MutableMap<String, MutableSet<String>>,
        ammoIconByAmmoId: MutableMap<String, String>,
        failedSources: MutableSet<String>,
        logger: Logger
    ) {
        runCatching {
            Files.list(packRoot).use { children ->
                children.forEach { child ->
                    when {
                        Files.isDirectory(child) -> collectFromPackDirectory(
                            packDir = child,
                            attachmentsById = attachmentsById,
                            allowEntriesByGunId = allowEntriesByGunId,
                            tagsByTagId = tagsByTagId,
                            ammoIconByAmmoId = ammoIconByAmmoId,
                            failedSources = failedSources,
                            logger = logger
                        )

                        child.fileName.toString().endsWith(".zip") -> collectFromPackZip(
                            packZip = child,
                            attachmentsById = attachmentsById,
                            allowEntriesByGunId = allowEntriesByGunId,
                            tagsByTagId = tagsByTagId,
                            ammoIconByAmmoId = ammoIconByAmmoId,
                            failedSources = failedSources,
                            logger = logger
                        )
                    }
                }
            }
        }.onFailure {
            logger.warn("[AttachmentCompat] Failed to list TACZ pack root: {}", packRoot, it)
        }
    }

    private fun collectFromPackDirectory(
        packDir: Path,
        attachmentsById: MutableMap<String, WeaponAttachmentDefinition>,
        allowEntriesByGunId: MutableMap<String, MutableSet<String>>,
        tagsByTagId: MutableMap<String, MutableSet<String>>,
        ammoIconByAmmoId: MutableMap<String, String>,
        failedSources: MutableSet<String>,
        logger: Logger
    ) {
        if (!Files.isRegularFile(packDir.resolve(PACK_META_FILE_NAME))) {
            return
        }

        val dataRoot = packDir.resolve("data")
        if (Files.isDirectory(dataRoot)) {
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
                        logger.warn("[AttachmentCompat] Failed to read file: {}", file, it)
                        continue
                    }

                    when {
                        INDEX_ATTACHMENTS_RELATIVE_REGEX.matches(relative) -> parseAttachmentIndexJson(
                            rawPath = relative,
                            sourceId = sourceId,
                            json = json,
                            resolveAttachmentDisplayJson = { displayId ->
                                resolveAttachmentDisplayJsonFromDirectory(packDir, displayId)
                            },
                            sink = attachmentsById,
                            failedSources = failedSources,
                            logger = logger
                        )

                        ALLOW_ATTACHMENTS_RELATIVE_REGEX.matches(relative) -> parseAllowAttachmentsJson(
                            rawPath = relative,
                            json = json,
                            sink = allowEntriesByGunId,
                            failedSources = failedSources,
                            sourceId = sourceId,
                            logger = logger
                        )

                        ATTACHMENT_TAG_RELATIVE_REGEX.matches(relative) && !ALLOW_ATTACHMENTS_RELATIVE_REGEX.matches(relative) -> parseAttachmentTagJson(
                            rawPath = relative,
                            json = json,
                            sink = tagsByTagId,
                            failedSources = failedSources,
                            sourceId = sourceId,
                            logger = logger
                        )
                    }
                }
            }
        }

        val assetsRoot = packDir.resolve("assets")
        if (Files.isDirectory(assetsRoot)) {
            Files.walk(assetsRoot).use { stream ->
                val iterator = stream.iterator()
                while (iterator.hasNext()) {
                    val file = iterator.next()
                    if (!Files.isRegularFile(file) || !file.fileName.toString().endsWith(".json")) {
                        continue
                    }

                    val relative = assetsRoot.relativize(file).toString().replace(File.separatorChar, '/')
                    if (!AMMO_DISPLAY_RELATIVE_REGEX.matches(relative)) {
                        continue
                    }

                    val sourceId = "${packDir.fileName}/assets/$relative"
                    val json = runCatching {
                        String(Files.readAllBytes(file), StandardCharsets.UTF_8)
                    }.getOrElse {
                        failedSources += sourceId
                        logger.warn("[AttachmentCompat] Failed to read ammo display: {}", file, it)
                        continue
                    }
                    parseAmmoDisplayJson(
                        rawPath = relative,
                        json = json,
                        sink = ammoIconByAmmoId,
                        failedSources = failedSources,
                        sourceId = sourceId,
                        logger = logger
                    )
                }
            }
        }
    }

    private fun collectFromPackZip(
        packZip: Path,
        attachmentsById: MutableMap<String, WeaponAttachmentDefinition>,
        allowEntriesByGunId: MutableMap<String, MutableSet<String>>,
        tagsByTagId: MutableMap<String, MutableSet<String>>,
        ammoIconByAmmoId: MutableMap<String, String>,
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
                        INDEX_ATTACHMENTS_ENTRY_REGEX.matches(entry.name) -> {
                            val json = readZipEntry(zip, entry.name)
                            parseAttachmentIndexJson(
                                rawPath = entry.name,
                                sourceId = sourceId,
                                json = json,
                                resolveAttachmentDisplayJson = { displayId ->
                                    resolveAttachmentDisplayJsonFromZip(zip, entry.name, displayId)
                                },
                                sink = attachmentsById,
                                failedSources = failedSources,
                                logger = logger
                            )
                        }

                        ALLOW_ATTACHMENTS_ENTRY_REGEX.matches(entry.name) -> {
                            val json = readZipEntry(zip, entry.name)
                            parseAllowAttachmentsJson(
                                rawPath = entry.name,
                                json = json,
                                sink = allowEntriesByGunId,
                                failedSources = failedSources,
                                sourceId = sourceId,
                                logger = logger
                            )
                        }

                        ATTACHMENT_TAG_ENTRY_REGEX.matches(entry.name) && !ALLOW_ATTACHMENTS_ENTRY_REGEX.matches(entry.name) -> {
                            val json = readZipEntry(zip, entry.name)
                            parseAttachmentTagJson(
                                rawPath = entry.name,
                                json = json,
                                sink = tagsByTagId,
                                failedSources = failedSources,
                                sourceId = sourceId,
                                logger = logger
                            )
                        }

                        AMMO_DISPLAY_ENTRY_REGEX.matches(entry.name) -> {
                            val json = readZipEntry(zip, entry.name)
                            parseAmmoDisplayJson(
                                rawPath = entry.name,
                                json = json,
                                sink = ammoIconByAmmoId,
                                failedSources = failedSources,
                                sourceId = sourceId,
                                logger = logger
                            )
                        }
                    }
                }
            }
        }.onFailure {
            logger.warn("[AttachmentCompat] Failed to read pack zip: {}", packZip, it)
        }
    }

    private fun parseAttachmentIndexJson(
        rawPath: String,
        sourceId: String,
        json: String,
        resolveAttachmentDisplayJson: (String) -> String?,
        sink: MutableMap<String, WeaponAttachmentDefinition>,
        failedSources: MutableSet<String>,
        logger: Logger
    ) {
        val match = INDEX_ATTACHMENTS_RELATIVE_REGEX.find(rawPath)
            ?: INDEX_ATTACHMENTS_ENTRY_REGEX.find(rawPath)
            ?: return
        val namespace = match.groupValues[1].ifBlank { DEFAULT_NAMESPACE }
        val attachmentPath = match.groupValues[2].substringBeforeLast('.').ifBlank { return }
        val attachmentId = normalizeResourceIdOrNull("$namespace:$attachmentPath", namespace) ?: return

        val root = parseJsonObject(json)
        if (root == null) {
            failedSources += sourceId
            logger.warn("[AttachmentCompat] Invalid attachment index json: {}", sourceId)
            return
        }

        val rawType = root.readString("type")
        val normalizedType = normalizeAttachmentType(rawType)
        val displayId = root.readString("display")
            ?.let { normalizeResourceIdOrNull(it, namespace) }
        val iconTexturePath = displayId
            ?.let(resolveAttachmentDisplayJson)
            ?.let(::parseSlotTexturePathFromDisplayJson)

        sink.putIfAbsent(
            attachmentId,
            WeaponAttachmentDefinition(
                attachmentId = attachmentId,
                attachmentType = normalizedType,
                sourceId = sourceId,
                displayId = displayId,
                iconTextureAssetPath = iconTexturePath
            )
        )
    }

    private fun parseAllowAttachmentsJson(
        rawPath: String,
        json: String,
        sink: MutableMap<String, MutableSet<String>>,
        failedSources: MutableSet<String>,
        sourceId: String,
        logger: Logger
    ) {
        val match = ALLOW_ATTACHMENTS_RELATIVE_REGEX.find(rawPath)
            ?: ALLOW_ATTACHMENTS_ENTRY_REGEX.find(rawPath)
            ?: return

        val namespace = match.groupValues[1].ifBlank { DEFAULT_NAMESPACE }
        val rawGun = match.groupValues[2].substringBeforeLast('.').ifBlank { return }
        val gunPath = normalizePath(rawGun)?.substringAfterLast('/') ?: return

        val entries = parseStringArray(json)
        if (entries == null) {
            failedSources += sourceId
            logger.warn("[AttachmentCompat] Invalid allow_attachments json: {}", sourceId)
            return
        }

        val normalizedEntries = entries
            .mapNotNull { normalizeAllowEntryOrNull(it, defaultNamespace = namespace) }
            .toSet()
        if (normalizedEntries.isEmpty()) {
            return
        }

        sink.getOrPut(gunPath) { linkedSetOf() }.addAll(normalizedEntries)
    }

    private fun parseAttachmentTagJson(
        rawPath: String,
        json: String,
        sink: MutableMap<String, MutableSet<String>>,
        failedSources: MutableSet<String>,
        sourceId: String,
        logger: Logger
    ) {
        val match = ATTACHMENT_TAG_RELATIVE_REGEX.find(rawPath)
            ?: ATTACHMENT_TAG_ENTRY_REGEX.find(rawPath)
            ?: return

        val namespace = match.groupValues[1].ifBlank { DEFAULT_NAMESPACE }
        val tagPath = match.groupValues[2].substringBeforeLast('.').ifBlank { return }
        if (tagPath.startsWith("allow_attachments/")) {
            return
        }
        val tagId = normalizeResourceIdOrNull("$namespace:$tagPath", namespace) ?: return

        val entries = parseStringArray(json)
        if (entries == null) {
            failedSources += sourceId
            logger.warn("[AttachmentCompat] Invalid attachment tag json: {}", sourceId)
            return
        }

        val normalizedEntries = entries
            .mapNotNull { normalizeAllowEntryOrNull(it, defaultNamespace = namespace) }
            .toSet()
        if (normalizedEntries.isEmpty()) {
            return
        }

        sink.getOrPut("#$tagId") { linkedSetOf() }.addAll(normalizedEntries)
    }

    private fun parseAmmoDisplayJson(
        rawPath: String,
        json: String,
        sink: MutableMap<String, String>,
        failedSources: MutableSet<String>,
        sourceId: String,
        logger: Logger
    ) {
        val match = AMMO_DISPLAY_RELATIVE_REGEX.find(rawPath)
            ?: AMMO_DISPLAY_ENTRY_REGEX.find(rawPath)
            ?: return

        val namespace = match.groupValues[1].ifBlank { DEFAULT_NAMESPACE }
        val ammoPath = normalizePath(match.groupValues[2]) ?: return
        val ammoId = normalizeResourceIdOrNull("$namespace:$ammoPath", namespace) ?: return
        val iconTexturePath = parseSlotTexturePathFromDisplayJson(json)
        if (iconTexturePath == null) {
            failedSources += sourceId
            logger.warn("[AttachmentCompat] Missing/invalid ammo display slot field: {}", sourceId)
            return
        }
        sink.putIfAbsent(ammoId, iconTexturePath)
    }

    private fun resolveAttachmentDisplayJsonFromDirectory(packDir: Path, displayId: String): String? {
        val normalizedDisplayId = normalizeResourceIdOrNull(displayId) ?: return null
        val namespace = normalizedDisplayId.substringBefore(':')
        val displayPath = normalizedDisplayId.substringAfter(':')
        val assetsRoot = packDir.resolve("assets")

        val candidates = listOf(
            assetsRoot.resolve(namespace).resolve("display").resolve("attachments").resolve("$displayPath.json"),
            assetsRoot.resolve(namespace).resolve("display").resolve("$displayPath.json")
        )

        val hit = candidates.firstOrNull { Files.isRegularFile(it) } ?: return null
        return runCatching {
            String(Files.readAllBytes(hit), StandardCharsets.UTF_8)
        }.getOrNull()
    }

    private fun resolveAttachmentDisplayJsonFromZip(zip: ZipFile, indexEntryPath: String, displayId: String): String? {
        val normalizedDisplayId = normalizeResourceIdOrNull(displayId) ?: return null
        val namespace = normalizedDisplayId.substringBefore(':')
        val displayPath = normalizedDisplayId.substringAfter(':')
        val prefix = indexEntryPath.substringBefore("data/")

        val candidates = listOf(
            "${prefix}assets/$namespace/display/attachments/$displayPath.json",
            "${prefix}assets/$namespace/display/$displayPath.json"
        )

        val entryName = candidates.firstOrNull { zip.getEntry(it) != null } ?: return null
        return readZipEntry(zip, entryName)
    }

    private fun parseSlotTexturePathFromDisplayJson(displayJson: String): String? {
        val root = parseJsonObject(displayJson) ?: return null
        val slot = root.readString("slot") ?: return null
        val normalizedSlot = normalizeResourceIdOrNull(slot) ?: return null
        val namespace = normalizedSlot.substringBefore(':')
        val path = normalizedSlot.substringAfter(':')
        return "assets/$namespace/textures/$path.png"
    }

    private fun normalizeAttachmentType(rawType: String?): String? {
        val normalized = rawType
            ?.trim()
            ?.lowercase()
            ?.map { ch -> if (ch.isLetterOrDigit()) ch else '_' }
            ?.joinToString(separator = "")
            ?.replace("__+".toRegex(), "_")
            ?.trim('_')
            ?.ifBlank { null }
            ?: return null

        return when (normalized) {
            "scope" -> "SCOPE"
            "muzzle" -> "MUZZLE"
            "extended_mag", "extendedmag", "mag" -> "EXTENDED_MAG"
            "stock" -> "STOCK"
            "grip" -> "GRIP"
            "laser" -> "LASER"
            else -> normalized.uppercase()
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

    private fun parseStringArray(json: String): List<String>? {
        val sanitized = stripJsonComments(json)
        val root = runCatching { JsonParser().parse(sanitized) }.getOrNull() ?: return null
        if (!root.isJsonArray) {
            return null
        }
        return root.asJsonArray
            .mapNotNull { it.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString }
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

    private fun readZipEntry(zip: ZipFile, entryName: String): String {
        val entry = zip.getEntry(entryName) ?: error("Missing zip entry: $entryName")
        zip.getInputStream(entry).use { stream ->
            return String(stream.readBytes(), StandardCharsets.UTF_8)
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

    private fun JsonObject.readString(key: String): String? {
        val element = this.get(key) ?: return null
        if (!element.isJsonPrimitive || !element.asJsonPrimitive.isString) {
            return null
        }
        return element.asString.trim().ifBlank { null }
    }

    private companion object {
        private const val DEFAULT_NAMESPACE: String = "tacz"
        private const val PACK_META_FILE_NAME: String = "gunpack.meta.json"

        private val INDEX_ATTACHMENTS_RELATIVE_REGEX: Regex = Regex("^([^/]+)/index/attachments/(.+)\\.json$")
        private val ALLOW_ATTACHMENTS_RELATIVE_REGEX: Regex = Regex("^([^/]+)/tacz_tags/attachments/allow_attachments/(.+)\\.json$")
        private val ATTACHMENT_TAG_RELATIVE_REGEX: Regex = Regex("^([^/]+)/tacz_tags/attachments/(.+)\\.json$")
        private val AMMO_DISPLAY_RELATIVE_REGEX: Regex = Regex("^([^/]+)/display/ammo/(.+)_display\\.json$")

        private val INDEX_ATTACHMENTS_ENTRY_REGEX: Regex = Regex("^(?:.*/)?data/([^/]+)/index/attachments/(.+)\\.json$")
        private val ALLOW_ATTACHMENTS_ENTRY_REGEX: Regex = Regex("^(?:.*/)?data/([^/]+)/tacz_tags/attachments/allow_attachments/(.+)\\.json$")
        private val ATTACHMENT_TAG_ENTRY_REGEX: Regex = Regex("^(?:.*/)?data/([^/]+)/tacz_tags/attachments/(.+)\\.json$")
        private val AMMO_DISPLAY_ENTRY_REGEX: Regex = Regex("^(?:.*/)?assets/([^/]+)/display/ammo/(.+)_display\\.json$")
    }
}
