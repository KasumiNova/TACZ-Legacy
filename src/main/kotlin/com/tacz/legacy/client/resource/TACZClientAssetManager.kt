package com.tacz.legacy.client.resource

import com.google.gson.GsonBuilder
import com.google.gson.stream.JsonReader
import com.tacz.legacy.TACZLegacy
import com.tacz.legacy.client.model.bedrock.BedrockModel
import com.tacz.legacy.client.resource.pojo.display.gun.GunDisplay
import com.tacz.legacy.client.resource.pojo.model.BedrockModelPOJO
import com.tacz.legacy.client.resource.pojo.model.BedrockVersion
import com.tacz.legacy.client.resource.pojo.model.CubesItem
import com.tacz.legacy.client.resource.serialize.Vector3fSerializer
import com.tacz.legacy.common.resource.TACZDisplayDefinition
import com.tacz.legacy.common.resource.TACZLoadedPack
import com.tacz.legacy.common.resource.TACZRuntimeSnapshot
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.client.renderer.texture.TextureManager
import net.minecraft.util.ResourceLocation
import org.apache.logging.log4j.MarkerManager
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

    /** Parsed gun display data keyed by display ResourceLocation. */
    private val gunDisplays = LinkedHashMap<ResourceLocation, GunDisplay>()

    /** Loaded bedrock models keyed by model ResourceLocation. */
    private val models = LinkedHashMap<ResourceLocation, BedrockModel>()

    /** Texture ResourceLocations registered with the TextureManager, keyed by pack texture path. */
    private val textures = LinkedHashMap<ResourceLocation, ResourceLocation>()

    fun getGunDisplay(id: ResourceLocation): GunDisplay? = gunDisplays[id]
    fun getModel(id: ResourceLocation): BedrockModel? = models[id]
    fun getTextureLocation(id: ResourceLocation): ResourceLocation? = textures[id]

    /**
     * Reload all client assets from the [snapshot].
     * Called after common-side pack loading completes, on the client thread.
     */
    fun reload(snapshot: TACZRuntimeSnapshot) {
        clear()
        parseDisplayDefinitions(snapshot.gunDisplays)

        // Collect all model and texture ResourceLocations referenced by parsed displays
        val neededModels = LinkedHashSet<ResourceLocation>()
        val neededTextures = LinkedHashSet<ResourceLocation>()
        for (display in gunDisplays.values) {
            display.modelLocation?.let(neededModels::add)
            display.modelTexture?.let(neededTextures::add)
            display.gunLod?.modelLocation?.let(neededModels::add)
            display.gunLod?.modelTexture?.let(neededTextures::add)
        }

        // Load assets from each pack
        for (pack in snapshot.packs.values) {
            loadAssetsFromPack(pack.sourceFile, neededModels, neededTextures)
        }

        TACZLegacy.logger.info(MARKER, "Client assets reloaded: {} displays, {} models, {} textures",
            gunDisplays.size, models.size, textures.size)
    }

    fun clear() {
        // Unregister dynamic textures
        val textureManager = Minecraft.getMinecraft().textureManager
        for (texLoc in textures.values) {
            textureManager.deleteTexture(texLoc)
        }
        gunDisplays.clear()
        models.clear()
        textures.clear()
    }

    private fun parseDisplayDefinitions(rawDisplays: Map<ResourceLocation, TACZDisplayDefinition>) {
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

    private fun loadAssetsFromPack(
        packFile: File,
        neededModels: Set<ResourceLocation>,
        neededTextures: Set<ResourceLocation>,
    ) {
        if (!packFile.exists()) return

        if (packFile.isDirectory) {
            loadFromDirectory(packFile, neededModels, neededTextures)
        } else if (packFile.name.endsWith(".zip", ignoreCase = true)) {
            loadFromZip(packFile, neededModels, neededTextures)
        }
    }

    // ------ ZIP loading ------

    private fun loadFromZip(
        file: File,
        neededModels: Set<ResourceLocation>,
        neededTextures: Set<ResourceLocation>,
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
        models[id] = BedrockModel(pojo, version)
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
}
