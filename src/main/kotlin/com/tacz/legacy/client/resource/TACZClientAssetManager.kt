package com.tacz.legacy.client.resource

import com.google.gson.GsonBuilder
import com.google.gson.stream.JsonReader
import com.tacz.legacy.TACZLegacy
import com.tacz.legacy.api.vmlib.LuaAnimationConstant
import com.tacz.legacy.api.vmlib.LuaGunAnimationConstant
import com.tacz.legacy.api.vmlib.LuaLibrary
import com.tacz.legacy.client.audio.TACZAudioReference
import com.tacz.legacy.client.audio.TACZAudioRuntime
import com.tacz.legacy.client.model.BedrockAmmoModel
import com.tacz.legacy.client.sound.GunPackAssetLocator
import com.tacz.legacy.client.sound.GunPackSoundResourcePack
import com.tacz.legacy.client.resource.index.ClientAttachmentIndex
import com.tacz.legacy.client.resource.gltf.GltfAnimationData
import com.tacz.legacy.client.resource.gltf.GltfAnimationParser
import com.tacz.legacy.client.resource.pojo.animation.bedrock.AnimationKeyframes
import com.tacz.legacy.client.resource.pojo.animation.bedrock.BedrockAnimationFile
import com.tacz.legacy.client.resource.pojo.animation.bedrock.SoundEffectKeyframes
import com.tacz.legacy.client.resource.pojo.display.ammo.AmmoDisplay
import com.tacz.legacy.client.resource.pojo.display.attachment.AttachmentDisplay
import com.tacz.legacy.client.resource.pojo.display.block.BlockDisplay
import com.tacz.legacy.client.resource.pojo.display.gun.DefaultAnimationType
import com.tacz.legacy.client.resource.pojo.display.gun.GunDisplay
import com.tacz.legacy.client.resource.pojo.model.BedrockModelPOJO
import com.tacz.legacy.client.resource.pojo.model.BedrockVersion
import com.tacz.legacy.client.resource.pojo.model.CubesItem
import com.tacz.legacy.client.resource.serialize.AnimationKeyframesSerializer
import com.tacz.legacy.client.resource.serialize.SoundEffectKeyframesSerializer
import com.tacz.legacy.client.resource.serialize.Vector3fSerializer
import com.tacz.legacy.common.resource.TACZDisplayDefinition
import com.tacz.legacy.common.resource.TACZGunPackPresentation
import com.tacz.legacy.common.resource.TACZGunPackRuntimeRegistry
import com.tacz.legacy.common.resource.TACZLoadedPack
import com.tacz.legacy.common.resource.TACZRuntimeSnapshot
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.client.renderer.texture.TextureManager
import net.minecraft.util.ResourceLocation
import org.apache.logging.log4j.MarkerManager
import org.luaj.vm2.*
import org.luaj.vm2.compiler.LuaC
import org.luaj.vm2.lib.Bit32Lib
import org.luaj.vm2.lib.PackageLib
import org.luaj.vm2.lib.TableLib
import org.luaj.vm2.lib.jse.*
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.io.InputStream
import java.io.StringReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.ArrayDeque
import java.util.zip.ZipFile
import javax.imageio.ImageIO

/**
 * Client-side asset manager for TACZ gun packs.
 *
 * Loads bedrock models (geometry JSON), textures (PNG), and parses
 * display definitions from [TACZRuntimeSnapshot] on the client.
 *
 * The manager re-opens pack files (via [TACZLoadedPack.sourceFile])
 * to load binary/model assets that the common-side runtime skips.
 */
internal object TACZClientAssetManager {
    private val MARKER = MarkerManager.getMarker("TACZClientAssets")
    private val DEFAULT_RIFLE_ANIMATION_ID = ResourceLocation(TACZLegacy.MOD_ID, "rifle_default")
    private val DEFAULT_PISTOL_ANIMATION_ID = ResourceLocation(TACZLegacy.MOD_ID, "pistol_default")

    private val MODEL_GSON = GsonBuilder()
        .registerTypeAdapter(CubesItem::class.java, CubesItem.Deserializer())
        .create()

    private val DISPLAY_GSON = GsonBuilder()
        .registerTypeAdapter(ResourceLocation::class.java,
            com.google.gson.JsonDeserializer<ResourceLocation> { json, _, _ -> ResourceLocation(json.asString) })
        .registerTypeAdapter(org.joml.Vector3f::class.java, Vector3fSerializer())
        .create()

    private val ANIMATION_GSON = GsonBuilder()
        .registerTypeAdapter(AnimationKeyframes::class.java, AnimationKeyframesSerializer())
        .registerTypeAdapter(SoundEffectKeyframes::class.java, SoundEffectKeyframesSerializer())
        .create()

    /** Parsed gun display data keyed by display ResourceLocation. */
    private val gunDisplays = LinkedHashMap<ResourceLocation, GunDisplay>()

