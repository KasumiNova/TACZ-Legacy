package com.tacz.legacy.common.application.weapon

import org.junit.Assert.assertEquals
import org.junit.Test
import org.luaj.vm2.lib.jse.JsePlatform

public class WeaponLuaRequireSupportTest {

    @Test
    public fun `install should resolve bundled default module`() {
        val globals = JsePlatform.standardGlobals()
        WeaponLuaRequireSupport.install(
            globals = globals,
            scriptId = "test:require_support"
        )

        val result = globals.load(
            """
                local default = require("tacz_default_state_machine")
                if default ~= nil and default.main_track_states ~= nil then
                    return "ok"
                end
                return "fail"
            """.trimIndent(),
            "@test:require_support_main"
        ).call()

        assertEquals("ok", result.tojstring())
    }

    @Test
    public fun `install should resolve dynamic namespace and relative modules`() {
        val globals = JsePlatform.standardGlobals()
        WeaponLuaRequireSupport.install(
            globals = globals,
            scriptId = "assets/tacz/custom/tacz_default_gun/assets/tacz/scripts/test_wrapper_state_machine.lua"
        )

        val result = globals.load(
            """
                local namespaced = require("tacz_ak47_state_machine")
                local relative = require("default_state_machine")
                if namespaced ~= nil and namespaced.main_track_states ~= nil and relative ~= nil and relative.main_track_states ~= nil then
                    return "ok"
                end
                return "fail"
            """.trimIndent(),
            "@test:require_support_dynamic"
        ).call()

        assertEquals("ok", result.tojstring())
    }
}
