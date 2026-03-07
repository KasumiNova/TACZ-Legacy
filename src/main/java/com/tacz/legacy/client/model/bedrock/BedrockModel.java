package com.tacz.legacy.client.model.bedrock;

import com.tacz.legacy.client.model.IFunctionalRenderer;
import com.tacz.legacy.client.resource.pojo.model.*;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import org.joml.Vector2f;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.*;

/**
 * Core bedrock model class.
 * Loads geometry from Bedrock-format POJO and renders using 1.12.2 GL immediate mode.
 * Port of upstream TACZ BedrockModel.
 */
public class BedrockModel {
    public static final BedrockModel DUMMY = new BedrockModel();

    /**
     * Renderers delegated to run after the main model render pass completes.
     * Used by TextShowRender to avoid corrupting GL state during bone traversal.
     */
    protected List<IFunctionalRenderer> delegateRenderers = new ArrayList<>();

    protected final HashMap<String, ModelRendererWrapper> modelMap = new HashMap<>();
    protected final HashMap<String, BonesItem> indexBones = new HashMap<>();
    protected final List<BedrockPart> shouldRender = new LinkedList<>();
    protected @Nullable Vector3f offset = null;
    protected @Nullable Vector2f size = null;

    public BedrockModel(BedrockModelPOJO pojo, BedrockVersion version) {
        if (version == BedrockVersion.LEGACY) {
            loadLegacyModel(pojo);
        }
        if (version == BedrockVersion.NEW) {
            loadNewModel(pojo);
        }
        // Apply illumination to bones ending with _illuminated
        for (ModelRendererWrapper rendererWrapper : modelMap.values()) {
            if (rendererWrapper.getModelRenderer().name != null
                    && rendererWrapper.getModelRenderer().name.endsWith("_illuminated")) {
                rendererWrapper.getModelRenderer().illuminated = true;
            }
        }
    }

    /** Dummy constructor for sentinel instance. */
    protected BedrockModel() {
    }

    private void setRotationAngle(BedrockPart modelRenderer, float x, float y, float z) {
        modelRenderer.xRot = x;
        modelRenderer.yRot = y;
        modelRenderer.zRot = z;
        modelRenderer.setInitRotationAngle(x, y, z);
    }

    protected void loadNewModel(BedrockModelPOJO pojo) {
        assert pojo.getGeometryModelNew() != null;
        pojo.getGeometryModelNew().deco();
        if (pojo.getGeometryModelNew().getBones() == null) {
            return;
        }
        Description description = pojo.getGeometryModelNew().getDescription();
        int texWidth = description.getTextureWidth();
        int texHeight = description.getTextureHeight();

        List<Float> visBoundsOffset = description.getVisibleBoundsOffset();
        float offsetX = visBoundsOffset.get(0);
        float offsetY = visBoundsOffset.get(1);
        float offsetZ = visBoundsOffset.get(2);
        this.offset = new Vector3f(offsetX, offsetY, offsetZ);
        float width = description.getVisibleBoundsWidth() / 2.0f;
        float height = description.getVisibleBoundsHeight() / 2.0f;
        this.size = new Vector2f(width, height);

        for (BonesItem bones : pojo.getGeometryModelNew().getBones()) {
            indexBones.putIfAbsent(bones.getName(), bones);
            modelMap.putIfAbsent(bones.getName(), new ModelRendererWrapper(new BedrockPart(bones.getName())));
        }

        for (BonesItem bones : pojo.getGeometryModelNew().getBones()) {
            String name = bones.getName();
            @Nullable List<Float> rotation = bones.getRotation();
            @Nullable String parent = bones.getParent();
            BedrockPart model = modelMap.get(name).getModelRenderer();

            model.mirror = bones.isMirror();
            model.setPos(convertPivot(bones, 0), convertPivot(bones, 1), convertPivot(bones, 2));

            if (rotation != null) {
                setRotationAngle(model, convertRotation(rotation.get(0)), convertRotation(rotation.get(1)), convertRotation(rotation.get(2)));
            }

            if (parent != null) {
                BedrockPart parentPart = modelMap.get(parent).getModelRenderer();
                parentPart.addChild(model);
                model.parent = parentPart;
            } else {
                shouldRender.add(model);
                model.parent = null;
            }

            if (bones.getCubes() == null) {
                continue;
            }

            for (CubesItem cube : bones.getCubes()) {
                List<Float> uv = cube.getUv();
                @Nullable FaceUVsItem faceUv = cube.getFaceUv();
                List<Float> cubeSize = cube.getSize();
                @Nullable List<Float> cubeRotation = cube.getRotation();
                boolean mirror = cube.isMirror();
                float inflate = cube.getInflate();

                if (cubeRotation == null) {
                    if (faceUv == null) {
                        model.cubes.add(new BedrockCubeBox(uv.get(0), uv.get(1),
                                convertOrigin(bones, cube, 0), convertOrigin(bones, cube, 1), convertOrigin(bones, cube, 2),
                                cubeSize.get(0), cubeSize.get(1), cubeSize.get(2), inflate, mirror,
                                texWidth, texHeight));
                    } else {
                        model.cubes.add(new BedrockCubePerFace(
                                convertOrigin(bones, cube, 0), convertOrigin(bones, cube, 1), convertOrigin(bones, cube, 2),
                                cubeSize.get(0), cubeSize.get(1), cubeSize.get(2), inflate,
                                texWidth, texHeight, faceUv));
                    }
                } else {
                    BedrockPart cubeRenderer = new BedrockPart(null);
                    cubeRenderer.setPos(convertPivot(bones, cube, 0), convertPivot(bones, cube, 1), convertPivot(bones, cube, 2));
                    setRotationAngle(cubeRenderer, convertRotation(cubeRotation.get(0)), convertRotation(cubeRotation.get(1)), convertRotation(cubeRotation.get(2)));
                    if (faceUv == null) {
                        cubeRenderer.cubes.add(new BedrockCubeBox(uv.get(0), uv.get(1),
                                convertOrigin(cube, 0), convertOrigin(cube, 1), convertOrigin(cube, 2),
                                cubeSize.get(0), cubeSize.get(1), cubeSize.get(2), inflate, mirror,
                                texWidth, texHeight));
                    } else {
                        cubeRenderer.cubes.add(new BedrockCubePerFace(
                                convertOrigin(cube, 0), convertOrigin(cube, 1), convertOrigin(cube, 2),
                                cubeSize.get(0), cubeSize.get(1), cubeSize.get(2), inflate,
                                texWidth, texHeight, faceUv));
                    }
                    model.addChild(cubeRenderer);
                }
            }
        }
    }

