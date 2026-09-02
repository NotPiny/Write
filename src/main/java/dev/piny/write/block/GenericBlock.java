package dev.piny.write.block;

import dev.piny.write.Write;
import dev.piny.write.event.BlockRegisterEvent;
import dev.piny.write.item.GenericItem;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Rotatable;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.ApiStatus;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

public class GenericBlock {
    /**
     * Indicates the cause of a block instance being broken/destroyed, passed to {@link GenericBlock#onBreak(BiConsumer)}.
     */
    public enum BreakReason {
        /** The block was broken by a player, via {@link BlockBreakEvent}. */
        PLAYER,
        /** The block was destroyed by an explosion caused by a block (e.g. a bed, or respawn anchor), via {@link BlockExplodeEvent}. */
        BLOCK_EXPLODE,
        /** The block was destroyed by an explosion caused by an entity (e.g. a creeper, TNT, or any other sort of explosive entity), via {@link EntityExplodeEvent}. */
        ENTITY_EXPLODE
    }

    private final NamespacedKey key;
    private final GenericItem item;
    private BiPredicate<PlayerInteractEvent, Boolean> canPlace;
    private Consumer<PlayerInteractEvent> onPlace;
    private Consumer<PlayerInteractEvent> onInteract;
    private Material baseMaterial = Material.COBBLESTONE;
    private Consumer<BlockBreakEvent> onPlayerBreak;
    private BiConsumer<BlockExplodeEvent, GenericBlockInstanceData> onBlockExplodeBreak;
    private BiConsumer<EntityExplodeEvent, GenericBlockInstanceData> onEntityExplodeBreak;
    private BiConsumer<GenericBlockInstanceData, BreakReason> onBreak;
    private boolean overrideVanillaBlockDrops = true;
    private boolean rotatable = true;
    private final HashMap<UUID, Long> lastInteractTimes = new HashMap<>();

    @ApiStatus.Internal
    @Nullable
    public static GenericBlockInstance createInstanceFromDisplay(ItemDisplay itemDisplay) {
        for (GenericBlockInstance instance : Write.BLOCK_INSTANCES) {
            if (instance.itemDisplayId().equals(itemDisplay.getUniqueId())) {
                return instance;
            }
        }

        if (itemDisplay.getItemStack().getItemMeta() == null || itemDisplay.getItemStack().getItemMeta().getItemModel() == null) {
            return null;
        }

        NamespacedKey key = itemDisplay.getItemStack().getItemMeta().getItemModel();
        NamespacedKey blockKey = new NamespacedKey(key.getNamespace(), key.getKey().replace("block/", ""));
        GenericBlock matchedBlock = Write.BLOCK_REGISTRY.stream()
                .filter(block -> block.key().equals(blockKey))
                .findFirst()
                .orElse(null);

        if (matchedBlock == null) return null;

        GenericBlockInstance newInstance = new GenericBlockInstance(matchedBlock, itemDisplay.getLocation(), itemDisplay);
        Write.BLOCK_INSTANCES.add(newInstance);
        return newInstance;
    }

    private boolean isPlaceable(PlayerInteractEvent event) {
        boolean initial = true;

        Location playerLocation = event.getPlayer().getLocation().clone();
        assert event.getClickedBlock() != null;
        Location blockLocation = event.getClickedBlock().getLocation().clone().add(event.getBlockFace().getDirection());

        // I know this check is flawed when dealing with non-full blocks and players standing on the edge, but it's good enough for now.
        Location normalisedPlayerLocation = new Location(playerLocation.getWorld(), Math.floor(playerLocation.getX()), Math.floor(playerLocation.getY()), Math.floor(playerLocation.getZ()));
        Location normalisedBlockLocation = new Location(blockLocation.getWorld(), Math.floor(blockLocation.getX()), Math.floor(blockLocation.getY()), Math.floor(blockLocation.getZ()));

        if (normalisedPlayerLocation.equals(normalisedBlockLocation)) return false;

        if (this.canPlace == null) return initial;
        return this.canPlace.test(event, initial);
    }

