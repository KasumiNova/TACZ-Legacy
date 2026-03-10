package com.tacz.legacy.common.foundation

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.tacz.legacy.TACZLegacy
import com.tacz.legacy.api.DefaultAssets
import com.tacz.legacy.api.event.GunFireEvent
import com.tacz.legacy.api.event.GunShootEvent
import com.tacz.legacy.api.item.IAttachment
import com.tacz.legacy.api.item.IGun
import com.tacz.legacy.api.item.attachment.AttachmentType
import com.tacz.legacy.api.item.gun.FireMode
import com.tacz.legacy.common.entity.EntityKineticBullet
import net.minecraft.entity.monster.EntityPigZombie
import com.tacz.legacy.common.item.LegacyItems
import com.tacz.legacy.common.resource.BoltType
import com.tacz.legacy.common.resource.GunCombatData
import com.tacz.legacy.common.resource.GunDataAccessor
import com.tacz.legacy.common.resource.TACZDisplayDefinition
import com.tacz.legacy.common.resource.TACZGunPackPresentation
import com.tacz.legacy.common.resource.TACZGunPackRuntimeRegistry
import com.tacz.legacy.common.resource.TACZLoadedAttachment
import com.tacz.legacy.common.resource.TACZLoadedGun
import com.tacz.legacy.common.resource.TACZRuntimeSnapshot
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.item.ItemStack
import net.minecraft.server.MinecraftServer
import net.minecraft.util.ResourceLocation
import net.minecraftforge.event.entity.EntityJoinWorldEvent
import net.minecraftforge.event.world.ExplosionEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.PlayerEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

internal data class FocusedSmokeDisplaySummary(
    val displayId: ResourceLocation,
    val defaultAnimationType: String?,
    val defaultAnimationId: ResourceLocation?,
    val stateMachineId: ResourceLocation?,
    val soundKeys: List<String>,
) {
    fun hasAnimationSignals(): Boolean =
        defaultAnimationType != null || defaultAnimationId != null || stateMachineId != null || soundKeys.isNotEmpty()
}

internal data class FocusedSmokePlan(
    val regularGunId: ResourceLocation,
    val explosiveGunId: ResourceLocation?,
    val attachmentIds: List<ResourceLocation>,
    val regularDisplay: FocusedSmokeDisplaySummary?,
    val explosiveDisplay: FocusedSmokeDisplaySummary?,
) {
    fun attachmentSummary(): String = attachmentIds.joinToString(",").ifBlank { "none" }
}

private data class FocusedSmokeAttachmentCandidate(
    val attachment: TACZLoadedAttachment,
    val priority: Int,
) {
    val id: ResourceLocation
        get() = attachment.id

    val typeKey: String
        get() = attachment.index.type.lowercase()
}

internal object FocusedSmokePlanner {
    fun buildPlan(snapshot: TACZRuntimeSnapshot): FocusedSmokePlan? {
        val attachments = TACZGunPackPresentation.sortedAttachments(snapshot)
        val gunCandidates = TACZGunPackPresentation.sortedGuns(snapshot).map { gun ->
            val combatData = GunCombatData.fromRawJson(gun.data.raw, gun.data)
            val displaySummary = describeGunDisplay(snapshot, gun.id)
            val compatibleAttachments = selectAttachmentsForGun(snapshot, gun.id, attachments)
            FocusedSmokeGunCandidate(
                gun = gun,
                combatData = combatData,
                displaySummary = displaySummary,
                compatibleAttachments = compatibleAttachments,
            )
        }

        val regular = gunCandidates
            .asSequence()
            .filterNot(FocusedSmokeGunCandidate::isExplosive)
            .sortedWith(
                compareByDescending<FocusedSmokeGunCandidate> { it.regularPriority }
                    .thenBy { it.gun.index.sort }
                    .thenBy { it.gun.id.toString() }
            )
            .firstOrNull()
            ?: return null

        val explosive = gunCandidates
            .filter(FocusedSmokeGunCandidate::isExplosive)
            .maxByOrNull(FocusedSmokeGunCandidate::explosivePriority)

        return FocusedSmokePlan(
            regularGunId = regular.gun.id,
            explosiveGunId = explosive?.gun?.id,
            attachmentIds = regular.compatibleAttachments.map(FocusedSmokeAttachmentCandidate::id),
            regularDisplay = regular.displaySummary,
            explosiveDisplay = explosive?.displaySummary,
        )
    }

