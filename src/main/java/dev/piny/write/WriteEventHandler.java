package dev.piny.write;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import dev.piny.write.block.GenericBlock;
import dev.piny.write.event.BlockRegisterEvent;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.AsyncStructureGenerateEvent;
import org.bukkit.util.EntityTransformer;

import java.util.HashMap;
import java.util.UUID;

public class WriteEventHandler implements Listener {
    public WriteEventHandler() {
        Bukkit.getPluginManager().registerEvents(this, Write.getInstance());
    }

    HashMap<UUID, NamespacedKey> unknownBlockEntities = new HashMap<>();

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (Write.RESOURCE_PACKS.isEmpty()) return;
        event.getPlayer().sendResourcePacks(ResourcePackRequest.resourcePackRequest()
                        .packs(Write.RESOURCE_PACKS)
                        .prompt(Component.text("This server uses Write, a library for creating custom items and blocks. You need to download the resource pack to see the custom content."))
                        .required(true)
                .asResourcePackRequest());
    }

    @EventHandler
    public void onEntityAddToWorld(EntityAddToWorldEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof ItemDisplay itemDisplay)) return;

        NamespacedKey key = itemDisplay.getItemStack().getItemMeta().getItemModel();
        if (key == null) return;

        NamespacedKey blockKey = new NamespacedKey(key.getNamespace(), key.getKey().replace("block/", ""));

        boolean found = Write.BLOCK_REGISTRY.stream()
                .anyMatch(block -> block.key().equals(blockKey));

        if (found) {
            GenericBlock.createInstanceFromDisplay(itemDisplay);
            unknownBlockEntities.remove(itemDisplay.getUniqueId());
        } else {
            unknownBlockEntities.put(itemDisplay.getUniqueId(), blockKey);
        }
    }

    @EventHandler
    public void onBlockRegister(BlockRegisterEvent event) {
        NamespacedKey blockKey = event.getBlock().key();

        unknownBlockEntities.entrySet().removeIf(entry -> {
            if (entry.getValue().equals(blockKey)) {
                Entity entity = Bukkit.getEntity(entry.getKey());
                if (entity instanceof ItemDisplay itemDisplay) {
                    GenericBlock.createInstanceFromDisplay(itemDisplay);
                }

                return true;
            }
            return false;
        });
    }
}