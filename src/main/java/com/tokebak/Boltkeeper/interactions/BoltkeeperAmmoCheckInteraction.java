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

import javax.annotation.Nonnull;

/**
 * Custom interaction that checks for arrows in hotbar, storage, AND backpack.
 * This overrides vanilla behavior which only checks hotbar and storage.
 * 
 * Used during crossbow reload to verify arrows are available.
 */
public class BoltkeeperAmmoCheckInteraction extends SimpleInstantInteraction {
    
    public static final BuilderCodec<BoltkeeperAmmoCheckInteraction> CODEC = BuilderCodec.builder(
            BoltkeeperAmmoCheckInteraction.class,
            BoltkeeperAmmoCheckInteraction::new,
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
            System.out.println("[BOLTKEEPER] Ammo check: No player component");
            context.getState().state = InteractionState.Failed;
            return;
        }
        
        final Inventory inventory = player.getInventory();
        if (inventory == null) {
            System.out.println("[BOLTKEEPER] Ammo check: No inventory");
            context.getState().state = InteractionState.Failed;
            return;
        }
        
        // Check if we have any arrows across all containers (hotbar, storage, backpack)
        int available = 0;
        available += this.countArrowsInContainer(inventory.getHotbar());
        available += this.countArrowsInContainer(inventory.getStorage());
        available += this.countArrowsInContainer(inventory.getBackpack());
        
        if (available > 0) {
            // Have arrows, proceed with loading
            System.out.println("[BOLTKEEPER] Ammo check passed: found " + available + " arrows");
        } else {
            // No arrows anywhere
            System.out.println("[BOLTKEEPER] Ammo check failed: no arrows found");
            context.getState().state = InteractionState.Failed;
        }
    }
    
    /**
     * Count how many arrows exist in a container.
     */
    private int countArrowsInContainer(@Nonnull final ItemContainer container) {
        int count = 0;
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            final ItemStack item = container.getItemStack(slot);
            if (item == null || item.isEmpty()) {
                continue;
            }
            final String id = item.getItem().getId();
            if (id != null && id.contains("Arrow")) {
                count += item.getQuantity();
            }
        }
        return count;
    }
}
