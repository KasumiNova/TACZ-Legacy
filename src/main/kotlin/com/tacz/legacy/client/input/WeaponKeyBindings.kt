package com.tacz.legacy.client.input

import net.minecraft.client.settings.KeyBinding
import net.minecraftforge.fml.client.registry.ClientRegistry
import org.lwjgl.input.Keyboard

public object WeaponKeyBindings {

    public val reloadKey: KeyBinding = KeyBinding(
        "key.tacz.reload.desc",
        Keyboard.KEY_R,
        "key.category.tacz"
    )

    public val inspectKey: KeyBinding = KeyBinding(
        "key.tacz.inspect.desc",
        Keyboard.KEY_V,
        "key.category.tacz"
    )

    public val attachmentWorkbenchKey: KeyBinding = KeyBinding(
        "key.tacz.attachment_workbench.desc",
        Keyboard.KEY_B,
        "key.category.tacz"
    )

    public val immersiveWorkbenchKey: KeyBinding = KeyBinding(
        "key.tacz.immersive_workbench.desc",
        Keyboard.KEY_G,
        "key.category.tacz"
    )

    public fun registerAll() {
        ClientRegistry.registerKeyBinding(reloadKey)
        ClientRegistry.registerKeyBinding(inspectKey)
        ClientRegistry.registerKeyBinding(attachmentWorkbenchKey)
        ClientRegistry.registerKeyBinding(immersiveWorkbenchKey)
    }

}
