package com.tacz.legacy.client.model.papi;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.item.ItemStack;

import java.util.function.Function;

/**
 * Resolves player name text for gun model text display.
 * Port of upstream TACZ PlayerNamePapi.
 */
public class PlayerNamePapi implements Function<ItemStack, String> {
    public static final String NAME = "player_name";

    @Override
    public String apply(ItemStack stack) {
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        if (player != null) {
            return player.getName();
        }
        return "";
    }
}
