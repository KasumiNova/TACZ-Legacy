package com.tacz.legacy.common.application.weapon

import org.luaj.vm2.Globals
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.OneArgFunction
import java.nio.charset.StandardCharsets

/**
 * TACZ-Legacy Lua require 兼容层：
 * - 预注册常用基础模块（default/manual action state machine）
 * - 包装 require，对未知模块做按需 classpath 解析并注入 package.preload
 */
public object WeaponLuaRequireSupport {

    public fun install(globals: Globals, scriptId: String) {
        val packageValue = globals.get(LUA_PACKAGE_TABLE)
        if (!packageValue.istable()) {
            return
        }

        val packageTable = packageValue.checktable()
        val preloadTable = packageTable.get(LUA_PRELOAD_TABLE).takeIf { it.istable() }?.checktable()
            ?: LuaTable().also { packageTable.set(LUA_PRELOAD_TABLE, it) }

        PRELOADED_REQUIRE_MODULES.forEach { (moduleName, resourcePath) ->
            if (preloadTable.get(moduleName).isfunction()) {
                return@forEach
            }

            val source = readClasspathText(resourcePath) ?: return@forEach
            preloadTable.set(moduleName, buildModuleLoader(globals, resourcePath, source))
        }

        val originalRequire = globals.get(LUA_REQUIRE_FUNCTION)
        if (!originalRequire.isfunction()) {
            return
        }

        globals.set(LUA_REQUIRE_FUNCTION, object : OneArgFunction() {
            override fun call(arg: LuaValue): LuaValue {
                val moduleName = arg.optjstring(null)?.trim()?.ifBlank { null }
                if (moduleName != null && !preloadTable.get(moduleName).isfunction()) {
                    resolveDynamicModuleSource(moduleName, scriptId)?.let { resolved ->
                        preloadTable.set(
                            moduleName,
                            buildModuleLoader(globals, resolved.resourcePath, resolved.source)
                        )
                    }
                }
                return originalRequire.call(arg)
            }
        })
    }

    private fun buildModuleLoader(globals: Globals, resourcePath: String, source: String): LuaValue {
        return object : OneArgFunction() {
            override fun call(arg: LuaValue): LuaValue {
                return globals.load(source, "@$resourcePath").call()
            }
        }
    }

    private fun resolveDynamicModuleSource(moduleName: String, scriptId: String): ResolvedModuleSource? {
        PRELOADED_REQUIRE_MODULES[moduleName]?.let { predefinedPath ->
            val predefinedSource = readClasspathText(predefinedPath)
            if (!predefinedSource.isNullOrBlank()) {
                return ResolvedModuleSource(predefinedPath, predefinedSource)
            }
        }

        val candidates = resolveDynamicModuleResourceCandidates(moduleName, scriptId)
        candidates.forEach { resourcePath ->
            val source = readClasspathText(resourcePath)
            if (!source.isNullOrBlank()) {
                return ResolvedModuleSource(resourcePath, source)
            }
        }
        return null
    }

    private fun resolveDynamicModuleResourceCandidates(moduleName: String, scriptId: String): List<String> {
        val normalized = moduleName.trim().ifBlank { return emptyList() }
        val out = linkedSetOf<String>()

        val delimiter = normalized.indexOf('_')
        if (delimiter > 0 && delimiter < normalized.length - 1) {
            val namespace = normalized.substring(0, delimiter)
            val pathToken = normalized.substring(delimiter + 1)
            collectNamespaceScriptCandidates(out, namespace, pathToken)
        }

        scriptDirectoryFromScriptId(scriptId)?.let { baseDir ->
            out += "$baseDir/$normalized.lua"
            val slashPath = normalized.replace('_', '/')
            if (slashPath != normalized) {
                out += "$baseDir/$slashPath.lua"
            }
        }

        if (normalized.startsWith("assets/") && normalized.endsWith(".lua")) {
            out += normalized.removePrefix("/")
        }

        return out.toList()
    }

    private fun collectNamespaceScriptCandidates(
        out: MutableSet<String>,
        namespace: String,
        pathToken: String
    ) {
        val normalizedNamespace = namespace.trim().ifBlank { return }
        val normalizedPathToken = pathToken.trim().ifBlank { return }

        val rawPath = normalizedPathToken
        val slashPath = normalizedPathToken.replace('_', '/')

        out += "assets/$normalizedNamespace/custom/tacz_default_gun/assets/$normalizedNamespace/scripts/$rawPath.lua"
        if (slashPath != rawPath) {
            out += "assets/$normalizedNamespace/custom/tacz_default_gun/assets/$normalizedNamespace/scripts/$slashPath.lua"
        }
        out += "assets/$normalizedNamespace/scripts/$rawPath.lua"
        if (slashPath != rawPath) {
            out += "assets/$normalizedNamespace/scripts/$slashPath.lua"
        }
    }

    private fun scriptDirectoryFromScriptId(scriptId: String): String? {
        val normalized = scriptId.trim().removePrefix("@").ifBlank { return null }
        if (!normalized.endsWith(".lua")) {
            return null
        }
        val slash = normalized.lastIndexOf('/')
        if (slash <= 0) {
            return null
        }
        return normalized.substring(0, slash)
    }

    private fun readClasspathText(resourcePath: String): String? {
        val normalized = resourcePath.removePrefix("/")
        val classLoader = javaClass.classLoader ?: return null
        val stream = classLoader.getResourceAsStream(normalized) ?: return null
        return stream.use { input ->
            String(input.readBytes(), StandardCharsets.UTF_8)
        }
    }

    private data class ResolvedModuleSource(
        val resourcePath: String,
        val source: String
    )

    private const val LUA_PACKAGE_TABLE: String = "package"
    private const val LUA_PRELOAD_TABLE: String = "preload"
    private const val LUA_REQUIRE_FUNCTION: String = "require"

    private val PRELOADED_REQUIRE_MODULES: Map<String, String> = linkedMapOf(
        "tacz_default_state_machine" to "assets/tacz/custom/tacz_default_gun/assets/tacz/scripts/default_state_machine.lua",
        "tacz_manual_action_state_machine" to "assets/tacz/custom/tacz_default_gun/assets/tacz/scripts/manual_action_state_machine.lua"
    )
}
