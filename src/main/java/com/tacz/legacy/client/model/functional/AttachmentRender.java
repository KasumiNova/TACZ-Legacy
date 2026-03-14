package com.tacz.legacy.client.model.functional;

import com.tacz.legacy.api.item.IAttachment;
import com.tacz.legacy.api.item.attachment.AttachmentType;
import com.tacz.legacy.client.model.BedrockAttachmentModel;
import com.tacz.legacy.client.model.BedrockGunModel;
import com.tacz.legacy.client.model.IFunctionalRenderer;
import com.tacz.legacy.client.resource.TACZClientAssetManager;
import com.tacz.legacy.client.resource.index.ClientAttachmentIndex;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;
import java.nio.FloatBuffer;

/**
 * Functional renderer for attachments mounted on a gun model node.
 */
public class AttachmentRender implements IFunctionalRenderer {
    private final BedrockGunModel bedrockGunModel;
    private final AttachmentType type;

    public AttachmentRender(BedrockGunModel bedrockGunModel, AttachmentType type) {
        this.bedrockGunModel = bedrockGunModel;
        this.type = type;
    }

    public static void renderAttachment(
            ItemStack attachmentItem,
            @Nullable ItemStack gunItem,
            @Nullable ResourceLocation gunTexture,
            int light,
            boolean bloomOnly
    ) {
        if (attachmentItem == null || attachmentItem.isEmpty()) {
            return;
        }
        IAttachment iAttachment = IAttachment.getIAttachmentOrNull(attachmentItem);
        if (iAttachment == null) {
            return;
        }
        ClientAttachmentIndex attachmentIndex = TACZClientAssetManager.INSTANCE.getAttachmentIndex(iAttachment.getAttachmentId(attachmentItem));
        if (attachmentIndex == null) {
            return;
        }
        BedrockAttachmentModel model = attachmentIndex.getAttachmentModel();
        ResourceLocation textureId = attachmentIndex.getModelTexture();
        if (model == null || textureId == null) {
            return;
        }
        ResourceLocation registeredTexture = TACZClientAssetManager.INSTANCE.getTextureLocation(textureId);
        if (registeredTexture == null) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        GlStateManager.pushMatrix();
        GlStateManager.translate(0.0f, -1.5f, 0.0f);
        mc.getTextureManager().bindTexture(registeredTexture);
        if (bloomOnly) {
            model.renderBloom(attachmentItem, gunItem);
        } else {
            model.render(attachmentItem, gunItem);
        }
        model.cleanAnimationTransform();
        model.cleanCameraAnimationTransform();
        GlStateManager.popMatrix();

        if (gunTexture != null) {
            mc.getTextureManager().bindTexture(gunTexture);
        }
    }

    @Override
    public void render(int light) {
        ItemStack attachmentItem = bedrockGunModel.getAttachmentItem(type);
        if (attachmentItem == null || attachmentItem.isEmpty()) {
            return;
        }
        FloatBuffer capturedModelView = captureCurrentModelView();
        ItemStack gunItem = bedrockGunModel.getCurrentGunItem();
        ResourceLocation gunTexture = bedrockGunModel.getActiveGunTexture();
        bedrockGunModel.delegateRender(new IFunctionalRenderer() {
            @Override
            public void render(int delegatedLight) {
                renderCapturedAttachment(capturedModelView, attachmentItem, gunItem, gunTexture, light, false);
            }
        });
    }

    @Override
    public void renderBloom(int light) {
        ItemStack attachmentItem = bedrockGunModel.getAttachmentItem(type);
        if (attachmentItem == null || attachmentItem.isEmpty()) {
            return;
        }
        renderAttachment(attachmentItem, bedrockGunModel.getCurrentGunItem(), bedrockGunModel.getActiveGunTexture(), light, true);
    }

    private static FloatBuffer captureCurrentModelView() {
        FloatBuffer buffer = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, buffer);
        buffer.rewind();
        return buffer;
    }

    private static void renderCapturedAttachment(
            FloatBuffer capturedModelView,
            ItemStack attachmentItem,
            @Nullable ItemStack gunItem,
            @Nullable ResourceLocation gunTexture,
            int light,
            boolean bloomOnly
    ) {
        GlStateManager.pushMatrix();
        try {
            capturedModelView.rewind();
            GL11.glLoadMatrix(capturedModelView);
            renderAttachment(attachmentItem, gunItem, gunTexture, light, bloomOnly);
        } finally {
            GlStateManager.popMatrix();
        }
    }
}
