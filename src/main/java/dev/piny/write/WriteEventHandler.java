package dev.piny.write;

import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class WriteEventHandler implements Listener {
    public WriteEventHandler() {
        Bukkit.getPluginManager().registerEvents(this, Write.getInstance());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (Write.RESOURCE_PACKS.isEmpty()) return;
        event.getPlayer().sendResourcePacks(ResourcePackRequest.resourcePackRequest()
                        .packs(Write.RESOURCE_PACKS)
                        .prompt(Component.text("This server uses Write, a library for creating custom items and blocks. You need to download the resource pack to see the custom content."))
                        .required(true)
                .asResourcePackRequest());
    }
}