    public GenericBlock(NamespacedKey key) {
        this.key = key;
        this.item = new GenericItem(key);

        Consumer<PlayerInteractEvent> onInteract = this.item.onInteract();

        this.item.onInteract(event -> {
            if (!event.getAction().isRightClick()) return;
            // Bukkit fires a PlayerInteractEvent for both the main hand and the off hand on a
            // single right click, so only handle the main hand event to avoid placing twice.
            if (event.getHand() != EquipmentSlot.HAND) return;

            // I think this is a remnant from when blocks were also popped_chorus_fruit, but now they're all structure_void, so this check is mostly redundant.
            // Guess it still helps for modded clients.
            UUID playerId = event.getPlayer().getUniqueId();
            long currentTime = System.currentTimeMillis();
            long lastInteractTime = lastInteractTimes.getOrDefault(playerId, 0L);
            if (currentTime - lastInteractTime < Write.getInstance().getConfig().getLong("cooldowns.block.place_delay_ms")) return;
            lastInteractTimes.put(playerId, currentTime);

            Block block = event.getClickedBlock();
            if (block == null) return;
            Vector direction = event.getBlockFace().getDirection();

            Location blockLocation = block.getLocation().add(direction);

            if (!isPlaceable(event)) return;

            ItemDisplay itemDisplay = blockLocation.getWorld().spawn(blockLocation.add(0.5, 0.5, 0.5), ItemDisplay.class);
            itemDisplay.setItemStack(Bukkit.getServer().getItemFactory().createItemStack("structure_void[item_model=\""+ this.key.getNamespace() + ":block/" + this.key.getKey() + "\"]"));

            float rotationYaw = 0f;
            if (this.rotatable) {
                // Snap the player's current facing yaw to the nearest 90° increment, like a stair would.
                float rawYaw = ((event.getPlayer().getLocation().getYaw() % 360f) + 360f) % 360f;
                rotationYaw = (Math.round(rawYaw / 90f) * 90f) % 360f;
            }

            itemDisplay.setTransformation(
                    new Transformation(
                            new Vector3f(0, 0, 0),   // Translation
                            new AxisAngle4f((float) Math.toRadians(rotationYaw), 0, 1, 0), // Left Rotation
                            new Vector3f(1.0009f, 1.0009f, 1.0009f), // Scale (Size is important)
                            new AxisAngle4f()        // Right Rotation
                    )
            );

            Block newBlock = blockLocation.getBlock();
            itemDisplay.setBrightness(new Display.Brightness(newBlock.getLightFromBlocks(), newBlock.getLightFromSky()));

            if (this.rotatable) {
                // Vanilla directional blocks (furnaces, stairs, etc.) face towards the player who placed them,
                // i.e. the opposite of the direction the player was looking.
                BlockFace playerFacing = switch ((int) rotationYaw) {
                    case 90 -> BlockFace.WEST;
                    case 180 -> BlockFace.NORTH;
                    case 270 -> BlockFace.EAST;
                    default -> BlockFace.SOUTH;
                };
                BlockFace blockFacing = playerFacing.getOppositeFace();

                BlockData blockData = Bukkit.createBlockData(this.baseMaterial);
                if (blockData instanceof Directional directional && directional.getFaces().contains(blockFacing)) {
                    directional.setFacing(blockFacing);
                } else if (blockData instanceof Rotatable rotatableData) {
                    rotatableData.setRotation(blockFacing);
                }
                newBlock.setBlockData(blockData);
            } else {
                newBlock.setType(this.baseMaterial);
            }

            Write.BLOCK_INSTANCES.add(new GenericBlockInstance(this, blockLocation, itemDisplay));

            assert event.getItem() != null;
            if (!event.getPlayer().getGameMode().equals(GameMode.CREATIVE)) event.getPlayer().getInventory().getItemInMainHand().setAmount(event.getItem().getAmount() - 1);

            onInteract.accept(event);

            if (this.onPlace != null) this.onPlace.accept(event);
        });
    }

    public void register() {
        Write.BLOCK_REGISTRY.add(this);
        this.item.name(Component.translatable("block." + this.key.getNamespace() + "." + this.key.getKey()));
        this.item.register();

        Bukkit.getServer().getPluginManager().callEvent(new BlockRegisterEvent(this));
    }

