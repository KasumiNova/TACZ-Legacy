package com.tacz.legacy.common.infrastructure.mc.gunpack

import com.tacz.legacy.common.application.gunpack.GunDisplayRuntimeRegistry
import org.apache.logging.log4j.LogManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

public class GunDisplayPreInitScannerTest {

    private val scanner: GunDisplayPreInitScanner = GunDisplayPreInitScanner()

    @Test
    public fun `scan should return null when tacz root is missing`() {
        val root = Files.createTempDirectory("tacz-legacy-display-empty-")
        try {
            val report = scanner.scanAndLog(root, LogManager.getLogger("GunDisplayScanEmptyTest"))
            assertNull(report)
        } finally {
            deleteRecursively(root)
        }
    }

    @Test
    public fun `scan should parse directory pack index and display data`() {
        val gameRoot = Files.createTempDirectory("tacz-legacy-display-dir-pack-")
        try {
            val configRoot = gameRoot.resolve("config")
            Files.createDirectories(configRoot)

            val packRoot = gameRoot.resolve("tacz").resolve("sample_default_pack")
            stageDirectoryPack(packRoot)

            val report = scanner.scanAndLog(configRoot, LogManager.getLogger("GunDisplayScanDirPackTest"))

            assertNotNull(report)
            assertEquals(1, report?.total)
            assertEquals(1, report?.successCount)
            assertEquals(0, report?.failedCount)

            val snapshot = GunDisplayRuntimeRegistry().replace(report)
            assertEquals(1, snapshot.loadedCount)

            val definition = snapshot.findDefinition("ak47")
            assertNotNull(definition)
            assertEquals("assets/tacz/geo_models/gun/ak47_geo.json", definition?.modelPath)
            assertEquals("assets/tacz/textures/gun/uv/ak47.png", definition?.modelTexturePath)
            assertEquals("assets/tacz/geo_models/gun/lod/ak47.json", definition?.lodModelPath)
            assertEquals("assets/tacz/textures/gun/lod/ak47.png", definition?.lodTexturePath)
            assertEquals("assets/tacz/animations/ak47.animation.json", definition?.animationPath)
            assertEquals("assets/tacz/scripts/ak47_state_machine.lua", definition?.stateMachinePath)
            assertEquals(0.4f, definition?.stateMachineParams?.get("intro_shell_ejecting_time"))
            assertEquals(0.17f, definition?.stateMachineParams?.get("bolt_shell_ejecting_time"))
            assertEquals("assets/tacz/player_animator/rifle_default.player_animation.json", definition?.playerAnimator3rdPath)
            assertEquals("m16", definition?.thirdPersonAnimation)
            assertEquals(true, definition?.modelParseSucceeded)
            assertEquals(1, definition?.modelGeometryCount)
            assertEquals(1, definition?.modelRootBoneCount)
            assertEquals(listOf("body"), definition?.modelRootBoneNames)
            assertEquals(1, definition?.modelBoneCount)
            assertEquals(2, definition?.modelCubeCount)
            assertEquals(true, definition?.animationParseSucceeded)
            assertEquals(11, definition?.animationClipCount)
            assertEquals("animation.ak47.idle", definition?.animationIdleClipName)
            assertEquals("animation.ak47.shoot", definition?.animationFireClipName)
            assertEquals("animation.ak47.reload_tactical", definition?.animationReloadClipName)
            assertEquals("animation.ak47.inspect", definition?.animationInspectClipName)
            assertEquals("animation.ak47.dry_fire", definition?.animationDryFireClipName)
            assertEquals("animation.ak47.draw", definition?.animationDrawClipName)
            assertEquals("animation.ak47.put_away", definition?.animationPutAwayClipName)
            assertEquals("animation.ak47.walk", definition?.animationWalkClipName)
            assertEquals("animation.ak47.run", definition?.animationRunClipName)
            assertEquals("animation.ak47.ads", definition?.animationAimClipName)
            assertEquals("animation.ak47.bolt", definition?.animationBoltClipName)
            assertEquals(6_000L, definition?.animationClipLengthsMillis?.get("animation.ak47.idle"))
            assertEquals(120L, definition?.animationClipLengthsMillis?.get("animation.ak47.shoot"))
            assertEquals(2_500L, definition?.animationClipLengthsMillis?.get("animation.ak47.reload_tactical"))
            assertEquals(1_200L, definition?.animationClipLengthsMillis?.get("animation.ak47.inspect"))
            assertEquals(180L, definition?.animationClipLengthsMillis?.get("animation.ak47.dry_fire"))
            assertEquals(320L, definition?.animationClipLengthsMillis?.get("animation.ak47.draw"))
            assertEquals(true, definition?.stateMachineResolved)
            assertEquals(true, definition?.playerAnimatorResolved)
            assertEquals("assets/tacz/textures/gun/hud/ak47.png", definition?.hudTexturePath)
            assertEquals("assets/tacz/textures/gun/hud/ak47_empty.png", definition?.hudEmptyTexturePath)
            assertEquals(false, definition?.showCrosshair)
            assertEquals(1.45f, definition?.ironZoom)
            assertEquals(46f, definition?.zoomModelFov)
            assertEquals("tacz:ak47/ak47_shoot", definition?.shootSoundId)
            assertEquals("tacz:ak47/ak47_draw", definition?.drawSoundId)
            assertEquals("tacz:ak47/ak47_put_away", definition?.putAwaySoundId)
            assertEquals("tacz:dry_fire", definition?.dryFireSoundId)
            assertEquals("tacz:ak47/ak47_inspect", definition?.inspectSoundId)
            assertEquals("tacz:ak47/ak47_inspect_empty", definition?.inspectEmptySoundId)
            assertEquals("tacz:ak47/ak47_reload_empty", definition?.reloadEmptySoundId)
        } finally {
            deleteRecursively(gameRoot)
        }
    }