    private fun selectAttachmentsForGun(
        snapshot: TACZRuntimeSnapshot,
        gunId: ResourceLocation,
        attachments: List<TACZLoadedAttachment>,
    ): List<FocusedSmokeAttachmentCandidate> {
        val compatible = attachments
            .asSequence()
            .filter { attachment -> TACZGunPackPresentation.allowsAttachment(snapshot, gunId, attachment.id) }
            .map { attachment ->
                FocusedSmokeAttachmentCandidate(
                    attachment = attachment,
                    priority = scoreAttachment(snapshot, attachment),
                )
            }
            .sortedWith(
                compareByDescending<FocusedSmokeAttachmentCandidate> { it.priority }
                    .thenBy { it.attachment.index.sort }
                    .thenBy { it.id.toString() }
            )
            .toList()
        val preferred = compatible
            .asSequence()
            .filter { it.priority > 0 }
            .distinctBy(FocusedSmokeAttachmentCandidate::typeKey)
            .take(MAX_RENDER_ATTACHMENT_SAMPLES)
            .toList()
        return if (preferred.isNotEmpty()) preferred else compatible.take(1)
    }

    private fun scoreAttachment(snapshot: TACZRuntimeSnapshot, attachment: TACZLoadedAttachment): Int {
        val type = attachment.index.type.lowercase()
        val displayId = TACZGunPackPresentation.resolveAttachmentDisplayId(snapshot, attachment.id)
        val raw = displayId?.let(snapshot.attachmentDisplays::get)?.raw
        var score = 0
        if (displayId != null) {
            score += 20
        }
        if (raw?.safeBoolean("scope") == true) {
            score += 1000
        }
        if (type == AttachmentType.SCOPE.serializedName) {
            score += 650
        }
        if (!raw?.safeString("adapter").isNullOrBlank()) {
            score += 500
        }
        if (raw?.safeBoolean("show_muzzle") == true) {
            score += 360
        }
        if (raw?.safeBoolean("sight") == true) {
            score += 220
        }
        if ((raw?.jsonArraySize("views") ?: 0) > 0) {
            score += 180
        }
        if ((raw?.jsonArraySize("zoom") ?: 0) > 0) {
            score += 140
        }
        if (type == AttachmentType.MUZZLE.serializedName) {
            score += 320
        }
        if (type == AttachmentType.GRIP.serializedName) {
            score += 280
        }
        if (type == AttachmentType.STOCK.serializedName) {
            score += 260
        }
        return score
    }

    fun describeGunDisplay(snapshot: TACZRuntimeSnapshot, gunId: ResourceLocation): FocusedSmokeDisplaySummary? {
        val displayId = TACZGunPackPresentation.resolveGunDisplayId(snapshot, gunId) ?: return null
        return describeDisplay(snapshot.gunDisplays[displayId])
    }

    private fun describeDisplay(definition: TACZDisplayDefinition?): FocusedSmokeDisplaySummary? {
        val raw = definition?.raw ?: return null
        val displayId = definition.id
        val sounds = raw.safeObject("sounds")
            ?.entrySet()
            ?.map { it.key }
            ?.sorted()
            .orEmpty()
        return FocusedSmokeDisplaySummary(
            displayId = displayId,
            defaultAnimationType = raw.safeString("use_default_animation"),
            defaultAnimationId = raw.safeResourceLocation("default_animation"),
            stateMachineId = raw.safeResourceLocation("state_machine"),
            soundKeys = sounds,
        )
    }

    private data class FocusedSmokeGunCandidate(
        val gun: TACZLoadedGun,
        val combatData: GunCombatData,
        val displaySummary: FocusedSmokeDisplaySummary?,
        val compatibleAttachments: List<FocusedSmokeAttachmentCandidate>,
    ) {
        val isExplosive: Boolean
            get() = combatData.bulletData.explosionData?.explode == true

        val regularPriority: Int
            get() {
                var score = 0
                if (displaySummary?.hasAnimationSignals() == true) {
                    score += 100_000
                }
                if (combatData.boltType != BoltType.MANUAL_ACTION) {
                    score += 10_000
                }
                if (compatibleAttachments.isNotEmpty()) {
                    score += 1_000
                }
                score += compatibleAttachments.sumOf(FocusedSmokeAttachmentCandidate::priority).coerceAtMost(5_000)
                return score
            }

        val explosivePriority: Int
            get() {
                val tags = buildList {
                    add(gun.id.path)
                    add(gun.index.type)
                    add(displaySummary?.displayId?.path)
                }.filterNotNull().joinToString(" ").lowercase()
                val keywordScore = when {
                    "rpg" in tags || "rocket" in tags -> 300
                    "heavy" in tags -> 220
                    "launcher" in tags || "grenade" in tags -> 160
                    else -> 0
                }
                val animationScore = if (displaySummary?.hasAnimationSignals() == true) 10 else 0
                return keywordScore + animationScore
            }
    }

