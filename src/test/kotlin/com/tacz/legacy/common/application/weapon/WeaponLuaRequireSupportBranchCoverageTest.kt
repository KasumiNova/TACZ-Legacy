package com.tacz.legacy.common.application.weapon

import org.junit.Assert.assertEquals
import org.junit.Test
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.OneArgFunction
import org.luaj.vm2.lib.jse.JsePlatform

public class WeaponLuaRequireSupportBranchCoverageTest {

    @Test
    public fun `install should no-op when package is not table`() {
        val globals = JsePlatform.standardGlobals()
        globals.set("package", LuaValue.NIL)

        WeaponLuaRequireSupport.install(globals, "test:no_package")

        // no exception means pass
        assertEquals(true, true)
    }

    @Test
    public fun `install should return when require is not function`() {
        val globals = JsePlatform.standardGlobals()
        globals.set("require", LuaValue.NIL)

        WeaponLuaRequireSupport.install(globals, "test:no_require")

        assertEquals(true, true)
    }

    @Test
    public fun `install should keep existing preload function without override`() {
        val globals = JsePlatform.standardGlobals()
        val packageTable = globals.get("package").checktable()
        val preload = packageTable.get("preload").checktable()

        preload.set("tacz_default_state_machine", object : OneArgFunction() {
            override fun call(arg: LuaValue): LuaValue {
                val t = LuaTable()
                t.set("custom_marker", LuaValue.valueOf("custom"))
                return t
            }
        })

        WeaponLuaRequireSupport.install(globals, "test:keep_preload")

        val result = globals.load(
            """
                local m = require('tacz_default_state_machine')
                return m.custom_marker
            """.trimIndent(),
            "@test:keep_preload_main"
        ).call()

        assertEquals("custom", result.tojstring())
    }

    @Test
    public fun `install should support direct assets lua path require`() {
        val globals = JsePlatform.standardGlobals()
        WeaponLuaRequireSupport.install(globals, "test:direct_assets")

        val result = globals.load(
            """
                local m = require('assets/tacz/custom/tacz_default_gun/assets/tacz/scripts/default_state_machine.lua')
                if m ~= nil and m.main_track_states ~= nil then
                    return 'ok'
                end
                return 'fail'
            """.trimIndent(),
            "@test:direct_assets_main"
        ).call()

        assertEquals("ok", result.tojstring())
    }

    @Test
    public fun `install should create preload table when package preload is missing`() {
        val globals = JsePlatform.standardGlobals()
        val packageTable = globals.get("package").checktable()
        packageTable.set("preload", LuaValue.NIL)

        WeaponLuaRequireSupport.install(globals, "test:missing_preload")

        val result = globals.load(
            """
                local m = require('tacz_default_state_machine')
                if m ~= nil and m.main_track_states ~= nil then
                    return 'ok'
                end
                return 'fail'
            """.trimIndent(),
            "@test:missing_preload_main"
        ).call()

        assertEquals("ok", result.tojstring())
    }

    @Test
    public fun `install should keep unknown require failure when script id has no directory`() {
        val globals = JsePlatform.standardGlobals()
        WeaponLuaRequireSupport.install(globals, "single.lua")

        val result = globals.load(
            """
                local ok = pcall(function()
                    require('non_existing_relative_module')
                end)
                return ok
            """.trimIndent(),
            "@test:no_script_directory_main"
        ).call()

        assertEquals(false, result.toboolean())
    }

    @Test
    public fun `install should fallback to dynamic candidates when predefined module path is unavailable`() {
        val globals = JsePlatform.standardGlobals()

        val field = WeaponLuaRequireSupport::class.java.getDeclaredField("PRELOADED_REQUIRE_MODULES")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val predefined = field.get(WeaponLuaRequireSupport) as MutableMap<String, String>

        val originalPath = predefined["tacz_default_state_machine"]
        predefined["tacz_default_state_machine"] = "assets/tacz/not_found/default_state_machine.lua"
        try {
            WeaponLuaRequireSupport.install(
                globals,
                "assets/tacz/custom/tacz_default_gun/assets/tacz/scripts/test_wrapper_state_machine.lua"
            )

            // 清空 preload，强制走 wrapped require 的动态解析分支。
            val preload = globals.get("package").checktable().get("preload").checktable()
            preload.set("tacz_default_state_machine", LuaValue.NIL)

            val result = globals.load(
                """
                    local m = require('tacz_default_state_machine')
                    if m ~= nil and m.main_track_states ~= nil then
                        return 'ok'
                    end
                    return 'fail'
                """.trimIndent(),
                "@test:predefined_fallback_main"
            ).call()

            assertEquals("ok", result.tojstring())
        } finally {
            if (originalPath == null) {
                predefined.remove("tacz_default_state_machine")
            } else {
                predefined["tacz_default_state_machine"] = originalPath
            }
        }
    }

    @Test
    public fun `install should resolve predefined module source via wrapped require when preload entry is cleared`() {
        val globals = JsePlatform.standardGlobals()
        WeaponLuaRequireSupport.install(globals, "test:predefined_dynamic_source")

        // 清空预置 preload，强制 wrapped require 进入 resolveDynamicModuleSource 的预置映射分支。
        val preload = globals.get("package").checktable().get("preload").checktable()
        preload.set("tacz_default_state_machine", LuaValue.NIL)

        val result = globals.load(
            """
                local m = require('tacz_default_state_machine')
                if m ~= nil and m.main_track_states ~= nil then
                    return 'ok'
                end
                return 'fail'
            """.trimIndent(),
            "@test:predefined_dynamic_source_main"
        ).call()

        assertEquals("ok", result.tojstring())
    }

    @Test
    public fun `install should keep require failure behavior for unknown module and non string arg`() {
        val globals = JsePlatform.standardGlobals()
        WeaponLuaRequireSupport.install(globals, "test:unknown_module")

        val unknown = globals.load(
            """
                local ok = pcall(function()
                    require('unknown_module_not_exists')
                end)
                return ok
            """.trimIndent(),
            "@test:unknown_module_main"
        ).call()

        val nonString = globals.load(
            """
                local ok = pcall(function()
                    require(123)
                end)
                return ok
            """.trimIndent(),
            "@test:non_string_require_main"
        ).call()

        assertEquals(false, unknown.toboolean())
        assertEquals(false, nonString.toboolean())
    }
}