    @Test
    public fun `scan should parse zip pack index and display data`() {
        val gameRoot = Files.createTempDirectory("tacz-legacy-display-zip-pack-")
        try {
            val configRoot = gameRoot.resolve("config")
            Files.createDirectories(configRoot)

            val taczRoot = gameRoot.resolve("tacz")
            Files.createDirectories(taczRoot)

            val zipPath = taczRoot.resolve("sample_display_pack.zip")
            createPackZip(zipPath)

            val report = scanner.scanAndLog(configRoot, LogManager.getLogger("GunDisplayScanZipPackTest"))

            assertNotNull(report)
            assertEquals(1, report?.total)
            assertEquals(1, report?.successCount)
            assertEquals(0, report?.failedCount)

            val snapshot = GunDisplayRuntimeRegistry().replace(report)
            assertEquals(1, snapshot.loadedCount)

            val definition = snapshot.findDefinition("ak47")
            assertNotNull(definition)
            assertEquals("tacz:ak47_display", definition?.displayResource)
            assertEquals("assets/tacz/geo_models/gun/ak47_geo.json", definition?.modelPath)
            assertEquals("assets/tacz/animations/ak47.animation.json", definition?.animationPath)
            assertEquals(true, definition?.modelParseSucceeded)
            assertEquals(1, definition?.modelGeometryCount)
            assertEquals(1, definition?.modelRootBoneCount)
            assertEquals(listOf("body"), definition?.modelRootBoneNames)
            assertEquals(true, definition?.animationParseSucceeded)
            assertEquals(11, definition?.animationClipCount)
            assertEquals("animation.ak47.shoot", definition?.animationFireClipName)
            assertEquals("animation.ak47.draw", definition?.animationDrawClipName)
            assertEquals("animation.ak47.run", definition?.animationRunClipName)
            assertEquals(true, definition?.stateMachineResolved)
            assertEquals(0.4f, definition?.stateMachineParams?.get("intro_shell_ejecting_time"))
            assertEquals(true, definition?.playerAnimatorResolved)
            assertEquals("assets/tacz/textures/gun/hud/ak47.png", definition?.hudTexturePath)
            assertEquals("tacz:ak47/ak47_shoot", definition?.shootSoundId)
            assertEquals("tacz:ak47/ak47_inspect", definition?.inspectSoundId)
            assertEquals(1.45f, definition?.ironZoom)
            assertEquals(46f, definition?.zoomModelFov)
        } finally {
            deleteRecursively(gameRoot)
        }
    }

