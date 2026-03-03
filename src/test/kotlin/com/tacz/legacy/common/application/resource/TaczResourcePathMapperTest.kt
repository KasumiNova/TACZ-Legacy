package com.tacz.legacy.common.application.resource

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

public class TaczResourcePathMapperTest {

    private val mapper: TaczResourcePathMapper = TaczResourcePathMapper()

    @Test
    public fun `mapper should direct-copy standard asset path`() {
        val decision = mapper.map("textures/hud/heat_bar.png")

        assertEquals(ResourceMappingAction.DIRECT_COPY, decision.action)
        assertEquals(
            "src/main/resources/assets/tacz/textures/hud/heat_bar.png",
            decision.targetPath
        )
    }

    @Test
    public fun `mapper should convert language json to lang target`() {
        val decision = mapper.map("lang/zh_cn.json")

        assertEquals(ResourceMappingAction.CONVERT_LANG_JSON_TO_LANG, decision.action)
        assertEquals(
            "src/main/resources/assets/tacz/lang/zh_cn.lang",
            decision.targetPath
        )
    }

    @Test
    public fun `mapper should route custom pack files to run tacz pack folder`() {
        val decision = mapper.map("custom/tacz_default_gun/data/tacz/data/guns/ak47_data.json")

        assertEquals(ResourceMappingAction.COPY_TO_GUNPACK, decision.action)
        assertEquals(
            "run/tacz/tacz_default_gun/data/tacz/data/guns/ak47_data.json",
            decision.targetPath
        )
    }

    @Test
    public fun `mapper should ignore custom pack readme`() {
        val decision = mapper.map("custom/tacz_default_gun/README.txt")

        assertEquals(ResourceMappingAction.IGNORE, decision.action)
        assertEquals(null, decision.targetPath)
    }

    @Test
    public fun `mapper should mark unknown top-level path for manual review`() {
        val decision = mapper.map("foo/bar/baz.json")

        assertEquals(ResourceMappingAction.MANUAL_REVIEW, decision.action)
        assertEquals(null, decision.targetPath)
    }

    @Test
    public fun `manifest builder should summarize actions and coverage`() {
        val summary = TaczResourceMappingManifestBuilder(mapper).build(
            listOf(
                "textures/hud/heat_bar.png",
                "lang/zh_cn.json",
                "custom/tacz_default_gun/data/tacz/data/guns/ak47_data.json",
                "custom/tacz_default_gun/README.txt",
                "foo/bar/baz.json"
            )
        )

        assertEquals(5, summary.totalFiles)
        assertEquals(1, summary.directCopyCount)
        assertEquals(1, summary.langConvertCount)
        assertEquals(1, summary.gunpackCopyCount)
        assertEquals(1, summary.ignoredCount)
        assertEquals(1, summary.manualReviewCount)
        assertTrue(summary.coverageRatio >= 0.8)
        assertEquals(listOf("foo/bar/baz.json"), summary.manualReviewSamples)
    }

}