    public NamespacedKey key() {
        return key;
    }

    public GenericItem item() {
        return item;
    }

    /**
     * The consumer that is called when a player interacts with the block. This consumer is set by the {@link GenericBlock#onInteract(Consumer)} method. If no consumer is set, the default consumer does nothing.
     * @return Returns the consumer that is called when a player interacts with the block.
     */
    public Consumer<PlayerInteractEvent> onInteract() {
        return onInteract;
    }

    /**
     * Sets the consumer that is called when a player interacts with the block.
     * <p>
     * <i>Note:</i> This method does not do bouncing, you will need to implement your own cooldown logic if duplicates are a concern. It will also trigger even if the block is being broken.
     * @param onInteract The consumer that is called when a player interacts with the block.
     */
    public GenericBlock onInteract(Consumer<PlayerInteractEvent> onInteract) {
        this.onInteract = onInteract;
        return this;
    }

    /**
     * The consumer that is called when a player breaks the block. This consumer is set by the {@link GenericBlock#onPlayerBreak(Consumer)} method. If no consumer is set, only default behaviour applies.
     * @return Returns the consumer that is called when a player breaks the block.
     */
    public Consumer<BlockBreakEvent> onPlayerBreak() {
        return onPlayerBreak;
    }

    /**
     * Sets the consumer that is called when a player breaks the block.
     * @param onPlayerBreak The consumer that is called when a player breaks the block.
     */
    public GenericBlock onPlayerBreak(Consumer<BlockBreakEvent> onPlayerBreak) {
        this.onPlayerBreak = onPlayerBreak;
        return this;
    }

    /**
     * The consumer that is called when the block is destroyed by a block-caused explosion (e.g. a bed, respawn
     * anchor, or TNT). This consumer is set by the {@link GenericBlock#onBlockExplodeBreak(BiConsumer)} method.
     * If no consumer is set, only default behaviour applies.
     * @return Returns the consumer that is called when the block is destroyed by a block-caused explosion.
     */
    public BiConsumer<BlockExplodeEvent, GenericBlockInstanceData> onBlockExplodeBreak() {
        return onBlockExplodeBreak;
    }

    /**
     * Sets the consumer that is called when the block is destroyed by a block-caused explosion (e.g. a bed,
     * respawn anchor, or TNT).
     * @param onBlockExplodeBreak The consumer that is called when the block is destroyed by a block-caused explosion.
     */
    public GenericBlock onBlockExplodeBreak(BiConsumer<BlockExplodeEvent, GenericBlockInstanceData> onBlockExplodeBreak) {
        this.onBlockExplodeBreak = onBlockExplodeBreak;
        return this;
    }

    /**
     * The consumer that is called when the block is destroyed by an entity-caused explosion (e.g. a creeper or
     * TNT minecart). This consumer is set by the {@link GenericBlock#onEntityExplodeBreak(BiConsumer)} method.
     * If no consumer is set, only default behaviour applies.
     * @return Returns the consumer that is called when the block is destroyed by an entity-caused explosion.
     */
    public BiConsumer<EntityExplodeEvent, GenericBlockInstanceData> onEntityExplodeBreak() {
        return onEntityExplodeBreak;
    }

    /**
     * Sets the consumer that is called when the block is destroyed by an entity-caused explosion (e.g. a creeper
     * or TNT minecart).
     * @param onEntityExplodeBreak The consumer that is called when the block is destroyed by an entity-caused explosion.
     */
    public GenericBlock onEntityExplodeBreak(BiConsumer<EntityExplodeEvent, GenericBlockInstanceData> onEntityExplodeBreak) {
        this.onEntityExplodeBreak = onEntityExplodeBreak;
        return this;
    }

    /**
     * The consumer that is called whenever the block is broken/destroyed, regardless of the {@link BreakReason}.
     * This consumer is set by the {@link GenericBlock#onBreak(BiConsumer)} method. If no consumer is set, only
     * default behaviour applies.
     * @return Returns the consumer that is called whenever the block is broken/destroyed.
     */
    public BiConsumer<GenericBlockInstanceData, BreakReason> onBreak() {
        return onBreak;
    }

