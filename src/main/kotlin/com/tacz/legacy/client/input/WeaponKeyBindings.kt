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

    public fun registerAll() {
        ClientRegistry.registerKeyBinding(reloadKey)
        ClientRegistry.registerKeyBinding(inspectKey)
    }

}