    /** Parsed ammo display data keyed by display ResourceLocation. */
    private val ammoDisplays = LinkedHashMap<ResourceLocation, AmmoDisplay>()

    /** Parsed attachment display data keyed by display ResourceLocation. */
    private val attachmentDisplays = LinkedHashMap<ResourceLocation, AttachmentDisplay>()

    /** Parsed block display data keyed by display ResourceLocation. */
    private val blockDisplays = LinkedHashMap<ResourceLocation, BlockDisplay>()

    /** Loaded bedrock model data keyed by model ResourceLocation. */
    data class ModelData(val pojo: BedrockModelPOJO, val version: BedrockVersion)
    private val models = LinkedHashMap<ResourceLocation, ModelData>()

    data class ShellRenderAsset(val model: BedrockAmmoModel, val textureLocation: ResourceLocation)

    /** Texture ResourceLocations registered with the TextureManager, keyed by pack texture path. */
    private val textures = LinkedHashMap<ResourceLocation, ResourceLocation>()

    /** GUI-safe 16×16 attachment slot thumbnails keyed by original slot texture path. */
    private val guiSlotTextures = LinkedHashMap<ResourceLocation, ResourceLocation>()

    /** Parsed bedrock animation files keyed by animation ResourceLocation. */
    private val animations = LinkedHashMap<ResourceLocation, BedrockAnimationFile>()

    /** Parsed glTF animation files keyed by animation ResourceLocation. */
    private val gltfAnimations = LinkedHashMap<ResourceLocation, GltfAnimationData>()

    /** Resolved gun pack sound resource ids referenced by displays / animation keyframes. */
    private val soundResources = LinkedHashSet<ResourceLocation>()

    /** Detailed sound reference origins for manifest / preflight. */
    private val audioReferences = LinkedHashMap<ResourceLocation, LinkedHashSet<TACZAudioReference>>()

    /** Source pack files backing the currently loaded runtime snapshot. */
    private val packSources = ArrayList<File>()

    /** Compiled Lua scripts keyed by script ResourceLocation. */
    private val scripts = LinkedHashMap<ResourceLocation, LuaTable>()

    /** Pending script sources collected during pack loading, resolved after all packs are scanned. */
    private val pendingScriptSources = LinkedHashMap<ResourceLocation, String>()

    /** Lua VM libraries injected into script globals. */
    private val luaLibraries: List<LuaLibrary> = listOf(LuaAnimationConstant(), LuaGunAnimationConstant())

    /** Cached GunDisplayInstance objects, keyed by display ResourceLocation. */
    private val gunDisplayInstances = LinkedHashMap<ResourceLocation, GunDisplayInstance>()

    /** Cached ClientAttachmentIndex objects, keyed by attachment ResourceLocation. */
    private val attachmentIndices = LinkedHashMap<ResourceLocation, ClientAttachmentIndex>()

    /** Cached shell render assets keyed by ammo ResourceLocation. */
    private val ammoShellRenderAssets = LinkedHashMap<ResourceLocation, ShellRenderAsset?>()

    fun getGunDisplay(id: ResourceLocation): GunDisplay? = gunDisplays[id]
    fun getAmmoDisplay(id: ResourceLocation): AmmoDisplay? = ammoDisplays[id]
    fun getAttachmentDisplay(id: ResourceLocation): AttachmentDisplay? = attachmentDisplays[id]
    fun getBlockDisplay(id: ResourceLocation): BlockDisplay? = blockDisplays[id]
    fun getModel(id: ResourceLocation): ModelData? = models[id]
    fun getTextureLocation(id: ResourceLocation): ResourceLocation? = textures[id]
    fun getGuiSlotTextureLocation(id: ResourceLocation): ResourceLocation? = guiSlotTextures[id]
    fun getAnimationFile(id: ResourceLocation): BedrockAnimationFile? = animations[id]
    fun getGltfAnimation(id: ResourceLocation): GltfAnimationData? = gltfAnimations[id]
    fun getScript(id: ResourceLocation): LuaTable? = scripts[id]
    fun getGunDisplayInstance(displayId: ResourceLocation): GunDisplayInstance? = gunDisplayInstances[displayId]
    fun getAttachmentIndex(attachmentId: ResourceLocation): ClientAttachmentIndex? = attachmentIndices[attachmentId]
    fun getAmmoShellRenderAsset(ammoId: ResourceLocation): ShellRenderAsset? {
        if (ammoShellRenderAssets.containsKey(ammoId)) {
            return ammoShellRenderAssets[ammoId]
        }
        val built = buildAmmoShellRenderAsset(ammoId)
        ammoShellRenderAssets[ammoId] = built
        return built
    }
    fun hasPackAsset(id: ResourceLocation): Boolean = GunPackAssetLocator.resourceExists(packSources, id)
    fun openPackAsset(id: ResourceLocation): InputStream? = try {
        GunPackAssetLocator.openResource(packSources, id)
    } catch (_: Exception) {
        null
    }

