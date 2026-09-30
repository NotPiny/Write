package dev.piny.write;

import dev.faststats.bukkit.BukkitContext;
import dev.faststats.data.Metric;
import dev.piny.pineLib.PineLib;
import dev.piny.pineLib.network.NetworkUtils;
import dev.piny.write.block.GenericBlock;
import dev.piny.write.block.GenericBlockEntityResolveListener;
import dev.piny.write.block.GenericBlockEventHandler;
import dev.piny.write.block.GenericBlockInstance;
import dev.piny.write.item.GenericItem;
import dev.piny.write.item.GenericItemEventHandler;
import dev.piny.write.network.ResourcePackRequestHandler;
import dev.piny.write.tasks.GenericBlockInstancePersistTask;
import dev.piny.write.tasks.GenericBlockResyncLightingTask;
import dev.piny.write.util.HostablePack;
import dev.piny.write.util.McPacksUploader;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.ApiStatus;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class Write extends JavaPlugin {
    private final BukkitContext context = new BukkitContext.Factory(this, "461f7c4767114f339505b52fd8ec072c")
            .metrics(factory -> factory
                    .addMetric(Metric.number("items_registered", ITEM_REGISTRY::size))
                    .addMetric(Metric.number("blocks_registered", BLOCK_REGISTRY::size))
                    .addMetric(Metric.number("resource_packs", RESOURCE_PACKS::size))
                    .create())
            .create();

    private String hostMethod = "none";

    /**
     * A registry of all GenericItems that have been registered. This is used to keep track of all items and their associated data.
     * You should not modify this list directly, as it is managed by the GenericItem class.
     *
     * @see GenericItem#register()
     */
    @ApiStatus.Internal
    public static final ArrayList<GenericItem> ITEM_REGISTRY = new ArrayList<>();

    /**
     * A registry of all GenericBlocks that have been registered. This is used to keep track of all blocks and their associated data.
     * You should not modify this list directly, as it is managed by the GenericBlock class
     *
     * @see GenericBlock#register()
     */
    @ApiStatus.Internal
    public static final ArrayList<GenericBlock> BLOCK_REGISTRY = new ArrayList<>();

    /**
     * An array of all GenericBlockInstances that have been created. This is used to keep track of all block instances and their associated data.
     */
    public static final ArrayList<GenericBlockInstance> BLOCK_INSTANCES = new ArrayList<>();

    /**
     * An array containing all resource packs that have either been directly registered or found in a scan of all plugins.
     */
    public static final ArrayList<HostablePack> RESOURCE_PACKS = new ArrayList<>();

    private final AtomicBoolean localRouteRegistered = new AtomicBoolean(false);

    // Cached separately from RESOURCE_PACKS so it's safe to read off the main thread when
    // deciding whether to skip a re-upload to mcpacks.dev on rescan.
    private final ConcurrentHashMap<String, ResourcePackInfo> uploadedPackCache = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        // Plugin startup logic
        saveDefaultConfig();
        hostMethod = getConfig().getString("pack.host.method", "none");

        if (!Files.exists(Paths.get(getDataFolder().toString(), "data"))) {
            getLogger().warning("Data folder does not exist, creating...");
            try {
                Files.createDirectories(Paths.get(getDataFolder().toString(), "data"));
            } catch (Exception e) {
                getLogger().severe("Failed to create data folder: " + e.getMessage());
            }
        }

        new GenericItemEventHandler();
        new GenericBlockEventHandler();
        new GenericBlockEntityResolveListener();

        new WriteEventHandler();

        new GenericBlockResyncLightingTask();
        new GenericBlockInstancePersistTask();

        long loadDelay = getConfig().getLong("tasks.block.persist_block_instances.load_delay_ticks", 40L);
        if (loadDelay >= getConfig().getLong("tasks.block.persist_block_instances.start_delay_ticks", 2400L) - 100) loadDelay = getConfig().getLong("tasks.block.persist_block_instances.start_delay_ticks", 2400L) - 100;

        Bukkit.getScheduler().runTaskLater(this, GenericBlockInstancePersistTask::load, loadDelay);

        if (!getConfig().getString("pack.host.method", "none").equals("none")) {
            getLogger().info("Starting resource pack host method: " + hostMethod + " after " + getConfig().getLong("pack.host.scan_delay_ticks", 200L) + " ticks.");

            Bukkit.getScheduler().runTaskLaterAsynchronously(this, this::rescanResourcePacks, getConfig().getLong("pack.host.scan_delay_ticks", 200L));
        }

        context.ready();
    }

    public void rescanResourcePacks() {
        if (getConfig().getString("pack.host.method", "none").equals("none")) {
            getLogger().info("No resource pack hosting method selected, skipping resource pack scan.");
            return;
        }

        if (hostMethod.equals("auto")) {
            hostMethod = Bukkit.getPluginManager().isPluginEnabled("PineLib") ? "local" : "cloud";
        }

        if (hostMethod.equals("local") && !Bukkit.getPluginManager().isPluginEnabled("PineLib")) {
            hostMethod = "none";
        }

        if (hostMethod.equals("none")) {
            getLogger().info("No resource pack hosting method selected, skipping resource pack scan.");
            return;
        }

        if (hostMethod.equals("local") && localRouteRegistered.compareAndSet(false, true)) {
            getLogger().info("Using local resource pack hosting via PineLib.");

            Bukkit.getScheduler().runTask(this, () -> PineLib.addNetworkRoute("/write/hosted_packs/[plugin]", new ResourcePackRequestHandler())); // Never thought I'd have to use this method ngl.
        }

        for (Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
            scanPlugin(plugin);
        }
    }

    private void scanPlugin(Plugin plugin) {
        Path ownWriteZip = Paths.get(plugin.getDataFolder().toString(), "write.zip");
        boolean hasOwnWriteZip = Files.exists(ownWriteZip); // This name has confused me so many times, but I can't think of anything better

        // Own write.zip always takes priority, only open the bundled resource if we need it.
        InputStream bundledWriteZip = hasOwnWriteZip ? null : plugin.getResource("write.zip");
        boolean hasBundledWriteZip = bundledWriteZip != null;

        if (!hasOwnWriteZip && !hasBundledWriteZip) {
            return;
        }

        getLogger().info("Found write.zip in plugin " + plugin.getName() + ", registering resource pack.");

        Path packPath = ownWriteZip;
        if (!hasOwnWriteZip) {
            Path extractedPacksDir = Paths.get(getDataFolder().toString(), "extracted_packs");

            try {
                if (!Files.exists(extractedPacksDir)) {
                    Files.createDirectories(extractedPacksDir);
                    Files.writeString(extractedPacksDir.resolve("readme.txt"), "Hello! This folder is used by the Write plugin to store extracted resource packs from other plugins if they don't have a write.zip in their own folder.");
                }
            } catch (IOException e) {
                getLogger().severe("Failed to create extracted_packs folder: " + e.getMessage());
                try {
                    bundledWriteZip.close();
                } catch (IOException ignored) {}
                return;
            }

            packPath = extractedPacksDir.resolve(plugin.getName() + ".zip");

            getLogger().info("Extracting write.zip from plugin " + plugin.getName() + " to extracted_packs folder.");

            try (InputStream in = bundledWriteZip) {
                Files.copy(in, packPath, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                getLogger().severe("Failed to extract write.zip for plugin " + plugin.getName() + ": " + e.getMessage());
                return;
            }
        }

        // Anything touching players/entities has to run on the main thread because Bukkit is weird like that. Pretty sure it does still include sending resource packs
        if (hostMethod.equals("local")) {
            HostablePack pack = new HostablePack(plugin.getName(), null);
            String packHash = pack.hash();

            Bukkit.getScheduler().runTask(this, () -> {
                RESOURCE_PACKS.removeIf(existing -> existing.name().equals(plugin.getName()));
                RESOURCE_PACKS.add(pack);

                Bukkit.getOnlinePlayers().forEach(player -> {
                    try {
                        ResourcePackInfo info = ResourcePackInfo.resourcePackInfo(pack.uuid(), new URI(NetworkUtils.buildBaseUrl(player) + "/write/hosted_packs/" + pack.name()), packHash);

                        player.sendResourcePacks(ResourcePackRequest.resourcePackRequest()
                                        .packs(info)
                                        .prompt(Component.text("Sorry to disturb, this server uses Write, a library for creating custom items and blocks. You need to download the resource pack to see the custom content."))
                                        .required(true)
                                .asResourcePackRequest());
                    } catch (URISyntaxException e) {
                        getLogger().severe("Failed to build resource pack URL for plugin " + plugin.getName() + ": " + e.getMessage());
                    }
                });
            });
        }

        if (hostMethod.equals("cloud")) {
            try {
                // Reused cache (not RESOURCE_PACKS) so this stays safe to read off the main thread,
                // and so a rescan skips re-uploading to mcpacks.dev if the file hasn't changed.
                String currentHash = HostablePack.getSha1Hash(packPath.toFile());
                ResourcePackInfo cached = uploadedPackCache.get(plugin.getName());

                ResourcePackInfo packInfo;
                if (cached != null && currentHash.equals(cached.hash())) {
                    packInfo = cached;
                } else {
                    McPacksUploader.UploadResponse response = McPacksUploader.upload(packPath.toFile());

                    packInfo = ResourcePackInfo.resourcePackInfo()
                            .id(UUID.fromString(response.data.uuid))
                            .uri(URI.create(response.data.downloadUrl))
                            .hash(response.data.sha1)
                            .asResourcePackInfo();

                    uploadedPackCache.put(plugin.getName(), packInfo);
                }

                ResourcePackInfo finalPackInfo = packInfo;
                Bukkit.getScheduler().runTask(this, () -> {
                    RESOURCE_PACKS.removeIf(existing -> existing.name().equals(plugin.getName()));
                    RESOURCE_PACKS.add(new HostablePack(plugin.getName(), finalPackInfo));

                    Bukkit.getOnlinePlayers().forEach(player -> {
                        player.sendResourcePacks(ResourcePackRequest.resourcePackRequest()
                                        .packs(finalPackInfo)
                                        .prompt(Component.text("Sorry to disturb, this server uses Write, a library for creating custom items and blocks. You need to download the resource pack to see the custom content."))
                                        .required(true)
                                .asResourcePackRequest());
                    });
                });
            } catch (IOException | InterruptedException | NoSuchAlgorithmException e) {
                getLogger().severe("Failed to upload resource pack for plugin " + plugin.getName() + ": " + e.getMessage());
            }
        }
    }

    @Override
    public void onDisable() {
        context.shutdown();
        // Plugin shutdown logic
        GenericBlockInstancePersistTask.save();
    }

    public static Write getInstance() {
        return JavaPlugin.getPlugin(Write.class);
    }

    public static ItemStack getItemStack(String item) {
        for (GenericItem genericItem : ITEM_REGISTRY) {
            if (genericItem.key().toString().equals(item)) {
                return genericItem.getItemStack();
            }
        }

        return Bukkit.getServer().getItemFactory().createItemStack(item);
    }
}
