package com.tacz.legacy.client.model;

import com.tacz.legacy.TACZLegacy;
import com.tacz.legacy.api.entity.IGunOperator;
import com.tacz.legacy.api.item.IAttachment;
import com.tacz.legacy.client.event.FirstPersonRenderGunEvent;
import com.tacz.legacy.client.model.bedrock.BedrockPart;
import com.tacz.legacy.client.model.bedrock.ModelRendererWrapper;
import com.tacz.legacy.client.model.functional.BeamRenderer;
import com.tacz.legacy.client.model.functional.TextShowRender;
import com.tacz.legacy.client.util.RenderHelper;
import com.tacz.legacy.client.resource.pojo.display.gun.TextShow;
import com.tacz.legacy.client.resource.pojo.model.BedrockModelPOJO;
import com.tacz.legacy.client.resource.pojo.model.BedrockVersion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.nio.FloatBuffer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Gun-mounted attachment model runtime.
 *
 * This is a minimal Legacy port of upstream BedrockAttachmentModel focused on
 * the runtime data needed for mounted attachment rendering and scope view
 * positioning. Full stencil/optic masking can be layered on top later.
 */
public class BedrockAttachmentModel extends BedrockAnimatedModel {
    private static final String SCOPE_VIEW_NODE = "scope_view";
    private static final String SCOPE_BODY_NODE = "scope_body";
    private static final String OCULAR_RING_NODE = "ocular_ring";
    private static final String DIVISION_NODE = "division";
    private static final String OCULAR_NODE = "ocular";
    private static final String OCULAR_SCOPE_NODE = "ocular_scope";
    private static final String OCULAR_SIGHT_NODE = "ocular_sight";
    private static final Pattern LASER_BEAM_PATTERN = Pattern.compile("^laser_beam(_(\\d+))?$");
    private static final Pattern OCULAR_PATTERN = Pattern.compile("^(" + OCULAR_NODE + "|" + OCULAR_SIGHT_NODE + "|" + OCULAR_SCOPE_NODE + ")(_(\\d+))?$");
    private static final FloatBuffer MODEL_VIEW_BUFFER = BufferUtils.createFloatBuffer(16);
    private static final Set<String> LOGGED_FOCUSED_SMOKE_OPTIC_KEYS = new HashSet<>();

    protected final List<List<BedrockPart>> scopeViewPaths = new ArrayList<>();
    @Nullable
    protected List<BedrockPart> scopeBodyPath;
    @Nullable
    protected List<BedrockPart> ocularRingPath;
    protected final List<List<BedrockPart>> ocularNodePaths = new ArrayList<>();
    protected final List<Boolean> scopeOcularFlags = new ArrayList<>();
    protected final List<List<BedrockPart>> divisionNodePaths = new ArrayList<>();
    protected final List<List<BedrockPart>> laserBeamPaths = new ArrayList<>();
    @Nullable
    private ItemStack currentGunItem;
    @Nullable
    private ItemStack attachmentItem;
    private boolean isScope = false;
    private boolean isSight = false;
    private float scopeViewRadiusModifier = 1.0f;

    public BedrockAttachmentModel(BedrockModelPOJO pojo, BedrockVersion version) {
        super(pojo, version);
        cacheScopeViewPaths();
        cacheOpticNodePaths();
        cacheLaserBeamPaths();
        hideIndexedNodes(SCOPE_VIEW_NODE);
        hideIndexedNodes(DIVISION_NODE);
        hideIndexedNodes(OCULAR_NODE);
        hideIndexedNodes(OCULAR_SCOPE_NODE);
        hideIndexedNodes(OCULAR_SIGHT_NODE);
    }

    private void cacheScopeViewPaths() {
        List<BedrockPart> path = getPath(modelMap.get(SCOPE_VIEW_NODE));
        int index = 2;
        while (path != null) {
            scopeViewPaths.add(path);
            path = getPath(modelMap.get(SCOPE_VIEW_NODE + '_' + index++));
        }
    }