    /**
     * Sets the consumer that is called whenever the block is broken/destroyed, regardless of the {@link BreakReason}.
     * <p>
     * <i>Note:</i> Unlike {@link GenericBlock#onPlayerBreak(Consumer)}, {@link GenericBlock#onBlockExplodeBreak(BiConsumer)},
     * and {@link GenericBlock#onEntityExplodeBreak(BiConsumer)}, this doesn't provide the underlying Bukkit event, just
     * a {@link GenericBlockInstanceData} snapshot of the instance (from which a {@link Location} can be reconstructed
     * via {@link GenericBlockInstanceData#toLocation()}) and the {@link BreakReason}.
     * @param onBreak The consumer that is called whenever the block is broken/destroyed.
     */
    public GenericBlock onBreak(BiConsumer<GenericBlockInstanceData, BreakReason> onBreak) {
        this.onBreak = onBreak;
        return this;
    }

    /**
     * The consumer that is called when a player places the block. This consumer is set by the {@link GenericBlock#onPlace(Consumer)} method. If no consumer is set, only default behaviour applies.
     * @return Returns the consumer that is called when a player places the block.
     */
    public Consumer<PlayerInteractEvent> onPlace() {
        return onPlace;
    }

    /**
     * Sets the consumer that is called when a player places the block.
     * @param onPlace The consumer that is called when a player places the block.
     */
    public GenericBlock onPlace(Consumer<PlayerInteractEvent> onPlace) {
        this.onPlace = onPlace;
        return this;
    }

    /**
     * Gets whether the block should override the default vanilla block break behaviour. If this is set to true, the event will be cancelled and Write will spawn drops.
     * @return Returns whether the block should override the default vanilla block break behaviour.
     */
    public boolean overrideVanillaBlockDrops() {
        return overrideVanillaBlockDrops;
    }
    
    /**
     * Sets whether the block should override the default vanilla block break behaviour. If this is set to true, the event will be cancelled and Write will spawn drops.
     * @param overrideVanillaBlockDrops Whether the block should override the default vanilla block break behaviour.
     */
    public GenericBlock overrideVanillaBlockDrops(boolean overrideVanillaBlockDrops) {
        this.overrideVanillaBlockDrops = overrideVanillaBlockDrops;
        return this;
    }

    /**
     * Gets whether the block can be rotated when placed. If {@code true} (the default), the block's
     * {@link ItemDisplay} will be rotated to face the direction the player was looking when they placed it,
     * snapped to the nearest 90° increment (like a stair).
     * @return Returns whether the block is rotatable.
     */
    public boolean rotatable() {
        return rotatable;
    }

    /**
     * Sets whether the block can be rotated when placed. If {@code true} (the default), the block's
     * {@link ItemDisplay} will be rotated to face the direction the player was looking when they placed it,
     * snapped to the nearest 90° increment (like a stair). Set to {@code false} to always place the block
     * with no rotation.
     * @param rotatable Whether the block is rotatable.
     */
    public GenericBlock rotatable(boolean rotatable) {
        this.rotatable = rotatable;
        return this;
    }

    /**
     * Gets the base material of the block. This is the material that is used to create the block when it is placed in the world. The default value is {@link Material#COBBLESTONE}.
     * @return Returns the base material of the block.
     */
    public Material baseMaterial() {
        return baseMaterial;
    }

    /**
     * Sets the base material of the block. This is the material that is used to create the block when it is placed in the world. The default value is {@link Material#COBBLESTONE}.
     * @param baseMaterial The base material of the block.
     */
    public GenericBlock baseMaterial(Material baseMaterial) {
        this.baseMaterial = baseMaterial;
        return this;
    }

    /**
     * Gets the block instance at the specified location.
     * @param location The location to get the block instance at.
     * @return Returns the block instance at the specified location, or null if no block instance is found. (Instances may be null if in unloaded chunks)
     */
    @Nullable
    public static GenericBlockInstance getBlockInstanceAt(Location location) {
        return Write.BLOCK_INSTANCES.stream()
                .filter(instance -> instance.location().equals(location.clone().add(0.5, 0.5, 0.5)))
                .findAny()
                .orElse(null);
    }
}
