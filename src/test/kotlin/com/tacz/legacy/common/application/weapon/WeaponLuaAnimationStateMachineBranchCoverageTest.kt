package com.tacz.legacy.common.application.weapon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

public class WeaponLuaAnimationStateMachineBranchCoverageTest {

    @Test
    public fun `initialize should fail when script fails to load`() {
        val machine = WeaponLuaAnimationStateMachine(
            scriptId = "test:load_fail",
            scriptSource = "function initialize(" // invalid lua
        )

        val result = machine.initialize()

        assertTrue(result.failed)
        assertFalse(machine.isAvailable())
    }

    @Test
    public fun `initialize called twice should return current snapshot`() {
        val machine = WeaponLuaAnimationStateMachine(
            scriptId = "test:init_twice",
            scriptSource = "states = { idle = {} }"
        )

        val first = machine.initialize()
        val second = machine.initialize()

        assertFalse(first.failed)
        assertFalse(second.failed)
        assertEquals(setOf("idle"), second.activeStates)
    }

    @Test
    public fun `tick should propagate initialize failure when machine is unavailable`() {
        val machine = WeaponLuaAnimationStateMachine(
            scriptId = "test:tick_init_fail",
            scriptSource = "function initialize(" // invalid lua
        )

        val tick = machine.tick(mapOf("input" to "shoot"))

        assertTrue(tick.failed)
    }

    @Test
    public fun `shutdown before initialize should return empty success`() {
        val machine = WeaponLuaAnimationStateMachine(
            scriptId = "test:shutdown_before_init",
            scriptSource = "states = { idle = {} }"
        )

        val shutdown = machine.shutdown()

        assertFalse(shutdown.failed)
        assertTrue(shutdown.activeStates.isEmpty())
    }

    @Test
    public fun `module initialize should override states function`() {
        val machine = WeaponLuaAnimationStateMachine(
            scriptId = "test:module_initialize_overrides_states",
            scriptSource = """
                local M = {
                  flow = {
                    idle = {},
                    fire = {}
                  }
                }

                function M:initialize(ctx)
                  return self.flow.fire
                end

                function M:states()
                  return { self.flow.idle }
                end

                return M
            """.trimIndent()
        )

        val init = machine.initialize()

        assertFalse(init.failed)
        assertEquals(setOf("flow.fire"), init.activeStates)
    }

    @Test
    public fun `module transition should ignore non table non string return`() {
        val machine = WeaponLuaAnimationStateMachine(
            scriptId = "test:module_invalid_transition",
            scriptSource = """
                local M = {
                  flow = {
                    idle = {
                      transition = function(this, context, input)
                        if input == 'go' then
                          return true
                        end
                        return nil
                      end
                    }
                  }
                }

                function M:states()
                  return { self.flow.idle }
                end

                return M
            """.trimIndent()
        )

        val tick = machine.tick(mapOf("input" to "go"))

        assertFalse(tick.failed)
        assertEquals(setOf("flow.idle"), tick.activeStates)
    }

    @Test
    public fun `module transition should accept ad hoc named state table`() {
        val machine = WeaponLuaAnimationStateMachine(
            scriptId = "test:module_ad_hoc_state",
            scriptSource = """
                local detached = {
                  name = 'detached_custom_state',
                  transition = function(this, context, input)
                    return nil
                  end
                }

                local M = {
                  flow = {
                    idle = {
                      transition = function(this, context, input)
                        if input == 'detach' then
                          return detached
                        end
                        return nil
                      end
                    }
                  }
                }

                function M:states()
                  return { self.flow.idle }
                end

                return M
            """.trimIndent()
        )

        val tick = machine.tick(mapOf("input" to "detach"))

        assertFalse(tick.failed)
        assertEquals(setOf("detached_custom_state"), tick.activeStates)
    }

