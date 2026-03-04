package com.tacz.legacy.common.infrastructure.mc.weapon

import com.tacz.legacy.common.application.port.DistanceDamagePairDto
import com.tacz.legacy.common.application.port.ExplosionDto
import com.tacz.legacy.common.application.port.Vec3d
import com.tacz.legacy.common.application.gunpack.GunDisplayDefinition
import com.tacz.legacy.common.application.gunpack.GunDisplayRuntime
import com.tacz.legacy.common.application.gunpack.GunDisplayStateMachineSemantics
import com.tacz.legacy.common.application.gunpack.ShellEjectTimingProfile
import com.tacz.legacy.common.application.weapon.WeaponAutoSessionOrchestrator
import com.tacz.legacy.common.application.weapon.WeaponBehaviorConfig
import com.tacz.legacy.common.application.weapon.WeaponBehaviorResult
import com.tacz.legacy.common.application.weapon.WeaponInaccuracyProfile
import com.tacz.legacy.common.application.weapon.WeaponLuaScriptEngine
import com.tacz.legacy.common.application.weapon.WeaponLuaAnimationStateMachineRuntime
import com.tacz.legacy.common.application.weapon.WeaponLuaScriptRuntime
import com.tacz.legacy.common.application.weapon.WeaponTickContext
import com.tacz.legacy.common.application.weapon.WeaponRuntime
import com.tacz.legacy.client.input.WeaponAimInputStateRegistry
import com.tacz.legacy.common.infrastructure.mc.network.LegacyNetworkHandler
import com.tacz.legacy.common.infrastructure.mc.network.PacketWeaponInput
import com.tacz.legacy.common.infrastructure.mc.registry.item.LegacyGunItem
import com.tacz.legacy.common.domain.weapon.WeaponInput
import com.tacz.legacy.common.domain.event.GunFireEvent
import com.tacz.legacy.common.domain.event.GunShootEvent
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.item.ItemStack
import net.minecraftforge.event.entity.player.AttackEntityEvent
import net.minecraftforge.event.entity.player.PlayerInteractEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.PlayerEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import net.minecraftforge.common.MinecraftForge

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

        val tickContext = WeaponTickContext(
            sessionId = sessionId,
            currentGunId = gunId,
            muzzlePosition = resolveMuzzlePosition(player),
            shotDirection = run {
                val look = player.lookVec
                Vec3d(look.x, look.y, look.z)
            },
            initialAmmoInMagazine = behaviorContext.initialAmmoInMagazine,
            initialAmmoReserve = behaviorContext.initialAmmoReserve,
            behaviorConfig = behaviorContext.config
        )

        val tickResult = context.withPlayer(player) {
            orchestrator.onTick(tickContext)
        }

        if (tickResult?.step?.shotFired == true) {
            MinecraftForge.EVENT_BUS.post(GunFireEvent(shooter = player, gunId = gunId))
        }

        syncHeldGunStackFromBehaviorResult(player, gunId, tickResult)
        enforceCreativeInfiniteAmmoIfNeeded(player, sessionId, gunId)

        val animationRuntimePath = resolveAnimationRuntimePath(
            worldIsRemote = player.world.isRemote,
            sessionId = sessionId,
            gunId = gunId,
            displayDefinition = behaviorContext.displayDefinition,
            gunScriptParams = behaviorContext.gunScriptParams,
            behaviorResult = tickResult
        )

        updateAnimationRuntime(
            worldIsRemote = player.world.isRemote,
            sessionId = sessionId,
            gunId = gunId,
            behaviorResult = tickResult,
            clipDurationOverridesMillis = behaviorContext.animationClipDurationsMillis,
            reloadTicks = behaviorContext.reloadTicks,
            preferBoltCycleAfterFire = behaviorContext.preferBoltCycleAfterFire,
            shellEjectPlan = behaviorContext.shellEjectPlan,
            preferredClip = animationRuntimePath.preferredClip,
            clipSource = animationRuntimePath.clipSource
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
        WeaponLuaAnimationStateMachineRuntime.clearSession(sessionId)

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
        val inputContext = WeaponTickContext(
            sessionId = sessionId,
            currentGunId = gunId,
            muzzlePosition = muzzlePosition,
            shotDirection = Vec3d(look.x, look.y, look.z),
            initialAmmoInMagazine = behaviorContext.initialAmmoInMagazine,
            initialAmmoReserve = behaviorContext.initialAmmoReserve,
            behaviorConfig = behaviorContext.config
        )

        val result = context.withPlayer(player) {
            orchestrator.onInput(
                context = inputContext,
                input = input
            )
        }

        if (result?.step?.shotFired == true) {
            if (input == WeaponInput.TriggerPressed) {
                MinecraftForge.EVENT_BUS.post(GunShootEvent(shooter = player, gunId = gunId))
            }
            MinecraftForge.EVENT_BUS.post(GunFireEvent(shooter = player, gunId = gunId))
        }

        syncHeldGunStackFromBehaviorResult(player, gunId, result)
        enforceCreativeInfiniteAmmoIfNeeded(player, sessionId, gunId)

        val animationRuntimePath = resolveAnimationRuntimePath(
            worldIsRemote = player.world.isRemote,
            sessionId = sessionId,
            gunId = gunId,
            displayDefinition = behaviorContext.displayDefinition,
            gunScriptParams = behaviorContext.gunScriptParams,
            behaviorResult = result
        )

        updateAnimationRuntime(
            worldIsRemote = player.world.isRemote,
            sessionId = sessionId,
            gunId = gunId,
            behaviorResult = result,
            clipDurationOverridesMillis = behaviorContext.animationClipDurationsMillis,
            reloadTicks = behaviorContext.reloadTicks,
            preferBoltCycleAfterFire = behaviorContext.preferBoltCycleAfterFire,
            shellEjectPlan = behaviorContext.shellEjectPlan,
            preferredClip = animationRuntimePath.preferredClip,
            clipSource = animationRuntimePath.clipSource
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
                    initialAmmoInMagazine = null,
                    initialAmmoReserve = null,
                    reloadTicks = null,
                    animationClipDurationsMillis = emptyMap(),
                    preferBoltCycleAfterFire = false,
                    shellEjectPlan = WeaponAnimationShellEjectPlan(),
                    displayDefinition = null,
                    gunScriptParams = emptyMap()
                )
            }

        val fallback = WeaponBehaviorConfig()
        val weaponDefinition = WeaponRuntime.registry().snapshot().findDefinition(normalizedGunId)
        val displayDefinition = GunDisplayRuntime.registry().snapshot().findDefinition(normalizedGunId)
        val heldGunStack = resolveHeldGunStack(player, normalizedGunId)
        val attachmentSnapshot = heldGunStack?.let { WeaponItemStackRuntimeData.readAttachmentSnapshot(it) }
            ?: WeaponAttachmentSnapshot()
        val attachmentModifiers = WeaponAttachmentModifierResolver.resolve(attachmentSnapshot)
        val gunScriptParams = weaponDefinition?.scriptParams.orEmpty()

        val initialAmmoInMagazine = heldGunStack?.let {
            WeaponItemStackRuntimeData.readAmmoInMagazine(
                stack = it,
                defaultValue = weaponDefinition?.spec?.magazineSize ?: DEFAULT_MAGAZINE_SIZE
            )
        }
        val initialAmmoReserve = heldGunStack?.let {
            WeaponItemStackRuntimeData.readAmmoReserve(it, 0)
        }

        val scriptHookAdjustments = WeaponLuaScriptEngine.evaluate(
            gunId = normalizedGunId,
            displayDefinition = displayDefinition,
            scriptParams = gunScriptParams,
            ammoInMagazine = initialAmmoInMagazine ?: (weaponDefinition?.spec?.magazineSize ?: DEFAULT_MAGAZINE_SIZE),
            ammoReserve = initialAmmoReserve ?: 0
        )?.ballisticAdjustments

        val luaAdjustments = WeaponLuaScriptRuntime.combineBallisticAdjustments(
            base = WeaponLuaScriptRuntime.resolveBallisticAdjustments(gunScriptParams),
            overlay = scriptHookAdjustments
        )

        val preferBoltCycleAfterFire = shouldPreferBoltCycleAfterFire(
            displayDefinition = displayDefinition,
            gunScriptParams = gunScriptParams
        )

        val adjustedProfile = weaponDefinition?.ballistics?.inaccuracy?.let { profile ->
            profile.copy(
                stand = (profile.stand + attachmentModifiers.standInaccuracyAdd).coerceAtLeast(0f),
                move = (profile.move + attachmentModifiers.moveInaccuracyAdd).coerceAtLeast(0f),
                sneak = (profile.sneak + attachmentModifiers.sneakInaccuracyAdd).coerceAtLeast(0f),
                lie = (profile.lie + attachmentModifiers.lieInaccuracyAdd).coerceAtLeast(0f),
                aim = (profile.aim + attachmentModifiers.aimInaccuracyAdd).coerceAtLeast(0f)
            )
        }

        val inaccuracyDegrees = resolveBulletInaccuracyDegrees(
            player = player,
            sessionId = sessionId,
            profile = adjustedProfile,
            fallback = fallback.bulletInaccuracyDegrees
        ).times(luaAdjustments.inaccuracyScale).coerceAtLeast(0f)

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
                bulletSpeed = ((weaponDefinition?.ballistics?.speed ?: fallback.bulletSpeed) * luaAdjustments.speedScale)
                    .coerceAtLeast(0.001f),
                bulletGravity = weaponDefinition?.ballistics?.gravity ?: fallback.bulletGravity,
                bulletFriction = weaponDefinition?.ballistics?.friction ?: fallback.bulletFriction,
                bulletDamage = ((weaponDefinition?.ballistics?.damage ?: fallback.bulletDamage) + attachmentModifiers.damageAdd)
                    .times(luaAdjustments.damageScale)
                    .coerceAtLeast(0f),
                bulletLifeTicks = weaponDefinition?.ballistics?.lifetimeTicks ?: fallback.bulletLifeTicks,
                bulletPierce = weaponDefinition?.ballistics?.pierce ?: fallback.bulletPierce,
                bulletPelletCount = weaponDefinition?.ballistics?.pelletCount ?: fallback.bulletPelletCount,
                bulletInaccuracyDegrees = inaccuracyDegrees,
                bulletArmorIgnore = ((weaponDefinition?.ballistics?.armorIgnore ?: fallback.bulletArmorIgnore) + attachmentModifiers.armorIgnoreAdd)
                    .coerceIn(0f, 1f),
                bulletHeadShotMultiplier = ((weaponDefinition?.ballistics?.headShotMultiplier
                    ?: fallback.bulletHeadShotMultiplier) + attachmentModifiers.headShotMultiplierAdd)
                    .coerceAtLeast(0f),
                bulletDamageAdjust = weaponDefinition?.ballistics?.damageAdjust?.map {
                    DistanceDamagePairDto(distance = it.distance, damage = it.damage)
                } ?: fallback.bulletDamageAdjust,
                bulletKnockback = ((weaponDefinition?.ballistics?.knockback ?: fallback.bulletKnockback) + attachmentModifiers.knockbackAdd)
                    .times(luaAdjustments.knockbackScale)
                    .coerceAtLeast(0f),
                bulletIgniteEntity = weaponDefinition?.ballistics?.igniteEntity ?: fallback.bulletIgniteEntity,
                bulletIgniteEntityTime = weaponDefinition?.ballistics?.igniteEntityTime ?: fallback.bulletIgniteEntityTime,
                bulletIgniteBlock = weaponDefinition?.ballistics?.igniteBlock ?: fallback.bulletIgniteBlock,
                bulletExplosion = weaponDefinition?.ballistics?.explosion?.let {
                    ExplosionDto(
                        radius = it.radius,
                        damage = it.damage,
                        knockback = it.knockback,
                        destroyBlock = it.destroyBlock,
                        delaySeconds = it.delaySeconds
                    )
                } ?: fallback.bulletExplosion,
                bulletGunId = weaponDefinition?.gunId,
                fireSoundPitchJitter = FIRE_SOUND_PITCH_JITTER
            ),
            initialAmmoInMagazine = initialAmmoInMagazine,
            initialAmmoReserve = initialAmmoReserve,
            reloadTicks = weaponDefinition?.spec?.reloadTicks,
            animationClipDurationsMillis = resolveAnimationClipDurationOverrides(displayDefinition),
            shellEjectPlan = resolveShellEjectPlan(
                displayDefinition = displayDefinition,
                gunScriptParams = gunScriptParams
            ),
            displayDefinition = displayDefinition,
            gunScriptParams = gunScriptParams
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
        shellEjectPlan: WeaponAnimationShellEjectPlan,
        preferredClip: WeaponAnimationClipType?,
        clipSource: WeaponAnimationClipSource
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
            shellEjectPlan = shellEjectPlan,
            preferredClip = preferredClip,
            clipSource = clipSource
        )
    }

    internal fun resolveAnimationRuntimePath(
        worldIsRemote: Boolean,
        sessionId: String,
        gunId: String?,
        displayDefinition: GunDisplayDefinition?,
        gunScriptParams: Map<String, Float>,
        behaviorResult: WeaponBehaviorResult?,
        luaFeatureEnabled: Boolean = ENABLE_LUA_ANIMATION_STATE_MACHINE
    ): ResolvedAnimationRuntimePath {
        if (!worldIsRemote) {
            return ResolvedAnimationRuntimePath(
                clipSource = WeaponAnimationClipSource.SIGNAL,
                preferredClip = null
            )
        }

        if (!luaFeatureEnabled) {
            WeaponLuaAnimationStateMachineRuntime.clearSession(sessionId)
            return ResolvedAnimationRuntimePath(
                clipSource = WeaponAnimationClipSource.SIGNAL,
                preferredClip = null
            )
        }

        val normalizedGunId = gunId?.trim()?.lowercase()?.ifBlank { null }
        if (normalizedGunId == null || displayDefinition == null || behaviorResult == null) {
            WeaponLuaAnimationStateMachineRuntime.clearSession(sessionId)
            return ResolvedAnimationRuntimePath(
                clipSource = WeaponAnimationClipSource.SIGNAL_FALLBACK,
                preferredClip = null
            )
        }

        val tickResult = WeaponLuaAnimationStateMachineRuntime.tick(
            sessionId = sessionId,
            gunId = normalizedGunId,
            displayDefinition = displayDefinition,
            gunScriptParams = gunScriptParams,
            behaviorResult = behaviorResult
        )

        if (tickResult == null || tickResult.failed) {
            return ResolvedAnimationRuntimePath(
                clipSource = WeaponAnimationClipSource.SIGNAL_FALLBACK,
                preferredClip = null
            )
        }

        return ResolvedAnimationRuntimePath(
            clipSource = WeaponAnimationClipSource.LUA_STATE_MACHINE,
            preferredClip = resolveLuaPreferredClip(tickResult.activeStates)
        )
    }

    internal fun resolveAnimationClipSource(
        worldIsRemote: Boolean,
        sessionId: String,
        gunId: String?,
        displayDefinition: GunDisplayDefinition?,
        gunScriptParams: Map<String, Float>,
        behaviorResult: WeaponBehaviorResult?,
        luaFeatureEnabled: Boolean = ENABLE_LUA_ANIMATION_STATE_MACHINE
    ): WeaponAnimationClipSource {
        return resolveAnimationRuntimePath(
            worldIsRemote = worldIsRemote,
            sessionId = sessionId,
            gunId = gunId,
            displayDefinition = displayDefinition,
            gunScriptParams = gunScriptParams,
            behaviorResult = behaviorResult,
            luaFeatureEnabled = luaFeatureEnabled
        ).clipSource
    }

    internal fun resolveLuaPreferredClip(activeStates: Set<String>): WeaponAnimationClipType? {
        if (activeStates.isEmpty()) {
            return null
        }

        var matched: WeaponAnimationClipType? = null
        var matchedPriority = Int.MAX_VALUE

        activeStates.forEach { raw ->
            val token = normalizeLuaStateToken(raw)
            if (token.isEmpty()) {
                return@forEach
            }

            LUA_STATE_TOKEN_PRIORITIES.forEachIndexed { priority, (predicate, clip) ->
                if (!predicate(token)) {
                    return@forEachIndexed
                }
                if (priority < matchedPriority) {
                    matchedPriority = priority
                    matched = clip
                }
                return@forEach
            }
        }

        return matched
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

    private fun syncHeldGunStackFromBehaviorResult(
        player: EntityPlayer,
        gunId: String?,
        behaviorResult: WeaponBehaviorResult?
    ) {
        val snapshot = behaviorResult?.step?.snapshot ?: return
        val heldGunStack = resolveHeldGunStack(player, gunId) ?: return
        WeaponItemStackRuntimeData.writeAmmoState(
            stack = heldGunStack,
            ammoInMagazine = snapshot.ammoInMagazine,
            ammoReserve = snapshot.ammoReserve,
            hasBulletInBarrel = snapshot.ammoInMagazine > 0
        )
    }

    private fun resolveHeldGunStack(player: EntityPlayer, gunId: String?): ItemStack? {
        val normalizedGunId = gunId?.trim()?.lowercase()?.ifBlank { null } ?: return null
        val stack = player.heldItemMainhand
        if (stack.isEmpty || stack.item !is LegacyGunItem) {
            return null
        }

        val stackGunId = stack.item.registryName
            ?.toString()
            ?.substringAfter(':')
            ?.trim()
            ?.lowercase()
            ?.ifBlank { null }

        if (stackGunId != normalizedGunId) {
            return null
        }

        return stack
    }

    private fun enforceCreativeInfiniteAmmoIfNeeded(
        player: EntityPlayer,
        sessionId: String,
        gunId: String?
    ) {
        if (!ENABLE_CREATIVE_INFINITE_AMMO) {
            return
        }
        if (player.world.isRemote || !player.capabilities.isCreativeMode) {
            return
        }

        val normalizedGunId = gunId?.trim()?.lowercase()?.ifBlank { null } ?: return
        val heldGunStack = resolveHeldGunStack(player, normalizedGunId) ?: return
        val sessionService = WeaponRuntimeMcBridge.sessionServiceOrNull() ?: return
        val snapshot = sessionService.snapshot(sessionId) ?: return
        val stackReserve = WeaponItemStackRuntimeData.readAmmoReserve(heldGunStack, snapshot.ammoReserve)

        if (snapshot.ammoReserve >= CREATIVE_INFINITE_RESERVE && stackReserve >= CREATIVE_INFINITE_RESERVE) {
            return
        }

        val patchedSnapshot = snapshot.copy(
            ammoReserve = CREATIVE_INFINITE_RESERVE
        )

        val upserted = sessionService.upsertAuthoritativeSnapshot(
            sessionId = sessionId,
            gunId = normalizedGunId,
            snapshot = patchedSnapshot,
            allowFallbackDefinition = true
        )
        if (upserted == null) {
            return
        }

        WeaponItemStackRuntimeData.writeAmmoState(
            stack = heldGunStack,
            ammoInMagazine = patchedSnapshot.ammoInMagazine,
            ammoReserve = CREATIVE_INFINITE_RESERVE,
            hasBulletInBarrel = patchedSnapshot.ammoInMagazine > 0
        )
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
        val initialAmmoInMagazine: Int?,
        val initialAmmoReserve: Int?,
        val reloadTicks: Int?,
        val animationClipDurationsMillis: Map<WeaponAnimationClipType, Long>,
        val preferBoltCycleAfterFire: Boolean,
        val shellEjectPlan: WeaponAnimationShellEjectPlan,
        val displayDefinition: GunDisplayDefinition?,
        val gunScriptParams: Map<String, Float>
    )

    internal data class ResolvedAnimationRuntimePath(
        val clipSource: WeaponAnimationClipSource,
        val preferredClip: WeaponAnimationClipType?
    )

    private companion object {
        private const val SESSION_SYNC_RESEND_INTERVAL_TICKS: Long = 20L
        private const val FIRE_SOUND_PITCH_JITTER: Float = 0.08f
        private const val MOVING_SPEED_EPSILON_SQ: Double = 0.0025
        private const val DEFAULT_MAGAZINE_SIZE: Int = 30
        private const val ENABLE_CREATIVE_INFINITE_AMMO: Boolean = true
        private const val CREATIVE_INFINITE_RESERVE: Int = 9_999
        private const val ENABLE_LUA_ANIMATION_STATE_MACHINE: Boolean = false

        private fun normalizeLuaStateToken(raw: String): String =
            raw.trim()
                .lowercase()
                .map { ch -> if (ch.isLetterOrDigit()) ch else '_' }
                .joinToString(separator = "")
                .replace("__+".toRegex(), "_")
                .trim('_')

        private fun containsToken(token: String, keyword: String): Boolean {
            if (token == keyword) {
                return true
            }
            return token.startsWith("${keyword}_") || token.endsWith("_${keyword}") || token.contains("_${keyword}_")
        }

        private fun containsAnyToken(token: String, keywords: Collection<String>): Boolean =
            keywords.any { keyword -> containsToken(token, keyword) }

        private val LUA_STATE_TOKEN_PRIORITIES: List<Pair<(String) -> Boolean, WeaponAnimationClipType>> = listOf(
            Pair({ token -> containsAnyToken(token, setOf("reload", "reload_empty", "reload_tactical", "reload_intro", "reload_loop", "reload_end")) }, WeaponAnimationClipType.RELOAD),
            Pair({ token -> containsAnyToken(token, setOf("dry_fire", "empty_click", "no_ammo")) || (containsToken(token, "dry") && containsToken(token, "fire")) }, WeaponAnimationClipType.DRY_FIRE),
            Pair({ token ->
                (containsAnyToken(token, setOf("shoot", "shot", "recoil")) || containsToken(token, "fire")) &&
                    !containsAnyToken(token, setOf("fire_select", "fire_mode", "firemode"))
            }, WeaponAnimationClipType.FIRE),
            Pair({ token -> containsToken(token, "inspect") }, WeaponAnimationClipType.INSPECT),
            Pair({ token -> containsToken(token, "bolt") || containsToken(token, "charge") }, WeaponAnimationClipType.BOLT),
            Pair({ token -> token == "main_track_states_start" || token.endsWith("_main_track_states_start") }, WeaponAnimationClipType.DRAW),
            Pair({ token -> containsToken(token, "draw") || containsToken(token, "equip") || containsToken(token, "deploy") }, WeaponAnimationClipType.DRAW),
            Pair({ token -> containsToken(token, "put_away") || containsToken(token, "putaway") || containsToken(token, "holster") || containsToken(token, "withdraw") || token == "final" }, WeaponAnimationClipType.PUT_AWAY),
            Pair({ token -> containsToken(token, "run") || containsToken(token, "sprint") }, WeaponAnimationClipType.RUN),
            Pair({ token -> containsToken(token, "walk") || containsToken(token, "move") }, WeaponAnimationClipType.WALK),
            Pair({ token ->
                (containsToken(token, "aim") || containsToken(token, "aiming") || containsToken(token, "ads") || containsToken(token, "sight")) &&
                    !containsAnyToken(token, setOf("walk", "run", "sprint"))
            }, WeaponAnimationClipType.AIM),
            Pair({ token -> containsToken(token, "idle") || containsToken(token, "static") }, WeaponAnimationClipType.IDLE)
        )
    }

}