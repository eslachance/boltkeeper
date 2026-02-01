package com.tokebak.Boltkeeper;

import com.hypixel.hytale.component.system.ISystem;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.util.Config;
import com.tokebak.Boltkeeper.interactions.BoltkeeperAmmoCheckInteraction;
import com.tokebak.Boltkeeper.interactions.BoltkeeperAmmoConsumeInteraction;
import com.tokebak.Boltkeeper.interactions.BoltkeeperEssenceCheckInteraction;
import com.tokebak.Boltkeeper.interactions.BoltkeeperEssenceConsumeInteraction;

import javax.annotation.Nonnull;

/**
 * Boltkeeper - A Hytale mod that enhances weapon behavior for projectile/magic weapons:
 * 
 * Supported weapons:
 * - Crossbows: Preserves loaded ammo (Ammo stat), allows arrow loading from backpack
 * - Fire Staff: Preserves magic charges (MagicCharges stat), allows Fire Essence from backpack
 * 
 * This is achieved through:
 * - Custom interactions that check/consume ammo from all inventory containers (including backpack)
 * - A tick-based system that tracks weapon stats across hotbar slot changes
 */
public class Boltkeeper extends JavaPlugin {

    private Config<BoltkeeperConfig> config;

    public Boltkeeper(@Nonnull final JavaPluginInit init) {
        super(init);
        this.config = this.withConfig("BoltkeeperConfig", BoltkeeperConfig.CODEC);
    }

    @Override
    protected void setup() {
        super.setup();

        // Load config from file (or create default)
        this.config.save();

        final BoltkeeperConfig cfg = (BoltkeeperConfig) this.config.get();

        // Register custom interactions for backpack arrow support
        this.registerInteractions();

        // Register the Boltkeeper system for ammo preservation
        final BoltkeeperSystem system = new BoltkeeperSystem(cfg);
        this.getEntityStoreRegistry().registerSystem((ISystem<EntityStore>) system);

        System.out.println("[BOLTKEEPER] ========================================");
        System.out.println("[BOLTKEEPER] Boltkeeper mod loaded!");
        System.out.println("[BOLTKEEPER] Config: enabled=" + cfg.isEnabled() + ", debug=" + cfg.isDebug());
        System.out.println("[BOLTKEEPER] ========================================");
    }
    
    /**
     * Register custom interaction types for weapon ammo/essence handling.
     * These interactions check/consume resources from backpack in addition to hotbar/storage.
     */
    private void registerInteractions() {
        // ==================== CROSSBOW INTERACTIONS ====================
        
        // Register BoltkeeperAmmoCheck - checks for arrows including backpack
        this.getCodecRegistry(Interaction.CODEC).register(
                "BoltkeeperAmmoCheck",
                BoltkeeperAmmoCheckInteraction.class,
                BoltkeeperAmmoCheckInteraction.CODEC
        );
        
        // Register BoltkeeperAmmoConsume - consumes arrows from anywhere including backpack
        this.getCodecRegistry(Interaction.CODEC).register(
                "BoltkeeperAmmoConsume",
                BoltkeeperAmmoConsumeInteraction.class,
                BoltkeeperAmmoConsumeInteraction.CODEC
        );
        
        // ==================== FIRE STAFF INTERACTIONS ====================
        
        // Register BoltkeeperEssenceCheck - checks for Fire Essence including backpack
        this.getCodecRegistry(Interaction.CODEC).register(
                "BoltkeeperEssenceCheck",
                BoltkeeperEssenceCheckInteraction.class,
                BoltkeeperEssenceCheckInteraction.CODEC
        );
        
        // Register BoltkeeperEssenceConsume - consumes Fire Essence from anywhere including backpack
        this.getCodecRegistry(Interaction.CODEC).register(
                "BoltkeeperEssenceConsume",
                BoltkeeperEssenceConsumeInteraction.class,
                BoltkeeperEssenceConsumeInteraction.CODEC
        );
        
        System.out.println("[BOLTKEEPER] Registered custom interactions: BoltkeeperAmmoCheck, BoltkeeperAmmoConsume, BoltkeeperEssenceCheck, BoltkeeperEssenceConsume");
    }
}
