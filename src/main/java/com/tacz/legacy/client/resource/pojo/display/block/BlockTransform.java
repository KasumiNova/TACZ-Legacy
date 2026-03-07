package com.tacz.legacy.client.resource.pojo.display.block;

import com.google.gson.annotations.SerializedName;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms.TransformType;

import javax.annotation.Nullable;

/**
 * Per-context transform data for block display.
 * Deserialized from the {@code "transforms"} field in block display JSON.
 * <p>
 * Format matches upstream TACZ / vanilla {@code ItemTransforms}:
 * <pre>{@code
 * "transforms": {
 *   "gui": { "rotation": [15, -145, 0], "translation": [-2.5, -3.75, 0], "scale": [0.4, 0.4, 0.4] },
 *   "ground": { ... },
 *   ...
 * }
 * }</pre>
 */
public class BlockTransform {
    @SerializedName("gui")
    @Nullable
    private Entry gui;

    @SerializedName("ground")
    @Nullable
    private Entry ground;

    @SerializedName("fixed")
    @Nullable
    private Entry fixed;

    @SerializedName("head")
    @Nullable
    private Entry head;

    @SerializedName("firstperson_righthand")
    @Nullable
    private Entry firstPersonRightHand;

    @SerializedName("firstperson_lefthand")
    @Nullable
    private Entry firstPersonLeftHand;

    @SerializedName("thirdperson_righthand")
    @Nullable
    private Entry thirdPersonRightHand;

    @SerializedName("thirdperson_lefthand")
    @Nullable
    private Entry thirdPersonLeftHand;

    @Nullable
    public Entry getForType(TransformType type) {
        switch (type) {
            case GUI:
                return gui;
            case GROUND:
                return ground;
            case FIXED:
                return fixed;
            case HEAD:
                return head;
            case FIRST_PERSON_RIGHT_HAND:
                return firstPersonRightHand;
            case FIRST_PERSON_LEFT_HAND:
                return firstPersonLeftHand;
            case THIRD_PERSON_RIGHT_HAND:
                return thirdPersonRightHand;
            case THIRD_PERSON_LEFT_HAND:
                return thirdPersonLeftHand;
            default:
                return null;
        }
    }

    /**
     * A single per-context transform entry: rotation (degrees), translation, and scale.
     */
    public static class Entry {
        @Nullable
        private float[] rotation;

        @Nullable
        private float[] translation;

        @Nullable
        private float[] scale;

        /**
         * Applies this transform to the current GL matrix.
         * Matches vanilla {@code ItemTransform.apply(false, poseStack)} behavior:
         * translate → rotate XYZ → scale.
         * <p>
         * Translation values are scaled by {@code 0.0625F} (1/16) to match vanilla's
         * {@code ItemTransform.Deserializer} which converts pixel-units to block-units
         * at parse time.  Upstream TACZ stores raw pixel-unit values in JSON and relies
         * on vanilla deserialization to apply this factor.
         */
        public void apply() {
            if (translation != null && translation.length >= 3) {
                GlStateManager.translate(
                        translation[0] * 0.0625F,
                        translation[1] * 0.0625F,
                        translation[2] * 0.0625F
                );
            }
            if (rotation != null && rotation.length >= 3) {
                GlStateManager.rotate(rotation[0], 1, 0, 0);
                GlStateManager.rotate(rotation[1], 0, 1, 0);
                GlStateManager.rotate(rotation[2], 0, 0, 1);
            }
            if (scale != null && scale.length >= 3) {
                GlStateManager.scale(scale[0], scale[1], scale[2]);
            }
        }

        @Nullable
        public float[] getRotation() {
            return rotation;
        }

        @Nullable
        public float[] getTranslation() {
            return translation;
        }

        @Nullable
        public float[] getScale() {
            return scale;
        }
    }
}