    @Test
    public fun `module shutdown should call module exit path safely`() {
        val machine = WeaponLuaAnimationStateMachine(
            scriptId = "test:module_shutdown_exit",
            scriptSource = """
                local M = {
                  flow = {
                    idle = {}
                  },
                  exited = false
                }

                function M:states()
                  return { self.flow.idle }
                end

                function M:exit(ctx)
                  self.exited = true
                end

                return M
            """.trimIndent()
        )

        val init = machine.initialize()
        assertFalse(init.failed)

        val shutdown = machine.shutdown()
        assertFalse(shutdown.failed)
    }

    @Test
    public fun `context conversion should handle blank keys and custom objects`() {
        val machine = WeaponLuaAnimationStateMachine(
            scriptId = "test:context_conversion",
            scriptSource = """
                states = {
                  idle = {
                    transition = function(ctx)
                      if tostring(ctx.custom_value) == 'marker' and ctx.flag == true and ctx.count == 3 then
                        return 'next'
                      end
                      return nil
                    end
                  },
                  next = {}
                }

                function initialize(ctx)
                  return 'idle'
                end
            """.trimIndent()
        )

        data class Marker(val v: String = "marker") {
            override fun toString(): String = v
        }

        val tick = machine.tick(
            mapOf(
                "" to "ignored",
                "custom_value" to Marker(),
                "flag" to true,
                "count" to 3L
            )
        )

        assertFalse(tick.failed)
        assertEquals(setOf("next"), tick.activeStates)
    }

    @Test
    public fun `legacy hooks should execute non transition call paths and global exit`() {
        val machine = WeaponLuaAnimationStateMachine(
            scriptId = "test:legacy_hooks_and_exit",
            scriptSource = """
                states = {
                  idle = {
                    entry = function(ctx)
                      _G.__entered = true
                    end,
                    update = function(ctx)
                      if _G.__entered then
                        _G.__updated = true
                      end
                    end,
                    transition = function(ctx, input)
                      if _G.__updated and input == 'go' then
                        return 'done'
                      end
                      return nil
                    end,
                    exit = function(ctx)
                      _G.__legacy_state_exited = true
                    end
                  },
                  done = {}
                }

                function initialize(ctx)
                  return 'idle'
                end

                function exit(ctx)
                  if _G.__legacy_state_exited ~= true then
                    error('state exit was not invoked')
                  end
                end
            """.trimIndent()
        )

        val init = machine.initialize()
        assertFalse(init.failed)
        assertEquals(setOf("idle"), init.activeStates)
        assertEquals(setOf("idle"), machine.currentActiveStates())

        val tick = machine.tick(mapOf("input" to "go"))
        assertFalse(tick.failed)
        assertEquals(setOf("done"), tick.activeStates)

        val shutdown = machine.shutdown()
        assertFalse(shutdown.failed)
    }

    @Test
    public fun `run guard should short circuit after machine entered failed state`() {
        val machine = WeaponLuaAnimationStateMachine(
            scriptId = "test:failed_short_circuit",
            scriptSource = """
                states = {
                  idle = {
                    transition = function(ctx)
                      error('boom_once')
                    end
                  }
                }

                function initialize(ctx)
                  return 'idle'
                end
            """.trimIndent()
        )

        val firstTick = machine.tick()
        assertTrue(firstTick.failed)

        val secondTick = machine.tick()
        assertTrue(secondTick.failed)
        assertEquals(firstTick.message, secondTick.message)
    }

    @Test
    public fun `module initialize should fallback to root idle table when states and initialize are absent`() {
        val machine = WeaponLuaAnimationStateMachine(
            scriptId = "test:module_idle_fallback",
            scriptSource = """
                local M = {
                  idle = {}
                }

                function M:exit(ctx)
                end

                return M
            """.trimIndent()
        )

        val init = machine.initialize()
        assertFalse(init.failed)
        assertEquals(setOf("idle"), init.activeStates)
    }