    internal data class ClientAssetLoadPlan(
        val models: Set<ResourceLocation>,
        val textures: Set<ResourceLocation>,
        val animations: Set<ResourceLocation>,
        val scripts: Set<ResourceLocation>,
    )

    internal fun buildAssetLoadPlan(
        gunDisplays: Collection<GunDisplay>,
        ammoDisplays: Collection<AmmoDisplay>,
        attachmentDisplays: Collection<AttachmentDisplay>,
        blockDisplays: Collection<BlockDisplay>,
    ): ClientAssetLoadPlan {
        val neededModels = LinkedHashSet<ResourceLocation>()
        val neededTextures = LinkedHashSet<ResourceLocation>()
        val neededAnimations = LinkedHashSet<ResourceLocation>()
        val neededScripts = LinkedHashSet<ResourceLocation>()

        for (display in gunDisplays) {
            display.modelLocation?.let(neededModels::add)
            display.modelTexture?.let(neededTextures::add)
            display.gunLod?.modelLocation?.let(neededModels::add)
            display.gunLod?.modelTexture?.let(neededTextures::add)
            display.animationLocation?.let(neededAnimations::add)
            display.defaultAnimation?.let(neededAnimations::add)
            when (display.defaultAnimationType) {
                DefaultAnimationType.RIFLE -> neededAnimations.add(DEFAULT_RIFLE_ANIMATION_ID)
                DefaultAnimationType.PISTOL -> neededAnimations.add(DEFAULT_PISTOL_ANIMATION_ID)
                null -> Unit
            }
            display.slotTextureLocation?.let(neededTextures::add)
            display.hudTextureLocation?.let(neededTextures::add)
            display.hudEmptyTextureLocation?.let(neededTextures::add)
            display.muzzleFlash?.texture?.let(neededTextures::add)
            val scriptLoc = display.stateMachineLocation ?: ResourceLocation("tacz", "default_state_machine")
            neededScripts.add(scriptLoc)
        }

        for (display in ammoDisplays) {
            display.modelLocation?.let(neededModels::add)
            display.modelTexture?.let(neededTextures::add)
            display.slotTextureLocation?.let(neededTextures::add)
            display.ammoEntity?.modelLocation?.let(neededModels::add)
            display.ammoEntity?.modelTexture?.let(neededTextures::add)
            display.shellDisplay?.modelLocation?.let(neededModels::add)
            display.shellDisplay?.modelTexture?.let(neededTextures::add)
        }

        for (display in attachmentDisplays) {
            display.model?.let(neededModels::add)
            display.texture?.let(neededTextures::add)
            display.slotTextureLocation?.let(neededTextures::add)
            display.attachmentLod?.modelLocation?.let(neededModels::add)
            display.attachmentLod?.modelTexture?.let(neededTextures::add)
        }

        for (display in blockDisplays) {
            display.modelLocation?.let(neededModels::add)
            display.modelTexture?.let(neededTextures::add)
        }

        return ClientAssetLoadPlan(
            models = neededModels,
            textures = neededTextures,
            animations = neededAnimations,
            scripts = neededScripts,
        )
    }

    /**
     * Reload all client assets from the [snapshot].
     * Called after common-side pack loading completes, on the client thread.
     */
    fun reload(snapshot: TACZRuntimeSnapshot) {
        clear()
        packSources.addAll(snapshot.packs.values.map { it.sourceFile })
        parseGunDisplayDefinitions(snapshot.gunDisplays)
        parseAmmoDisplayDefinitions(snapshot.ammoDisplays)
        parseAttachmentDisplayDefinitions(snapshot.attachmentDisplays)
        parseBlockDisplayDefinitions(snapshot.blockDisplays)
        val loadPlan = buildAssetLoadPlan(
            gunDisplays = gunDisplays.values,
            ammoDisplays = ammoDisplays.values,
            attachmentDisplays = attachmentDisplays.values,
            blockDisplays = blockDisplays.values,
        )

        // Load assets from each pack
        for (pack in snapshot.packs.values) {
            loadAssetsFromPack(pack.sourceFile, loadPlan.models, loadPlan.textures, loadPlan.animations, loadPlan.scripts)
        }
        loadAssetsFromClasspath(loadPlan.models, loadPlan.textures, loadPlan.animations, loadPlan.scripts)

        // Resolve scripts with dependency-aware retry (scripts use require() to reference each other)
        resolveAllScripts()

        val audioReferenceSnapshot = LinkedHashMap<ResourceLocation, Set<TACZAudioReference>>()
        for ((soundId, references) in audioReferences) {
            audioReferenceSnapshot[soundId] = LinkedHashSet(references)
        }
        TACZAudioRuntime.reload(ArrayList(packSources), audioReferenceSnapshot)

        // Only keep the vanilla resource-pack bridge alive when explicitly running legacy fallback.
        GunPackSoundResourcePack.synchronize(soundResources, TACZAudioRuntime.shouldUseLegacyMinecraftBridge())

        buildAttachmentIndices(snapshot)

        val totalDisplays = gunDisplays.size + ammoDisplays.size + attachmentDisplays.size + blockDisplays.size
        TACZLegacy.logger.info(MARKER,
            "Client assets reloaded: {} displays (gun={}, ammo={}, attach={}, block={}), {} models, {} textures, {} animations (bedrock={}, gltf={}), {} scripts",
            totalDisplays, gunDisplays.size, ammoDisplays.size, attachmentDisplays.size, blockDisplays.size,
            models.size, textures.size, animations.size + gltfAnimations.size, animations.size, gltfAnimations.size, scripts.size)

        // Build GunDisplayInstance for each gun display
        buildGunDisplayInstances()
    }

