package com.tacz.legacy.client.resource.index;

import com.tacz.legacy.client.model.BedrockAttachmentModel;
import com.tacz.legacy.client.resource.TACZClientAssetManager;
import com.tacz.legacy.client.resource.pojo.display.LaserConfig;
import com.tacz.legacy.client.resource.pojo.display.attachment.AttachmentDisplay;
import com.tacz.legacy.client.resource.pojo.display.attachment.AttachmentLod;
import com.tacz.legacy.client.resource.pojo.display.gun.TextShow;
import com.tacz.legacy.client.resource.pojo.model.BedrockVersion;
import com.tacz.legacy.util.ColorHex;
import net.minecraft.util.ResourceLocation;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Client-side attachment render/runtime index.
 *
 * Mirrors the upstream TACZ ClientAttachmentIndex responsibilities needed for
 * mounted attachment rendering in Legacy: resolve attachment model/texture,
 * adapter/default visibility metadata, and optic view selection metadata.
 */
public class ClientAttachmentIndex {
    @Nullable
    private BedrockAttachmentModel attachmentModel;
    @Nullable
    private ResourceLocation modelTexture;
    @Nullable
    private ResourceLocation slotTexture;
    @Nullable
    private LodModel lodModel;
    @Nullable
    private String adapterNodeName;
    private boolean showMuzzle;
    private float[] zoom = new float[]{1.0f};
    private int[] views = new int[]{1};
    private boolean scope;
    private boolean sight;
    private float fov = 70.0f;
    @Nullable
    private float[] viewsFov;
    @Nullable
    private LaserConfig laserConfig;

    private ClientAttachmentIndex() {
    }

    public static ClientAttachmentIndex create(AttachmentDisplay display, TACZClientAssetManager assets) {
        ClientAttachmentIndex index = new ClientAttachmentIndex();
        index.slotTexture = display.getSlotTextureLocation();
        index.modelTexture = display.getTexture();
        index.adapterNodeName = display.getAdapterNodeName();
        index.showMuzzle = display.isShowMuzzle();
        index.zoom = normalizeZoom(display.getZoom());
        index.views = normalizeViews(display.getViews());
        index.scope = display.isScope();
        index.sight = display.isSight();
        index.fov = display.getFov();
        index.viewsFov = normalizeViewsFov(display.getViewsFov(), index.views.length, index.fov);
        index.laserConfig = display.getLaserConfig();
        index.attachmentModel = loadAttachmentModel(display.getModel(), assets, index.scope, index.sight);
        checkTextShow(display, index.attachmentModel);
        index.lodModel = loadLodModel(display.getAttachmentLod(), assets, index.scope, index.sight);
        return index;
    }

    private static void checkTextShow(AttachmentDisplay display, @Nullable BedrockAttachmentModel model) {
        if (model == null) {
            return;
        }
        Map<String, TextShow> textShowMap = collectTextShowMap(display.getTextShows());
        model.setTextShowList(textShowMap);
    }

    private static Map<String, TextShow> collectTextShowMap(Map<String, TextShow> configuredTextShows) {
        Map<String, TextShow> textShowMap = new HashMap<>();
        configuredTextShows.forEach((key, textShow) -> {
            if (StringUtils.isNoneBlank(key)) {
                textShow.setColorInt(ColorHex.colorTextToRgbInt(textShow.getColorText()));
                textShowMap.put(key, textShow);
            }
        });
        return textShowMap;
    }

    @Nullable
    private static BedrockAttachmentModel loadAttachmentModel(
            @Nullable ResourceLocation modelLocation,
            TACZClientAssetManager assets,
            boolean scope,
            boolean sight
    ) {
        if (modelLocation == null) {
            return null;
        }
        TACZClientAssetManager.ModelData modelData = assets.getModel(modelLocation);
        if (modelData == null) {
            return null;
        }
        BedrockAttachmentModel model = new BedrockAttachmentModel(modelData.getPojo(), modelData.getVersion());
        model.setIsScope(scope);
        model.setIsSight(sight);
        return model;
    }

    @Nullable
    private static LodModel loadLodModel(@Nullable AttachmentLod lod, TACZClientAssetManager assets, boolean scope, boolean sight) {
        if (lod == null || lod.getModelLocation() == null || lod.getModelTexture() == null) {
            return null;
        }
        TACZClientAssetManager.ModelData modelData = assets.getModel(lod.getModelLocation());
        if (modelData == null) {
            return null;
        }
        BedrockAttachmentModel model = new BedrockAttachmentModel(modelData.getPojo(), modelData.getVersion());
        model.setIsScope(scope);
        model.setIsSight(sight);
        return new LodModel(model, lod.getModelTexture());
    }

    private static float[] normalizeZoom(@Nullable float[] zoomValues) {
        if (zoomValues == null || zoomValues.length == 0) {
            return new float[]{1.0f};
        }
        float[] result = Arrays.copyOf(zoomValues, zoomValues.length);
        for (int i = 0; i < result.length; i++) {
            result[i] = Math.max(1.0f, result[i]);
        }
        return result;
    }

    private static int[] normalizeViews(@Nullable int[] viewValues) {
        if (viewValues == null || viewValues.length == 0) {
            return new int[]{1};
        }
        int[] result = Arrays.copyOf(viewValues, viewValues.length);
        for (int i = 0; i < result.length; i++) {
            result[i] = Math.max(1, result[i]);
        }
        return result;
    }

    @Nullable
    private static float[] normalizeViewsFov(@Nullable float[] rawViewsFov, int viewCount, float fallbackFov) {
        if (rawViewsFov == null || rawViewsFov.length == 0) {
            return null;
        }
        float[] result = Arrays.copyOf(rawViewsFov, Math.max(rawViewsFov.length, viewCount));
        for (int i = 0; i < result.length; i++) {
            float current = i < rawViewsFov.length ? rawViewsFov[i] : fallbackFov;
            result[i] = current > 0.0f ? current : fallbackFov;
        }
        return result;
    }

    @Nullable
    public BedrockAttachmentModel getAttachmentModel() {
        return attachmentModel;
    }

    @Nullable
    public ResourceLocation getModelTexture() {
        return modelTexture;
    }

    @Nullable
    public ResourceLocation getSlotTexture() {
        return slotTexture;
    }

    @Nullable
    public LodModel getLodModel() {
        return lodModel;
    }

    @Nullable
    public String getAdapterNodeName() {
        return adapterNodeName;
    }

    public boolean isShowMuzzle() {
        return showMuzzle;
    }

    public float[] getZoom() {
        return Arrays.copyOf(zoom, zoom.length);
    }

    public int[] getViews() {
        return Arrays.copyOf(views, views.length);
    }

    public boolean isScope() {
        return scope;
    }

    public boolean isSight() {
        return sight;
    }

    public float getFov() {
        return fov;
    }

    @Nullable
    public LaserConfig getLaserConfig() {
        return laserConfig;
    }

    @Nullable
    public float[] getViewsFov() {
        return viewsFov == null ? null : Arrays.copyOf(viewsFov, viewsFov.length);
    }

    public static final class LodModel {
        private final BedrockAttachmentModel model;
        private final ResourceLocation texture;

        private LodModel(BedrockAttachmentModel model, ResourceLocation texture) {
            this.model = model;
            this.texture = texture;
        }

        public BedrockAttachmentModel getModel() {
            return model;
        }

        public ResourceLocation getTexture() {
            return texture;
        }
    }
}
