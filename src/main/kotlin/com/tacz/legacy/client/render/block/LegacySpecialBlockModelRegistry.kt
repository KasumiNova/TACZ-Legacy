package com.tacz.legacy.client.render.block

import com.tacz.legacy.common.infrastructure.mc.registry.LegacyContentIds

public data class LegacySpecialBlockModelDescriptor(
    val blockRegistryPath: String,
    val modelResourcePath: String,
    val debugTag: String,
    val translucent: Boolean = false
)

public data class LegacySpecialBlockModelAdaptationStats(
    val adapterCount: Int,
    val translucentAdapterCount: Int,
    val blockRegistryPaths: List<String>
)

public data class LegacySpecialBlockModelValidationEntry(
    val blockRegistryPath: String,
    val modelResourcePath: String,
    val modelJsonClasspathPath: String,
    val exists: Boolean
)

public data class LegacySpecialBlockModelValidationReport(
    val total: Int,
    val valid: Int,
    val missing: Int,
    val missingModelJsonClasspathPaths: List<String>,
    val entries: List<LegacySpecialBlockModelValidationEntry>
)

public object LegacySpecialBlockModelRegistry {

    private val builtinDescriptors: Map<String, LegacySpecialBlockModelDescriptor> = listOf(
        LegacySpecialBlockModelDescriptor(
            blockRegistryPath = LegacyContentIds.WEAPON_WORKBENCH,
            modelResourcePath = "tacz:block/gun_smith_table",
            debugTag = "builtin.weapon_workbench"
        ),
        LegacySpecialBlockModelDescriptor(
            blockRegistryPath = LegacyContentIds.GUN_SMITH_TABLE,
            modelResourcePath = "tacz:block/gun_smith_table",
            debugTag = "builtin.gun_smith_table"
        ),
        LegacySpecialBlockModelDescriptor(
            blockRegistryPath = LegacyContentIds.WORKBENCH_A,
            modelResourcePath = "tacz:block/gun_smith_table",
            debugTag = "builtin.workbench_a"
        ),
        LegacySpecialBlockModelDescriptor(
            blockRegistryPath = LegacyContentIds.WORKBENCH_B,
            modelResourcePath = "tacz:block/gun_smith_table",
            debugTag = "builtin.workbench_b"
        ),
        LegacySpecialBlockModelDescriptor(
            blockRegistryPath = LegacyContentIds.WORKBENCH_C,
            modelResourcePath = "tacz:block/gun_smith_table",
            debugTag = "builtin.workbench_c"
        ),
        LegacySpecialBlockModelDescriptor(
            blockRegistryPath = LegacyContentIds.STEEL_TARGET,
            modelResourcePath = "tacz:block/target",
            debugTag = "builtin.steel_target"
        ),
        LegacySpecialBlockModelDescriptor(
            blockRegistryPath = LegacyContentIds.TARGET,
            modelResourcePath = "tacz:block/target",
            debugTag = "builtin.target"
        ),
        LegacySpecialBlockModelDescriptor(
            blockRegistryPath = LegacyContentIds.STATUE,
            modelResourcePath = "tacz:block/statue",
            debugTag = "builtin.statue"
        )
    ).associateBy { normalizeRegistryPath(it.blockRegistryPath) }

    public fun find(blockRegistryPath: String?): LegacySpecialBlockModelDescriptor? {
        val key = normalizeRegistryPath(blockRegistryPath ?: return null)
        return builtinDescriptors[key]
    }

    public fun allBuiltin(): List<LegacySpecialBlockModelDescriptor> =
        builtinDescriptors.values.sortedBy { it.blockRegistryPath }

    public fun adaptationStats(): LegacySpecialBlockModelAdaptationStats {
        val descriptors = allBuiltin()
        return LegacySpecialBlockModelAdaptationStats(
            adapterCount = descriptors.size,
            translucentAdapterCount = descriptors.count { it.translucent },
            blockRegistryPaths = descriptors.map { it.blockRegistryPath }
        )
    }

    public fun validateModelResources(
        resourceExists: (String) -> Boolean = ::classpathResourceExists
    ): LegacySpecialBlockModelValidationReport {
        val entries = allBuiltin().map { descriptor ->
            val modelJsonPath = toBlockModelJsonClasspathPath(descriptor.modelResourcePath)
            LegacySpecialBlockModelValidationEntry(
                blockRegistryPath = descriptor.blockRegistryPath,
                modelResourcePath = descriptor.modelResourcePath,
                modelJsonClasspathPath = modelJsonPath,
                exists = resourceExists.invoke(modelJsonPath)
            )
        }

        val missingPaths = entries
            .filterNot { it.exists }
            .map { it.modelJsonClasspathPath }

        return LegacySpecialBlockModelValidationReport(
            total = entries.size,
            valid = entries.count { it.exists },
            missing = missingPaths.size,
            missingModelJsonClasspathPaths = missingPaths,
            entries = entries
        )
    }

    private fun normalizeRegistryPath(raw: String): String =
        raw.trim().lowercase().substringAfter(':')

    private fun toBlockModelJsonClasspathPath(modelResourcePath: String): String {
        val normalized = modelResourcePath.trim().lowercase()
        val namespace = normalized.substringBefore(':', "minecraft")
        val domainPath = normalized.substringAfter(':', normalized)
        val blockPath = domainPath.removePrefix("block/")
        return "assets/$namespace/models/block/$blockPath.json"
    }

    private fun classpathResourceExists(path: String): Boolean {
        val classLoader = LegacySpecialBlockModelRegistry::class.java.classLoader
        return classLoader.getResource(path) != null
    }

}
