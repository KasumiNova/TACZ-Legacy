package com.tacz.legacy.common.infrastructure.mc.gunpack

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.tacz.legacy.common.application.gunpack.DisplayVec3
import com.tacz.legacy.common.application.gunpack.GunDisplayDefinition
import com.tacz.legacy.common.application.gunpack.GunDisplayScanEntry
import com.tacz.legacy.common.application.gunpack.GunDisplayScanReport
import org.apache.logging.log4j.Logger
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile

public class GunDisplayPreInitScanner {

    public fun scanAndLog(configRoot: Path, logger: Logger): GunDisplayScanReport? {
        val taczRoots = resolveTaczPackRoots(configRoot)
        if (taczRoots.isEmpty()) {
            logger.info("[GunDisplay] scan skipped (no TACZ pack root found near {}).", configRoot)
            return null
        }

        val entries = mutableListOf<GunDisplayScanEntry>()
        taczRoots.forEach { root -> collectFromTaczPackRoot(root, entries, logger) }

        val report = GunDisplayScanReport(entries)
        logger.info(
            "[GunDisplay] scan finished: total={} success={} failed={}",
            report.total,
            report.successCount,
            report.failedCount
        )

        report.failedEntries()
            .take(LOG_DETAILS_LIMIT)
            .forEach { failed ->
                logger.warn(
                    "[GunDisplay] failed source={} reason={}",
                    failed.sourceId,
                    failed.message ?: "unknown"
                )
            }

        val remaining = report.failedCount - LOG_DETAILS_LIMIT
        if (remaining > 0) {
            logger.warn("[GunDisplay] failure log truncated: {} more entry(s).", remaining)
        }

        return report
    }

    private fun collectFromTaczPackRoot(packRoot: Path, sink: MutableList<GunDisplayScanEntry>, logger: Logger) {
        runCatching {
            Files.list(packRoot).use { children ->
                children.forEach { child ->
                    when {
                        Files.isDirectory(child) -> collectFromPackDirectory(child, sink, logger)
                        child.fileName.toString().endsWith(".zip") -> collectFromPackZip(child, sink, logger)
                    }
                }
            }
        }.onFailure {
            logger.warn("[GunDisplay] failed to list TACZ root {}.", packRoot, it)
        }
    }

    private fun collectFromPackDirectory(packDir: Path, sink: MutableList<GunDisplayScanEntry>, logger: Logger) {
        if (!Files.isRegularFile(packDir.resolve(PACK_META_FILE_NAME))) {
            return
        }

        val dataRoot = packDir.resolve("data")
        if (!Files.isDirectory(dataRoot)) {
            return
        }

        val references = mutableListOf<IndexDisplayRef>()
        Files.walk(dataRoot).use { stream ->
            stream.filter { path -> Files.isRegularFile(path) && path.fileName.toString().endsWith(".json") }
                .forEach { file ->
                    val relative = dataRoot.relativize(file).toString().replace(File.separatorChar, '/')
                    if (!PACK_INDEX_RELATIVE_REGEX.matches(relative)) {
                        return@forEach
                    }

                    val sourceId = "${packDir.fileName}/data/$relative"
                    val json = runCatching {
                        String(Files.readAllBytes(file), StandardCharsets.UTF_8)
                    }.getOrElse {
                        sink += GunDisplayScanEntry(sourceId = sourceId, definition = null, message = "read index json failed")
                        return@forEach
                    }

                    val fallbackGunId = file.fileName.toString().substringBeforeLast('.')
                    val ref = parseIndexDisplayRef(json, sourceId, fallbackGunId)
                    if (ref == null) {
                        sink += GunDisplayScanEntry(sourceId = sourceId, definition = null, message = "invalid index json")
                    } else {
                        references += ref
                    }
                }
        }

        references.forEach { ref ->
            val displayPath = resolveDisplayJsonPath(ref.displayResource)
            if (displayPath == null) {
                sink += GunDisplayScanEntry(ref.sourceId, null, "invalid display resource '${ref.displayResource}'")
                return@forEach
            }

            val displayFile = packDir.resolve(displayPath)
            val displaySourceId = "${packDir.fileName}/$displayPath"
            if (!Files.isRegularFile(displayFile)) {
                sink += GunDisplayScanEntry(displaySourceId, null, "display file not found")
                return@forEach
            }

            val displayJson = runCatching {
                String(Files.readAllBytes(displayFile), StandardCharsets.UTF_8)
            }.getOrElse {
                sink += GunDisplayScanEntry(displaySourceId, null, "read display json failed")
                return@forEach
            }

            val definition = parseDisplayDefinition(
                gunId = ref.gunId,
                displayResource = ref.displayResource,
                displayJson = displayJson,
                displaySourceId = displaySourceId,
                assetReader = { assetPath ->
                    val file = packDir.resolve(assetPath)
                    if (!Files.isRegularFile(file)) {
                        null
                    } else {
                        runCatching {
                            String(Files.readAllBytes(file), StandardCharsets.UTF_8)
                        }.getOrNull()
                    }
                }
            )

            sink += GunDisplayScanEntry(
                sourceId = displaySourceId,
                definition = definition,
                message = if (definition == null) "invalid display json" else null
            )
        }
    }

