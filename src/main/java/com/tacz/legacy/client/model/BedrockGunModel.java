package com.tacz.legacy.client.model;

import com.tacz.legacy.TACZLegacy;
import com.tacz.legacy.api.item.IAttachment;
import com.tacz.legacy.api.item.IGun;
import com.tacz.legacy.api.client.animation.AnimationListener;
import com.tacz.legacy.api.client.animation.ObjectAnimationChannel;
import com.tacz.legacy.api.item.attachment.AttachmentType;
import com.tacz.legacy.client.model.bedrock.BedrockPart;
import com.tacz.legacy.client.model.bedrock.BedrockRenderMode;
import com.tacz.legacy.client.model.bedrock.ModelRendererWrapper;
import com.tacz.legacy.client.model.functional.AttachmentRender;
import com.tacz.legacy.client.model.functional.BeamRenderer;
import com.tacz.legacy.client.model.functional.LeftHandRender;
import com.tacz.legacy.client.model.functional.MuzzleFlashRender;
import com.tacz.legacy.client.model.functional.RightHandRender;
import com.tacz.legacy.client.model.functional.ShellRender;
import com.tacz.legacy.client.model.functional.TextShowRender;
import com.tacz.legacy.client.resource.pojo.display.gun.ShellEjection;
import com.tacz.legacy.client.model.listener.model.ModelAdditionalMagazineListener;
import com.tacz.legacy.client.resource.TACZClientAssetManager;
import com.tacz.legacy.client.resource.index.ClientAttachmentIndex;
import com.tacz.legacy.client.resource.pojo.display.gun.TextShow;
import com.tacz.legacy.client.resource.pojo.model.BedrockModelPOJO;
import com.tacz.legacy.client.resource.pojo.model.BedrockVersion;
import com.tacz.legacy.common.resource.GunDataAccessor;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.tacz.legacy.client.model.GunModelConstant.ATTACHMENT_ADAPTER_NODE;
import static com.tacz.legacy.client.model.GunModelConstant.ATTACHMENT_POS_SUFFIX;
import static com.tacz.legacy.client.model.GunModelConstant.CARRY;
import static com.tacz.legacy.client.model.GunModelConstant.DEFAULT_ATTACHMENT_SUFFIX;
import static com.tacz.legacy.client.model.GunModelConstant.HANDGUARD_DEFAULT_NODE;
import static com.tacz.legacy.client.model.GunModelConstant.HANDGUARD_TACTICAL_NODE;
import static com.tacz.legacy.client.model.GunModelConstant.IDLE_VIEW_NODE;
import static com.tacz.legacy.client.model.GunModelConstant.IRON_VIEW_NODE;
import static com.tacz.legacy.client.model.GunModelConstant.LEFTHAND_POS_NODE;
import static com.tacz.legacy.client.model.GunModelConstant.MAG_ADDITIONAL_NODE;
import static com.tacz.legacy.client.model.GunModelConstant.MAG_EXTENDED_1;
import static com.tacz.legacy.client.model.GunModelConstant.MAG_EXTENDED_2;
import static com.tacz.legacy.client.model.GunModelConstant.MAG_EXTENDED_3;
import static com.tacz.legacy.client.model.GunModelConstant.MAG_STANDARD;
import static com.tacz.legacy.client.model.GunModelConstant.MOUNT;
import static com.tacz.legacy.client.model.GunModelConstant.MAG_NORMAL_NODE;
import static com.tacz.legacy.client.model.GunModelConstant.MUZZLE_FLASH_ORIGIN_NODE;
import static com.tacz.legacy.client.model.GunModelConstant.REFIT_VIEW_NODE;
import static com.tacz.legacy.client.model.GunModelConstant.REFIT_VIEW_PREFIX;
import static com.tacz.legacy.client.model.GunModelConstant.REFIT_VIEW_SUFFIX;
import static com.tacz.legacy.client.model.GunModelConstant.RIGHTHAND_POS_NODE;
import static com.tacz.legacy.client.model.GunModelConstant.SHELL_ORIGIN_NODE;
import static com.tacz.legacy.client.model.GunModelConstant.SHELL_ORIGIN_NODE_PREFIX;
import static com.tacz.legacy.client.model.GunModelConstant.SIGHT;
import static com.tacz.legacy.client.model.GunModelConstant.SIGHT_FOLDED;

