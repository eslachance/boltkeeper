package com.tokebak.Boltkeeper;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Boltkeeper System - Preserves weapon charge stats between weapon swaps.
 * 
 * By default, Hytale resets certain stats (Ammo, MagicCharges) when switching hotbar slots.
 * This system:
 * 1. Saves the stat value to item metadata when swapping away from a supported weapon
 * 2. Restores that exact amount when swapping back
 * 
 * Supported weapons:
 * - Crossbows: Preserves the "Ammo" stat (loaded bolts)
 * - Fire Staff: Preserves the "MagicCharges" stat (charged fire orbs)
 */
public class BoltkeeperSystem extends EntityTickingSystem<EntityStore> {
    
    /**
     * Metadata key for storing saved Ammo count on crossbow items.
     */
    public static final String META_KEY_SAVED_AMMO = "BK_SavedAmmo";
    
    /**
     * Metadata key for storing saved MagicCharges count on fire staff items.
     */
    public static final String META_KEY_SAVED_MAGIC_CHARGES = "BK_SavedMagicCharges";
    
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
     * Tracks the MagicCharges value from the PREVIOUS tick per player UUID.
     */
    private final Map<UUID, Float> previousTickMagicCharges = new ConcurrentHashMap<>();
    
    /**
     * Scheduler for delayed stat restoration.
     */
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    
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
        
        final UUIDComponent uuidComponent = (UUIDComponent) archetypeChunk.getComponent(index, UUIDComponent.getComponentType());
        if (uuidComponent == null) {
            return;
        }
        final UUID playerUuid = uuidComponent.getUuid();
        if (playerUuid == null) {
            return;
        }
        
        final byte currentSlot = inventory.getActiveHotbarSlot();
        
        // Read current stats (we track these every tick)
        final float currentAmmo = this.getStatValue(entityRef, store, "Ammo");
        final float currentMagicCharges = this.getStatValue(entityRef, store, "MagicCharges");
        
        // Get last known slot (or initialize if first time)
        final Byte lastSlotObj = this.lastActiveSlot.get(playerUuid);
        if (lastSlotObj == null) {
            this.lastActiveSlot.put(playerUuid, currentSlot);
            this.previousTickAmmo.put(playerUuid, currentAmmo);
            this.previousTickMagicCharges.put(playerUuid, currentMagicCharges);
            this.debug(String.format("Player first tick - initial slot: %d, ammo: %.0f, magicCharges: %.0f", 
                    currentSlot, currentAmmo, currentMagicCharges));
            return;
        }
        
        final byte previousSlot = lastSlotObj;
        
        // Check if slot changed
        if (currentSlot == previousSlot) {
            // No slot change - just update the tracked stats for next tick
            this.previousTickAmmo.put(playerUuid, currentAmmo);
            this.previousTickMagicCharges.put(playerUuid, currentMagicCharges);
            return;
        }
        
        // Slot changed! Get the stats from BEFORE the reset (previous tick's values)
        final Float ammoBeforeReset = this.previousTickAmmo.get(playerUuid);
        final float savedAmmo = ammoBeforeReset != null ? ammoBeforeReset : 0f;
        
        final Float magicChargesBeforeReset = this.previousTickMagicCharges.get(playerUuid);
        final float savedMagicCharges = magicChargesBeforeReset != null ? magicChargesBeforeReset : 0f;
        
        // Update tracking for next tick
        this.lastActiveSlot.put(playerUuid, currentSlot);
        this.previousTickAmmo.put(playerUuid, currentAmmo);
        this.previousTickMagicCharges.put(playerUuid, currentMagicCharges);
        
        this.debug(String.format("Hotbar slot change: %d -> %d (ammo: %.0f, magicCharges: %.0f)",
                previousSlot, currentSlot, savedAmmo, savedMagicCharges));
        
