package com.tacz.legacy.client.model.functional;

import com.tacz.legacy.TACZLegacy;
import com.tacz.legacy.api.item.IGun;
import com.tacz.legacy.client.model.BedrockAmmoModel;
import com.tacz.legacy.client.model.BedrockGunModel;
import com.tacz.legacy.client.model.IFunctionalRenderer;
import com.tacz.legacy.client.resource.TACZClientAssetManager;
import com.tacz.legacy.client.resource.pojo.display.gun.ShellEjection;
import com.tacz.legacy.common.resource.GunCombatData;
import com.tacz.legacy.common.resource.GunDataAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;
import java.nio.FloatBuffer;
import java.util.concurrent.ConcurrentLinkedDeque;

public class ShellRender implements IFunctionalRenderer {
    private static final int MAX_SHELL_COUNT = 128;
    private static final String FOCUSED_SMOKE_PROPERTY = "tacz.focusedSmoke";

    public static boolean isSelf = false;

    private final ConcurrentLinkedDeque<Data> shellQueue = new ConcurrentLinkedDeque<>();
    private final BedrockGunModel bedrockGunModel;
    private final int slotIndex;

    public ShellRender(BedrockGunModel bedrockGunModel, int slotIndex) {
        this.bedrockGunModel = bedrockGunModel;
        this.slotIndex = slotIndex;
    }

    public void addShell(Vector3f randomVelocity) {
        if (shellQueue.size() > MAX_SHELL_COUNT) {
            shellQueue.pollFirst();
        }
        double xRandom = Math.random() * randomVelocity.x();
        double yRandom = Math.random() * randomVelocity.y();
        double zRandom = Math.random() * randomVelocity.z();
        Vector3f randomOffset = new Vector3f((float) xRandom, (float) yRandom, (float) zRandom);
        shellQueue.offerLast(new Data(System.currentTimeMillis(), randomOffset));
    }

    public int getActiveShellCount() {
        return shellQueue.size();
    }

    @Override
    public void render(int light) {
        if (!isSelf) {
            return;
        }
        ItemStack currentGunItem = bedrockGunModel.getCurrentGunItem();
        IGun iGun = IGun.getIGunOrNull(currentGunItem);
        if (iGun == null) {
            return;
        }
        ResourceLocation gunId = iGun.getGunId(currentGunItem);
        GunCombatData gunData = GunDataAccessor.getGunData(gunId);
        if (gunData == null || gunData.getAmmoId() == null) {
            return;
        }
        ShellEjection shellEjection = bedrockGunModel.getShellEjection();
        if (shellEjection == null) {
            shellQueue.clear();
            return;
        }
        TACZClientAssetManager.ShellRenderAsset shellAsset = TACZClientAssetManager.INSTANCE.getAmmoShellRenderAsset(gunData.getAmmoId());
        if (shellAsset == null) {
            return;
        }
        long lifeTime = Math.max((long) (shellEjection.getLivingTime() * 1000.0f), 0L);
        pruneExpired(lifeTime);
        if (shellQueue.isEmpty()) {
            return;
        }

        Matrix4f currentModelView = captureCurrentModelView();
        for (Data data : shellQueue) {
            if (data.modelView == null) {
                data.modelView = new Matrix4f(currentModelView);
            }
        }

        Minecraft.getMinecraft().getTextureManager().bindTexture(shellAsset.getTextureLocation());
        try {
            for (Data data : shellQueue) {
                renderSingleShell(gunId, shellAsset.getModel(), shellEjection, data, lifeTime);
            }
        } finally {
            ResourceLocation activeGunTexture = bedrockGunModel.getActiveGunTexture();
            if (activeGunTexture != null) {
                Minecraft.getMinecraft().getTextureManager().bindTexture(activeGunTexture);
            }
        }
    }

