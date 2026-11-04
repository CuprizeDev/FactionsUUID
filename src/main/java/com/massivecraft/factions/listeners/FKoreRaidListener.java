package com.massivecraft.factions.listeners;

import com.golfing8.kore.FactionsKore;
import com.golfing8.kore.event.RaidEndEvent;
import com.golfing8.kore.event.RaidStartEvent;
import com.golfing8.kore.feature.RaidClaimFeature;
import com.golfing8.kore.object.Raid;
import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.FPlayers;
import com.massivecraft.factions.Faction;
import com.massivecraft.factions.raids.RaidManager;
import com.massivecraft.factions.raids.RaidRecord;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.Plugin;

import java.util.List;

public class FKoreRaidListener implements Listener {

    private static boolean registered = false;

    public static synchronized void registerIfPossible(Plugin plugin) {
        if (registered) {
            return;
        }

        if (Bukkit.getPluginManager().isPluginEnabled("FactionsKore")) {
            Bukkit.getPluginManager().registerEvents(new FKoreRaidListener(), plugin);
            registered = true;

            try {
                RaidClaimFeature feature = FactionsKore.get().getFeature(RaidClaimFeature.class);
                if (feature != null && feature.isOn()) {
                    List<Raid> active = feature.getActiveRaids();
                    if (active != null) {
                        for (Raid raid : active) {
                            Location loc = raid.getFirstExplosionLocation() != null ? raid.getFirstExplosionLocation() : raid.getLastLocation();
                            RaidManager.getInstance().handleRaidStart(raid.getFaction(), raid.getRaided(), raid.getWorld(), loc, false);
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }
    }

    public static void updateActiveRaidStats(RaidRecord record) {
        if (record == null || !record.isActive()) {
            return;
        }
        if (!Bukkit.getPluginManager().isPluginEnabled("FactionsKore")) {
            return;
        }

        try {
            RaidClaimFeature feature = FactionsKore.get().getFeature(RaidClaimFeature.class);
            if (feature != null) {
                Raid raid = feature.getRaid(record.getDefenderFactionId());
                if (raid != null) {
                    record.setAttackerKills(Math.max(record.getAttackerKills(), raid.getRaidingKills()));
                    record.setDefenderKills(Math.max(record.getDefenderKills(), raid.getRaidedKills()));
                }
            }
        } catch (Throwable ignored) {
        }
    }

    @EventHandler
    public void onRaidStart(RaidStartEvent event) {
        Raid raid = event.getRaid();
        String raiderId = event.getFactionRaiding();
        String raidedId = event.getFactionRaided();
        if (raid != null) {
            if (raiderId == null) {
                raiderId = raid.getFaction();
            }
            if (raidedId == null) {
                raidedId = raid.getRaided();
            }
        }

        Location loc = null;
        World world = null;
        if (raid != null) {
            loc = raid.getFirstExplosionLocation() != null ? raid.getFirstExplosionLocation() : raid.getLastLocation();
            world = raid.getWorld();
            if (world == null && loc != null) {
                world = loc.getWorld();
            }
        }

        RaidManager.getInstance().handleRaidStart(raiderId, raidedId, world, loc);
    }

    @EventHandler
    public void onRaidEnd(RaidEndEvent event) {
        Raid raid = event.getRaid();
        String raiderId = event.getFactionRaiding();
        String raidedId = event.getFactionRaided();
        if (raid != null) {
            if (raiderId == null) {
                raiderId = raid.getFaction();
            }
            if (raidedId == null) {
                raidedId = raid.getRaided();
            }
        }

        int raiderKills = raid != null ? raid.getRaidingKills() : 0;
        int defenderKills = raid != null ? raid.getRaidedKills() : 0;

        RaidManager.getInstance().handleRaidEnd(raiderId, raidedId, raiderKills, defenderKills);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }

        FPlayer fPlayer = FPlayers.getInstance().getByPlayer(player);
        if (fPlayer == null || !fPlayer.hasFaction()) {
            return;
        }

        RaidManager.getInstance().recordBlockPlaced(fPlayer.getFaction(), event.getBlock().getWorld());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player dead = event.getEntity();
        if (dead == null) {
            return;
        }

        Player killer = dead.getKiller();
        if (killer == null && dead.getLastDamageCause() instanceof EntityDamageByEntityEvent) {
            Entity damager = ((EntityDamageByEntityEvent) dead.getLastDamageCause()).getDamager();
            if (damager instanceof Player) {
                killer = (Player) damager;
            } else if (damager instanceof Projectile && ((Projectile) damager).getShooter() instanceof Player) {
                killer = (Player) ((Projectile) damager).getShooter();
            }
        }

        if (killer == null || killer.equals(dead)) {
            return;
        }

        FPlayer deadFPlayer = FPlayers.getInstance().getByPlayer(dead);
        FPlayer killerFPlayer = FPlayers.getInstance().getByPlayer(killer);
        if (deadFPlayer == null || killerFPlayer == null) {
            return;
        }
        if (!deadFPlayer.hasFaction() || !killerFPlayer.hasFaction()) {
            return;
        }

        Faction deadFaction = deadFPlayer.getFaction();
        Faction killerFaction = killerFPlayer.getFaction();
        if (deadFaction.equals(killerFaction)) {
            return;
        }

        RaidManager.getInstance().recordKill(killerFaction, deadFaction);
    }
}
