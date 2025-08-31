package com.github.wackyerwack.beaconPortals.storage;

import com.github.wackyerwack.beaconPortals.Portal;
import com.github.wackyerwack.beaconPortals.util.Position;
import org.bukkit.World;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public interface PortalStorage {
    CompletableFuture<Portal> getPortal(Position position);

    CompletableFuture<List<Portal>> getPortals(int addressId);

    void deletePortal(long fullId);

    // Doesn't have to instantly save the portal, can cache & save later
    void savePortal(Portal portal);

    // NOTE: the implementation doesn't need to do anything on world saves
    void onWorldSave(World world);

    // NOTE: the implementation doesn't need to do anything when a portal thinks it is dirty
    void onPortalDirty(Portal portal);
}
