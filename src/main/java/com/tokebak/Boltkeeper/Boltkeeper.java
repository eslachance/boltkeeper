package com.tokebak.Boltkeeper;

import com.hypixel.hytale.component.system.ISystem;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.util.Config;
import com.tokebak.Boltkeeper.interactions.BoltkeeperEssenceCheckInteraction;
import com.tokebak.Boltkeeper.interactions.BoltkeeperEssenceConsumeInteraction;

import javax.annotation.Nonnull;

/**
 * Boltkeeper - A Hytale mod that enhances weapon behavior for projectile/magic weapons:
 * 
 * Supported weapons:
 * - Crossbows: Preserves loaded ammo (Ammo stat) across hotbar slot changes (arrow loading from backpack is vanilla)
 * - Fire Staff: Preserves magic charges (MagicCharges stat), allows Fire Essence from backpack
 * 
 * This is achieved through:
 * - Custom interactions for Fire Staff that check/consume essence from all inventory (including backpack)
 * - A tick-based system that tracks weapon stats (e.g. crossbow Ammo) across hotbar slot changes
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

        // Register custom interactions for Fire Staff backpack essence support
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
     * Register custom interaction types for Fire Staff essence handling.
     * These interactions check/consume Fire Essence from backpack in addition to hotbar/storage.
     */
    private void registerInteractions() {
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
        
        System.out.println("[BOLTKEEPER] Registered custom interactions: BoltkeeperEssenceCheck, BoltkeeperEssenceConsume");
    }
}
