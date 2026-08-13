package dev.piny.write.tasks;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dev.piny.write.Write;
import dev.piny.write.block.GenericBlock;
import dev.piny.write.block.GenericBlockInstance;
import dev.piny.write.block.GenericBlockInstanceData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class GenericBlockInstancePersistTask {
    private static void log(String message) {
        if (Write.getInstance().getConfig().getBoolean("tasks.block.persist_block_instances.log", false)) Write.getInstance().getLogger().info("[GenericBlockInstancePersistTask] " + message);
    }

    public static void save() {
        List<GenericBlockInstanceData> serializable = new ArrayList<>();
        for (GenericBlockInstance instance : Write.BLOCK_INSTANCES) {
            serializable.add(instance.toData());
        }

        String data = new Gson().toJson(serializable);

        if (!Files.exists(Paths.get(Write.getInstance().getDataFolder().toString(), "data"))) {
            Write.getInstance().getLogger().warning("[GenericBlockInstancePersistTask] Data folder does not exist, creating...");
            try {
                Files.createDirectories(Paths.get(Write.getInstance().getDataFolder().toString(), "data"));
            } catch (Exception e) {
                Write.getInstance().getLogger().severe("Failed to create data folder: " + e.getMessage());
            }
        }

        try {
            Files.write(Paths.get(Write.getInstance().getDataFolder().toString(), "data", "block_instances.json"), data.getBytes());
            if (!Write.getInstance().getConfig().getBoolean("meta.checkpoints.first_save_triggered", false)) {
                Write.getInstance().getConfig().set("meta.checkpoints.first_save_triggered", true);
                Write.getInstance().saveConfig();
            }
        } catch (Exception e) {
            Write.getInstance().getLogger().severe("Failed to save block instances: " + e.getMessage());
        }
    }

    public static void load() {
        if (!Files.exists(Paths.get(Write.getInstance().getDataFolder().toString(), "data", "block_instances.json"))) {
            if (Write.getInstance().getConfig().getBoolean("meta.checkpoints.first_save_triggered", false))
                Write.getInstance().getLogger().warning("[GenericBlockInstancePersistTask] Block instances file does not exist, skipping load.");
            else log("Block instances file does not exist, config checkpoint states that there hasn't been a save yet, skipping load.");

            return;
        }

        try {
            String data = new String(Files.readAllBytes(Paths.get(Write.getInstance().getDataFolder().toString(), "data", "block_instances.json")));
            Type listType = new TypeToken<List<GenericBlockInstanceData>>() {}.getType();
            List<GenericBlockInstanceData> loaded = new Gson().fromJson(data, listType);

            if (loaded == null) return;

            for (GenericBlockInstanceData entry : loaded) {
                GenericBlock block = null;
                for (GenericBlock candidate : Write.BLOCK_REGISTRY) {
                    if (candidate.key().toString().equals(entry.blockKey())) {
                        block = candidate;
                        break;
                    }
                }

                if (block == null) {
                    Write.getInstance().getLogger().warning("[GenericBlockInstancePersistTask] Skipping block instance with unknown block key: " + entry.blockKey());
                    continue;
                }

                World world = entry.world() != null ? Bukkit.getWorld(entry.world()) : null;
                if (world == null) {
                    Write.getInstance().getLogger().warning("[GenericBlockInstancePersistTask] Skipping block instance with unknown world: " + entry.world());
                    continue;
                }

                Location location = new Location(world, entry.x(), entry.y(), entry.z(), entry.yaw(), entry.pitch());

                ItemDisplay itemDisplay = null;
                if (entry.itemDisplayId() != null) {
                    Entity entity = Bukkit.getEntity(entry.itemDisplayId());
                    if (entity instanceof ItemDisplay display) itemDisplay = display;
                    // If the entity isn't loaded yet (chunk not loaded), it will be resolved later by
                    // GenericBlockEntityResolveListener once its chunk's entities load.
                }

                Write.BLOCK_INSTANCES.add(new GenericBlockInstance(block, location, entry.itemDisplayId(), itemDisplay));
            }
        } catch (Exception e) {
            Write.getInstance().getLogger().severe("Failed to load block instances: " + e.getMessage());
        }
    }

    public GenericBlockInstancePersistTask() {
        Write.getInstance().getServer().getScheduler().runTaskTimer(Write.getInstance(), () -> {
            log("Saving block instances...");
            save();
            log("Saved " + Write.BLOCK_INSTANCES.size() + " block instances.");
        }, Write.getInstance().getConfig().getLong("tasks.block.persist_block_instances.start_delay_ticks", 2400L), Write.getInstance().getConfig().getLong("tasks.block.persist_block_instances.interval_ticks", 1200L));
    }
}
