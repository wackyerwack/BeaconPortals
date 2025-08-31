package com.github.wackyerwack.beaconPortals.listeners;

import com.github.wackyerwack.beaconPortals.Portal;
import com.github.wackyerwack.beaconPortals.storage.PortalStorage;
import com.github.wackyerwack.beaconPortals.util.Position;
import org.bukkit.*;
import org.bukkit.block.Beacon;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.BoundingBox;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Comparator;
import java.util.stream.Collectors;

public class PortalListener implements Listener {
    private final PortalStorage storage;
    private final Plugin plugin;

    public PortalListener(PortalStorage storage, Plugin plugin) {
        this.storage = storage;
        this.plugin = plugin;
    }

    // TODO: actually make work. For some reason breaking the beacon didn't cause the removal
    private void onBlockBreak(Block block) {
        Position position = new Position(block);
        storage.getPortal(position).thenAccept(portal -> {
            if (portal == null) return;

            Bukkit.getScheduler().runTask(plugin, () -> {
                position.loadForTick(); // ensures the chunk is loaded (it should be, but you never know)

                var address = portal.getFullAddress();
                if (address == null) return;

                storage.deletePortal(address);
            });
        });
    }

    // TODO: add both explode events, & other block breaking events
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerBreakBlock(BlockBreakEvent e) {
        onBlockBreak(e.getBlock());
    }

    @EventHandler
    public void onPlayerToggleSneakEvent(@NotNull PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        if (!event.isSneaking()) return;

        var b = player.getLocation().subtract(0, 1, 0).getBlock();
        if (b.getType() != Material.BEACON) return;

        if (!(b.getState() instanceof Beacon beacon)) return;
        if (beacon.getTier() < 1) return;

        Position position = new Position(beacon);
        storage.getPortal(position).thenAccept(p -> Bukkit.getScheduler().runTask(plugin, () -> preTeleportFromPortal(p, b, position, player)));
    }

    private void preTeleportFromPortal(Portal portal, Block b, Position position, Player player) {
        // ensure we are still on the correct block
        var testPos = new Position(player.getLocation().subtract(0, 1, 0).getBlock());

        if (!position.equals(testPos)) return;

        // ensure loaded
        position.loadForTick();
        if (portal == null) {
            if (b.getType() != Material.BEACON) return;
            if (!(b.getState() instanceof Beacon beacon2)) return;
            if (beacon2.getTier() < 1) return;

            portal = new Portal(storage, null, position);
            portal.refreshAddress();

            storage.savePortal(portal);
            b.getWorld().spawnParticle(Particle.LAVA, b.getLocation().add(0, 1, 0), 5);
            // TODO: light portal sound?
        }
        else {
            portal.refreshAddress();
        }

        Integer connection = portal.getDialedConnection();
        if (connection == null) return;

        Portal finalPortal = portal;
        storage.getPortals(connection).thenAccept(portals -> {
            if (portals.isEmpty()) return;

            portals.sort(Comparator.comparingDouble(p ->
                    p.getPosition().distanceSquaredIgnoreWorld(position)
            ));

            Portal closest = portals.getFirst();
            Bukkit.getScheduler().runTask(plugin, () -> teleportFormPortal(finalPortal, closest, player));
        });


    }

    private void teleportFormPortal(Portal origin, Portal destination, Player player) {
        Position originPos = origin.getPosition();
        Position destinationPos = destination.getPosition();

        Collection<LivingEntity> leashedEntities = origin.getPosition().world.getNearbyEntities(
                        BoundingBox.of(
                                originPos.getLocation(), 5,5,5
                        ))
                .parallelStream().filter(entity -> entity instanceof LivingEntity)
                .map(entity -> (LivingEntity) entity)
                .filter(le -> (le.isLeashed() && le.getLeashHolder().equals(player)) || le == player)
                .collect(Collectors.toSet());

        leashedEntities.forEach(le -> {
            Location originLoc = le.getLocation();
            originPos.world.playSound(originLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 16, 16);
            originPos.world.spawnParticle(Particle.FLASH, originLoc, 10, 0.2, 2, 0.2);

            Location offset = originLoc.clone().subtract(originPos.getLocation().add(0.5, 0.5, 0.5));
            Location destinationLoc = destinationPos.getLocation().add(0.5, 0.5, 0.5).add(offset);
            destinationLoc.setDirection(originLoc.getDirection());
            le.teleport(destinationLoc);

            destinationPos.world.playSound(destinationLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 16, 16);
            destinationPos.world.spawnParticle(Particle.FLASH, destinationLoc, 20, 0.2, 1, 0.2);

            if (le != player) le.setLeashHolder(player);
        });

        player.setSneaking(false);
    }
}