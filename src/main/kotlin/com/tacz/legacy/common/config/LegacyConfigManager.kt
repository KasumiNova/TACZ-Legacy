package com.tacz.legacy.common.config

import com.tacz.legacy.TACZLegacy
import net.minecraftforge.common.config.Configuration
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent
import net.minecraftforge.fml.relauncher.Side
import java.io.File

internal data class PreloadConfigValues(
    var defaultPackDebug: Boolean = false,
)

internal data class CommonConfigValues(
    var defaultGunFireSoundDistance: Int = 64,
    var defaultGunSilenceSoundDistance: Int = 16,
    var defaultGunOtherSoundDistance: Int = 16,
    var creativePlayerConsumeAmmo: Boolean = true,
    var autoReloadWhenRespawn: Boolean = false,
    var explosiveAmmoDestroysBlock: Boolean = true,
    var explosiveAmmoFire: Boolean = false,
    var explosiveAmmoKnockBack: Boolean = true,
    var explosiveAmmoVisibleDistance: Int = 192,
    var passThroughBlocks: List<String> = emptyList(),
    var destroyGlass: Boolean = true,
    var igniteBlock: Boolean = true,
    var igniteEntity: Boolean = true,
    var globalBulletSpeedModifier: Double = 2.0,
    var legacyDefaultPackDebug: Boolean = false,
    var targetSoundDistance: Int = 128,
    var serverHitboxOffset: Double = 3.0,
    var serverHitboxLatencyFix: Boolean = true,
    var serverHitboxLatencyMaxSaveMs: Double = 1000.0,
)

internal data class ClientConfigValues(
    var holdToAim: Boolean = true,
    var holdToCrawl: Boolean = true,
    var autoReload: Boolean = false,
    var enableLaserFadeOut: Boolean = true,
    var gunLodRenderDistance: Int = 0,
    var bulletHoleParticleLife: Int = 400,
    var bulletHoleParticleFadeThreshold: Double = 0.98,
    var crosshairType: String = "DOT_1",
    var hitMarkerStartPosition: Double = 4.0,
    var headShotDebugHitbox: Boolean = false,
    var gunHudEnable: Boolean = true,
    var killAmountEnable: Boolean = true,
    var killAmountDurationSecond: Double = 3.0,
    var targetRenderDistance: Int = 128,
    var firstPersonBulletTracerEnable: Boolean = true,
    var disableInteractHudText: Boolean = false,
    var damageCounterResetTime: Int = 2000,
    var disableMovementAttributeFov: Boolean = true,
    var enableTaczIdInTooltip: Boolean = true,
    var blockEntityTranslucent: Boolean = false,
    var screenDistanceCoefficient: Double = 1.33,
    var zoomSensitivityBaseMultiplier: Double = 1.0,
)

internal data class ServerConfigValues(
    var interactKeyWhitelistBlocks: List<String> = emptyList(),
    var interactKeyWhitelistEntities: List<String> = emptyList(),
    var interactKeyBlacklistBlocks: List<String> = emptyList(),
    var interactKeyBlacklistEntities: List<String> = emptyList(),
    var enableTableFilter: Boolean = true,
    var serverShootNetworkCheck: Boolean = true,
    var serverShootCooldownCheck: Boolean = true,
    var damageBaseMultiplier: Double = 1.0,
    var armorIgnoreBaseMultiplier: Double = 1.0,
    var headShotBaseMultiplier: Double = 1.0,
    var weightSpeedMultiplier: Double = 0.015,
    var headShotAabb: List<String> = emptyList(),
    var ammoBoxStackSize: Int = 3,
    var enableCrawl: Boolean = true,
)

internal enum class ServerToggleKey(
    internal val translationKey: String,
) {
    DEFAULT_TABLE_LIMIT("commands.tacz.config.default_table_limit"),
    SERVER_SHOOT_NETWORK_CHECK("commands.tacz.config.server_shoot_network_check"),
    SERVER_SHOOT_COOLDOWN_CHECK("commands.tacz.config.server_shoot_cooldown_check"),
}

