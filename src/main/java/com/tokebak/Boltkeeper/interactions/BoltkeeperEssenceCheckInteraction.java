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
 * Custom interaction that ONLY CHECKS for Fire Essence in hotbar, storage, AND backpack.
 * This overrides vanilla behavior which only checks hotbar and storage.
 * 
 * Used for Fire Staff condition checks - does NOT consume, just checks availability.
 */
public class BoltkeeperEssenceCheckInteraction extends SimpleInstantInteraction {
    
    private static final String FIRE_ESSENCE_ID = "Ingredient_Fire_Essence";
    
    public static final BuilderCodec<BoltkeeperEssenceCheckInteraction> CODEC = BuilderCodec.builder(
            BoltkeeperEssenceCheckInteraction.class,
            BoltkeeperEssenceCheckInteraction::new,
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
        
        // Check if we have any Fire Essence across all containers (hotbar, storage, backpack)
        // Do NOT consume - this is just a check
        boolean hasEssence = this.hasEssenceInContainer(inventory.getHotbar())
                || this.hasEssenceInContainer(inventory.getStorage())
                || this.hasEssenceInContainer(inventory.getBackpack());
        
        if (!hasEssence) {
            // No Fire Essence anywhere - fail the interaction
            context.getState().state = InteractionState.Failed;
        }
        // If essence found, interaction succeeds (default state)
    }
    
    /**
     * Check if a container has any Fire Essence.
     */
    private boolean hasEssenceInContainer(@Nonnull final ItemContainer container) {
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            final ItemStack item = container.getItemStack(slot);
            if (item == null || item.isEmpty()) {
                continue;
            }
            final String id = item.getItem().getId();
            if (FIRE_ESSENCE_ID.equals(id)) {
                return true;
            }
        }
        return false;
    }
}