    private fun buildAttachmentIndices(snapshot: TACZRuntimeSnapshot) {
        attachmentIndices.clear()
        for ((attachmentId, attachment) in snapshot.attachments) {
            val displayId = attachment.index.display ?: continue
            val display = attachmentDisplays[displayId] ?: continue
            runCatching {
                ClientAttachmentIndex.create(display, this)
            }.onSuccess { index ->
                attachmentIndices[attachmentId] = index
            }.onFailure { error ->
                TACZLegacy.logger.warn(MARKER, "Failed to build ClientAttachmentIndex for {}: {}", attachmentId, error.message)
            }
        }
        TACZLegacy.logger.info(MARKER, "Built {} client attachment indices", attachmentIndices.size)
    }

    private fun buildGunDisplayInstances() {
        for ((displayId, display) in gunDisplays) {
            try {
                val instance = GunDisplayInstance.create(display, this)
                if (instance != null) {
                    gunDisplayInstances[displayId] = instance
                }
            } catch (e: Exception) {
                TACZLegacy.logger.warn(MARKER, "Failed to build GunDisplayInstance for {}: {}", displayId, e.message)
            }
        }
        TACZLegacy.logger.info(MARKER, "Built {} gun display instances", gunDisplayInstances.size)
    }

    fun clear() {
        // Unregister dynamic textures
        val textureManager = Minecraft.getMinecraft().textureManager
        for (texLoc in textures.values) {
            textureManager.deleteTexture(texLoc)
        }
        for (texLoc in guiSlotTextures.values) {
            textureManager.deleteTexture(texLoc)
        }
        gunDisplays.clear()
        ammoDisplays.clear()
        attachmentDisplays.clear()
        blockDisplays.clear()
        models.clear()
        textures.clear()
        guiSlotTextures.clear()
        animations.clear()
        gltfAnimations.clear()
        soundResources.clear()
        audioReferences.clear()
        packSources.clear()
        scripts.clear()
        pendingScriptSources.clear()
        attachmentIndices.clear()
        gunDisplayInstances.clear()
        ammoShellRenderAssets.clear()
        TACZAudioRuntime.clear()
    }

    private fun buildAmmoShellRenderAsset(ammoId: ResourceLocation): ShellRenderAsset? {
        val snapshot = TACZGunPackRuntimeRegistry.getSnapshot()
        val displayId = TACZGunPackPresentation.resolveAmmoDisplayId(snapshot, ammoId) ?: return null
        val display = ammoDisplays[displayId] ?: return null
        val shellDisplay = display.shellDisplay ?: return null
        val modelLocation = shellDisplay.modelLocation ?: return null
        val textureLocation = shellDisplay.modelTexture?.let(::getTextureLocation) ?: return null
        val modelData = models[modelLocation] ?: return null
        return ShellRenderAsset(
            model = BedrockAmmoModel(modelData.pojo, modelData.version),
            textureLocation = textureLocation,
        )
    }

    private fun registerSoundReference(
        soundId: ResourceLocation?,
        sourceType: String,
        ownerId: ResourceLocation? = null,
        key: String? = null,
    ) {
        if (soundId == null) {
            return
        }
        soundResources.add(soundId)
        audioReferences.getOrPut(soundId) { LinkedHashSet() }
            .add(TACZAudioReference(sourceType = sourceType, ownerId = ownerId, key = key))
    }

    private fun parseGunDisplayDefinitions(rawDisplays: Map<ResourceLocation, TACZDisplayDefinition>) {
        for ((id, def) in rawDisplays) {
            try {
                val display = DISPLAY_GSON.fromJson(def.raw, GunDisplay::class.java)
                display.init()
                gunDisplays[id] = display
                display.sounds?.forEach { (soundKey, soundId) ->
                    registerSoundReference(soundId, sourceType = "gun-display", ownerId = id, key = soundKey)
                    registerSoundReference(soundId, sourceType = "server-sound", ownerId = id, key = soundKey)
                }
            } catch (e: Exception) {
                TACZLegacy.logger.warn(MARKER, "Failed to parse gun display: {}", id, e)
            }
        }
    }

