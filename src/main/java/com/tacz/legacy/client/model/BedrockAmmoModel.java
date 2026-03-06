package com.tacz.legacy.client.model;

import com.tacz.legacy.client.model.bedrock.BedrockModel;
import com.tacz.legacy.client.model.bedrock.BedrockPart;
import com.tacz.legacy.client.resource.pojo.model.BedrockModelPOJO;
import com.tacz.legacy.client.resource.pojo.model.BedrockVersion;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Bedrock model with positioning node paths for ammo items.
 * Port of upstream TACZ BedrockAmmoModel.
 */
public class BedrockAmmoModel extends BedrockModel {
    private static final String FIXED_ORIGIN_NODE = "fixed";
    private static final String GROUND_ORIGIN_NODE = "ground";
    private static final String THIRD_PERSON_HAND_ORIGIN_NODE = "thirdperson_hand";

    protected @Nullable List<BedrockPart> fixedOriginPath;
    protected @Nullable List<BedrockPart> groundOriginPath;
    protected @Nullable List<BedrockPart> thirdPersonHandOriginPath;

    public BedrockAmmoModel(BedrockModelPOJO pojo, BedrockVersion version) {
        super(pojo, version);
        fixedOriginPath = getPath(modelMap.get(FIXED_ORIGIN_NODE));
        groundOriginPath = getPath(modelMap.get(GROUND_ORIGIN_NODE));
        thirdPersonHandOriginPath = getPath(modelMap.get(THIRD_PERSON_HAND_ORIGIN_NODE));
    }

    @Nullable
    public List<BedrockPart> getFixedOriginPath() {
        return fixedOriginPath;
    }

    @Nullable
    public List<BedrockPart> getGroundOriginPath() {
        return groundOriginPath;
    }

    @Nullable
    public List<BedrockPart> getThirdPersonHandOriginPath() {
        return thirdPersonHandOriginPath;
    }
}
