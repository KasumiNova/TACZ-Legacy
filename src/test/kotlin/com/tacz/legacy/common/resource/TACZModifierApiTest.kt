package com.tacz.legacy.common.resource

import com.google.gson.JsonParser
import com.google.gson.stream.JsonReader
import com.tacz.legacy.api.modifier.JsonProperty
import com.tacz.legacy.api.modifier.Modifier
import com.tacz.legacy.api.modifier.ParameterizedCache
import java.io.StringReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TACZModifierApiTest {
    @Suppress("UNCHECKED_CAST")
    @Test
    fun `legacy modifier aliases are parsed and evaluated`() {
        val rawJson = """
            {
              "ads_addend": -0.05,
              "weight": 0.4,
              "recoil_modifier": {
                "pitch": 0.1,
                "yaw": -0.2
              },
              "inaccuracy_addend": 0.5,
              "ignite": {
                "entity": true,
                "block": false
              }
            }
        """.trimIndent()
        val reader = JsonReader(StringReader(rawJson)).apply { isLenient = true }
        val jsonObject = JsonParser().parse(reader).asJsonObject

        val modifiers = TACZAttachmentModifierRegistry.readModifiers(rawJson, jsonObject)

        val ads = (modifiers["ads"] as JsonProperty<Modifier>).getValue()
        assertNotNull(ads)
        assertEquals(-0.05, ads!!.addend, 0.0001)
        assertEquals(0.15, TACZAttachmentModifierRegistry.evalNumeric(listOf(ads), 0.2), 0.0001)

        val recoil = (modifiers["recoil"] as JsonProperty<TACZRecoilModifierValue>).getValue()
        assertNotNull(recoil)
        val recoilCache = TACZAttachmentModifierRegistry.evalRecoil(listOf(recoil!!), 1.0f, 1.0f)
        assertEquals(1.1, recoilCache.left().eval(1.0), 0.0001)
        assertEquals(0.8, recoilCache.right().eval(1.0), 0.0001)

        val inaccuracy = (modifiers["inaccuracy"] as JsonProperty<Map<String, Modifier>>).getValue()
        assertNotNull(inaccuracy)
        assertTrue(inaccuracy!!.containsKey("default"))

        val ignite = (modifiers["ignite"] as JsonProperty<TACZIgniteValue>).getValue()
        assertNotNull(ignite)
        val igniteResult = TACZAttachmentModifierRegistry.evalIgnite(listOf(ignite!!), TACZIgniteValue())
        assertTrue(igniteResult.igniteEntity)
        assertFalse(igniteResult.igniteBlock)
    }

    @Test
    fun `parameterized cache can apply lua function`() {
        val cache = ParameterizedCache.of(listOf(Modifier(function = "y = x * 2")), 1.0)
        assertEquals(4.0, cache.eval(2.0), 0.0001)
    }
}
