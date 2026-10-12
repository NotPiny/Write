package dev.piny.write.util;

import dev.piny.write.Write;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

public final class ResourcePackWatcher {
    private ResourcePackWatcher() {}

    private static WatchService watchService;
    private static Thread watchThread;
    private static final Set<Path> WATCHED_ROOTS = ConcurrentHashMap.newKeySet();
    private static final Set<WatchKey> WATCH_KEYS = ConcurrentHashMap.newKeySet();
    private static final AtomicBoolean RESCAN_PENDING = new AtomicBoolean(false);

    public static synchronized void watch(Plugin plugin, Path folder) {
        Path normalized = folder.toAbsolutePath().normalize();
        if (!WATCHED_ROOTS.add(normalized)) return;

        try {
            ensureWatchService();
            registerRecursively(normalized);

            Write.getInstance().getLogger().info("Watching folder-based resource pack for plugin " + plugin.getName() + " for changes.");
        } catch (IOException e) {
            Write.getInstance().getLogger().warning("Failed to watch resource pack folder for plugin " + plugin.getName() + ": " + e.getMessage());
        }
    }

    private static void ensureWatchService() throws IOException {
        if (watchService != null) return;

        watchService = FileSystems.getDefault().newWatchService();

        watchThread = new Thread(ResourcePackWatcher::watchLoop, "Write Resource Pack Watcher");
        watchThread.setDaemon(true);
        watchThread.start();
    }

    private static void registerRecursively(Path root) throws IOException {
        if (!Files.isDirectory(root)) return;

        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public @NonNull FileVisitResult preVisitDirectory(@NonNull Path dir, @NonNull BasicFileAttributes attrs) throws IOException {
                WATCH_KEYS.add(dir.register(watchService, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY));
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void watchLoop() {
        try {
            while (true) {
                WatchKey key = watchService.take();
                key.pollEvents();

                // Newly created subdirectories get watched too
                if (key.watchable() instanceof Path dir) {
                    try {
                        registerRecursively(dir);
                    } catch (IOException ignored) {}
                }

                boolean valid = key.reset();
                if (!valid) WATCH_KEYS.remove(key);

                scheduleDebouncedRescan();
            }
        } catch (InterruptedException | ClosedWatchServiceException e) {
            // Uh oh. Should probs add logging
        }
    }

    private static void scheduleDebouncedRescan() {
        if (!RESCAN_PENDING.compareAndSet(false, true)) return;

        Bukkit.getScheduler().runTaskLaterAsynchronously(Write.getInstance(), () -> {
            RESCAN_PENDING.set(false);
            Write.getInstance().getLogger().info("Detected change in a folder-based resource pack, rescanning...");
            Write.getInstance().rescanResourcePacks();
        }, 20L); // 1 second debounce for annoying programs sake
    }

    public static synchronized void shutdown() {
        WATCH_KEYS.forEach(WatchKey::cancel);
        WATCH_KEYS.clear();
        WATCHED_ROOTS.clear();

        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException ignored) {}
            watchService = null;
        }

        if (watchThread != null) {
            watchThread.interrupt();
            watchThread = null;
        }
    }
}