internal object LegacyConfigManager {
    internal const val CONFIG_SUBDIR: String = "tacz"
    internal const val PRELOAD_FILE_NAME: String = "tacz-pre.cfg"
    internal const val COMMON_FILE_NAME: String = "tacz-common.cfg"
    internal const val CLIENT_FILE_NAME: String = "tacz-client.cfg"
    internal const val SERVER_FILE_NAME: String = "tacz-server.cfg"

    internal val preload: PreloadConfigValues = PreloadConfigValues()
    internal val common: CommonConfigValues = CommonConfigValues()
    internal val client: ClientConfigValues = ClientConfigValues()
    internal val server: ServerConfigValues = ServerConfigValues()

    private var baseConfigDirectory: File? = null
    private var modConfigDirectory: File? = null
    private var gameDirectory: File? = null
    private var lastLoadedSide: Side = Side.SERVER

    private var preloadConfig: Configuration? = null
    private var commonConfig: Configuration? = null
    private var clientConfig: Configuration? = null
    private var serverConfig: Configuration? = null

    internal fun loadPreloadConfig(configDirectory: File): Unit {
        baseConfigDirectory = configDirectory
        modConfigDirectory = File(configDirectory, CONFIG_SUBDIR).apply { mkdirs() }
        val cfg = Configuration(File(modConfigDirectory, PRELOAD_FILE_NAME))
        preloadConfig = cfg
        try {
            cfg.load()
            cfg.addCustomCategoryComment(
                "gunpack",
                "When enabled, the mod will not try to overwrite the default pack under .minecraft/tacz.\n" +
                    "Since 1.0.4, the overwriting only runs when the client or dedicated server starts."
            )
            preload.defaultPackDebug = cfg.getBoolean(
                "DefaultPackDebug",
                "gunpack",
                false,
                "When enabled, the default exported gun pack will not be overwritten.",
            )
        } finally {
            if (cfg.hasChanged()) {
                cfg.save()
            }
        }
    }

    internal fun loadMainConfigs(event: FMLPreInitializationEvent): Unit {
        val configDir = modConfigDirectory ?: File(event.modConfigurationDirectory, CONFIG_SUBDIR).apply { mkdirs() }
        modConfigDirectory = configDir
        baseConfigDirectory = event.modConfigurationDirectory
        gameDirectory = event.modConfigurationDirectory.parentFile
        lastLoadedSide = event.side

        loadCommonConfig(Configuration(File(configDir, COMMON_FILE_NAME)))
        loadClientConfig(Configuration(File(configDir, CLIENT_FILE_NAME)), event.side)
        loadServerConfig(Configuration(File(configDir, SERVER_FILE_NAME)))
        syncDerivedViews()
    }

    internal fun reloadAll(): Unit {
        val baseDir = baseConfigDirectory ?: return
        loadPreloadConfig(baseDir)
        loadCommonConfig(Configuration(File(requireNotNull(modConfigDirectory), COMMON_FILE_NAME)))
        loadClientConfig(Configuration(File(requireNotNull(modConfigDirectory), CLIENT_FILE_NAME)), lastLoadedSide)
        loadServerConfig(Configuration(File(requireNotNull(modConfigDirectory), SERVER_FILE_NAME)))
        syncDerivedViews()
    }

    internal fun shouldOverwriteDefaultPack(): Boolean = !preload.defaultPackDebug

    internal fun setOverwriteEnabled(enabled: Boolean): Unit {
        preload.defaultPackDebug = !enabled
        val cfg = preloadConfig ?: return
        cfg.get("gunpack", "DefaultPackDebug", false).set(!enabled)
        cfg.save()
    }

    internal fun applyServerToggle(key: ServerToggleKey, enabled: Boolean): Unit {
        when (key) {
            ServerToggleKey.DEFAULT_TABLE_LIMIT -> server.enableTableFilter = enabled
            ServerToggleKey.SERVER_SHOOT_NETWORK_CHECK -> server.serverShootNetworkCheck = enabled
            ServerToggleKey.SERVER_SHOOT_COOLDOWN_CHECK -> server.serverShootCooldownCheck = enabled
        }
        val cfg = serverConfig ?: return
        when (key) {
            ServerToggleKey.DEFAULT_TABLE_LIMIT -> cfg.get("misc", "EnableDefaultGunSmithTableFilter", true).set(enabled)
            ServerToggleKey.SERVER_SHOOT_NETWORK_CHECK -> cfg.get("misc", "ServerShootNetworkCheck", true).set(enabled)
            ServerToggleKey.SERVER_SHOOT_COOLDOWN_CHECK -> cfg.get("misc", "ServerShootCooldownCheck", true).set(enabled)
        }
        cfg.save()
    }

