package com.tacz.legacy.client.sound;

import com.tacz.legacy.TACZLegacy;
import com.tacz.legacy.client.resource.TACZClientAssetManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.data.IMetadataSection;
import net.minecraft.client.resources.data.MetadataSerializer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.awt.image.BufferedImage;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Resource-pack bridge exposing gun pack sound assets to Minecraft's sound system.
 */
@SideOnly(Side.CLIENT)
public final class GunPackSoundResourcePack implements IResourcePack {
    private static final GunPackSoundResourcePack INSTANCE = new GunPackSoundResourcePack();
    private static final Field DEFAULT_RESOURCE_PACKS_FIELD;

    static {
        try {
            DEFAULT_RESOURCE_PACKS_FIELD = Minecraft.class.getDeclaredField("defaultResourcePacks");
            DEFAULT_RESOURCE_PACKS_FIELD.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException("Unable to access Minecraft.defaultResourcePacks", e);
        }
    }

    private volatile Set<String> resourceDomains = Collections.emptySet();

    private GunPackSoundResourcePack() {
    }

    @SuppressWarnings("deprecation")
    public static synchronized void installOrUpdate(Set<ResourceLocation> soundResources) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) {
            return;
        }
        LinkedHashSet<String> newDomains = new LinkedHashSet<>();
        for (ResourceLocation soundId : soundResources) {
            newDomains.add(soundId.getNamespace());
        }
        boolean domainsChanged = !INSTANCE.resourceDomains.equals(newDomains);
        INSTANCE.resourceDomains = Collections.unmodifiableSet(newDomains);

        boolean installedNow = ensureInstalled(minecraft);
        if ((installedNow || domainsChanged) && !newDomains.isEmpty()) {
            TACZLegacy.logger.info("Refreshing resources after gun pack sound bridge update (domains={})", newDomains);
            minecraft.refreshResources();
        }
    }

    @SuppressWarnings("unchecked")
    private static boolean ensureInstalled(Minecraft minecraft) {
        try {
            List<IResourcePack> defaultResourcePacks = (List<IResourcePack>) DEFAULT_RESOURCE_PACKS_FIELD.get(minecraft);
            if (defaultResourcePacks.contains(INSTANCE)) {
                return false;
            }
            defaultResourcePacks.add(INSTANCE);
            return true;
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Unable to install gun pack sound resource pack", e);
        }
    }

    @Override
    public InputStream getInputStream(ResourceLocation location) throws IOException {
        InputStream stream = TACZClientAssetManager.INSTANCE.openPackAsset(location);
        if (stream == null) {
            throw new FileNotFoundException(location.toString());
        }
        return stream;
    }

    @Override
    public boolean resourceExists(ResourceLocation location) {
        String path = location.getPath();
        if (!path.endsWith(".ogg")) {
            return false;
        }
        return TACZClientAssetManager.INSTANCE.hasPackAsset(location);
    }

    @Override
    public Set<String> getResourceDomains() {
        return resourceDomains;
    }

    @Override
    @Nullable
    public <T extends IMetadataSection> T getPackMetadata(MetadataSerializer metadataSerializer, String metadataSectionName) {
        return null;
    }

    @Override
    public BufferedImage getPackImage() {
        return new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
    }

    @Override
    public String getPackName() {
        return "TACZ Gun Pack Sounds";
    }
}
