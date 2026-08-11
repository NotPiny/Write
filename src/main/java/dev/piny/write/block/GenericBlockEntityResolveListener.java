package dev.piny.write.block;

import dev.piny.write.Write;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.EntitiesLoadEvent;

/**
 * Resolves the {@link ItemDisplay} entity for {@link GenericBlockInstance}s that were loaded from
 * persistence before their entity's chunk had loaded (in which case {@link GenericBlockInstance#itemDisplay()}
 * is {@code null} and only {@link GenericBlockInstance#itemDisplayId()} is known).
 * <p>
 * When a chunk's entities load, we check whether any of the newly loaded {@link ItemDisplay} entities
 * match the UUID of an unresolved instance, and if so, resolve it.
 */
public class GenericBlockEntityResolveListener implements Listener {
    public GenericBlockEntityResolveListener() {
        Bukkit.getPluginManager().registerEvents(this, Write.getInstance());
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (!(entity instanceof ItemDisplay itemDisplay)) continue;

            for (GenericBlockInstance instance : Write.BLOCK_INSTANCES) {
                if (instance.itemDisplay() != null) continue;
                if (instance.itemDisplayId() == null) continue;
                if (!instance.itemDisplayId().equals(itemDisplay.getUniqueId())) continue;

                instance.itemDisplay(itemDisplay);
                break;
            }
        }
    }
}