    private const val MAX_RENDER_ATTACHMENT_SAMPLES: Int = 4
}

internal object FocusedSmokeRuntime {
    private const val ENABLED_PROPERTY: String = "tacz.focusedSmoke"
    private const val AUTO_WORLD_PROPERTY: String = "tacz.focusedSmoke.autoWorld"
    private const val REGULAR_GUN_PROPERTY: String = "tacz.focusedSmoke.regularGun"
    private const val WORLD_FOLDER_PROPERTY: String = "tacz.focusedSmoke.worldFolder"
    private const val WORLD_NAME_PROPERTY: String = "tacz.focusedSmoke.worldName"
    private const val WORLD_TIME_PROPERTY: String = "tacz.focusedSmoke.worldTime"
    private const val FREEZE_WORLD_TIME_PROPERTY: String = "tacz.focusedSmoke.freezeWorldTime"
    private const val CLEAR_WEATHER_PROPERTY: String = "tacz.focusedSmoke.clearWeather"
    private const val EXPLOSIVE_GUN_PROPERTY: String = "tacz.focusedSmoke.explosiveGun"
    private const val DISABLE_EXPLOSIVE_PROPERTY: String = "tacz.focusedSmoke.disableExplosive"
    private const val DISABLE_ATTACHMENTS_PROPERTY: String = "tacz.focusedSmoke.disableAttachments"
    private const val REFIT_PREVIEW_PROPERTY: String = "tacz.focusedSmoke.refitPreview"
    private const val PASS_AFTER_ANIMATION_PROPERTY: String = "tacz.focusedSmoke.passAfterAnimation"
    private const val PASS_AFTER_REFIT_PROPERTY: String = "tacz.focusedSmoke.passAfterRefit"
    private const val REQUIRE_TRACER_FRAME_PROPERTY: String = "tacz.focusedSmoke.requireTracerFrame"
    private const val SKIP_RELOAD_PROPERTY: String = "tacz.focusedSmoke.skipReload"
    private const val REGULAR_SHOT_PITCH_PROPERTY: String = "tacz.focusedSmoke.regularShotPitch"
    private const val REGULAR_SHOT_YAW_PROPERTY: String = "tacz.focusedSmoke.regularShotYaw"
    private const val BULLET_SPEED_MULTIPLIER_PROPERTY: String = "tacz.focusedSmoke.bulletSpeedMultiplier"
    private const val TRACER_SIZE_MULTIPLIER_PROPERTY: String = "tacz.focusedSmoke.tracerSizeMultiplier"
    private const val TRACER_LENGTH_MULTIPLIER_PROPERTY: String = "tacz.focusedSmoke.tracerLengthMultiplier"
    private const val HIT_FEEDBACK_TARGET_PROPERTY: String = "tacz.focusedSmoke.hitFeedbackTarget"

    private val loggedKeys = ConcurrentHashMap.newKeySet<String>()

    @Volatile
    private var plannedScenario: FocusedSmokePlan? = null

    @Volatile
    private var focusedPlayerId: UUID? = null

    @Volatile
    internal var serverGearReady: Boolean = false
        private set

    @Volatile
    internal var animationObserved: Boolean = false
        private set

    @Volatile
    internal var regularProjectileObserved: Boolean = false
        private set

    @Volatile
    internal var tracerFrameObserved: Boolean = false
        private set

    @Volatile
    internal var regularGunFireCount: Int = 0
        private set

    @Volatile
    internal var audioPlaybackObserved: Boolean = false
        private set

    @Volatile
    internal var explosiveProjectileObserved: Boolean = false
        private set

    @Volatile
    internal var explosionObserved: Boolean = false
        private set

