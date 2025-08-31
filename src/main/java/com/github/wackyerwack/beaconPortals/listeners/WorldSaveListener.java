package com.github.wackyerwack.beaconPortals.listeners;

import com.github.wackyerwack.beaconPortals.storage.PortalStorage;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldSaveEvent;
import org.jetbrains.annotations.NotNull;

public class WorldSaveListener implements Listener {
    private final PortalStorage portalStore;

    public WorldSaveListener(PortalStorage portalStore) {
        this.portalStore = portalStore;
    }

    @EventHandler
    public void onWorldSaveEvent(@NotNull WorldSaveEvent event) {
        portalStore.onWorldSave(event.getWorld());
    }
}