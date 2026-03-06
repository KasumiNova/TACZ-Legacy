package com.tacz.legacy.common.resource

import com.tacz.legacy.TACZLegacy
import com.tacz.legacy.api.resource.ResourceManager
import com.tacz.legacy.common.config.LegacyConfigManager
import java.io.File
import java.net.URI
import java.nio.file.FileSystemAlreadyExistsException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.Comparator

internal data class ExportResult(
    val exported: Boolean,
    val skipped: Boolean,
    val targetDirectory: File,
    val backupDirectory: File?,
)

internal object DefaultGunPackExporter {
    internal const val DEFAULT_PACK_NAME: String = "tacz_default_gun"
    private const val RESOURCE_ROOT: String = "assets/tacz/custom/tacz_default_gun"

    internal fun exportIfNeeded(gameDirectory: File): ExportResult {
        val taczDirectory = File(gameDirectory, TACZLegacy.MOD_ID).apply { mkdirs() }
        val targetDirectory = File(taczDirectory, DEFAULT_PACK_NAME)
        val overwrite = LegacyConfigManager.shouldOverwriteDefaultPack()

        if (targetDirectory.exists() && !overwrite) {
            exportRegisteredResources(taczDirectory, overwrite)
            TACZGunPackRuntimeRegistry.reload(gameDirectory)
            return ExportResult(exported = false, skipped = true, targetDirectory = targetDirectory, backupDirectory = null)
        }

        val backupDirectory = if (targetDirectory.exists()) {
            backupExistingDirectory(targetDirectory)
        } else {
            null
        }

        targetDirectory.mkdirs()
        copyBundledPackTo(targetDirectory.toPath(), RESOURCE_ROOT, javaClass.classLoader)
        exportRegisteredResources(taczDirectory, overwrite)
        TACZGunPackRuntimeRegistry.reload(gameDirectory)
        return ExportResult(exported = true, skipped = false, targetDirectory = targetDirectory, backupDirectory = backupDirectory)
    }

    private fun backupExistingDirectory(targetDirectory: File): File {
        val backupRoot = File(requireNotNull(LegacyConfigManager.getModConfigDirectory()), "backup").apply { mkdirs() }
        val backupDirectory = File(backupRoot, "${targetDirectory.name}-${System.currentTimeMillis()}")
        copyDirectory(targetDirectory.toPath(), backupDirectory.toPath())
        deleteDirectory(targetDirectory.toPath())
        return backupDirectory
    }

    private fun exportRegisteredResources(rootDirectory: File, overwrite: Boolean): Unit {
        ResourceManager.EXTRA_ENTRIES.forEach { entry ->
            val targetDirectory = File(rootDirectory, entry.extraDirName)
            if (targetDirectory.exists() && !overwrite) {
                return@forEach
            }
            if (targetDirectory.exists()) {
                backupExistingDirectory(targetDirectory)
            }
            targetDirectory.mkdirs()
            runCatching {
                copyBundledPackTo(targetDirectory.toPath(), entry.srcPath, entry.modMainClass.classLoader)
            }.onSuccess {
                TACZLegacy.logger.info(
                    "Exported registered gun pack resource {} from {} to {}.",
                    entry.srcPath,
                    entry.modMainClass.name,
                    targetDirectory,
                )
            }.onFailure { throwable ->
                TACZLegacy.logger.warn(
                    "Failed to export registered gun pack resource {} from {}.",
                    entry.srcPath,
                    entry.modMainClass.name,
                    throwable,
                )
            }
        }
    }

    private fun copyBundledPackTo(targetDirectory: Path, resourceRoot: String, classLoader: ClassLoader): Unit {
        val resourceUrl = classLoader.getResource(resourceRoot)
            ?: throw IllegalStateException("Unable to locate bundled default gun pack at $resourceRoot")

        when (resourceUrl.protocol) {
            "file" -> copyDirectory(Paths.get(resourceUrl.toURI()), targetDirectory)
            "jar" -> {
                val jarRootUri = URI.create(resourceUrl.toURI().toString().substringBefore("!"))
                val fileSystem = try {
                    FileSystems.newFileSystem(jarRootUri, emptyMap<String, Any>())
                } catch (_: FileSystemAlreadyExistsException) {
                    FileSystems.getFileSystem(jarRootUri)
                }
                val sourceRoot = fileSystem.getPath("/$resourceRoot")
                copyDirectory(sourceRoot, targetDirectory)
            }
            else -> throw IllegalStateException("Unsupported resource protocol: ${resourceUrl.protocol}")
        }
    }

    private fun copyDirectory(sourceRoot: Path, targetRoot: Path): Unit {
        Files.createDirectories(targetRoot)
        Files.walk(sourceRoot).use { stream ->
            stream.forEach { sourcePath ->
                val relative = sourceRoot.relativize(sourcePath).toString()
                val targetPath = if (relative.isEmpty()) {
                    targetRoot
                } else {
                    targetRoot.resolve(relative)
                }
                if (Files.isDirectory(sourcePath)) {
                    Files.createDirectories(targetPath)
                } else {
                    Files.createDirectories(targetPath.parent)
                    Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }
    }

    private fun deleteDirectory(directory: Path): Unit {
        if (!Files.exists(directory)) {
            return
        }
        Files.walk(directory)
            .sorted(Comparator.reverseOrder())
            .forEach(Files::deleteIfExists)
    }
}
