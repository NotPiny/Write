package dev.piny.write.block;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ItemDisplay;
import org.jetbrains.annotations.ApiStatus;

import java.util.UUID;

/**
 * Represents a placed instance of a {@link GenericBlock} in the world.
 * <p>
 * The backing {@link ItemDisplay} entity may not be immediately available (for example, right after
 * loading persisted instances if the entity's chunk hasn't loaded yet). In that case {@link #itemDisplay()}
 * will return {@code null} until the entity is resolved, which happens once its chunk loads and it's matched
 * by {@link #itemDisplayId()}
 */
public class GenericBlockInstance {
    private final GenericBlock block;
    private final Location location;
    private final UUID itemDisplayId;
    private ItemDisplay itemDisplay;

    public GenericBlockInstance(GenericBlock block, Location location, UUID itemDisplayId, ItemDisplay itemDisplay) {
        this.block = block;
        this.location = location;
        this.itemDisplayId = itemDisplayId;
        this.itemDisplay = itemDisplay;
    }

    /**
     * Creates an instance with a known, already resolved {@link ItemDisplay} entity.
     */
    public GenericBlockInstance(GenericBlock block, Location location, ItemDisplay itemDisplay) {
        this(block, location, itemDisplay != null ? itemDisplay.getUniqueId() : null, itemDisplay);
    }

    /**
     * Creates an instance whose {@link ItemDisplay} entity is not yet resolved (e.g. right after loading
     * from persistence, before its chunk has loaded). {@link #itemDisplay()} will return {@code null} until
     * it is resolved.
     */
    public GenericBlockInstance(GenericBlock block, Location location, UUID itemDisplayId) {
        this(block, location, itemDisplayId, null);
    }

    public GenericBlock block() {
        return block;
    }

    public Location location() {
        return location;
    }

    /**
     * The UUID of the backing {@link ItemDisplay} entity. This is always known, even if the entity itself
     * hasn't been resolved yet.
     * @see #itemDisplay()
     */
    public UUID itemDisplayId() {
        return itemDisplayId;
    }

    /**
     * The backing {@link ItemDisplay} entity, or {@code null} if it hasn't been resolved yet.
     */
    public ItemDisplay itemDisplay() {
        return itemDisplay;
    }

    /**
     * Sets the backing {@link ItemDisplay} entity once it has been resolved.
     */
    @ApiStatus.Internal
    public void itemDisplay(ItemDisplay itemDisplay) {
        this.itemDisplay = itemDisplay;
    }

    /**
     * Converts this instance into a plain, Gson-safe {@link GenericBlockInstanceData} snapshot, suitable for
     * persistence or passing to consumers that shouldn't hold onto live Bukkit objects.
     */
    public GenericBlockInstanceData toData() {
        World world = location.getWorld();
        return new GenericBlockInstanceData(
                block.key().toString(),
                world != null ? world.getName() : null,
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch(),
                itemDisplayId
        );
    }
}
