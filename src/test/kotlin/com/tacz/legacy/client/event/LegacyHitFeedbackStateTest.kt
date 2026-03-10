package com.tacz.legacy.client.event

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyHitFeedbackStateTest {
    @After
    fun tearDown() {
        LegacyHitFeedbackState.resetForTests()
    }

    @Test
    fun `kill amount accumulates within timeout and resets after timeout`() {
        val first = LegacyHitFeedbackState.markKill(killAmountTimeoutMs = 3_000L, now = 1_000L)
        val second = LegacyHitFeedbackState.markKill(killAmountTimeoutMs = 3_000L, now = 2_000L)
        val reset = LegacyHitFeedbackState.markKill(killAmountTimeoutMs = 3_000L, now = 5_500L)

        assertEquals(1, first)
        assertEquals(2, second)
        assertEquals(1, reset)
    }

    @Test
    fun `hit marker snapshot prefers kill timestamp and headshot tint`() {
        LegacyHitFeedbackState.markHit(now = 1_000L)
        LegacyHitFeedbackState.markKill(killAmountTimeoutMs = 3_000L, now = 1_050L)
        LegacyHitFeedbackState.markHeadShot(now = 1_060L)

        val snapshot = LegacyHitFeedbackState.currentHitMarkerSnapshot(now = 1_120L, startPosition = 4.0f)

        assertNotNull(snapshot)
        assertTrue(snapshot!!.headShotTint)
        assertTrue(snapshot.offset > 4.0f)
        assertTrue(snapshot.alpha in 0.0f..1.0f)
    }

    @Test
    fun `kill amount snapshot fades out after two thirds of timeout`() {
        LegacyHitFeedbackState.markKill(killAmountTimeoutMs = 3_000L, now = 1_000L)

        val fresh = LegacyHitFeedbackState.currentKillAmountSnapshot(now = 1_500L, killAmountTimeoutMs = 3_000L)
        val fading = LegacyHitFeedbackState.currentKillAmountSnapshot(now = 3_500L, killAmountTimeoutMs = 3_000L)
        val expired = LegacyHitFeedbackState.currentKillAmountSnapshot(now = 4_100L, killAmountTimeoutMs = 3_000L)

        assertNotNull(fresh)
        assertNotNull(fading)
        assertFalse(fresh!!.color == fading!!.color)
        assertEquals(null, expired)
    }
}