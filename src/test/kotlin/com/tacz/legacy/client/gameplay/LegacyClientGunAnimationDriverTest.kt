package com.tacz.legacy.client.gameplay

import com.google.gson.GsonBuilder
import com.tacz.legacy.api.client.animation.AnimationListener
import com.tacz.legacy.api.client.animation.AnimationListenerSupplier
import com.tacz.legacy.api.client.animation.AnimationController
import com.tacz.legacy.api.client.animation.Animations
import com.tacz.legacy.api.client.animation.ObjectAnimation
import com.tacz.legacy.api.client.animation.ObjectAnimationChannel
import com.tacz.legacy.api.client.animation.statemachine.AnimationState
import com.tacz.legacy.api.client.animation.statemachine.AnimationStateMachine
import com.tacz.legacy.client.animation.statemachine.GunAnimationConstant
import com.tacz.legacy.client.animation.statemachine.GunAnimationStateContext
import com.tacz.legacy.client.resource.pojo.animation.bedrock.AnimationKeyframes
import com.tacz.legacy.client.resource.pojo.animation.bedrock.BedrockAnimationFile
import com.tacz.legacy.client.resource.pojo.animation.bedrock.SoundEffectKeyframes
import com.tacz.legacy.client.resource.serialize.AnimationKeyframesSerializer
import com.tacz.legacy.client.resource.serialize.SoundEffectKeyframesSerializer
import net.minecraft.init.Bootstrap
import net.minecraft.item.ItemStack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyClientGunAnimationDriverTest {
    private val animationGson = GsonBuilder()
        .registerTypeAdapter(AnimationKeyframes::class.java, AnimationKeyframesSerializer())
        .registerTypeAdapter(SoundEffectKeyframes::class.java, SoundEffectKeyframesSerializer())
        .create()

    @Test
    fun `determineLoopInput matches upstream idle walk run semantics`() {
        assertEquals(
            GunAnimationConstant.INPUT_IDLE,
            LegacyClientGunAnimationDriver.determineLoopInput(
                isSprinting = false,
                isSneaking = false,
                moveForward = 0f,
                moveStrafe = 0f,
            ),
        )
        assertEquals(
            GunAnimationConstant.INPUT_WALK,
            LegacyClientGunAnimationDriver.determineLoopInput(
                isSprinting = false,
                isSneaking = false,
                moveForward = 0.2f,
                moveStrafe = 0f,
            ),
        )
        assertEquals(
            GunAnimationConstant.INPUT_RUN,
            LegacyClientGunAnimationDriver.determineLoopInput(
                isSprinting = true,
                isSneaking = false,
                moveForward = 0.2f,
                moveStrafe = 0f,
            ),
        )
        assertEquals(
            GunAnimationConstant.INPUT_IDLE,
            LegacyClientGunAnimationDriver.determineLoopInput(
                isSprinting = true,
                isSneaking = true,
                moveForward = 0.2f,
                moveStrafe = 0f,
            ),
        )
    }

    @Test
    fun `resolveMeleeAnimationInput matches upstream muzzle stock push priority`() {
        assertEquals(
            GunAnimationConstant.INPUT_BAYONET_MUZZLE,
            LegacyClientGunAnimationDriver.resolveMeleeAnimationInput(
                hasMuzzleMelee = true,
                hasStockMelee = true,
                defaultAnimationType = "melee_stock",
            ),
        )
        assertEquals(
            GunAnimationConstant.INPUT_BAYONET_STOCK,
            LegacyClientGunAnimationDriver.resolveMeleeAnimationInput(
                hasMuzzleMelee = false,
                hasStockMelee = true,
                defaultAnimationType = "melee_push",
            ),
        )
        assertEquals(
            GunAnimationConstant.INPUT_BAYONET_STOCK,
            LegacyClientGunAnimationDriver.resolveMeleeAnimationInput(
                hasMuzzleMelee = false,
                hasStockMelee = false,
                defaultAnimationType = "melee_stock",
            ),
        )
        assertEquals(
            GunAnimationConstant.INPUT_BAYONET_PUSH,
            LegacyClientGunAnimationDriver.resolveMeleeAnimationInput(
                hasMuzzleMelee = false,
                hasStockMelee = false,
                defaultAnimationType = "melee_push",
            ),
        )
        assertNull(
            LegacyClientGunAnimationDriver.resolveMeleeAnimationInput(
                hasMuzzleMelee = false,
                hasStockMelee = false,
                defaultAnimationType = null,
            ),
        )
    }

    @Test
    fun `trigger starts animation runner through state transition`() {
        Bootstrap.register()
        val controller = createAnimationController(AnimationListenerSupplier { _, _ -> null })
        lateinit var stateMachine: AnimationStateMachine<GunAnimationStateContext>
        var transitionTriggered = false
        val loopingState = object : AnimationState<GunAnimationStateContext> {
            override fun update(context: GunAnimationStateContext) = Unit

            override fun entryAction(context: GunAnimationStateContext) = Unit

            override fun exitAction(context: GunAnimationStateContext) = Unit

            override fun transition(context: GunAnimationStateContext, condition: String): AnimationState<GunAnimationStateContext>? {
                if (condition == GunAnimationConstant.INPUT_WALK) {
                    transitionTriggered = true
                    stateMachine.animationController.runAnimation(0, "walk", ObjectAnimation.PlayType.PLAY_ONCE_HOLD, 0f)
                    return this
                }
                return null
            }
        }
        stateMachine = AnimationStateMachine(controller)
        stateMachine.setStatesSupplier { listOf(loopingState) }
        LegacyClientGunAnimationDriver.prepareContext(stateMachine, ItemStack.EMPTY, null, 0f)
        stateMachine.initialize()

        assertNull(controller.getAnimation(0))

        assertTrue(LegacyClientGunAnimationDriver.trigger(stateMachine, GunAnimationConstant.INPUT_WALK, ItemStack.EMPTY, null))
        assertTrue("Expected walk trigger to reach state transition", transitionTriggered)
        assertNotNull("Expected walk trigger to start animation runner", controller.getAnimation(0))
    }

    @Test
    fun `controller animation emits translation listener updates`() {
        val translationProbe = TranslationProbe()
        val controller = createAnimationController(
            AnimationListenerSupplier { nodeName, type ->
                if (nodeName == "root" && type == ObjectAnimationChannel.ChannelType.TRANSLATION) {
                    translationProbe
                } else {
                    null
                }
            },
        )

        assertEquals(0f, translationProbe.lastValues[0], 1.0e-6f)

        controller.runAnimation(0, "walk", ObjectAnimation.PlayType.PLAY_ONCE_HOLD, 0f)
        Thread.sleep(30)
        controller.update()

        assertTrue(
            "Expected translation listener to receive non-zero motion after walk trigger",
            translationProbe.lastValues[0] > 0.01f,
        )
    }

    private fun createAnimationController(listenerSupplier: AnimationListenerSupplier): AnimationController {
        val json = """
        {
          "format_version": "1.8.0",
          "animations": {
            "walk": {
              "animation_length": 0.02,
              "bones": {
                "root": {
                  "position": {
                    "0.0": {"post": [0, 0, 0]},
                    "0.02": {"post": [16, 0, 0]}
                  }
                }
              }
            }
          }
        }
        """.trimIndent()
        val animationFile = animationGson.fromJson(json, BedrockAnimationFile::class.java)
        return Animations.createControllerFromBedrock(animationFile, listenerSupplier)
    }

    private class TranslationProbe : AnimationListener {
        val lastValues: FloatArray = floatArrayOf(0f, 0f, 0f)

        override fun update(values: FloatArray, blend: Boolean) {
            if (blend) {
                lastValues[0] += values[0]
                lastValues[1] += values[1]
                lastValues[2] += values[2]
            } else {
                lastValues[0] = values[0]
                lastValues[1] = values[1]
                lastValues[2] = values[2]
            }
        }

        override fun initialValue(): FloatArray = floatArrayOf(0f, 0f, 0f)

        override fun getType(): ObjectAnimationChannel.ChannelType = ObjectAnimationChannel.ChannelType.TRANSLATION
    }
}
