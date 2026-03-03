package com.tacz.legacy

import com.tacz.legacy.common.CommonProxy
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.SidedProxy
import net.minecraftforge.fml.common.event.FMLInitializationEvent
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

@Mod(
    modid = TACZLegacy.MOD_ID,
    name = TACZLegacy.MOD_NAME,
    version = TACZLegacy.VERSION,
    acceptedMinecraftVersions = "[1.12.2]",
    dependencies = "required-after:forge@[14.23.5.2847,);required-after:forgelin_continuous@[2.3.0.0,);after:mixinbooter@[10.7,)",
    modLanguageAdapter = "io.github.chaosunity.forgelin.KotlinAdapter"
)
public object TACZLegacy {

    public const val MOD_ID: String = Tags.MOD_ID
    public const val MOD_NAME: String = Tags.MOD_NAME
    public const val VERSION: String = Tags.VERSION

    @JvmField
    internal var logger: Logger = LogManager.getLogger(MOD_ID)

    @JvmStatic
    @SidedProxy(
        clientSide = "com.tacz.legacy.client.ClientProxy",
        serverSide = "com.tacz.legacy.common.CommonProxy"
    )
    internal lateinit var proxy: CommonProxy

    @Mod.EventHandler
    internal fun preInit(event: FMLPreInitializationEvent) {
        logger = event.modLog
        event.modMetadata.version = VERSION
        proxy.preInit(event)
    }

    @Mod.EventHandler
    internal fun init(event: FMLInitializationEvent) {
        proxy.init(event)
    }

    @Mod.EventHandler
    internal fun postInit(event: FMLPostInitializationEvent) {
        proxy.postInit(event)
    }

}
