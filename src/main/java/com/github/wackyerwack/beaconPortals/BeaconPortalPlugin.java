package com.github.wackyerwack.beaconPortals;

import com.github.wackyerwack.beaconPortals.listeners.PortalListener;
import com.github.wackyerwack.beaconPortals.listeners.WorldSaveListener;
import com.github.wackyerwack.beaconPortals.storage.PortalStorage;
import com.github.wackyerwack.beaconPortals.storage.YmlStorage;
import org.bukkit.plugin.java.JavaPlugin;

public final class BeaconPortalPlugin extends JavaPlugin {
    private PortalStorage storage;

    public void onEnable() {
        storage = new YmlStorage(this, getDataFolder().toPath().resolve("portals"));

        this.getServer().getPluginManager().registerEvents(new PortalListener(storage, this), this);
        this.getServer().getPluginManager().registerEvents(new WorldSaveListener(storage), this);
    }

    public void onDisable() {
        // probs not needed -- untested
//        for (var world : Bukkit.getWorlds()) {
//            storage.onWorldSave(world);
//        }
    }
}
