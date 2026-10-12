package dev.piny.write.item;

import dev.piny.write.Write;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.concurrent.atomic.AtomicBoolean;

public class GenericItemEventHandler implements Listener {
    public GenericItemEventHandler() {
        Bukkit.getPluginManager().registerEvents(this, Write.getInstance());
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getItem() == null || event.getItem().getItemMeta() == null) return;

        AtomicBoolean found = new AtomicBoolean(false);

        Write.ITEM_REGISTRY.stream()
                .filter(item -> item.key().equals(event.getItem().getItemMeta().getItemModel()))
                .findFirst()
                .ifPresent(item -> {
                    found.set(true);
                    // Bukkit fires a PlayerInteractEvent for both the main hand and the off hand on a single
                    // right click, so only handle one of them to avoid invoking onInteract twice.
                    if (!GenericItem.shouldHandle(event, item.key())) return;
                    item.onInteract().accept(event);
                });

        if (found.get()) {
            return;
        }

        NamespacedKey itemModel = event.getItem().getItemMeta().getItemModel();

        if (itemModel != null) {
            NamespacedKey blockKey = new NamespacedKey(itemModel.getNamespace(), itemModel.getKey().replace("block/", ""));

            Write.BLOCK_REGISTRY.stream()
                    .filter(block -> block.key().equals(blockKey))
                    .findFirst()
                    .ifPresent(block -> {
                        // Bukkit fires a PlayerInteractEvent for both the main hand and the off hand on a single
                        // right click, so only handle one of them to avoid invoking onInteract twice.
                        if (!GenericItem.shouldHandle(event, block.key())) return;
                        block.item().onInteract().accept(event);
                    });
        }
    }
}
