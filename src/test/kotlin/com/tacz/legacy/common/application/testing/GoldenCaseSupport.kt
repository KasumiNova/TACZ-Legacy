package com.tacz.legacy.common.application.testing

import org.junit.Assert.fail

public data class GoldenCase<I, O>(
    val id: String,
    val input: I,
    val expected: O
)

public data class GoldenCaseResult<O>(
    val id: String,
    val expected: O,
    val actual: O,
    val passed: Boolean
)

public class GoldenCaseRunner<I, O>(
    private val renderer: (O) -> String = { value -> value.toString() }
) {

    public fun run(
        cases: Collection<GoldenCase<I, O>>,
        execute: (I) -> O
    ): List<GoldenCaseResult<O>> = cases.map { goldenCase ->
        val actual = execute(goldenCase.input)
        GoldenCaseResult(
            id = goldenCase.id,
            expected = goldenCase.expected,
            actual = actual,
            passed = actual == goldenCase.expected
        )
    }

    public fun assertAll(
        cases: Collection<GoldenCase<I, O>>,
        execute: (I) -> O
    ) {
        val failures = run(cases, execute).filterNot { result -> result.passed }
        if (failures.isEmpty()) {
            return
        }

        val message = buildString {
            append("Golden case assertion failed for ")
            append(failures.size)
            append(" case(s):")
            failures.forEach { result ->
                append("\n- ")
                append(result.id)
                append(" expected=")
                append(renderer(result.expected))
                append(" actual=")
                append(renderer(result.actual))
            }
        }
        fail(message)
    }

}
