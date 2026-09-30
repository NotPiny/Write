package dev.piny.write.command;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import dev.piny.write.Write;
import dev.piny.write.item.GenericItem;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import org.bukkit.NamespacedKey;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

public class GenerateCommand {
    record PolytoneCreativeTabAdditions(Object[] items) {}
    record PolytoneCreativeTab(Object icon, PolytoneCreativeTabAdditions[] additions, boolean create_new, Object name) {}

    private void log(CommandContext<CommandSourceStack> context, String message) {
        context.getSource().getSender().sendRichMessage(message);
    }

    public LiteralCommandNode<CommandSourceStack> create() {
        return Commands.literal("generate")
                .then(
                        Commands.literal("polytoneCreativeTab")
                                .then(
                                        Commands.literal("namespace")
                                                .then(
                                                        Commands.argument("namespace", StringArgumentType.word())
                                                                .suggests((context, builder) -> {
                                                                    for (GenericItem item : Write.ITEM_REGISTRY) {
                                                                        String namespace = item.key().getNamespace();
                                                                        if (namespace.contains(context.getInput().substring(context.getInput().lastIndexOf(' ') + 1))) {
                                                                            builder.suggest(namespace);
                                                                        }
                                                                    }

                                                                    return builder.buildFuture();
                                                                })
                                                                .executes(ctx -> processNamespace(ctx, null))
                                                                .then(
                                                                        Commands.argument("icon", ArgumentTypes.namespacedKey())
                                                                                .suggests((context, builder) -> {
                                                                                    for (GenericItem item : Write.ITEM_REGISTRY) {
                                                                                        if (item.key().toString().contains(context.getInput().substring(context.getInput().lastIndexOf(' ') + 1))) {
                                                                                            builder.suggest(item.key().toString());
                                                                                        }
                                                                                    }

                                                                                    return builder.buildFuture();
                                                                                })
                                                                                .executes(ctx -> processNamespace(ctx, ctx.getArgument("icon", NamespacedKey.class)))
                                                                )
                                                )
                                )
                )
                .then(
                        Commands.literal("serializeItemStack")
                                .then(
                                        Commands.argument("item", ArgumentTypes.namespacedKey())
                                                .suggests((context, builder) -> {
                                                    for (GenericItem item : Write.ITEM_REGISTRY) {
                                                        if (item.key().toString().contains(context.getInput().substring(context.getInput().lastIndexOf(' ') + 1))) {
                                                            builder.suggest(item.key().toString());
                                                        }
                                                    }

                                                    return builder.buildFuture();
                                                })
                                                .executes(ctx -> {
                                                    NamespacedKey itemKey = ctx.getArgument("item", NamespacedKey.class);
                                                    ItemStack itemStack = Write.getItemStack(itemKey.toString());
                                                    String serializedData = serializeItemStack(itemStack);
                                                    ctx.getSource().getSender().sendMessage(
                                                            Component.text("Serialized data for item: " + itemKey + " ").append(
                                                                    Component.text(serializedData)
                                                                            .color(NamedTextColor.GRAY)
                                                                            .append(
                                                                                    Component.text("\n[Copy]")
                                                                                            .hoverEvent(Component.text("Copy serialized data to clipboard"))
                                                                                            .clickEvent(ClickEvent.copyToClipboard(serializedData))
                                                                                            .color(NamedTextColor.DARK_GRAY)
                                                                            )
                                                            ).color(NamedTextColor.GREEN)
                                                    );
                                                    return 1;
                                                })
                                )
                )
                .build();
    }

    private int processNamespace(CommandContext<CommandSourceStack> ctx, @Nullable NamespacedKey icon) {
        String namespace = StringArgumentType.getString(ctx, "namespace");

        log(ctx, "<yellow>ℹ Generating polytone for namespace: " + namespace);

        ArrayList<Object> nmsItems = new ArrayList<>();
        for (GenericItem genericItem : Write.ITEM_REGISTRY) {
            log(ctx, "");
            log(ctx, "<gray>ℹ Generating polytone for item: " + genericItem.key().toString());
            String serializedData = serializeItemStack(genericItem.getItemStack());
            ctx.getSource().getSender().sendMessage(
                    Component.text("ℹ Serialized data for item: " + genericItem.key().toString() + " ").append(
                            Component.text("[Copy]")
                                    .hoverEvent(Component.text("Copy serialized data to clipboard"))
                                    .clickEvent(ClickEvent.copyToClipboard(serializedData))
                    ).color(NamedTextColor.GRAY)
            );
            log(ctx, "");
            nmsItems.add(new Gson().fromJson(serializedData, Object.class));
        }

        if (nmsItems.isEmpty()) {
            log(ctx, "<red>⚠ No items found in the registry for namespace: " + namespace);
            return 0;
        }

        PolytoneCreativeTab tab = new PolytoneCreativeTab(icon != null ? new Gson().fromJson(serializeItemStack(Write.getItemStack(icon.asString())), Object.class) : nmsItems.getFirst(), new PolytoneCreativeTabAdditions[]{new PolytoneCreativeTabAdditions(nmsItems.toArray())}, true, new Gson().fromJson("[{\"translate\":\"itemGroup.%s\"}]".formatted(namespace), Object.class));

        log(ctx, "<green>✅ Generated polytone creative tab: " + namespace);

        Path outputPath = Write.getInstance().getDataFolder().toPath().resolve("generated", "assets", namespace, "polytone", "creative_tab_modifiers", namespace + ".json");
        try {
            Files.createDirectories(outputPath.getParent());
        } catch (Exception e) {
            log(ctx, "<red>⚠ Failed to create directories for output path: " + e.getMessage());
            return 0;
        }

        try {
            Files.writeString(outputPath, new GsonBuilder().setPrettyPrinting().create().toJson(tab));
            log(ctx, "<green>✅ Successfully wrote polytone creative tab to: " + outputPath);
        } catch (Exception e) {
            log(ctx, "<red>⚠ Failed to write polytone creative tab to file: " + e.getMessage());
            return 0;
        }

        return 1;
    }

    private String serializeItemStack(ItemStack itemStack) {
        net.minecraft.world.item.ItemStack nmsItemStack = CraftItemStack.asNMSCopy(itemStack); // OH GOD HELP ME I DON'T KNOW WHAT I'M DOING IN THIS NMS STUFF

        RegistryAccess registryAccess = MinecraftServer.getServer().registryAccess();
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, registryAccess);

        DataResult<JsonElement> result = net.minecraft.world.item.ItemStack.CODEC.encodeStart(ops, nmsItemStack);
        JsonElement json = result.getOrThrow();

        return json.toString();
    }
}
