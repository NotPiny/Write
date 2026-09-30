package dev.piny.write;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import dev.piny.pineLib.network.NetworkUtils;
import dev.piny.write.block.GenericBlock;
import dev.piny.write.block.GenericBlockInstance;
import dev.piny.write.event.BlockRegisterEvent;
import dev.piny.write.util.HostablePack;
import io.papermc.paper.event.player.PlayerPickBlockEvent;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.AsyncStructureGenerateEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.EntityTransformer;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
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

        ArrayList<ResourcePackInfo> packs = new ArrayList<>();

        for (HostablePack pack : Write.RESOURCE_PACKS) {
            if (pack.resourcePackInfo() != null) {
                packs.add(pack.resourcePackInfo());
            } else {
                try {
                    ResourcePackInfo info = ResourcePackInfo.resourcePackInfo(pack.uuid(), new URI(NetworkUtils.buildBaseUrl(event.getPlayer()) + "/write/hosted_packs/" + pack.name()), pack.hash());
                    packs.add(info);
                } catch (URISyntaxException e) {
                    throw new RuntimeException("Failed to build resource pack URL for pack '" + pack.name() + "' for player " + event.getPlayer().getName(), e);
                }
            }
        }

        event.getPlayer().sendResourcePacks(ResourcePackRequest.resourcePackRequest()
                        .packs(packs)
                        .prompt(Component.text("This server uses Write, a library for creating custom items and blocks. You need to download the resource pack to see the custom content."))
                        .required(true)
                .asResourcePackRequest());
    }

    @EventHandler
    public void onEntityAddToWorld(EntityAddToWorldEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof ItemDisplay itemDisplay)) return;

        if (!itemDisplay.getItemStack().hasItemMeta()) return;
        NamespacedKey key = itemDisplay.getItemStack().getItemMeta().getItemModel();
        if (key == null) return;

        NamespacedKey blockKey = new NamespacedKey(key.getNamespace(), key.getKey().replace("block/", ""));

        boolean found = Write.BLOCK_REGISTRY.stream()
                .anyMatch(block -> block.key().equals(blockKey));

        if (found) {
            itemDisplay.setViewRange(itemDisplay.getWorld().getViewDistance() * 16);
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

    @EventHandler
    public void onPlayerPickBlock(PlayerPickBlockEvent event) {
        Block targetBlock = event.getBlock();
        GenericBlockInstance instance = GenericBlock.getBlockInstanceAt(targetBlock.getLocation());
        if (instance == null) return;
        event.setCancelled(true);

        Player player = event.getPlayer();
        PlayerInventory inv = player.getInventory();
        ItemStack customItem = instance.block().item().getItemStack();

        int heldSlot = inv.getHeldItemSlot();

        int existingSlot = -1;
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack != null && stack.isSimilar(customItem)) {
                existingSlot = i;
                break;
            }
        }

        if (existingSlot != -1 && existingSlot < 9) {
            inv.setHeldItemSlot(existingSlot);
        } else if (existingSlot != -1) {
            ItemStack heldStack = inv.getItem(heldSlot);
            inv.setItem(heldSlot, inv.getItem(existingSlot));
            inv.setItem(existingSlot, heldStack);
        } else if (player.getGameMode() == GameMode.CREATIVE) {
            inv.setItem(heldSlot, customItem);
        }
    }
}