    @Test
    public fun `module initialize should fallback to legacy idle state when module idle is absent`() {
        val machine = WeaponLuaAnimationStateMachine(
            scriptId = "test:module_to_legacy_idle_fallback",
            scriptSource = """
                states = {
                  idle = {}
                }

                local M = {
                  marker = {}
                }

                function M:exit(ctx)
                end

                return M
            """.trimIndent()
        )

        val init = machine.initialize()
        assertFalse(init.failed)
        assertEquals(setOf("idle"), init.activeStates)
    }

    @Test
    public fun `module transition should ignore blank string state name`() {
        val machine = WeaponLuaAnimationStateMachine(
            scriptId = "test:module_blank_state_name",
            scriptSource = """
                local M = {
                  flow = {
                    idle = {
                      transition = function(this, context, input)
                        return '   '
                      end
                    }
                  }
                }

                function M:states()
                  return { self.flow.idle }
                end

                return M
            """.trimIndent()
        )

        val tick = machine.tick(mapOf("input" to "any"))

        assertFalse(tick.failed)
        assertEquals(setOf("flow.idle"), tick.activeStates)
    }

    @Test
    public fun `module transition should allow unknown string state and keep running`() {
        val machine = WeaponLuaAnimationStateMachine(
            scriptId = "test:module_unknown_state_name",
            scriptSource = """
                states = {}

                local M = {
                  flow = {
                    idle = {
                      transition = function(this, context, input)
                        if input == 'ghost' then
                          return 'ghost_state'
                        end
                        return nil
                      end
                    }
                  }
                }

                function M:states()
                  return { self.flow.idle }
                end

                return M
            """.trimIndent()
        )

        val ghost = machine.tick(mapOf("input" to "ghost"))
        assertFalse(ghost.failed)
        assertEquals(setOf("ghost_state"), ghost.activeStates)

        val keep = machine.tick(mapOf("input" to "noop"))
        assertFalse(keep.failed)
        assertEquals(setOf("ghost_state"), keep.activeStates)
    }

    @Test
    public fun `module scanner should handle numeric and non string keys and deduplicate shared tables`() {
        val machine = WeaponLuaAnimationStateMachine(
            scriptId = "test:module_scan_numeric_and_shared",
            scriptSource = """
                local shared = {
                  transition = function(this, context, input)
                    return nil
                  end
                }

                local M = {
                  graph = {
                    [1] = shared,
                    [2] = shared,
                    [true] = {
                      transition = function(this, context, input)
                        return nil
                      end
                    }
                  }
                }

                function M:states()
                  return { self.graph[1] }
                end

                return M
            """.trimIndent()
        )

        val init = machine.initialize()

        assertFalse(init.failed)
        assertEquals(setOf("graph.1"), init.activeStates)
    }

    @Test
    public fun `module scanner should fall back to anonymous state for over depth state tables`() {
        val machine = WeaponLuaAnimationStateMachine(
            scriptId = "test:module_scan_over_depth",
            scriptSource = """
                local leaf = {
                  transition = function(this, context, input)
                    return nil
                  end
                }

                local M = {
                  n1 = {
                    n2 = {
                      n3 = {
                        n4 = {
                          n5 = {
                            n6 = {
                              n7 = {
                                n8 = {
                                  n9 = {
                                    state = leaf
                                  }
                                }
                              }
                            }
                          }
                        }
                      }
                    }
                  }
                }

                function M:states()
                  return { self.n1.n2.n3.n4.n5.n6.n7.n8.n9.state }
                end

                return M
            """.trimIndent()
        )

        val init = machine.initialize()

        assertFalse(init.failed)
        assertEquals(1, init.activeStates.size)
        assertTrue(init.activeStates.first().startsWith("state_"))
    }

