package com.tacz.legacy.client.model.functional;

import com.tacz.legacy.TACZLegacy;
import com.tacz.legacy.api.item.IAttachment;
import com.tacz.legacy.api.item.IGun;
import com.tacz.legacy.client.model.bedrock.BedrockPart;
import com.tacz.legacy.client.resource.GunDisplayInstance;
import com.tacz.legacy.client.resource.TACZClientAssetManager;
import com.tacz.legacy.client.resource.index.ClientAttachmentIndex;
import com.tacz.legacy.client.resource.pojo.display.LaserConfig;
import com.tacz.legacy.common.config.LegacyConfigManager;
import com.tacz.legacy.common.resource.TACZGunPackPresentation;
import com.tacz.legacy.common.resource.TACZGunPackRuntimeRegistry;
import com.tacz.legacy.util.LaserColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Immediate-mode Legacy renderer for gun-pack laser beams.
 *
 * Port of upstream TACZ BeamRenderer adapted to 1.12.2's GL/Tessellator path.
 */
public final class BeamRenderer {
    public static final ResourceLocation LASER_BEAM_TEXTURE = new ResourceLocation(TACZLegacy.MOD_ID, "textures/entity/beam.png");
    private static final LaserConfig DEFAULT_LASER_CONFIG = new LaserConfig();
    private static final float VIEW_OFFSET_SCALE = 0.99975586F;
    private static final float DEPTH_BIAS_FACTOR = -1.0F;
    private static final float DEPTH_BIAS_UNITS = -10.0F;
    private static final float MIN_HANDHELD_RENDER_WIDTH = 0.03F;
    private static final float HANDHELD_CORE_LINE_WIDTH = 3.5F;
    private static final int HANDHELD_CORE_LINE_END_ALPHA = 192;
    private static final FloatBuffer MODEL_VIEW_BUFFER = BufferUtils.createFloatBuffer(16);
    private static final FloatBuffer PROJECTION_BUFFER = BufferUtils.createFloatBuffer(16);
    private static final IntBuffer VIEWPORT_BUFFER = BufferUtils.createIntBuffer(16);
    private static final ThreadLocal<RenderContext> CURRENT_CONTEXT = ThreadLocal.withInitial(() -> RenderContext.NONE);
    private static final Set<String> LOGGED_FOCUSED_SMOKE_KEYS = new HashSet<>();
    private static final Set<String> LOGGED_FOCUSED_SMOKE_SCREEN_KEYS = new HashSet<>();

    private BeamRenderer() {
    }

    public enum RenderContext {
        NONE(false, false, "none"),
        FIRST_PERSON(true, true, "first_person"),
        THIRD_PERSON_RIGHT_HAND(true, false, "third_person_right_hand"),
        GUI_PREVIEW(false, false, "gui_preview");

        private final boolean renderBeam;
        private final boolean firstPerson;
        private final String logName;

        RenderContext(boolean renderBeam, boolean firstPerson, String logName) {
            this.renderBeam = renderBeam;
            this.firstPerson = firstPerson;
            this.logName = logName;
        }

        public boolean shouldRenderBeam() {
            return renderBeam;
        }

        public boolean isFirstPerson() {
            return firstPerson;
        }

        public String getLogName() {
            return logName;
        }
    }

    @Nonnull
    public static RenderContext pushRenderContext(@Nullable RenderContext context) {
        RenderContext previous = CURRENT_CONTEXT.get();
        CURRENT_CONTEXT.set(context == null ? RenderContext.NONE : context);
        return previous;
    }

    public static void popRenderContext(@Nullable RenderContext previous) {
        CURRENT_CONTEXT.set(previous == null ? RenderContext.NONE : previous);
    }

    @Nonnull
    public static RenderContext getRenderContext() {
        return CURRENT_CONTEXT.get();
    }

