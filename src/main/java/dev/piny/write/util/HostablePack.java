package dev.piny.write.util;

import dev.piny.write.Write;
import net.kyori.adventure.resource.ResourcePackInfo;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

public record HostablePack(@NotNull String name, @Nullable ResourcePackInfo resourcePackInfo) {
    public static String getSha1Hash(File file) throws NoSuchAlgorithmException, IOException {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        try (InputStream is = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }

        byte[] hashBytes = digest.digest();
        return bytesToHex(hashBytes);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public UUID uuid() {
        return resourcePackInfo == null ? UUID.nameUUIDFromBytes(name.getBytes()) : resourcePackInfo.id();
    }

    public static File resolvePackFile(String pluginName) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin(pluginName);
        if (plugin != null) {
            File ownWriteZip = Paths.get(plugin.getDataFolder().toString(), "write.zip").toFile();
            if (ownWriteZip.exists()) return ownWriteZip;
        }

        return Paths.get(Write.getInstance().getDataFolder().toString(), "extracted_packs", pluginName + ".zip").toFile();
    }

    public String hash() {
        if (resourcePackInfo != null) {
            return resourcePackInfo.hash();
        } else {
            File file = resolvePackFile(name);

            if (file.exists() && !file.isDirectory()) {
                try {
                    return getSha1Hash(file);
                } catch (NoSuchAlgorithmException | IOException e) {
                    Write.getInstance().getLogger().severe("Failed to hash resource pack '" + name + "': " + e.getMessage());
                }
            }

            return "";
        }
    }
}