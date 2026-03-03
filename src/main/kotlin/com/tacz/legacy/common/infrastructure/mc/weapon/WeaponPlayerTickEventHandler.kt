package com.tacz.legacy.common.infrastructure.mc.weapon

import com.tacz.legacy.common.application.port.DistanceDamagePairDto
import com.tacz.legacy.common.application.port.Vec3d
import com.tacz.legacy.common.application.gunpack.GunDisplayDefinition
import com.tacz.legacy.common.application.gunpack.GunDisplayRuntime
import com.tacz.legacy.common.application.gunpack.GunDisplayStateMachineSemantics
import com.tacz.legacy.common.application.gunpack.ShellEjectTimingProfile
import com.tacz.legacy.common.application.weapon.WeaponAutoSessionOrchestrator
import com.tacz.legacy.common.application.weapon.WeaponBehaviorConfig
import com.tacz.legacy.common.application.weapon.WeaponBehaviorResult
import com.tacz.legacy.common.application.weapon.WeaponInaccuracyProfile
import com.tacz.legacy.common.application.weapon.WeaponTickContext
import com.tacz.legacy.common.application.weapon.WeaponRuntime
import com.tacz.legacy.client.input.WeaponAimInputStateRegistry
import com.tacz.legacy.common.infrastructure.mc.network.LegacyNetworkHandler
import com.tacz.legacy.common.infrastructure.mc.network.PacketWeaponInput
import com.tacz.legacy.common.domain.weapon.WeaponInput
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraftforge.event.entity.player.AttackEntityEvent
import net.minecraftforge.event.entity.player.PlayerInteractEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.PlayerEvent
import net.minecraftforge.fml.common.gameevent.TickEvent

