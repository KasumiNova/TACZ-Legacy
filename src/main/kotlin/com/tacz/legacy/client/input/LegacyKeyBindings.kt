package com.tacz.legacy.client.input

import net.minecraft.client.settings.KeyBinding
import net.minecraftforge.client.settings.KeyConflictContext
import net.minecraftforge.fml.client.registry.ClientRegistry
import org.lwjgl.input.Keyboard

internal object LegacyKeyBindings {
    private const val CATEGORY: String = "key.category.tacz"
    private const val MOUSE_LEFT: Int = -100
    private const val MOUSE_RIGHT: Int = -99

    internal val SHOOT: KeyBinding = KeyBinding("key.tacz.shoot.desc", KeyConflictContext.IN_GAME, MOUSE_LEFT, CATEGORY)
    internal val AIM: KeyBinding = KeyBinding("key.tacz.aim.desc", KeyConflictContext.IN_GAME, MOUSE_RIGHT, CATEGORY)
    internal val RELOAD: KeyBinding = KeyBinding("key.tacz.reload.desc", KeyConflictContext.IN_GAME, Keyboard.KEY_R, CATEGORY)
    internal val FIRE_SELECT: KeyBinding = KeyBinding("key.tacz.fire_select.desc", KeyConflictContext.IN_GAME, Keyboard.KEY_G, CATEGORY)
    internal val MELEE: KeyBinding = KeyBinding("key.tacz.melee.desc", KeyConflictContext.IN_GAME, Keyboard.KEY_V, CATEGORY)
    internal val INTERACT: KeyBinding = KeyBinding("key.tacz.interact.desc", KeyConflictContext.IN_GAME, Keyboard.KEY_O, CATEGORY)

    private var registered: Boolean = false

    internal fun registerAll(): Unit {
        if (registered) {
            return
        }
        registered = true
        ClientRegistry.registerKeyBinding(SHOOT)
        ClientRegistry.registerKeyBinding(AIM)
        ClientRegistry.registerKeyBinding(RELOAD)
        ClientRegistry.registerKeyBinding(FIRE_SELECT)
        ClientRegistry.registerKeyBinding(MELEE)
        ClientRegistry.registerKeyBinding(INTERACT)
    }
}
