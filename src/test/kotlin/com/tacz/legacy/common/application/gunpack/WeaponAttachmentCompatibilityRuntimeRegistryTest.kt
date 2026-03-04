package com.tacz.legacy.common.application.gunpack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

public class WeaponAttachmentCompatibilityRuntimeRegistryTest {

    @Test
    public fun `resolveAllowedAttachmentIds should fallback to unrestricted when allow list only contains invalid ids`() {
        val snapshot = WeaponAttachmentCompatibilitySnapshot(
            loadedAtEpochMillis = 1L,
            attachmentsById = mapOf(
                "tacz:scope_a" to attachment("tacz:scope_a")
            ),
            allowEntriesByGunId = mapOf(
                "ak47" to setOf("tacz:high_version_only_scope")
            ),
            tagsByTagId = emptyMap(),
            ammoIconTextureByAmmoId = emptyMap()
        )

        assertNull(snapshot.resolveAllowedAttachmentIds("tacz:ak47"))
        assertTrue(snapshot.isAttachmentAllowed("tacz:ak47", "tacz:scope_a"))
    }

    @Test
    public fun `resolveAllowedAttachmentIds should keep valid ids and filter unknown ids`() {
        val snapshot = WeaponAttachmentCompatibilitySnapshot(
            loadedAtEpochMillis = 1L,
            attachmentsById = mapOf(
                "tacz:scope_a" to attachment("tacz:scope_a")
            ),
            allowEntriesByGunId = mapOf(
                "ak47" to setOf("tacz:scope_a", "tacz:high_version_only_scope")
            ),
            tagsByTagId = emptyMap(),
            ammoIconTextureByAmmoId = emptyMap()
        )

        val allowed = snapshot.resolveAllowedAttachmentIds("tacz:ak47")

        assertEquals(setOf("tacz:scope_a"), allowed)
        assertTrue(snapshot.isAttachmentAllowed("tacz:ak47", "tacz:scope_a"))
    }

    @Test
    public fun `resolveAllowedAttachmentIds should filter invalid entries resolved from tags and definition allow list`() {
        val snapshot = WeaponAttachmentCompatibilitySnapshot(
            loadedAtEpochMillis = 1L,
            attachmentsById = mapOf(
                "tacz:scope_a" to attachment("tacz:scope_a"),
                "tacz:muzzle_a" to attachment("tacz:muzzle_a")
            ),
            allowEntriesByGunId = mapOf(
                "ak47" to setOf("#tacz:scope_family")
            ),
            tagsByTagId = mapOf(
                "#tacz:scope_family" to setOf("tacz:scope_a", "tacz:high_version_only_scope")
            ),
            ammoIconTextureByAmmoId = emptyMap()
        )

        val allowed = snapshot.resolveAllowedAttachmentIds(
            gunId = "tacz:ak47",
            definitionAllowEntries = setOf("tacz:muzzle_a", "tacz:high_version_only_muzzle")
        )

        assertEquals(setOf("tacz:scope_a", "tacz:muzzle_a"), allowed)
    }

    private fun attachment(id: String): WeaponAttachmentDefinition {
        return WeaponAttachmentDefinition(
            attachmentId = id,
            attachmentType = "SCOPE",
            sourceId = "test",
            displayId = null,
            iconTextureAssetPath = null
        )
    }
}