    private fun collectFromPackZip(packZip: Path, sink: MutableList<GunDisplayScanEntry>, logger: Logger) {
        runCatching {
            ZipFile(packZip.toFile()).use { zip ->
                if (!hasPackMetaEntry(zip)) {
                    return
                }

                val indexEntries = mutableListOf<String>()
                val iterator = zip.entries()
                while (iterator.hasMoreElements()) {
                    val entry = iterator.nextElement()
                    if (entry.isDirectory || !entry.name.endsWith(".json")) {
                        continue
                    }
                    if (PACK_INDEX_ENTRY_REGEX.matches(entry.name)) {
                        indexEntries += entry.name
                    }
                }

                indexEntries.sorted().forEach { indexPath ->
                    val indexSourceId = "${packZip.fileName}!/$indexPath"
                    val indexJson = readZipEntry(zip, indexPath)
                    if (indexJson == null) {
                        sink += GunDisplayScanEntry(indexSourceId, null, "read index json failed")
                        return@forEach
                    }

                    val fallbackGunId = indexPath.substringAfterLast('/').substringBeforeLast('.')
                    val ref = parseIndexDisplayRef(indexJson, indexSourceId, fallbackGunId)
                    if (ref == null) {
                        sink += GunDisplayScanEntry(indexSourceId, null, "invalid index json")
                        return@forEach
                    }

                    val indexPrefix = indexPath.substringBefore("data/", missingDelimiterValue = "")

                    val displayPath = resolveDisplayJsonPath(ref.displayResource)
                    if (displayPath == null) {
                        sink += GunDisplayScanEntry(indexSourceId, null, "invalid display resource '${ref.displayResource}'")
                        return@forEach
                    }

                    val displayZipPath = "$indexPrefix$displayPath"
                    val displayJson = readZipEntry(zip, displayZipPath)
                    val displaySourceId = "${packZip.fileName}!/$displayZipPath"
                    if (displayJson == null) {
                        sink += GunDisplayScanEntry(displaySourceId, null, "display file not found")
                        return@forEach
                    }

                    val definition = parseDisplayDefinition(
                        gunId = ref.gunId,
                        displayResource = ref.displayResource,
                        displayJson = displayJson,
                        displaySourceId = displaySourceId,
                        assetReader = { assetPath ->
                            readZipEntry(zip, "$indexPrefix$assetPath")
                                ?: readZipEntry(zip, assetPath)
                        }
                    )

                    sink += GunDisplayScanEntry(
                        sourceId = displaySourceId,
                        definition = definition,
                        message = if (definition == null) "invalid display json" else null
                    )
                }
            }
        }.onFailure {
            logger.warn("[GunDisplay] failed to read TACZ pack zip {}.", packZip, it)
        }
    }

    private fun hasPackMetaEntry(zip: ZipFile): Boolean {
        val iterator = zip.entries()
        while (iterator.hasMoreElements()) {
            val entry = iterator.nextElement()
            if (entry.isDirectory) {
                continue
            }
            if (entry.name == PACK_META_FILE_NAME || entry.name.endsWith("/$PACK_META_FILE_NAME")) {
                return true
            }
        }
        return false
    }

    private fun parseIndexDisplayRef(
        indexJson: String,
        sourceId: String,
        fallbackGunId: String
    ): IndexDisplayRef? {
        val root = parseJsonObject(indexJson) ?: return null
        val display = root.readString("display")?.trim()?.ifBlank { null } ?: return null
        return IndexDisplayRef(
            gunId = normalizeGunId(fallbackGunId),
            displayResource = display,
            sourceId = sourceId
        )
    }

