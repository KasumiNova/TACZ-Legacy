package com.tacz.legacy.common.resource

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import com.tacz.legacy.TACZLegacy
import com.tacz.legacy.common.resource.TACZJson.parseObject
import net.minecraftforge.fml.common.Loader
import net.minecraftforge.fml.common.versioning.VersionParser
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

internal object TACZPackVersionChecker {
    private val packInfoPattern: Pattern = Pattern.compile("^\\w+/pack\\.json$", Pattern.CASE_INSENSITIVE)
    private val cache: MutableMap<Path, Boolean> = ConcurrentHashMap()

    internal fun match(dir: File): Boolean = cache.computeIfAbsent(dir.toPath()) { checkDirVersion(dir) }

    internal fun noneMatch(zipFile: ZipFile, zipFilePath: Path): Boolean = !cache.computeIfAbsent(zipFilePath) { checkZipVersion(zipFile) }

    internal fun clearCache(): Unit {
        cache.clear()
    }

    private fun checkDirVersion(root: File): Boolean {
        if (!root.isDirectory) {
            return false
        }
        val packInfoFile = root.toPath().resolve("pack.json")
        if (!packInfoFile.toFile().isFile) {
            return true
        }
        return runCatching {
            val info = TACZJson.fromJson(packInfoFile.toFile().readText(StandardCharsets.UTF_8), TACZDependencyInfo::class.java)
            dependenciesMatch(info.dependencies)
        }.getOrElse {
            TACZLegacy.logger.warn("[GunPackCompat] Failed to read legacy pack.json from {}", packInfoFile, it)
            true
        }
    }

    private fun checkZipVersion(zipFile: ZipFile): Boolean {
        val entries = Collections.list(zipFile.entries()).map { it.name }
        entries.forEach { path ->
            if (!packInfoPattern.matcher(path).matches()) {
                return@forEach
            }
            val entry = zipFile.getEntry(path) ?: return@forEach
            val matches = runCatching {
                zipFile.getInputStream(entry).bufferedReader(StandardCharsets.UTF_8).use {
                    val info = TACZJson.fromJson(it.readText(), TACZDependencyInfo::class.java)
                    dependenciesMatch(info.dependencies)
                }
            }.getOrElse {
                TACZLegacy.logger.warn("[GunPackCompat] Failed to read legacy pack.json from {} in {}", path, zipFile.name, it)
                true
            }
            if (!matches) {
                return false
            }
        }
        return true
    }

    private fun dependenciesMatch(dependencies: Map<String, String>): Boolean {
        if (dependencies.isEmpty()) {
            return true
        }
        return dependencies.entries.all { (modId, versionRange) ->
            val modContainer = runCatching { Loader.instance().indexedModList[modId] }.getOrNull() ?: return@all false
            val processedVersion = modContainer.processedVersion
            runCatching {
                val expected = VersionParser.parseVersionReference("$modId@$versionRange")
                VersionParser.satisfies(expected, processedVersion)
            }.getOrDefault(false)
        }
    }

    private data class TACZDependencyInfo(
        @SerializedName("dependencies")
        val dependencies: Map<String, String> = emptyMap(),
    )
}

internal object TACZPackConvertor {
    internal val LEGACY_PACK_FOLDER: Path = Paths.get("config", TACZLegacy.MOD_ID, "custom")
    private val packInfoPattern: Pattern = Pattern.compile("^(\\w+)/pack\\.json$", Pattern.CASE_INSENSITIVE)

    internal fun fromZipFile(file: File): LegacyPack? {
        ZipFile(file).use { zipFile ->
            Collections.list(zipFile.entries()).map { it.name }.forEach { path ->
                val matcher = packInfoPattern.matcher(path)
                if (!matcher.find()) {
                    return@forEach
                }
                val namespace = matcher.group(1)
                val entry = zipFile.getEntry(path) ?: return@forEach
                return runCatching {
                    zipFile.getInputStream(entry).bufferedReader(StandardCharsets.UTF_8).use {
                        val info = TACZJson.fromJson(it.readText(), TACZPackInfo::class.java)
                        LegacyPack(file = file, namespace = namespace, info = info)
                    }
                }.getOrNull()
            }
        }
        return null
    }

