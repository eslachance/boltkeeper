package com.tokebak.Boltkeeper.interactions;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.protocol.WaitForDataFrom;

import javax.annotation.Nonnull;

/**
 * Custom interaction that consumes arrows from hotbar, storage, AND backpack.
 * This overrides vanilla behavior which only consumes from hotbar and storage.
 * 
 * Used during crossbow reload to consume arrows.
 * Priority: hotbar first, then storage, then backpack.
 */
public class BoltkeeperAmmoConsumeInteraction extends SimpleInstantInteraction {
    
    public static final BuilderCodec<BoltkeeperAmmoConsumeInteraction> CODEC = BuilderCodec.builder(
            BoltkeeperAmmoConsumeInteraction.class,
            BoltkeeperAmmoConsumeInteraction::new,
            SimpleInstantInteraction.CODEC
    ).build();
    
    @Nonnull
    @Override
    public WaitForDataFrom getWaitForDataFrom() {
        return WaitForDataFrom.Server;
    }
    
    @Override
    protected void firstRun(
            @Nonnull final InteractionType interactionType,
            @Nonnull final InteractionContext context,
            @Nonnull final CooldownHandler cooldownHandler
    ) {
        final CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
        final Ref<EntityStore> ref = context.getEntity();
        
        final Player player = commandBuffer.getComponent(ref, Player.getComponentType());
        if (player == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        
        final Inventory inventory = player.getInventory();
        if (inventory == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        
        // Try to consume 1 arrow from hotbar first, then storage, then backpack
        boolean consumed = this.consumeArrowFromContainer(inventory.getHotbar());
        if (!consumed) {
            consumed = this.consumeArrowFromContainer(inventory.getStorage());
        }
        if (!consumed) {
            consumed = this.consumeArrowFromContainer(inventory.getBackpack());
        }
        
        if (!consumed) {
            // Couldn't consume an arrow
            context.getState().state = InteractionState.Failed;
        }
        // If consumed, interaction succeeds (default state)
    }
    
    /**
     * Try to consume one arrow from a container.
     * Returns true if an arrow was consumed.
     */
    private boolean consumeArrowFromContainer(@Nonnull final ItemContainer container) {
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            final ItemStack item = container.getItemStack(slot);
            if (item == null || item.isEmpty()) {
                continue;
            }
            
            final String id = item.getItem().getId();
            if (id == null || !id.contains("Arrow")) {
                continue;
            }
            
            // Found an arrow - consume it
            container.removeItemStackFromSlot(slot, 1);
            return true;
        }
        return false;
    }
}
