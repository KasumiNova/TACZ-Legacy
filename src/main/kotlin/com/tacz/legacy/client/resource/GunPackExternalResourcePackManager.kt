package com.tacz.legacy.client.resource

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.tacz.legacy.client.sound.TaczSoundEngine
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.IResourcePack
import net.minecraft.client.resources.data.IMetadataSection
import net.minecraft.client.resources.data.MetadataSerializer
import net.minecraft.util.ResourceLocation
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.FilterInputStream
import java.io.InputStream
import java.lang.reflect.Field
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.zip.ZipFile

/**
 * 将 run/tacz 下的外部枪包以资源包形式桥接进 Minecraft 资源系统：
 * 1) 暴露原始 assets/... 资源（含 sounds.json 与 sounds 目录下的 ogg 文件）；
 * 2) 额外提供 custom/<packId>/... 别名，兼容当前渲染解析器的路径约定。
 */
public object GunPackExternalResourcePackManager {

    private val installedPacksByKey: MutableMap<String, GunPackExternalResourcePack> = linkedMapOf()

    public fun installOrRefresh(logger: Logger, forceRefreshResources: Boolean) {
        val minecraft = runCatching { Minecraft.getMinecraft() }.getOrNull() ?: return
        val defaultResourcePacks = resolveDefaultResourcePackList(minecraft)
        if (defaultResourcePacks == null) {
            logger.warn("[GunPackResource] Failed to resolve Minecraft.defaultResourcePacks; external gun packs won't be mounted.")
            return
        }

        val descriptors = scanExternalPackDescriptors(minecraft.gameDir.toPath())
        val desiredKeys = descriptors.mapTo(linkedSetOf()) { it.key }

        var changed = false

        val staleKeys = installedPacksByKey.keys.filter { it !in desiredKeys }
        staleKeys.forEach { key ->
            val removed = installedPacksByKey.remove(key) ?: return@forEach
            if (defaultResourcePacks.remove(removed)) {
                changed = true
            }
        }

        descriptors.forEach { descriptor ->
            if (installedPacksByKey.containsKey(descriptor.key)) {
                return@forEach
            }
            val resourcePack = GunPackExternalResourcePack(descriptor)
            installedPacksByKey[descriptor.key] = resourcePack
            defaultResourcePacks.add(resourcePack)
            changed = true
        }

        if (changed || (forceRefreshResources && installedPacksByKey.isNotEmpty())) {
            minecraft.refreshResources()
        }

        logger.info(
            "[GunPackResource] mounted external packs: count={} changed={} root={}",
            installedPacksByKey.size,
            changed,
            minecraft.gameDir.toPath().resolve("tacz").toAbsolutePath().normalize()
        )

        // preInit 阶段 OpenAL native 可能尚未加载，先注册延迟预加载任务。
        preloadSoundEngine(descriptors, logger)
    }

    private fun preloadSoundEngine(descriptors: List<ExternalPackDescriptor>, logger: Logger) {
        val sources = descriptors.flatMap { descriptor ->
            descriptor.resourceDomains.flatMap { namespace ->
                when (descriptor.kind) {
                    ExternalPackKind.DIRECTORY -> listOf(
                        TaczSoundEngine.DirectorySoundPackSource(
                            namespace = namespace,
                            packRoot = descriptor.containerPath,
                            customPackId = descriptor.packId
                        )
                    )
                    ExternalPackKind.ZIP -> listOf(
                        TaczSoundEngine.ZipSoundPackSource(
                            namespace = namespace,
                            zipPath = descriptor.containerPath,
                            zipPrefixes = descriptor.zipPrefixes,
                            customPackId = descriptor.packId
                        )
                    )
                }
            }
        }

        TaczSoundEngine.queuePreloadFromPacks(sources)
        logger.info("[GunPackResource] queued tacz sound preload sources={}", sources.size)
    }

    /**
     * Test-only factory: creates a standalone external resource pack wrapper without touching Minecraft internals.
     * This exists to unit-test the sounds bridge (sounds -> tacz_sounds) and related path compatibility rules.
     */
    internal fun createExternalResourcePackForTests(
        packId: String,
        containerPath: Path,
        isZip: Boolean,
        zipPrefixes: List<String> = listOf(""),
        resourceDomains: Set<String> = setOf(DEFAULT_RESOURCE_DOMAIN)
    ): IResourcePack {
        val normalizedPackId = normalizePackId(packId) ?: (packId.trim().ifBlank { "test_pack" })
        val kind = if (isZip) ExternalPackKind.ZIP else ExternalPackKind.DIRECTORY
        val normalizedContainerPath = containerPath.toAbsolutePath().normalize()
        val compatCacheBase = if (isZip) {
            normalizedContainerPath.parent ?: normalizedContainerPath
        } else {
            normalizedContainerPath
        }
        val descriptor = ExternalPackDescriptor(
            key = "TEST|$kind|$normalizedContainerPath|$normalizedPackId",
            kind = kind,
            packId = normalizedPackId,
            containerPath = normalizedContainerPath,
            zipPrefixes = zipPrefixes.map(::normalizePrefix),
            resourceDomains = resourceDomains + DEFAULT_RESOURCE_DOMAIN,
            compatCacheRoot = compatCacheBase.resolve(".legacy_audio_compat")
        )
        return GunPackExternalResourcePack(descriptor)
    }