    public static void renderLaserBeam(@Nullable ItemStack stack, @Nonnull List<BedrockPart> path) {
        RenderContext context = CURRENT_CONTEXT.get();
        if (stack == null || stack.isEmpty() || path.isEmpty() || !context.shouldRenderBeam()) {
            return;
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) {
            return;
        }

        LaserConfig laserConfig = getLaserConfig(stack);
        float length = context.isFirstPerson() ? laserConfig.getLength() : laserConfig.getLengthThird();
        float width = context.isFirstPerson() ? laserConfig.getWidth() : laserConfig.getWidthThird();
        float renderWidth = getRenderableWidth(context, width);
        if (length <= 0.0f || renderWidth <= 0.0f) {
            return;
        }

        int color = LaserColorUtil.getLaserColor(stack, laserConfig) & 0xFFFFFF;
        int red = (color >> 16) & 0xFF;
        int green = (color >> 8) & 0xFF;
        int blue = color & 0xFF;
        boolean fadeOut = LegacyConfigManager.isLaserFadeOutEnabledForJava();

        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        float previousBrightnessX = OpenGlHelper.lastBrightnessX;
        float previousBrightnessY = OpenGlHelper.lastBrightnessY;

        GlStateManager.pushMatrix();
        try {
            for (BedrockPart bedrockPart : path) {
                bedrockPart.translateAndRotateAndScale();
            }

            // Upstream uses VIEW_OFFSET_Z_LAYERING so the beam is not swallowed by
            // coplanar/self-occluding item geometry. 1.12.2 has no RenderType
            // layering shard, so approximate it with a tiny model-view shrink plus
            // classic polygon offset depth bias.
            GlStateManager.scale(VIEW_OFFSET_SCALE, VIEW_OFFSET_SCALE, VIEW_OFFSET_SCALE);
            GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
            GL11.glPolygonOffset(DEPTH_BIAS_FACTOR, DEPTH_BIAS_UNITS);

            minecraft.getTextureManager().bindTexture(LASER_BEAM_TEXTURE);
            GlStateManager.disableLighting();
            GlStateManager.disableCull();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(
                    GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE,
                    GlStateManager.SourceFactor.ONE,
                    GlStateManager.DestFactor.ZERO
            );
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0f, 240.0f);

            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder buffer = tessellator.getBuffer();
            buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
            stringVertex(-length, renderWidth, buffer, red, green, blue, fadeOut);
            tessellator.draw();
            renderVisibilityCoreLine(tessellator, context, length, red, green, blue, fadeOut);

            logFocusedSmokeBeamRender(stack, context, length, renderWidth, color, fadeOut, path);
            logFocusedSmokeBeamScreenProjection(stack, context, length, renderWidth, path);
        } finally {
            GL11.glPolygonOffset(0.0F, 0.0F);
            GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
            GlStateManager.bindTexture(previousTexture);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, previousBrightnessX, previousBrightnessY);
            GlStateManager.tryBlendFuncSeparate(
                    GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ONE,
                    GlStateManager.DestFactor.ZERO
            );
            GlStateManager.enableCull();
            GlStateManager.enableLighting();
            GlStateManager.popMatrix();
        }
    }

    private static float getRenderableWidth(RenderContext context, float configuredWidth) {
        if (context.isFirstPerson() || context == RenderContext.THIRD_PERSON_RIGHT_HAND) {
            return Math.max(configuredWidth, MIN_HANDHELD_RENDER_WIDTH);
        }
        return configuredWidth;
    }

    private static void renderVisibilityCoreLine(
            Tessellator tessellator,
            RenderContext context,
            float length,
            int red,
            int green,
            int blue,
            boolean fadeOut
    ) {
        if (!(context.isFirstPerson() || context == RenderContext.THIRD_PERSON_RIGHT_HAND)) {
            return;
        }

        float previousLineWidth = GL11.glGetFloat(GL11.GL_LINE_WIDTH);
        GlStateManager.disableTexture2D();
        GL11.glLineWidth(HANDHELD_CORE_LINE_WIDTH);
        try {
            BufferBuilder buffer = tessellator.getBuffer();
            buffer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
            buffer.pos(0.0D, 0.0D, 0.0D).color(red, green, blue, 255).endVertex();
            buffer.pos(0.0D, 0.0D, -length).color(red, green, blue, fadeOut ? HANDHELD_CORE_LINE_END_ALPHA : 255).endVertex();
            tessellator.draw();
        } finally {
            GL11.glLineWidth(previousLineWidth);
            GlStateManager.enableTexture2D();
        }
    }

    private static LaserConfig getLaserConfig(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return DEFAULT_LASER_CONFIG;
        }

        if (stack.getItem() instanceof IAttachment) {
            IAttachment attachment = (IAttachment) stack.getItem();
            ClientAttachmentIndex index = TACZClientAssetManager.INSTANCE.getAttachmentIndex(attachment.getAttachmentId(stack));
            return index != null && index.getLaserConfig() != null ? index.getLaserConfig() : DEFAULT_LASER_CONFIG;
        }

        if (stack.getItem() instanceof IGun) {
            IGun gun = (IGun) stack.getItem();
                ResourceLocation displayId = TACZGunPackPresentation.INSTANCE.resolveGunDisplayId(
                        TACZGunPackRuntimeRegistry.currentSnapshotForJava(),
                    gun.getGunId(stack)
            );
            if (displayId == null) {
                return DEFAULT_LASER_CONFIG;
            }
            GunDisplayInstance displayInstance = TACZClientAssetManager.INSTANCE.getGunDisplayInstance(displayId);
            return displayInstance != null && displayInstance.getLaserConfig() != null ? displayInstance.getLaserConfig() : DEFAULT_LASER_CONFIG;
        }

        return DEFAULT_LASER_CONFIG;
    }

    private static void stringVertex(float z, float width, BufferBuilder buffer, int r, int g, int b, boolean fadeOut) {
        float halfWidth = width / 2.0f;
        int endAlpha = fadeOut ? 0 : 255;

        buffer.pos(-halfWidth, -halfWidth, 0).tex(0, 0).color(r, g, b, 255).endVertex();
        buffer.pos(-halfWidth, halfWidth, 0).tex(0, 1).color(r, g, b, 255).endVertex();
        buffer.pos(-halfWidth, halfWidth, z).tex(1, 1).color(r, g, b, endAlpha).endVertex();
        buffer.pos(-halfWidth, -halfWidth, z).tex(1, 0).color(r, g, b, endAlpha).endVertex();

        buffer.pos(-halfWidth, halfWidth, 0).tex(0, 0).color(r, g, b, 255).endVertex();
        buffer.pos(halfWidth, halfWidth, 0).tex(0, 1).color(r, g, b, 255).endVertex();
        buffer.pos(halfWidth, halfWidth, z).tex(1, 1).color(r, g, b, endAlpha).endVertex();
        buffer.pos(-halfWidth, halfWidth, z).tex(1, 0).color(r, g, b, endAlpha).endVertex();

        buffer.pos(halfWidth, halfWidth, 0).tex(0, 0).color(r, g, b, 255).endVertex();
        buffer.pos(halfWidth, -halfWidth, 0).tex(0, 1).color(r, g, b, 255).endVertex();
        buffer.pos(halfWidth, -halfWidth, z).tex(1, 1).color(r, g, b, endAlpha).endVertex();
        buffer.pos(halfWidth, halfWidth, z).tex(1, 0).color(r, g, b, endAlpha).endVertex();

        buffer.pos(halfWidth, -halfWidth, 0).tex(0, 0).color(r, g, b, 255).endVertex();
        buffer.pos(-halfWidth, -halfWidth, 0).tex(0, 1).color(r, g, b, 255).endVertex();
        buffer.pos(-halfWidth, -halfWidth, z).tex(1, 1).color(r, g, b, endAlpha).endVertex();
        buffer.pos(halfWidth, -halfWidth, z).tex(1, 0).color(r, g, b, endAlpha).endVertex();
    }

    private static void logFocusedSmokeBeamRender(
            ItemStack stack,
            RenderContext context,
            float length,
            float width,
            int color,
            boolean fadeOut,
            List<BedrockPart> path
    ) {
        if (!Boolean.parseBoolean(System.getProperty("tacz.focusedSmoke", "false"))) {
            return;
        }
        String stackId = resolveStackId(stack);
        String endNodeName = path.isEmpty() || path.get(path.size() - 1) == null || path.get(path.size() - 1).name == null
                ? "unknown"
                : path.get(path.size() - 1).name;
        String key = stackId + '|' + context.getLogName() + '|' + endNodeName + '|' + color + '|' + length + '|' + width + '|' + fadeOut;
        if (!LOGGED_FOCUSED_SMOKE_KEYS.add(key)) {
            return;
        }
        TACZLegacy.logger.info(
                "[FocusedSmoke] LASER_BEAM_RENDERED item={} context={} path={} color=0x{} length={} width={} fadeOut={}",
                stackId,
                context.getLogName(),
                endNodeName,
                String.format("%06X", color & 0xFFFFFF),
                String.format("%.3f", length),
                String.format("%.4f", width),
                fadeOut
        );
    }

    private static void logFocusedSmokeBeamScreenProjection(
            ItemStack stack,
            RenderContext context,
            float length,
            float width,
            List<BedrockPart> path
    ) {
        if (!Boolean.parseBoolean(System.getProperty("tacz.focusedSmoke", "false"))) {
            return;
        }
        String stackId = resolveStackId(stack);
        String endNodeName = path.isEmpty() || path.get(path.size() - 1) == null || path.get(path.size() - 1).name == null
                ? "unknown"
                : path.get(path.size() - 1).name;
        String key = stackId + '|' + context.getLogName() + '|' + endNodeName + '|' + length + '|' + width;
        if (!LOGGED_FOCUSED_SMOKE_SCREEN_KEYS.add(key)) {
            return;
        }

        String startProjection = projectCurrentPoint(0.0f, 0.0f, 0.0f);
        String endProjection = projectCurrentPoint(0.0f, 0.0f, -length);
        float halfWidth = width / 2.0f;
        float startPixelWidth = projectedPixelDistance(-halfWidth, 0.0f, 0.0f, halfWidth, 0.0f, 0.0f);
        float endPixelWidth = projectedPixelDistance(-halfWidth, 0.0f, -length, halfWidth, 0.0f, -length);
        TACZLegacy.logger.info(
                "[FocusedSmoke] LASER_BEAM_SCREEN item={} context={} path={} start={} end={} startPxWidth={} endPxWidth={}",
                stackId,
                context.getLogName(),
                endNodeName,
                startProjection,
                endProjection,
                String.format("%.3f", startPixelWidth),
                String.format("%.3f", endPixelWidth)
        );
    }

    private static float projectedPixelDistance(float ax, float ay, float az, float bx, float by, float bz) {
        ScreenPoint a = projectCurrentPointValue(ax, ay, az);
        ScreenPoint b = projectCurrentPointValue(bx, by, bz);
        if (a == null || b == null) {
            return -1.0f;
        }
        float dx = a.x - b.x;
        float dy = a.y - b.y;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private static String projectCurrentPoint(float x, float y, float z) {
        ScreenPoint point = projectCurrentPointValue(x, y, z);
        return point == null ? "null" : String.format("(%.1f,%.1f,%.4f)", point.x, point.y, point.z);
    }

    @Nullable
    private static ScreenPoint projectCurrentPointValue(float x, float y, float z) {
        MODEL_VIEW_BUFFER.clear();
        PROJECTION_BUFFER.clear();
        VIEWPORT_BUFFER.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, MODEL_VIEW_BUFFER);
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, PROJECTION_BUFFER);
        GL11.glGetInteger(GL11.GL_VIEWPORT, VIEWPORT_BUFFER);
        MODEL_VIEW_BUFFER.rewind();
        PROJECTION_BUFFER.rewind();
        VIEWPORT_BUFFER.rewind();

        Matrix4f modelView = new Matrix4f().set(MODEL_VIEW_BUFFER);
        Matrix4f projection = new Matrix4f().set(PROJECTION_BUFFER);
        int viewportX = VIEWPORT_BUFFER.get(0);
        int viewportY = VIEWPORT_BUFFER.get(1);
        int viewportWidth = VIEWPORT_BUFFER.get(2);
        int viewportHeight = VIEWPORT_BUFFER.get(3);

        Vector4f clip = new Vector4f(x, y, z, 1.0f);
        modelView.transform(clip);
        projection.transform(clip);
        if (Math.abs(clip.w) <= 1.0E-6f) {
            return null;
        }
        float invW = 1.0f / clip.w;
        float ndcX = clip.x * invW;
        float ndcY = clip.y * invW;
        float ndcZ = clip.z * invW;
        float screenX = viewportX + (ndcX + 1.0f) * 0.5f * viewportWidth;
        float screenY = viewportY + (ndcY + 1.0f) * 0.5f * viewportHeight;
        return new ScreenPoint(screenX, screenY, ndcZ);
    }

    private static final class ScreenPoint {
        private final float x;
        private final float y;
        private final float z;

        private ScreenPoint(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private static String resolveStackId(ItemStack stack) {
        if (stack.getItem() instanceof IAttachment) {
            return String.valueOf(((IAttachment) stack.getItem()).getAttachmentId(stack));
        }
        if (stack.getItem() instanceof IGun) {
            return String.valueOf(((IGun) stack.getItem()).getGunId(stack));
        }
        return stack.getItem().getRegistryName() == null ? stack.getItem().getClass().getName() : stack.getItem().getRegistryName().toString();
    }
}