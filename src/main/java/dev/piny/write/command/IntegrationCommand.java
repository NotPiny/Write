package dev.piny.write.command;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.piny.write.Write;
import dev.piny.write.item.GenericItem;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.jspecify.annotations.Nullable;

public class IntegrationCommand {
    public LiteralCommandNode<CommandSourceStack> create() {
        return Commands.literal("integration")
                .requires(source -> source.getSender().hasPermission("write.integration"))
                .then(
                        Commands.literal("register")
                                .then(
                                        Commands.literal("item")
                                                .then(
                                                        Commands.argument("key", ArgumentTypes.namespacedKey())
                                                                .executes(context -> {
                                                                    NamespacedKey itemKey = context.getArgument("key", NamespacedKey.class);

                                                                    int result = registerItem(itemKey, null);
                                                                    processResult(context, result, itemKey);
                                                                    return result;
                                                                })
                                                                .then(
                                                                        Commands.argument("onInteract", ArgumentTypes.namespacedKey())
                                                                                .executes(context -> {
                                                                                    NamespacedKey itemKey = context.getArgument("key", NamespacedKey.class);
                                                                                    NamespacedKey onInteract = context.getArgument("onInteract", NamespacedKey.class);

                                                                                    int result = registerItem(itemKey, onInteract);
                                                                                    processResult(context, result, itemKey);
                                                                                    return result;
                                                                                })
                                                                )
                                                )
                                )
                )
                .build();
    }

    private void processResult(CommandContext<CommandSourceStack> context, int result, NamespacedKey itemKey) {
        if (result == 1) {
            context.getSource().getSender().sendRichMessage("<green>Registered item: " + itemKey.toString());
        } else {
            context.getSource().getSender().sendRichMessage("<red>Item already registered: " + itemKey.toString());
        }
    }

    private int registerItem(NamespacedKey itemKey, @Nullable NamespacedKey onInteract) {
        GenericItem item = Write.ITEM_REGISTRY.stream()
                .filter(i -> i.key().toString().equals(itemKey.toString()))
                .findFirst()
                .orElse(null);

        if (item != null) {
            return 0;
        }

        GenericItem newItem = new GenericItem(itemKey);

        if (onInteract != null) {
            newItem.onInteract(event -> {
                Bukkit.dispatchCommand(event.getPlayer(), "function " + onInteract.getNamespace() + ":" + onInteract.getKey());
            });
        }

        newItem.register();

        return 1;
    }
}
