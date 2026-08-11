package dev.piny.write.item;

import dev.piny.write.Write;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.concurrent.atomic.AtomicBoolean;

public class GenericItemEventHandler implements Listener {
    public GenericItemEventHandler() {
        Bukkit.getPluginManager().registerEvents(this, Write.getInstance());
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getItem() == null || event.getItem().getItemMeta() == null) return;
        // Bukkit fires a PlayerInteractEvent for both the main hand and the off hand on a single right click,
        // so only handle the main hand event to avoid invoking onInteract twice.
        if (event.getHand() != EquipmentSlot.HAND) return;

        AtomicBoolean found = new AtomicBoolean(false);

        Write.ITEM_REGISTRY.stream()
                .filter(item -> item.key().equals(event.getItem().getItemMeta().getItemModel()))
                .findFirst()
                .ifPresent(item -> {
                    item.onInteract().accept(event);
                    found.set(true);
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
                        block.item().onInteract().accept(event);
                    });
        }
    }
}