/**
 * Gun-specific bedrock runtime used by mounted attachment rendering.
 *
 * This is the Legacy landing zone for upstream TACZ BedrockGunModel parity:
 * current gun attachment state is resolved here, then mapped onto functional
 * renderers for scope/muzzle/grip/stock nodes, adapter nodes, and *_default
 * visibility.
 */
public class BedrockGunModel extends BedrockAnimatedModel {
    private final EnumMap<AttachmentType, List<BedrockPart>> refitAttachmentViewPath = new EnumMap<>(AttachmentType.class);
    private final EnumMap<AttachmentType, ItemStack> currentAttachmentItem = new EnumMap<>(AttachmentType.class);
    private final Set<String> adapterToRender = new HashSet<>();
    private final ArrayList<ShellRender> shellRenderList = new ArrayList<>();
    private @Nullable ShellEjection shellEjection;
    @Nullable
    private final BedrockPart magazineNode;
    @Nullable
    private final BedrockPart additionalMagazineNode;
    @Nullable
    private final LeftHandRender leftHandRender;
    @Nullable
    private final RightHandRender rightHandRender;
    @Nullable
    private final MuzzleFlashRender muzzleFlashRender;

    @Nullable
    private List<BedrockPart> ironSightPath;
    @Nullable
    private List<BedrockPart> scopePosPath;
    @Nullable
    private List<BedrockPart> muzzleFlashPosPath;
    @Nullable
    private List<BedrockPart> laserBeamPath;
    @Nullable
    private ResourceLocation activeGunTexture;

    private boolean renderHand = false;
    private ItemStack currentGunItem = ItemStack.EMPTY;
    private int currentExtendMagLevel = 0;
    @Nullable
    private String lastFocusedSmokeMagStateKey;

    public BedrockGunModel(BedrockModelPOJO pojo, BedrockVersion version) {
        super(pojo, version);
        this.leftHandRender = new LeftHandRender(this);
        this.rightHandRender = new RightHandRender(this);
        this.muzzleFlashRender = new MuzzleFlashRender(this);
        this.magazineNode = resolveNode(MAG_NORMAL_NODE);
        this.additionalMagazineNode = resolveNode(MAG_ADDITIONAL_NODE);
        this.idleSightPath = getPath(modelMap.get(IDLE_VIEW_NODE));
        this.ironSightPath = getPath(modelMap.get(IRON_VIEW_NODE));
        this.scopePosPath = getPath(modelMap.get(AttachmentType.SCOPE.getSerializedName() + ATTACHMENT_POS_SUFFIX));
        this.muzzleFlashPosPath = getPath(modelMap.get(MUZZLE_FLASH_ORIGIN_NODE));
        this.laserBeamPath = getPath(modelMap.get("laser_beam"));

        this.setFunctionalRenderer(LEFTHAND_POS_NODE, bedrockPart -> leftHandRender);
        this.setFunctionalRenderer(RIGHTHAND_POS_NODE, bedrockPart -> rightHandRender);
        this.setFunctionalRenderer(MUZZLE_FLASH_ORIGIN_NODE, bedrockPart -> muzzleFlashRender);
        this.setFunctionalRenderer(MOUNT, bedrockPart -> scopeVisibilityRender(bedrockPart, true));
        this.setFunctionalRenderer(CARRY, bedrockPart -> scopeVisibilityRender(bedrockPart, false));
        this.setFunctionalRenderer(SIGHT_FOLDED, bedrockPart -> scopeVisibilityRender(bedrockPart, true));
        this.setFunctionalRenderer(SIGHT, bedrockPart -> scopeVisibilityRender(bedrockPart, false));
        this.setFunctionalRenderer(HANDGUARD_DEFAULT_NODE, this::handguardDefaultRender);
        this.setFunctionalRenderer(HANDGUARD_TACTICAL_NODE, this::handguardTacticalRender);
        this.setFunctionalRenderer(ATTACHMENT_ADAPTER_NODE, this::attachmentAdapterNodeRender);
        this.setFunctionalRenderer(MAG_STANDARD, bedrockPart -> magVisibilityRender(bedrockPart, 0));
        this.setFunctionalRenderer(MAG_EXTENDED_1, bedrockPart -> magVisibilityRender(bedrockPart, 1));
        this.setFunctionalRenderer(MAG_EXTENDED_2, bedrockPart -> magVisibilityRender(bedrockPart, 2));
        this.setFunctionalRenderer(MAG_EXTENDED_3, bedrockPart -> magVisibilityRender(bedrockPart, 3));
        this.setFunctionalRenderer(MAG_ADDITIONAL_NODE, this::renderAdditionalMagazine);
        this.cacheRefitAttachmentViewPath();
        this.cacheShellOriginNodes();
        allAttachmentRender();
    }

