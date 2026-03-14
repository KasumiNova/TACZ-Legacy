package com.tacz.legacy.client.animation.statemachine

import com.tacz.legacy.api.client.animation.AnimationController
import com.tacz.legacy.api.client.animation.AnimationListenerSupplier
import com.tacz.legacy.api.client.animation.statemachine.LuaStateMachineFactory
import com.tacz.legacy.api.vmlib.LuaAnimationConstant
import com.tacz.legacy.api.vmlib.LuaGunAnimationConstant
import com.tacz.legacy.api.vmlib.LuaLibrary
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Test
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.compiler.LuaC
import org.luaj.vm2.lib.Bit32Lib
import org.luaj.vm2.lib.PackageLib
import org.luaj.vm2.lib.TableLib
import org.luaj.vm2.lib.jse.CoerceJavaToLua
import org.luaj.vm2.lib.jse.JseBaseLib
import org.luaj.vm2.lib.jse.JseMathLib

class GunAnimationStateContextLuaExposureTest {
    private val luaLibraries: List<LuaLibrary> = listOf(LuaAnimationConstant(), LuaGunAnimationConstant())

    @Test
    fun `default state machine context methods are exposed to lua`() {
        val scriptText = readScriptText(DEFAULT_STATE_MACHINE_PATH)

        val methods = Regex("context:(\\w+)\\(")
            .findAll(scriptText)
            .map { it.groupValues[1] }
            .toSortedSet()

        val luaContext = CoerceJavaToLua.coerce(GunAnimationStateContext())

        methods.forEach { methodName ->
            assertFalse(
                "Lua context method '$methodName' is missing from GunAnimationStateContext exposure",
                luaContext.get(methodName).isnil(),
            )
        }
    }

    @Test
    fun `default state machine shoot transition invokes popShellFrom`() {
        val context = RecordingGunAnimationStateContext()
        val stateMachine = createStateMachine(loadScriptTable(DEFAULT_STATE_MACHINE_PATH))

        stateMachine.setContext(context)
        stateMachine.initialize()
        stateMachine.trigger(GunAnimationConstant.INPUT_DRAW)
        stateMachine.trigger(GunAnimationConstant.INPUT_SHOOT)

        assertEquals(listOf(0), context.poppedShellIndices)
    }

    @Test
    fun `fn evolys shoot transition invokes popShellFrom`() {
        val defaultScript = loadScriptTable(DEFAULT_STATE_MACHINE_PATH)
        val context = RecordingGunAnimationStateContext()
        val stateMachine = createStateMachine(
            loadScriptTable(
                FN_EVOLYS_STATE_MACHINE_PATH,
                preloadModules = mapOf("tacz_default_state_machine" to defaultScript),
            ),
        )

        stateMachine.setContext(context)
        stateMachine.initialize()
        stateMachine.trigger(GunAnimationConstant.INPUT_DRAW)
        stateMachine.trigger(GunAnimationConstant.INPUT_SHOOT)

        assertEquals(listOf(0), context.poppedShellIndices)
    }

    private fun createStateMachine(scriptTable: LuaTable) =
        LuaStateMachineFactory<GunAnimationStateContext>()
            .setController(AnimationController(emptyList(), AnimationListenerSupplier { _, _ -> null }))
            .setLuaScripts(scriptTable)
            .build()

    private fun loadScriptTable(
        resourcePath: String,
        preloadModules: Map<String, LuaTable> = emptyMap(),
    ): LuaTable {
        val globals = createSecureGlobals()
        luaLibraries.forEach { it.install(globals) }
        val preload = globals.get("package").get("preload")
        if (preload is LuaTable) {
            preloadModules.forEach { (moduleName, moduleTable) ->
                preload.set(moduleName, object : org.luaj.vm2.lib.ZeroArgFunction() {
                    override fun call(): LuaValue = moduleTable
                })
            }
        }
        val chunk = globals.load(readScriptText(resourcePath), resourcePath)
        val result = chunk.call().checktable()
        luaLibraries.forEach { it.install(result) }
        return result
    }

    private fun createSecureGlobals(): Globals {
        val globals = Globals()
        globals.load(JseBaseLib())
        globals.load(PackageLib())
        globals.load(Bit32Lib())
        globals.load(TableLib())
        globals.load(org.luaj.vm2.lib.StringLib())
        globals.load(JseMathLib())
        LuaC.install(globals)
        return globals
    }

    private fun readScriptText(resourcePath: String): String =
        javaClass.classLoader
            .getResourceAsStream(resourcePath)
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: error("$resourcePath not found on test classpath")

    private class RecordingGunAnimationStateContext : GunAnimationStateContext() {
        val poppedShellIndices: MutableList<Int> = mutableListOf()

        override fun hasBulletInBarrel(): Boolean = true

        override fun isOverHeat(): Boolean = false

        override fun getShootInterval(): Long = 50L

        override fun getAmmoCount(): Int = 30

        override fun getMaxAmmoCount(): Int = 30

        override fun hasAmmoToConsume(): Boolean = true

        override fun getMagExtentLevel(): Int = 0

        override fun getFireMode(): Int = 0

        override fun getAimingProgress(): Float = 0f

        override fun isAiming(): Boolean = false

        override fun getShootCoolDown(): Long = 0L

        override fun getReloadStateType(): Int = 0

        override fun isInputUp(): Boolean = false

        override fun isInputDown(): Boolean = false

        override fun isInputLeft(): Boolean = false

        override fun isInputRight(): Boolean = false

        override fun isInputJumping(): Boolean = false

        override fun isCrawl(): Boolean = false

        override fun isOnGround(): Boolean = true

        override fun isCrouching(): Boolean = false

        override fun shouldSlide(): Boolean = false

        override fun anchorWalkDist() = Unit

        override fun getWalkDist(): Float = 0f

        override fun popShellFrom(index: Int) {
            poppedShellIndices += index
        }
    }

    companion object {
        private const val DEFAULT_STATE_MACHINE_PATH =
            "assets/tacz/custom/tacz_default_gun/assets/tacz/scripts/default_state_machine.lua"
        private const val FN_EVOLYS_STATE_MACHINE_PATH =
            "assets/tacz/custom/tacz_default_gun/assets/tacz/scripts/fn_evolys_state_machine.lua"
    }
}