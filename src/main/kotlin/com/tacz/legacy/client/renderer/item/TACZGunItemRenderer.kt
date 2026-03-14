package com.tacz.legacy.client.renderer.item

import com.tacz.legacy.api.item.IGun
import com.tacz.legacy.client.model.BedrockAnimatedModel
import com.tacz.legacy.client.model.BedrockGunModel
import com.tacz.legacy.client.model.SlotModel
import com.tacz.legacy.client.model.TACZPerspectiveAwareBakedModel
import com.tacz.legacy.client.model.bedrock.BedrockModel
import com.tacz.legacy.client.model.functional.BeamRenderer
import com.tacz.legacy.client.renderer.bloom.TACZBloomBridge
import com.tacz.legacy.client.resource.TACZClientAssetManager
import com.tacz.legacy.client.resource.GunDisplayInstance
import com.tacz.legacy.client.resource.pojo.display.gun.GunDisplay
import com.tacz.legacy.client.resource.pojo.display.gun.GunTransform
import com.tacz.legacy.common.resource.TACZGunPackPresentation
import com.tacz.legacy.common.resource.TACZGunPackRuntimeRegistry
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.block.model.ItemCameraTransforms
import net.minecraft.client.renderer.tileentity.TileEntityItemStackRenderer
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly

/**
 * TEISR (TileEntityItemStackRenderer) for gun items.
 *
 * Context-aware rendering via [TACZPerspectiveAwareBakedModel]:
 * - **Item presentation contexts** (GUI, dropped, fixed, head) → flat slot texture
 * - **First person** → skipped (handled by FirstPersonRenderGunEvent)
 * - **Third person right hand** → 3D bedrock model with gun pack texture
 * - **Third person left hand** → skipped (upstream convention)
 */
@SideOnly(Side.CLIENT)
internal object TACZGunItemRenderer : TileEntityItemStackRenderer() {
    private val SLOT_MODEL = SlotModel()

    override fun renderByItem(stack: ItemStack) {
        renderByItem(stack, 1.0f)
    }

    override fun renderByItem(stack: ItemStack, partialTicks: Float) {
        val item = stack.item
        if (item !is IGun) return

        val snapshot = TACZGunPackRuntimeRegistry.getSnapshot()
        val gunId = item.getGunId(stack)

        val displayId = TACZGunPackPresentation.resolveGunDisplayId(snapshot, gunId) ?: return
        val display: GunDisplay = TACZClientAssetManager.getGunDisplay(displayId) ?: return
        val displayInstance = TACZClientAssetManager.getGunDisplayInstance(displayId)

        val transformType = TACZPerspectiveAwareBakedModel.getCurrentTransformType()

        // First person is usually handled by FirstPersonRenderGunEvent.
        // If that hook bails out early (missing display instance, texture, etc.),
        // allow the TEISR to act as a last-resort visual fallback instead of
        // making the gun disappear entirely.

        // Third person left hand — skip per upstream convention
        if (transformType == ItemCameraTransforms.TransformType.THIRD_PERSON_LEFT_HAND) {
            return
        }

        // Item presentation contexts → flat slot texture
        if (TACZPerspectiveAwareBakedModel.isItemPresentationContext(transformType)) {
            renderSlotTexture(display)
            return
        }

        // Third person right hand → 3D model
        renderGunModel(stack, display, displayInstance)
    }

    private fun renderSlotTexture(display: GunDisplay) {
        val slotTexLoc = display.slotTextureLocation ?: return
        val registeredSlot = TACZClientAssetManager.getTextureLocation(slotTexLoc) ?: return
        Minecraft.getMinecraft().textureManager.bindTexture(registeredSlot)

        GlStateManager.pushMatrix()
        GlStateManager.translate(0.5f, 1.5f, 0.5f)
        GlStateManager.rotate(180f, 0f, 0f, 1f)

        GlStateManager.enableLighting()
        GlStateManager.enableRescaleNormal()
        GlStateManager.enableBlend()
        GlStateManager.tryBlendFuncSeparate(
            GlStateManager.SourceFactor.SRC_ALPHA,
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
            GlStateManager.SourceFactor.ONE,
            GlStateManager.DestFactor.ZERO,
        )

        SLOT_MODEL.render()

        GlStateManager.disableBlend()
        GlStateManager.disableRescaleNormal()
        GlStateManager.popMatrix()
    }