    @Volatile
    private var explosiveProjectileSeenAtMs: Long = 0L

    @Volatile
    private var passLogged: Boolean = false

    @Volatile
    private var failureReason: String? = null

    @Volatile
    private var lastAnimationDetails: String? = null

    internal val enabled: Boolean
        get() = System.getProperty(ENABLED_PROPERTY, "false").toBoolean()

    internal val autoWorldLaunchEnabled: Boolean
        get() = System.getProperty(AUTO_WORLD_PROPERTY, "true").toBoolean()

    internal val worldFolderName: String
        get() = System.getProperty(WORLD_FOLDER_PROPERTY, "tacz_focused_smoke_auto")

    internal val worldDisplayName: String
        get() = System.getProperty(WORLD_NAME_PROPERTY, "tacz_focused_smoke_auto")

    internal val forcedWorldTime: Long?
        get() = System.getProperty(WORLD_TIME_PROPERTY)?.toLongOrNull()

    internal val freezeWorldTimeEnabled: Boolean
        get() = System.getProperty(FREEZE_WORLD_TIME_PROPERTY, if (forcedWorldTime != null) "true" else "false").toBoolean()

    internal val clearWeatherEnabled: Boolean
        get() = System.getProperty(CLEAR_WEATHER_PROPERTY, if (forcedWorldTime != null) "true" else "false").toBoolean()

    internal val forcedRegularGunId: ResourceLocation?
        get() = parseResourceLocation(System.getProperty(REGULAR_GUN_PROPERTY))

    internal val forcedExplosiveGunId: ResourceLocation?
        get() = parseResourceLocation(System.getProperty(EXPLOSIVE_GUN_PROPERTY))

    internal val disableExplosive: Boolean
        get() = System.getProperty(DISABLE_EXPLOSIVE_PROPERTY, "false").toBoolean()

    internal val disableAttachments: Boolean
        get() = System.getProperty(DISABLE_ATTACHMENTS_PROPERTY, "false").toBoolean()

    internal val refitPreviewEnabled: Boolean
        get() = System.getProperty(REFIT_PREVIEW_PROPERTY, "false").toBoolean()

    internal val passAfterAnimationEnabled: Boolean
        get() = System.getProperty(PASS_AFTER_ANIMATION_PROPERTY, "false").toBoolean()

    internal val passAfterRefitEnabled: Boolean
        get() = System.getProperty(PASS_AFTER_REFIT_PROPERTY, "false").toBoolean()

    internal val skipReloadEnabled: Boolean
        get() = System.getProperty(SKIP_RELOAD_PROPERTY, "false").toBoolean()

    internal val requireTracerFrameEnabled: Boolean
        get() = System.getProperty(REQUIRE_TRACER_FRAME_PROPERTY, "false").toBoolean()

    internal val regularShotPitchOverride: Float?
        get() = System.getProperty(REGULAR_SHOT_PITCH_PROPERTY)?.toFloatOrNull()

    internal val regularShotYawOverride: Float?
        get() = System.getProperty(REGULAR_SHOT_YAW_PROPERTY)?.toFloatOrNull()

    internal val hitFeedbackTargetEnabled: Boolean
        get() = System.getProperty(HIT_FEEDBACK_TARGET_PROPERTY, "false").toBoolean()

    internal val bulletSpeedMultiplier: Float
        get() = parsePositiveFloatProperty(BULLET_SPEED_MULTIPLIER_PROPERTY)

    internal val tracerSizeMultiplier: Float
        get() = parsePositiveFloatProperty(TRACER_SIZE_MULTIPLIER_PROPERTY)

    internal val tracerLengthMultiplier: Float
        get() = parsePositiveFloatProperty(TRACER_LENGTH_MULTIPLIER_PROPERTY)

    @Synchronized
    internal fun currentPlan(snapshot: TACZRuntimeSnapshot = TACZGunPackRuntimeRegistry.getSnapshot()): FocusedSmokePlan? {
        if (!enabled) {
            return null
        }
        val cached = plannedScenario
        if (cached != null) {
            return cached
        }
        val built = FocusedSmokePlanner.buildPlan(snapshot)?.let { applyOverrides(snapshot, it) }
        if (built == null) {
            logOnce("plan-missing", "FAIL reason=no_regular_gun_plan")
        }
        plannedScenario = built
        return built
    }

