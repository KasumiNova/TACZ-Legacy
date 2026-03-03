package com.tacz.legacy.client.render.item

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

public class FirstPersonGunRenderEventHandlerTest {

    private val handler: FirstPersonGunRenderEventHandler = FirstPersonGunRenderEventHandler()

    @Test
    public fun `custom first person render should require in game first person player camera`() {
        assertTrue(
            handler.shouldUseCustomMainHandRendering(
                hasScreen = false,
                thirdPersonView = 0,
                isRenderViewEntityPlayer = true
            )
        )

        assertTrue(
            handler.shouldUseCustomMainHandRendering(
                hasScreen = true,
                thirdPersonView = 0,
                isRenderViewEntityPlayer = true
            )
        )

        assertFalse(
            handler.shouldUseCustomMainHandRendering(
                hasScreen = false,
                thirdPersonView = 1,
                isRenderViewEntityPlayer = true
            )
        )

        assertFalse(
            handler.shouldUseCustomMainHandRendering(
                hasScreen = false,
                thirdPersonView = 0,
                isRenderViewEntityPlayer = false
            )
        )
    }

    @Test
    public fun `offhand should only hide when custom main hand rendering is active`() {
        assertTrue(
            handler.shouldHideOffHand(
                mainHandHasLegacyGun = true,
                customMainHandRenderEnabled = true
            )
        )

        assertFalse(
            handler.shouldHideOffHand(
                mainHandHasLegacyGun = true,
                customMainHandRenderEnabled = false
            )
        )

        assertFalse(
            handler.shouldHideOffHand(
                mainHandHasLegacyGun = false,
                customMainHandRenderEnabled = true
            )
        )
    }
}
