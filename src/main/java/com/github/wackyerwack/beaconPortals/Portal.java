package com.github.wackyerwack.beaconPortals;

import com.github.wackyerwack.beaconPortals.storage.PortalStorage;
import com.github.wackyerwack.beaconPortals.util.Position;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class Portal {
    private final PortalStorage storage;
    private final Position position;
    private Integer address;
    /* tier? */

    public Portal(PortalStorage storage, @Nullable Integer address, Position position) {
        this.storage = storage;
        this.position = position;
        this.address = address;
    }

    public Position getPosition() {
        return position;
    }

    @Nullable
    private Integer scanLayer(int layer) {
        position.loadForTick();
        Block block = Objects.requireNonNull(position.getBlock()).getRelative(0, layer, 0);

        List<Integer> IDs = new ArrayList<>();
        block = block.getRelative(-1, 0, -2);
        boolean encountered_block = false;

        for (int i = 0; i < 16; ++i) {
            if (!encountered_block && !block.getType().isAir()) encountered_block = true;

            block.getRelative(0, 20, 0).setType(Material.GLASS);

            int blockHash = block.getBlockData().getAsString().hashCode();

            if (block instanceof Container){
                Container container = (Container) block.getState();
                blockHash = Long.hashCode(((long) Arrays.hashCode(container.getInventory().getContents()) << 32) + blockHash);
            }

            IDs.add(blockHash);

            if (i < 3) block = block.getRelative(1, 0, 0);
            if (3 <= i & i < 7) block = block.getRelative(0, 0, 1);
            if (7 <= i & i < 11) block = block.getRelative(-1, 0, 0);
            if (!(11 <= i & i < 15)) continue;
            block = block.getRelative(0, 0, -1);
        }

        return encountered_block ? IDs.hashCode() : null;
    }

    private Iterator<Integer> getDialedConnections() {
        return new Iterator<>() {
            private int layer = 0;
            private Integer next = null;
            private boolean finished = false;

            private void advance() {
                if (finished) return;
                while (layer < 10) {
                    Integer res = scanLayer(layer++);
                    if (res == null) continue;
                    next = res;
                    return;
                }
                finished = true;
            }

            @Override
            public boolean hasNext() {
                if (next == null) advance();
                return layer < 10;
            }

            @Override
            public Integer next() {
                advance();
                return next;
            }
        };
    }

    @Nullable
    public Integer getDialedConnection() {
        if (!position.isLoaded()) return null;
        return getDialedConnections().next();
    }

    public void refreshAddress() {
        if (!position.isLoaded()) return;
        var old = this.address;
        this.address = scanLayer(-1);
        if (!Objects.equals(old, this.address)) storage.onPortalDirty(this);
    }

    @Nullable
    public Integer getCachedAddress() {
        return address;
    }

    @Nullable
    public Long getCachedFullAddress() {
        if (address == null) return null;
        return ((long) address) << 32 + hashCode();
    }

    @Nullable
    public Integer getAddress() {
        if (address == null) refreshAddress();
        return getCachedAddress();
    }

    @Nullable
    public Long getFullAddress() {
        if (address == null) refreshAddress();
        return getCachedFullAddress();
    }

    @Override
    public int hashCode() {
        return position.hashCode();
    }
}
