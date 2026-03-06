package com.tacz.legacy.api.resource

import com.tacz.legacy.TACZLegacy
import java.nio.file.Paths

/**
 * 用于注册需要导出的枪包资源，供附属模组复用。
 */
public object ResourceManager {
    @JvmField
    public val EXTRA_ENTRIES: MutableList<ExtraEntry> = mutableListOf()

    /**
     * @deprecated 旧入口仅保留兼容提示，导出请改用 [registerExportResource]。
     */
    @Deprecated("Use registerExportResource instead")
    @JvmStatic
    public fun registerExtraGunPack(modMainClass: Class<*>, extraFolderPath: String): Unit {
        TACZLegacy.logger.warn(
            "Deprecated gun pack export API used by {}: {}. Please migrate to registerExportResource().",
            modMainClass.name,
            extraFolderPath,
        )
    }

    @JvmStatic
    public fun registerExportResource(modMainClass: Class<*>, extraFolderPath: String): Unit {
        EXTRA_ENTRIES += ExtraEntry(
            modMainClass = modMainClass,
            srcPath = extraFolderPath,
            extraDirName = Paths.get(extraFolderPath).fileName.toString(),
        )
    }

    public data class ExtraEntry(
        val modMainClass: Class<*>,
        val srcPath: String,
        val extraDirName: String,
    )
}