    private fun parseDisplayDefinition(
        gunId: String,
        displayResource: String,
        displayJson: String,
        displaySourceId: String,
        assetReader: (String) -> String?
    ): GunDisplayDefinition? {
        val root = parseJsonObject(displayJson) ?: return null
        val lod = root.readObject("lod")

        val ammoCountStyle = root.readString("ammo_count_style")
            ?.trim()
            ?.lowercase()
            ?.ifBlank { null }
        val damageStyle = root.readString("damage_style")
            ?.trim()
            ?.lowercase()
            ?.ifBlank { null }

        val transform = root.readObject("transform")
        val transformScale = transform?.readObject("scale")
        val transformScaleThirdPerson = transformScale?.readVec3("thirdperson")
        val transformScaleGround = transformScale?.readVec3("ground")
        val transformScaleFixed = transformScale?.readVec3("fixed")

        val muzzleFlash = root.readObject("muzzle_flash")
        val muzzleFlashTexturePath = toTextureAssetPath(muzzleFlash?.readString("texture"))
        val muzzleFlashScale = muzzleFlash?.readDouble("scale")?.toFloat()

        val modelPath = toGeoModelAssetPath(root.readString("model"))
        val modelTexturePath = toTextureAssetPath(root.readString("texture"))
        val lodModelPath = toGeoModelAssetPath(lod?.readString("model"))
        val lodTexturePath = toTextureAssetPath(lod?.readString("texture"))
        val slotTexturePath = toTextureAssetPath(root.readString("slot"))
        val animationPath = toAnimationAssetPath(root.readString("animation"))
        val defaultAnimationPath = toAnimationAssetPath(root.readString("default_animation"))
        val useDefaultAnimation = root.readString("use_default_animation")
            ?.trim()
            ?.lowercase()
            ?.ifBlank { null }
        val rawStateMachine = root.readString("state_machine")
            ?.trim()
            ?.ifBlank { null }
        val parsedStateMachinePath = toScriptAssetPath(rawStateMachine)
        val stateMachinePath = parsedStateMachinePath
            ?: if (rawStateMachine == null) DEFAULT_STATE_MACHINE_ASSET_PATH else null
        val stateMachineSource = when {
            parsedStateMachinePath != null -> STATE_MACHINE_SOURCE_DISPLAY
            rawStateMachine == null -> STATE_MACHINE_SOURCE_DEFAULT_FALLBACK
            else -> STATE_MACHINE_SOURCE_INVALID
        }
        val stateMachineScriptContent = stateMachinePath
            ?.let(assetReader)
            ?.trim()
            ?.ifBlank { null }
        val stateMachineParams = parseStateMachineParams(root.readObject("state_machine_param"))
        val playerAnimator3rdPath = toPlayerAnimatorAssetPath(root.readString("player_animator_3rd"))
        val thirdPersonAnimation = root.readString("third_person_animation")?.trim()?.ifBlank { null }
        val sounds = root.readObject("sounds")
        val shootSoundId = sounds?.readSoundResourceId("shoot")
        val shootThirdPersonSoundId = sounds?.readSoundResourceId("shoot_3p")
        val drawSoundId = sounds?.readSoundResourceId("draw")
        val putAwaySoundId = sounds?.readSoundResourceId("put_away")
        val dryFireSoundId = sounds?.readSoundResourceId("dry_fire")
        val inspectSoundId = sounds?.readSoundResourceId("inspect")
        val inspectEmptySoundId = sounds?.readSoundResourceId("inspect_empty")
        val reloadEmptySoundId = sounds?.readSoundResourceId("reload_empty")
        val reloadTacticalSoundId = sounds?.readSoundResourceId("reload_tactical")

        val hudTexturePath = toTextureAssetPath(root.readString("hud"))
        val hudEmptyTexturePath = toTextureAssetPath(root.readString("hud_empty"))
        val showCrosshair = root.readBoolean("show_crosshair") ?: true
        val ironZoom = root.readDouble("iron_zoom")?.toFloat()
        val zoomModelFov = root.readDouble("zoom_model_fov")?.toFloat()

        val modelStats = modelPath
            ?.let(assetReader)
            ?.let(::parseGeoModelStats)
        val animationStats = animationPath
            ?.let(assetReader)
            ?.let(::parseAnimationStats)

        val animationClipCount = animationStats?.clipCount

        val modelParseSucceeded = modelPath != null && modelStats != null
        val animationParseSucceeded = animationPath != null && animationStats != null
        val stateMachineResolved = !stateMachineScriptContent.isNullOrBlank()
        val playerAnimatorResolved = playerAnimator3rdPath
            ?.let(assetReader)
            ?.isNotBlank()
            ?: false

        return GunDisplayDefinition(
            sourceId = displaySourceId,
            gunId = normalizeGunId(gunId),
            displayResource = displayResource,
            modelPath = modelPath,
            modelTexturePath = modelTexturePath,
            lodModelPath = lodModelPath,
            lodTexturePath = lodTexturePath,
            slotTexturePath = slotTexturePath,
            animationPath = animationPath,
            defaultAnimationPath = defaultAnimationPath,
            useDefaultAnimation = useDefaultAnimation,
            stateMachinePath = stateMachinePath,
            stateMachineSource = stateMachineSource,
            stateMachineScriptContent = stateMachineScriptContent,
            stateMachineParams = stateMachineParams,
            playerAnimator3rdPath = playerAnimator3rdPath,
            thirdPersonAnimation = thirdPersonAnimation,
            modelParseSucceeded = modelParseSucceeded,
            modelBoneCount = modelStats?.boneCount,
            modelCubeCount = modelStats?.cubeCount,
            animationParseSucceeded = animationParseSucceeded,
            animationClipCount = animationClipCount,
            animationClipNames = animationStats?.clipNames,
            animationClipLengthsMillis = animationStats?.clipDurationMillisByName,
            animationIdleClipName = animationStats?.idleClipName,
            animationFireClipName = animationStats?.fireClipName,
            animationReloadClipName = animationStats?.reloadClipName,
            animationInspectClipName = animationStats?.inspectClipName,
            animationDryFireClipName = animationStats?.dryFireClipName,
            animationDrawClipName = animationStats?.drawClipName,
            animationPutAwayClipName = animationStats?.putAwayClipName,
            animationWalkClipName = animationStats?.walkClipName,
            animationRunClipName = animationStats?.runClipName,
            animationAimClipName = animationStats?.aimClipName,
            animationBoltClipName = animationStats?.boltClipName,
            stateMachineResolved = stateMachineResolved,
            playerAnimatorResolved = playerAnimatorResolved,
            hudTexturePath = hudTexturePath,
            hudEmptyTexturePath = hudEmptyTexturePath,
            showCrosshair = showCrosshair,
            ironZoom = ironZoom,
            zoomModelFov = zoomModelFov,
            shootSoundId = shootSoundId,
            shootThirdPersonSoundId = shootThirdPersonSoundId,
            drawSoundId = drawSoundId,
            putAwaySoundId = putAwaySoundId,
            dryFireSoundId = dryFireSoundId,
            inspectSoundId = inspectSoundId,
            inspectEmptySoundId = inspectEmptySoundId,
            reloadEmptySoundId = reloadEmptySoundId,
            reloadTacticalSoundId = reloadTacticalSoundId,
            modelGeometryCount = modelStats?.geometryCount,
            modelRootBoneCount = modelStats?.rootBoneCount,
            modelRootBoneNames = modelStats?.rootBoneNames,
            ammoCountStyle = ammoCountStyle,
            damageStyle = damageStyle,
            transformScaleThirdPerson = transformScaleThirdPerson,
            transformScaleGround = transformScaleGround,
            transformScaleFixed = transformScaleFixed,
            muzzleFlashTexturePath = muzzleFlashTexturePath,
            muzzleFlashScale = muzzleFlashScale
        )
    }