    @Synchronized
    internal fun resetScenarioState() {
        plannedScenario = null
        focusedPlayerId = null
        serverGearReady = false
        animationObserved = false
        regularProjectileObserved = false
        tracerFrameObserved = false
        regularGunFireCount = 0
        audioPlaybackObserved = false
        explosiveProjectileObserved = false
        explosionObserved = false
        explosiveProjectileSeenAtMs = 0L
        passLogged = false
        failureReason = null
        lastAnimationDetails = null
        loggedKeys.clear()
    }

    private fun applyOverrides(snapshot: TACZRuntimeSnapshot, plan: FocusedSmokePlan): FocusedSmokePlan {
        var updatedPlan = plan
        if (disableAttachments && updatedPlan.attachmentIds.isNotEmpty()) {
            logOnce("attachments-disabled", "ATTACHMENTS_DISABLED for_focused_smoke")
            updatedPlan = updatedPlan.copy(attachmentIds = emptyList())
        }

        forcedRegularGunId?.let { forcedRegular ->
            if (!snapshot.guns.containsKey(forcedRegular)) {
                logOnce("forced-regular-missing", "REGULAR_OVERRIDE_MISSING gun=$forcedRegular")
            } else {
                logOnce("forced-regular-using", "REGULAR_OVERRIDE gun=$forcedRegular")
                updatedPlan = updatedPlan.copy(
                    regularGunId = forcedRegular,
                    attachmentIds = emptyList(),
                    regularDisplay = FocusedSmokePlanner.describeGunDisplay(snapshot, forcedRegular),
                )
            }
        }

        if (disableExplosive) {
            if (updatedPlan.explosiveGunId != null) {
                logOnce("explosive-disabled", "EXPLOSIVE_DISABLED for_focused_smoke")
            }
            return updatedPlan.copy(explosiveGunId = null, explosiveDisplay = null)
        }

        val forcedExplosive = forcedExplosiveGunId ?: return updatedPlan
        if (!snapshot.guns.containsKey(forcedExplosive)) {
            logOnce("forced-explosive-missing", "EXPLOSIVE_OVERRIDE_MISSING gun=$forcedExplosive")
            return updatedPlan
        }
        logOnce("forced-explosive-using", "EXPLOSIVE_OVERRIDE gun=$forcedExplosive")
        return updatedPlan.copy(
            explosiveGunId = forcedExplosive,
            explosiveDisplay = FocusedSmokePlanner.describeGunDisplay(snapshot, forcedExplosive),
        )
    }

    private fun parseResourceLocation(value: String?): ResourceLocation? {
        if (value.isNullOrBlank()) {
            return null
        }
        return runCatching { ResourceLocation(value.trim()) }.getOrNull()
    }

    private fun parsePositiveFloatProperty(name: String): Float {
        val rawValue = System.getProperty(name)?.toFloatOrNull() ?: return 1.0f
        if (!rawValue.isFinite() || rawValue <= 0.0f) {
            return 1.0f
        }
        return rawValue
    }

    internal fun rememberFocusedPlayer(player: EntityPlayerMP) {
        if (!enabled) {
            return
        }
        focusedPlayerId = player.uniqueID
    }

    internal fun markAnimationObserved(details: String) {
        if (!enabled) {
            return
        }
        animationObserved = true
        lastAnimationDetails = details
        logOnce("animation-observed", "ANIMATION_OBSERVED $details")
        if (passAfterAnimationEnabled) {
            forcePass("animation_only")
            return
        }
        maybeLogPass()
    }

    internal fun markLeftClickSuppressed(details: String) {
        if (!enabled) {
            return
        }
        logOnce("left-click-suppressed", "LEFT_CLICK_SUPPRESSED $details")
    }

    internal fun markRegularShootResult(result: String, gunId: ResourceLocation) {
        if (!enabled) {
            return
        }
        log("REGULAR_SHOOT_RESULT gun=$gunId result=$result")
    }

    internal fun markTracerFrameObserved(details: String) {
        if (!enabled) {
            return
        }
        tracerFrameObserved = true
        logOnce("tracer-frame-observed", "TRACER_FRAME_OBSERVED $details")
        maybeLogPass()
    }

