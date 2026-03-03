package com.tacz.legacy.client.render.feature.builtin

import com.tacz.legacy.client.render.draw.LambdaDrawCommand
import com.tacz.legacy.client.render.feature.RenderFeature
import com.tacz.legacy.client.render.feature.RenderFeatureRegistry
import com.tacz.legacy.client.render.frame.FramePhase
import com.tacz.legacy.client.render.pass.SimpleSubpass

public object DrawCommandDiagnosticsFeature : RenderFeature {

    override val id: String = "builtin.draw_command_diagnostics"

    override fun install(registry: RenderFeatureRegistry) {
        registry.registerSubpass(
            FramePhase.UPDATE,
            SimpleSubpass("builtin.draw_command.submit") { context ->
                context.commandBuffer.submit(
                    LambdaDrawCommand("builtin.draw_command.noop") { _ -> }
                )

                val submitted = (context.diagnostics["draw.command.submitted"] as? Int) ?: 0
                context.diagnostics["draw.command.submitted"] = submitted + 1
                context.diagnostics["draw.command.buffer.size"] = context.commandBuffer.size()
            }
        )

        registry.registerSubpass(
            FramePhase.RENDER_OPAQUE,
            SimpleSubpass("builtin.draw_command.execute") { context ->
                val report = context.commandBuffer.executeAllWithReport(context)
                context.diagnostics["draw.command.executed"] =
                    ((context.diagnostics["draw.command.executed"] as? Int) ?: 0) + report.executed
                context.diagnostics["draw.command.failed"] =
                    ((context.diagnostics["draw.command.failed"] as? Int) ?: 0) + report.failed
                context.diagnostics["draw.command.consumed"] =
                    ((context.diagnostics["draw.command.consumed"] as? Int) ?: 0) + report.consumed
                if (report.failedIds.isNotEmpty()) {
                    context.diagnostics["draw.command.failed.ids"] = report.failedIds
                }
                context.diagnostics["draw.command.buffer.size"] = context.commandBuffer.size()
            }
        )
    }

}
