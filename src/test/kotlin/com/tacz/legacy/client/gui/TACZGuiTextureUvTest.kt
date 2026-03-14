package com.tacz.legacy.client.gui

import org.junit.Assert.assertEquals
import org.junit.Test

class TACZGuiTextureUvTest {
	@Test
	fun `slot icon region keeps full 32px source width when scaled down`() {
		val region = TACZGuiTextureUv.region(
			u = 96,
			v = 0,
			regionWidth = 32,
			regionHeight = 32,
			textureWidth = 224,
			textureHeight = 32,
		)

		assertEquals(96f / 224f, region.minU)
		assertEquals(128f / 224f, region.maxU)
		assertEquals(0f, region.minV)
		assertEquals(1f, region.maxV)
	}

	@Test
	fun `logical 16x16 slot space maps whole texture`() {
		val region = TACZGuiTextureUv.region(
			u = 0,
			v = 0,
			regionWidth = 16,
			regionHeight = 16,
			textureWidth = 16,
			textureHeight = 16,
		)

		assertEquals(0f, region.minU)
		assertEquals(1f, region.maxU)
		assertEquals(0f, region.minV)
		assertEquals(1f, region.maxV)
	}
}