package com.tokebak.Boltkeeper;

import com.hypixel.hytale.component.system.ISystem;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.util.Config;
import com.tokebak.Boltkeeper.interactions.BoltkeeperAmmoCheckInteraction;
import com.tokebak.Boltkeeper.interactions.BoltkeeperAmmoConsumeInteraction;

import javax.annotation.Nonnull;

/**
 * Boltkeeper - A Hytale mod that enhances crossbow behavior:
 * 1. Automatically preserves loaded ammo when switching hotbar slots
 * 2. Allows crossbows to load arrows from the backpack (not just hotbar/storage)
 * 
 * This is achieved through:
 * - Custom interactions that check/consume arrows from all inventory containers
 * - A tick-based system that tracks ammo state across slot changes
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
     * Register custom interaction types for crossbow arrow handling.
     * These interactions check/consume arrows from backpack in addition to hotbar/storage.
     */
    private void registerInteractions() {
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
        
        System.out.println("[BOLTKEEPER] Registered custom interactions: BoltkeeperAmmoCheck, BoltkeeperAmmoConsume");
    }
}
