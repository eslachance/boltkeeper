package com.tokebak.Boltkeeper;

import com.hypixel.hytale.component.system.ISystem;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.util.Config;

import javax.annotation.Nonnull;

/**
 * Boltkeeper - A Hytale mod that automatically reloads crossbow ammunition
 * when switching hotbar slots.
 * 
 * By default, Hytale resets the Ammo stat (loaded bolts) when you change your 
 * active hotbar slot. This mod detects when you swap to a crossbow and automatically
 * maximizes the ammo, giving you a fully loaded crossbow without the reload animation.
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

        // Register the Boltkeeper system
        final BoltkeeperSystem system = new BoltkeeperSystem(cfg);
        this.getEntityStoreRegistry().registerSystem((ISystem) system);

        System.out.println("[BOLTKEEPER] ========================================");
        System.out.println("[BOLTKEEPER] Boltkeeper mod loaded!");
        System.out.println("[BOLTKEEPER] Config: enabled=" + cfg.isEnabled() + ", debug=" + cfg.isDebug() + ", delayMs=" + cfg.getRestoreDelayMs());
        System.out.println("[BOLTKEEPER] Crossbows will auto-reload when swapped to.");
        System.out.println("[BOLTKEEPER] ========================================");
    }
}