    internal fun markAudioPlaybackObserved(details: String = "") {
        if (!enabled) {
            return
        }
        audioPlaybackObserved = true
        logOnce("audio-playback", "AUDIO_PLAYBACK_OBSERVED $details".trim())
    }

    internal fun expectedRegularFireCount(): Int = 1

    internal fun hasObservedExpectedRegularFireCount(): Boolean =
        regularGunFireCount >= expectedRegularFireCount()

    internal fun markExplosiveShootResult(result: String, gunId: ResourceLocation) {
        if (!enabled) {
            return
        }
        log("EXPLOSIVE_SHOOT_RESULT gun=$gunId result=$result")
    }

    internal fun markFailure(reason: String) {
        if (!enabled || failureReason != null) {
            return
        }
        failureReason = reason
        log("FAIL reason=$reason")
    }

    internal fun hasFailed(): Boolean = failureReason != null

    internal fun hasPassed(): Boolean = passLogged

    internal fun forcePass(mode: String) {
        if (!enabled || passLogged) {
            return
        }
        passLogged = true
        val plan = plannedScenario
        val attachment = plan?.attachmentSummary() ?: "skipped"
        val explosive = plan?.explosiveGunId?.toString() ?: "skipped"
        log(
            "PASS mode=$mode animation=$animationObserved projectile=$regularProjectileObserved " +
                "explosion=${plan?.explosiveGunId == null || explosionObserved} regularGun=${plan?.regularGunId ?: "unknown"} " +
                "explosiveGun=$explosive attachment=$attachment"
        )
    }

    internal fun statusSummary(): String {
        val plan = plannedScenario
        return buildString {
            append("enabled=").append(enabled)
            append(", gear=").append(serverGearReady)
            append(", animation=").append(animationObserved)
            append(", projectile=").append(regularProjectileObserved)
            append(", tracerFrame=").append(tracerFrameObserved)
            append(", explosion=").append(explosionObserved)
            append(", plan=").append(plan?.regularGunId ?: "<none>")
            plan?.explosiveGunId?.let { append("/").append(it) }
            failureReason?.let { append(", failure=").append(it) }
        }
    }

    private fun maybeLogPass() {
        if (!enabled || passLogged) {
            return
        }
        val plan = plannedScenario ?: return
        if (!animationObserved || !regularProjectileObserved) {
            return
        }
        if (requireTracerFrameEnabled && !tracerFrameObserved) {
            return
        }
        if (plan.explosiveGunId != null && !explosionObserved) {
            return
        }
        passLogged = true
        val attachment = plan.attachmentSummary()
        val explosive = plan.explosiveGunId?.toString() ?: "skipped"
        log("PASS animation=true projectile=true explosion=${plan.explosiveGunId == null || explosionObserved} regularGun=${plan.regularGunId} explosiveGun=$explosive attachment=$attachment")
    }

    private fun log(message: String) {
        TACZLegacy.logger.info("[FocusedSmoke] {}", message)
    }

    private fun logOnce(key: String, message: String) {
        if (loggedKeys.add(key)) {
            log(message)
        }
    }

    private fun matchesFocusedPlayer(entityId: UUID?): Boolean {
        val expected = focusedPlayerId ?: return false
        return entityId == expected
    }

    private fun prepareSmokeGear(player: EntityPlayerMP, plan: FocusedSmokePlan) {
        val regularStack = createGunStack(plan.regularGunId)
        if (plan.attachmentIds.isNotEmpty()) {
            val iGun = regularStack.item as? IGun
            plan.attachmentIds.forEach { attachmentId ->
                val attachmentStack = ItemStack(LegacyItems.ATTACHMENT)
                LegacyItems.ATTACHMENT.setAttachmentId(attachmentStack, attachmentId)
                iGun?.installAttachment(regularStack, attachmentStack)
            }
        }
        player.inventory.setInventorySlotContents(0, regularStack)

        plan.explosiveGunId?.let { explosiveGunId ->
            player.inventory.setInventorySlotContents(1, createGunStack(explosiveGunId))
        }

        player.inventory.markDirty()
        player.inventoryContainer.detectAndSendChanges()
        if (hitFeedbackTargetEnabled) {
            spawnHitFeedbackTarget(player, plan.regularGunId)
        }
        applyWorldVisualOverrides(player.server)
        logCaptureOverrides()
        serverGearReady = true
        val attachmentText = plan.attachmentSummary()
        logOnce(
            "server-gear-ready",
            "SERVER_GEAR_READY regularGun=${plan.regularGunId} explosiveGun=${plan.explosiveGunId ?: "none"} attachment=$attachmentText"
        )
    }

