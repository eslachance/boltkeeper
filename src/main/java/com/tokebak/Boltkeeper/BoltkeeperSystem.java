package com.tokebak.Boltkeeper;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Boltkeeper System - Preserves crossbow ammo (loaded bolts) between weapon swaps.
 * 
 * By default, Hytale resets the Ammo stat when switching hotbar slots, and returns
 * arrows to the player's inventory. This system:
 * 1. Saves the loaded ammo count to item metadata when swapping away from crossbow
 * 2. Restores that exact amount when swapping back
 * 3. Consumes arrows from inventory to prevent duplication
 * 
 * Extends EntityTickingSystem to properly integrate with the ECS system.
 */
public class BoltkeeperSystem extends EntityTickingSystem<EntityStore> {
    
    /**
     * Metadata key for storing saved Ammo count on crossbow items.
     */
    public static final String META_KEY_SAVED_AMMO = "BK_SavedAmmo";
    
    /**
     * Represents the source location of consumed arrows.
     */
    private record ArrowSource(String containerType, short slot, String itemId, int count) {}
    
    /**
     * Result of consuming arrows, including source tracking.
     */
    private record ConsumeResult(int consumed, List<ArrowSource> sources) {}
    
    private final BoltkeeperConfig config;
    
    /**
     * Tracks the last known active hotbar slot per player UUID.
     */
    private final Map<UUID, Byte> lastActiveSlot = new ConcurrentHashMap<>();
    
    /**
     * Tracks the Ammo value from the PREVIOUS tick per player UUID.
     * This is crucial because by the time we detect a slot change, the game has
     * already reset the ammo. We need the value from BEFORE the reset.
     */
    private final Map<UUID, Float> previousTickAmmo = new ConcurrentHashMap<>();
    
    /**
     * Scheduler for delayed ammo restoration.
     */
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    
    /**
     * Tracks the arrow source for each player's currently loaded crossbow.
     * Key: player UUID, Value: arrow source info
     * This avoids updating the crossbow item after setting ammo (which resets ammo).
     */
    private final Map<UUID, ArrowSource> playerArrowSource = new ConcurrentHashMap<>();
    
    public BoltkeeperSystem(@Nonnull final BoltkeeperConfig config) {
        this.config = config;
    }
    