    @Test
    public fun `module protocol should be recognized by initialize function without states function`() {
        val machine = WeaponLuaAnimationStateMachine(
            scriptId = "test:module_protocol_initialize_only",
            scriptSource = """
                local M = {
                  flow = {
                    idle = {}
                  }
                }

                function M:initialize(ctx)
                  return self.flow.idle
                end

                return M
            """.trimIndent()
        )

        val init = machine.initialize()
        assertFalse(init.failed)
        assertEquals(setOf("flow.idle"), init.activeStates)
    }

    @Test
    public fun `module transition should resolve module state by string name`() {
        val machine = WeaponLuaAnimationStateMachine(
            scriptId = "test:module_transition_string_to_module_state",
            scriptSource = """
                local M = {
                  flow = {
                    idle = {
                      transition = function(this, context, input)
                        if input == 'to_fire' then
                          return 'flow.fire'
                        end
                        return nil
                      end
                    },
                    fire = {}
                  }
                }

                function M:states()
                  return { self.flow.idle }
                end

                return M
            """.trimIndent()
        )

        val tick = machine.tick(mapOf("input" to "to_fire"))

        assertFalse(tick.failed)
        assertEquals(setOf("flow.fire"), tick.activeStates)
    }

    @Test
    public fun `module transition should resolve legacy state by string name fallback`() {
        val machine = WeaponLuaAnimationStateMachine(
            scriptId = "test:module_transition_string_to_legacy_state",
            scriptSource = """
                states = {
                  legacy_fire = {}
                }

                local M = {
                  flow = {
                    idle = {
                      transition = function(this, context, input)
                        if input == 'to_legacy' then
                          return 'legacy_fire'
                        end
                        return nil
                      end
                    }
                  }
                }

                function M:states()
                  return { self.flow.idle }
                end

                return M
            """.trimIndent()
        )

        val tick = machine.tick(mapOf("input" to "to_legacy"))

        assertFalse(tick.failed)
        assertEquals(setOf("legacy_fire"), tick.activeStates)
    }

    @Test
    public fun `legacy state table should be resolved lazily for active state without cached table`() {
        val machine = WeaponLuaAnimationStateMachine(
            scriptId = "test:legacy_lazy_state_table_resolution",
            scriptSource = """
                local late_lookup_count = 0

                states = {
                  idle = {
                    transition = function(ctx)
                      if ctx.input == 'late' then
                        return 'late'
                      end
                      return nil
                    end
                  }
                }

                setmetatable(states, {
                  __index = function(t, key)
                    if key ~= 'late' then
                      return nil
                    end

                    late_lookup_count = late_lookup_count + 1
                    if late_lookup_count >= 2 then
                      local created = {
                        transition = function(ctx)
                          return nil
                        end
                      }
                      rawset(t, key, created)
                      return created
                    end
                    return nil
                  end
                })

                function initialize(ctx)
                  return 'idle'
                end
            """.trimIndent()
        )

        val late = machine.tick(mapOf("input" to "late"))
        assertFalse(late.failed)
        assertEquals(setOf("late"), late.activeStates)

        val steady = machine.tick(mapOf("input" to "noop"))
        assertFalse(steady.failed)
        assertEquals(setOf("late"), steady.activeStates)
    }

    @Test
    public fun `module update hook should execute non transition owner based call path`() {
        val machine = WeaponLuaAnimationStateMachine(
            scriptId = "test:module_update_non_transition_path",
            scriptSource = """
                local M = {
                  flow = {
                    idle = {
                      update = function(this, context)
                        this._updated_by_idle = true
                      end,
                      transition = function(this, context, input)
                        if this._updated_by_idle and input == 'next' then
                          return this.flow.done
                        end
                        return nil
                      end
                    },
                    done = {}
                  }
                }

                function M:states()
                  return { self.flow.idle }
                end

                return M
            """.trimIndent()
        )

        val tick = machine.tick(mapOf("input" to "next"))

        assertFalse(tick.failed)
        assertEquals(setOf("flow.done"), tick.activeStates)
    }
}