    protected void loadLegacyModel(BedrockModelPOJO pojo) {
        assert pojo.getGeometryModelLegacy() != null;
        pojo.getGeometryModelLegacy().deco();
        if (pojo.getGeometryModelLegacy().getBones() == null) {
            return;
        }

        int texWidth = pojo.getGeometryModelLegacy().getTextureWidth();
        int texHeight = pojo.getGeometryModelLegacy().getTextureHeight();

        List<Float> visBoundsOffset = pojo.getGeometryModelLegacy().getVisibleBoundsOffset();
        float offsetX = visBoundsOffset.get(0);
        float offsetY = visBoundsOffset.get(1);
        float offsetZ = visBoundsOffset.get(2);
        this.offset = new Vector3f(offsetX, offsetY, offsetZ);
        float width = pojo.getGeometryModelLegacy().getVisibleBoundsWidth() / 2.0f;
        float height = pojo.getGeometryModelLegacy().getVisibleBoundsHeight() / 2.0f;
        this.size = new Vector2f(width, height);

        for (BonesItem bones : pojo.getGeometryModelLegacy().getBones()) {
            indexBones.putIfAbsent(bones.getName(), bones);
            modelMap.putIfAbsent(bones.getName(), new ModelRendererWrapper(new BedrockPart(bones.getName())));
        }

        for (BonesItem bones : pojo.getGeometryModelLegacy().getBones()) {
            String name = bones.getName();
            @Nullable List<Float> rotation = bones.getRotation();
            @Nullable String parent = bones.getParent();
            BedrockPart model = modelMap.get(name).getModelRenderer();

            model.mirror = bones.isMirror();
            model.setPos(convertPivot(bones, 0), convertPivot(bones, 1), convertPivot(bones, 2));

            if (rotation != null) {
                setRotationAngle(model, convertRotation(rotation.get(0)), convertRotation(rotation.get(1)), convertRotation(rotation.get(2)));
            }

            if (parent != null) {
                modelMap.get(parent).getModelRenderer().addChild(model);
            } else {
                shouldRender.add(model);
            }

            if (bones.getCubes() == null) {
                continue;
            }

            for (CubesItem cube : bones.getCubes()) {
                List<Float> uv = cube.getUv();
                List<Float> cubeSize = cube.getSize();
                boolean mirror = cube.isMirror();
                float inflate = cube.getInflate();

                model.cubes.add(new BedrockCubeBox(uv.get(0), uv.get(1),
                        convertOrigin(bones, cube, 0), convertOrigin(bones, cube, 1), convertOrigin(bones, cube, 2),
                        cubeSize.get(0), cubeSize.get(1), cubeSize.get(2), inflate, mirror,
                        texWidth, texHeight));
            }
        }
    }

