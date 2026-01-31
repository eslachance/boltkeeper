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
import com.tokebak.Boltkeeper.BoltkeeperSystem;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Custom interaction that consumes arrows from hotbar, storage, AND backpack.
 * This overrides vanilla behavior which only consumes from hotbar and storage.
 * 
 * Used when firing a crossbow to consume the loaded arrow.
 * Priority: hotbar first, then storage, then backpack.
 */
public class BoltkeeperAmmoConsumeInteraction extends SimpleInstantInteraction {
    
    public static final BuilderCodec<BoltkeeperAmmoConsumeInteraction> CODEC = BuilderCodec.builder(
            BoltkeeperAmmoConsumeInteraction.class,
            BoltkeeperAmmoConsumeInteraction::new,
            SimpleInstantInteraction.CODEC
    ).build();
    
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
            System.out.println("[BOLTKEEPER] Ammo consume: No player component");
            context.getState().state = InteractionState.Failed;
            return;
        }
        
        final Inventory inventory = player.getInventory();
        if (inventory == null) {
            System.out.println("[BOLTKEEPER] Ammo consume: No inventory");
            context.getState().state = InteractionState.Failed;
            return;
        }
        
        @SuppressWarnings("deprecation")
        final UUID playerUuid = player.getUuid();
        
        // Try to consume 1 arrow from hotbar first, then storage, then backpack
        // Track the source so we can return arrows there on swap-out
        ConsumeResult result = this.consumeArrowsFromContainer(inventory.getHotbar(), 1, "hotbar");
        if (result.remaining > 0) {
            result = this.consumeArrowsFromContainer(inventory.getStorage(), result.remaining, "storage");
        }
        if (result.remaining > 0) {
            result = this.consumeArrowsFromContainer(inventory.getBackpack(), result.remaining, "backpack");
        }
        
        if (result.remaining > 0) {
            // Couldn't consume an arrow
            System.out.println("[BOLTKEEPER] Ammo consume failed: no arrows to consume");
            context.getState().state = InteractionState.Failed;
        } else {
            System.out.println("[BOLTKEEPER] Ammo consume succeeded");
            
            // Record the arrow source for later return on swap-out
            if (result.sourceContainer != null && playerUuid != null) {
                BoltkeeperSystem.recordArrowSource(
                        playerUuid,
                        result.sourceContainer,
                        result.sourceSlot,
                        result.sourceItemId
                );
            }
        }
    }
    
    /**
     * Result of consuming arrows, including source tracking.
     */
    private record ConsumeResult(int remaining, String sourceContainer, short sourceSlot, String sourceItemId) {}
    
    /**
     * Consume arrows from a container.
     * Returns a ConsumeResult with remaining count and source info if consumed.
     */
    private ConsumeResult consumeArrowsFromContainer(
            @Nonnull final ItemContainer container,
            int remaining,
            @Nonnull final String containerName
    ) {
        for (short slot = 0; slot < container.getCapacity() && remaining > 0; slot++) {
            final ItemStack item = container.getItemStack(slot);
            if (item == null || item.isEmpty()) {
                continue;
            }
            
            final String id = item.getItem().getId();
            if (id == null || !id.contains("Arrow")) {
                continue;
            }
            
            final int available = item.getQuantity();
            final int toRemove = Math.min(available, remaining);
            
            System.out.println("[BOLTKEEPER] Consuming " + toRemove + " arrow from " + containerName + " slot " + slot);
            
            container.removeItemStackFromSlot(slot, toRemove);
            remaining -= toRemove;
            
            // Return result with source info (first successful consume)
            return new ConsumeResult(remaining, containerName, slot, id);
        }
        
        // No arrows consumed from this container
        return new ConsumeResult(remaining, null, (short) -1, null);
    }
}
