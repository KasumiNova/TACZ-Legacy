package com.tacz.legacy.api.resource

import com.google.gson.JsonIOException
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import com.google.gson.stream.JsonReader
import com.tacz.legacy.TACZLegacy
import net.minecraft.util.ResourceLocation
import org.apache.logging.log4j.Marker
import org.apache.logging.log4j.MarkerManager
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.io.StringReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.regex.Pattern
import java.util.zip.ZipFile

/**
 * 旧版枪包路径读取器，仅保留给兼容转换链路使用。
 */
@Deprecated("Only retained for legacy pack compatibility")
public abstract class JsonResourceLoader<T>(
    private val dataClass: Class<T>,
    markerName: String,
    private val domain: String,
) {
    private val marker: Marker = MarkerManager.getMarker(markerName)
    private val pattern: Pattern = Pattern.compile("^([a-zA-Z0-9_.-]+)/${Pattern.quote(domain)}/([\\w/.-]+)\\.json$")

    public fun getDataClass(): Class<T> = dataClass

    public fun load(zipFile: ZipFile, zipPath: String): Boolean {
        val matcher = pattern.matcher(zipPath)
        if (!matcher.find()) {
            return false
        }
        val namespace = matcher.group(1)
        val path = matcher.group(2)
        val entry = zipFile.getEntry(zipPath)
        if (entry == null) {
            TACZLegacy.logger.warn(marker, "{} file doesn't exist in {}", zipPath, zipFile.name)
            return false
        }
        return try {
            zipFile.getInputStream(entry).use { stream ->
                val json = stream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
                parseLenient(json)
                resolveJson(ResourceLocation(namespace, path), json)
                true
            }
        } catch (exception: IOException) {
            TACZLegacy.logger.warn(marker, "Failed to read file: {}, entry: {}", zipFile.name, zipPath, exception)
            false
        } catch (exception: JsonSyntaxException) {
            TACZLegacy.logger.warn(marker, "Failed to parse file: {}, entry: {}", zipFile.name, zipPath, exception)
            false
        } catch (exception: JsonIOException) {
            TACZLegacy.logger.warn(marker, "Failed to parse file: {}, entry: {}", zipFile.name, zipPath, exception)
            false
        }
    }

    public fun load(root: File): Unit {
        val filePath = root.toPath().resolve(domain)
        if (!Files.isDirectory(filePath)) {
            return
        }
        try {
            Files.walk(filePath).use { stream ->
                stream
                    .filter(Files::isRegularFile)
                    .filter { it.fileName.toString().endsWith(".json") }
                    .forEach { file ->
                        val relative = filePath.relativize(file).toString().replace(File.separatorChar, '/')
                        val resourcePath = relative.removeSuffix(".json")
                        runCatching {
                            Files.newInputStream(file).use { input ->
                                val json = InputStreamReader(input, StandardCharsets.UTF_8).use { it.readText() }
                                parseLenient(json)
                                resolveJson(ResourceLocation(root.name, resourcePath), json)
                            }
                        }.onFailure { throwable ->
                            TACZLegacy.logger.warn(marker, "Failed to read legacy json file: {}", file, throwable)
                        }
                    }
            }
        } catch (exception: IOException) {
            TACZLegacy.logger.warn(marker, "Failed to walk file tree: {}", filePath, exception)
        }
    }

    private fun parseLenient(json: String): Unit {
        val reader = JsonReader(StringReader(json))
        reader.isLenient = true
        JsonParser().parse(reader)
    }

    public abstract fun resolveJson(id: ResourceLocation, json: String): Unit
}