    @Test
    public fun `scan should parse zip pack with nested root folder`() {
        val gameRoot = Files.createTempDirectory("tacz-legacy-display-zip-nested-root-")
        try {
            val configRoot = gameRoot.resolve("config")
            Files.createDirectories(configRoot)

            val taczRoot = gameRoot.resolve("tacz")
            Files.createDirectories(taczRoot)

            val zipPath = taczRoot.resolve("sample_display_pack_nested.zip")
            ZipOutputStream(Files.newOutputStream(zipPath)).use { out ->
                out.putNextEntry(ZipEntry("sample_display_pack/gunpack.meta.json"))
                out.write("{\"namespace\":\"sample_display_pack\"}".toByteArray(StandardCharsets.UTF_8))
                out.closeEntry()

                out.putNextEntry(ZipEntry("sample_display_pack/data/tacz/index/guns/ak47.json"))
                out.write(sampleIndexJson().toByteArray(StandardCharsets.UTF_8))
                out.closeEntry()

                out.putNextEntry(ZipEntry("sample_display_pack/assets/tacz/display/guns/ak47_display.json"))
                out.write(sampleDisplayJson().toByteArray(StandardCharsets.UTF_8))
                out.closeEntry()

                out.putNextEntry(ZipEntry("sample_display_pack/assets/tacz/geo_models/gun/ak47_geo.json"))
                out.write(sampleGeoModelJson().toByteArray(StandardCharsets.UTF_8))
                out.closeEntry()

                out.putNextEntry(ZipEntry("sample_display_pack/assets/tacz/animations/ak47.animation.json"))
                out.write(sampleAnimationJson().toByteArray(StandardCharsets.UTF_8))
                out.closeEntry()

                out.putNextEntry(ZipEntry("sample_display_pack/assets/tacz/scripts/ak47_state_machine.lua"))
                out.write(sampleStateMachineLua().toByteArray(StandardCharsets.UTF_8))
                out.closeEntry()

                out.putNextEntry(ZipEntry("sample_display_pack/assets/tacz/player_animator/rifle_default.player_animation.json"))
                out.write(samplePlayerAnimatorJson().toByteArray(StandardCharsets.UTF_8))
                out.closeEntry()
            }

            val report = scanner.scanAndLog(configRoot, LogManager.getLogger("GunDisplayScanZipNestedRootTest"))

            assertNotNull(report)
            assertEquals(1, report?.total)
            assertEquals(1, report?.successCount)
            assertEquals(0, report?.failedCount)

            val snapshot = GunDisplayRuntimeRegistry().replace(report)
            assertEquals(1, snapshot.loadedCount)
            val definition = snapshot.findDefinition("ak47")
            assertNotNull(definition)
            assertEquals("assets/tacz/geo_models/gun/ak47_geo.json", definition?.modelPath)
            assertEquals("assets/tacz/animations/ak47.animation.json", definition?.animationPath)
        } finally {
            deleteRecursively(gameRoot)
        }
    }