    /**
     * Helper to log debug messages only when debug mode is enabled.
     */
    private void debug(@Nonnull final String message) {
        if (this.config.isDebug()) {
            System.out.println("[BOLTKEEPER:DEBUG] " + message);
        }
    }
    
    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return Player.getComponentType();
    }
    
    @Override
    public void tick(
            final float dt,
            final int index,
            @Nonnull final ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull final Store<EntityStore> store,
            @Nonnull final CommandBuffer<EntityStore> commandBuffer
    ) {
        if (!this.config.isEnabled()) {
            return;
        }
        
        final Ref<EntityStore> entityRef = archetypeChunk.getReferenceTo(index);
        if (entityRef == null || !entityRef.isValid()) {
            return;
        }
        
        final Player player = (Player) archetypeChunk.getComponent(index, Player.getComponentType());
        if (player == null) {
            return;
        }
        
        final Inventory inventory = player.getInventory();
        if (inventory == null) {
            return;
        }
        
        final UUID playerUuid = player.getUuid();
        if (playerUuid == null) {
            return;
        }
        
        final byte currentSlot = inventory.getActiveHotbarSlot();
        
        // Read current Ammo (we track this every tick)
        final float currentAmmo = this.getAmmo(entityRef, store);
        
        // Get last known slot (or initialize if first time)
        final Byte lastSlotObj = this.lastActiveSlot.get(playerUuid);
        if (lastSlotObj == null) {
            this.lastActiveSlot.put(playerUuid, currentSlot);
            this.previousTickAmmo.put(playerUuid, currentAmmo);
            this.debug(String.format("Player first tick - initial slot: %d, ammo: %.0f", currentSlot, currentAmmo));
            return;
        }
        
        final byte previousSlot = lastSlotObj;
        
        // Check if slot changed
        if (currentSlot == previousSlot) {
            // No slot change - just update the tracked ammo for next tick
            this.previousTickAmmo.put(playerUuid, currentAmmo);
            return;
        }
        
        // Slot changed! Get the ammo from BEFORE the reset (previous tick's value)
        final Float ammoBeforeReset = this.previousTickAmmo.get(playerUuid);
        final float savedAmmo = ammoBeforeReset != null ? ammoBeforeReset : 0f;
        
        // Update tracking for next tick
        this.lastActiveSlot.put(playerUuid, currentSlot);
        this.previousTickAmmo.put(playerUuid, currentAmmo);
        
        this.debug(String.format("Hotbar slot change: %d -> %d (ammo before reset: %.0f)",
                previousSlot, currentSlot, savedAmmo));
        
        // Handle the slot change
        this.handleSlotChange(entityRef, store, inventory, previousSlot, currentSlot, savedAmmo, playerUuid);
    }
    
    private void handleSlotChange(
            @Nonnull final Ref<EntityStore> entityRef,
            @Nonnull final Store<EntityStore> store,
            @Nonnull final Inventory inventory,
            final byte previousSlot,
            final byte currentSlot,
            final float ammoBeforeReset,
            @Nonnull final UUID playerUuid
    ) {
        final ItemContainer hotbar = inventory.getHotbar();
        ItemStack oldItem = hotbar.getItemStack((short) previousSlot);
        final ItemStack newItem = hotbar.getItemStack((short) currentSlot);
        
        this.debug(String.format("Hotbar swap: slot %d -> %d | Ammo before reset: %.0f",
                previousSlot, currentSlot, ammoBeforeReset));
        this.debug(String.format("Old item: %s | New item: %s",
                oldItem != null ? oldItem.getItem().getId() : "null",
                newItem != null ? newItem.getItem().getId() : "null"));
        
        // Save ammo to the OLD item (if it's a crossbow with loaded ammo)
        final boolean oldIsCrossbow = this.isCrossbow(oldItem);
        this.debug(String.format("Old item is crossbow: %b", oldIsCrossbow));
        
        if (oldIsCrossbow && ammoBeforeReset > 0) {
            // First, return vanilla-dumped arrows to their original source (using map, not item metadata)
            final ArrowSource arrowSource = this.playerArrowSource.remove(playerUuid);
            if (arrowSource != null) {
                this.returnArrowsToSourceFromMap(inventory, arrowSource, (int) ammoBeforeReset);
            } else {
                this.debug("No arrow source in map - arrows will remain where vanilla placed them");
            }
            
            // Re-fetch the old item after potential inventory changes
            oldItem = hotbar.getItemStack((short) previousSlot);
            
            // Save ammo count to crossbow metadata
            final ItemStack updatedOldItem = this.saveAmmo(oldItem, ammoBeforeReset);
            hotbar.setItemStackForSlot((short) previousSlot, updatedOldItem);
            this.debug(String.format("SAVED ammo %.0f to crossbow in slot %d", ammoBeforeReset, previousSlot));
        } else if (oldIsCrossbow) {
            // Clear any stale arrow source when swapping away with no ammo
            this.playerArrowSource.remove(playerUuid);
            this.debug("Skipping save - ammo before reset is 0");
        }
        
        // Check if the NEW item is a crossbow with saved ammo to restore
        final boolean newIsCrossbow = this.isCrossbow(newItem);
        this.debug(String.format("New item is crossbow: %b", newIsCrossbow));
        
        if (newIsCrossbow) {
            final Float savedAmmo = this.getSavedAmmo(newItem);
            this.debug(String.format("Saved ammo in crossbow metadata: %s", savedAmmo));
            
            if (savedAmmo != null && savedAmmo > 0) {
                // Clear the saved ammo from the item's metadata BEFORE scheduling restore
                final ItemStack updatedNewItem = this.clearSavedAmmo(newItem);
                hotbar.setItemStackForSlot((short) currentSlot, updatedNewItem);
                this.debug("Cleared saved ammo from crossbow metadata");
                
                // Schedule the restore with a delay
                final World world = ((EntityStore) store.getExternalData()).getWorld();
                final float ammoToRestore = savedAmmo;
                final long delayMs = this.config.getRestoreDelayMs();
                
                this.debug(String.format("Scheduling restore of %.0f ammo in %dms", ammoToRestore, delayMs));
                
                this.scheduler.schedule(() -> {
                    this.debug(String.format("Scheduler fired - restoring %.0f ammo", ammoToRestore));
                    world.execute(() -> {
                        this.debug(String.format("world.execute() running - restoring %.0f ammo", ammoToRestore));
                        
                        // Consume arrows from inventory with source tracking
                        final ConsumeResult result = this.consumeArrowsWithTracking(inventory, (int) ammoToRestore);
                        this.debug(String.format("Consumed %d arrows from inventory", result.consumed()));
                        
                        // Only restore the amount of arrows we actually consumed
                        if (result.consumed() > 0) {
                            // IMPORTANT: Set ammo LAST, do NOT update item after this
                            // Store arrow source in map instead of item metadata to avoid resetting ammo
                            if (!result.sources().isEmpty()) {
                                final ArrowSource primarySource = result.sources().get(0);
                                this.playerArrowSource.put(playerUuid, primarySource);
                                this.debug(String.format("Saved arrow source to map: %s slot %d (%s)",
                                        primarySource.containerType(), primarySource.slot(), primarySource.itemId()));
                            }
                            
                            this.setAmmo(entityRef, store, result.consumed());
                            this.debug(String.format("RESTORED %.0f ammo for crossbow in slot %d",
                                    (float) result.consumed(), currentSlot));
                        } else {
                            this.debug("No arrows available to consume - cannot restore ammo");
                        }
                    });
                }, delayMs, TimeUnit.MILLISECONDS);
            } else {
                this.debug("No saved ammo to restore (null or 0)");
            }
        } else {
            this.debug("New item is not a crossbow - no restore needed");
        }
    }
    
    /**
     * Return arrows to their original source location after vanilla dumps them to hotbar.
     * Uses the ArrowSource from the map (not item metadata) to avoid item updates that reset ammo.
     * 
     * Note: This moves whole stacks rather than partial quantities due to API limitations.
     * The arrows vanilla returns should match what we originally consumed.
     */
    private void returnArrowsToSourceFromMap(
            @Nonnull final Inventory inventory,
            @Nonnull final ArrowSource source,
            final int arrowCount
    ) {
        final String sourceContainer = source.containerType();
        final short sourceSlot = source.slot();
        final String arrowItemId = source.itemId();
        
        // Don't need to move if source was hotbar
        if ("hotbar".equals(sourceContainer)) {
            this.debug("Arrow source was hotbar - no need to relocate");
            return;
        }
        
        this.debug(String.format("Attempting to return arrows (%s) to %s slot %d",
                arrowItemId, sourceContainer, sourceSlot));
        
        final ItemContainer hotbar = inventory.getHotbar();
        final ItemContainer targetContainer = "storage".equals(sourceContainer)
                ? inventory.getStorage()
                : inventory.getBackpack();
        
        // Find the arrows in hotbar that vanilla returned and swap them to target
        for (short slot = 0; slot < hotbar.getCapacity(); slot++) {
            final ItemStack item = hotbar.getItemStack(slot);
            if (item == null || item.isEmpty()) {
                continue;
            }
            
            final String itemId = item.getItem().getId();
            if (!arrowItemId.equals(itemId)) {
                continue;
            }
            
            // Found matching arrows in hotbar - swap with target slot
            final ItemStack existingAtTarget = targetContainer.getItemStack(sourceSlot);
            
            // Move arrows from hotbar to target
            targetContainer.setItemStackForSlot(sourceSlot, item);
            
            // If target had something, move it to hotbar (swap)
            if (existingAtTarget != null && !existingAtTarget.isEmpty()) {
                hotbar.setItemStackForSlot(slot, existingAtTarget);
                this.debug(String.format("Swapped arrows from hotbar slot %d with %s slot %d",
                        slot, sourceContainer, sourceSlot));
            } else {
                // Target was empty, just clear hotbar slot
                hotbar.setItemStackForSlot(slot, null);
                this.debug(String.format("Moved arrows from hotbar slot %d to %s slot %d",
                        slot, sourceContainer, sourceSlot));
            }
            
            // Only move one stack of matching arrows
            return;
        }
        
        this.debug("Could not find matching arrows in hotbar to return");
    }
    
    /**
     * Check if an item is a crossbow weapon.
     */
    private boolean isCrossbow(@Nullable final ItemStack item) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        if (item.getItem().getWeapon() == null) {
            return false;
        }
        final String itemId = item.getItem().getId();
        return itemId != null && itemId.contains("Crossbow");
    }
    
    /**
     * Consume arrows from the player's inventory with source tracking.
     * Searches for items containing "Arrow" in their ID.
     * Returns a ConsumeResult with the count consumed and source locations.
     */
    private ConsumeResult consumeArrowsWithTracking(@Nonnull final Inventory inventory, final int count) {
        if (count <= 0) {
            return new ConsumeResult(0, new ArrayList<>());
        }
        
        final List<ArrowSource> allSources = new ArrayList<>();
        int remaining = count;
        
        // Search through hotbar, then storage, then backpack for arrows
        var hotbarResult = this.consumeArrowsFromContainerWithTracking(inventory.getHotbar(), "hotbar", remaining);
        remaining = hotbarResult.remaining;
        allSources.addAll(hotbarResult.sources);
        
        if (remaining > 0) {
            var storageResult = this.consumeArrowsFromContainerWithTracking(inventory.getStorage(), "storage", remaining);
            remaining = storageResult.remaining;
            allSources.addAll(storageResult.sources);
        }
        
        if (remaining > 0) {
            var backpackResult = this.consumeArrowsFromContainerWithTracking(inventory.getBackpack(), "backpack", remaining);
            remaining = backpackResult.remaining;
            allSources.addAll(backpackResult.sources);
        }
        
        return new ConsumeResult(count - remaining, allSources);
    }
    
    /**
     * Helper record for container consumption results.
     */
    private record ContainerConsumeResult(int remaining, List<ArrowSource> sources) {}
    
    /**
     * Consume arrows from a specific container with source tracking.
     * Returns the remaining count needed and list of sources consumed from.
     */
    private ContainerConsumeResult consumeArrowsFromContainerWithTracking(
            @Nonnull final ItemContainer container,
            @Nonnull final String containerType,
            int remaining
    ) {
        final List<ArrowSource> sources = new ArrayList<>();
        
        for (short slot = 0; slot < container.getCapacity() && remaining > 0; slot++) {
            final ItemStack item = container.getItemStack(slot);
            if (item == null || item.isEmpty()) {
                continue;
            }
            
            final String itemId = item.getItem().getId();
            if (itemId == null || !itemId.contains("Arrow")) {
                continue;
            }
            
            final int available = item.getQuantity();
            final int toRemove = Math.min(available, remaining);
            
            this.debug(String.format("Found %d arrows (%s) in %s slot %d, removing %d",
                    available, itemId, containerType, slot, toRemove));
            
            // Track the source before removing
            sources.add(new ArrowSource(containerType, slot, itemId, toRemove));
            
            // Remove the arrows
            container.removeItemStackFromSlot(slot, toRemove);
            remaining -= toRemove;
        }
        
        return new ContainerConsumeResult(remaining, sources);
    }
    
    /**
     * Clean up tracking data when a player disconnects.
     */
    public void cleanupPlayer(@Nonnull final UUID playerUuid) {
        this.lastActiveSlot.remove(playerUuid);
        this.previousTickAmmo.remove(playerUuid);
        this.playerArrowSource.remove(playerUuid);
    }
    
    // ==================== AMMO STAT HELPERS ====================
    
    @SuppressWarnings("unchecked")
    private float getAmmo(
            @Nonnull final Ref<EntityStore> entityRef,
            @Nonnull final Store<EntityStore> store
    ) {
        final int ammoIndex = EntityStatType.getAssetMap().getIndex("Ammo");
        if (ammoIndex == Integer.MIN_VALUE) {
            return 0f;
        }
        
        final EntityStatMap statMap = (EntityStatMap) store.getComponent(
                (Ref) entityRef,
                EntityStatMap.getComponentType()
        );
        
        if (statMap == null) {
            return 0f;
        }
        
        final var statValue = statMap.get(ammoIndex);
        return statValue != null ? statValue.get() : 0f;
    }
    
    @SuppressWarnings("unchecked")
    private void setAmmo(
            @Nonnull final Ref<EntityStore> entityRef,
            @Nonnull final Store<EntityStore> store,
            final float value
    ) {
        final int ammoIndex = EntityStatType.getAssetMap().getIndex("Ammo");
        if (ammoIndex == Integer.MIN_VALUE) {
            this.debug("setAmmo FAILED: Ammo stat not found!");
            return;
        }
        
        if (!entityRef.isValid()) {
            this.debug("setAmmo FAILED: entityRef is no longer valid!");
            return;
        }
        
        final EntityStatMap statMap = (EntityStatMap) store.getComponent(
                (Ref) entityRef,
                EntityStatMap.getComponentType()
        );
        
        if (statMap == null) {
            this.debug("setAmmo FAILED: statMap is null!");
            return;
        }
        
        statMap.setStatValue(ammoIndex, value);
        
        if (this.config.isDebug()) {
            final var verify = statMap.get(ammoIndex);
            final float verifyValue = verify != null ? verify.get() : -1f;
            this.debug(String.format("setAmmo: set %.0f, verify read back: %.0f", value, verifyValue));
        }
    }
    
    // ==================== ITEM METADATA HELPERS ====================
    
    @Nonnull
    private ItemStack saveAmmo(@Nonnull final ItemStack item, final float ammo) {
        return item.withMetadata(META_KEY_SAVED_AMMO, Codec.FLOAT, ammo);
    }
    
    @Nullable
    private Float getSavedAmmo(@Nonnull final ItemStack item) {
        return (Float) item.getFromMetadataOrNull(META_KEY_SAVED_AMMO, Codec.FLOAT);
    }
    
    @Nonnull
    private ItemStack clearSavedAmmo(@Nonnull final ItemStack item) {
        return item.withMetadata(META_KEY_SAVED_AMMO, Codec.FLOAT, 0f);
    }
}
