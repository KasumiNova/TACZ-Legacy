package com.tacz.legacy.common.infrastructure.mc.gunpack

import com.tacz.legacy.common.application.gunpack.GunDisplayRuntime
import com.tacz.legacy.common.application.gunpack.GunDisplayRuntimeSnapshot
import com.tacz.legacy.common.application.gunpack.GunPackRuntime
import com.tacz.legacy.common.application.gunpack.GunPackRuntimeSnapshot
import com.tacz.legacy.common.application.gunpack.GunPackTooltipLangRuntime
import com.tacz.legacy.common.application.gunpack.GunPackTooltipLangSnapshot
import com.tacz.legacy.common.application.gunpack.GunPackTooltipIndexRuntime
import com.tacz.legacy.common.application.gunpack.GunPackTooltipIndexSnapshot
import com.tacz.legacy.common.application.gunpack.WeaponAttachmentCompatibilityRuntime
import com.tacz.legacy.common.application.gunpack.WeaponAttachmentCompatibilitySnapshot
import com.tacz.legacy.common.application.weapon.WeaponLuaScriptEngine
import com.tacz.legacy.common.application.weapon.WeaponRuntime
import com.tacz.legacy.common.application.weapon.WeaponRuntimeSnapshot
import com.tacz.legacy.common.infrastructure.mc.registry.LegacyItems
import org.apache.logging.log4j.Logger
import java.nio.file.Path

public data class ItemRegistryDelta(
    val addedPaths: Set<String>,
    val removedPaths: Set<String>
) {
    public val changed: Boolean
        get() = addedPaths.isNotEmpty() || removedPaths.isNotEmpty()
}

public data class GunPackReloadOutcome(
    val gunPackSnapshot: GunPackRuntimeSnapshot,
    val gunDisplaySnapshot: GunDisplayRuntimeSnapshot,
    val tooltipLangSnapshot: GunPackTooltipLangSnapshot,
    val tooltipIndexSnapshot: GunPackTooltipIndexSnapshot,
    val attachmentCompatibilitySnapshot: WeaponAttachmentCompatibilitySnapshot,
    val weaponRuntimeSnapshot: WeaponRuntimeSnapshot,
    val lockedItemRegistryPaths: Set<String>,
    val currentItemRegistryPaths: Set<String>,
    val itemRegistryDelta: ItemRegistryDelta
) {
    public val itemRegistryChanged: Boolean
        get() = itemRegistryDelta.changed
}

public object GunPackReloadCoordinator {

    private val gunPackScanner: GunPackCompatibilityPreInitScanner = GunPackCompatibilityPreInitScanner()
    private val gunDisplayScanner: GunDisplayPreInitScanner = GunDisplayPreInitScanner()
    private val tooltipLangScanner: GunPackTooltipLangPreInitScanner = GunPackTooltipLangPreInitScanner()
    private val tooltipIndexScanner: GunPackTooltipIndexPreInitScanner = GunPackTooltipIndexPreInitScanner()
    private val attachmentScanner: WeaponAttachmentPreInitScanner = WeaponAttachmentPreInitScanner()

    @Volatile
    private var configRootPath: Path? = null

    @Volatile
    private var lockedItemRegistryPaths: Set<String> = emptySet()

    @Synchronized
    public fun bootstrap(configRoot: Path, logger: Logger): GunPackReloadOutcome {
        val normalizedRoot = configRoot.toAbsolutePath().normalize()
        configRootPath = normalizedRoot
        return reloadInternal(
            configRoot = normalizedRoot,
            logger = logger,
            initializeItemRegistryLock = true
        )
    }

    @Synchronized
    public fun reload(logger: Logger): GunPackReloadOutcome? {
        val root = configRootPath ?: return null
        return reloadInternal(
            configRoot = root,
            logger = logger,
            initializeItemRegistryLock = false
        )
    }

    public fun lockedItemRegistryPaths(): Set<String> = lockedItemRegistryPaths

    internal fun computeItemRegistryDelta(
        lockedPaths: Set<String>,
        currentPaths: Set<String>
    ): ItemRegistryDelta {
        val added = (currentPaths - lockedPaths).toSortedSet()
        val removed = (lockedPaths - currentPaths).toSortedSet()
        return ItemRegistryDelta(
            addedPaths = added,
            removedPaths = removed
        )
    }

    private fun reloadInternal(
        configRoot: Path,
        logger: Logger,
        initializeItemRegistryLock: Boolean
    ): GunPackReloadOutcome {
        val gunPackReport = gunPackScanner.scanAndLog(configRoot, logger)
        val gunPackSnapshot = GunPackRuntime.registry().replace(gunPackReport)

        val gunDisplayReport = gunDisplayScanner.scanAndLog(configRoot, logger)
        val gunDisplaySnapshot = GunDisplayRuntime.registry().replace(gunDisplayReport)

        val tooltipLangSnapshot = tooltipLangScanner.scan(configRoot, logger)
        GunPackTooltipLangRuntime.registry().replace(tooltipLangSnapshot)

        val tooltipIndexSnapshot = tooltipIndexScanner.scan(configRoot, logger)
        GunPackTooltipIndexRuntime.registry().replace(tooltipIndexSnapshot)

        val attachmentSnapshot = attachmentScanner.scan(configRoot, logger)
        WeaponAttachmentCompatibilityRuntime.registry().replace(attachmentSnapshot)
        val cachedLuaScripts = WeaponLuaScriptEngine.preload(gunDisplaySnapshot)
        logger.info(
            "[WeaponLua] cached scripts={} loadedDisplays={}",
            cachedLuaScripts,
            gunDisplaySnapshot.loadedCount
        )

        val weaponRuntimeSnapshot = WeaponRuntime.registry().replaceFromGunPack(gunPackSnapshot)

        val currentItemPaths = LegacyItems.dynamicStandaloneRegistryPaths(gunPackSnapshot).toSet()
        if (initializeItemRegistryLock || lockedItemRegistryPaths.isEmpty()) {
            lockedItemRegistryPaths = currentItemPaths
        }
        val delta = computeItemRegistryDelta(
            lockedPaths = lockedItemRegistryPaths,
            currentPaths = currentItemPaths
        )

        return GunPackReloadOutcome(
            gunPackSnapshot = gunPackSnapshot,
            gunDisplaySnapshot = gunDisplaySnapshot,
            tooltipLangSnapshot = tooltipLangSnapshot,
            tooltipIndexSnapshot = tooltipIndexSnapshot,
            attachmentCompatibilitySnapshot = attachmentSnapshot,
            weaponRuntimeSnapshot = weaponRuntimeSnapshot,
            lockedItemRegistryPaths = lockedItemRegistryPaths,
            currentItemRegistryPaths = currentItemPaths,
            itemRegistryDelta = delta
        )
    }
}