    internal data class LegacyPack(
        val file: File,
        val namespace: String,
        val info: TACZPackInfo,
    ) {
        private val displayPattern: Pattern = Pattern.compile("^(\\w+)/(\\w+)/display/([\\w/.-]+)$", Pattern.CASE_INSENSITIVE)
        private val indexPattern: Pattern = Pattern.compile("^(\\w+)/(\\w+)/index/([\\w/.-]+)$", Pattern.CASE_INSENSITIVE)
        private val dataPattern: Pattern = Pattern.compile("^(\\w+)/(\\w+)/data/([\\w/.-]+)$", Pattern.CASE_INSENSITIVE)
        private val modelsPattern: Pattern = Pattern.compile("^(\\w+)/models/([\\w/.-]+)$", Pattern.CASE_INSENSITIVE)
        private val langPattern: Pattern = Pattern.compile("^(\\w+)/lang/([\\w/.-]+)$", Pattern.CASE_INSENSITIVE)
        private val animationPattern: Pattern = Pattern.compile("^(\\w+)/animations/([\\w/.-]+)$", Pattern.CASE_INSENSITIVE)
        private val texturePattern: Pattern = Pattern.compile("^(\\w+)/textures/([\\w/.-]+)$", Pattern.CASE_INSENSITIVE)
        private val soundPattern: Pattern = Pattern.compile("^(\\w+)/sounds/([\\w/.-]+)$", Pattern.CASE_INSENSITIVE)
        private val playerAnimatorPattern: Pattern = Pattern.compile("^(\\w+)/player_animator/([\\w/.-]+)$", Pattern.CASE_INSENSITIVE)
        private val tagsPattern: Pattern = Pattern.compile("^(\\w+)/tags/([\\w/.-]+)$", Pattern.CASE_INSENSITIVE)
        private val recipePattern: Pattern = Pattern.compile("^(\\w+)/recipes/([\\w/.-]+)$", Pattern.CASE_INSENSITIVE)
        private val packInfoPattern: Pattern = Pattern.compile("^(\\w+)/pack\\.json$", Pattern.CASE_INSENSITIVE)

        fun convertTo(targetDirectory: File): File {
            targetDirectory.mkdirs()
            val newName = file.name.removeSuffix(".zip") + "_converted.zip"
            val targetFile = File(targetDirectory, newName)
            if (targetFile.exists()) {
                throw FileAlreadyExistsException(targetFile.absolutePath)
            }
            ZipFile(file).use { oldPack ->
                FileOutputStream(targetFile).use { output ->
                    ZipOutputStream(output).use { newZip ->
                        addMeta(newZip)
                        Collections.list(oldPack.entries()).forEach { entry ->
                            if (parseDisplay(newZip, entry, oldPack)) return@forEach
                            if (parseIndex(newZip, entry, oldPack)) return@forEach
                            if (parseData(newZip, entry, oldPack)) return@forEach
                            if (parseModels(newZip, entry, oldPack)) return@forEach
                            if (parseLang(newZip, entry, oldPack)) return@forEach
                            if (parseAnimation(newZip, entry, oldPack)) return@forEach
                            if (parseTexture(newZip, entry, oldPack)) return@forEach
                            if (parseSound(newZip, entry, oldPack)) return@forEach
                            if (parsePlayerAnimator(newZip, entry, oldPack)) return@forEach
                            if (parseTags(newZip, entry, oldPack)) return@forEach
                            if (parseRecipe(newZip, entry, oldPack)) return@forEach
                            if (parsePackInfo(newZip, entry, oldPack)) return@forEach
                        }
                    }
                }
            }
            return targetFile
        }

        private fun parsePackInfo(newZip: ZipOutputStream, entry: ZipEntry, oldPack: ZipFile): Boolean =
            remapEntry(newZip, entry, oldPack, packInfoPattern) { namespace, _, path -> "assets/$namespace/gunpack_info.json" }

        private fun parseRecipe(newZip: ZipOutputStream, entry: ZipEntry, oldPack: ZipFile): Boolean {
            val matcher = recipePattern.matcher(entry.name)
            if (!matcher.find()) {
                return false
            }
            val namespace = matcher.group(1)
            val path = matcher.group(2)
            val newPath = "data/$namespace/recipes/$path"
            oldPack.getInputStream(entry).bufferedReader(StandardCharsets.UTF_8).use { reader ->
                val objectNode = parseObject(reader.readText())
                objectNode.addProperty("type", "tacz:gun_smith_table_crafting")
                newZip.putNextEntry(ZipEntry(newPath))
                newZip.write(TACZJson.GSON.toJson(objectNode).toByteArray(StandardCharsets.UTF_8))
                newZip.closeEntry()
            }
            return true
        }

        private fun parseTags(newZip: ZipOutputStream, entry: ZipEntry, oldPack: ZipFile): Boolean =
            remapEntry(newZip, entry, oldPack, tagsPattern) { namespace, _, path -> "data/$namespace/tacz_tags/$path" }

        private fun parsePlayerAnimator(newZip: ZipOutputStream, entry: ZipEntry, oldPack: ZipFile): Boolean =
            remapEntry(newZip, entry, oldPack, playerAnimatorPattern) { namespace, _, path -> "assets/$namespace/player_animator/$path" }

        private fun parseSound(newZip: ZipOutputStream, entry: ZipEntry, oldPack: ZipFile): Boolean =
            remapEntry(newZip, entry, oldPack, soundPattern) { namespace, _, path -> "assets/$namespace/tacz_sounds/$path" }

        private fun parseTexture(newZip: ZipOutputStream, entry: ZipEntry, oldPack: ZipFile): Boolean =
            remapEntry(newZip, entry, oldPack, texturePattern) { namespace, _, path -> "assets/$namespace/textures/$path" }

        private fun parseAnimation(newZip: ZipOutputStream, entry: ZipEntry, oldPack: ZipFile): Boolean =
            remapEntry(newZip, entry, oldPack, animationPattern) { namespace, _, path -> "assets/$namespace/animations/$path" }

        private fun parseLang(newZip: ZipOutputStream, entry: ZipEntry, oldPack: ZipFile): Boolean =
            remapEntry(newZip, entry, oldPack, langPattern) { namespace, _, path -> "assets/$namespace/lang/$path" }

        private fun parseModels(newZip: ZipOutputStream, entry: ZipEntry, oldPack: ZipFile): Boolean =
            remapEntry(newZip, entry, oldPack, modelsPattern) { namespace, _, path -> "assets/$namespace/geo_models/$path" }

        private fun parseDisplay(newZip: ZipOutputStream, entry: ZipEntry, oldPack: ZipFile): Boolean =
            remapEntry(newZip, entry, oldPack, displayPattern) { namespace, type, path -> "assets/$namespace/display/$type/$path" }

        private fun parseIndex(newZip: ZipOutputStream, entry: ZipEntry, oldPack: ZipFile): Boolean =
            remapEntry(newZip, entry, oldPack, indexPattern) { namespace, type, path -> "data/$namespace/index/$type/$path" }

        private fun parseData(newZip: ZipOutputStream, entry: ZipEntry, oldPack: ZipFile): Boolean =
            remapEntry(newZip, entry, oldPack, dataPattern) { namespace, type, path -> "data/$namespace/data/$type/$path" }

        private fun remapEntry(
            newZip: ZipOutputStream,
            entry: ZipEntry,
            oldPack: ZipFile,
            pattern: Pattern,
            pathFactory: (namespace: String, type: String, path: String) -> String,
        ): Boolean {
            val matcher = pattern.matcher(entry.name)
            if (!matcher.find()) {
                return false
            }
            val namespace = matcher.group(1)
            val type = if (matcher.groupCount() >= 2) matcher.group(2) else ""
            val path = if (matcher.groupCount() >= 3) matcher.group(3) else type
            writeEntry(newZip, entry, oldPack, pathFactory(namespace, type, path))
            return true
        }

        private fun writeEntry(newZip: ZipOutputStream, entry: ZipEntry, oldPack: ZipFile, newPath: String): Unit {
            newZip.putNextEntry(ZipEntry(newPath))
            oldPack.getInputStream(entry).use { input ->
                input.copyTo(newZip)
            }
            newZip.closeEntry()
        }

        private fun addMeta(newZip: ZipOutputStream): Unit {
            newZip.putNextEntry(ZipEntry("gunpack.meta.json"))
            val meta = TACZPackMeta(name = namespace)
            newZip.write(TACZJson.GSON.toJson(meta).toByteArray(StandardCharsets.UTF_8))
            newZip.closeEntry()
        }
    }
}