    protected float convertPivot(BonesItem bones, int index) {
        if (bones.getParent() != null) {
            if (index == 1) {
                return indexBones.get(bones.getParent()).getPivot().get(index) - bones.getPivot().get(index);
            } else {
                return bones.getPivot().get(index) - indexBones.get(bones.getParent()).getPivot().get(index);
            }
        } else {
            if (index == 1) {
                return 24 - bones.getPivot().get(index);
            } else {
                return bones.getPivot().get(index);
            }
        }
    }

    protected float convertPivot(BonesItem parent, CubesItem cube, int index) {
        assert cube.getPivot() != null;
        if (index == 1) {
            return parent.getPivot().get(index) - cube.getPivot().get(index);
        } else {
            return cube.getPivot().get(index) - parent.getPivot().get(index);
        }
    }

    protected float convertOrigin(BonesItem bone, CubesItem cube, int index) {
        if (index == 1) {
            return bone.getPivot().get(index) - cube.getOrigin().get(index) - cube.getSize().get(index);
        } else {
            return cube.getOrigin().get(index) - bone.getPivot().get(index);
        }
    }

    protected float convertOrigin(CubesItem cube, int index) {
        assert cube.getPivot() != null;
        if (index == 1) {
            return cube.getPivot().get(index) - cube.getOrigin().get(index) - cube.getSize().get(index);
        } else {
            return cube.getOrigin().get(index) - cube.getPivot().get(index);
        }
    }

    protected float convertRotation(float degree) {
        return (float) (degree * Math.PI / 180);
    }

    @Nullable
    public BedrockPart getNode(String nodeName) {
        ModelRendererWrapper rendererWrapper = modelMap.get(nodeName);
        return rendererWrapper != null ? rendererWrapper.getModelRenderer() : null;
    }

    @Nullable
    public BonesItem getBone(String name) {
        return indexBones.get(name);
    }

    /**
     * Register a renderer to be invoked after the main model render pass.
     */
    public void delegateRender(IFunctionalRenderer renderer) {
        delegateRenderers.add(renderer);
    }

    /**
     * Render all root bones using 1.12.2 GL immediate mode.
     * Caller must bind texture and set up GL state before calling this.
     */
    public void render() {
        float prevBX = OpenGlHelper.lastBrightnessX;
        float prevBY = OpenGlHelper.lastBrightnessY;

        GlStateManager.pushMatrix();
        for (BedrockPart model : shouldRender) {
            model.render();
        }
        GlStateManager.popMatrix();

        // Restore lightmap in case any illuminated bones changed it
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, prevBX, prevBY);

        // Execute delegate renderers (e.g. text show) after main pass
        if (!delegateRenderers.isEmpty()) {
            int packedLight = (int) prevBX | ((int) prevBY << 16);
            for (IFunctionalRenderer delegate : delegateRenderers) {
                delegate.render(packedLight);
            }
            delegateRenderers = new ArrayList<>();
        }
    }

    @Nullable
    public Vector3f getOffset() {
        return offset;
    }

    @Nullable
    public Vector2f getSize() {
        return size;
    }

    public List<BedrockPart> getShouldRender() {
        return shouldRender;
    }

    public HashMap<String, BonesItem> getIndexBones() {
        return indexBones;
    }

    public HashMap<String, ModelRendererWrapper> getModelMap() {
        return modelMap;
    }

    /**
     * Get the path from root to the given node (inclusive).
     * Returns null if the wrapper is null.
     */
    @Nullable
    protected List<BedrockPart> getPath(@Nullable ModelRendererWrapper rendererWrapper) {
        if (rendererWrapper == null) {
            return null;
        }
        BedrockPart part = rendererWrapper.getModelRenderer();
        List<BedrockPart> path = new ArrayList<>();
        Stack<BedrockPart> stack = new Stack<>();
        do {
            stack.push(part);
            part = part.getParent();
        } while (part != null);
        while (!stack.isEmpty()) {
            part = stack.pop();
            path.add(part);
        }
        return path;
    }
}
