package com.massivecraft.factions.util;

import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.FPlayers;
import com.massivecraft.factions.FactionsPlugin;
import com.massivecraft.factions.event.FPlayerFlyDisableByEnemyNearbyEvent;
import com.massivecraft.factions.perms.Relation;
import com.massivecraft.factions.struct.Permission;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Collection;

public class FlightUtil {

    private static FlightUtil instance;

    private EnemiesTask enemiesTask;

    private FlightUtil() {
        double enemyCheck = FactionsPlugin.getInstance().conf().commands().fly().getRadiusCheck() * 20;
        if (enemyCheck > 0) {
            enemiesTask = new EnemiesTask();
            enemiesTask.runTaskTimerAsynchronously(FactionsPlugin.getInstance(), 0, (long) enemyCheck);
        }

        double spawnRate = FactionsPlugin.getInstance().conf().commands().fly().particles().getSpawnRate() * 20;
        if (spawnRate > 0) {
            new ParticleTrailsTask().runTaskTimer(FactionsPlugin.getInstance(), 0, (long) spawnRate);
        }
    }

    public static void start() {
        instance = new FlightUtil();
    }

    public static FlightUtil instance() {
        return instance;
    }

    public boolean enemiesNearby(FPlayer target, int radius) {
        if (this.enemiesTask == null) {
            return false;
        } else {
            return this.enemiesTask.enemiesNearby(target, radius);
        }
    }

    public static class EnemiesTask extends BukkitRunnable {

        @Override
        public void run() {
            Collection<FPlayer> players = FPlayers.getInstance().getOnlinePlayers();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if(Permission.FLY_ANY.has(player.getPlayer()))continue;

                FPlayer pilot = FPlayers.getInstance().getByPlayer(player);

                if(FactionsPlugin.getInstance().getConfigManager().getMainConfig().commands().fly().ignoreDisableInWorlds().contains(player.getWorld().getName()))
                    continue;

                if (pilot.isFlying() && !pilot.isAdminBypassing()) {
                    if (enemiesNearby(pilot, FactionsPlugin.getInstance().conf().commands().fly().getEnemyRadius(), players))
                    {//!pilot.getPlayer().hasPermission("essentials.fly") that code was moved to the FactionsPlayerListener class
                        FPlayerFlyDisableByEnemyNearbyEvent event = new FPlayerFlyDisableByEnemyNearbyEvent(pilot);
                        Bukkit.getPluginManager().callEvent(event);
                        if(event.isCancelled())
                            continue;
                        pilot.msg(TL.COMMAND_FLY_ENEMY_DISABLE);
                        pilot.setFlying(false);
                        if (pilot.isAutoFlying()) {
                            pilot.setAutoFlying(false);
                        }
                    }
                }
            }
        }

        public boolean enemiesNearby(FPlayer target, int radius) {
            return this.enemiesNearby(target, radius, FPlayers.getInstance().getOnlinePlayers());
        }

        public boolean enemiesNearby(FPlayer target, int radius, Collection<FPlayer> players) {
            if (!FactionsPlugin.getInstance().worldUtil().isEnabled(target.getPlayer().getWorld())) {
                return false;
            }

            if(target.getPlayer() != null && target.getPlayer().getGameMode() == GameMode.SPECTATOR)
                return false;

            int radiusSquared = radius * radius;
            boolean ignoreYLevel = FactionsPlugin.getInstance().conf().commands().fly().isIgnoreYPositionOnEnemyCheck();
            Location loc = target.getPlayer().getLocation().clone();
            if(ignoreYLevel)
                loc.setY(1);
            Location cur;
            for (FPlayer player : players) {
                if (player.isStealth() || player == target || (player.getPlayer() != null && Permission.FLY_ANY.has(player.getPlayer())) || player.isAdminBypassing() || (player.getPlayer() != null && player.getPlayer().getGameMode() == GameMode.SPECTATOR)) {
                    continue;
                }

                cur = player.getPlayer().getLocation().clone();
                if(ignoreYLevel)
                    cur.setY(1);

                if (cur.getWorld().getUID().equals(loc.getWorld().getUID()) &&
                        cur.distanceSquared(loc) <= radiusSquared &&
                        player.getRelationTo(target) == Relation.ENEMY &&
                        target.getPlayer().canSee(player.getPlayer())) {
                    return true;
                }
            }
            return false;
        }
    }

    public static class ParticleTrailsTask extends BukkitRunnable {

        private final int amount;
        private final float speed;

        private ParticleTrailsTask() {
            this.amount = FactionsPlugin.getInstance().conf().commands().fly().particles().getAmount();
            this.speed = (float) FactionsPlugin.getInstance().conf().commands().fly().particles().getSpeed();
        }

        @Override
        public void run() {
            for (Player player : Bukkit.getOnlinePlayers()) {
                FPlayer pilot = FPlayers.getInstance().getByPlayer(player);
                if (pilot.isFlying()) {
                    if (pilot.getFlyTrailsEffect() != null && Permission.FLY_TRAILS.has(player) && pilot.getFlyTrailsState()) {
                        Object effect = FactionsPlugin.getInstance().getParticleProvider().effectFromString(pilot.getFlyTrailsEffect());
                        FactionsPlugin.getInstance().getParticleProvider().spawn(effect, player.getLocation(), amount, speed, 0, 0, 0);
                    }
                }
            }
        }

    }

}
