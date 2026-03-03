package com.tacz.legacy.client.input

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

public class WeaponAimInputStateRegistryTest {

    @After
    public fun tearDown() {
        WeaponAimInputStateRegistry.clearAll()
    }

    @Test
    public fun `registry should resolve fresh client input state`() {
        WeaponAimInputStateRegistry.updateFromClientInput(
            sessionId = "player:test",
            isAiming = true,
            nowMillis = 1000L
        )

        val resolved = WeaponAimInputStateRegistry.resolve(
            sessionId = "player:test",
            nowMillis = 1200L,
            staleAfterMillis = 300L
        )
        val snapshot = WeaponAimInputStateRegistry.snapshot("player:test")

        assertTrue(resolved == true)
        assertEquals(WeaponAimStateSource.CLIENT_INPUT, snapshot?.source)
        assertTrue(snapshot?.isAiming == true)
    }

    @Test
    public fun `registry should return null when state is stale`() {
        WeaponAimInputStateRegistry.updateFromClientInput(
            sessionId = "player:test",
            isAiming = true,
            nowMillis = 1000L
        )

        val resolved = WeaponAimInputStateRegistry.resolve(
            sessionId = "player:test",
            nowMillis = 1401L,
            staleAfterMillis = 300L
        )

        assertNull(resolved)
    }

    @Test
    public fun `registry should allow external sync updates and clear session`() {
        WeaponAimInputStateRegistry.updateFromExternalSync(
            sessionId = "player:test",
            isAiming = false,
            nowMillis = 1000L
        )

        val resolvedBeforeClear = WeaponAimInputStateRegistry.resolve(
            sessionId = "player:test",
            nowMillis = 1100L,
            staleAfterMillis = 300L
        )
        val snapshot = WeaponAimInputStateRegistry.snapshot("player:test")

        assertFalse(resolvedBeforeClear == true)
        assertEquals(WeaponAimStateSource.EXTERNAL_SYNC, snapshot?.source)

        WeaponAimInputStateRegistry.clearSession("player:test")
        val resolvedAfterClear = WeaponAimInputStateRegistry.resolve(
            sessionId = "player:test",
            nowMillis = 1100L,
            staleAfterMillis = 300L
        )

        assertNull(resolvedAfterClear)
    }
}