    private fun parseAmmoDisplayDefinitions(rawDisplays: Map<ResourceLocation, TACZDisplayDefinition>) {
        for ((id, def) in rawDisplays) {
            try {
                val display = DISPLAY_GSON.fromJson(def.raw, AmmoDisplay::class.java)
                display.init()
                ammoDisplays[id] = display
            } catch (e: Exception) {
                TACZLegacy.logger.warn(MARKER, "Failed to parse ammo display: {}", id, e)
            }
        }
    }

    private fun parseAttachmentDisplayDefinitions(rawDisplays: Map<ResourceLocation, TACZDisplayDefinition>) {
        for ((id, def) in rawDisplays) {
            try {
                val display = DISPLAY_GSON.fromJson(def.raw, AttachmentDisplay::class.java)
                display.init()
                attachmentDisplays[id] = display
                display.sounds?.forEach { (soundKey, soundId) ->
                    registerSoundReference(soundId, sourceType = "attachment-display", ownerId = id, key = soundKey)
                }
            } catch (e: Exception) {
                TACZLegacy.logger.warn(MARKER, "Failed to parse attachment display: {}", id, e)
            }
        }
    }

    private fun parseBlockDisplayDefinitions(rawDisplays: Map<ResourceLocation, TACZDisplayDefinition>) {
        for ((id, def) in rawDisplays) {
            try {
                val display = DISPLAY_GSON.fromJson(def.raw, BlockDisplay::class.java)
                display.init()
                blockDisplays[id] = display
            } catch (e: Exception) {
                TACZLegacy.logger.warn(MARKER, "Failed to parse block display: {}", id, e)
            }
        }
    }

    private fun loadAssetsFromPack(
        packFile: File,
        neededModels: Set<ResourceLocation>,
        neededTextures: Set<ResourceLocation>,
        neededAnimations: Set<ResourceLocation>,
        neededScripts: Set<ResourceLocation>,
    ) {
        if (!packFile.exists()) return

        if (packFile.isDirectory) {
            loadFromDirectory(packFile, neededModels, neededTextures, neededAnimations, neededScripts)
        } else if (packFile.name.endsWith(".zip", ignoreCase = true)) {
            loadFromZip(packFile, neededModels, neededTextures, neededAnimations, neededScripts)
        }
    }

    private fun loadAssetsFromClasspath(
        neededModels: Set<ResourceLocation>,
        neededTextures: Set<ResourceLocation>,
        neededAnimations: Set<ResourceLocation>,
        neededScripts: Set<ResourceLocation>,
    ) {
        for (modelLoc in neededModels) {
            if (models.containsKey(modelLoc)) continue
            val resourcePath = "assets/${modelLoc.namespace}/geo_models/${modelLoc.path}.json"
            openClasspathResource(resourcePath)?.bufferedReader(StandardCharsets.UTF_8)?.use { reader ->
                loadModelFromJson(modelLoc, reader.readText())
            }
        }
        for (texLoc in neededTextures) {
            if (textures.containsKey(texLoc)) continue
            val resourcePath = "assets/${texLoc.namespace}/${texLoc.path}"
            openClasspathResource(resourcePath)?.use { stream ->
                loadTextureFromStream(texLoc, stream)
            }
        }
        for (animLoc in neededAnimations) {
            if (animations.containsKey(animLoc) || gltfAnimations.containsKey(animLoc)) continue
            val bedrockPath = "assets/${animLoc.namespace}/animations/${animLoc.path}.animation.json"
            val gltfPath = "assets/${animLoc.namespace}/animations/${animLoc.path}.gltf"
            openClasspathResource(bedrockPath)?.bufferedReader(StandardCharsets.UTF_8)?.use { reader ->
                loadAnimationFromJson(animLoc, reader.readText())
            } ?: openClasspathResource(gltfPath)?.bufferedReader(StandardCharsets.UTF_8)?.use { reader ->
                loadGltfAnimationFromJson(animLoc, reader.readText()) { uri ->
                    openClasspathResource(resolveAnimationBufferPath(animLoc, uri))?.use { it.readBytes() }
                }
            }
        }
        for (scriptLoc in neededScripts) {
            if (pendingScriptSources.containsKey(scriptLoc) || scripts.containsKey(scriptLoc)) continue
            val resourcePath = "assets/${scriptLoc.namespace}/scripts/${scriptLoc.path}.lua"
            openClasspathResource(resourcePath)?.bufferedReader(StandardCharsets.UTF_8)?.use { reader ->
                pendingScriptSources[scriptLoc] = reader.readText()
            }
        }
    }

    private fun openClasspathResource(path: String): InputStream? =
        TACZClientAssetManager::class.java.classLoader.getResourceAsStream(path)

    // ------ ZIP loading ------

