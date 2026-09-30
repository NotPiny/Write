package dev.piny.write.tasks;

import dev.piny.write.Write;
import dev.piny.write.block.GenericBlockInstance;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Display;

import java.util.Iterator;

public class GenericBlockResyncLightingTask {
    private static final FileConfiguration CONFIG = Write.getInstance().getConfig();

    public static Display.Brightness getBrightnessFromLocation(Location location) {
        Block block = location.getBlock();

        if (block.getLightFromSky() > 0 || block.getLightFromBlocks() > 0) { // If the block has any light, return its brightness
            return new Display.Brightness(block.getLightFromBlocks(), block.getLightFromSky());
        }

        // If the block has no light (like it does when it's a solid block because????),
        // we need to check its neighbours to see if any of them have light.
        // If they do, we return the highest light value from the neighbours.
        // This causes snapped lighting, but I don't know how to fix that.
        int maxBlockLight = 0;
        int maxSkyLight = 0;

        for (Block neighbour : new Block[]{
                block.getRelative(1, 0, 0),
                block.getRelative(-1, 0, 0),
                block.getRelative(0, 1, 0),
                block.getRelative(0, -1, 0),
                block.getRelative(0, 0, 1),
                block.getRelative(0, 0, -1),
                block // If the block is transparent it should still have light, so we'll check it too (only triggers if non-default material)
        }) {
            maxBlockLight = Math.max(maxBlockLight, neighbour.getLightFromBlocks());
            maxSkyLight = Math.max(maxSkyLight, neighbour.getLightFromSky());
        }

        return new Display.Brightness(maxBlockLight, maxSkyLight);
    }

    public GenericBlockResyncLightingTask() {
        Bukkit.getScheduler().runTaskTimer(Write.getInstance(), () -> {
            Iterator<GenericBlockInstance> iterator = Write.BLOCK_INSTANCES.iterator();
            while (iterator.hasNext()) {
                GenericBlockInstance instance = iterator.next();
                if (instance.itemDisplay() == null) continue; // Not resolved yet, wait for its chunk to load

                if (instance.itemDisplay().isDead()) {
                    Write.getInstance().getLogger().warning("[GenericBlockResyncLightingTask] Removing block instance at " + instance.location() + " with dead item display.");
                    iterator.remove();
                    continue;
                }

                instance.itemDisplay().setBrightness(getBrightnessFromLocation(instance.location()));
            }
        }, CONFIG.getLong("tasks.block.resync_lighting.start_delay_ticks", 60L), CONFIG.getLong("tasks.block.resync_lighting.interval_ticks", 20L));
    }
}