    private fun scanExternalPackDescriptors(gameDir: Path): List<ExternalPackDescriptor> {
        val taczRoot = gameDir.toAbsolutePath().normalize().resolve("tacz")
        if (!Files.isDirectory(taczRoot)) {
            return emptyList()
        }
        val compatCacheRoot = taczRoot.resolve(".legacy_audio_compat")

        val descriptors = mutableListOf<ExternalPackDescriptor>()
        val children = runCatching {
            Files.list(taczRoot).use { stream ->
                val collected = mutableListOf<Path>()
                stream.forEach { collected.add(it) }
                collected.sortedBy { it.fileName.toString().lowercase() }
            }
        }.getOrNull() ?: return emptyList()

        children.forEach { child ->
            when {
                Files.isDirectory(child) -> {
                    if (!Files.isRegularFile(child.resolve(PACK_META_FILE_NAME))) {
                        return@forEach
                    }
                    val packId = normalizePackId(child.fileName.toString()) ?: return@forEach
                    val domains = detectDirectoryDomains(child).ifEmpty { setOf(DEFAULT_RESOURCE_DOMAIN) }
                    descriptors += ExternalPackDescriptor(
                        key = buildDescriptorKey(kind = ExternalPackKind.DIRECTORY, containerPath = child, packId = packId, prefixes = listOf("")),
                        kind = ExternalPackKind.DIRECTORY,
                        packId = packId,
                        containerPath = child.toAbsolutePath().normalize(),
                        zipPrefixes = listOf(""),
                        resourceDomains = domains + DEFAULT_RESOURCE_DOMAIN,
                        compatCacheRoot = compatCacheRoot
                    )
                }

                child.fileName.toString().endsWith(".zip", ignoreCase = true) -> {
                    val inspection = inspectZipPack(child) ?: return@forEach
                    val packId = normalizePackId(child.fileName.toString()) ?: return@forEach
                    val prefixes = (inspection.metaPrefixes + "")
                        .map(::normalizePrefix)
                        .distinct()
                    val domains = (inspection.resourceDomains + DEFAULT_RESOURCE_DOMAIN)
                    descriptors += ExternalPackDescriptor(
                        key = buildDescriptorKey(kind = ExternalPackKind.ZIP, containerPath = child, packId = packId, prefixes = prefixes),
                        kind = ExternalPackKind.ZIP,
                        packId = packId,
                        containerPath = child.toAbsolutePath().normalize(),
                        zipPrefixes = prefixes,
                        resourceDomains = domains,
                        compatCacheRoot = compatCacheRoot
                    )
                }
            }
        }

        return descriptors
    }

    private fun detectDirectoryDomains(packDir: Path): Set<String> {
        val assetsDir = packDir.resolve("assets")
        if (!Files.isDirectory(assetsDir)) {
            return emptySet()
        }

        return runCatching {
            Files.list(assetsDir).use { stream ->
                val domains = linkedSetOf<String>()
                stream.forEach { child ->
                    if (!Files.isDirectory(child)) {
                        return@forEach
                    }
                    child.fileName
                        .toString()
                        .trim()
                        .lowercase()
                        .takeIf { it.isNotBlank() }
                        ?.takeIf { it !in RESERVED_RESOURCE_DOMAINS }
                        ?.let(domains::add)
                }
                domains
            }
        }.getOrDefault(emptySet())
    }

    private fun inspectZipPack(zipPath: Path): ZipPackInspection? {
        return runCatching {
            ZipFile(zipPath.toFile()).use { zip ->
                val prefixes = linkedSetOf<String>()
                val domains = linkedSetOf<String>()

                val iterator = zip.entries()
                while (iterator.hasMoreElements()) {
                    val entry = iterator.nextElement()
                    if (entry.isDirectory) {
                        continue
                    }

                    val name = normalizeLogicalPath(entry.name)
                    if (name == PACK_META_FILE_NAME || name.endsWith("/$PACK_META_FILE_NAME")) {
                        val prefix = if (name == PACK_META_FILE_NAME) {
                            ""
                        } else {
                            name.removeSuffix(PACK_META_FILE_NAME)
                        }
                        prefixes += normalizePrefix(prefix)
                    }

                    extractDomainFromPackPath(name)
                        ?.takeIf { it !in RESERVED_RESOURCE_DOMAINS }
                        ?.let(domains::add)
                }

                if (prefixes.isEmpty()) {
                    null
                } else {
                    ZipPackInspection(
                        metaPrefixes = prefixes.toList(),
                        resourceDomains = domains.toSet()
                    )
                }
            }
        }.getOrNull()
    }

    private fun extractDomainFromPackPath(path: String): String? {
        val normalized = normalizeLogicalPath(path)
        val directPrefix = "assets/"
        if (normalized.startsWith(directPrefix)) {
            val domain = normalized.removePrefix(directPrefix).substringBefore('/').trim().lowercase()
            return domain.ifBlank { null }
        }

        val nestedMarker = "/assets/"
        if (!normalized.contains(nestedMarker)) {
            return null
        }

        val suffix = normalized.substringAfter(nestedMarker)
        val domain = suffix.substringBefore('/').trim().lowercase()
        return domain.ifBlank { null }
    }

    private fun buildDescriptorKey(
        kind: ExternalPackKind,
        containerPath: Path,
        packId: String,
        prefixes: List<String>
    ): String {
        return listOf(
            kind.name,
            containerPath.toAbsolutePath().normalize().toString(),
            packId,
            prefixes.joinToString(separator = ",")
        ).joinToString(separator = "|")
    }

    @Suppress("UNCHECKED_CAST")
    private fun resolveDefaultResourcePackList(minecraft: Minecraft): MutableList<IResourcePack>? {
        val field = resolveDefaultResourcePackField(minecraft.javaClass) ?: return null
        val value = runCatching { field.get(minecraft) }.getOrNull() ?: return null
        return value as? MutableList<IResourcePack>
    }

    private fun resolveDefaultResourcePackField(minecraftClass: Class<*>): Field? {
        val candidates = listOf("defaultResourcePacks", "field_110449_ao")
        candidates.forEach { fieldName ->
            val field = runCatching { minecraftClass.getDeclaredField(fieldName) }.getOrNull() ?: return@forEach
            field.isAccessible = true
            return field
        }
        return null
    }

