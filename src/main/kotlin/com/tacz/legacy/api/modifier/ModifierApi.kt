package com.tacz.legacy.api.modifier

import net.minecraft.util.text.ITextComponent
import org.luaj.vm2.script.LuaScriptEngineFactory
import java.util.Locale

public data class Modifier(
    val addend: Double = 0.0,
    val percent: Double = 0.0,
    val multiplier: Double = 1.0,
    val function: String? = null,
)

public class CacheValue<T>(public var value: T)

public abstract class JsonProperty<T>(private var value: T?) {
    protected val componentList: MutableList<ITextComponent> = mutableListOf()

    public fun getValue(): T? = value

    public fun setValue(value: T?): Unit {
        this.value = value
    }

    public fun getComponents(): List<ITextComponent> = componentList.toList()

    public abstract fun initComponents()
}

public interface IAttachmentModifier<T, K> {
    public fun getId(): String

    public fun getOptionalFields(): String = ""

    public fun readJson(json: String): JsonProperty<T>

    public fun initCache(defaultValue: K): CacheValue<K>

    public fun eval(modifiedValues: List<T>, cache: CacheValue<K>): Unit
}

public class ParameterizedCache<T> private constructor(
    modifiers: List<Modifier>,
    private val defaultValue: T,
) {
    private val scripts: List<String>
    private val addend: Double
    private val percent: Double
    private val multiplier: Double

    init {
        var addendAccumulator = 0.0
        var percentAccumulator = 1.0
        var multiplierAccumulator = 1.0
        val scriptAccumulator = mutableListOf<String>()
        modifiers.forEach { modifier ->
            addendAccumulator += modifier.addend
            percentAccumulator += modifier.percent
            multiplierAccumulator *= modifier.multiplier.coerceAtLeast(0.0)
            if (!modifier.function.isNullOrBlank()) {
                scriptAccumulator += modifier.function
            }
        }
        addend = addendAccumulator
        percent = percentAccumulator
        multiplier = multiplierAccumulator
        scripts = scriptAccumulator.toList()
    }

    public fun getDefaultValue(): T = defaultValue

    public fun eval(input: Double): Double {
        var value = (input + addend) * percent.coerceAtLeast(0.0) * multiplier
        scripts.forEach { function ->
            value = ModifierEvaluator.functionEval(value, input, function)
        }
        return value
    }

    public fun eval(input: Double, extraAddend: Double, extraPercent: Double, extraMultiplier: Double): Double {
        var value = (input + addend + extraAddend) * (percent + extraPercent).coerceAtLeast(0.0) * multiplier * extraMultiplier.coerceAtLeast(0.0)
        scripts.forEach { function ->
            value = ModifierEvaluator.functionEval(value, input, function)
        }
        return value
    }

    public companion object {
        @JvmStatic
        public fun <T> of(defaultValue: T): ParameterizedCache<T> = ParameterizedCache(emptyList(), defaultValue)

        @JvmStatic
        public fun <T> of(modifiers: List<Modifier>, defaultValue: T): ParameterizedCache<T> = ParameterizedCache(modifiers, defaultValue)
    }
}

public class ParameterizedCachePair<L, R> private constructor(
    private val leftCache: ParameterizedCache<L>,
    private val rightCache: ParameterizedCache<R>,
) {
    public fun left(): ParameterizedCache<L> = leftCache

    public fun right(): ParameterizedCache<R> = rightCache

    public companion object {
        @JvmStatic
        public fun <L, R> of(defaultLeft: L, defaultRight: R): ParameterizedCachePair<L, R> =
            ParameterizedCachePair(ParameterizedCache.of(defaultLeft), ParameterizedCache.of(defaultRight))

        @JvmStatic
        public fun <L, R> of(
            left: List<Modifier>,
            right: List<Modifier>,
            defaultLeft: L,
            defaultRight: R,
        ): ParameterizedCachePair<L, R> = ParameterizedCachePair(
            ParameterizedCache.of(left, defaultLeft),
            ParameterizedCache.of(right, defaultRight),
        )
    }
}

public object ModifierEvaluator {
    private val luajEngine = LuaScriptEngineFactory().scriptEngine

    @JvmStatic
    public fun eval(modifier: Modifier, defaultValue: Double): Double = eval(listOf(modifier), defaultValue)

    @JvmStatic
    public fun eval(modifiers: List<Modifier>, defaultValue: Double): Double {
        var addend = defaultValue
        var percent = 1.0
        var multiplier = 1.0
        modifiers.forEach { modifier ->
            addend += modifier.addend
            percent += modifier.percent
            multiplier *= modifier.multiplier.coerceAtLeast(0.0)
        }
        var value = addend * percent.coerceAtLeast(0.0) * multiplier
        modifiers.forEach { modifier ->
            if (!modifier.function.isNullOrBlank()) {
                value = functionEval(value, defaultValue, modifier.function)
            }
        }
        return value
    }

    @JvmStatic
    public fun eval(modified: List<Boolean>, defaultValue: Boolean): Boolean {
        return if (defaultValue) {
            modified.all { it }
        } else {
            modified.any { it }
        }
    }

    @Synchronized
    @JvmStatic
    public fun functionEval(value: Double, defaultValue: Double, script: String): Double {
        val normalizedScript = script.lowercase(Locale.ENGLISH)
        luajEngine.put("x", value)
        luajEngine.put("r", defaultValue)
        return runCatching {
            luajEngine.eval(normalizedScript)
            (luajEngine.get("y") as? Number)?.toDouble() ?: value
        }.getOrDefault(value)
    }
}
