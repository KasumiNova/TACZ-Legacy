package com.tacz.legacy.common.application.gunpack

public data class GunDisplayDefinition(
    val sourceId: String,
    val gunId: String,
    val displayResource: String,
    val modelPath: String?,
    val modelTexturePath: String?,
    val lodModelPath: String?,
    val lodTexturePath: String?,
    val slotTexturePath: String?,
    val animationPath: String?,
    val defaultAnimationPath: String? = null,
    val useDefaultAnimation: String? = null,
    val stateMachinePath: String?,
    val stateMachineSource: String = "display",
    val stateMachineScriptContent: String? = null,
    val stateMachineParams: Map<String, Float> = emptyMap(),
    val playerAnimator3rdPath: String?,
    val thirdPersonAnimation: String?,
    val modelParseSucceeded: Boolean,
    val modelBoneCount: Int?,
    val modelCubeCount: Int?,
    val animationParseSucceeded: Boolean,
    val animationClipCount: Int?,
    val animationClipNames: List<String>? = null,
    val animationClipLengthsMillis: Map<String, Long>? = null,
    val animationIdleClipName: String? = null,
    val animationFireClipName: String? = null,
    val animationReloadClipName: String? = null,
    val animationInspectClipName: String? = null,
    val animationDryFireClipName: String? = null,
    val animationDrawClipName: String? = null,
    val animationPutAwayClipName: String? = null,
    val animationWalkClipName: String? = null,
    val animationRunClipName: String? = null,
    val animationAimClipName: String? = null,
    val animationBoltClipName: String? = null,
    val stateMachineResolved: Boolean,
    val playerAnimatorResolved: Boolean,
    val hudTexturePath: String?,
    val hudEmptyTexturePath: String?,
    val showCrosshair: Boolean,
    val ironZoom: Float? = null,
    val zoomModelFov: Float? = null,
    val shootSoundId: String? = null,
    val shootThirdPersonSoundId: String? = null,
    val drawSoundId: String? = null,
    val putAwaySoundId: String? = null,
    val dryFireSoundId: String? = null,
    val inspectSoundId: String? = null,
    val inspectEmptySoundId: String? = null,
    val reloadEmptySoundId: String? = null,
    val reloadTacticalSoundId: String? = null,
    val modelGeometryCount: Int? = null,
    val modelRootBoneCount: Int? = null,
    val modelRootBoneNames: List<String>? = null,

    // --- Display JSON 扩展字段（逐步对齐 TACZ display schema） ---
    val ammoCountStyle: String? = null,
    val damageStyle: String? = null,
    val transformScaleThirdPerson: DisplayVec3? = null,
    val transformScaleGround: DisplayVec3? = null,
    val transformScaleFixed: DisplayVec3? = null,
    val muzzleFlashTexturePath: String? = null,
    val muzzleFlashScale: Float? = null
)

/**
 * display json 里常见的三元向量：例如 transform.scale 或各类 pos/rotate/scale。
 * 注意：这里是纯数据承载，坐标系解释由渲染/消费侧决定。
 */
public data class DisplayVec3(
    val x: Float,
    val y: Float,
    val z: Float
)

public data class GunDisplayScanEntry(
    val sourceId: String,
    val definition: GunDisplayDefinition?,
    val message: String? = null
)

public data class GunDisplayScanReport(
    val entries: List<GunDisplayScanEntry>
) {

    public val total: Int
        get() = entries.size

    public val successCount: Int
        get() = entries.count { it.definition != null }

    public val failedCount: Int
        get() = entries.count { it.definition == null }

    public fun failedEntries(): List<GunDisplayScanEntry> = entries.filter { it.definition == null }

}

public data class GunDisplayRuntimeSnapshot(
    val loadedAtEpochMillis: Long,
    val totalSources: Int,
    val loadedDefinitionsByGunId: Map<String, GunDisplayDefinition>,
    val failedSources: Set<String>
) {

    public val loadedCount: Int
        get() = loadedDefinitionsByGunId.size

    public fun findDefinition(gunId: String): GunDisplayDefinition? =
        loadedDefinitionsByGunId[gunId.trim().lowercase()]

    public companion object {
        public fun empty(atEpochMillis: Long = System.currentTimeMillis()): GunDisplayRuntimeSnapshot =
            GunDisplayRuntimeSnapshot(
                loadedAtEpochMillis = atEpochMillis,
                totalSources = 0,
                loadedDefinitionsByGunId = emptyMap(),
                failedSources = emptySet()
            )
    }

}

public class GunDisplayRuntimeRegistry {

    @Volatile
    private var latestSnapshot: GunDisplayRuntimeSnapshot = GunDisplayRuntimeSnapshot.empty()

    @Synchronized
    public fun replace(scanReport: GunDisplayScanReport?): GunDisplayRuntimeSnapshot {
        val now = System.currentTimeMillis()
        if (scanReport == null) {
            latestSnapshot = GunDisplayRuntimeSnapshot.empty(now)
            return latestSnapshot
        }

        val definitionsByGunId = linkedMapOf<String, GunDisplayDefinition>()
        val failedSources = linkedSetOf<String>()

        scanReport.entries
            .sortedBy { it.sourceId }
            .forEach { entry ->
                val definition = entry.definition
                if (definition == null) {
                    failedSources += entry.sourceId
                    return@forEach
                }

                val normalizedGunId = definition.gunId.trim().lowercase()
                if (normalizedGunId.isBlank()) {
                    failedSources += entry.sourceId
                    return@forEach
                }

                if (definitionsByGunId.containsKey(normalizedGunId)) {
                    failedSources += entry.sourceId
                    return@forEach
                }

                definitionsByGunId[normalizedGunId] = definition.copy(gunId = normalizedGunId)
            }

        latestSnapshot = GunDisplayRuntimeSnapshot(
            loadedAtEpochMillis = now,
            totalSources = scanReport.total,
            loadedDefinitionsByGunId = definitionsByGunId.toMap(),
            failedSources = failedSources
        )
        return latestSnapshot
    }

    @Synchronized
    public fun clear(): GunDisplayRuntimeSnapshot {
        latestSnapshot = GunDisplayRuntimeSnapshot.empty()
        return latestSnapshot
    }

    public fun snapshot(): GunDisplayRuntimeSnapshot = latestSnapshot

}

public object GunDisplayRuntime {

    private val registry: GunDisplayRuntimeRegistry = GunDisplayRuntimeRegistry()

    public fun registry(): GunDisplayRuntimeRegistry = registry

}
