package com.tacz.legacy.client.render.draw

import com.tacz.legacy.client.render.core.RenderContext

public class DrawCommandBuffer {

    private val commands: MutableList<DrawCommand> = mutableListOf()

    public fun submit(command: DrawCommand) {
        commands += command
    }

    public fun submitAll(newCommands: Collection<DrawCommand>) {
        commands += newCommands
    }

    public fun executeAll(context: RenderContext): Int = executeAllWithReport(context).consumed

    public fun executeAllWithReport(context: RenderContext): DrawCommandExecutionReport {
        val pending = commands.toList()
        commands.clear()

        var executed = 0
        var failed = 0
        val failedIds = mutableListOf<String>()

        pending.forEach { command ->
            try {
                command.execute(context)
                executed += 1
            } catch (throwable: Throwable) {
                failed += 1
                failedIds += command.id
                if (!context.pipelineConfig.enableVanillaFallback) {
                    throw IllegalStateException("Draw command '${command.id}' failed in strict mode", throwable)
                }
            }
        }

        return DrawCommandExecutionReport(
            submitted = pending.size,
            executed = executed,
            failed = failed,
            failedIds = failedIds
        )
    }

    public fun clear() {
        commands.clear()
    }

    public fun size(): Int = commands.size

    public fun snapshotIds(): List<String> = commands.map { it.id }

}
