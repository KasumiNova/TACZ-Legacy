package com.tacz.legacy.client.resource

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

public class GunPackExternalResourcePackManagerTest {

    @Test
    public fun `normalize pack id should sanitize zip file names`() {
        assertEquals(
            "cyber_armorer_v2",
            GunPackExternalResourcePackManager.normalizePackId("Cyber Armorer V2.zip")
        )
    }

    @Test
    public fun `normalize pack id should keep supported separators`() {
        assertEquals(
            "my.pack-01",
            GunPackExternalResourcePackManager.normalizePackId("My.Pack-01")
        )
    }

    @Test
    public fun `normalize pack id should return null when result is blank`() {
        assertNull(GunPackExternalResourcePackManager.normalizePackId("***"))
    }
}
