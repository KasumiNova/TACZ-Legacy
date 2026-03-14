package com.tacz.legacy.client.gui

internal data class TACZGuiTextureRegion(
	val minU: Float,
	val minV: Float,
	val maxU: Float,
	val maxV: Float,
)

internal object TACZGuiTextureUv {
	fun region(
		u: Int,
		v: Int,
		regionWidth: Int,
		regionHeight: Int,
		textureWidth: Int,
		textureHeight: Int,
	): TACZGuiTextureRegion {
		require(textureWidth > 0) { "textureWidth must be positive" }
		require(textureHeight > 0) { "textureHeight must be positive" }
		return TACZGuiTextureRegion(
			minU = u.toFloat() / textureWidth.toFloat(),
			minV = v.toFloat() / textureHeight.toFloat(),
			maxU = (u + regionWidth).toFloat() / textureWidth.toFloat(),
			maxV = (v + regionHeight).toFloat() / textureHeight.toFloat(),
		)
	}
}