    private fun logCaptureOverrides() {
        val bulletSpeed = bulletSpeedMultiplier
        val tracerSize = tracerSizeMultiplier
        val tracerLength = tracerLengthMultiplier
        if (bulletSpeed == 1.0f && tracerSize == 1.0f && tracerLength == 1.0f) {
            return
        }
        logOnce(
            "capture-overrides",
            "TRACER_CAPTURE_OVERRIDES bulletSpeedMultiplier=$bulletSpeed tracerSizeMultiplier=$tracerSize tracerLengthMultiplier=$tracerLength"
        )
    }

    private fun applyWorldVisualOverrides(server: MinecraftServer) {
        val forcedTime = forcedWorldTime
        val freezeTime = freezeWorldTimeEnabled
        val clearWeather = clearWeatherEnabled
        if (forcedTime == null && !clearWeather) {
            return
        }
        server.worlds
            .asSequence()
            .filterNotNull()
            .forEach { world ->
                if (forcedTime != null) {
                    world.setWorldTime(forcedTime)
                    world.gameRules.setOrCreateGameRule("doDaylightCycle", (!freezeTime).toString())
                }
                if (clearWeather) {
                    world.worldInfo.setCleanWeatherTime(Int.MAX_VALUE)
                    world.worldInfo.setRainTime(0)
                    world.worldInfo.setRaining(false)
                    world.worldInfo.setThunderTime(0)
                    world.worldInfo.setThundering(false)
                    world.gameRules.setOrCreateGameRule("doWeatherCycle", "false")
                }
                logOnce(
                    "world-visual-${world.provider.dimension}",
                    "WORLD_VISUAL_OVERRIDES dimension=${world.provider.dimension} worldTime=${forcedTime ?: "unchanged"} freezeTime=$freezeTime clearWeather=$clearWeather"
                )
            }
    }

    private fun createGunStack(gunId: ResourceLocation): ItemStack {
        val stack = ItemStack(LegacyItems.MODERN_KINETIC_GUN)
        val iGun = stack.item as IGun
        val gunData = GunDataAccessor.getGunData(gunId)
        val ammoAmount = gunData?.ammoAmount?.coerceAtLeast(1) ?: 1
        val initialAmmo = if (ammoAmount > 1) ammoAmount - 1 else ammoAmount
        iGun.setGunId(stack, gunId)
        iGun.setCurrentAmmoCount(stack, initialAmmo)
        iGun.setDummyAmmoAmount(stack, (ammoAmount * 8).coerceAtLeast(96))
        val fireMode = gunData?.fireModesSet?.firstOrNull()?.let(::parseFireMode) ?: FireMode.UNKNOWN
        iGun.setFireMode(stack, fireMode)
        if ((gunData?.boltType ?: BoltType.OPEN_BOLT) != BoltType.OPEN_BOLT) {
            iGun.setBulletInBarrel(stack, true)
        }
        return stack
    }

    private fun spawnHitFeedbackTarget(player: EntityPlayerMP, regularGunId: ResourceLocation) {
        val world = player.serverWorld
        val look = player.lookVec.normalize()
        val spawnDistance = 8.0
        val target = EntityPigZombie(world).apply {
            setNoAI(true)
            setPosition(
                player.posX + look.x * spawnDistance,
                player.posY,
                player.posZ + look.z * spawnDistance,
            )
            rotationYaw = player.rotationYaw + 180.0f
            renderYawOffset = rotationYaw
            val baseDamage = GunDataAccessor.getGunData(regularGunId)?.bulletData?.damage ?: 4.0f
            // Set health low so a single bullet kills the target (avoids hurtResistantTime blocking follow-up damage)
            val desiredHealth = (baseDamage * 0.5f).coerceIn(1.0f, maxHealth - 0.5f)
            health = desiredHealth
        }
        world.spawnEntity(target)
        logOnce(
            "hit-feedback-target",
            "HIT_FEEDBACK_TARGET_READY entityId=${target.entityId} type=${target.javaClass.simpleName} gun=$regularGunId health=${"%.2f".format(target.health)} pos=${"%.2f".format(target.posX)},${"%.2f".format(target.posY)},${"%.2f".format(target.posZ)}",
        )
    }