    internal fun normalizePackId(raw: String?): String? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isBlank()) {
            return null
        }

        val withoutZip = if (trimmed.endsWith(".zip", ignoreCase = true)) {
            trimmed.dropLast(4)
        } else {
            trimmed
        }

        val sanitized = withoutZip
            .trim()
            .lowercase()
            .map { ch ->
                if (ch.isLetterOrDigit() || ch == '_' || ch == '-' || ch == '.') {
                    ch
                } else {
                    '_'
                }
            }
            .joinToString(separator = "")
            .replace("__+".toRegex(), "_")
            .trim('_')

        return sanitized.ifBlank { null }
    }

    private fun normalizePrefix(raw: String): String {
        val normalized = normalizeLogicalPath(raw)
        return if (normalized.isBlank()) {
            ""
        } else {
            normalized.trimEnd('/') + "/"
        }
    }

    private fun normalizeLogicalPath(path: String): String =
        path.replace('\\', '/').trim().trimStart('/')

    private data class ExternalPackDescriptor(
        val key: String,
        val kind: ExternalPackKind,
        val packId: String,
        val containerPath: Path,
        val zipPrefixes: List<String>,
        val resourceDomains: Set<String>,
        val compatCacheRoot: Path
    )

    private data class ZipPackInspection(
        val metaPrefixes: List<String>,
        val resourceDomains: Set<String>
    )

    private enum class ExternalPackKind {
        DIRECTORY,
        ZIP
    }

    private class GunPackExternalResourcePack(
        private val descriptor: ExternalPackDescriptor
    ) : IResourcePack {

        private val logger = LogManager.getLogger("tacz")

        private val generatedSoundKeysByNamespace: MutableMap<String, Set<String>> = ConcurrentHashMap()
        private val resourceExistenceCache: MutableMap<String, Boolean> = ConcurrentHashMap()
        private val failureCooldownUntilMillis: MutableMap<String, Long> = ConcurrentHashMap()
        private val transcodedAudioPathByRequestKey: MutableMap<String, Path> = ConcurrentHashMap()

        override fun getInputStream(location: ResourceLocation): InputStream {
            val cacheKey = location.toString()

            val now = System.currentTimeMillis()
            val cooldownUntil = failureCooldownUntilMillis[cacheKey]
            if (cooldownUntil != null && now < cooldownUntil) {
                throw FileNotFoundException("Resource temporarily blocked (cooldown) in gun pack ${descriptor.packId}: $location")
            }

            try {
                if (location.path == SOUNDS_JSON_FILE_NAME) {
                    val generated = resolveGeneratedSoundsJson(location.namespace)
                    if (generated != null) {
                        // sounds.json is virtual; keep it cheap and deterministic.
                        cacheExistence(cacheKey, true)
                        return ByteArrayInputStream(generated)
                    }
                }

                if (isSoundOggResource(location)) {
                    val compatibleStream = resolveCompatibleAudioStream(location, cacheKey)
                    if (compatibleStream != null) {
                        cacheExistence(cacheKey, true)
                        return compatibleStream
                    }
                }

                val candidates = resolveLogicalCandidates(location)
                if (candidates.isEmpty()) {
                    markFailure(cacheKey, now)
                    throw FileNotFoundException("Resource not found in gun pack ${descriptor.packId}: $location")
                }

                val resolvedStream = when (descriptor.kind) {
                    ExternalPackKind.DIRECTORY -> resolveInputStreamFromDirectory(candidates)
                    ExternalPackKind.ZIP -> resolveInputStreamFromZip(candidates)
                }
                if (resolvedStream != null) {
                    cacheExistence(cacheKey, true)
                    return resolvedStream
                }

                markFailure(cacheKey, now)
                throw FileNotFoundException("Resource not found in gun pack ${descriptor.packId}: $location")
            } catch (e: Exception) {
                // Ensure existence cache can't lie after a failure.
                markFailure(cacheKey, now)
                if (e is FileNotFoundException) {
                    throw e
                }
                // Preserve the root cause for diagnosing freezes/hangs in audio threads.
                throw e
            }
        }

        override fun resourceExists(location: ResourceLocation): Boolean {
            val cacheKey = location.toString()

            val now = System.currentTimeMillis()
            val cooldownUntil = failureCooldownUntilMillis[cacheKey]
            if (cooldownUntil != null && now < cooldownUntil) {
                return false
            }

            resourceExistenceCache[cacheKey]?.let { return it }

            val exists = resolveExists(location)
            cacheExistence(cacheKey, exists)
            return exists
        }

        override fun getResourceDomains(): MutableSet<String> = descriptor.resourceDomains.toMutableSet()

        override fun <T : IMetadataSection> getPackMetadata(
            metadataSerializer: MetadataSerializer,
            metadataSectionName: String
        ): T? = null

        override fun getPackImage(): BufferedImage = EMPTY_PACK_IMAGE

        override fun getPackName(): String = "TACZ External Gun Pack (${descriptor.packId})"

        private fun resolveExists(location: ResourceLocation): Boolean {
            val candidates = resolveLogicalCandidates(location)
            if (candidates.isNotEmpty()) {
                val matched = when (descriptor.kind) {
                    ExternalPackKind.DIRECTORY -> resolveExistsFromDirectory(candidates)
                    ExternalPackKind.ZIP -> resolveExistsFromZip(candidates)
                }
                if (matched) {
                    return true
                }
            }

            if (isSoundOggResource(location) && resolveCompatibleAudioSourceExists(location)) {
                return true
            }

            if (location.path == SOUNDS_JSON_FILE_NAME) {
                return resolveGeneratedSoundsJson(location.namespace) != null
            }

            return false
        }

        private fun cacheExistence(cacheKey: String, exists: Boolean) {
            if (resourceExistenceCache.size > MAX_EXISTENCE_CACHE_SIZE) {
                resourceExistenceCache.clear()
            }
            resourceExistenceCache[cacheKey] = exists
        }

        private fun markFailure(cacheKey: String, nowMillis: Long = System.currentTimeMillis()) {
            cacheExistence(cacheKey, false)
            failureCooldownUntilMillis[cacheKey] = nowMillis + FAILURE_COOLDOWN_MILLIS
            if (failureCooldownUntilMillis.size > MAX_FAILURE_CACHE_SIZE) {
                failureCooldownUntilMillis.clear()
            }
        }

        private fun resolveLogicalCandidates(location: ResourceLocation): List<String> {
            val candidates = linkedSetOf<String>()
            val customAssetRoot = "assets/${location.namespace}/custom/${descriptor.packId}/assets/${location.namespace}"

            parseCustomAliasPath(location.path)?.let { alias ->
                if (alias.packId == descriptor.packId) {
                    candidates += alias.innerPath
                }
            }

            candidates += "assets/${location.namespace}/${location.path}"
            candidates += "$customAssetRoot/${location.path}"
            if (location.path.startsWith("sounds/")) {
                val relative = location.path.removePrefix("sounds/")
                if (relative.isNotBlank()) {
                    candidates += "assets/${location.namespace}/tacz_sounds/$relative"
                    candidates += "$customAssetRoot/tacz_sounds/$relative"
                }
            }
            return candidates.map(::normalizeLogicalPath).filter { it.isNotBlank() }
        }

        private fun isSoundOggResource(location: ResourceLocation): Boolean {
            val path = location.path.trim().lowercase()
            return path.startsWith("sounds/") && path.endsWith(".ogg")
        }

        private fun resolveCompatibleAudioStream(location: ResourceLocation, requestCacheKey: String): InputStream? {
            transcodedAudioPathByRequestKey[requestCacheKey]?.let { cachedPath ->
                if (Files.isRegularFile(cachedPath)) {
                    return Files.newInputStream(cachedPath)
                }
                transcodedAudioPathByRequestKey.remove(requestCacheKey)
            }

            val source = resolveCompatibleAudioSource(location) ?: return null
            val shouldAttemptTranscode = when {
                source.extension != "ogg" -> AudioCompatTranscoder.isAvailable()
                FORCE_TRANSCODE_OGG_FOR_COMPAT -> AudioCompatTranscoder.isAvailable()
                else -> AudioCompatTranscoder.shouldTranscodeOggForCompatibility(
                    sourceBytes = source.bytes,
                    sourcePathHint = source.logicalPath,
                    packId = descriptor.packId
                )
            }

            if (shouldAttemptTranscode) {
                val transcoded = AudioCompatTranscoder.transcodeToLegacyOgg(
                    cacheRoot = descriptor.compatCacheRoot,
                    packId = descriptor.packId,
                    sourceBytes = source.bytes,
                    sourceExtension = source.extension,
                    sourcePathHint = source.logicalPath
                )
                if (transcoded != null && Files.isRegularFile(transcoded)) {
                    transcodedAudioPathByRequestKey[requestCacheKey] = transcoded
                    return Files.newInputStream(transcoded)
                }
            }

            if (source.extension == "ogg") {
                return ByteArrayInputStream(source.bytes)
            }

            return null
        }

        private fun resolveCompatibleAudioSourceExists(location: ResourceLocation): Boolean {
            if (!AudioCompatTranscoder.isAvailable()) {
                return false
            }

            val variantLocations = resolveAudioVariantLocations(location)
            if (variantLocations.size <= 1) {
                return false
            }

            variantLocations.drop(1).forEach { variantLocation ->
                val candidates = resolveLogicalCandidates(variantLocation)
                if (candidates.isEmpty()) {
                    return@forEach
                }

                val matched = when (descriptor.kind) {
                    ExternalPackKind.DIRECTORY -> resolveExistsFromDirectory(candidates)
                    ExternalPackKind.ZIP -> resolveExistsFromZip(candidates)
                }
                if (matched) {
                    return true
                }
            }

            return false
        }

        private fun resolveCompatibleAudioSource(location: ResourceLocation): ResolvedAudioSource? {
            val variantLocations = resolveAudioVariantLocations(location)
            if (variantLocations.isEmpty()) {
                return null
            }

            variantLocations.forEach { variantLocation ->
                val candidates = resolveLogicalCandidates(variantLocation)
                if (candidates.isEmpty()) {
                    return@forEach
                }

                val resolved = when (descriptor.kind) {
                    ExternalPackKind.DIRECTORY -> resolveAudioSourceFromDirectory(candidates)
                    ExternalPackKind.ZIP -> resolveAudioSourceFromZip(candidates)
                }

                if (resolved != null) {
                    return resolved
                }
            }

            return null
        }

        private fun resolveAudioVariantLocations(location: ResourceLocation): List<ResourceLocation> {
            if (!isSoundOggResource(location)) {
                return listOf(location)
            }

            val rawPath = normalizeLogicalPath(location.path)
            val extensionIndex = rawPath.lastIndexOf('.')
            if (extensionIndex <= 0) {
                return listOf(location)
            }

            val pathWithoutExtension = rawPath.substring(0, extensionIndex)
            return AUDIO_COMPAT_SOURCE_EXTENSIONS.map { extension ->
                ResourceLocation(location.namespace, "$pathWithoutExtension.$extension")
            }
        }

        private fun resolveAudioSourceFromDirectory(logicalCandidates: List<String>): ResolvedAudioSource? {
            for (logicalPath in logicalCandidates) {
                if (logicalPath.contains("..")) {
                    continue
                }

                val resolved = descriptor.containerPath.resolve(logicalPath).normalize()
                if (!resolved.startsWith(descriptor.containerPath) || !Files.isRegularFile(resolved)) {
                    continue
                }

                val extension = extractAudioExtension(logicalPath) ?: continue
                val bytes = runCatching { Files.readAllBytes(resolved) }.getOrNull() ?: continue
                return ResolvedAudioSource(logicalPath = logicalPath, extension = extension, bytes = bytes)
            }

            return null
        }

        private fun resolveAudioSourceFromZip(logicalCandidates: List<String>): ResolvedAudioSource? {
            return runCatching {
                ZipFile(descriptor.containerPath.toFile()).use { zip ->
                    for (logicalPath in logicalCandidates) {
                        val extension = extractAudioExtension(logicalPath) ?: continue

                        for (prefix in descriptor.zipPrefixes) {
                            val entryName = normalizeLogicalPath(prefix + logicalPath)
                            val entry = zip.getEntry(entryName) ?: continue
                            if (entry.isDirectory) {
                                continue
                            }

                            val bytes = zip.getInputStream(entry).use { input -> input.readBytes() }
                            return@use ResolvedAudioSource(
                                logicalPath = logicalPath,
                                extension = extension,
                                bytes = bytes
                            )
                        }
                    }

                    null
                }
            }.getOrNull()
        }

        private fun extractAudioExtension(logicalPath: String): String? {
            val normalized = normalizeLogicalPath(logicalPath)
            val extension = normalized.substringAfterLast('.', missingDelimiterValue = "").trim().lowercase()
            return extension.takeIf { it in AUDIO_COMPAT_SOURCE_EXTENSIONS }
        }

        private fun resolveGeneratedSoundsJson(namespace: String): ByteArray? {
            val normalizedNamespace = namespace.trim().lowercase().ifBlank { return null }
            val keys = generatedSoundKeysByNamespace.computeIfAbsent(normalizedNamespace) {
                resolveSoundKeys(normalizedNamespace).toSet()
            }
            if (keys.isEmpty()) {
                return null
            }

            val root = JsonObject()
            keys.forEach { key ->
                val definition = JsonObject()
                val sounds = JsonArray()
                sounds.add("$normalizedNamespace:$key")
                definition.add("sounds", sounds)
                root.add(key, definition)
            }
            return root.toString().toByteArray(Charsets.UTF_8)
        }

        private fun resolveSoundKeys(namespace: String): Set<String> {
            return when (descriptor.kind) {
                ExternalPackKind.DIRECTORY -> resolveSoundKeysFromDirectory(namespace)
                ExternalPackKind.ZIP -> resolveSoundKeysFromZip(namespace)
            }
        }

        private fun resolveSoundKeysFromDirectory(namespace: String): Set<String> {
            val collected = linkedSetOf<String>()
            collectSoundKeysFromDirectoryRoot(
                root = descriptor.containerPath.resolve("assets").resolve(namespace).resolve("tacz_sounds"),
                relativePrefix = "",
                sink = collected
            )
            collectSoundKeysFromDirectoryRoot(
                root = descriptor.containerPath.resolve("assets").resolve(namespace).resolve("sounds"),
                relativePrefix = "",
                sink = collected
            )
            collectSoundKeysFromDirectoryRoot(
                root = descriptor.containerPath
                    .resolve("assets")
                    .resolve(namespace)
                    .resolve("custom")
                    .resolve(descriptor.packId)
                    .resolve("assets")
                    .resolve(namespace)
                    .resolve("tacz_sounds"),
                relativePrefix = "",
                sink = collected
            )
            collectSoundKeysFromDirectoryRoot(
                root = descriptor.containerPath
                    .resolve("assets")
                    .resolve(namespace)
                    .resolve("custom")
                    .resolve(descriptor.packId)
                    .resolve("assets")
                    .resolve(namespace)
                    .resolve("sounds"),
                relativePrefix = "",
                sink = collected
            )
            return collected
        }

        private fun collectSoundKeysFromDirectoryRoot(
            root: Path,
            relativePrefix: String,
            sink: MutableSet<String>
        ) {
            if (!Files.isDirectory(root)) {
                return
            }

            runCatching {
                Files.walk(root).use { stream ->
                    stream.forEach { child ->
                        if (!Files.isRegularFile(child)) {
                            return@forEach
                        }
                        val relative = root.relativize(child)
                            .toString()
                            .replace('\\', '/')
                            .trim()
                            .trimStart('/')
                        val candidate = (relativePrefix + relative)
                            .trim()
                            .trimStart('/')
                        normalizeSoundKey(candidate)?.let(sink::add)
                    }
                }
            }
        }

        private fun resolveSoundKeysFromZip(namespace: String): Set<String> {
            val collected = linkedSetOf<String>()
            runCatching {
                ZipFile(descriptor.containerPath.toFile()).use { zip ->
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        if (entry.isDirectory) {
                            continue
                        }

                        val normalizedEntry = normalizeLogicalPath(entry.name)
                        descriptor.zipPrefixes.forEach { prefix ->
                            val normalizedPrefix = normalizePrefix(prefix)
                            val taczSoundPrefix = normalizeLogicalPath("${normalizedPrefix}assets/$namespace/tacz_sounds/")
                            if (normalizedEntry.startsWith(taczSoundPrefix)) {
                                val relative = normalizedEntry.removePrefix(taczSoundPrefix)
                                normalizeSoundKey(relative)?.let(collected::add)
                                return@forEach
                            }

                            val vanillaSoundPrefix = normalizeLogicalPath("${normalizedPrefix}assets/$namespace/sounds/")
                            if (normalizedEntry.startsWith(vanillaSoundPrefix)) {
                                val relative = normalizedEntry.removePrefix(vanillaSoundPrefix)
                                normalizeSoundKey(relative)?.let(collected::add)
                                return@forEach
                            }

                            val customTaczSoundPrefix = normalizeLogicalPath(
                                "${normalizedPrefix}assets/$namespace/custom/${descriptor.packId}/assets/$namespace/tacz_sounds/"
                            )
                            if (normalizedEntry.startsWith(customTaczSoundPrefix)) {
                                val relative = normalizedEntry.removePrefix(customTaczSoundPrefix)
                                normalizeSoundKey(relative)?.let(collected::add)
                                return@forEach
                            }

                            val customVanillaSoundPrefix = normalizeLogicalPath(
                                "${normalizedPrefix}assets/$namespace/custom/${descriptor.packId}/assets/$namespace/sounds/"
                            )
                            if (normalizedEntry.startsWith(customVanillaSoundPrefix)) {
                                val relative = normalizedEntry.removePrefix(customVanillaSoundPrefix)
                                normalizeSoundKey(relative)?.let(collected::add)
                            }
                        }
                    }
                }
            }
            return collected
        }

        private fun normalizeSoundKey(path: String): String? {
            val normalizedPath = normalizeLogicalPath(path)
            if (normalizedPath.isBlank()) {
                return null
            }

            // Minecraft 1.12 sound pipeline hardcodes ".ogg" in Sound#getSoundAsOggLocation.
            // For non-ogg sources (mp3/wav), only generate keys when transcoder compatibility is available.
            if (!normalizedPath.endsWith(".ogg", ignoreCase = true)) {
                val hasCompatibleTranscoder = AudioCompatTranscoder.isAvailable()
                val isNonOggSupportedSource = AUDIO_COMPAT_SOURCE_EXTENSIONS
                    .asSequence()
                    .filter { it != "ogg" }
                    .any { extension -> normalizedPath.endsWith(".$extension", ignoreCase = true) }
                if (!(AUDIO_COMPAT_GENERATE_NON_OGG_KEYS && hasCompatibleTranscoder && isNonOggSupportedSource)) {
                    return null
                }
            }

            return normalizedPath.substringBeforeLast('.')
        }

        private fun resolveInputStreamFromDirectory(logicalCandidates: List<String>): InputStream? {
            var firstIOException: Exception? = null

            for (logicalPath in logicalCandidates) {
                if (logicalPath.contains("..")) {
                    continue
                }
                val resolved = descriptor.containerPath.resolve(logicalPath).normalize()
                if (!resolved.startsWith(descriptor.containerPath)) {
                    continue
                }
                if (!Files.isRegularFile(resolved)) {
                    continue
                }

                try {
                    return Files.newInputStream(resolved)
                } catch (e: Exception) {
                    if (firstIOException == null) {
                        firstIOException = e
                    }
                }
            }

            if (firstIOException != null) {
                // Surface the real IO issue rather than masquerading as “not found”.
                throw firstIOException
            }

            return null
        }

        private fun resolveInputStreamFromZip(logicalCandidates: List<String>): InputStream? {
            // ZipFile input streams become invalid once the ZipFile is closed.
            // We keep the ZipFile open for the lifetime of the returned stream and close it when the stream is closed.
            val zip = ZipFile(descriptor.containerPath.toFile())
            var firstIOException: Exception? = null

            try {
                for (logicalPath in logicalCandidates) {
                    for (prefix in descriptor.zipPrefixes) {
                        val entryName = normalizeLogicalPath(prefix + logicalPath)
                        val entry = zip.getEntry(entryName) ?: continue
                        if (entry.isDirectory) {
                            continue
                        }
                        try {
                            val input = zip.getInputStream(entry)
                            return object : FilterInputStream(input) {
                                override fun close() {
                                    try {
                                        super.close()
                                    } finally {
                                        zip.close()
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            if (firstIOException == null) {
                                firstIOException = e
                            }
                        }
                    }
                }

                if (firstIOException != null) {
                    throw firstIOException
                }

                zip.close()
                return null
            } catch (e: Exception) {
                runCatching { zip.close() }
                throw e
            }
        }

        private fun resolveExistsFromDirectory(logicalCandidates: List<String>): Boolean {
            for (logicalPath in logicalCandidates) {
                if (logicalPath.contains("..")) {
                    continue
                }
                val resolved = descriptor.containerPath.resolve(logicalPath).normalize()
                if (!resolved.startsWith(descriptor.containerPath)) {
                    continue
                }
                if (Files.isRegularFile(resolved)) {
                    return true
                }
            }
            return false
        }

        private fun resolveExistsFromZip(logicalCandidates: List<String>): Boolean {
            return runCatching {
                ZipFile(descriptor.containerPath.toFile()).use { zip ->
                    for (logicalPath in logicalCandidates) {
                        for (prefix in descriptor.zipPrefixes) {
                            val entryName = normalizeLogicalPath(prefix + logicalPath)
                            val entry = zip.getEntry(entryName) ?: continue
                            if (!entry.isDirectory) {
                                return true
                            }
                        }
                    }
                    false
                }
            }.getOrDefault(false)
        }

        private fun parseCustomAliasPath(path: String): CustomAliasPath? {
            val normalizedPath = normalizeLogicalPath(path)
            if (!normalizedPath.startsWith("custom/")) {
                return null
            }

            val remainder = normalizedPath.removePrefix("custom/")
            val packSegment = remainder.substringBefore('/').trim()
            val inner = remainder.substringAfter('/', missingDelimiterValue = "").trim()
            if (packSegment.isBlank() || inner.isBlank()) {
                return null
            }

            val packId = normalizePackId(packSegment) ?: return null
            return CustomAliasPath(
                packId = packId,
                innerPath = normalizeLogicalPath(inner)
            )
        }

        private data class CustomAliasPath(
            val packId: String,
            val innerPath: String
        )

        private data class ResolvedAudioSource(
            val logicalPath: String,
            val extension: String,
            val bytes: ByteArray
        )

        private companion object {
            private val EMPTY_PACK_IMAGE: BufferedImage = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
            private const val SOUNDS_JSON_FILE_NAME: String = "sounds.json"

            private const val FAILURE_COOLDOWN_MILLIS: Long = 10_000L
            private const val MAX_EXISTENCE_CACHE_SIZE: Int = 8192
            private const val MAX_FAILURE_CACHE_SIZE: Int = 2048
            private val AUDIO_COMPAT_SOURCE_EXTENSIONS: List<String> = listOf("ogg", "mp3", "wav")
            private val FORCE_TRANSCODE_OGG_FOR_COMPAT: Boolean =
                java.lang.Boolean.parseBoolean(System.getProperty("tacz.audio.compat.forceOggTranscode", "true"))
        }
    }

    private const val PACK_META_FILE_NAME: String = "gunpack.meta.json"
    private const val DEFAULT_RESOURCE_DOMAIN: String = "tacz"

    /**
     * External gun packs should never be allowed to override these base domains.
     * Doing so can accidentally shadow vanilla/Forge resources and cause hard-to-debug issues.
     */
    private val RESERVED_RESOURCE_DOMAINS: Set<String> = setOf("minecraft", "forge", "fml")

    private object AudioCompatTranscoder {
        private val logger: Logger = LogManager.getLogger("tacz")
        private val transcodeLocksByKey: MutableMap<String, Any> = ConcurrentHashMap()
        private val transcodeResultByKey: MutableMap<String, Path> = ConcurrentHashMap()
        private val failedTranscodeKeys: MutableSet<String> = ConcurrentHashMap.newKeySet()
        private val jOrbisCompatibilityByDigest: MutableMap<String, Boolean> = ConcurrentHashMap()

        @Volatile
        private var probeCompleted: Boolean = false

        @Volatile
        private var transcoderBinary: String? = null

        fun isAvailable(): Boolean {
            if (!AUDIO_COMPAT_ENABLED) {
                return false
            }
            ensureProbe()
            return !transcoderBinary.isNullOrBlank()
        }

        fun transcodeToLegacyOgg(
            cacheRoot: Path,
            packId: String,
            sourceBytes: ByteArray,
            sourceExtension: String,
            sourcePathHint: String
        ): Path? {
            if (!isAvailable() || sourceBytes.isEmpty()) {
                return null
            }

            val normalizedExtension = sourceExtension.trim().lowercase().ifBlank { return null }
            val digest = sha1Hex(sourceBytes)
            val cacheKey = "$packId|$normalizedExtension|$digest"
            if (failedTranscodeKeys.contains(cacheKey)) {
                return null
            }
            transcodeResultByKey[cacheKey]?.let { cached ->
                return if (Files.isRegularFile(cached)) cached else null
            }

            val lock = transcodeLocksByKey.computeIfAbsent(cacheKey) { Any() }
            synchronized(lock) {
                transcodeResultByKey[cacheKey]?.let { cached ->
                    return if (Files.isRegularFile(cached)) cached else null
                }
                if (failedTranscodeKeys.contains(cacheKey)) {
                    return null
                }

                val outputDir = cacheRoot.resolve(packId).normalize()
                val createCacheDirResult = runCatching { Files.createDirectories(outputDir) }
                if (createCacheDirResult.isFailure) {
                    logger.warn(
                        "[GunPackAudioCompat] Failed to create cache dir for pack={} path={}",
                        packId,
                        outputDir,
                        createCacheDirResult.exceptionOrNull()
                    )
                    failedTranscodeKeys += cacheKey
                    return null
                }

                val finalOutput = outputDir.resolve("$digest.$normalizedExtension.legacy.ogg")
                if (Files.isRegularFile(finalOutput)) {
                    transcodeResultByKey[cacheKey] = finalOutput
                    failedTranscodeKeys.remove(cacheKey)
                    return finalOutput
                }

                val inputTemp = Files.createTempFile(outputDir, "audio_src_", ".$normalizedExtension")
                val outputTemp = Files.createTempFile(outputDir, "audio_out_", ".ogg")

                try {
                    Files.write(inputTemp, sourceBytes)

                    val binary = transcoderBinary ?: run {
                        failedTranscodeKeys += cacheKey
                        return null
                    }

                    val command = listOf(
                        binary,
                        "-hide_banner",
                        "-loglevel",
                        "error",
                        "-y",
                        "-i",
                        inputTemp.toString(),
                        "-vn",
                        "-ar",
                        "44100",
                        "-ac",
                        "1",
                        "-c:a",
                        "libvorbis",
                        outputTemp.toString()
                    )

                    val process = ProcessBuilder(command)
                        .directory(File(cacheRoot.toString()))
                        .redirectErrorStream(true)
                        .start()

                    val outputText = process.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    val finished = process.waitFor(TRANSCODE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    if (!finished) {
                        process.destroyForcibly()
                        logger.warn(
                            "[GunPackAudioCompat] Transcode timeout pack={} source={} ext={}",
                            packId,
                            sourcePathHint,
                            normalizedExtension
                        )
                        failedTranscodeKeys += cacheKey
                        return null
                    }

                    if (process.exitValue() != 0 || !Files.isRegularFile(outputTemp) || Files.size(outputTemp) <= 0L) {
                        logger.warn(
                            "[GunPackAudioCompat] Transcode failed pack={} source={} ext={} exit={} output={}",
                            packId,
                            sourcePathHint,
                            normalizedExtension,
                            process.exitValue(),
                            outputText.take(512)
                        )
                        failedTranscodeKeys += cacheKey
                        return null
                    }

                    Files.move(outputTemp, finalOutput, StandardCopyOption.REPLACE_EXISTING)
                    transcodeResultByKey[cacheKey] = finalOutput
                    failedTranscodeKeys.remove(cacheKey)
                    return finalOutput
                } catch (t: Throwable) {
                    logger.warn(
                        "[GunPackAudioCompat] Transcode exception pack={} source={} ext={}",
                        packId,
                        sourcePathHint,
                        normalizedExtension,
                        t
                    )
                    failedTranscodeKeys += cacheKey
                    return null
                } finally {
                    runCatching { Files.deleteIfExists(inputTemp) }
                    runCatching { Files.deleteIfExists(outputTemp) }
                }
            }
        }

        fun shouldTranscodeOggForCompatibility(
            sourceBytes: ByteArray,
            sourcePathHint: String,
            packId: String
        ): Boolean {
            if (!isAvailable() || sourceBytes.isEmpty()) {
                return false
            }

            val digest = sha1Hex(sourceBytes)
            jOrbisCompatibilityByDigest[digest]?.let { compatible ->
                return !compatible
            }

            val compatible = probeJOrbisCompatibility(sourceBytes)
            jOrbisCompatibilityByDigest[digest] = compatible
            if (!compatible) {
                logger.warn(
                    "[GunPackAudioCompat] Detected JOrbis-incompatible ogg, transcode required pack={} source={}",
                    packId,
                    sourcePathHint
                )
            }
            return !compatible
        }

        private fun ensureProbe() {
            if (probeCompleted) {
                return
            }

            synchronized(this) {
                if (probeCompleted) {
                    return
                }

                transcoderBinary = resolveTranscoderBinary()
                probeCompleted = true

                if (transcoderBinary == null) {
                    logger.warn(
                        "[GunPackAudioCompat] Audio transcoder unavailable. Set system property '{}' to ffmpeg path to enable compatibility transcoding.",
                        AUDIO_COMPAT_TRANSCODER_PROPERTY
                    )
                } else {
                    logger.info("[GunPackAudioCompat] Audio transcoder enabled: {}", transcoderBinary)
                }
            }
        }

        private fun resolveTranscoderBinary(): String? {
            val configured = System.getProperty(AUDIO_COMPAT_TRANSCODER_PROPERTY)
                ?.trim()
                ?.takeIf { it.isNotBlank() }

            val candidates = linkedSetOf<String>()
            if (configured != null) {
                candidates += configured
            }
            candidates += "ffmpeg"
            candidates += "avconv"

            candidates.forEach { candidate ->
                val available = runCatching {
                    val process = ProcessBuilder(candidate, "-version")
                        .redirectErrorStream(true)
                        .start()
                    process.inputStream.bufferedReader(Charsets.UTF_8).use { it.readLine() }
                    val finished = process.waitFor(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    if (!finished) {
                        process.destroyForcibly()
                        false
                    } else {
                        process.exitValue() == 0
                    }
                }.getOrDefault(false)

                if (available) {
                    return candidate
                }
            }

            return null
        }

        private fun sha1Hex(bytes: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-1").digest(bytes)
            return digest.joinToString(separator = "") { b -> "%02x".format(b) }
        }

        private fun probeJOrbisCompatibility(sourceBytes: ByteArray): Boolean {
            return runCatching {
                val tempFile = Files.createTempFile("jorbis_probe_", ".ogg")
                try {
                    Files.write(tempFile, sourceBytes)
                    val executor = Executors.newSingleThreadExecutor { runnable ->
                        Thread(runnable, "tacz-audio-jorbis-probe").apply { isDaemon = true }
                    }
                    try {
                        val probeTask = executor.submit<Boolean> {
                            val codec = paulscode.sound.codecs.CodecJOrbis()
                            try {
                                val initialized = codec.initialize(tempFile.toUri().toURL())
                                if (!initialized) {
                                    false
                                } else {
                                    codec.read() != null || codec.audioFormat != null || codec.endOfStream()
                                }
                            } finally {
                                codec.cleanup()
                            }
                        }

                        try {
                            probeTask.get(JORBIS_PROBE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
                        } catch (_: TimeoutException) {
                            probeTask.cancel(true)
                            false
                        }
                    } finally {
                        executor.shutdownNow()
                    }
                } finally {
                    runCatching { Files.deleteIfExists(tempFile) }
                }
            }.getOrDefault(false)
        }

        private const val PROBE_TIMEOUT_SECONDS: Long = 5
        private const val TRANSCODE_TIMEOUT_SECONDS: Long = 20
        private const val JORBIS_PROBE_TIMEOUT_MILLIS: Long = 400
    }

    internal fun isAudioCompatTranscodingAvailableForTests(): Boolean = AudioCompatTranscoder.isAvailable()
    internal fun isAudioCompatNonOggKeyGenerationEnabledForTests(): Boolean =
        AUDIO_COMPAT_ENABLED && AUDIO_COMPAT_GENERATE_NON_OGG_KEYS

    private val AUDIO_COMPAT_ENABLED: Boolean =
        java.lang.Boolean.parseBoolean(System.getProperty("tacz.audio.compat.enabled", "true"))
    private val AUDIO_COMPAT_GENERATE_NON_OGG_KEYS: Boolean =
        java.lang.Boolean.parseBoolean(System.getProperty("tacz.audio.compat.generateNonOggKeys", "true"))
    private const val AUDIO_COMPAT_TRANSCODER_PROPERTY: String = "tacz.audio.compat.transcoder"
}