    private fun renderGunModel(stack: ItemStack, display: GunDisplay, displayInstance: GunDisplayInstance?) {
        val textureLocation: ResourceLocation = displayInstance?.modelTexture ?: display.modelTexture ?: return
        val registeredTexture: ResourceLocation = TACZClientAssetManager.getTextureLocation(textureLocation) ?: return
        val transformType = TACZPerspectiveAwareBakedModel.getCurrentTransformType()
        val beamRenderContext = when (transformType) {
            ItemCameraTransforms.TransformType.THIRD_PERSON_RIGHT_HAND -> BeamRenderer.RenderContext.THIRD_PERSON_RIGHT_HAND
            ItemCameraTransforms.TransformType.FIRST_PERSON_LEFT_HAND,
            ItemCameraTransforms.TransformType.FIRST_PERSON_RIGHT_HAND -> BeamRenderer.RenderContext.FIRST_PERSON
            else -> BeamRenderer.RenderContext.NONE
        }

        val runtimeModel = displayInstance?.gunModel
        val fallbackModel = if (runtimeModel == null) {
            val modelLocation: ResourceLocation = display.modelLocation ?: return
            val modelData = TACZClientAssetManager.getModel(modelLocation) ?: return
            BedrockModel(modelData.pojo, modelData.version)
        } else {
            null
        }

        Minecraft.getMinecraft().textureManager.bindTexture(registeredTexture)

        GlStateManager.pushMatrix()
        GlStateManager.translate(0.5f, 2.0f, 0.5f)
        GlStateManager.scale(-1f, -1f, 1f)

        GlStateManager.enableLighting()
        GlStateManager.enableRescaleNormal()
        GlStateManager.enableBlend()
        GlStateManager.tryBlendFuncSeparate(
            GlStateManager.SourceFactor.SRC_ALPHA,
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
            GlStateManager.SourceFactor.ONE,
            GlStateManager.DestFactor.ZERO,
        )

        displayInstance?.setActiveGunTexture(registeredTexture)
        if (runtimeModel != null) {
            runtimeModel.renderHand = false
            val previousBeamContext = BeamRenderer.pushRenderContext(beamRenderContext)
            try {
                runtimeModel.render(stack)
            } finally {
                BeamRenderer.popRenderContext(previousBeamContext)
            }
            captureBloomIfSupported(transformType, registeredTexture, runtimeModel) {
                runtimeModel.renderHand = false
                runtimeModel.renderBloom(stack)
            }
            runtimeModel.cleanAnimationTransform()
            runtimeModel.cleanCameraAnimationTransform()
        } else {
            val staticModel = fallbackModel ?: return
            staticModel.render()
            captureBloomIfSupported(transformType, registeredTexture, staticModel) {
                staticModel.renderBloom()
            }
        }

        GlStateManager.disableBlend()
        GlStateManager.disableRescaleNormal()
        GlStateManager.popMatrix()
    }

    private fun captureBloomIfSupported(
        transformType: ItemCameraTransforms.TransformType,
        texture: ResourceLocation,
        model: BedrockModel?,
        renderBloom: () -> Unit,
    ) {
        if (model == null) {
            return
        }
        if (transformType == ItemCameraTransforms.TransformType.FIRST_PERSON_LEFT_HAND ||
            transformType == ItemCameraTransforms.TransformType.FIRST_PERSON_RIGHT_HAND
        ) {
            return
        }

        when (model) {
            is BedrockAnimatedModel -> {
                val snapshot = model.captureRenderState()
                TACZBloomBridge.captureCurrentModelBloom(texture) {
                    model.restoreRenderState(snapshot)
                    renderBloom()
                    model.cleanAnimationTransform()
                    model.cleanCameraAnimationTransform()
                }
            }
            else -> {
                TACZBloomBridge.captureCurrentModelBloom(texture, renderBloom)
            }
        }
    }
}