    private fun loadFromZip(
        file: File,
        neededModels: Set<ResourceLocation>,
        neededTextures: Set<ResourceLocation>,
        neededAnimations: Set<ResourceLocation>,
        neededScripts: Set<ResourceLocation>,
    ) {
        try {
            ZipFile(file).use { zip ->
                for (modelLoc in neededModels) {
                    if (models.containsKey(modelLoc)) continue
                    val entryPath = "assets/${modelLoc.namespace}/geo_models/${modelLoc.path}.json"
                    val entry = zip.getEntry(entryPath) ?: continue
                    try {
                        val json = zip.getInputStream(entry).bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
                        loadModelFromJson(modelLoc, json)
                    } catch (e: Exception) {
                        TACZLegacy.logger.warn(MARKER, "Failed to load model {} from {}", modelLoc, file.name, e)
                    }
                }
                for (texLoc in neededTextures) {
                    if (textures.containsKey(texLoc)) continue
                    // texLoc is already expanded: tacz:textures/gun/uv/ak47.png
                    val entryPath = "assets/${texLoc.namespace}/${texLoc.path}"
                    val entry = zip.getEntry(entryPath) ?: continue
                    try {
                        zip.getInputStream(entry).use { stream ->
                            loadTextureFromStream(texLoc, stream)
                        }
                    } catch (e: Exception) {
                        TACZLegacy.logger.warn(MARKER, "Failed to load texture {} from {}", texLoc, file.name, e)
                    }
                }
                for (animLoc in neededAnimations) {
                    if (animations.containsKey(animLoc) || gltfAnimations.containsKey(animLoc)) continue
                    val bedrockPath = "assets/${animLoc.namespace}/animations/${animLoc.path}.animation.json"
                    val gltfPath = "assets/${animLoc.namespace}/animations/${animLoc.path}.gltf"
                    val bedrockEntry = zip.getEntry(bedrockPath)
                    val gltfEntry = zip.getEntry(gltfPath)
                    try {
                        when {
                            bedrockEntry != null -> {
                                val json = zip.getInputStream(bedrockEntry).bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
                                loadAnimationFromJson(animLoc, json)
                            }
                            gltfEntry != null -> {
                                val json = zip.getInputStream(gltfEntry).bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
                                loadGltfAnimationFromJson(animLoc, json) { uri ->
                                    val resolvedEntry = zip.getEntry(resolveAnimationBufferPath(animLoc, uri)) ?: return@loadGltfAnimationFromJson null
                                    zip.getInputStream(resolvedEntry).use { it.readBytes() }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        TACZLegacy.logger.warn(MARKER, "Failed to load animation {} from {}", animLoc, file.name, e)
                    }
                }
                for (scriptLoc in neededScripts) {
                    if (pendingScriptSources.containsKey(scriptLoc) || scripts.containsKey(scriptLoc)) continue
                    val entryPath = "assets/${scriptLoc.namespace}/scripts/${scriptLoc.path}.lua"
                    val entry = zip.getEntry(entryPath) ?: continue
                    try {
                        val source = zip.getInputStream(entry).bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
                        pendingScriptSources[scriptLoc] = source
                    } catch (e: Exception) {
                        TACZLegacy.logger.warn(MARKER, "Failed to read script {} from {}", scriptLoc, file.name, e)
                    }
                }
            }
        } catch (e: Exception) {
            TACZLegacy.logger.warn(MARKER, "Failed to open pack zip: {}", file.name, e)
        }
    }

    // ------ Directory loading ------

    private fun loadFromDirectory(
        root: File,
        neededModels: Set<ResourceLocation>,
        neededTextures: Set<ResourceLocation>,
        neededAnimations: Set<ResourceLocation>,
        neededScripts: Set<ResourceLocation>,
    ) {
        for (modelLoc in neededModels) {
            if (models.containsKey(modelLoc)) continue
            val filePath = root.toPath().resolve("assets/${modelLoc.namespace}/geo_models/${modelLoc.path}.json")
            if (!Files.isRegularFile(filePath)) continue
            try {
                val json = Files.newBufferedReader(filePath, StandardCharsets.UTF_8).use { it.readText() }
                loadModelFromJson(modelLoc, json)
            } catch (e: Exception) {
                TACZLegacy.logger.warn(MARKER, "Failed to load model {} from dir {}", modelLoc, root.name, e)
            }
        }
        for (texLoc in neededTextures) {
            if (textures.containsKey(texLoc)) continue
            val filePath = root.toPath().resolve("assets/${texLoc.namespace}/${texLoc.path}")
            if (!Files.isRegularFile(filePath)) continue
            try {
                Files.newInputStream(filePath).use { stream ->
                    loadTextureFromStream(texLoc, stream)
                }
            } catch (e: Exception) {
                TACZLegacy.logger.warn(MARKER, "Failed to load texture {} from dir {}", texLoc, root.name, e)
            }
        }
        for (animLoc in neededAnimations) {
            if (animations.containsKey(animLoc) || gltfAnimations.containsKey(animLoc)) continue
            val bedrockPath = root.toPath().resolve("assets/${animLoc.namespace}/animations/${animLoc.path}.animation.json")
            val gltfPath = root.toPath().resolve("assets/${animLoc.namespace}/animations/${animLoc.path}.gltf")
            try {
                when {
                    Files.isRegularFile(bedrockPath) -> {
                        val json = Files.newBufferedReader(bedrockPath, StandardCharsets.UTF_8).use { it.readText() }
                        loadAnimationFromJson(animLoc, json)
                    }
                    Files.isRegularFile(gltfPath) -> {
                        val json = Files.newBufferedReader(gltfPath, StandardCharsets.UTF_8).use { it.readText() }
                        loadGltfAnimationFromJson(animLoc, json) { uri ->
                            val resolved = root.toPath().resolve(resolveAnimationBufferPath(animLoc, uri))
                            if (Files.isRegularFile(resolved)) Files.readAllBytes(resolved) else null
                        }
                    }
                }
            } catch (e: Exception) {
                TACZLegacy.logger.warn(MARKER, "Failed to load animation {} from dir {}", animLoc, root.name, e)
            }
        }
        for (scriptLoc in neededScripts) {
            if (pendingScriptSources.containsKey(scriptLoc) || scripts.containsKey(scriptLoc)) continue
            val filePath = root.toPath().resolve("assets/${scriptLoc.namespace}/scripts/${scriptLoc.path}.lua")
            if (!Files.isRegularFile(filePath)) continue
            try {
                val source = Files.newBufferedReader(filePath, StandardCharsets.UTF_8).use { it.readText() }
                pendingScriptSources[scriptLoc] = source
            } catch (e: Exception) {
                TACZLegacy.logger.warn(MARKER, "Failed to read script source {} from dir {}", scriptLoc, root.name, e)
            }
        }
    }

    // ------ Model parsing ------

    private fun loadModelFromJson(id: ResourceLocation, json: String) {
        val reader = JsonReader(StringReader(json))
        reader.isLenient = true
        val pojo = MODEL_GSON.fromJson<BedrockModelPOJO>(reader, BedrockModelPOJO::class.java)
        val version = BedrockVersion.fromPojo(pojo) ?: run {
            TACZLegacy.logger.warn(MARKER, "Unknown bedrock format version in model {}: {}", id, pojo.formatVersion)
            return
        }
        models[id] = ModelData(pojo, version)
    }

    // ------ Texture loading ------

    private fun loadTextureFromStream(id: ResourceLocation, stream: InputStream) {
        val image: BufferedImage = ImageIO.read(stream) ?: run {
            TACZLegacy.logger.warn(MARKER, "Failed to decode image: {}", id)
            return
        }
        val dynamicTexture = DynamicTexture(image)
        val textureManager: TextureManager = Minecraft.getMinecraft().textureManager
        // Register with a unique location under tacz_dynamic namespace to avoid collision
        val registeredLoc = textureManager.getDynamicTextureLocation("tacz_pack", dynamicTexture)
        textures[id] = registeredLoc
        if (isAttachmentSlotTexture(id)) {
            val guiTexture = DynamicTexture(buildGuiSlotThumbnail(image))
            guiSlotTextures[id] = textureManager.getDynamicTextureLocation("tacz_pack_gui_slot", guiTexture)
        }
    }

    private fun isAttachmentSlotTexture(id: ResourceLocation): Boolean = id.path.startsWith("attachment/slot/")

    private fun buildGuiSlotThumbnail(source: BufferedImage): BufferedImage {
        val bled = bleedTransparentPixelRgb(source)
        val thumbnail = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
        val graphics = thumbnail.createGraphics()
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED)
        graphics.drawImage(bled, 0, 0, 16, 16, null)
        graphics.dispose()
        return bleedTransparentPixelRgb(thumbnail)
    }

    private fun bleedTransparentPixelRgb(source: BufferedImage): BufferedImage {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        val visited = BooleanArray(width * height)
        val queue = ArrayDeque<Int>()

        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = y * width + x
                val argb = source.getRGB(x, y)
                pixels[index] = argb
                if (((argb ushr 24) and 0xFF) > 0) {
                    visited[index] = true
                    queue.addLast(index)
                }
            }
        }

        while (!queue.isEmpty()) {
            val index = queue.removeFirst()
            val x = index % width
            val y = index / width
            val rgb = pixels[index] and 0x00FFFFFF

            if (x > 0) {
                val left = index - 1
                if (!visited[left]) {
                    visited[left] = true
                    pixels[left] = rgb
                    queue.addLast(left)
                }
            }
            if (x + 1 < width) {
                val right = index + 1
                if (!visited[right]) {
                    visited[right] = true
                    pixels[right] = rgb
                    queue.addLast(right)
                }
            }
            if (y > 0) {
                val up = index - width
                if (!visited[up]) {
                    visited[up] = true
                    pixels[up] = rgb
                    queue.addLast(up)
                }
            }
            if (y + 1 < height) {
                val down = index + width
                if (!visited[down]) {
                    visited[down] = true
                    pixels[down] = rgb
                    queue.addLast(down)
                }
            }
        }

        val result = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        result.setRGB(0, 0, width, height, pixels, 0, width)
        return result
    }

