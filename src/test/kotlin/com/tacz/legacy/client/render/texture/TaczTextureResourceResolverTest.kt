package com.tacz.legacy.client.render.texture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

public class TaczTextureResourceResolverTest {

    @Test
    public fun `candidate resources should include direct and embedded custom paths`() {
        val candidates = TaczTextureResourceResolver.candidateResources(
            rawPath = "assets/tacz/textures/gun/uv/ak47.png"
        )

        assertEquals(2, candidates.size)
        assertEquals("tacz:textures/gun/uv/ak47.png", candidates[0].toString())
        assertEquals(
            "tacz:custom/tacz_default_gun/assets/tacz/textures/gun/uv/ak47.png",
            candidates[1].toString()
        )
    }

    @Test
    public fun `candidate resources should include source pack derived custom path before default fallback`() {
        val candidates = TaczTextureResourceResolver.candidateResources(
            rawPath = "assets/tacz/textures/gun/hud/ak47.png",
            sourceId = "my_pack/assets/tacz/display/guns/ak47_display.json"
        )

        assertEquals(3, candidates.size)
        assertEquals("tacz:textures/gun/hud/ak47.png", candidates[0].toString())
        assertEquals(
            "tacz:custom/my_pack/assets/tacz/textures/gun/hud/ak47.png",
            candidates[1].toString()
        )
        assertEquals(
            "tacz:custom/tacz_default_gun/assets/tacz/textures/gun/hud/ak47.png",
            candidates[2].toString()
        )
    }

    @Test
    public fun `candidate resources should support zip source ids`() {
        val candidates = TaczTextureResourceResolver.candidateResources(
            rawPath = "assets/tacz/textures/gun/hud/ak47.png",
            sourceId = "sample_display_pack.zip!/assets/tacz/display/guns/ak47_display.json"
        )

        assertEquals("tacz:textures/gun/hud/ak47.png", candidates[0].toString())
        assertEquals(
            "tacz:custom/sample_display_pack.zip/assets/tacz/textures/gun/hud/ak47.png",
            candidates[1].toString()
        )
        assertEquals(
            "tacz:custom/sample_display_pack/assets/tacz/textures/gun/hud/ak47.png",
            candidates[2].toString()
        )
    }

    @Test
    public fun `candidate resources should return empty for invalid raw path`() {
        assertTrue(TaczTextureResourceResolver.candidateResources(null).isEmpty())
        assertTrue(TaczTextureResourceResolver.candidateResources(" ").isEmpty())
        assertTrue(TaczTextureResourceResolver.candidateResources("invalid_path_without_namespace").isEmpty())
    }

    @Test
    public fun `candidate resources should normalize zip pack id with spaces and symbols`() {
        val candidates = TaczTextureResourceResolver.candidateResources(
            rawPath = "assets/tacz/textures/gun/hud/ak47.png",
            sourceId = "Applied Armorer-v1.1.4.1-for114+.zip!/assets/tacz/display/guns/ak47_display.json"
        )

        val rendered = candidates.map { it.toString() }
        assertTrue(rendered.contains("tacz:textures/gun/hud/ak47.png"))
        assertTrue(
            rendered.contains(
                "tacz:custom/applied_armorer-v1.1.4.1-for114_.zip/assets/tacz/textures/gun/hud/ak47.png"
            )
        )
        assertTrue(
            rendered.contains(
                "tacz:custom/applied_armorer-v1.1.4.1-for114/assets/tacz/textures/gun/hud/ak47.png"
            )
        )
        assertTrue(
            rendered.contains(
                "tacz:custom/tacz_default_gun/assets/tacz/textures/gun/hud/ak47.png"
            )
        )
        assertFalse(rendered.any { it.contains("Applied Armorer") })
    }

}
