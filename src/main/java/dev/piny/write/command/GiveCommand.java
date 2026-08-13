package dev.piny.write.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.piny.write.Write;
import dev.piny.write.item.GenericItem;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class GiveCommand {
    public LiteralCommandNode<CommandSourceStack> create() {
        return Commands.literal("give")
                .requires(source -> source.getSender().hasPermission("write.give"))
                .then(
                        Commands.argument("targets", ArgumentTypes.players())
                                .then(
                                        Commands.argument("item", ArgumentTypes.namespacedKey())
                                                .suggests((context, builder) -> {
                                                    // Suggest all items in the registry
                                                    for (GenericItem item : Write.ITEM_REGISTRY) {
                                                        if (item.key().toString().contains(context.getInput().substring(context.getInput().lastIndexOf(' ') + 1))) {
                                                            builder.suggest(item.key().toString());
                                                        }
                                                    }

                                                    return builder.buildFuture();
                                                })
                                                .executes(context -> giveItem(context, 1))
                                                .then(
                                                        Commands.argument("count", IntegerArgumentType.integer(1))
                                                                .executes(context -> giveItem(context, IntegerArgumentType.getInteger(context, "count")))
                                                )
                                )
                )
                .build();
    }

    private int giveItem(CommandContext<CommandSourceStack> context, int count) throws CommandSyntaxException {
        final PlayerSelectorArgumentResolver targetResolver = context.getArgument("targets", PlayerSelectorArgumentResolver.class);
        final List<Player> targets = targetResolver.resolve(context.getSource());
        NamespacedKey itemKey = context.getArgument("item", NamespacedKey.class);
        ItemStack itemStack = Write.getItemStack(itemKey.toString());

        for (Player target : targets) {
            target.give(itemStack.asQuantity(count));
        }

        return 1;
    }
}