    // ------ Animation parsing ------

    private fun loadAnimationFromJson(id: ResourceLocation, json: String) {
        val reader = JsonReader(StringReader(json))
        reader.isLenient = true
        val animFile = ANIMATION_GSON.fromJson<BedrockAnimationFile>(reader, BedrockAnimationFile::class.java)
        if (animFile?.animations == null || animFile.animations.isEmpty()) {
            TACZLegacy.logger.warn(MARKER, "Animation file has no animations: {}", id)
            return
        }
        animations[id] = animFile
        for ((animationName, animation) in animFile.animations) {
            val soundEffects = animation.soundEffects ?: continue
            for (entry in soundEffects.keyframes.double2ObjectEntrySet()) {
                registerSoundReference(
                    soundId = entry.value,
                    sourceType = "animation-keyframe",
                    ownerId = id,
                    key = "$animationName@${entry.doubleKey}",
                )
            }
        }
    }

    private fun loadGltfAnimationFromJson(
        id: ResourceLocation,
        json: String,
        resolver: (String) -> ByteArray?,
    ) {
        val gltfAnimation = GltfAnimationParser.parse(json) { uri -> resolver(uri) }
        if (gltfAnimation.animations.isEmpty()) {
            TACZLegacy.logger.warn(MARKER, "glTF animation file has no animations: {}", id)
            return
        }
        gltfAnimations[id] = gltfAnimation
    }

