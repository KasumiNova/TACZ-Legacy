package com.tacz.legacy.client.model.functional;

import com.tacz.legacy.client.foundation.TACZAsciiFontHelper;
import com.tacz.legacy.client.model.IFunctionalRenderer;
import com.tacz.legacy.client.model.bedrock.BedrockModel;
import com.tacz.legacy.client.model.papi.PapiManager;
import com.tacz.legacy.client.resource.pojo.display.gun.Align;
import com.tacz.legacy.client.resource.pojo.display.gun.TextShow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.item.ItemStack;
import org.apache.commons.lang3.StringUtils;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;

/**
 * Renders text on a gun model bone using 1.12.2 GL immediate mode.
 * Port of upstream TACZ TextShowRender.
 * <p>
 * When render() is called during bone traversal, the current modelview matrix
 * is captured and a delegate is registered on the BedrockModel to perform the
 * actual text draw after the main model pass completes.
 */
public class TextShowRender implements IFunctionalRenderer {
    private static final FloatBuffer MATRIX_BUFFER = BufferUtils.createFloatBuffer(16);
    private final BedrockModel bedrockModel;
    private final TextShow textShow;
    private final ItemStack gunStack;

    public TextShowRender(BedrockModel bedrockModel, TextShow textShow, ItemStack gunStack) {
        this.bedrockModel = bedrockModel;
        this.textShow = textShow;
        this.gunStack = gunStack;
    }

    @Override
    public void render(int light) {
        String text = PapiManager.getTextShow(textShow.getTextKey(), gunStack);
        if (StringUtils.isBlank(text)) {
            return;
        }
        // Capture current modelview matrix — bone transform is applied
        float[] capturedMatrix = new float[16];
        MATRIX_BUFFER.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, MATRIX_BUFFER);
        MATRIX_BUFFER.get(capturedMatrix);

        bedrockModel.delegateRender(delegateLight -> {
            FontRenderer font = Minecraft.getMinecraft().fontRenderer;
            boolean shadow = textShow.isShadow();
            int color = textShow.getColorInt();
            float scale = textShow.getScale();
            int textLight = textShow.getTextLight();

            int width = TACZAsciiFontHelper.getStringWidth(font, text);
            int xOffset;
            Align align = textShow.getAlign();
            if (align == Align.CENTER) {
                xOffset = width / 2;
            } else if (align == Align.RIGHT) {
                xOffset = width;
            } else {
                xOffset = 0;
            }

            GlStateManager.pushMatrix();
            // Restore the bone's modelview matrix
            MATRIX_BUFFER.clear();
            MATRIX_BUFFER.put(capturedMatrix);
            MATRIX_BUFFER.flip();
            GL11.glLoadMatrix(MATRIX_BUFFER);

            // Rotate 180° on Z to match upstream orientation
            GlStateManager.rotate(180f, 0, 0, 1);
            float s = 2f / 300f * scale;
            GlStateManager.scale(s, -s, -2f / 300f);

            // Set lightmap for emissive text
            float prevBX = OpenGlHelper.lastBrightnessX;
            float prevBY = OpenGlHelper.lastBrightnessY;
            float packed = Math.min(textLight, 15) * 16f;
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, packed, packed);

            GlStateManager.disableLighting();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(
                    GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ONE,
                    GlStateManager.DestFactor.ZERO
            );
            GlStateManager.depthMask(true);

            TACZAsciiFontHelper.drawString(font, text, -xOffset, -font.FONT_HEIGHT / 2, color, shadow);

            GlStateManager.enableLighting();
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, prevBX, prevBY);

            GlStateManager.popMatrix();
        });
    }
}
