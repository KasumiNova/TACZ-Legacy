package com.tacz.legacy.client.model;

import com.tacz.legacy.api.item.IAttachment;
import com.tacz.legacy.api.item.IGun;
import com.tacz.legacy.api.item.attachment.AttachmentType;
import com.tacz.legacy.client.model.bedrock.BedrockPart;
import com.tacz.legacy.client.model.functional.AttachmentRender;
import com.tacz.legacy.client.model.functional.LeftHandRender;
import com.tacz.legacy.client.model.functional.RightHandRender;
import com.tacz.legacy.client.model.functional.TextShowRender;
import com.tacz.legacy.client.resource.TACZClientAssetManager;
import com.tacz.legacy.client.resource.index.ClientAttachmentIndex;
import com.tacz.legacy.client.resource.pojo.display.gun.TextShow;
import com.tacz.legacy.client.resource.pojo.model.BedrockModelPOJO;
import com.tacz.legacy.client.resource.pojo.model.BedrockVersion;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

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
import static com.tacz.legacy.client.model.GunModelConstant.IRON_VIEW_NODE;
import static com.tacz.legacy.client.model.GunModelConstant.LEFTHAND_POS_NODE;
import static com.tacz.legacy.client.model.GunModelConstant.MOUNT;
import static com.tacz.legacy.client.model.GunModelConstant.MUZZLE_FLASH_ORIGIN_NODE;
import static com.tacz.legacy.client.model.GunModelConstant.RIGHTHAND_POS_NODE;
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
    private final EnumMap<AttachmentType, ItemStack> currentAttachmentItem = new EnumMap<>(AttachmentType.class);
    private final Set<String> adapterToRender = new HashSet<>();
    @Nullable
    private final LeftHandRender leftHandRender;
    @Nullable
    private final RightHandRender rightHandRender;

    @Nullable
    private List<BedrockPart> ironSightPath;
    @Nullable
    private List<BedrockPart> scopePosPath;
    @Nullable
    private List<BedrockPart> muzzleFlashPosPath;
    @Nullable
    private ResourceLocation activeGunTexture;

    private boolean renderHand = false;
    private ItemStack currentGunItem = ItemStack.EMPTY;

    public BedrockGunModel(BedrockModelPOJO pojo, BedrockVersion version) {
        super(pojo, version);
        this.leftHandRender = new LeftHandRender(this);
        this.rightHandRender = new RightHandRender(this);
        this.ironSightPath = getPath(modelMap.get(IRON_VIEW_NODE));
        this.scopePosPath = getPath(modelMap.get(AttachmentType.SCOPE.getSerializedName() + ATTACHMENT_POS_SUFFIX));
        this.muzzleFlashPosPath = getPath(modelMap.get(MUZZLE_FLASH_ORIGIN_NODE));

        this.setFunctionalRenderer(LEFTHAND_POS_NODE, bedrockPart -> leftHandRender);
        this.setFunctionalRenderer(RIGHTHAND_POS_NODE, bedrockPart -> rightHandRender);
        this.setFunctionalRenderer(MOUNT, bedrockPart -> scopeVisibilityRender(bedrockPart, true));
        this.setFunctionalRenderer(CARRY, bedrockPart -> scopeVisibilityRender(bedrockPart, false));
        this.setFunctionalRenderer(SIGHT_FOLDED, bedrockPart -> scopeVisibilityRender(bedrockPart, true));
        this.setFunctionalRenderer(SIGHT, bedrockPart -> scopeVisibilityRender(bedrockPart, false));
        this.setFunctionalRenderer(HANDGUARD_DEFAULT_NODE, this::handguardDefaultRender);
        this.setFunctionalRenderer(HANDGUARD_TACTICAL_NODE, this::handguardTacticalRender);
        this.setFunctionalRenderer(ATTACHMENT_ADAPTER_NODE, this::attachmentAdapterNodeRender);
        allAttachmentRender();
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

    private void updateAttachmentRuntime(ItemStack gunItem) {
        currentGunItem = gunItem;
        adapterToRender.clear();
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
            ClientAttachmentIndex attachmentIndex = TACZClientAssetManager.INSTANCE.getAttachmentIndex(iAttachment.getAttachmentId(attachmentItem));
            if (attachmentIndex != null && attachmentIndex.getAdapterNodeName() != null && !attachmentIndex.getAdapterNodeName().isEmpty()) {
                adapterToRender.add(attachmentIndex.getAdapterNodeName());
            }
        }
    }

    public void render(ItemStack gunItem) {
        updateAttachmentRuntime(gunItem);
        super.render();
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

    public EnumMap<AttachmentType, ItemStack> getCurrentAttachmentItem() {
        return currentAttachmentItem;
    }

    public ItemStack getCurrentGunItem() {
        return currentGunItem;
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

    @Override
    public boolean getRenderHand() {
        return renderHand;
    }

    public void setRenderHand(boolean renderHand) {
        this.renderHand = renderHand;
    }
}
