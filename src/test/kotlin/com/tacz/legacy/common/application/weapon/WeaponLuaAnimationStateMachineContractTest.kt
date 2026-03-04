package com.tacz.legacy.common.application.weapon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

public class WeaponLuaAnimationStateMachineContractTest {

    @Test
    public fun `initialize should fallback to idle when global initialize function is absent`() {
        val machine = WeaponLuaAnimationStateMachine(
            scriptId = "test:no_initialize",
            scriptSource = "states = { idle = {} }"
        )

        val result = machine.initialize()

        assertFalse(result.failed)
        assertEquals(setOf("idle"), result.activeStates)
        assertEquals(setOf("idle"), result.enteredStates)
    }

    @Test
    public fun `tick should keep current state when transition result is non string and non table`() {
        val machine = WeaponLuaAnimationStateMachine(
            scriptId = "test:invalid_transition",
            scriptSource = """
                states = {
                  idle = {
                    transition = function(ctx)
                      if ctx.fire then
                        return true
                      end
                      return nil
                    end
                  }
                }

                function initialize(ctx)
                  return "idle"
                end
            """.trimIndent()
        )

        val tick = machine.tick(mapOf("fire" to true))

        assertFalse(tick.failed)
        assertEquals(setOf("idle"), tick.activeStates)
    }

    @Test
    public fun `tick should accept primitive context values and transition accordingly`() {
        val machine = WeaponLuaAnimationStateMachine(
            scriptId = "test:primitive_context",
            scriptSource = """
                states = {
                  idle = {
                    transition = function(ctx)
                      if ctx.flag == true and ctx.count == 3 and ctx.ratio > 0.4 and ctx.tag == "ok" then
                        return "ready"
                      end
                      return nil
                    end
                  },
                  ready = {}
                }

                function initialize(ctx)
                  return "idle"
                end
            """.trimIndent()
        )

        val tick = machine.tick(
            mapOf(
                "flag" to true,
                "count" to 3,
                "ratio" to 0.5f,
                "tag" to "ok"
            )
        )

        assertFalse(tick.failed)
        assertEquals(setOf("ready"), tick.activeStates)
    }

    @Test
    public fun `shutdown should execute state exit hook and fail safely when exit throws`() {
        val machine = WeaponLuaAnimationStateMachine(
            scriptId = "test:shutdown_exit_throw",
            scriptSource = """
                states = {
                  idle = {
                    exit = function(ctx)
                      error("exit called")
                    end
                  }
                }

                function initialize(ctx)
                  return "idle"
                end
            """.trimIndent()
        )

        val init = machine.initialize()
        assertFalse(init.failed)

        val shutdown = machine.shutdown()

        assertTrue(shutdown.failed)
        assertFalse(machine.isAvailable())
        assertTrue((machine.lastFailureMessage() ?: "").isNotBlank())
    }

    @Test
    public fun `module protocol should initialize from states list and transition by TACZ input token`() {
        val machine = WeaponLuaAnimationStateMachine(
            scriptId = "test:module_protocol_input",
            scriptSource = """
                local M = {
                  main_track_states = {
                    idle = {},
                    fire = {}
                  }
                }

                function M:states()
                  return { self.main_track_states.idle }
                end

                function M.main_track_states.idle.transition(this, context, input)
                  if input == 'shoot' then
                    return this.main_track_states.fire
                  end
                  return nil
                end

                function M.main_track_states.fire.transition(this, context, input)
                  if input == 'idle' then
                    return this.main_track_states.idle
                  end
                  return nil
                end

                return M
            """.trimIndent()
        )

        val init = machine.initialize()
        assertFalse(init.failed)
        assertEquals(setOf("main_track_states.idle"), init.activeStates)

        val fire = machine.tick(mapOf("input" to "shoot"))
        assertFalse(fire.failed)
        assertEquals(setOf("main_track_states.fire"), fire.activeStates)

        val settle = machine.tick(mapOf("input_token" to "idle"))
        assertFalse(settle.failed)
        assertEquals(setOf("main_track_states.idle"), settle.activeStates)
    }

