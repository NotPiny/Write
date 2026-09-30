package dev.piny.write.command;

import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.piny.write.Write;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.Bukkit;

public class ManageCommand {
    public LiteralCommandNode<CommandSourceStack> create() {
        return Commands.literal("manage")
                .requires(source -> source.getSender().hasPermission("write.manage"))
                .then(
                        Commands.literal("rescan")
                                .executes(context -> {
                                    context.getSource().getSender().sendRichMessage("<yellow>Rescanning plugins for resource packs...");

                                    Bukkit.getScheduler().runTaskAsynchronously(Write.getInstance(), () -> {
                                        Write.getInstance().rescanResourcePacks();

                                        Bukkit.getScheduler().runTask(Write.getInstance(), () ->
                                                context.getSource().getSender().sendRichMessage("<green>Resource pack rescan complete."));
                                    });

                                    return 1;
                                })
                )
                .build();
    }
}

