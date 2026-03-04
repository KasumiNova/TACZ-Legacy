package com.tacz.legacy.common.application.weapon

import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Globals
import org.luaj.vm2.lib.jse.JsePlatform
import java.util.IdentityHashMap

public data class WeaponLuaAnimationTickResult(
    val activeStates: Set<String>,
    val enteredStates: Set<String> = emptySet(),
    val exitedStates: Set<String> = emptySet(),
    val failed: Boolean = false,
    val message: String? = null
)

/**
 * Lua 动画状态机最小执行内核（骨架版）：
 * - 支持 initialize/exit 全局函数（可选）
 * - 支持 states[state].entry/update/transition/exit（可选）
 * - 支持多状态并行（Set<String>）
 *
 * 说明：
 * - 该类仅提供“可执行基础能力”，不直接绑定武器数值逻辑。
 * - 运行失败后会进入 failed 状态，调用方可据此回退到 signal-based 路径。
 */
public class WeaponLuaAnimationStateMachine(
    private val scriptId: String,
    private val scriptSource: String
) {

    private val globals: Globals = JsePlatform.standardGlobals()
    private val activeStates: LinkedHashMap<String, ActiveState> = linkedMapOf()

    private var protocolMode: LuaProtocolMode = LuaProtocolMode.LEGACY_GLOBAL_STATES
    private var moduleRoot: LuaTable? = null
    private val moduleStateNameByRef: IdentityHashMap<LuaTable, String> = IdentityHashMap()
    private val moduleStateRefByName: MutableMap<String, LuaTable> = linkedMapOf()
    private var anonymousStateCounter: Long = 0L

    private var loaded: Boolean = false
    private var initialized: Boolean = false
    private var failed: Boolean = false
    private var lastErrorMessage: String? = null

    init {
        WeaponLuaRequireSupport.install(globals, scriptId)
        loadScript()
    }

    public fun isAvailable(): Boolean = loaded && !failed

    public fun currentActiveStates(): Set<String> = activeStates.keys.toSet()

    public fun lastFailureMessage(): String? = lastErrorMessage

    public fun initialize(context: Map<String, Any?> = emptyMap()): WeaponLuaAnimationTickResult {
        if (!isAvailable()) {
            return failureSnapshot("lua animation state machine unavailable")
        }
        if (initialized) {
            return successSnapshot()
        }

        val contextTable = buildContextTable(context)
        val inputValue = resolveInputValue(contextTable)
        return runGuard {
            val initial = when (protocolMode) {
                LuaProtocolMode.LEGACY_GLOBAL_STATES -> initializeLegacy(contextTable)
                LuaProtocolMode.MODULE_TABLE -> initializeModule(contextTable)
            }

            activeStates.clear()
            initial.forEach { state ->
                activeStates[state.name] = state
            }

            val entered = linkedSetOf<String>()
            activeStates.values.forEach { state ->
                callStateHook(state, STATE_ENTRY_FN, contextTable, inputValue)
                entered += state.name
            }

            initialized = true
            WeaponLuaAnimationTickResult(
                activeStates = activeStates.keys.toSet(),
                enteredStates = entered,
                failed = false
            )
        }
    }

    public fun tick(context: Map<String, Any?> = emptyMap()): WeaponLuaAnimationTickResult {
        if (!initialized) {
            val init = initialize(context)
            if (init.failed) {
                return init
            }
        }

        val contextTable = buildContextTable(context)
        val inputValue = resolveInputValue(contextTable)
        return runGuard {
            val previous = activeStates.values.toList()

            previous.forEach { state ->
                callStateHook(state, STATE_UPDATE_FN, contextTable, inputValue)
            }

            val next = linkedMapOf<String, ActiveState>()
            previous.forEach { state ->
                val transitionResult = callStateHook(state, STATE_TRANSITION_FN, contextTable, inputValue)
                val transitioned = readTransitionTargets(transitionResult)
                if (transitioned.isEmpty()) {
                    next[state.name] = state
                } else {
                    transitioned.forEach { candidate ->
                        next.putIfAbsent(candidate.name, candidate)
                    }
                }
            }

            val previousNames = previous.map { it.name }.toSet()
            val nextNames = next.keys.toSet()
            val exited = previousNames.filterNot(nextNames::contains).toSet()
            val entered = nextNames.filterNot(previousNames::contains).toSet()

            exited.forEach { state ->
                previous.firstOrNull { it.name == state }?.let { exiting ->
                    callStateHook(exiting, STATE_EXIT_FN, contextTable, inputValue)
                }
            }
            entered.forEach { state ->
                next[state]?.let { entering ->
                    callStateHook(entering, STATE_ENTRY_FN, contextTable, inputValue)
                }
            }

            activeStates.clear()
            activeStates.putAll(next)

            WeaponLuaAnimationTickResult(
                activeStates = activeStates.keys.toSet(),
                enteredStates = entered,
                exitedStates = exited,
                failed = false
            )
        }
    }

    public fun shutdown(context: Map<String, Any?> = emptyMap()): WeaponLuaAnimationTickResult {
        if (!initialized && !failed) {
            return successSnapshot()
        }

        val contextTable = buildContextTable(context)
        val inputValue = resolveInputValue(contextTable)
        return runGuard {
            val exited = activeStates.keys.toSet()
            activeStates.values.forEach { state ->
                callStateHook(state, STATE_EXIT_FN, contextTable, inputValue)
            }

            when (protocolMode) {
                LuaProtocolMode.LEGACY_GLOBAL_STATES -> {
                    val exitFunction = globals.get(GLOBAL_EXIT_FN)
                    if (exitFunction.isfunction()) {
                        exitFunction.call(contextTable)
                    }
                }

                LuaProtocolMode.MODULE_TABLE -> {
                    val module = moduleRoot
                    val exitFunction = module?.get(MODULE_EXIT_FN)
                    if (module != null && exitFunction != null && exitFunction.isfunction()) {
                        exitFunction.call(module, contextTable)
                    }
                }
            }

            activeStates.clear()
            initialized = false
            WeaponLuaAnimationTickResult(
                activeStates = emptySet(),
                exitedStates = exited,
                failed = false
            )
        }
    }

    private fun loadScript() {
        runCatching {
            val result = globals.load(scriptSource, "@$scriptId").call()
            val moduleCandidate = result.takeIf { it.istable() }?.checktable()
            if (moduleCandidate != null && isModuleProtocol(moduleCandidate)) {
                protocolMode = LuaProtocolMode.MODULE_TABLE
                moduleRoot = moduleCandidate
                indexModuleStateTables(moduleCandidate)
            } else {
                protocolMode = LuaProtocolMode.LEGACY_GLOBAL_STATES
                moduleRoot = null
                moduleStateNameByRef.clear()
                moduleStateRefByName.clear()
            }
            loaded = true
        }.onFailure { throwable ->
            loaded = false
            failed = true
            lastErrorMessage = throwable.message ?: throwable.javaClass.simpleName
        }
    }

    private fun runGuard(action: () -> WeaponLuaAnimationTickResult): WeaponLuaAnimationTickResult {
        if (failed) {
            return failureSnapshot(lastErrorMessage ?: "lua animation state machine failed")
        }

        return runCatching(action).getOrElse { throwable ->
            failed = true
            lastErrorMessage = throwable.message ?: throwable.javaClass.simpleName
            failureSnapshot(lastErrorMessage)
        }
    }

    private fun successSnapshot(): WeaponLuaAnimationTickResult =
        WeaponLuaAnimationTickResult(
            activeStates = activeStates.keys.toSet(),
            failed = false
        )

    private fun failureSnapshot(message: String?): WeaponLuaAnimationTickResult =
        WeaponLuaAnimationTickResult(
            activeStates = activeStates.keys.toSet(),
            failed = true,
            message = message
        )

    private fun callStateHook(state: ActiveState, hookName: String, context: LuaTable, inputValue: LuaValue): LuaValue? {
        val stateTable = resolveStateTable(state) ?: return null
        val hook = stateTable.get(hookName)
        if (!hook.isfunction()) {
            return null
        }

        return when (protocolMode) {
            LuaProtocolMode.LEGACY_GLOBAL_STATES -> {
                if (hookName == STATE_TRANSITION_FN) {
                    hook.call(context, inputValue)
                } else {
                    hook.call(context)
                }
            }

            LuaProtocolMode.MODULE_TABLE -> {
                val owner = moduleRoot ?: LuaValue.NIL
                if (hookName == STATE_TRANSITION_FN) {
                    hook.call(owner, context, inputValue)
                } else {
                    hook.call(owner, context)
                }
            }
        }
    }

    private fun readStateNames(value: LuaValue?): Set<String> {
        if (value == null || value.isnil()) {
            return emptySet()
        }

        if (value.isstring()) {
            val single = normalizeStateName(value.tojstring())
            return if (single != null) setOf(single) else emptySet()
        }

        if (!value.istable()) {
            return emptySet()
        }

        val out = linkedSetOf<String>()
        var key: LuaValue = LuaValue.NIL
        while (true) {
            val pair = value.next(key)
            key = pair.arg1()
            if (key.isnil()) {
                break
            }

            val candidate = pair.arg(2)
            if (candidate.isstring()) {
                normalizeStateName(candidate.tojstring())?.let(out::add)
            }
        }
        return out
    }

    private fun initializeLegacy(contextTable: LuaTable): List<ActiveState> {
        val initialStates = linkedSetOf<String>()
        val initializeFunction = globals.get(GLOBAL_INITIALIZE_FN)
        if (initializeFunction.isfunction()) {
            initialStates += readStateNames(initializeFunction.call(contextTable))
        }

        if (initialStates.isEmpty()) {
            initialStates += DEFAULT_IDLE_STATE
        }

        return initialStates.map(::resolveLegacyState)
    }

    private fun initializeModule(contextTable: LuaTable): List<ActiveState> {
        val module = moduleRoot ?: return listOf(resolveLegacyState(DEFAULT_IDLE_STATE))

        val initializeFunction = module.get(MODULE_INITIALIZE_FN)
        val fromInitialize = if (initializeFunction.isfunction()) {
            readModuleTransitionTargets(initializeFunction.call(module, contextTable))
        } else {
            emptyList()
        }

        val initial = if (fromInitialize.isNotEmpty()) {
            fromInitialize
        } else {
            val statesFunction = module.get(MODULE_STATES_FN)
            if (statesFunction.isfunction()) {
                readModuleTransitionTargets(statesFunction.call(module))
            } else {
                emptyList()
            }
        }

        if (initial.isNotEmpty()) {
            return initial
        }

        val idle = moduleStateRefByName[DEFAULT_IDLE_STATE]
        return if (idle != null) {
            listOf(resolveModuleState(idle))
        } else {
            listOf(resolveLegacyState(DEFAULT_IDLE_STATE))
        }
    }

    private fun readTransitionTargets(value: LuaValue?): List<ActiveState> {
        return when (protocolMode) {
            LuaProtocolMode.LEGACY_GLOBAL_STATES -> readStateNames(value).map(::resolveLegacyState)
            LuaProtocolMode.MODULE_TABLE -> readModuleTransitionTargets(value)
        }
    }

    private fun readModuleTransitionTargets(value: LuaValue?): List<ActiveState> {
        if (value == null || value.isnil()) {
            return emptyList()
        }

        if (value.isstring()) {
            val name = normalizeStateName(value.tojstring()) ?: return emptyList()
            return listOf(resolveStateByName(name))
        }

        if (!value.istable()) {
            return emptyList()
        }

        val table = value.checktable()
        if (isStateTableReference(table)) {
            return listOf(resolveModuleState(table))
        }

        val out = linkedMapOf<String, ActiveState>()
        var key: LuaValue = LuaValue.NIL
        while (true) {
            val pair = table.next(key)
            key = pair.arg1()
            if (key.isnil()) {
                break
            }

            val candidate = pair.arg(2)
            val resolved = readModuleTransitionTargets(candidate)
            resolved.forEach { state -> out.putIfAbsent(state.name, state) }
        }
        return out.values.toList()
    }

    private fun resolveStateTable(state: ActiveState): LuaTable? {
        state.table?.let { return it }
        if (protocolMode == LuaProtocolMode.LEGACY_GLOBAL_STATES) {
            val table = resolveLegacyStateTable(state.name)
            if (table != null) {
                return table
            }
        }
        return moduleStateRefByName[state.name]
    }

    private fun resolveLegacyState(name: String): ActiveState = ActiveState(
        name = name,
        table = resolveLegacyStateTable(name)
    )

    private fun resolveLegacyStateTable(name: String): LuaTable? {
        val table = globals.get(STATES_TABLE).get(name)
        return if (table.istable()) table.checktable() else null
    }

    private fun resolveStateByName(name: String): ActiveState {
        val moduleTable = moduleStateRefByName[name]
        if (moduleTable != null) {
            return ActiveState(name, moduleTable)
        }

        val legacy = resolveLegacyStateTable(name)
        if (legacy != null) {
            return ActiveState(name, legacy)
        }

        return ActiveState(name, null)
    }

    private fun resolveModuleState(stateTable: LuaTable): ActiveState {
        val cached = moduleStateNameByRef[stateTable]
        if (cached != null) {
            return ActiveState(cached, stateTable)
        }

        val explicitName = normalizeStateName(stateTable.get(STATE_NAME_FIELD).optjstring(null))
        val resolvedName = explicitName ?: "state_${++anonymousStateCounter}"
        moduleStateNameByRef[stateTable] = resolvedName
        moduleStateRefByName.putIfAbsent(resolvedName, stateTable)
        return ActiveState(resolvedName, stateTable)
    }

    private fun isStateTableReference(table: LuaTable): Boolean {
        if (moduleStateNameByRef.containsKey(table)) {
            return true
        }

        return table.get(STATE_ENTRY_FN).isfunction() ||
            table.get(STATE_UPDATE_FN).isfunction() ||
            table.get(STATE_TRANSITION_FN).isfunction() ||
            table.get(STATE_EXIT_FN).isfunction()
    }

    private fun isModuleProtocol(module: LuaTable): Boolean {
        return module.get(MODULE_STATES_FN).isfunction() ||
            module.get(MODULE_INITIALIZE_FN).isfunction() ||
            module.get(MODULE_EXIT_FN).isfunction()
    }

    private fun indexModuleStateTables(module: LuaTable) {
        moduleStateNameByRef.clear()
        moduleStateRefByName.clear()

        val visited: IdentityHashMap<LuaTable, Boolean> = IdentityHashMap()
        fun walk(value: LuaValue, path: String, depth: Int) {
            if (!value.istable() || depth > MODULE_STATE_SCAN_MAX_DEPTH) {
                return
            }

            val table = value.checktable()
            if (visited.put(table, true) != null) {
                return
            }

            if (path.isNotBlank()) {
                moduleStateNameByRef.putIfAbsent(table, path)
                moduleStateRefByName.putIfAbsent(path, table)
            }

            var key: LuaValue = LuaValue.NIL
            while (true) {
                val pair = table.next(key)
                key = pair.arg1()
                if (key.isnil()) {
                    break
                }

                val child = pair.arg(2)
                if (!child.istable()) {
                    continue
                }

                val segment = when {
                    key.isstring() -> key.tojstring().trim().ifBlank { null }
                    key.isnumber() -> key.tojstring().trim().ifBlank { null }
                    else -> null
                } ?: continue

                val childPath = if (path.isBlank()) segment else "$path.$segment"
                walk(child, childPath, depth + 1)
            }
        }

        walk(module, "", 0)
    }

    private fun resolveInputValue(contextTable: LuaTable): LuaValue {
        val direct = contextTable.get(CONTEXT_INPUT_KEY)
        if (!direct.isnil()) {
            return direct
        }

        val fallback = contextTable.get(CONTEXT_INPUT_TOKEN_KEY)
        if (!fallback.isnil()) {
            return fallback
        }

        return LuaValue.NIL
    }

    private fun normalizeStateName(raw: String?): String? {
        val normalized = raw?.trim()?.ifBlank { null } ?: return null
        return normalized
    }

    private fun buildContextTable(context: Map<String, Any?>): LuaTable {
        val table = LuaTable()
        context.forEach { (key, value) ->
            if (key.isBlank()) {
                return@forEach
            }
            table.set(key, toLuaValue(value))
        }
        return table
    }

    private fun toLuaValue(value: Any?): LuaValue {
        return when (value) {
            null -> LuaValue.NIL
            is LuaValue -> value
            is Boolean -> LuaValue.valueOf(value)
            is Int -> LuaValue.valueOf(value)
            is Long -> LuaValue.valueOf(value.toDouble())
            is Float -> LuaValue.valueOf(value.toDouble())
            is Double -> LuaValue.valueOf(value)
            is String -> LuaValue.valueOf(value)
            else -> LuaValue.valueOf(value.toString())
        }
    }

    private data class ActiveState(
        val name: String,
        val table: LuaTable?
    )

    private enum class LuaProtocolMode {
        LEGACY_GLOBAL_STATES,
        MODULE_TABLE
    }

    private companion object {
        private const val GLOBAL_INITIALIZE_FN: String = "initialize"
        private const val GLOBAL_EXIT_FN: String = "exit"
        private const val STATES_TABLE: String = "states"

        private const val MODULE_STATES_FN: String = "states"
        private const val MODULE_INITIALIZE_FN: String = "initialize"
        private const val MODULE_EXIT_FN: String = "exit"

        private const val STATE_ENTRY_FN: String = "entry"
        private const val STATE_UPDATE_FN: String = "update"
        private const val STATE_TRANSITION_FN: String = "transition"
        private const val STATE_EXIT_FN: String = "exit"
        private const val STATE_NAME_FIELD: String = "name"

        private const val DEFAULT_IDLE_STATE: String = "idle"
        private const val MODULE_STATE_SCAN_MAX_DEPTH: Int = 8

        private const val CONTEXT_INPUT_KEY: String = "input"
        private const val CONTEXT_INPUT_TOKEN_KEY: String = "input_token"
    }
}
