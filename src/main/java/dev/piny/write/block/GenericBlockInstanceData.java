package dev.piny.write.block;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * A plain, Gson-safe representation of a {@link GenericBlockInstance} used for persistence.
 * Stores just enough to recover a real {@link GenericBlockInstance} later, without storing any Bukkit objects.
 */
public record GenericBlockInstanceData(
        String blockKey,
        String world,
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        UUID itemDisplayId
) {
    /**
     * Reconstructs the {@link Location} this data represents.
     * @return Returns the reconstructed location, or {@code null} if {@link #world()} refers to a world that isn't loaded.
     */
    @Nullable
    public Location toLocation() {
        World bukkitWorld = world != null ? Bukkit.getWorld(world) : null;
        if (bukkitWorld == null) return null;
        return new Location(bukkitWorld, x, y, z, yaw, pitch);
    }
}