    private void cacheOpticNodePaths() {
        TreeMap<Integer, OcularWrapper> orderedOculars = new TreeMap<>();
        for (Map.Entry<String, ModelRendererWrapper> entry : modelMap.entrySet()) {
            Matcher ocularMatcher = OCULAR_PATTERN.matcher(entry.getKey());
            if (ocularMatcher.matches()) {
                int index = 1;
                String suffix = ocularMatcher.group(3);
                if (suffix != null) {
                    index = Integer.parseInt(suffix);
                }
                boolean scopeOcular = OCULAR_SCOPE_NODE.equals(ocularMatcher.group(1));
                orderedOculars.put(index, new OcularWrapper(entry.getValue(), scopeOcular));
            }
        }
        for (OcularWrapper wrapper : orderedOculars.values()) {
            List<BedrockPart> path = getPath(wrapper.renderer);
            if (path != null) {
                ocularNodePaths.add(path);
                scopeOcularFlags.add(wrapper.scopeOcular);
            }
        }

        ModelRendererWrapper divisionWrapper = modelMap.get(DIVISION_NODE);
        int divisionIndex = 2;
        while (divisionWrapper != null) {
            List<BedrockPart> path = getPath(divisionWrapper);
            if (path != null) {
                divisionNodePaths.add(path);
            }
            divisionWrapper = modelMap.get(DIVISION_NODE + '_' + divisionIndex++);
        }

        scopeBodyPath = getPath(modelMap.get(SCOPE_BODY_NODE));
        ocularRingPath = getPath(modelMap.get(OCULAR_RING_NODE));
    }

    private void hideIndexedNodes(String baseNodeName) {
        ModelRendererWrapper wrapper = modelMap.get(baseNodeName);
        int index = 2;
        while (wrapper != null) {
            wrapper.setHidden(true);
            wrapper = modelMap.get(baseNodeName + '_' + index++);
        }
    }

    private void cacheLaserBeamPaths() {
        TreeMap<Integer, List<BedrockPart>> orderedPaths = new TreeMap<>();
        for (Map.Entry<String, ModelRendererWrapper> entry : modelMap.entrySet()) {
            Matcher matcher = LASER_BEAM_PATTERN.matcher(entry.getKey());
            if (!matcher.matches()) {
                continue;
            }
            int index = 1;
            String suffix = matcher.group(2);
            if (suffix != null) {
                index = Integer.parseInt(suffix);
            }
            List<BedrockPart> path = getPath(entry.getValue());
            if (path != null) {
                orderedPaths.put(index, path);
            }
        }
        laserBeamPaths.addAll(orderedPaths.values());
    }

    @Nullable
    public List<BedrockPart> getScopeViewPath(int viewSwitchCount) {
        if (scopeViewPaths.isEmpty()) {
            return null;
        }
        if (viewSwitchCount < 0 || viewSwitchCount >= scopeViewPaths.size()) {
            return scopeViewPaths.get(0);
        }
        return scopeViewPaths.get(viewSwitchCount);
    }

    public void setIsScope(boolean scope) {
        isScope = scope;
    }

    public void setIsSight(boolean sight) {
        isSight = sight;
    }

    public boolean isScope() {
        return isScope;
    }

    public boolean isSight() {
        return isSight;
    }

    public void setScopeViewRadiusModifier(float scopeViewRadiusModifier) {
        this.scopeViewRadiusModifier = scopeViewRadiusModifier;
    }

    public void setTextShowList(Map<String, TextShow> textShowList) {
        textShowList.forEach((name, textShow) -> setFunctionalRenderer(name,
            bedrockPart -> new TextShowRender(this, textShow, currentGunItem)));
    }

    public void render(@Nullable ItemStack attachmentItem, @Nullable ItemStack currentGunItem) {
        this.attachmentItem = attachmentItem;
        this.currentGunItem = currentGunItem;

        BeamRenderer.RenderContext renderContext = BeamRenderer.getRenderContext();
        boolean firstPerson = renderContext.isFirstPerson();
        if (!isScope && !isSight) {
            renderLaserBeams();
        }

        if (firstPerson) {
            if (isScope && isSight) {
                renderBoth();
            } else if (isScope) {
                renderScope();
            } else if (isSight) {
                renderSight();
            } else {
                super.render();
            }
        } else {
            if (scopeBodyPath != null) {
                renderTempPart(scopeBodyPath);
            }
            if (ocularRingPath != null) {
                renderTempPart(ocularRingPath);
            }
            super.render();
        }

        if (isScope || isSight) {
            renderLaserBeams();
        }
    }

    public void renderBloom(@Nullable ItemStack attachmentItem, @Nullable ItemStack currentGunItem) {
        this.attachmentItem = attachmentItem;
        this.currentGunItem = currentGunItem;
        super.renderBloom();
    }

    private void renderLaserBeams() {
        if (attachmentItem == null || attachmentItem.isEmpty() || laserBeamPaths.isEmpty()) {
            return;
        }
        for (List<BedrockPart> path : laserBeamPaths) {
            BeamRenderer.renderLaserBeam(attachmentItem, path);
        }
    }

    @Nullable
    public ItemStack getCurrentGunItem() {
        return currentGunItem;
    }

