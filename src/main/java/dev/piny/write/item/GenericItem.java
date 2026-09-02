package dev.piny.write.item;

import dev.piny.write.Write;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * A representation of a generic custom item, configured using a fluent builder API, e.g.:
 * <pre>{@code
 * GenericItem item = new GenericItem(key)
 *         .name(Component.translatable("item." + key.getNamespace() + "." + key.getKey()))
 *         .maxStackSize(64)
 *         .modify(itemStack -> itemStack)
 *         .onInteract(event -> {})
 *         .build();
 * }</pre>
 */
public class GenericItem {
    private final NamespacedKey key;
    private Component name;
    private int maxStackSize = 64;
    private UnaryOperator<ItemStack> modify = UnaryOperator.identity();
    private Consumer<PlayerInteractEvent> onInteract = event -> {};

    /**
     * @param key The unique ID for the item
     */
    public GenericItem(NamespacedKey key) {
        this.key = key;
        boolean isBlock = Write.BLOCK_REGISTRY.stream().anyMatch(block -> block.key().equals(key));
        this.name = Component.translatable((isBlock ? "block." : "item.") + key.getNamespace() + "." + key.getKey());
    }

    /**
     * Sets the item's display name. Defaults to {@code <lang:type.namespace.id>}.
     */
    public GenericItem name(Component name) {
        this.name = name;
        return this;
    }

    /**
     * Sets the item's maximum stack size. Defaults to {@code 64}.
     */
    public GenericItem maxStackSize(int maxStackSize) {
        this.maxStackSize = maxStackSize;
        return this;
    }

    /**
     * Sets a function that modifies the initial ItemStack. Defaults to the identity function.
     */
    public GenericItem modify(UnaryOperator<ItemStack> modify) {
        this.modify = modify;
        return this;
    }

    /**
     * Sets the handler invoked when a player interacts with this item. Defaults to a no-op.
     */
    public GenericItem onInteract(Consumer<PlayerInteractEvent> onInteract) {
        this.onInteract = onInteract;
        return this;
    }

    public NamespacedKey key() {
        return key;
    }

    public Component name() {
        return name;
    }

    public int maxStackSize() {
        return maxStackSize;
    }

    public UnaryOperator<ItemStack> modify() {
        return modify;
    }

    public Consumer<PlayerInteractEvent> onInteract() {
        return onInteract;
    }

    public ItemStack getItemStack() {
        boolean isBlock = Write.BLOCK_REGISTRY.stream()
                .anyMatch(block -> block.key().equals(key));

        ItemStack itemStack = Bukkit.getServer().getItemFactory().createItemStack((isBlock ? "structure_void" : "popped_chorus_fruit") + "[item_model=\"" + key.getNamespace() + ":" + (isBlock ? "block/" : "") + key.getKey() + "\",max_stack_size=" + maxStackSize + ",rarity=\"common\"]");
        itemStack.editMeta(meta -> meta.itemName(name));

        itemStack.setData(DataComponentTypes.CUSTOM_MODEL_DATA, CustomModelData.customModelData()
                        .addString(key.getNamespace() + ":" + (isBlock ? "block" : "item") + "/" + key.getKey())
                .build());

        return modify.apply(itemStack);
    }

    public void register() {
        String pluginName = key.getNamespace();

        if (Bukkit.getPluginManager().getPlugin(pluginName) == null) {
            for (StackTraceElement stackTraceElement : Thread.currentThread().getStackTrace()) {
                if (Bukkit.getPluginManager().getPlugin(stackTraceElement.getClassName()) != null) {
                    pluginName = Objects.requireNonNull(Bukkit.getPluginManager().getPlugin(stackTraceElement.getClassName())).getName();
                    break;
                }
            }
        }

        Bukkit.getLogger().info("[Write/" + pluginName + "] Registered item: " + key + " with name: " + name + " and max stack size: " + maxStackSize);

        Write.ITEM_REGISTRY.add(this);
    }

    public static GenericItem getInstance(@NotNull ItemStack itemStack) {
        if (itemStack.getItemMeta() == null || itemStack.getItemMeta().getItemModel() == null) return null;

        NamespacedKey itemModel = itemStack.getItemMeta().getItemModel();
        for (GenericItem genericItem : Write.ITEM_REGISTRY) {
            if (genericItem.key().equals(itemModel)) {
                return genericItem;
            }
        }

        return null;
    }

    /**
     * Invokes the configured {@link #onInteract(Consumer)} handler for this item.
     */
    public void handleInteract(PlayerInteractEvent event) {
        onInteract.accept(event);
    }
}