    private fun parseFireMode(rawValue: String): FireMode =
        runCatching { FireMode.valueOf(rawValue.uppercase()) }.getOrDefault(FireMode.UNKNOWN)

    @SubscribeEvent
    fun onPlayerLoggedIn(event: PlayerEvent.PlayerLoggedInEvent) {
        if (!enabled) {
            return
        }
        val player = event.player as? EntityPlayerMP ?: return
        if (!player.server.isSinglePlayer) {
            logOnce("non-singleplayer", "FAIL reason=focused_smoke_requires_integrated_server")
            return
        }
        rememberFocusedPlayer(player)
        val plan = currentPlan() ?: return
        prepareSmokeGear(player, plan)
    }

    @SubscribeEvent
    fun onGunShoot(event: GunShootEvent) {
        if (!enabled || !matchesFocusedPlayer(event.shooter.uniqueID)) {
            return
        }
        val gunId = IGun.getIGunOrNull(event.gunItemStack)?.getGunId(event.gunItemStack) ?: return
        logOnce("shoot-${event.logicalSide}-$gunId", "GUN_SHOOT side=${event.logicalSide} gun=$gunId")
    }

    @SubscribeEvent
    fun onGunFire(event: GunFireEvent) {
        if (!enabled || !matchesFocusedPlayer(event.shooter.uniqueID)) {
            return
        }
        val gunId = IGun.getIGunOrNull(event.gunItemStack)?.getGunId(event.gunItemStack) ?: return
        logOnce("fire-${event.logicalSide}-$gunId", "GUN_FIRE side=${event.logicalSide} gun=$gunId")
    }

    @SubscribeEvent
    fun onEntityJoinWorld(event: EntityJoinWorldEvent) {
        if (!enabled || event.world.isRemote) {
            return
        }
        val bullet = event.entity as? EntityKineticBullet ?: return
        if (!matchesFocusedPlayer(bullet.thrower?.uniqueID)) {
            return
        }
        val plan = plannedScenario ?: return
        val gunId = bullet.gunId
        val explosive = plannedScenario?.explosiveGunId == gunId || GunDataAccessor.getGunData(gunId)?.bulletData?.explosionData?.explode == true
        if (plan.regularGunId == gunId) {
            regularProjectileObserved = true
            regularGunFireCount++
            logOnce("regular-projectile", "REGULAR_PROJECTILE_OBSERVED gun=$gunId side=SERVER")
        }
        if (explosive) {
            explosiveProjectileObserved = true
            explosiveProjectileSeenAtMs = System.currentTimeMillis()
            logOnce("explosive-projectile", "EXPLOSIVE_PROJECTILE_OBSERVED gun=$gunId side=SERVER")
        }
        maybeLogPass()
    }

    @SubscribeEvent
    fun onExplosionStart(event: ExplosionEvent.Start) {
        if (!enabled || event.world.isRemote) {
            return
        }
        if (!explosiveProjectileObserved) {
            return
        }
        if (System.currentTimeMillis() - explosiveProjectileSeenAtMs > 15_000L) {
            return
        }
        explosionObserved = true
        logOnce("explosion-start", "EXPLOSION_OBSERVED side=SERVER x=${"%.2f".format(event.explosion.position.x)} y=${"%.2f".format(event.explosion.position.y)} z=${"%.2f".format(event.explosion.position.z)}")
        maybeLogPass()
    }
}

private fun JsonObject.safeObject(key: String): JsonObject? =
    get(key)?.takeIf(JsonElement::isJsonObject)?.asJsonObject

private fun JsonObject.safeString(key: String): String? =
    get(key)
        ?.takeIf(JsonElement::isJsonPrimitive)
        ?.asString
        ?.takeIf(String::isNotBlank)

private fun JsonObject.safeBoolean(key: String, defaultValue: Boolean = false): Boolean =
    get(key)
        ?.takeIf { !it.isJsonNull }
        ?.asBoolean
        ?: defaultValue

private fun JsonObject.safeResourceLocation(key: String): ResourceLocation? =
    safeString(key)?.let { raw ->
        runCatching { ResourceLocation(raw) }.getOrNull()
    }

private fun JsonObject.jsonArraySize(key: String): Int =
    getAsJsonArray(key)?.size() ?: 0