    private fun stageDirectoryPack(packRoot: Path) {
        Files.createDirectories(packRoot)
        Files.write(
            packRoot.resolve("gunpack.meta.json"),
            "{\"namespace\":\"sample_default_pack\"}".toByteArray(StandardCharsets.UTF_8)
        )

        val indexFile = packRoot
            .resolve("data")
            .resolve("tacz")
            .resolve("index")
            .resolve("guns")
            .resolve("ak47.json")
        Files.createDirectories(indexFile.parent)
        Files.write(indexFile, sampleIndexJson().toByteArray(StandardCharsets.UTF_8))

        val displayFile = packRoot
            .resolve("assets")
            .resolve("tacz")
            .resolve("display")
            .resolve("guns")
            .resolve("ak47_display.json")
        Files.createDirectories(displayFile.parent)
        Files.write(displayFile, sampleDisplayJson().toByteArray(StandardCharsets.UTF_8))

        val modelFile = packRoot
            .resolve("assets")
            .resolve("tacz")
            .resolve("geo_models")
            .resolve("gun")
            .resolve("ak47_geo.json")
        Files.createDirectories(modelFile.parent)
        Files.write(modelFile, sampleGeoModelJson().toByteArray(StandardCharsets.UTF_8))

        val animationFile = packRoot
            .resolve("assets")
            .resolve("tacz")
            .resolve("animations")
            .resolve("ak47.animation.json")
        Files.createDirectories(animationFile.parent)
        Files.write(animationFile, sampleAnimationJson().toByteArray(StandardCharsets.UTF_8))

        val stateMachineFile = packRoot
            .resolve("assets")
            .resolve("tacz")
            .resolve("scripts")
            .resolve("ak47_state_machine.lua")
        Files.createDirectories(stateMachineFile.parent)
        Files.write(stateMachineFile, sampleStateMachineLua().toByteArray(StandardCharsets.UTF_8))

        val playerAnimatorFile = packRoot
            .resolve("assets")
            .resolve("tacz")
            .resolve("player_animator")
            .resolve("rifle_default.player_animation.json")
        Files.createDirectories(playerAnimatorFile.parent)
        Files.write(playerAnimatorFile, samplePlayerAnimatorJson().toByteArray(StandardCharsets.UTF_8))
    }

    private fun createPackZip(zipPath: Path) {
        ZipOutputStream(Files.newOutputStream(zipPath)).use { out ->
            out.putNextEntry(ZipEntry("gunpack.meta.json"))
            out.write("{\"namespace\":\"sample_display_pack\"}".toByteArray(StandardCharsets.UTF_8))
            out.closeEntry()

            out.putNextEntry(ZipEntry("data/tacz/index/guns/ak47.json"))
            out.write(sampleIndexJson().toByteArray(StandardCharsets.UTF_8))
            out.closeEntry()

            out.putNextEntry(ZipEntry("assets/tacz/display/guns/ak47_display.json"))
            out.write(sampleDisplayJson().toByteArray(StandardCharsets.UTF_8))
            out.closeEntry()

            out.putNextEntry(ZipEntry("assets/tacz/geo_models/gun/ak47_geo.json"))
            out.write(sampleGeoModelJson().toByteArray(StandardCharsets.UTF_8))
            out.closeEntry()

            out.putNextEntry(ZipEntry("assets/tacz/animations/ak47.animation.json"))
            out.write(sampleAnimationJson().toByteArray(StandardCharsets.UTF_8))
            out.closeEntry()

            out.putNextEntry(ZipEntry("assets/tacz/scripts/ak47_state_machine.lua"))
            out.write(sampleStateMachineLua().toByteArray(StandardCharsets.UTF_8))
            out.closeEntry()

            out.putNextEntry(ZipEntry("assets/tacz/player_animator/rifle_default.player_animation.json"))
            out.write(samplePlayerAnimatorJson().toByteArray(StandardCharsets.UTF_8))
            out.closeEntry()
        }
    }

    private fun sampleIndexJson(): String =
        """
        {
          // 枪械显示映射
          "display": "tacz:ak47_display"
        }
        """.trimIndent()

