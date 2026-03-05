package com.tacz.legacy.client.animation

import com.tacz.legacy.api.client.animation.AnimationController
import com.tacz.legacy.api.client.animation.Animations
import com.tacz.legacy.api.client.animation.ObjectAnimation
import com.tacz.legacy.api.client.animation.statemachine.LuaAnimationStateMachine
import com.tacz.legacy.api.client.animation.statemachine.LuaStateMachineFactory
import com.tacz.legacy.client.animation.model.GunAnimatedModel
import com.tacz.legacy.client.animation.statemachine.GunAnimationConstant
import com.tacz.legacy.client.animation.statemachine.GunAnimationStateContext
import com.tacz.legacy.client.render.item.LegacyAnimationPose
import com.tacz.legacy.client.sound.SoundPlayManager
import com.tacz.legacy.common.application.gunpack.GunDisplayDefinition
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.LoadState
import org.luaj.vm2.lib.Bit32Lib
import org.luaj.vm2.lib.PackageLib
import org.luaj.vm2.lib.StringLib
import org.luaj.vm2.lib.TableLib
import org.luaj.vm2.lib.jse.CoerceJavaToLua
import org.luaj.vm2.lib.jse.JseBaseLib
import org.luaj.vm2.lib.jse.JseMathLib
import org.luaj.vm2.compiler.LuaC
import com.tacz.legacy.common.application.weapon.WeaponLuaRequireSupport
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly

/**
 * 对标 TACZ 上游 GunDisplayInstance.checkAnimation()：
 * 持有 AnimationController + LuaAnimationStateMachine<GunAnimationStateContext>。
 *
 * 由 [LegacyGunDisplayInstanceRegistry] 按 gun/session 缓存。
 */
@SideOnly(Side.CLIENT)
internal class LegacyGunDisplayInstance private constructor(
    val animationController: AnimationController,
    val animationStateMachine: LuaAnimationStateMachine<GunAnimationStateContext>,
    val gunAnimatedModel: GunAnimatedModel,
    val stateMachineParam: LuaTable?
) {
    companion object {
        /**
         * 从已解析的动画原型和 Lua 脚本构建实例。
         * 对标 GunDisplayInstance.checkAnimation()。
         */
        fun create(
            primaryAnimations: List<ObjectAnimation>,
            defaultAnimations: List<ObjectAnimation>,
            scriptSource: String,
            scriptId: String,
            stateMachineParams: Map<String, Float>?
        ): LegacyGunDisplayInstance? {
            if (primaryAnimations.isEmpty() && defaultAnimations.isEmpty()) {
                return null
            }

            // 收集所有骨骼名
            val allBones = linkedSetOf<String>()
            primaryAnimations.forEach { anim -> allBones.addAll(anim.channels.keys) }
            defaultAnimations.forEach { anim -> allBones.addAll(anim.channels.keys) }

            // 创建 GunAnimatedModel（bone listener supplier）
            val model = GunAnimatedModel(allBones)

            // 创建 AnimationController，注册所有动画原型
            // primary 先注册，default 仅在 primary 中不存在时补充（providePrototypeIfAbsent）
            val controller = AnimationController(primaryAnimations, model)
            for (anim in defaultAnimations) {
                controller.providePrototypeIfAbsent(anim.name) { ObjectAnimation(anim) }
            }

            // 编译 Lua 脚本
            val scriptTable = compileLuaScript(scriptSource, scriptId) ?: return null

            // 构建状态机
            val stateMachine = LuaStateMachineFactory<GunAnimationStateContext>()
                .setController(controller)
                .setLuaScripts(scriptTable)
                .build()

            // Lua 状态机参数
            val paramTable = if (!stateMachineParams.isNullOrEmpty()) {
                LuaTable().also { table ->
                    stateMachineParams.forEach { (key, value) ->
                        table.set(key, CoerceJavaToLua.coerce(value))
                    }
                }
            } else {
                null
            }

            return LegacyGunDisplayInstance(
                animationController = controller,
                animationStateMachine = stateMachine,
                gunAnimatedModel = model,
                stateMachineParam = paramTable
            )
        }

        /**
         * 编译 Lua 脚本源码并返回模块 LuaTable。
         * 对标 TACZ 上游 ScriptManager：安全 Globals（无 IO/OS），
         * 脚本 return 的 table 含 initialize/states/exit。
         */
        private fun compileLuaScript(scriptSource: String, scriptId: String): LuaTable? {
            return try {
                val globals = createSecureGlobals()
                WeaponLuaRequireSupport.install(globals, scriptId)
                val chunk = globals.load(scriptSource, "@$scriptId")
                val result = chunk.call()
                if (result.istable()) result.checktable() else null
            } catch (e: Exception) {
                null
            }
        }

        /**
         * 匹配 TACZ 上游 ScriptManager.secureStandardGlobals()
         */
        private fun createSecureGlobals(): Globals {
            val globals = Globals()
            globals.load(JseBaseLib())
            globals.load(PackageLib())
            globals.load(Bit32Lib())
            globals.load(TableLib())
            globals.load(StringLib())
            globals.load(JseMathLib())
            LoadState.install(globals)
            LuaC.install(globals)
            return globals
        }
    }

    /**
     * 初始化状态机（对标 AnimateGeoItemRenderer.tryInit）。
     * 必须在首次 update 前调用一次。
     */
    fun initStateMachine(context: GunAnimationStateContext) {
        if (animationStateMachine.isInitialized) {
            animationStateMachine.exit()
        }
        animationStateMachine.setContext(context)
        animationStateMachine.initialize()
        animationStateMachine.trigger(GunAnimationConstant.INPUT_DRAW)
    }

    /**
     * 退出状态机（对标 AnimateGeoItemRenderer.tryExit）。
     */
    fun exitStateMachine(putAwayTimeMs: Long) {
        animationStateMachine.processContextIfExist { ctx ->
            ctx.setPutAwayTime(putAwayTimeMs / 1000f)
        }
        if (animationStateMachine.isInitialized) {
            animationStateMachine.trigger(GunAnimationConstant.INPUT_PUT_AWAY)
            animationStateMachine.exit()
            animationStateMachine.setExitingTime(putAwayTimeMs + 50)
        }
    }

    /**
     * 每帧调用：更新上下文、驱动状态机 + 控制器、收集骨骼姿态。
     * 对标 AnimateGeoItemRenderer.renderFirstPerson / GunItemRendererWrapper.renderFirstPerson 的核心流程。
     */
    fun updateAndCollectPose(enableSound: Boolean = true): LegacyAnimationPose {
        gunAnimatedModel.cleanAllTransforms()
        // stateMachine.update() 内部调 Lua state.update() + controller.update()
        if (enableSound) {
            animationStateMachine.update()
        } else {
            // visualUpdate 只更新状态但不写入模型
            animationStateMachine.visualUpdate()
        }
        return gunAnimatedModel.collectPose()
    }

    /**
     * 是否需要重新初始化（切换到同把枪后 exit 超时已过）。
     */
    fun needReInit(): Boolean {
        return !animationStateMachine.isInitialized
                && animationStateMachine.exitingTime < System.currentTimeMillis()
    }
}
