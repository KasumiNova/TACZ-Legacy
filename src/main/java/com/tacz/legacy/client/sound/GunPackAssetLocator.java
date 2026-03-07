package com.tacz.legacy.client.sound;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.io.FilterInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Resolves raw asset streams directly from loaded gun packs (directory or zip).
 *
 * <p>This is used by the client sound bridge so gun pack {@code .ogg} assets can be
 * exposed to Minecraft's resource manager without unpacking them to disk.</p>
 */
@SideOnly(Side.CLIENT)
public final class GunPackAssetLocator {
    private GunPackAssetLocator() {
    }

    public static boolean resourceExists(List<File> packFiles, ResourceLocation location) {
        String assetPath = toAssetPath(location);
        for (File packFile : packFiles) {
            if (!packFile.exists()) {
                continue;
            }
            if (packFile.isDirectory()) {
                Path path = packFile.toPath().resolve(assetPath);
                if (Files.isRegularFile(path)) {
                    return true;
                }
                continue;
            }
            if (!isZipPack(packFile)) {
                continue;
            }
            try (ZipFile zipFile = new ZipFile(packFile)) {
                if (zipFile.getEntry(assetPath) != null) {
                    return true;
                }
            } catch (IOException ignored) {
                // Keep probing remaining packs.
            }
        }
        return false;
    }

    @Nullable
    public static InputStream openResource(List<File> packFiles, ResourceLocation location) throws IOException {
        String assetPath = toAssetPath(location);
        for (File packFile : packFiles) {
            if (!packFile.exists()) {
                continue;
            }
            if (packFile.isDirectory()) {
                Path path = packFile.toPath().resolve(assetPath);
                if (Files.isRegularFile(path)) {
                    return Files.newInputStream(path);
                }
                continue;
            }
            if (!isZipPack(packFile)) {
                continue;
            }
            ZipFile zipFile = new ZipFile(packFile);
            ZipEntry entry = zipFile.getEntry(assetPath);
            if (entry == null) {
                zipFile.close();
                continue;
            }
            InputStream delegate = zipFile.getInputStream(entry);
            return new FilterInputStream(delegate) {
                @Override
                public void close() throws IOException {
                    try {
                        super.close();
                    } finally {
                        zipFile.close();
                    }
                }
            };
        }
        return null;
    }

    private static String toAssetPath(ResourceLocation location) {
        return "assets/" + location.getNamespace() + "/" + location.getPath();
    }

    private static boolean isZipPack(File packFile) {
        return packFile.isFile() && packFile.getName().toLowerCase(Locale.ROOT).endsWith(".zip");
    }
}
