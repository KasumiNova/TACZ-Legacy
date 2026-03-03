package com.tacz.legacy.client.render.camera

import com.tacz.legacy.common.domain.gunpack.GunRecoilKeyFrameData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

public class WeaponRecoilCurvesTest {

    @Test
    public fun `build curve should include baseline and interpolate`() {
        val curve = WeaponRecoilCurves.buildCurve(
            keyframes = listOf(
                GunRecoilKeyFrameData(timeSeconds = 0f, valueMin = 1f, valueMax = 1f),
                GunRecoilKeyFrameData(timeSeconds = 0.1f, valueMin = 0f, valueMax = 0f)
            ),
            modifier = 1f,
            random = Random(0)
        )

        assertNotNull(curve)
        assertEquals(0f, curve?.sample(0f) ?: -1f, 1e-5f)
        assertEquals(1f, curve?.sample(30f) ?: -1f, 1e-5f)
        val mid = curve?.sample(80f) ?: -1f
        assertTrue(mid in 0f..1f)
        assertNull(curve?.sample(1000f))
    }

    @Test
    public fun `build curve should apply modifier and normalize min max`() {
        val curve = WeaponRecoilCurves.buildCurve(
            keyframes = listOf(
                GunRecoilKeyFrameData(timeSeconds = 0f, valueMin = 0.8f, valueMax = 0.2f)
            ),
            modifier = 0.5f,
            random = Random(0)
        )

        assertNotNull(curve)
        val sampled = curve?.sample(30f) ?: -1f
        assertTrue(sampled in 0.1f..0.4f)
    }

    @Test
    public fun `recoil modifier should respond to ads zoom and crawl`() {
        val hip = WeaponRecoilCurves.resolveRecoilModifier(
            aimingProgress = 0f,
            aimingZoom = 4f,
            crawlRecoilMultiplier = 0.5f,
            isCrawlingLike = false
        )
        val ads = WeaponRecoilCurves.resolveRecoilModifier(
            aimingProgress = 1f,
            aimingZoom = 4f,
            crawlRecoilMultiplier = 0.5f,
            isCrawlingLike = false
        )
        val crawlAds = WeaponRecoilCurves.resolveRecoilModifier(
            aimingProgress = 1f,
            aimingZoom = 4f,
            crawlRecoilMultiplier = 0.5f,
            isCrawlingLike = true
        )

        assertEquals(1f, hip, 1e-5f)
        assertEquals(0.5f, ads, 1e-5f)
        assertEquals(0.25f, crawlAds, 1e-5f)
    }
}