    private fun parseGeoModelStats(modelJson: String): GeoModelStats? {
        val root = parseJsonObject(modelJson) ?: return null
        val geometry = root.readArray("minecraft:geometry") ?: return null

        val geometryCount = geometry.size()
        var boneCount = 0
        var cubeCount = 0
        var rootBoneCount = 0
        val rootBoneNames = mutableListOf<String>()

        geometry.forEach { element ->
            if (!element.isJsonObject) {
                return@forEach
            }

            val geoObject = element.asJsonObject
            val bones = geoObject.readArray("bones") ?: return@forEach
            boneCount += bones.size()

            bones.forEach { bone ->
                if (!bone.isJsonObject) {
                    return@forEach
                }

                val boneObj = bone.asJsonObject
                val parent = boneObj.readString("parent")?.trim()
                if (parent.isNullOrEmpty()) {
                    rootBoneCount += 1
                    boneObj.readString("name")
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() }
                        ?.let(rootBoneNames::add)
                }

                val cubes = boneObj.readArray("cubes") ?: return@forEach
                cubeCount += cubes.size()
            }
        }

        return GeoModelStats(
            geometryCount = geometryCount,
            boneCount = boneCount,
            cubeCount = cubeCount,
            rootBoneCount = rootBoneCount,
            rootBoneNames = rootBoneNames.distinct().take(MAX_ROOT_BONE_NAMES)
        )
    }

    private fun parseAnimationStats(animationJson: String): AnimationStats? {
        val root = parseJsonObject(animationJson) ?: return null
        val animations = root.readObject("animations") ?: return null

        val clipNames = animations.entrySet()
            .map { it.key.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()

        val clipDurationMillisByName = linkedMapOf<String, Long>()
        animations.entrySet().forEach { entry ->
            val clipName = entry.key.trim()
            if (clipName.isEmpty() || !entry.value.isJsonObject) {
                return@forEach
            }

            val animationLengthSeconds = entry.value.asJsonObject
                .readDouble("animation_length")
                ?.takeIf { it > 0.0 }
                ?: return@forEach
            val durationMillis = (animationLengthSeconds * 1_000.0).toLong().coerceAtLeast(1L)
            clipDurationMillisByName[clipName] = durationMillis
        }

        return AnimationStats(
            clipCount = clipNames.size,
            clipNames = clipNames,
            clipDurationMillisByName = clipDurationMillisByName.toMap(),
            idleClipName = selectClipName(clipNames, preferredKeywords = listOf("idle")),
            fireClipName = selectClipName(
                clipNames,
                preferredKeywords = listOf("shoot", "fire", "shot", "recoil"),
                excludedKeywords = setOf("dry", "empty")
            ),
            reloadClipName = selectClipName(
                clipNames,
                preferredKeywords = listOf("reload_tactical", "reload_empty", "reload")
            ),
            inspectClipName = selectClipName(clipNames, preferredKeywords = listOf("inspect")),
            dryFireClipName = selectClipName(
                clipNames,
                preferredKeywords = listOf("dry_fire", "dry", "empty_click", "no_ammo")
            ),
            drawClipName = selectClipName(
                clipNames,
                preferredKeywords = listOf("draw", "equip", "deploy", "pull_out")
            ),
            putAwayClipName = selectClipName(
                clipNames,
                preferredKeywords = listOf("put_away", "putaway", "holster", "withdraw")
            ),
            walkClipName = selectClipName(
                clipNames,
                preferredKeywords = listOf("walk", "move")
            ),
            runClipName = selectClipName(
                clipNames,
                preferredKeywords = listOf("run", "sprint")
            ),
            aimClipName = selectClipName(
                clipNames,
                preferredKeywords = listOf("aim", "ads", "sight", "aiming"),
                excludedKeywords = setOf("fire", "shoot", "reload")
            ),
            boltClipName = selectClipName(
                clipNames,
                preferredKeywords = listOf("bolt", "blot", "pull_bolt", "charge")
            )
        )
    }

    private fun selectClipName(
        clipNames: List<String>,
        preferredKeywords: List<String>,
        excludedKeywords: Set<String> = emptySet()
    ): String? {
        val normalizedExcluded = excludedKeywords.map(::normalizeClipToken).toSet()
        val normalized = clipNames.map { name ->
            name to normalizeClipToken(name)
        }

        preferredKeywords.forEach { keywordRaw ->
            val keyword = normalizeClipToken(keywordRaw)

            normalized.firstOrNull { (_, token) ->
                (token == keyword || token.endsWith("_$keyword")) && normalizedExcluded.none { token.contains(it) }
            }?.first?.let { return it }

            normalized.firstOrNull { (_, token) ->
                token.contains(keyword) && normalizedExcluded.none { token.contains(it) }
            }?.first?.let { return it }
        }

        return null
    }

    private fun normalizeClipToken(raw: String): String =
        raw.trim()
            .lowercase()
            .map { ch -> if (ch.isLetterOrDigit()) ch else '_' }
            .joinToString(separator = "")
            .replace("__+".toRegex(), "_")
            .trim('_')

    private fun toGeoModelAssetPath(rawResource: String?): String? {
        val resource = parseResourceId(rawResource) ?: return null
        var path = resource.path
        if (!path.startsWith("geo_models/")) {
            path = "geo_models/$path"
        }
        if (!path.endsWith(".json")) {
            path = "$path.json"
        }
        return "assets/${resource.namespace}/$path"
    }

    private fun toTextureAssetPath(rawResource: String?): String? {
        val resource = parseResourceId(rawResource) ?: return null
        var path = resource.path
        if (!path.startsWith("textures/")) {
            path = "textures/$path"
        }

        val hasImageExtension = path.endsWith(".png") || path.endsWith(".jpg") || path.endsWith(".jpeg")
        if (!hasImageExtension) {
            path = "$path.png"
        }

        return "assets/${resource.namespace}/$path"
    }

    private fun toAnimationAssetPath(rawResource: String?): String? {
        val resource = parseResourceId(rawResource) ?: return null
        var path = resource.path
        if (!path.startsWith("animations/")) {
            path = "animations/$path"
        }
        if (!path.endsWith(".animation.json")) {
            path = if (path.endsWith(".json")) {
                path.removeSuffix(".json") + ".animation.json"
            } else {
                "$path.animation.json"
            }
        }
        return "assets/${resource.namespace}/$path"
    }

    private fun toScriptAssetPath(rawResource: String?): String? {
        val resource = parseResourceId(rawResource) ?: return null
        var path = resource.path
        if (!path.startsWith("scripts/")) {
            path = "scripts/$path"
        }
        if (!path.endsWith(".lua")) {
            path = "$path.lua"
        }
        return "assets/${resource.namespace}/$path"
    }

    private fun toPlayerAnimatorAssetPath(rawResource: String?): String? {
        val resource = parseResourceId(rawResource) ?: return null
        var path = resource.path
        if (!path.startsWith("player_animator/")) {
            path = "player_animator/$path"
        }

        path = when {
            path.endsWith(".json") -> path
            path.endsWith(".player_animation") -> "$path.json"
            else -> "$path.player_animation.json"
        }

        return "assets/${resource.namespace}/$path"
    }

    private fun resolveDisplayJsonPath(displayResource: String): String? {
        val resource = parseResourceId(displayResource) ?: return null
        var path = resource.path.removePrefix("display/guns/")
        if (path.isBlank()) {
            return null
        }
        if (!path.endsWith(".json")) {
            path = "$path.json"
        }
        return "assets/${resource.namespace}/display/guns/$path"
    }

    private fun parseStateMachineParams(raw: JsonObject?): Map<String, Float> {
        if (raw == null || raw.entrySet().isEmpty()) {
            return emptyMap()
        }

        val out = linkedMapOf<String, Float>()
        raw.entrySet().forEach { entry ->
            val key = entry.key.trim().lowercase()
            if (key.isEmpty()) {
                return@forEach
            }

            val value = entry.value
            if (!value.isJsonPrimitive || !value.asJsonPrimitive.isNumber) {
                return@forEach
            }

            val numeric = value.asFloat
            if (!numeric.isFinite()) {
                return@forEach
            }
            out[key] = numeric
        }

        return out.toMap()
    }

    private fun parseResourceId(raw: String?): ResourceId? {
        val normalized = raw?.trim()?.ifBlank { null } ?: return null
        val delimiter = normalized.indexOf(':')
        if (delimiter <= 0 || delimiter >= normalized.length - 1) {
            return null
        }

        val namespace = normalized.substring(0, delimiter).trim()
        val path = normalized.substring(delimiter + 1).trim().trimStart('/')
        if (namespace.isBlank() || path.isBlank()) {
            return null
        }
        return ResourceId(namespace, path)
    }

    private fun JsonObject.readSoundResourceId(name: String): String? {
        val raw = readString(name) ?: return null
        val resource = parseResourceId(raw) ?: return null
        return "${resource.namespace}:${resource.path}"
    }

    private fun normalizeGunId(raw: String): String {
        val lowered = raw.trim().lowercase()
        if (lowered.isBlank()) {
            return "unknown_gun"
        }

        return buildString(lowered.length) {
            lowered.forEach { ch ->
                append(if (ch.isLetterOrDigit()) ch else '_')
            }
        }.replace("__+".toRegex(), "_")
            .trim('_')
            .ifBlank { "unknown_gun" }
    }

    private fun readZipEntry(zip: ZipFile, entryName: String): String? {
        val entry = zip.getEntry(entryName) ?: return null
        zip.getInputStream(entry).use { stream ->
            return String(stream.readBytes(), StandardCharsets.UTF_8)
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

    private fun resolveTaczPackRoots(configRoot: Path): List<Path> {
        val normalizedConfigRoot = configRoot.toAbsolutePath().normalize()
        val candidates = linkedSetOf<Path>()
        candidates.add(normalizedConfigRoot.resolveSibling("tacz"))
        normalizedConfigRoot.parent?.let { parent ->
            candidates.add(parent.resolve("tacz"))
        }
        return candidates.filter { candidate -> Files.isDirectory(candidate) }
    }

    private fun JsonObject.readString(name: String): String? {
        val element = get(name) ?: return null
        if (!element.isJsonPrimitive || !element.asJsonPrimitive.isString) {
            return null
        }
        return element.asString
    }

    private fun JsonObject.readBoolean(name: String): Boolean? {
        val element = get(name) ?: return null
        if (!element.isJsonPrimitive || !element.asJsonPrimitive.isBoolean) {
            return null
        }
        return element.asBoolean
    }

    private fun JsonObject.readDouble(name: String): Double? {
        val element = get(name) ?: return null
        if (!element.isJsonPrimitive || !element.asJsonPrimitive.isNumber) {
            return null
        }
        return element.asDouble
    }

    private fun JsonObject.readObject(name: String): JsonObject? {
        val element = get(name) ?: return null
        if (!element.isJsonObject) {
            return null
        }
        return element.asJsonObject
    }

    private fun JsonObject.readArray(name: String): com.google.gson.JsonArray? {
        val element = get(name) ?: return null
        if (!element.isJsonArray) {
            return null
        }
        return element.asJsonArray
    }

    private fun JsonObject.readVec3(name: String): DisplayVec3? {
        val array = readArray(name) ?: return null
        if (array.size() < 3) {
            return null
        }

        fun readFloatAt(index: Int): Float? {
            val element = array.get(index) ?: return null
            if (!element.isJsonPrimitive || !element.asJsonPrimitive.isNumber) {
                return null
            }
            val value = element.asFloat
            if (!value.isFinite()) {
                return null
            }
            return value
        }

        val x = readFloatAt(0) ?: return null
        val y = readFloatAt(1) ?: return null
        val z = readFloatAt(2) ?: return null
        return DisplayVec3(x, y, z)
    }

    private data class ResourceId(
        val namespace: String,
        val path: String
    )

    private data class IndexDisplayRef(
        val gunId: String,
        val displayResource: String,
        val sourceId: String
    )

    private data class GeoModelStats(
        val geometryCount: Int,
        val boneCount: Int,
        val cubeCount: Int,
        val rootBoneCount: Int,
        val rootBoneNames: List<String>
    )

    private data class AnimationStats(
        val clipCount: Int,
        val clipNames: List<String>,
        val clipDurationMillisByName: Map<String, Long>,
        val idleClipName: String?,
        val fireClipName: String?,
        val reloadClipName: String?,
        val inspectClipName: String?,
        val dryFireClipName: String?,
        val drawClipName: String?,
        val putAwayClipName: String?,
        val walkClipName: String?,
        val runClipName: String?,
        val aimClipName: String?,
        val boltClipName: String?
    )

    private companion object {
        private val PACK_INDEX_RELATIVE_REGEX: Regex = Regex("^[^/]+/index/guns/.+\\.json$")
        private val PACK_INDEX_ENTRY_REGEX: Regex = Regex("^(?:.*/)?data/[^/]+/index/guns/.+\\.json$")

        private const val PACK_META_FILE_NAME: String = "gunpack.meta.json"
        private const val LOG_DETAILS_LIMIT: Int = 20
        private const val MAX_ROOT_BONE_NAMES: Int = 8
        private const val DEFAULT_STATE_MACHINE_ASSET_PATH: String = "assets/tacz/scripts/default_state_machine.lua"
        private const val STATE_MACHINE_SOURCE_DISPLAY: String = "display"
        private const val STATE_MACHINE_SOURCE_DEFAULT_FALLBACK: String = "fallback_default"
        private const val STATE_MACHINE_SOURCE_INVALID: String = "invalid"
    }

}
