package dev.piny.write.block;

import dev.piny.write.Write;
import dev.piny.write.tasks.GenericBlockInstancePersistTask;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public class GenericBlockEventHandler implements Listener {
    public GenericBlockEventHandler() {
        Bukkit.getPluginManager().registerEvents(this, Write.getInstance());
    }

    private void removeInstance(GenericBlockInstance instance) {
        Write.BLOCK_INSTANCES.remove(instance);
        if (instance.itemDisplay() != null) instance.itemDisplay().remove();

        GenericBlockInstancePersistTask.scheduleSave(Write.getInstance().getConfig().getLong("tasks.block.persist_block_instances.on_break_delay", 20L));
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Write.BLOCK_INSTANCES.stream()
                .filter(instance -> instance.location().equals(event.getBlock().getLocation().clone().add(0.5, 0.5, 0.5)))
                .findAny()
                .ifPresent(instance -> {
                    GenericBlockInstanceData data = instance.toData();

                    removeInstance(instance);

                    if (instance.block().overrideVanillaBlockDrops() && !event.getPlayer().getGameMode().equals(GameMode.CREATIVE)) {
                        event.setDropItems(false);
                        event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), instance.block().item().getItemStack());
                    }

                    if (instance.block().onPlayerBreak() != null) instance.block().onPlayerBreak().accept(event);
                    if (instance.block().onBreak() != null) instance.block().onBreak().accept(data, GenericBlock.BreakReason.PLAYER);
                });
    }

    private void destroyAndDrop(GenericBlockInstance instance, Block block) {
        removeInstance(instance);

        if (instance.block().overrideVanillaBlockDrops()) {
            block.setType(Material.AIR);
            block.getWorld().dropItemNaturally(block.getLocation(), instance.block().item().getItemStack());
        } else {
            block.breakNaturally();
        }
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> {
            GenericBlockInstance instance = Write.BLOCK_INSTANCES.stream()
                    .filter(i -> i.location().equals(block.getLocation().clone().add(0.5, 0.5, 0.5)))
                    .findAny()
                    .orElse(null);
            if (instance == null) return false;

            GenericBlockInstanceData data = instance.toData();

            destroyAndDrop(instance, block);

            if (instance.block().onBlockExplodeBreak() != null) instance.block().onBlockExplodeBreak().accept(event, data);
            if (instance.block().onBreak() != null) instance.block().onBreak().accept(data, GenericBlock.BreakReason.BLOCK_EXPLODE);

            return true;
        });
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> {
            GenericBlockInstance instance = Write.BLOCK_INSTANCES.stream()
                    .filter(i -> i.location().equals(block.getLocation().clone().add(0.5, 0.5, 0.5)))
                    .findAny()
                    .orElse(null);
            if (instance == null) return false;

            GenericBlockInstanceData data = instance.toData();

            destroyAndDrop(instance, block);

            if (instance.block().onEntityExplodeBreak() != null) instance.block().onEntityExplodeBreak().accept(event, data);
            if (instance.block().onBreak() != null) instance.block().onBreak().accept(data, GenericBlock.BreakReason.ENTITY_EXPLODE);

            return true;
        });
    }

    @EventHandler
    public void onBlockInteracted(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;
        // Bukkit fires a PlayerInteractEvent for both the main hand and the offhand on a single
        // right click, so only handle the main hand event to avoid invoking onInteract twice.
        if (event.getHand() != EquipmentSlot.HAND) return;

        Write.BLOCK_INSTANCES.stream()
                .filter(instance -> instance.location().equals(event.getClickedBlock().getLocation().clone().add(0.5, 0.5, 0.5)))
                .findAny()
                .ifPresent(instance -> {
                    if (instance.block().onInteract() != null) instance.block().onInteract().accept(event);
                });
    }
}
