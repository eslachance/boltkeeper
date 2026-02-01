package com.tokebak.Boltkeeper.interactions;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
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
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackSlotTransaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.protocol.WaitForDataFrom;

import javax.annotation.Nonnull;

/**
 * Custom interaction that consumes Fire Essence from hotbar, storage, AND backpack.
 * This overrides vanilla behavior which only consumes from hotbar and storage.
 * 
 * Also supports durability adjustment on the held item (same as ModifyInventory).
 * 
 * Used during Fire Staff attacks to consume Fire Essence.
 * Priority: hotbar first, then storage, then backpack.
 */
public class BoltkeeperEssenceConsumeInteraction extends SimpleInstantInteraction {
    
    private static final String FIRE_ESSENCE_ID = "Ingredient_Fire_Essence";
    
    private double adjustHeldItemDurability;
    
    public static final BuilderCodec<BoltkeeperEssenceConsumeInteraction> CODEC = ((BuilderCodec.Builder<BoltkeeperEssenceConsumeInteraction>)
            BuilderCodec.builder(
                    BoltkeeperEssenceConsumeInteraction.class,
                    BoltkeeperEssenceConsumeInteraction::new,
                    SimpleInstantInteraction.CODEC
            ).addField(
                    new KeyedCodec<>("AdjustHeldItemDurability", Codec.DOUBLE),
                    (interaction, value) -> interaction.adjustHeldItemDurability = value,
                    (interaction) -> interaction.adjustHeldItemDurability
            )
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
        
        // Try to consume 1 Fire Essence from hotbar first, then storage, then backpack
        boolean consumed = this.consumeEssenceFromContainer(inventory.getHotbar());
        if (!consumed) {
            consumed = this.consumeEssenceFromContainer(inventory.getStorage());
        }
        if (!consumed) {
            consumed = this.consumeEssenceFromContainer(inventory.getBackpack());
        }
        
        if (!consumed) {
            // Couldn't consume Fire Essence
            context.getState().state = InteractionState.Failed;
            return;
        }
        
        // Apply durability adjustment to held item if specified
        if (this.adjustHeldItemDurability != 0.0) {
            final ItemStack heldItem = context.getHeldItem();
            if (heldItem != null) {
                final ItemStack newItem = heldItem.withIncreasedDurability(this.adjustHeldItemDurability);
                final ItemStackSlotTransaction slotTransaction = context.getHeldItemContainer()
                        .setItemStackForSlot((short) context.getHeldItemSlot(), newItem);
                if (slotTransaction.succeeded()) {
                    context.setHeldItem(newItem);
                }
            }
        }
        // If consumed, interaction succeeds (default state)
    }
    
    /**
     * Try to consume one Fire Essence from a container.
     * Returns true if Fire Essence was consumed.
     */
    private boolean consumeEssenceFromContainer(@Nonnull final ItemContainer container) {
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            final ItemStack item = container.getItemStack(slot);
            if (item == null || item.isEmpty()) {
                continue;
            }
            
            final String id = item.getItem().getId();
            if (!FIRE_ESSENCE_ID.equals(id)) {
                continue;
            }
            
            // Found Fire Essence - consume it
            container.removeItemStackFromSlot(slot, 1);
            return true;
        }
        return false;
    }
}