public class WeaponPlayerTickEventHandler(
    private val orchestrator: WeaponAutoSessionOrchestrator,
    private val context: WeaponMcExecutionContext
) {

    private val lastSyncedSignatureBySessionId: MutableMap<String, Int> = linkedMapOf()
    private val lastSyncedTickBySessionId: MutableMap<String, Long> = linkedMapOf()

    @SubscribeEvent
    public fun onPlayerLoggedIn(event: PlayerEvent.PlayerLoggedInEvent) {
        if (event.player.world.isRemote || event.player !is EntityPlayerMP) {
            return
        }

        val player = event.player as EntityPlayerMP
        val sessionId = sessionId(player.uniqueID.toString(), isRemote = false)
        PacketWeaponInput.clearTrackedInputState(sessionId)
        WeaponAimInputStateRegistry.clearSession(sessionId)
        LegacyNetworkHandler.sendWeaponBaseTimestampSyncToClient(player)
    }

    @SubscribeEvent
    public fun onPlayerTick(event: TickEvent.PlayerTickEvent) {
        if (event.phase != TickEvent.Phase.END) {
            return
        }

        val player = event.player
        val isRemote = player.world.isRemote
        val sessionId = sessionId(player.uniqueID.toString(), isRemote)
        val gunId = currentGunId(player)
        val behaviorContext = resolveBehaviorContext(gunId, player, sessionId)

        val tickResult = context.withPlayer(player) {
            val look = player.lookVec
            val muzzlePosition = resolveMuzzlePosition(player)
            orchestrator.onTick(
                WeaponTickContext(
                    sessionId = sessionId,
                    currentGunId = gunId,
                    muzzlePosition = muzzlePosition,
                    shotDirection = Vec3d(look.x, look.y, look.z),
                    behaviorConfig = behaviorContext.config
                )
            )
        }

        updateAnimationRuntime(
            worldIsRemote = player.world.isRemote,
            sessionId = sessionId,
            gunId = gunId,
            behaviorResult = tickResult,
            clipDurationOverridesMillis = behaviorContext.animationClipDurationsMillis,
            reloadTicks = behaviorContext.reloadTicks,
            preferBoltCycleAfterFire = behaviorContext.preferBoltCycleAfterFire,
            shellEjectPlan = behaviorContext.shellEjectPlan
        )

        syncAuthoritativeSessionIfNeeded(player, sessionId)
    }

    @SubscribeEvent
    public fun onPlayerLoggedOut(event: PlayerEvent.PlayerLoggedOutEvent) {
        val player = event.player
        val isRemote = player.world.isRemote
        val sessionId = sessionId(player.uniqueID.toString(), isRemote)
        orchestrator.onSessionEnd(sessionId)
        WeaponAnimationRuntimeRegistry.removeSession(sessionId)

        if (!isRemote && player is EntityPlayerMP) {
            LegacyNetworkHandler.sendWeaponSessionClearToClient(player, sessionId)
        }
        PacketWeaponInput.clearTrackedInputState(sessionId)
        WeaponAimInputStateRegistry.clearSession(sessionId)
        if (isRemote) {
            WeaponAimInputStateRegistry.clearSession(WeaponRuntimeMcBridge.clientSessionIdForPlayer(player.uniqueID.toString()))
        }
        lastSyncedSignatureBySessionId.remove(sessionId)
        lastSyncedTickBySessionId.remove(sessionId)
    }

    @SubscribeEvent
    public fun onAttackEntity(event: AttackEntityEvent) {
        val player = event.entityPlayer
        if (!shouldHandlePrimaryTrigger(player.world.isRemote)) {
            return
        }
        handleTriggerTap(player)
    }

    @SubscribeEvent
    public fun onLeftClickBlock(event: PlayerInteractEvent.LeftClickBlock) {
        val player = event.entityPlayer
        if (!shouldHandlePrimaryTrigger(player.world.isRemote)) {
            return
        }
        handleTriggerTap(player)
    }

    @SubscribeEvent
    public fun onLeftClickEmpty(event: PlayerInteractEvent.LeftClickEmpty) {
        val player = event.entityPlayer
        if (!shouldHandlePrimaryTrigger(player.world.isRemote)) {
            return
        }
        handleTriggerTap(player)
    }

    @SubscribeEvent
    public fun onRightClickItem(event: PlayerInteractEvent.RightClickItem) {
        val player = event.entityPlayer
        if (!shouldHandleReload(player.world.isRemote, player.isSneaking)) {
            return
        }
        WeaponRuntimeMcBridge.dispatchClientInput(player, WeaponInput.ReloadPressed)
    }

    private fun handleTriggerTap(player: EntityPlayer) {
        dispatchInput(player, WeaponInput.TriggerPressed)
        dispatchInput(player, WeaponInput.TriggerReleased)
    }

    internal fun dispatchInput(player: EntityPlayer, input: WeaponInput): WeaponBehaviorResult? {
        val gunId = currentGunId(player)
        val isRemote = player.world.isRemote
        val sessionId = sessionId(player.uniqueID.toString(), isRemote)
        val behaviorContext = resolveBehaviorContext(gunId, player, sessionId)
        val look = player.lookVec
        val muzzlePosition = resolveMuzzlePosition(player)

        val result = context.withPlayer(player) {
            orchestrator.onInput(
                context = WeaponTickContext(
                    sessionId = sessionId,
                    currentGunId = gunId,
                    muzzlePosition = muzzlePosition,
                    shotDirection = Vec3d(look.x, look.y, look.z),
                    behaviorConfig = behaviorContext.config
                ),
                input = input
            )
        }

        updateAnimationRuntime(
            worldIsRemote = player.world.isRemote,
            sessionId = sessionId,
            gunId = gunId,
            behaviorResult = result,
            clipDurationOverridesMillis = behaviorContext.animationClipDurationsMillis,
            reloadTicks = behaviorContext.reloadTicks,
            preferBoltCycleAfterFire = behaviorContext.preferBoltCycleAfterFire,
            shellEjectPlan = behaviorContext.shellEjectPlan
        )
        return result
    }

    private fun resolveBehaviorContext(
        gunId: String?,
        player: EntityPlayer,
        sessionId: String
    ): ResolvedBehaviorContext {
        val normalizedGunId = gunId?.trim()?.lowercase()?.ifBlank { null } ?: return WeaponBehaviorConfig()
            .let { config ->
                ResolvedBehaviorContext(
                    config = config,
                    reloadTicks = null,
                    animationClipDurationsMillis = emptyMap(),
                    preferBoltCycleAfterFire = false,
                    shellEjectPlan = WeaponAnimationShellEjectPlan()
                )
            }

        val fallback = WeaponBehaviorConfig()
        val weaponDefinition = WeaponRuntime.registry().snapshot().findDefinition(normalizedGunId)
        val displayDefinition = GunDisplayRuntime.registry().snapshot().findDefinition(normalizedGunId)
        val gunScriptParams = weaponDefinition?.scriptParams.orEmpty()
        val preferBoltCycleAfterFire = shouldPreferBoltCycleAfterFire(
            displayDefinition = displayDefinition,
            gunScriptParams = gunScriptParams
        )
        val inaccuracyDegrees = resolveBulletInaccuracyDegrees(
            player = player,
            sessionId = sessionId,
            profile = weaponDefinition?.ballistics?.inaccuracy,
            fallback = fallback.bulletInaccuracyDegrees
        )

        return ResolvedBehaviorContext(
            preferBoltCycleAfterFire = preferBoltCycleAfterFire,
            config = fallback.copy(
                shootSoundId = displayDefinition?.shootSoundId ?: fallback.shootSoundId,
                dryFireSoundId = displayDefinition?.dryFireSoundId ?: fallback.dryFireSoundId,
                inspectSoundId = displayDefinition?.inspectSoundId ?: fallback.inspectSoundId,
                inspectEmptySoundId = displayDefinition?.inspectEmptySoundId ?: fallback.inspectEmptySoundId,
                reloadEmptySoundId = displayDefinition?.reloadEmptySoundId ?: fallback.reloadEmptySoundId,
                reloadTacticalSoundId = displayDefinition?.reloadTacticalSoundId ?: fallback.reloadTacticalSoundId,
                maxDistance = weaponDefinition?.spec?.maxDistance ?: fallback.maxDistance,
                bulletSpeed = weaponDefinition?.ballistics?.speed ?: fallback.bulletSpeed,
                bulletGravity = weaponDefinition?.ballistics?.gravity ?: fallback.bulletGravity,
                bulletFriction = weaponDefinition?.ballistics?.friction ?: fallback.bulletFriction,
                bulletDamage = weaponDefinition?.ballistics?.damage ?: fallback.bulletDamage,
                bulletLifeTicks = weaponDefinition?.ballistics?.lifetimeTicks ?: fallback.bulletLifeTicks,
                bulletPierce = weaponDefinition?.ballistics?.pierce ?: fallback.bulletPierce,
                bulletPelletCount = weaponDefinition?.ballistics?.pelletCount ?: fallback.bulletPelletCount,
                bulletInaccuracyDegrees = inaccuracyDegrees,
                bulletArmorIgnore = weaponDefinition?.ballistics?.armorIgnore ?: fallback.bulletArmorIgnore,
                bulletHeadShotMultiplier = weaponDefinition?.ballistics?.headShotMultiplier ?: fallback.bulletHeadShotMultiplier,
                bulletDamageAdjust = weaponDefinition?.ballistics?.damageAdjust?.map {
                    DistanceDamagePairDto(distance = it.distance, damage = it.damage)
                } ?: fallback.bulletDamageAdjust,
                fireSoundPitchJitter = FIRE_SOUND_PITCH_JITTER
            ),
            reloadTicks = weaponDefinition?.spec?.reloadTicks,
            animationClipDurationsMillis = resolveAnimationClipDurationOverrides(displayDefinition),
            shellEjectPlan = resolveShellEjectPlan(
                displayDefinition = displayDefinition,
                gunScriptParams = gunScriptParams
            )
        )
    }

    private fun resolveShellEjectPlan(
        displayDefinition: GunDisplayDefinition?,
        gunScriptParams: Map<String, Float>
    ): WeaponAnimationShellEjectPlan {
        val timingProfile: ShellEjectTimingProfile = GunDisplayStateMachineSemantics.resolveShellEjectTimingProfile(
            displayDefinition = displayDefinition,
            gunScriptParams = gunScriptParams
        )
        return WeaponAnimationShellEjectPlan(
            fireTriggerMillis = timingProfile.fireTriggerMillis,
            reloadTriggerMillis = timingProfile.reloadTriggerMillis,
            boltTriggerMillis = timingProfile.boltTriggerMillis
        )
    }

    internal fun shouldPreferBoltCycleAfterFire(displayDefinition: GunDisplayDefinition?): Boolean {
        return shouldPreferBoltCycleAfterFire(displayDefinition, emptyMap())
    }

    internal fun shouldPreferBoltCycleAfterFire(
        displayDefinition: GunDisplayDefinition?,
        gunScriptParams: Map<String, Float>
    ): Boolean {
        return GunDisplayStateMachineSemantics.shouldPreferBoltCycleAfterFire(
            displayDefinition = displayDefinition,
            gunScriptParams = gunScriptParams
        )
    }

    private fun resolveBulletInaccuracyDegrees(
        player: EntityPlayer,
        sessionId: String,
        profile: WeaponInaccuracyProfile?,
        fallback: Float
    ): Float {
        if (profile == null) {
            return fallback
        }

        val aiming = WeaponAimInputStateRegistry.resolve(sessionId) == true
        if (aiming) {
            return profile.aim.coerceAtLeast(0.0f)
        }

        if (player.isPlayerSleeping) {
            return profile.lie.coerceAtLeast(0.0f)
        }

        if (player.isSneaking) {
            return profile.sneak.coerceAtLeast(0.0f)
        }

        val horizontalSpeedSq = player.motionX * player.motionX + player.motionZ * player.motionZ
        if (horizontalSpeedSq > MOVING_SPEED_EPSILON_SQ || player.isSprinting) {
            return profile.move.coerceAtLeast(0.0f)
        }

        return profile.stand.coerceAtLeast(0.0f)
    }

    private fun resolveAnimationClipDurationOverrides(
        displayDefinition: GunDisplayDefinition?
    ): Map<WeaponAnimationClipType, Long> {
        if (displayDefinition == null) {
            return emptyMap()
        }

        val clipLengths = displayDefinition.animationClipLengthsMillis ?: return emptyMap()
        val out = linkedMapOf<WeaponAnimationClipType, Long>()

        resolveClipLength(clipLengths, resolveAnimationClipName(displayDefinition, WeaponAnimationClipType.FIRE))
            ?.let { out[WeaponAnimationClipType.FIRE] = it }
        resolveClipLength(clipLengths, resolveAnimationClipName(displayDefinition, WeaponAnimationClipType.RELOAD))
            ?.let { out[WeaponAnimationClipType.RELOAD] = it }
        resolveClipLength(clipLengths, resolveAnimationClipName(displayDefinition, WeaponAnimationClipType.INSPECT))
            ?.let { out[WeaponAnimationClipType.INSPECT] = it }
        resolveClipLength(clipLengths, resolveAnimationClipName(displayDefinition, WeaponAnimationClipType.DRY_FIRE))
            ?.let { out[WeaponAnimationClipType.DRY_FIRE] = it }
        resolveClipLength(clipLengths, resolveAnimationClipName(displayDefinition, WeaponAnimationClipType.DRAW))
            ?.let { out[WeaponAnimationClipType.DRAW] = it }
        resolveClipLength(clipLengths, resolveAnimationClipName(displayDefinition, WeaponAnimationClipType.PUT_AWAY))
            ?.let { out[WeaponAnimationClipType.PUT_AWAY] = it }
        resolveClipLength(clipLengths, resolveAnimationClipName(displayDefinition, WeaponAnimationClipType.WALK))
            ?.let { out[WeaponAnimationClipType.WALK] = it }
        resolveClipLength(clipLengths, resolveAnimationClipName(displayDefinition, WeaponAnimationClipType.RUN))
            ?.let { out[WeaponAnimationClipType.RUN] = it }
        resolveClipLength(clipLengths, resolveAnimationClipName(displayDefinition, WeaponAnimationClipType.AIM))
            ?.let { out[WeaponAnimationClipType.AIM] = it }
        resolveClipLength(clipLengths, resolveAnimationClipName(displayDefinition, WeaponAnimationClipType.BOLT))
            ?.let { out[WeaponAnimationClipType.BOLT] = it }

        return out.toMap()
    }

    private fun resolveAnimationClipName(
        displayDefinition: GunDisplayDefinition,
        clipType: WeaponAnimationClipType
    ): String? {
        val explicit = when (clipType) {
            WeaponAnimationClipType.IDLE -> displayDefinition.animationIdleClipName
            WeaponAnimationClipType.FIRE -> displayDefinition.animationFireClipName
            WeaponAnimationClipType.RELOAD -> displayDefinition.animationReloadClipName
            WeaponAnimationClipType.INSPECT -> displayDefinition.animationInspectClipName
            WeaponAnimationClipType.DRY_FIRE -> displayDefinition.animationDryFireClipName
            WeaponAnimationClipType.DRAW -> displayDefinition.animationDrawClipName
            WeaponAnimationClipType.PUT_AWAY -> displayDefinition.animationPutAwayClipName
            WeaponAnimationClipType.WALK -> displayDefinition.animationWalkClipName
            WeaponAnimationClipType.RUN -> displayDefinition.animationRunClipName
            WeaponAnimationClipType.AIM -> displayDefinition.animationAimClipName
            WeaponAnimationClipType.BOLT -> displayDefinition.animationBoltClipName
        }?.trim()?.ifBlank { null }

        if (explicit != null) {
            return explicit
        }

        val clipNames = displayDefinition.animationClipNames.orEmpty()
        if (clipNames.isEmpty()) {
            return null
        }

        return when (clipType) {
            WeaponAnimationClipType.IDLE -> selectClipName(
                clipNames,
                preferredKeywords = listOf("idle")
            )

            WeaponAnimationClipType.FIRE -> selectClipName(
                clipNames,
                preferredKeywords = listOf("shoot", "fire", "shot", "recoil"),
                excludedKeywords = setOf("dry", "empty")
            )

            WeaponAnimationClipType.RELOAD -> selectClipName(
                clipNames,
                preferredKeywords = listOf("reload_tactical", "reload_empty", "reload")
            )

            WeaponAnimationClipType.INSPECT -> selectClipName(
                clipNames,
                preferredKeywords = listOf("inspect")
            )

            WeaponAnimationClipType.DRY_FIRE -> selectClipName(
                clipNames,
                preferredKeywords = listOf("dry_fire", "dry", "empty_click", "no_ammo")
            )

            WeaponAnimationClipType.DRAW -> selectClipName(
                clipNames,
                preferredKeywords = listOf("draw", "equip", "deploy", "pull_out")
            )

            WeaponAnimationClipType.PUT_AWAY -> selectClipName(
                clipNames,
                preferredKeywords = listOf("put_away", "putaway", "holster", "withdraw")
            )

            WeaponAnimationClipType.WALK -> selectClipName(
                clipNames,
                preferredKeywords = listOf("walk", "move")
            )

            WeaponAnimationClipType.RUN -> selectClipName(
                clipNames,
                preferredKeywords = listOf("run", "sprint")
            )

            WeaponAnimationClipType.AIM -> selectClipName(
                clipNames,
                preferredKeywords = listOf("aim", "ads", "sight", "aiming"),
                excludedKeywords = setOf("fire", "shoot", "reload")
            )

            WeaponAnimationClipType.BOLT -> selectClipName(
                clipNames,
                preferredKeywords = listOf("bolt", "blot", "pull_bolt", "charge")
            )
        }
    }

    private fun selectClipName(
        clipNames: List<String>,
        preferredKeywords: List<String>,
        excludedKeywords: Set<String> = emptySet()
    ): String? {
        val normalizedExcluded = excludedKeywords.map(::normalizeClipToken).toSet()
        val normalized = clipNames.map { name ->
            name to normalizeClipToken(name)
        }

        preferredKeywords.forEach { keywordRaw ->
            val keyword = normalizeClipToken(keywordRaw)

            normalized.firstOrNull { (_, token) ->
                (token == keyword || token.endsWith("_$keyword")) && normalizedExcluded.none { token.contains(it) }
            }?.first?.let { return it }

            normalized.firstOrNull { (_, token) ->
                token.contains(keyword) && normalizedExcluded.none { token.contains(it) }
            }?.first?.let { return it }
        }

        return null
    }

    private fun normalizeClipToken(raw: String): String =
        raw.trim()
            .lowercase()
            .map { ch -> if (ch.isLetterOrDigit()) ch else '_' }
            .joinToString(separator = "")
            .replace("__+".toRegex(), "_")
            .trim('_')

    private fun resolveClipLength(clipLengths: Map<String, Long>, clipName: String?): Long? {
        val normalized = clipName?.trim()?.ifBlank { null } ?: return null
        return clipLengths[normalized]?.takeIf { it > 0L }
    }

    private fun updateAnimationRuntime(
        worldIsRemote: Boolean,
        sessionId: String,
        gunId: String?,
        behaviorResult: WeaponBehaviorResult?,
        clipDurationOverridesMillis: Map<WeaponAnimationClipType, Long>,
        reloadTicks: Int?,
        preferBoltCycleAfterFire: Boolean,
        shellEjectPlan: WeaponAnimationShellEjectPlan
    ) {
        if (!worldIsRemote) {
            return
        }

        val normalizedGunId = gunId?.trim()?.lowercase()?.ifBlank { null }
        if (normalizedGunId == null) {
            WeaponAnimationRuntimeRegistry.removeSession(sessionId)
            return
        }

        val result = behaviorResult ?: return
        WeaponAnimationRuntimeRegistry.observeBehavior(
            sessionId = sessionId,
            gunId = normalizedGunId,
            result = result,
            clipDurationOverridesMillis = clipDurationOverridesMillis,
            reloadTicks = reloadTicks,
            preferBoltCycleAfterFire = preferBoltCycleAfterFire,
            shellEjectPlan = shellEjectPlan
        )
    }

    private fun currentGunId(player: EntityPlayer): String? =
        player.heldItemMainhand
            .takeUnless { it.isEmpty }
            ?.item
            ?.registryName
            ?.toString()
            ?.substringAfter(':')
            ?.ifBlank { null }

    private fun sessionId(playerUuid: String, isRemote: Boolean): String =
        if (isRemote) "player:$playerUuid:client" else "player:$playerUuid"

    private fun resolveMuzzlePosition(player: EntityPlayer): Vec3d {
        val interpolatedX = player.prevPosX + (player.posX - player.prevPosX) * 0.5
        val interpolatedY = player.prevPosY + (player.posY - player.prevPosY) * 0.5
        val interpolatedZ = player.prevPosZ + (player.posZ - player.prevPosZ) * 0.5
        return Vec3d(
            x = interpolatedX,
            y = interpolatedY + player.eyeHeight.toDouble(),
            z = interpolatedZ
        )
    }

    internal fun shouldHandlePrimaryTrigger(worldIsRemote: Boolean): Boolean {
        @Suppress("UNUSED_PARAMETER")
        val unused = worldIsRemote
        // 开火输入改为客户端按住状态同步（WeaponKeyInputEventHandler），
        // 避免 LeftClick 事件中的“按下+立刻抬起”干扰 AUTO/BURST 连发。
        return false
    }

    internal fun shouldHandleReload(worldIsRemote: Boolean, isSneaking: Boolean): Boolean =
        worldIsRemote && isSneaking

    private fun syncAuthoritativeSessionIfNeeded(player: EntityPlayer, sessionId: String) {
        if (player.world.isRemote || player !is EntityPlayerMP) {
            return
        }

        val service = WeaponRuntimeMcBridge.sessionServiceOrNull() ?: return
        val debugSnapshot = service.debugSnapshot(sessionId)
        if (debugSnapshot == null) {
            PacketWeaponInput.clearTrackedInputState(sessionId)
            if (lastSyncedSignatureBySessionId.remove(sessionId) != null ||
                lastSyncedTickBySessionId.remove(sessionId) != null
            ) {
                LegacyNetworkHandler.sendWeaponSessionClearToClient(player, sessionId)
            }
            return
        }

        val currentTick = player.world.totalWorldTime
        val signature = buildSyncSignature(debugSnapshot)
        val lastSignature = lastSyncedSignatureBySessionId[sessionId]
        val lastTick = lastSyncedTickBySessionId[sessionId]

        if (!shouldEmitAuthoritativeSync(lastSignature, signature, lastTick, currentTick)) {
            return
        }

        LegacyNetworkHandler.sendWeaponSessionSyncToClient(
            player = player,
            sessionId = sessionId,
            debugSnapshot = debugSnapshot
        )
        lastSyncedSignatureBySessionId[sessionId] = signature
        lastSyncedTickBySessionId[sessionId] = currentTick
    }

    internal fun buildSyncSignature(
        debugSnapshot: com.tacz.legacy.common.application.weapon.WeaponSessionDebugSnapshot
    ): Int {
        val snapshot = debugSnapshot.snapshot
        var signature = 17
        signature = signature * 31 + debugSnapshot.sourceId.hashCode()
        signature = signature * 31 + debugSnapshot.gunId.hashCode()
        signature = signature * 31 + snapshot.state.ordinal
        signature = signature * 31 + snapshot.ammoInMagazine
        signature = signature * 31 + snapshot.ammoReserve
        signature = signature * 31 + snapshot.reloadTicksRemaining
        signature = signature * 31 + snapshot.cooldownTicksRemaining
        signature = signature * 31 + snapshot.totalShotsFired
        signature = signature * 31 + if (snapshot.isTriggerHeld) 1 else 0
        signature = signature * 31 + if (snapshot.semiLocked) 1 else 0
        signature = signature * 31 + snapshot.burstShotsRemaining
        return signature
    }

    internal fun shouldEmitAuthoritativeSync(
        lastSignature: Int?,
        currentSignature: Int,
        lastSyncedTick: Long?,
        currentTick: Long,
        intervalTicks: Long = SESSION_SYNC_RESEND_INTERVAL_TICKS
    ): Boolean {
        val changed = lastSignature == null || lastSignature != currentSignature
        if (changed) {
            return true
        }
        if (lastSyncedTick == null) {
            return true
        }
        return (currentTick - lastSyncedTick) >= intervalTicks
    }

    private data class ResolvedBehaviorContext(
        val config: WeaponBehaviorConfig,
        val reloadTicks: Int?,
        val animationClipDurationsMillis: Map<WeaponAnimationClipType, Long>,
        val preferBoltCycleAfterFire: Boolean,
        val shellEjectPlan: WeaponAnimationShellEjectPlan
    )

    private companion object {
        private const val SESSION_SYNC_RESEND_INTERVAL_TICKS: Long = 20L
        private const val FIRE_SOUND_PITCH_JITTER: Float = 0.08f
        private const val MOVING_SPEED_EPSILON_SQ: Double = 0.0025
    }

}