    private fun sampleDisplayJson(): String =
        """
        {
                    "model": "tacz:gun/ak47_geo",
                    "texture": "tacz:gun/uv/ak47",
                    "lod": {
                        "model": "tacz:gun/lod/ak47",
                        "texture": "tacz:gun/lod/ak47"
                    },
                    "slot": "tacz:gun/slot/ak47",
                    "animation": "tacz:ak47",
                    "state_machine": "tacz:ak47_state_machine",
                    "state_machine_param": {
                        "intro_shell_ejecting_time": 0.4,
                        "bolt_shell_ejecting_time": 0.17
                    },
                    "player_animator_3rd": "tacz:rifle_default",
                    "third_person_animation": "m16",
          "hud": "tacz:gun/hud/ak47",
          "hud_empty": "tacz:gun/hud/ak47_empty",
          "iron_zoom": 1.45,
          "zoom_model_fov": 46,
                    "sounds": {
                        "shoot": "tacz:ak47/ak47_shoot",
                        "shoot_3p": "tacz:ak47/ak47_shoot_3p",
                        "draw": "tacz:ak47/ak47_draw",
                        "put_away": "tacz:ak47/ak47_put_away",
                        "reload_empty": "tacz:ak47/ak47_reload_empty",
                        "reload_tactical": "tacz:ak47/ak47_reload_tactical",
                        "inspect": "tacz:ak47/ak47_inspect",
                        "inspect_empty": "tacz:ak47/ak47_inspect_empty",
                        "dry_fire": "tacz:dry_fire"
                    },
          "show_crosshair": false
        }
        """.trimIndent()

        private fun sampleGeoModelJson(): String =
                """
                {
                    "format_version": "1.12.0",
                    "minecraft:geometry": [
                        {
                            "description": {
                                "identifier": "geometry.ak47"
                            },
                            "bones": [
                                {
                                    "name": "body",
                                    "pivot": [0, 0, 0],
                                    "cubes": [
                                        {
                                            "origin": [0, 0, 0],
                                            "size": [1, 1, 1],
                                            "uv": [0, 0]
                                        },
                                        {
                                            "origin": [1, 0, 0],
                                            "size": [1, 1, 1],
                                            "uv": [2, 0]
                                        }
                                    ]
                                }
                            ]
                        }
                    ]
                }
                """.trimIndent()

        private fun sampleAnimationJson(): String =
                """
                {
                    "format_version": "1.8.0",
                    "animations": {
                        "animation.ak47.idle": {
                                "loop": true,
                                "animation_length": 6.0
                        },
                            "animation.ak47.shoot": {
                                "animation_length": 0.12
                            },
                            "animation.ak47.reload_tactical": {
                                "loop": false,
                                "animation_length": 2.5
                            },
                            "animation.ak47.inspect": {
                                "animation_length": 1.2
                            },
                            "animation.ak47.dry_fire": {
                                "animation_length": 0.18
                        },
                            "animation.ak47.draw": {
                                "animation_length": 0.32
                            },
                            "animation.ak47.put_away": {
                                "animation_length": 0.24
                            },
                            "animation.ak47.walk": {
                                "loop": true,
                                "animation_length": 0.9
                            },
                            "animation.ak47.run": {
                                "loop": true,
                                "animation_length": 0.7
                            },
                            "animation.ak47.ads": {
                                "animation_length": 0.28
                            },
                            "animation.ak47.bolt": {
                                "animation_length": 0.22
                        }
                    }
                }
                """.trimIndent()

        private fun sampleStateMachineLua(): String =
                """
                -- minimal state machine for tests
                return {}
                """.trimIndent()

        private fun samplePlayerAnimatorJson(): String =
                """
                {
                    "format_version": "1.0.0",
                    "animations": {}
                }
                """.trimIndent()

    private fun deleteRecursively(root: Path) {
        if (!Files.exists(root)) {
            return
        }

        Files.walk(root).use { stream ->
            stream.sorted(Comparator.reverseOrder()).forEach { path ->
                Files.deleteIfExists(path)
            }
        }
    }

}
