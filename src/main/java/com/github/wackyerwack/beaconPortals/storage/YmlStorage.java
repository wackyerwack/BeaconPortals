package com.github.wackyerwack.beaconPortals.storage;

import com.github.wackyerwack.beaconPortals.Portal;
import com.github.wackyerwack.beaconPortals.util.Position;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class YmlStorage implements PortalStorage {
    private final Map<Position, Portal> portalCache = new ConcurrentHashMap<>();
    private final Map<Integer, Map<Integer, Portal>> portalAddressCache = new ConcurrentHashMap<>();

    private final Plugin plugin;
    private final Path addressRoot;
    private final Path positionRoot;

    private static final String YML_SUFFIX = ".yml";

    public YmlStorage(Plugin plugin, Path portalsRoot) {
        this.plugin = plugin;
        this.addressRoot = portalsRoot.resolve("addresses");
        this.positionRoot = portalsRoot.resolve("positions");
        if (!this.addressRoot.toFile().exists()) this.addressRoot.toFile().mkdirs();
        if (!this.positionRoot.toFile().exists()) this.positionRoot.toFile().mkdirs();
    }

    // TODO: all this config loading & unloading is done without proper error handling

    @Override
    public CompletableFuture<Portal> getPortal(Position position) {
        if (portalCache.containsKey(position)) return CompletableFuture.completedFuture(portalCache.get(position));

        var positionId = position.hashCode();

        return CompletableFuture.supplyAsync(() -> {
            File portalFile = positionRoot.resolve(positionId+YML_SUFFIX).toFile();
            if (!portalFile.exists() || !portalFile.isFile() || !portalFile.getName().endsWith(YML_SUFFIX)) return null;

            YamlConfiguration config = YamlConfiguration.loadConfiguration(portalFile);
            Integer addressId = config.getInt("addressId");

            Map<Integer, Portal> loadedPortals = portalAddressCache.computeIfAbsent(addressId, k -> new ConcurrentHashMap<>());

            CompletableFuture<Portal> loaderTask = new CompletableFuture<>();
            Bukkit.getScheduler().runTask(plugin, () -> {
                Portal portal = null;
                try {
                    portal = new Portal(this, addressId, position);
                    loadedPortals.put(positionId, portal);
                    portalCache.put(position, portal);
                } finally {
                    loaderTask.complete(portal);
                }
            });
            return loaderTask.join();
        });
    }

    @Override
    public CompletableFuture<List<Portal>> getPortals(int addressId) {
        if (portalAddressCache.containsKey(addressId))
            return CompletableFuture.completedFuture(new ArrayList<>(portalAddressCache.get(addressId).values()));

        return CompletableFuture.supplyAsync(() -> {
            File addressFolder = addressRoot.resolve(addressId+"").toFile();
            if (!addressFolder.exists() || !addressFolder.isDirectory()) return Collections.emptyList();

            Map<Integer, Portal> loadedPortals = portalAddressCache.computeIfAbsent(addressId, k -> new ConcurrentHashMap<>());
            File[] portalFiles = addressFolder.listFiles();
            if (portalFiles == null) return Collections.emptyList();

            for (File portalFile : portalFiles) {
                if (!portalFile.isFile() || !portalFile.getName().endsWith(YML_SUFFIX)) continue;

                YamlConfiguration config = YamlConfiguration.loadConfiguration(portalFile);
                String worldKeyStr = config.getString("world");
                if (worldKeyStr == null) continue;

                CompletableFuture<Void> loaderTask = new CompletableFuture<>();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    try {
                        World world = Bukkit.getWorld(worldKeyStr);
                        if (world == null) return;

                        Position pos = new Position(config.getInt("x"), config.getInt("y"), config.getInt("z"), world);
                        Portal portal = new Portal(this, addressId, pos);

                        loadedPortals.put(pos.hashCode(), portal);
                        portalCache.put(pos, portal);
                    } finally {
                        loaderTask.complete(null);
                    }
                });
                loaderTask.join();
            }

            return new ArrayList<>(loadedPortals.values());
        });
    }

    @Override
    public void deletePortal(long fullId) {
        int addressId = (int) (fullId >> 32);
        int positionHash = (int) fullId;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            File addressFolder = addressRoot.resolve(addressId+"").toFile();
            File portalFile = new File(addressFolder, positionHash + YML_SUFFIX);

            File positionFile = positionRoot.resolve(positionHash + YML_SUFFIX).toFile();
            if (positionFile.exists()) positionFile.delete();

            if (portalFile.exists()) portalFile.delete();

            if (addressFolder.exists() && addressFolder.isDirectory()) {
                File[] remaining = addressFolder.listFiles();
                if (remaining == null || remaining.length == 0) {
                    addressFolder.delete();
                }
            }
        });

        Map<Integer, Portal> portals = portalAddressCache.get(addressId);
        if (portals == null) return;

        var portal = portals.get(positionHash);
        if (portal != null) removeCachedPortal(portal.getPosition());
    }

    private void removeCachedPortal(Position position) {
        var portal = portalCache.remove(position);
        if (portal == null) return;

        var address = portal.getCachedAddress();
        if (address == null) return;

        Map<Integer, Portal> addressList = portalAddressCache.get(address);
        if (addressList == null) return;

        addressList.values().removeIf(p -> p.getPosition().equals(position));
    }

    @Override
    public void savePortal(Portal portal) {
        Integer addressId = portal.getCachedAddress();
        Position position = portal.getPosition();

        if (portalCache.containsKey(position) && portalCache.get(position) != portal) removeCachedPortal(position);
        portalCache.put(position, portal);

        if (addressId != null) {
            Map<Integer, Portal> addressList = portalAddressCache.computeIfAbsent(addressId, k -> new ConcurrentHashMap<>());
            // NOTE: in practice there are not going to be hash collisions but they could happen
            addressList.put(portal.getPosition().hashCode(), portal);

            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                File addressFolder = addressRoot.resolve(addressId+"").toFile();
                addressFolder.mkdirs();
                File portalFile = new File(addressFolder, position.hashCode() + YML_SUFFIX);
                YamlConfiguration config = new YamlConfiguration();
                config.set("x", position.x);
                config.set("y", position.y);
                config.set("z", position.z);
                config.set("world", position.world.getName());
                try {
                    config.save(portalFile);
                } catch (IOException e) {
                    // TODO: dirty logging should probs be replaced
                    e.printStackTrace();
                }
            });
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            File positionFile = positionRoot.resolve(position.hashCode() + YML_SUFFIX).toFile();
            YamlConfiguration config = new YamlConfiguration();
            config.set("addressId", addressId);
            try {
                config.save(positionFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    public void onWorldSave(World world) {
        // Everything should already be saved so we won't bother
    }

    @Override
    public void onPortalDirty(Portal portal) {
        savePortal(portal);
    }
}