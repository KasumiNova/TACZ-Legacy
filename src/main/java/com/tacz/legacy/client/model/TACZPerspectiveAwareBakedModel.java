package com.tacz.legacy.client.model;

import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nonnull;
import javax.vecmath.Matrix4f;

/**
 * Wraps a baked item model to capture the current {@link ItemCameraTransforms.TransformType}
 * before the TEISR is invoked. In 1.12.2, {@code TileEntityItemStackRenderer.renderByItem}
 * does not receive the transform type, so TEISRs read it from
 * {@link #getCurrentTransformType()} instead.
 * <p>
 * Install via {@code ModelBakeEvent} for any item that uses {@code "parent": "builtin/entity"}.
 */
@SideOnly(Side.CLIENT)
public class TACZPerspectiveAwareBakedModel extends BakedModelWrapper<IBakedModel> {
    private static ItemCameraTransforms.TransformType currentTransformType = ItemCameraTransforms.TransformType.NONE;

    public TACZPerspectiveAwareBakedModel(IBakedModel original) {
        super(original);
    }

    @Override
    @Nonnull
    public Pair<? extends IBakedModel, Matrix4f> handlePerspective(@Nonnull ItemCameraTransforms.TransformType cameraTransformType) {
        currentTransformType = cameraTransformType;
        // For item-presentation contexts (GUI, ground, fixed, head) the TEISRs render
        // slot textures in a coordinate space that assumes no pre-applied display
        // transforms.  Upstream TACZ 1.20 model JSONs have no/identity display
        // sections so BEWLRs always start from a clean matrix.  Return null to
        // neutralise the transforms from Legacy model JSONs for these contexts,
        // while keeping hand-context matrices unchanged to avoid first-person side effects.
        if (isItemPresentationContext(cameraTransformType)) {
            return Pair.of(this, null);
        }
        Pair<? extends IBakedModel, Matrix4f> result = originalModel.handlePerspective(cameraTransformType);
        return Pair.of(this, result.getRight());
    }

    /**
     * Returns the transform type that was last set during {@code handlePerspective}.
     * Called by TACZ TEISRs on the render thread to determine the current display context.
     */
    @Nonnull
    public static ItemCameraTransforms.TransformType getCurrentTransformType() {
        return currentTransformType;
    }

    /**
     * Whether the given transform type is an "item presentation" context
     * (GUI, dropped, item frame, head) as opposed to a hand-held context.
     */
    public static boolean isItemPresentationContext(@Nonnull ItemCameraTransforms.TransformType type) {
        switch (type) {
            case GUI:
            case GROUND:
            case FIXED:
            case HEAD:
            case NONE:
                return true;
            default:
                return false;
        }
    }
}
