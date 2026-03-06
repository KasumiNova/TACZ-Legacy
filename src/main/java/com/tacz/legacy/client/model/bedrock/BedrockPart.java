package com.tacz.legacy.client.model.bedrock;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A bone/part in a Bedrock model hierarchy.
 * Adapted from upstream TACZ BedrockPart for 1.12.2 rendering
 * (GlStateManager + Tessellator instead of PoseStack + VertexConsumer).
 */
public class BedrockPart {
    private static final FloatBuffer MATRIX_BUFFER = BufferUtils.createFloatBuffer(16);

    @Nullable
    public final String name;
    public final List<BedrockCube> cubes = new ArrayList<>();
    public final List<BedrockPart> children = new ArrayList<>();
    public float x;
    public float y;
    public float z;
    public float xRot;
    public float yRot;
    public float zRot;
    public float offsetX;
    public float offsetY;
    public float offsetZ;
    public boolean visible = true;
    public boolean illuminated = false;
    public boolean mirror;
    /**
     * Used for animation rotation.
     */
    public Quaternionf additionalQuaternion = new Quaternionf(0, 0, 0, 1);
    public float xScale = 1;
    public float yScale = 1;
    public float zScale = 1;
    protected BedrockPart parent;
    private float initRotX;
    private float initRotY;
    private float initRotZ;

    public BedrockPart(@Nullable String name) {
        this.name = name;
    }

    public void setPos(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public void render() {
        if (this.visible) {
            if (!this.cubes.isEmpty() || !this.children.isEmpty()) {
                GlStateManager.pushMatrix();
                this.translateAndRotateAndScale();

                // Save/restore lightmap for illuminated bones.
                // Children inherit illumination (matching upstream cascade behavior).
                boolean needsLightRestore = false;
                float prevBrightnessX = 0;
                float prevBrightnessY = 0;
                if (illuminated) {
                    prevBrightnessX = OpenGlHelper.lastBrightnessX;
                    prevBrightnessY = OpenGlHelper.lastBrightnessY;
                    OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240, 240);
                    needsLightRestore = true;
                }

                if (!this.cubes.isEmpty()) {
                    Tessellator tessellator = Tessellator.getInstance();
                    BufferBuilder buffer = tessellator.getBuffer();
                    buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_NORMAL);
                    for (BedrockCube cube : this.cubes) {
                        cube.compile(buffer);
                    }
                    tessellator.draw();
                }

                for (BedrockPart part : this.children) {
                    part.render();
                }

                if (needsLightRestore) {
                    OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, prevBrightnessX, prevBrightnessY);
                }

                GlStateManager.popMatrix();
            }
        }
    }

    public void translateAndRotateAndScale() {
        GlStateManager.translate(this.offsetX, this.offsetY, this.offsetZ);
        GlStateManager.translate(this.x / 16.0F, this.y / 16.0F, this.z / 16.0F);
        if (this.zRot != 0.0F) {
            GlStateManager.rotate((float) Math.toDegrees(this.zRot), 0, 0, 1);
        }
        if (this.yRot != 0.0F) {
            GlStateManager.rotate((float) Math.toDegrees(this.yRot), 0, 1, 0);
        }
        if (this.xRot != 0.0F) {
            GlStateManager.rotate((float) Math.toDegrees(this.xRot), 1, 0, 0);
        }
        applyQuaternion(additionalQuaternion);
        GlStateManager.scale(xScale, yScale, zScale);
    }

    private static void applyQuaternion(Quaternionf q) {
        if (q.x == 0 && q.y == 0 && q.z == 0 && q.w == 1) {
            return;
        }
        Matrix4f mat = new Matrix4f().rotation(q);
        MATRIX_BUFFER.clear();
        mat.get(MATRIX_BUFFER);
        MATRIX_BUFFER.rewind();
        GL11.glMultMatrix(MATRIX_BUFFER);
    }

    public BedrockCube getRandomCube(Random random) {
        return this.cubes.get(random.nextInt(this.cubes.size()));
    }

    public boolean isEmpty() {
        return this.cubes.isEmpty();
    }

    public void setInitRotationAngle(float x, float y, float z) {
        this.initRotX = x;
        this.initRotY = y;
        this.initRotZ = z;
    }

    public float getInitRotX() {
        return initRotX;
    }

    public float getInitRotY() {
        return initRotY;
    }

    public float getInitRotZ() {
        return initRotZ;
    }

    public void addChild(BedrockPart model) {
        this.children.add(model);
    }

    public BedrockPart getParent() {
        return parent;
    }
}