    private void renderSingleShell(ResourceLocation gunId, BedrockAmmoModel shellModel, ShellEjection shellEjection, Data data, long lifeTime) {
        if (data.modelView == null) {
            return;
        }

        double elapsedSeconds = (System.currentTimeMillis() - data.timeStamp) / 1000.0;
        Vector3f initialVelocity = shellEjection.getInitialVelocity();
        Vector3f acceleration = shellEjection.getAcceleration();
        Vector3f angularVelocity = shellEjection.getAngularVelocity();
        Vector3f randomOffset = data.randomOffset;

        double x = (initialVelocity.x() + randomOffset.x()) * elapsedSeconds + 0.5 * acceleration.x() * elapsedSeconds * elapsedSeconds;
        double y = (initialVelocity.y() + randomOffset.y()) * elapsedSeconds + 0.5 * acceleration.y() * elapsedSeconds * elapsedSeconds;
        double z = (initialVelocity.z() + randomOffset.z()) * elapsedSeconds + 0.5 * acceleration.z() * elapsedSeconds * elapsedSeconds;

        double xw = elapsedSeconds * angularVelocity.x();
        double yw = elapsedSeconds * angularVelocity.y();
        double zw = elapsedSeconds * angularVelocity.z();

        GlStateManager.pushMatrix();
        try {
            loadModelView(data.modelView);
            GlStateManager.translate(-x, -y, z);
            GlStateManager.rotate((float) -xw, 1.0f, 0.0f, 0.0f);
            GlStateManager.rotate((float) -yw, 0.0f, 1.0f, 0.0f);
            GlStateManager.rotate((float) zw, 0.0f, 0.0f, 1.0f);
            GlStateManager.translate(0.0f, -1.5f, 0.0f);
            shellModel.render();
            logFocusedSmokeVisible(gunId, data, shellEjection, lifeTime);
        } finally {
            GlStateManager.popMatrix();
        }
    }

    private void pruneExpired(long lifeTime) {
        if (lifeTime <= 0L) {
            shellQueue.clear();
            return;
        }
        while (!shellQueue.isEmpty()) {
            Data data = shellQueue.peekFirst();
            if (data == null) {
                return;
            }
            if ((System.currentTimeMillis() - data.timeStamp) <= lifeTime) {
                return;
            }
            shellQueue.pollFirst();
        }
    }

    private Matrix4f captureCurrentModelView() {
        FloatBuffer buffer = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, buffer);
        buffer.rewind();
        return new Matrix4f().set(buffer);
    }

    private void loadModelView(Matrix4f modelView) {
        FloatBuffer buffer = BufferUtils.createFloatBuffer(16);
        modelView.get(buffer);
        buffer.rewind();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadMatrix(buffer);
    }

    private void logFocusedSmokeVisible(ResourceLocation gunId, Data data, ShellEjection shellEjection, long lifeTime) {
        if (!isFocusedSmokeEnabled() || data.focusedSmokeVisibleLogged) {
            return;
        }
        data.focusedSmokeVisibleLogged = true;
        TACZLegacy.logger.info(
                "[FocusedSmoke] SHELL_VISIBLE gun={} slot={} active={} ageMs={} lifetimeMs={} initialVelocity={} randomVelocity={} acceleration={} angularVelocity={}",
                gunId,
                slotIndex,
                shellQueue.size(),
                Math.max(System.currentTimeMillis() - data.timeStamp, 0L),
                lifeTime,
                formatVector(shellEjection.getInitialVelocity()),
                formatVector(shellEjection.getRandomVelocity()),
                formatVector(shellEjection.getAcceleration()),
                formatVector(shellEjection.getAngularVelocity())
        );
    }

    private static String formatVector(Vector3f vector) {
        return String.format("[%.3f,%.3f,%.3f]", vector.x(), vector.y(), vector.z());
    }

    private static boolean isFocusedSmokeEnabled() {
        return Boolean.parseBoolean(System.getProperty(FOCUSED_SMOKE_PROPERTY, "false"));
    }

    private static final class Data {
        private final long timeStamp;
        private final Vector3f randomOffset;
        private @Nullable Matrix4f modelView;
        private boolean focusedSmokeVisibleLogged = false;

        private Data(long timeStamp, Vector3f randomOffset) {
            this.timeStamp = timeStamp;
            this.randomOffset = randomOffset;
        }
    }
}