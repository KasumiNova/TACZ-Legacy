package com.tacz.legacy.client.resource

import com.google.gson.GsonBuilder
import com.google.gson.stream.JsonReader
import com.tacz.legacy.TACZLegacy
import com.tacz.legacy.client.model.bedrock.BedrockModel
import com.tacz.legacy.api.vmlib.LuaAnimationConstant
import com.tacz.legacy.api.vmlib.LuaGunAnimationConstant
import com.tacz.legacy.api.vmlib.LuaLibrary
import com.tacz.legacy.client.resource.pojo.animation.bedrock.AnimationKeyframes
import com.tacz.legacy.client.resource.pojo.animation.bedrock.BedrockAnimationFile
import com.tacz.legacy.client.resource.pojo.animation.bedrock.SoundEffectKeyframes
import com.tacz.legacy.client.resource.pojo.display.ammo.AmmoDisplay
import com.tacz.legacy.client.resource.pojo.display.attachment.AttachmentDisplay
import com.tacz.legacy.client.resource.pojo.display.block.BlockDisplay
import com.tacz.legacy.client.resource.pojo.display.gun.GunDisplay
import com.tacz.legacy.client.resource.pojo.model.BedrockModelPOJO
import com.tacz.legacy.client.resource.pojo.model.BedrockVersion
import com.tacz.legacy.client.resource.pojo.model.CubesItem
import com.tacz.legacy.client.resource.serialize.AnimationKeyframesSerializer
import com.tacz.legacy.client.resource.serialize.SoundEffectKeyframesSerializer
import com.tacz.legacy.client.resource.serialize.Vector3fSerializer
import com.tacz.legacy.common.resource.TACZDisplayDefinition
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
import java.awt.image.BufferedImage
import java.io.File
import java.io.InputStream
import java.io.StringReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
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

    /** Texture ResourceLocations registered with the TextureManager, keyed by pack texture path. */
    private val textures = LinkedHashMap<ResourceLocation, ResourceLocation>()

    /** Parsed bedrock animation files keyed by animation ResourceLocation. */
    private val animations = LinkedHashMap<ResourceLocation, BedrockAnimationFile>()

    /** Compiled Lua scripts keyed by script ResourceLocation. */
    private val scripts = LinkedHashMap<ResourceLocation, LuaTable>()

    /** Lua VM libraries injected into script globals. */
    private val luaLibraries: List<LuaLibrary> = listOf(LuaAnimationConstant(), LuaGunAnimationConstant())

    fun getGunDisplay(id: ResourceLocation): GunDisplay? = gunDisplays[id]
    fun getAmmoDisplay(id: ResourceLocation): AmmoDisplay? = ammoDisplays[id]
    fun getAttachmentDisplay(id: ResourceLocation): AttachmentDisplay? = attachmentDisplays[id]
    fun getBlockDisplay(id: ResourceLocation): BlockDisplay? = blockDisplays[id]
    fun getModel(id: ResourceLocation): ModelData? = models[id]
    fun getTextureLocation(id: ResourceLocation): ResourceLocation? = textures[id]
    fun getAnimationFile(id: ResourceLocation): BedrockAnimationFile? = animations[id]
    fun getScript(id: ResourceLocation): LuaTable? = scripts[id]

    /**
     * Reload all client assets from the [snapshot].
     * Called after common-side pack loading completes, on the client thread.
     */
    fun reload(snapshot: TACZRuntimeSnapshot) {
        clear()
        parseGunDisplayDefinitions(snapshot.gunDisplays)
        parseAmmoDisplayDefinitions(snapshot.ammoDisplays)
        parseAttachmentDisplayDefinitions(snapshot.attachmentDisplays)
        parseBlockDisplayDefinitions(snapshot.blockDisplays)

        // Collect all model, texture, animation, and script ResourceLocations referenced by parsed displays
        val neededModels = LinkedHashSet<ResourceLocation>()
        val neededTextures = LinkedHashSet<ResourceLocation>()
        val neededAnimations = LinkedHashSet<ResourceLocation>()
        val neededScripts = LinkedHashSet<ResourceLocation>()

        // Gun displays
        for (display in gunDisplays.values) {
            display.modelLocation?.let(neededModels::add)
            display.modelTexture?.let(neededTextures::add)
            display.gunLod?.modelLocation?.let(neededModels::add)
            display.gunLod?.modelTexture?.let(neededTextures::add)
            display.animationLocation?.let(neededAnimations::add)
            display.slotTextureLocation?.let(neededTextures::add)
            display.hudTextureLocation?.let(neededTextures::add)
            display.hudEmptyTextureLocation?.let(neededTextures::add)
            // Collect script locations; default to tacz:default_state_machine if not specified
            val scriptLoc = display.stateMachineLocation ?: ResourceLocation("tacz", "default_state_machine")
            neededScripts.add(scriptLoc)
        }

        // Ammo displays
        for (display in ammoDisplays.values) {
            display.modelLocation?.let(neededModels::add)
            display.modelTexture?.let(neededTextures::add)
            display.slotTextureLocation?.let(neededTextures::add)
        }

        // Attachment displays
        for (display in attachmentDisplays.values) {
            display.model?.let(neededModels::add)
            display.texture?.let(neededTextures::add)
            display.slotTextureLocation?.let(neededTextures::add)
            display.attachmentLod?.modelLocation?.let(neededModels::add)
            display.attachmentLod?.modelTexture?.let(neededTextures::add)
        }

        // Block displays
        for (display in blockDisplays.values) {
            display.modelLocation?.let(neededModels::add)
            display.modelTexture?.let(neededTextures::add)
        }

        // Load assets from each pack
        for (pack in snapshot.packs.values) {
            loadAssetsFromPack(pack.sourceFile, neededModels, neededTextures, neededAnimations, neededScripts)
        }

        val totalDisplays = gunDisplays.size + ammoDisplays.size + attachmentDisplays.size + blockDisplays.size
        TACZLegacy.logger.info(MARKER,
            "Client assets reloaded: {} displays (gun={}, ammo={}, attach={}, block={}), {} models, {} textures, {} animations, {} scripts",
            totalDisplays, gunDisplays.size, ammoDisplays.size, attachmentDisplays.size, blockDisplays.size,
            models.size, textures.size, animations.size, scripts.size)
    }

    fun clear() {
        // Unregister dynamic textures
        val textureManager = Minecraft.getMinecraft().textureManager
        for (texLoc in textures.values) {
            textureManager.deleteTexture(texLoc)
        }
        gunDisplays.clear()
        ammoDisplays.clear()
        attachmentDisplays.clear()
        blockDisplays.clear()
        models.clear()
        textures.clear()
        animations.clear()
        scripts.clear()
    }

    private fun parseGunDisplayDefinitions(rawDisplays: Map<ResourceLocation, TACZDisplayDefinition>) {
        for ((id, def) in rawDisplays) {
            try {
                val display = DISPLAY_GSON.fromJson(def.raw, GunDisplay::class.java)
                display.init()
                gunDisplays[id] = display
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
                    if (animations.containsKey(animLoc)) continue
                    val entryPath = "assets/${animLoc.namespace}/animations/${animLoc.path}.animation.json"
                    val entry = zip.getEntry(entryPath) ?: continue
                    try {
                        val json = zip.getInputStream(entry).bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
                        loadAnimationFromJson(animLoc, json)
                    } catch (e: Exception) {
                        TACZLegacy.logger.warn(MARKER, "Failed to load animation {} from {}", animLoc, file.name, e)
                    }
                }
                for (scriptLoc in neededScripts) {
                    if (scripts.containsKey(scriptLoc)) continue
                    val entryPath = "assets/${scriptLoc.namespace}/scripts/${scriptLoc.path}.lua"
                    val entry = zip.getEntry(entryPath) ?: continue
                    try {
                        val source = zip.getInputStream(entry).bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
                        loadScriptFromSource(scriptLoc, source)
                    } catch (e: Exception) {
                        TACZLegacy.logger.warn(MARKER, "Failed to load script {} from {}", scriptLoc, file.name, e)
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
            if (animations.containsKey(animLoc)) continue
            val filePath = root.toPath().resolve("assets/${animLoc.namespace}/animations/${animLoc.path}.animation.json")
            if (!Files.isRegularFile(filePath)) continue
            try {
                val json = Files.newBufferedReader(filePath, StandardCharsets.UTF_8).use { it.readText() }
                loadAnimationFromJson(animLoc, json)
            } catch (e: Exception) {
                TACZLegacy.logger.warn(MARKER, "Failed to load animation {} from dir {}", animLoc, root.name, e)
            }
        }
        for (scriptLoc in neededScripts) {
            if (scripts.containsKey(scriptLoc)) continue
            val filePath = root.toPath().resolve("assets/${scriptLoc.namespace}/scripts/${scriptLoc.path}.lua")
            if (!Files.isRegularFile(filePath)) continue
            try {
                val source = Files.newBufferedReader(filePath, StandardCharsets.UTF_8).use { it.readText() }
                loadScriptFromSource(scriptLoc, source)
            } catch (e: Exception) {
                TACZLegacy.logger.warn(MARKER, "Failed to load script {} from dir {}", scriptLoc, root.name, e)
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
        val chunk = globals.load(source, id.toString())
        val result = chunk.call()
        if (result is LuaTable) {
            // Install our VM library constants into the chunk table
            for (lib in luaLibraries) {
                lib.install(result)
            }
            scripts[id] = result
        } else {
            TACZLegacy.logger.warn(MARKER, "Script {} did not return a table", id)
        }
    }
}