    internal fun getModConfigDirectory(): File? = modConfigDirectory

    internal fun getGameDirectory(): File? = gameDirectory

    @JvmStatic
    fun isLaserFadeOutEnabledForJava(): Boolean = client.enableLaserFadeOut

    private fun loadCommonConfig(cfg: Configuration): Unit {
        commonConfig = cfg
        try {
            cfg.load()
            common.defaultGunFireSoundDistance = cfg.getInt(
                "DefaultGunFireSoundDistance", "gun", 64, 0, Int.MAX_VALUE,
                "The default fire sound range (block)."
            )
            common.defaultGunSilenceSoundDistance = cfg.getInt(
                "DefaultGunSilenceSoundDistance", "gun", 16, 0, Int.MAX_VALUE,
                "The silencer default fire sound range (block)."
            )
            common.defaultGunOtherSoundDistance = cfg.getInt(
                "DefaultGunOtherSoundDistance", "gun", 16, 0, Int.MAX_VALUE,
                "The range (block) of other gun sounds, reloading sounds, etc."
            )
            common.creativePlayerConsumeAmmo = cfg.getBoolean(
                "CreativePlayerConsumeAmmo", "gun", true,
                "Whether or not the player will consume ammo in creative mode."
            )
            common.autoReloadWhenRespawn = cfg.getBoolean(
                "AutoReloadWhenRespawn", "gun", false,
                "Auto reload all guns in the player inventory after respawn."
            )

            common.explosiveAmmoDestroysBlock = cfg.getBoolean(
                "ExplosiveAmmoDestroysBlock", "ammo", true,
                "Warning: Ammo with explosive properties can break blocks."
            )
            common.explosiveAmmoFire = cfg.getBoolean(
                "ExplosiveAmmoFire", "ammo", false,
                "Warning: Ammo with explosive properties can set surroundings on fire."
            )
            common.explosiveAmmoKnockBack = cfg.getBoolean(
                "ExplosiveAmmoKnockBack", "ammo", true,
                "Ammo with explosive properties can add knockback effect."
            )
            common.explosiveAmmoVisibleDistance = cfg.getInt(
                "ExplosiveAmmoVisibleDistance", "ammo", 192, 0, Int.MAX_VALUE,
                "The distance at which the explosion effect can be seen."
            )
            common.passThroughBlocks = cfg.getStringList(
                "PassThroughBlocks", "ammo", emptyArray(),
                "Those blocks that the ammo can pass through."
            ).toList()
            common.destroyGlass = cfg.getBoolean(
                "DestroyGlass", "ammo", true,
                "Whether a ammo can break glass."
            )
            common.igniteBlock = cfg.getBoolean(
                "IgniteBlock", "ammo", true,
                "Whether ammo can ignite blocks."
            )
            common.igniteEntity = cfg.getBoolean(
                "IgniteEntity", "ammo", true,
                "Whether ammo can ignite entities."
            )
            common.globalBulletSpeedModifier = cfg.getFloat(
                "GlobalBulletSpeedModifier", "ammo", 2.0f, 0.01f, 20.0f,
                "Global bullet speed modifier."
            ).toDouble()

            common.legacyDefaultPackDebug = cfg.getBoolean(
                "DefaultPackDebug", "other", false,
                "Deprecated: now move to .minecraft/tacz/tacz-pre.cfg or config/tacz/tacz-pre.cfg."
            )
            common.targetSoundDistance = cfg.getInt(
                "TargetSoundDistance", "other", 128, 0, Int.MAX_VALUE,
                "The farthest sound distance of the target, including minecarts type."
            )
            common.serverHitboxOffset = cfg.getFloat(
                "ServerHitboxOffset", "other", 3.0f, -1024.0f, 1024.0f,
                "DEV: Server hitbox offset."
            ).toDouble()
            common.serverHitboxLatencyFix = cfg.getBoolean(
                "ServerHitboxLatencyFix", "other", true,
                "Server hitbox latency fix."
            )
            common.serverHitboxLatencyMaxSaveMs = cfg.getFloat(
                "ServerHitboxLatencyMaxSaveMs", "other", 1000.0f, 250.0f, 60000.0f,
                "The maximum latency (in milliseconds) for the server hitbox latency fix saved."
            ).toDouble()
        } finally {
            if (cfg.hasChanged()) {
                cfg.save()
            }
        }
    }

