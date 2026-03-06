package com.tacz.legacy.client.model;

import com.tacz.legacy.client.model.bedrock.BedrockCubePerFace;
import com.tacz.legacy.client.model.bedrock.BedrockPart;
import com.tacz.legacy.client.resource.pojo.model.FaceUVsItem;

/**
 * A simple flat-quad model used for GUI slot rendering of ammo/attachment items.
 * Port of upstream TACZ SlotModel for 1.12.2 (uses BedrockPart.render() instead of PoseStack).
 */
public class SlotModel {
    private final BedrockPart bone;

    public SlotModel(boolean illuminated) {
        bone = new BedrockPart("slot");
        bone.setPos(8.0F, 24.0F, -10.0F);
        bone.cubes.add(new BedrockCubePerFace(-16.0F, -16.0F, 9.5F, 16.0F, 16.0F, 0, 0, 16, 16, FaceUVsItem.singleSouthFace()));
        bone.illuminated = illuminated;
    }

    public SlotModel() {
        this(false);
    }

    /**
     * Render the flat quad. Caller must bind texture and set up GL state.
     */
    public void render() {
        bone.render();
    }
}