    @Nullable
    private BedrockPart resolveNode(String name) {
        ModelRendererWrapper wrapper = modelMap.get(name);
        return wrapper != null ? wrapper.getModelRenderer() : null;
    }

    private void cacheRefitAttachmentViewPath() {
        for (AttachmentType type : AttachmentType.values()) {
            if (type == AttachmentType.NONE) {
                refitAttachmentViewPath.put(type, getPath(modelMap.get(REFIT_VIEW_NODE)));
                continue;
            }
            String nodeName = REFIT_VIEW_PREFIX + type.getSerializedName() + REFIT_VIEW_SUFFIX;
            refitAttachmentViewPath.put(type, getPath(modelMap.get(nodeName)));
        }
    }

    private void cacheShellOriginNodes() {
        ModelRendererWrapper rendererWrapper = modelMap.get(SHELL_ORIGIN_NODE);
        int shellIndex = 0;
        int suffixIndex = 1;
        while (rendererWrapper != null) {
            ShellRender shellRender = new ShellRender(this, shellIndex);
            this.setFunctionalRenderer(rendererWrapper.getModelRenderer().name, bedrockPart -> shellRender);
            shellRenderList.add(shellRender);
            rendererWrapper = modelMap.get(SHELL_ORIGIN_NODE_PREFIX + suffixIndex);
            shellIndex++;
            suffixIndex++;
        }
    }

    /**
     * Register text show functional renderers for named bones.
     * Port of upstream TACZ BedrockGunModel.setTextShowList().
     */
    public void setTextShowList(Map<String, TextShow> textShowList) {
        textShowList.forEach((name, textShow) ->
                this.setFunctionalRenderer(name, bedrockPart -> new TextShowRender(this, textShow, currentGunItem)));
    }

    private void allAttachmentRender() {
        for (AttachmentType type : AttachmentType.values()) {
            if (type == AttachmentType.NONE) {
                continue;
            }
            String baseNodeName = type.getSerializedName();
            String positionNodeName = baseNodeName + ATTACHMENT_POS_SUFFIX;
            String defaultNodeName = baseNodeName + DEFAULT_ATTACHMENT_SUFFIX;
            this.setFunctionalRenderer(positionNodeName, bedrockPart -> {
                bedrockPart.visible = false;
                return new AttachmentRender(this, type);
            });
            this.setFunctionalRenderer(defaultNodeName, bedrockPart -> {
                ItemStack attachmentItem = getAttachmentItem(type);
                if (type == AttachmentType.MUZZLE && applyShowMuzzle(bedrockPart, attachmentItem)) {
                    return null;
                }
                bedrockPart.visible = attachmentItem == null || attachmentItem.isEmpty();
                return null;
            });
        }
    }

