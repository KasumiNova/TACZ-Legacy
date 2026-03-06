package com.tacz.legacy.client.model.bedrock;

import net.minecraft.util.EnumFacing;
import org.joml.Vector3f;

public class BedrockPolygon {
    public final BedrockVertex[] vertices;
    public final Vector3f normal;

    public BedrockPolygon(BedrockVertex[] vertices, float u1, float v1, float u2, float v2,
                          float texWidth, float texHeight, boolean mirror, EnumFacing direction) {
        this.vertices = vertices;
        float f = 0.0F / texWidth;
        float f1 = 0.0F / texHeight;
        vertices[0] = vertices[0].remap(u2 / texWidth - f, v1 / texHeight + f1);
        vertices[1] = vertices[1].remap(u1 / texWidth + f, v1 / texHeight + f1);
        vertices[2] = vertices[2].remap(u1 / texWidth + f, v2 / texHeight - f1);
        vertices[3] = vertices[3].remap(u2 / texWidth - f, v2 / texHeight - f1);
        if (mirror) {
            int i = vertices.length;
            for (int j = 0; j < i / 2; ++j) {
                BedrockVertex bedrockVertex = vertices[j];
                vertices[j] = vertices[i - 1 - j];
                vertices[i - 1 - j] = bedrockVertex;
            }
        }
        this.normal = directionNormal(direction);
        if (mirror) {
            this.normal.mul(-1.0F, 1.0F, 1.0F);
        }
    }

    private static Vector3f directionNormal(EnumFacing facing) {
        switch (facing) {
            case DOWN:  return new Vector3f(0, -1, 0);
            case UP:    return new Vector3f(0, 1, 0);
            case NORTH: return new Vector3f(0, 0, -1);
            case SOUTH: return new Vector3f(0, 0, 1);
            case WEST:  return new Vector3f(-1, 0, 0);
            case EAST:  return new Vector3f(1, 0, 0);
            default:    return new Vector3f(0, 1, 0);
        }
    }
}