    private fun resolveAnimationBufferPath(id: ResourceLocation, uri: String): String {
        val normalized = uri.replace('\\', '/').removePrefix("./")
        if (normalized.contains(':')) {
            val resource = ResourceLocation(normalized)
            return "assets/${resource.namespace}/animations/${resource.path}"
        }
        val baseDir = id.path.substringBeforeLast('/', "")
        val resolved = if (baseDir.isEmpty()) normalized else "$baseDir/$normalized"
        return "assets/${id.namespace}/animations/$resolved"
    }

    // ------ Script loading ------

    private fun createSecureGlobals(): Globals {
        val globals = Globals()
        globals.load(JseBaseLib())
        globals.load(PackageLib())
        globals.load(Bit32Lib())
        globals.load(TableLib())
        globals.load(org.luaj.vm2.lib.StringLib())
        globals.load(JseMathLib())
        LuaC.install(globals)
        return globals
    }

    private fun loadScriptFromSource(id: ResourceLocation, source: String) {
        val globals = createSecureGlobals()
        for (lib in luaLibraries) {
            lib.install(globals)
        }
        // Install previously loaded scripts as require()-able modules.
        // Gun pack scripts use require("{namespace}_{path}") to reference each other.
        val preload = globals.get("package").get("preload")
        if (preload is LuaTable) {
            for ((scriptId, scriptTable) in scripts) {
                val requireName = "${scriptId.namespace}_${scriptId.path}"
                preload.set(requireName, object : org.luaj.vm2.lib.ZeroArgFunction() {
                    override fun call(): LuaValue = scriptTable
                })
            }
        }
        val chunk = globals.load(source, id.toString())
        val result = chunk.call()
        if (result is LuaTable) {
            // Also mirror constants onto the returned script table for compatibility
            // with scripts that access them through `self`/`this` instead of globals.
            for (lib in luaLibraries) {
                lib.install(result)
            }
            scripts[id] = result
        } else {
            TACZLegacy.logger.warn(MARKER, "Script {} did not return a table", id)
        }
    }

    /**
     * Resolve all pending script sources with a dependency-aware retry loop.
     * Scripts may require() other scripts; we load repeatedly until no progress is made.
     */
    private fun resolveAllScripts() {
        val remaining = LinkedHashMap(pendingScriptSources)
        var lastSize = -1
        while (remaining.isNotEmpty() && remaining.size != lastSize) {
            lastSize = remaining.size
            val iterator = remaining.entries.iterator()
            while (iterator.hasNext()) {
                val (id, source) = iterator.next()
                try {
                    loadScriptFromSource(id, source)
                    iterator.remove()
                } catch (_: Exception) {
                    // Likely a require() for a script not yet loaded — will retry
                }
            }
        }
        if (remaining.isNotEmpty()) {
            for (id in remaining.keys) {
                TACZLegacy.logger.warn(MARKER, "Script {} could not be resolved (unmet require dependency)", id)
            }
        }
        pendingScriptSources.clear()
    }
}