    private fun loadClientConfig(cfg: Configuration, side: Side): Unit {
        clientConfig = cfg
        try {
            cfg.load()
            client.holdToAim = cfg.getBoolean("HoldToAim", "key", true, "Hold right mouse button to aim.")
            client.holdToCrawl = cfg.getBoolean("HoldToCrawl", "key", true, "Hold the crawl button to crawl.")
            client.autoReload = cfg.getBoolean("AutoReload", "key", false, "Try to reload automatically when the gun is empty.")

            client.enableLaserFadeOut = cfg.getBoolean("EnableLaserFadeOut", "render", true, "Apply fade-out effect on the laser beam.")
            client.gunLodRenderDistance = cfg.getInt("GunLodRenderDistance", "render", 0, 0, Int.MAX_VALUE, "How far to display the LOD model, 0 means always display.")
            client.bulletHoleParticleLife = cfg.getInt("BulletHoleParticleLife", "render", 400, 0, Int.MAX_VALUE, "The existence time of bullet hole particles, in tick.")
            client.bulletHoleParticleFadeThreshold = cfg.getFloat("BulletHoleParticleFadeThreshold", "render", 0.98f, 0.0f, 1.0f, "The threshold for fading out when rendering bullet hole particles.").toDouble()
            client.crosshairType = cfg.getString("CrosshairType", "render", "DOT_1", "The crosshair when holding a gun.")
            client.hitMarkerStartPosition = cfg.getFloat("HitMarketStartPosition", "render", 4.0f, -1024.0f, 1024.0f, "The starting position of the hit marker.").toDouble()
            client.headShotDebugHitbox = cfg.getBoolean("HeadShotDebugHitbox", "render", false, "Whether or not to display the head shot hitbox.")
            client.gunHudEnable = cfg.getBoolean("GunHUDEnable", "render", true, "Whether or not to display the gun HUD.")
            client.killAmountEnable = cfg.getBoolean("KillAmountEnable", "render", true, "Whether or not to display the kill amount.")
            client.killAmountDurationSecond = cfg.getFloat("KillAmountDurationSecond", "render", 3.0f, 0.0f, 600.0f, "The duration of the kill amount, in second.").toDouble()
            client.targetRenderDistance = cfg.getInt("TargetRenderDistance", "render", 128, 0, Int.MAX_VALUE, "The farthest render distance of the target, including minecarts type.")
            client.firstPersonBulletTracerEnable = cfg.getBoolean("FirstPersonBulletTracerEnable", "render", true, "Whether or not to render first person bullet trail.")
            client.disableInteractHudText = cfg.getBoolean("DisableInteractHudText", "render", false, "Disable the interact HUD text in the center of the screen.")
            client.damageCounterResetTime = cfg.getInt("DamageCounterResetTime", "render", 2000, 10, Int.MAX_VALUE, "Max time the damage counter will reset.")
            client.disableMovementAttributeFov = cfg.getBoolean("DisableMovementAttributeFov", "render", true, "Disable the FOV effect from the movement speed attribute while holding a gun.")
            client.enableTaczIdInTooltip = cfg.getBoolean("EnableTaczIdInTooltip", "render", true, "Enable the display of the TACZ ID in the tooltip when advanced tooltip is enabled.")
            client.blockEntityTranslucent = cfg.getBoolean("EnableBlockEntityTranslucent", "render", false, "Enable translucent block-entity rendering.")

            client.screenDistanceCoefficient = cfg.getFloat("ScreenDistanceCoefficient", "Zoom", 1.33f, 0.0f, 3.0f, "Screen distance coefficient for zoom.").toDouble()
            client.zoomSensitivityBaseMultiplier = cfg.getFloat("ZoomSensitivityBaseMultiplier", "Zoom", 1.0f, 0.0f, 2.0f, "Zoom sensitivity base multiplier.").toDouble()
        } catch (t: Throwable) {
            TACZLegacy.logger.warn("Failed to load ${side.name.lowercase()} config, using defaults.", t)
        } finally {
            if (cfg.hasChanged()) {
                cfg.save()
            }
        }
    }

