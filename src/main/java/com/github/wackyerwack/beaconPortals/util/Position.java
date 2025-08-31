package com.github.wackyerwack.beaconPortals.util;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.util.NumberConversions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class Position {
    public final int x;
    public final int y;
    public final int z;
    public final World world;

    public Position(int x, int y, int z, World world) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.world = world;
    }

    public Position(Block block) {
        this.x = block.getX();
        this.y = block.getY();
        this.z = block.getZ();
        this.world = block.getWorld();
    }

    public Position(BlockState state) {
        this.x = state.getX();
        this.y = state.getY();
        this.z = state.getZ();
        this.world = state.getWorld();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Position that)) return false;
        return x == that.x && y == that.y && z == that.z && Objects.equals(world.getName(), that.world.getName());
    }

    @Override
    public int hashCode() {
        return Objects.hash(world.getName(), x, y, z);
    }

    @Override
    public String toString() {
        return "Position{" +
                "x=" + x +
                ", y=" + y +
                ", z=" + z +
                ", world=" + world +
                '}';
    }

    @Nullable
    public Block getBlock() {
        if (isLoaded()) return world.getBlockAt(x, y, z);
        return null;
    }

    @Nullable
    public Chunk getChunk() {
        if (isLoaded()) return world.getChunkAt(getChunkX(), getChunkZ());
        return null;
    }

    public int getChunkX() {
        return x >> 3;
    }

    public int getChunkZ() {
        return y >> 3;
    }

    public void loadForTick() {
        this.world.getChunkAt(this.getChunkX(), this.getChunkZ());
    }

    public boolean isLoaded() {
        return this.world.isChunkLoaded(this.getChunkX(), this.getChunkZ());
    }

    public Location getLocation() {
        return new Location(world, x, y, z);
    }

    public double distanceSquaredIgnoreWorld(@NotNull Position o) {
        return NumberConversions.square(x - o.x) + NumberConversions.square(y - o.y) + NumberConversions.square(z - o.z);
    }
}
