package dev.piny.write.command;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.piny.write.Write;
import dev.piny.write.block.GenericBlock;
import dev.piny.write.item.GenericItem;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
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
                                                                    processResult(context, result, itemKey, "item");
                                                                    return result;
                                                                })
                                                                .then(
                                                                        Commands.argument("onInteract", ArgumentTypes.namespacedKey())
                                                                                .executes(context -> {
                                                                                    NamespacedKey itemKey = context.getArgument("key", NamespacedKey.class);
                                                                                    NamespacedKey onInteract = context.getArgument("onInteract", NamespacedKey.class);

                                                                                    int result = registerItem(itemKey, onInteract);
                                                                                    processResult(context, result, itemKey, "item");
                                                                                    return result;
                                                                                })
                                                                )
                                                )
                                )
                                .then(
                                        Commands.literal("block")
                                                .then(
                                                        Commands.argument("key", ArgumentTypes.namespacedKey())
                                                                .executes(context -> {
                                                                    NamespacedKey blockKey = context.getArgument("key", NamespacedKey.class);

                                                                    int result = registerBlock(blockKey, null, null, null);
                                                                    processResult(context, result, blockKey, "block");
                                                                    return result;
                                                                })
                                                                .then(
                                                                        Commands.argument("onPlace", ArgumentTypes.namespacedKey())
                                                                                .executes(context -> {
                                                                                    NamespacedKey blockKey = context.getArgument("key", NamespacedKey.class);
                                                                                    NamespacedKey onPlace = context.getArgument("onPlace", NamespacedKey.class);

                                                                                    int result = registerBlock(blockKey, onPlace, null, null);
                                                                                    processResult(context, result, blockKey, "block");
                                                                                    return result;
                                                                                })
                                                                                .then(
                                                                                        Commands.argument("onBreak", ArgumentTypes.namespacedKey())
                                                                                                .executes(context -> {
                                                                                                    NamespacedKey blockKey = context.getArgument("key", NamespacedKey.class);
                                                                                                    NamespacedKey onPlace = context.getArgument("onPlace", NamespacedKey.class);
                                                                                                    NamespacedKey onBreak = context.getArgument("onBreak", NamespacedKey.class);

                                                                                                    int result = registerBlock(blockKey, onPlace, onBreak, null);
                                                                                                    processResult(context, result, blockKey, "block");
                                                                                                    return result;
                                                                                                })
                                                                                                .then(
                                                                                                        Commands.argument("onInteract", ArgumentTypes.namespacedKey())
                                                                                                                .executes(context -> {
                                                                                                                    NamespacedKey blockKey = context.getArgument("key", NamespacedKey.class);
                                                                                                                    NamespacedKey onPlace = context.getArgument("onPlace", NamespacedKey.class);
                                                                                                                    NamespacedKey onBreak = context.getArgument("onBreak", NamespacedKey.class);
                                                                                                                    NamespacedKey onInteract = context.getArgument("onInteract", NamespacedKey.class);

                                                                                                                    int result = registerBlock(blockKey, onPlace, onBreak, onInteract);
                                                                                                                    processResult(context, result, blockKey, "block");
                                                                                                                    return result;
                                                                                                                })
                                                                                                )
                                                                                )
                                                                )
                                                )
                                )
                )
                .build();
    }

    private void processResult(CommandContext<CommandSourceStack> context, int result, NamespacedKey key, String type) {
        if (result == 1) {
            context.getSource().getSender().sendRichMessage("<green>Registered " + type + ": " + key.toString());
        } else {
            context.getSource().getSender().sendRichMessage("<red>" + Character.toUpperCase(type.charAt(0)) + type.substring(1) + " already registered: " + key.toString());
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
            newItem.onInteract(event -> dispatchFunction(event.getPlayer(), onInteract));
        }

        newItem.register();

        return 1;
    }

    private int registerBlock(NamespacedKey blockKey, @Nullable NamespacedKey onPlace, @Nullable NamespacedKey onBreak, @Nullable NamespacedKey onInteract) {
        GenericBlock block = Write.BLOCK_REGISTRY.stream()
                .filter(b -> b.key().toString().equals(blockKey.toString()))
                .findFirst()
                .orElse(null);

        if (block != null) {
            return 0;
        }

        GenericBlock newBlock = new GenericBlock(blockKey);

        if (onPlace != null) {
            newBlock.onPlace(event -> dispatchFunction(event.getPlayer(), onPlace));
        }

        if (onBreak != null) {
            newBlock.onPlayerBreak(event -> dispatchFunction(event.getPlayer(), onBreak));
        }

        if (onInteract != null) {
            newBlock.onInteract(event -> dispatchFunction(event.getPlayer(), onInteract));
        }

        newBlock.register();

        return 1;
    }

    private void dispatchFunction(Player player, NamespacedKey functionKey) {
        Bukkit.dispatchCommand(player, "function " + functionKey.getNamespace() + ":" + functionKey.getKey());
    }
}
