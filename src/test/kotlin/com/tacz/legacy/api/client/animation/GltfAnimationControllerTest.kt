package com.tacz.legacy.api.client.animation

import com.tacz.legacy.api.client.animation.interpolator.SLerp
import com.tacz.legacy.client.resource.gltf.GltfAnimationParser
import com.tacz.legacy.util.math.MathUtil
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64

class GltfAnimationControllerTest {
    @Test
    fun `gltf translation channels are converted into legacy listener space`() {
        val times = floatArrayOf(0f, 1f)
        val translations = floatArrayOf(
            1f, 2f, 3f,
            5f, 7f, 9f,
        )
        val gltf = buildInlineGltf(
            times = times,
            values = translations,
            valueType = "VEC3",
            path = "translation",
            nodeTranslation = floatArrayOf(1f, 2f, 3f),
        )
        val parsed = GltfAnimationParser.parse(gltf, null)

        assertEquals(1, parsed.animations.size)
        assertEquals(1, parsed.animations.first().channels.size)

        val controller = Animations.createControllerFromGltf(parsed, object : AnimationListenerSupplier {
            override fun supplyListeners(nodeName: String, type: ObjectAnimationChannel.ChannelType): AnimationListener? {
                if (nodeName != "root" || type != ObjectAnimationChannel.ChannelType.TRANSLATION) {
                    return null
                }
                return object : AnimationListener {
                    override fun update(values: FloatArray, blend: Boolean) = Unit

                    override fun initialValue(): FloatArray = floatArrayOf(0.25f, -0.5f, 0.75f)

                    override fun getType(): ObjectAnimationChannel.ChannelType = ObjectAnimationChannel.ChannelType.TRANSLATION
                }
            }
        })

        val prototypesField = AnimationController::class.java.getDeclaredField("prototypes")
        prototypesField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val prototypes = prototypesField.get(controller) as Map<String, ObjectAnimation>
        val animation = prototypes["test_anim"]
        assertNotNull(animation)
        val channel = animation!!.channels["root"]!!.single()

        assertArrayEquals(floatArrayOf(-1.25f, 1.5f, 2.25f), channel.content.values[0], 1.0e-6f)
        assertArrayEquals(floatArrayOf(-5.25f, 6.5f, 8.25f), channel.content.values[1], 1.0e-6f)
    }

    @Test
    fun `gltf rotation channels use slerp and legacy quaternion remap`() {
        val times = floatArrayOf(0f, 1f)
        val rawRotationA = MathUtil.toQuaternion((Math.PI / 6).toFloat(), (Math.PI / 8).toFloat(), (-Math.PI / 10).toFloat())
        val rawRotationB = MathUtil.toQuaternion((-Math.PI / 3).toFloat(), (Math.PI / 5).toFloat(), (Math.PI / 7).toFloat())
        val rotations = floatArrayOf(*rawRotationA, *rawRotationB)
        val gltf = buildInlineGltf(times, rotations, "VEC4", "rotation")
        val parsed = GltfAnimationParser.parse(gltf, null)

        val listenerInitial = MathUtil.toQuaternion((-Math.PI / 9).toFloat(), (Math.PI / 11).toFloat(), (Math.PI / 13).toFloat())

        val controller = Animations.createControllerFromGltf(parsed, object : AnimationListenerSupplier {
            override fun supplyListeners(nodeName: String, type: ObjectAnimationChannel.ChannelType): AnimationListener? {
                if (nodeName != "root" || type != ObjectAnimationChannel.ChannelType.ROTATION) {
                    return null
                }
                return object : AnimationListener {
                    override fun update(values: FloatArray, blend: Boolean) = Unit

                    override fun initialValue(): FloatArray = listenerInitial.copyOf()

                    override fun getType(): ObjectAnimationChannel.ChannelType = ObjectAnimationChannel.ChannelType.ROTATION
                }
            }
        })

        val prototypesField = AnimationController::class.java.getDeclaredField("prototypes")
        prototypesField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val prototypes = prototypesField.get(controller) as Map<String, ObjectAnimation>
        val animation = prototypes["test_anim"]
        assertNotNull(animation)
        val channel = animation!!.channels["root"]!!.single()

        assertTrue(channel.interpolator is SLerp)

        val inverseInitial = MathUtil.inverseQuaternion(listenerInitial)
        val expectedA = expectedLegacyRotation(rawRotationA, inverseInitial)
        val expectedB = expectedLegacyRotation(rawRotationB, inverseInitial)
        assertArrayEquals(expectedA, channel.content.values[0], 1.0e-6f)
        assertArrayEquals(expectedB, channel.content.values[1], 1.0e-6f)

        val midpoint = channel.getResult(0.5f)
        assertArrayEquals(MathUtil.slerp(expectedA, expectedB, 0.5f), midpoint, 1.0e-6f)
    }

    private fun expectedLegacyRotation(rawQuaternion: FloatArray, inverseInitial: FloatArray): FloatArray {
        val rawEuler = MathUtil.toEulerAngles(rawQuaternion)
        val legacyQuaternion = MathUtil.toQuaternion(-rawEuler[0], -rawEuler[1], rawEuler[2])
        return MathUtil.mulQuaternion(inverseInitial, legacyQuaternion)
    }

    private fun buildInlineGltf(
        times: FloatArray,
        values: FloatArray,
        valueType: String,
        path: String,
        nodeTranslation: FloatArray? = null,
    ): String {
        val timeBytes = floatArrayToBytes(times)
        val valueBytes = floatArrayToBytes(values)
        val combined = ByteArray(timeBytes.size + valueBytes.size)
        System.arraycopy(timeBytes, 0, combined, 0, timeBytes.size)
        System.arraycopy(valueBytes, 0, combined, timeBytes.size, valueBytes.size)
        val base64 = Base64.getEncoder().encodeToString(combined)
        val nodeTranslationJson = nodeTranslation?.joinToString(prefix = ", \"translation\": [", postfix = "]") ?: ""
        return """
            {
              "asset": {"version": "2.0"},
              "buffers": [{"byteLength": ${combined.size}, "uri": "data:application/octet-stream;base64,$base64"}],
              "bufferViews": [
                {"buffer": 0, "byteOffset": 0, "byteLength": ${timeBytes.size}},
                {"buffer": 0, "byteOffset": ${timeBytes.size}, "byteLength": ${valueBytes.size}}
              ],
              "accessors": [
                {"bufferView": 0, "componentType": 5126, "count": ${times.size}, "type": "SCALAR"},
                {"bufferView": 1, "componentType": 5126, "count": ${times.size}, "type": "$valueType"}
              ],
                            "nodes": [{"name": "root"$nodeTranslationJson}],
              "animations": [{
                "name": "test_anim",
                "samplers": [{"input": 0, "output": 1, "interpolation": "LINEAR"}],
                "channels": [{"sampler": 0, "target": {"node": 0, "path": "$path"}}]
              }]
            }
        """.trimIndent()
    }

    private fun floatArrayToBytes(values: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(values.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        values.forEach(buffer::putFloat)
        return buffer.array()
    }
}