    @Test
    public fun `module protocol should preserve indexed state path names`() {
        val machine = WeaponLuaAnimationStateMachine(
            scriptId = "test:module_protocol_state_name_field",
            scriptSource = """
                local M = {
                  base = {
                    state_a = {
                      name = 'state_custom_a'
                    },
                    state_b = {
                      name = 'state_custom_b'
                    }
                  }
                }

                function M:states()
                  return { self.base.state_a }
                end

                function M.base.state_a.transition(this, context, input)
                  if input == 'to_b' then
                    return this.base.state_b
                  end
                  return nil
                end

                return M
            """.trimIndent()
        )

        val init = machine.initialize()
        assertFalse(init.failed)
        assertEquals(setOf("base.state_a"), init.activeStates)

        val tick = machine.tick(mapOf("input" to "to_b"))
        assertFalse(tick.failed)
        assertEquals(setOf("base.state_b"), tick.activeStates)
    }

    @Test
    public fun `module protocol should support requiring bundled tacz default state machine`() {
        val machine = WeaponLuaAnimationStateMachine(
            scriptId = "test:require_default_module",
            scriptSource = """
                local default = require("tacz_default_state_machine")

                local M = {
                  states_table = {
                    idle = {
                      transition = function(this, context, input)
                        if input == 'shoot' and default.main_track_states ~= nil then
                          return this.states_table.fire
                        end
                        return nil
                      end
                    },
                    fire = {}
                  }
                }

                function M:states()
                  return { self.states_table.idle }
                end

                return M
            """.trimIndent()
        )

        val init = machine.initialize()
        assertFalse(init.failed)
        assertEquals(setOf("states_table.idle"), init.activeStates)

        val fire = machine.tick(mapOf("input" to "shoot"))
        assertFalse(fire.failed)
        assertEquals(setOf("states_table.fire"), fire.activeStates)
    }

    @Test
    public fun `module protocol should support nested require through manual action module`() {
        val machine = WeaponLuaAnimationStateMachine(
            scriptId = "test:require_manual_module",
            scriptSource = """
                local manual = require("tacz_manual_action_state_machine")

                local M = {
                  states_table = {
                    idle = {}
                  }
                }

                function M:states()
                  if manual ~= nil and manual.main_track_states ~= nil then
                    return { self.states_table.idle }
                  end
                  return {}
                end

                return M
            """.trimIndent()
        )

        val init = machine.initialize()
        assertFalse(init.failed)
        assertEquals(setOf("states_table.idle"), init.activeStates)
    }

    @Test
    public fun `module protocol should support dynamic namespace path require resolution`() {
        val machine = WeaponLuaAnimationStateMachine(
            scriptId = "test:require_dynamic_namespace_module",
            scriptSource = """
                local ak47 = require("tacz_ak47_state_machine")

                local M = {
                  states_table = {
                    idle = {}
                  }
                }

                function M:states()
                  if ak47 ~= nil and ak47.main_track_states ~= nil then
                    return { self.states_table.idle }
                  end
                  return {}
                end

                return M
            """.trimIndent()
        )

        val init = machine.initialize()
        assertFalse(init.failed)
        assertEquals(setOf("states_table.idle"), init.activeStates)
    }

    @Test
    public fun `module protocol should support script directory relative require resolution`() {
        val machine = WeaponLuaAnimationStateMachine(
            scriptId = "assets/tacz/custom/tacz_default_gun/assets/tacz/scripts/test_wrapper_state_machine.lua",
            scriptSource = """
                local default = require("default_state_machine")

                local M = {
                  states_table = {
                    idle = {}
                  }
                }

                function M:states()
                  if default ~= nil and default.main_track_states ~= nil then
                    return { self.states_table.idle }
                  end
                  return {}
                end

                return M
            """.trimIndent()
        )

        val init = machine.initialize()
        assertFalse(init.failed)
        assertEquals(setOf("states_table.idle"), init.activeStates)
    }
}
