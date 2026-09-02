package dev.piny.write.event;

import dev.piny.write.block.GenericBlock;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jspecify.annotations.NonNull;

public class BlockRegisterEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    private final GenericBlock block;

    public BlockRegisterEvent(GenericBlock block) {
        this.block = block;
    }

    public GenericBlock getBlock() {
        return this.block;
    }

    @Override
    public @NonNull HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
