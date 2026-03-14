package com.tacz.legacy.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.GL11;

public final class RenderHelper {
    private RenderHelper() {
    }

    public static boolean enableItemEntityStencilTest() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) {
            return false;
        }
        Framebuffer framebuffer = minecraft.getFramebuffer();
        if (framebuffer == null) {
            return false;
        }
        if (!framebuffer.isStencilEnabled()) {
            framebuffer.enableStencil();
        }
        GL11.glEnable(GL11.GL_STENCIL_TEST);
        return true;
    }

    public static void disableItemEntityStencilTest() {
        GL11.glDisable(GL11.GL_STENCIL_TEST);
    }
}