    @Nullable
    public ItemStack getAttachmentItem() {
        return attachmentItem;
    }

    private void renderBoth() {
        if (!RenderHelper.enableItemEntityStencilTest()) {
            super.render();
            return;
        }
        logFocusedSmokeOpticRender("both");
        try {
            GL11.glClearStencil(0);
            GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT);
            if (ocularRingPath != null) {
                GL11.glStencilFunc(GL11.GL_ALWAYS, 0, 0xFF);
                GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
                renderTempPart(ocularRingPath);
            }
            renderOcularStencil(true);
            if (scopeBodyPath != null) {
                GL11.glStencilFunc(GL11.GL_EQUAL, 0, 0xFF);
                renderTempPart(scopeBodyPath);
            }
            renderOcularStencil(false);
            renderOcularAndDivision(true);
        } finally {
            resetStencilState();
            RenderHelper.disableItemEntityStencilTest();
        }
        super.render();
    }

    private void renderSight() {
        if (!RenderHelper.enableItemEntityStencilTest()) {
            if (scopeBodyPath != null) {
                renderTempPart(scopeBodyPath);
            }
            super.render();
            return;
        }
        logFocusedSmokeOpticRender("sight");
        try {
            GL11.glClearStencil(0);
            GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT);
            renderOcularStencil(false);
            renderDivisionOnly();
        } finally {
            resetStencilState();
            RenderHelper.disableItemEntityStencilTest();
        }
        if (scopeBodyPath != null) {
            renderTempPart(scopeBodyPath);
        }
        super.render();
    }

    private void renderScope() {
        if (!RenderHelper.enableItemEntityStencilTest()) {
            super.render();
            return;
        }
        logFocusedSmokeOpticRender("scope");
        try {
            GL11.glClearStencil(0);
            GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT);
            if (ocularRingPath != null) {
                GL11.glStencilFunc(GL11.GL_ALWAYS, 0, 0xFF);
                GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
                renderTempPart(ocularRingPath);
            }
            renderOcularStencil(false);
            if (scopeBodyPath != null) {
                GL11.glStencilFunc(GL11.GL_EQUAL, 0, 0xFF);
                renderTempPart(scopeBodyPath);
            }
            renderOcularAndDivision(false);
        } finally {
            resetStencilState();
            RenderHelper.disableItemEntityStencilTest();
        }
        super.render();
    }

    private void resetStencilState() {
        GL11.glStencilMask(0xFF);
        GL11.glStencilFunc(GL11.GL_ALWAYS, 0, 0xFF);
        GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
        GL11.glColorMask(true, true, true, true);
        GL11.glDepthMask(true);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
    }

    private void renderOcularStencil(boolean scopeOcular) {
        if (ocularNodePaths.isEmpty()) {
            return;
        }
        GL11.glColorMask(false, false, false, false);
        GL11.glDepthMask(false);
        GL11.glStencilMask(0xFF);
        GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_REPLACE);
        for (int i = ocularNodePaths.size() - 1; i >= 0; i--) {
            if (scopeOcular == scopeOcularFlags.get(i)) {
                GL11.glStencilFunc(GL11.GL_GREATER, i + 1, 0xFF);
                renderTempPart(ocularNodePaths.get(i));
            }
        }
        GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
        GL11.glDepthMask(true);
        GL11.glColorMask(true, true, true, true);
    }

    private void renderDivisionOnly() {
        if (divisionNodePaths.isEmpty()) {
            return;
        }
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        try {
            for (int i = 0; i < divisionNodePaths.size(); i++) {
                GL11.glStencilFunc(GL11.GL_EQUAL, i + 1, 0xFF);
                renderTempPart(divisionNodePaths.get(i));
            }
        } finally {
            GL11.glEnable(GL11.GL_DEPTH_TEST);
        }
    }

    private void renderOcularAndDivision(boolean selective) {
        if (ocularNodePaths.isEmpty()) {
            return;
        }
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();

        GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_INVERT);
        GL11.glColorMask(false, false, false, false);
        GL11.glDepthMask(false);

        float radius = 80.0f * scopeViewRadiusModifier * resolveCurrentAimingProgress();
        for (int i = 0; i < ocularNodePaths.size(); i++) {
            if (selective && !scopeOcularFlags.get(i)) {
                continue;
            }
            GL11.glStencilFunc(GL11.GL_EQUAL, i + 1, 0xFF);
            Vector3f ocularCenter = getBedrockPartCenter(ocularNodePaths.get(i));
            float centerX = ocularCenter.x() * 16.0f * 90.0f;
            float centerY = ocularCenter.y() * 16.0f * 90.0f;
            buffer.begin(GL11.GL_TRIANGLE_FAN, DefaultVertexFormats.POSITION_COLOR);
            buffer.pos(centerX, centerY, -90.0D).color(255, 255, 255, 255).endVertex();
            for (int j = 0; j <= 90; j++) {
                float angle = j * ((float) Math.PI * 2.0F) / 90.0F;
                float sin = MathHelper.sin(angle);
                float cos = MathHelper.cos(angle);
                buffer.pos(centerX + cos * radius, centerY + sin * radius, -90.0D).color(255, 255, 255, 255).endVertex();
            }
            tessellator.draw();
        }

        GL11.glDepthMask(true);
        GL11.glColorMask(true, true, true, true);
        GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);

        for (int i = 0; i < ocularNodePaths.size() && i < divisionNodePaths.size(); i++) {
            if (selective && !scopeOcularFlags.get(i)) {
                GL11.glStencilFunc(GL11.GL_EQUAL, i + 1, 0xFF);
                renderTempPart(divisionNodePaths.get(i));
                continue;
            }
            GL11.glStencilFunc(GL11.GL_EQUAL, i + 1, 0xFF);
            renderTempPart(ocularNodePaths.get(i));
            int invertedMask = ~(i + 1) & 0xFF;
            GL11.glStencilFunc(GL11.GL_EQUAL, invertedMask, 0xFF);
            renderTempPart(divisionNodePaths.get(i));
        }
    }

    private Vector3f getBedrockPartCenter(List<BedrockPart> path) {
        GlStateManager.pushMatrix();
        try {
            for (BedrockPart part : path) {
                part.translateAndRotateAndScale();
            }
            MODEL_VIEW_BUFFER.clear();
            GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, MODEL_VIEW_BUFFER);
            MODEL_VIEW_BUFFER.rewind();
            Matrix4f matrix = new Matrix4f().set(MODEL_VIEW_BUFFER);
            return new Vector3f(matrix.m30(), matrix.m31(), matrix.m32());
        } finally {
            GlStateManager.popMatrix();
        }
    }

    private void renderTempPart(List<BedrockPart> path) {
        if (path.isEmpty()) {
            return;
        }
        GlStateManager.pushMatrix();
        try {
            for (int i = 0; i < path.size() - 1; i++) {
                path.get(i).translateAndRotateAndScale();
            }
            BedrockPart part = path.get(path.size() - 1);
            part.visible = true;
            part.render();
            part.visible = false;
        } finally {
            GlStateManager.popMatrix();
        }
    }

    private float resolveCurrentAimingProgress() {
        Float preparedAimingProgress = FirstPersonRenderGunEvent.getPreparedAimingProgress();
        if (preparedAimingProgress != null) {
            return MathHelper.clamp(preparedAimingProgress, 0.0f, 1.0f);
        }
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        if (player == null) {
            return 0.0f;
        }
        return MathHelper.clamp(IGunOperator.fromLivingEntity(player).getSynAimingProgress(), 0.0f, 1.0f);
    }

    private void logFocusedSmokeOpticRender(String mode) {
        if (!Boolean.parseBoolean(System.getProperty("tacz.focusedSmoke", "false"))) {
            return;
        }
        String attachmentId = resolveAttachmentDebugId();
        String key = attachmentId + '|' + mode;
        if (!LOGGED_FOCUSED_SMOKE_OPTIC_KEYS.add(key)) {
            return;
        }
        TACZLegacy.logger.info(
                "[FocusedSmoke] OPTIC_STENCIL_RENDERED attachment={} mode={} oculars={} divisions={}",
                attachmentId,
                mode,
                ocularNodePaths.size(),
                divisionNodePaths.size()
        );
    }

    private String resolveAttachmentDebugId() {
        if (attachmentItem != null && !attachmentItem.isEmpty()) {
            IAttachment iAttachment = IAttachment.getIAttachmentOrNull(attachmentItem);
            if (iAttachment != null) {
                return String.valueOf(iAttachment.getAttachmentId(attachmentItem));
            }
            if (attachmentItem.getItem().getRegistryName() != null) {
                return attachmentItem.getItem().getRegistryName().toString();
            }
        }
        return "unknown";
    }

    private static final class OcularWrapper {
        private final ModelRendererWrapper renderer;
        private final boolean scopeOcular;

        private OcularWrapper(ModelRendererWrapper renderer, boolean scopeOcular) {
            this.renderer = renderer;
            this.scopeOcular = scopeOcular;
        }
    }
}