    private boolean applyShowMuzzle(BedrockPart bedrockPart, @Nullable ItemStack attachmentItem) {
        IAttachment iAttachment = IAttachment.getIAttachmentOrNull(attachmentItem);
        if (iAttachment == null) {
            return false;
        }
        ClientAttachmentIndex attachmentIndex = TACZClientAssetManager.INSTANCE.getAttachmentIndex(iAttachment.getAttachmentId(attachmentItem));
        if (attachmentIndex == null) {
            return false;
        }
        bedrockPart.visible = attachmentIndex.isShowMuzzle();
        return true;
    }

    @Nullable
    private IFunctionalRenderer attachmentAdapterNodeRender(BedrockPart bedrockPart) {
        for (BedrockPart child : bedrockPart.children) {
            if (child.name == null) {
                child.visible = false;
                continue;
            }
            child.visible = adapterToRender.contains(child.name);
        }
        return null;
    }

    @Nullable
    private IFunctionalRenderer handguardDefaultRender(BedrockPart bedrockPart) {
        ItemStack laserItem = getAttachmentItem(AttachmentType.LASER);
        ItemStack gripItem = getAttachmentItem(AttachmentType.GRIP);
        bedrockPart.visible = (laserItem == null || laserItem.isEmpty()) && (gripItem == null || gripItem.isEmpty());
        return null;
    }

    @Nullable
    private IFunctionalRenderer handguardTacticalRender(BedrockPart bedrockPart) {
        ItemStack laserItem = getAttachmentItem(AttachmentType.LASER);
        ItemStack gripItem = getAttachmentItem(AttachmentType.GRIP);
        bedrockPart.visible = (laserItem != null && !laserItem.isEmpty()) || (gripItem != null && !gripItem.isEmpty());
        return null;
    }

    @Nullable
    private IFunctionalRenderer scopeVisibilityRender(BedrockPart bedrockPart, boolean visibleWhenScopeInstalled) {
        ItemStack scopeItem = getAttachmentItem(AttachmentType.SCOPE);
        boolean hasScope = scopeItem != null && !scopeItem.isEmpty();
        bedrockPart.visible = visibleWhenScopeInstalled ? hasScope : !hasScope;
        return null;
    }

    @Nullable
    private IFunctionalRenderer magVisibilityRender(BedrockPart bedrockPart, int expectedLevel) {
        bedrockPart.visible = currentExtendMagLevel == expectedLevel;
        return null;
    }

    @Nullable
    private IFunctionalRenderer renderAdditionalMagazine(BedrockPart bedrockPart) {
        return new IFunctionalRenderer() {
            @Override
            public void render(int light) {
                if (!bedrockPart.visible) {
                    return;
                }
                renderNodeContentAtCurrentTransform(bedrockPart, BedrockRenderMode.NORMAL);
                if (magazineNode != null && magazineNode.visible) {
                    renderNodeContentAtCurrentTransform(magazineNode, BedrockRenderMode.NORMAL);
                }
            }

            @Override
            public void renderBloom(int light) {
                if (!bedrockPart.visible) {
                    return;
                }
                renderNodeContentAtCurrentTransform(bedrockPart, BedrockRenderMode.BLOOM);
                if (magazineNode != null && magazineNode.visible) {
                    renderNodeContentAtCurrentTransform(magazineNode, BedrockRenderMode.BLOOM);
                }
            }
        };
    }

