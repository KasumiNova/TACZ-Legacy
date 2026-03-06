package com.tacz.legacy.client.model.bedrock;

import net.minecraft.client.renderer.BufferBuilder;

/**
 * Abstract interface for a bedrock cube that can compile its vertices
 * into a BufferBuilder in 1.12.2 rendering context.
 */
public interface BedrockCube {
    /**
     * Compile this cube's vertices into the given BufferBuilder.
     * The BufferBuilder is expected to already be in begin() state
     * with POSITION_TEX_NORMAL format.
     */
    void compile(BufferBuilder buffer);
}