    private fun loadServerConfig(cfg: Configuration): Unit {
        serverConfig = cfg
        try {
            cfg.load()
            server.interactKeyWhitelistBlocks = cfg.getStringList(
                "InteractKeyWhitelistBlocks", "interact_key", emptyArray(),
                "These whitelist blocks can be interacted with when the interact key is pressed."
            ).toList()
            server.interactKeyWhitelistEntities = cfg.getStringList(
                "InteractKeyWhitelistEntities", "interact_key", emptyArray(),
                "These whitelist entities can be interacted with when the interact key is pressed."
            ).toList()
            server.interactKeyBlacklistBlocks = cfg.getStringList(
                "InteractKeyBlacklistBlocks", "interact_key", emptyArray(),
                "These blacklist blocks cannot be interacted with when the interact key is pressed."
            ).toList()
            server.interactKeyBlacklistEntities = cfg.getStringList(
                "InteractKeyBlacklistEntities", "interact_key", emptyArray(),
                "These blacklist entities cannot be interacted with when the interact key is pressed."
            ).toList()

            server.damageBaseMultiplier = cfg.getFloat(
                "DamageBaseMultiplier", "base_multiplier", 1.0f, 0.0f, Float.MAX_VALUE,
                "All base damage numbers are multiplied by this factor."
            ).toDouble()
            server.armorIgnoreBaseMultiplier = cfg.getFloat(
                "ArmorIgnoreBaseMultiplier", "base_multiplier", 1.0f, 0.0f, Float.MAX_VALUE,
                "All armor ignore damage numbers are multiplied by this factor."
            ).toDouble()
            server.headShotBaseMultiplier = cfg.getFloat(
                "HeadShotBaseMultiplier", "base_multiplier", 1.0f, 0.0f, Float.MAX_VALUE,
                "All head shot damage numbers are multiplied by this factor."
            ).toDouble()
            server.weightSpeedMultiplier = cfg.getFloat(
                "WeightSpeedMultiplier", "base_multiplier", 0.015f, -1.0f, Float.MAX_VALUE,
                "Movement speed decrease per kg of weight."
            ).toDouble()

            server.headShotAabb = cfg.getStringList(
                "HeadShotAABB", "misc", emptyArray(),
                "The entity head hitbox during headshots. Format: modid:entity [x1, y1, z1, x2, y2, z2]"
            ).toList()
            server.ammoBoxStackSize = cfg.getInt(
                "AmmoBoxStackSize", "misc", 3, 1, Int.MAX_VALUE,
                "The maximum stack size of ammo that the ammo box can hold."
            )
            server.enableCrawl = cfg.getBoolean(
                "EnableCrawl", "misc", true,
                "Whether or not players are allowed to use the crawl feature."
            )
            server.enableTableFilter = cfg.getBoolean(
                "EnableDefaultGunSmithTableFilter", "misc", true,
                "Enable the recipe limit of the default gunsmith table or not."
            )
            server.serverShootNetworkCheck = cfg.getBoolean(
                "ServerShootNetworkCheck", "misc", true,
                "Do server-side network checks while shooting or not."
            )
            server.serverShootCooldownCheck = cfg.getBoolean(
                "ServerShootCooldownCheck", "misc", true,
                "Do server-side shoot cooldown checks or not."
            )
        } finally {
            if (cfg.hasChanged()) {
                cfg.save()
            }
        }
    }

    private fun syncDerivedViews(): Unit {
        HeadShotAabbConfigRead.reload(server.headShotAabb)
        InteractKeyConfigRead.reload(
            blockWhitelist = server.interactKeyWhitelistBlocks,
            entityWhitelist = server.interactKeyWhitelistEntities,
            blockBlacklist = server.interactKeyBlacklistBlocks,
            entityBlacklist = server.interactKeyBlacklistEntities,
        )
    }
}