    private static void renderNodeContentAtCurrentTransform(BedrockPart bedrockPart, BedrockRenderMode mode) {
        boolean subtreeIlluminated = bedrockPart.illuminated;
        boolean renderSelf = mode != BedrockRenderMode.BLOOM || subtreeIlluminated;
        if (renderSelf && !bedrockPart.cubes.isEmpty()) {
            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder buffer = tessellator.getBuffer();
            buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_NORMAL);
            bedrockPart.cubes.forEach(cube -> cube.compile(buffer));
            tessellator.draw();
        }
        for (BedrockPart child : bedrockPart.children) {
            child.render(mode, subtreeIlluminated);
        }
    }

    private void updateAttachmentRuntime(ItemStack gunItem) {
        currentGunItem = gunItem;
        adapterToRender.clear();
        currentExtendMagLevel = 0;
        IGun iGun = IGun.getIGunOrNull(gunItem);
        if (iGun == null) {
            currentAttachmentItem.clear();
            return;
        }
        for (AttachmentType type : AttachmentType.values()) {
            if (type == AttachmentType.NONE) {
                continue;
            }
            ItemStack attachmentItem = iGun.getAttachment(gunItem, type);
            if (attachmentItem.isEmpty()) {
                attachmentItem = iGun.getBuiltinAttachment(gunItem, type);
            }
            currentAttachmentItem.put(type, attachmentItem);
            IAttachment iAttachment = IAttachment.getIAttachmentOrNull(attachmentItem);
            if (iAttachment == null) {
                continue;
            }
            if (type == AttachmentType.EXTENDED_MAG) {
                ResourceLocation attachmentId = iAttachment.getAttachmentId(attachmentItem);
                currentExtendMagLevel = GunDataAccessor.getAttachmentExtendedMagLevel(attachmentId);
            }
            ClientAttachmentIndex attachmentIndex = TACZClientAssetManager.INSTANCE.getAttachmentIndex(iAttachment.getAttachmentId(attachmentItem));
            if (attachmentIndex != null && attachmentIndex.getAdapterNodeName() != null && !attachmentIndex.getAdapterNodeName().isEmpty()) {
                adapterToRender.add(attachmentIndex.getAdapterNodeName());
            }
        }
    }

    @Override
    public @Nullable AnimationListener supplyListeners(String node, ObjectAnimationChannel.ChannelType type) {
        AnimationListener listener = super.supplyListeners(node, type);
        if (listener == null) {
            return null;
        }
        if (MAG_ADDITIONAL_NODE.equals(node)) {
            return new ModelAdditionalMagazineListener(listener, this);
        }
        return listener;
    }

    @Override
    public void cleanAnimationTransform() {
        super.cleanAnimationTransform();
        if (additionalMagazineNode != null) {
            additionalMagazineNode.visible = false;
        }
    }

    public void render(ItemStack gunItem) {
        updateAttachmentRuntime(gunItem);
        if (laserBeamPath != null) {
            BeamRenderer.renderLaserBeam(gunItem, laserBeamPath);
        }
        super.render();
        logFocusedSmokeMagRenderState(gunItem);
    }

    public void renderBloom(ItemStack gunItem) {
        updateAttachmentRuntime(gunItem);
        super.renderBloom();
    }

    private void logFocusedSmokeMagRenderState(ItemStack gunItem) {
        if (!Boolean.parseBoolean(System.getProperty("tacz.focusedSmoke", "false"))) {
            return;
        }
        IGun iGun = IGun.getIGunOrNull(gunItem);
        if (iGun == null) {
            return;
        }
        ResourceLocation gunId = iGun.getGunId(gunItem);
        boolean standardVisible = isNodeVisible(MAG_STANDARD);
        boolean extended1Visible = isNodeVisible(MAG_EXTENDED_1);
        boolean extended2Visible = isNodeVisible(MAG_EXTENDED_2);
        boolean extended3Visible = isNodeVisible(MAG_EXTENDED_3);
        boolean additionalVisible = additionalMagazineNode != null && additionalMagazineNode.visible;
        String stateKey = gunId + "|level=" + currentExtendMagLevel
                + "|standard=" + standardVisible
                + "|ext1=" + extended1Visible
                + "|ext2=" + extended2Visible
                + "|ext3=" + extended3Visible
                + "|additional=" + additionalVisible;
        if (stateKey.equals(lastFocusedSmokeMagStateKey)) {
            return;
        }
        lastFocusedSmokeMagStateKey = stateKey;
        TACZLegacy.logger.info(
                "[FocusedSmoke] MAG_RENDER_STATE gun={} level={} standard={} ext1={} ext2={} ext3={} additional={}",
                gunId,
                currentExtendMagLevel,
                standardVisible,
                extended1Visible,
                extended2Visible,
                extended3Visible,
                additionalVisible
        );
    }

    private boolean isNodeVisible(String nodeName) {
        BedrockPart node = resolveNode(nodeName);
        return node != null && node.visible;
    }

    @Nullable
    public List<BedrockPart> resolveAimingViewPath(ItemStack gunItem) {
        IGun iGun = IGun.getIGunOrNull(gunItem);
        if (iGun == null) {
            return ironSightPath;
        }
        ItemStack scopeItem = iGun.getAttachment(gunItem, AttachmentType.SCOPE);
        if (scopeItem.isEmpty()) {
            scopeItem = iGun.getBuiltinAttachment(gunItem, AttachmentType.SCOPE);
        }
        if (scopeItem.isEmpty()) {
            return ironSightPath;
        }
        if (scopePosPath == null) {
            return ironSightPath;
        }
        IAttachment iAttachment = IAttachment.getIAttachmentOrNull(scopeItem);
        if (iAttachment == null) {
            return scopePosPath;
        }
        ClientAttachmentIndex attachmentIndex = TACZClientAssetManager.INSTANCE.getAttachmentIndex(iAttachment.getAttachmentId(scopeItem));
        if (attachmentIndex == null) {
            return scopePosPath;
        }
        BedrockAttachmentModel attachmentModel = attachmentIndex.getAttachmentModel();
        if (attachmentModel == null) {
            return scopePosPath;
        }
        int[] views = attachmentIndex.getViews();
        int zoomNumber = iAttachment.getZoomNumber(scopeItem);
        int viewSwitchCount = 0;
        if (views.length > 0) {
            viewSwitchCount = Math.max(0, views[Math.floorMod(zoomNumber, views.length)] - 1);
        }
        List<BedrockPart> scopeViewPath = attachmentModel.getScopeViewPath(viewSwitchCount);
        if (scopeViewPath == null || scopeViewPath.isEmpty()) {
            return scopePosPath;
        }
        List<BedrockPart> combined = new ArrayList<>(scopePosPath);
        combined.addAll(scopeViewPath);
        return combined;
    }

    @Nullable
    public ItemStack getAttachmentItem(AttachmentType type) {
        ItemStack stack = currentAttachmentItem.get(type);
        return stack == null ? ItemStack.EMPTY : stack;
    }

    @Nullable
    public List<BedrockPart> getRefitAttachmentViewPath(AttachmentType type) {
        return refitAttachmentViewPath.get(type);
    }

    public EnumMap<AttachmentType, ItemStack> getCurrentAttachmentItem() {
        return currentAttachmentItem;
    }

    public ItemStack getCurrentGunItem() {
        return currentGunItem;
    }

    @Nullable
    public BedrockPart getAdditionalMagazineNode() {
        return additionalMagazineNode;
    }

    @Nullable
    public ResourceLocation getActiveGunTexture() {
        return activeGunTexture;
    }

    public void setActiveGunTexture(@Nullable ResourceLocation activeGunTexture) {
        this.activeGunTexture = activeGunTexture;
        if (leftHandRender != null) {
            leftHandRender.setGunTexture(activeGunTexture);
        }
        if (rightHandRender != null) {
            rightHandRender.setGunTexture(activeGunTexture);
        }
    }

    @Nullable
    public List<BedrockPart> getIronSightPath() {
        return ironSightPath;
    }

    @Nullable
    public List<BedrockPart> getScopePosPath() {
        return scopePosPath;
    }

    @Nullable
    public List<BedrockPart> getMuzzleFlashPosPath() {
        return muzzleFlashPosPath;
    }

    @Nullable
    public MuzzleFlashRender getMuzzleFlashRender() {
        return muzzleFlashRender;
    }

    @Nullable
    public ShellRender getShellRender(int index) {
        if (index < 0 || index >= shellRenderList.size()) {
            return null;
        }
        return shellRenderList.get(index);
    }

    @Nullable
    public ShellEjection getShellEjection() {
        return shellEjection;
    }

    public void setShellEjection(@Nullable ShellEjection shellEjection) {
        this.shellEjection = shellEjection;
    }

    @Override
    public boolean getRenderHand() {
        return renderHand;
    }

    public void setRenderHand(boolean renderHand) {
        this.renderHand = renderHand;
    }
}