        // Handle the slot change
        this.handleSlotChange(entityRef, store, inventory, previousSlot, currentSlot, savedAmmo, savedMagicCharges);
    }
    
    private void handleSlotChange(
            @Nonnull final Ref<EntityStore> entityRef,
            @Nonnull final Store<EntityStore> store,
            @Nonnull final Inventory inventory,
            final byte previousSlot,
            final byte currentSlot,
            final float ammoBeforeReset,
            final float magicChargesBeforeReset
    ) {
        final ItemContainer hotbar = inventory.getHotbar();
        ItemStack oldItem = hotbar.getItemStack((short) previousSlot);
        ItemStack newItem = hotbar.getItemStack((short) currentSlot);
        
        this.debug(String.format("Hotbar swap: slot %d -> %d | Ammo: %.0f, MagicCharges: %.0f",
                previousSlot, currentSlot, ammoBeforeReset, magicChargesBeforeReset));
        this.debug(String.format("Old item: %s | New item: %s",
                oldItem != null ? oldItem.getItem().getId() : "null",
                newItem != null ? newItem.getItem().getId() : "null"));
        
        // ==================== HANDLE OLD ITEM (SAVE STATS) ====================
        
        // Save ammo to the OLD item (if it's a crossbow with loaded ammo)
        final boolean oldIsCrossbow = this.isCrossbow(oldItem);
        if (oldIsCrossbow && ammoBeforeReset > 0) {
            oldItem = this.saveAmmo(oldItem, ammoBeforeReset);
            hotbar.setItemStackForSlot((short) previousSlot, oldItem);
            this.debug(String.format("SAVED ammo %.0f to crossbow in slot %d", ammoBeforeReset, previousSlot));
        }
        
        // Save magic charges to the OLD item (if it's a fire staff with charges)
        final boolean oldIsFireStaff = this.isFireStaff(oldItem);
        if (oldIsFireStaff && magicChargesBeforeReset > 0) {
            oldItem = this.saveMagicCharges(oldItem, magicChargesBeforeReset);
            hotbar.setItemStackForSlot((short) previousSlot, oldItem);
            this.debug(String.format("SAVED magicCharges %.0f to fire staff in slot %d", magicChargesBeforeReset, previousSlot));
        }
        
        // ==================== HANDLE NEW ITEM (RESTORE STATS) ====================
        
        final World world = ((EntityStore) store.getExternalData()).getWorld();
        final long delayMs = this.config.getRestoreDelayMs();
        
        // Restore ammo for crossbow
        final boolean newIsCrossbow = this.isCrossbow(newItem);
        if (newIsCrossbow) {
            final Float savedAmmo = this.getSavedAmmo(newItem);
            if (savedAmmo != null && savedAmmo > 0) {
                newItem = this.clearSavedAmmo(newItem);
                hotbar.setItemStackForSlot((short) currentSlot, newItem);
                
                final float ammoToRestore = savedAmmo;
                this.debug(String.format("Scheduling restore of %.0f ammo in %dms", ammoToRestore, delayMs));
                
                this.scheduler.schedule(() -> {
                    world.execute(() -> {
                        this.setStatValue(entityRef, store, "Ammo", ammoToRestore);
                        this.debug(String.format("RESTORED %.0f ammo for crossbow in slot %d", ammoToRestore, currentSlot));
                    });
                }, delayMs, TimeUnit.MILLISECONDS);
            }
        }
        
        // Restore magic charges for fire staff
        final boolean newIsFireStaff = this.isFireStaff(newItem);
        if (newIsFireStaff) {
            final Float savedMagicCharges = this.getSavedMagicCharges(newItem);
            if (savedMagicCharges != null && savedMagicCharges > 0) {
                newItem = this.clearSavedMagicCharges(newItem);
                hotbar.setItemStackForSlot((short) currentSlot, newItem);
                
                final float chargesToRestore = savedMagicCharges;
                this.debug(String.format("Scheduling restore of %.0f magicCharges in %dms", chargesToRestore, delayMs));
                
                this.scheduler.schedule(() -> {
                    world.execute(() -> {
                        this.setStatValue(entityRef, store, "MagicCharges", chargesToRestore);
                        this.debug(String.format("RESTORED %.0f magicCharges for fire staff in slot %d", chargesToRestore, currentSlot));
                    });
                }, delayMs, TimeUnit.MILLISECONDS);
            }
        }
    }
    
    // ==================== WEAPON TYPE CHECKS ====================
    
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
     * Check if an item is a Fire Staff weapon.
     */
    private boolean isFireStaff(@Nullable final ItemStack item) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        if (item.getItem().getWeapon() == null) {
            return false;
        }
        final String itemId = item.getItem().getId();
        return itemId != null && itemId.equals("Weapon_Staff_Crystal_Flame");
    }
    
    /**
     * Clean up tracking data when a player disconnects.
     */
    public void cleanupPlayer(@Nonnull final UUID playerUuid) {
        this.lastActiveSlot.remove(playerUuid);
        this.previousTickAmmo.remove(playerUuid);
        this.previousTickMagicCharges.remove(playerUuid);
    }
    
    // ==================== GENERIC STAT HELPERS ====================
    
    private float getStatValue(
            @Nonnull final Ref<EntityStore> entityRef,
            @Nonnull final Store<EntityStore> store,
            @Nonnull final String statName
    ) {
        final int statIndex = EntityStatType.getAssetMap().getIndex(statName);
        if (statIndex == Integer.MIN_VALUE) {
            return 0f;
        }
        
        final EntityStatMap statMap = (EntityStatMap) store.getComponent(
                entityRef,
                EntityStatMap.getComponentType()
        );
        
        if (statMap == null) {
            return 0f;
        }
        
        final var statValue = statMap.get(statIndex);
        return statValue != null ? statValue.get() : 0f;
    }
    
    private void setStatValue(
            @Nonnull final Ref<EntityStore> entityRef,
            @Nonnull final Store<EntityStore> store,
            @Nonnull final String statName,
            final float value
    ) {
        final int statIndex = EntityStatType.getAssetMap().getIndex(statName);
        if (statIndex == Integer.MIN_VALUE) {
            this.debug(String.format("setStatValue FAILED: %s stat not found!", statName));
            return;
        }
        
        if (!entityRef.isValid()) {
            this.debug(String.format("setStatValue FAILED: entityRef is no longer valid for %s!", statName));
            return;
        }
        
        final EntityStatMap statMap = (EntityStatMap) store.getComponent(
                entityRef,
                EntityStatMap.getComponentType()
        );
        
        if (statMap == null) {
            this.debug(String.format("setStatValue FAILED: statMap is null for %s!", statName));
            return;
        }
        
        statMap.setStatValue(statIndex, value);
        
        if (this.config.isDebug()) {
            final var verify = statMap.get(statIndex);
            final float verifyValue = verify != null ? verify.get() : -1f;
            this.debug(String.format("setStatValue(%s): set %.0f, verify read back: %.0f", statName, value, verifyValue));
        }
    }
    
    // ==================== AMMO METADATA HELPERS (Crossbow) ====================
    
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
    
    // ==================== MAGIC CHARGES METADATA HELPERS (Fire Staff) ====================
    
    @Nonnull
    private ItemStack saveMagicCharges(@Nonnull final ItemStack item, final float charges) {
        return item.withMetadata(META_KEY_SAVED_MAGIC_CHARGES, Codec.FLOAT, charges);
    }
    
    @Nullable
    private Float getSavedMagicCharges(@Nonnull final ItemStack item) {
        return (Float) item.getFromMetadataOrNull(META_KEY_SAVED_MAGIC_CHARGES, Codec.FLOAT);
    }
    
    @Nonnull
    private ItemStack clearSavedMagicCharges(@Nonnull final ItemStack item) {
        return item.withMetadata(META_KEY_SAVED_MAGIC_CHARGES, Codec.FLOAT, 0f);
    }
}
