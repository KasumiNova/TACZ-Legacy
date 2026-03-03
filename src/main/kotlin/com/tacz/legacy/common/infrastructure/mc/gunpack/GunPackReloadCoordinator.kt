package com.tacz.legacy.common.infrastructure.mc.gunpack

import com.tacz.legacy.common.application.gunpack.GunDisplayRuntime
import com.tacz.legacy.common.application.gunpack.GunDisplayRuntimeSnapshot
import com.tacz.legacy.common.application.gunpack.GunPackRuntime
import com.tacz.legacy.common.application.gunpack.GunPackRuntimeSnapshot
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

        val weaponRuntimeSnapshot = WeaponRuntime.registry().replaceFromGunPack(gunPackSnapshot)

        val currentItemPaths = LegacyItems.dynamicGunRegistryPaths(gunPackSnapshot).toSet()
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
            weaponRuntimeSnapshot = weaponRuntimeSnapshot,
            lockedItemRegistryPaths = lockedItemRegistryPaths,
            currentItemRegistryPaths = currentItemPaths,
            itemRegistryDelta = delta
        )
    }
}
