package com.massivecraft.factions.listeners;

import com.massivecraft.factions.Board;
import com.massivecraft.factions.FLocation;
import com.massivecraft.factions.Faction;
import com.massivecraft.factions.FactionsPlugin;
import com.massivecraft.factions.util.UpgradeType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.SpawnerSpawnEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class FactionsSpawnerBoostListener extends AbstractListener{

    public FactionsPlugin plugin;

    public FactionsSpawnerBoostListener(FactionsPlugin plugin) {
        this.plugin = plugin;
    }

    private long lastSpawnTime = -1L;
    private Location lastSpawnLocation = null;

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSpawn(SpawnerSpawnEvent e) {
        if(!FactionsPlugin.getInstance().conf().upgrades().spawnerBoost().isEnabled())
            return;

        if (lastSpawnLocation == null) {
            lastSpawnLocation = e.getSpawner().getLocation();
            lastSpawnTime = System.currentTimeMillis();
        } else if (lastSpawnLocation.equals(e.getSpawner().getLocation())) {
            long deltaTime = System.currentTimeMillis() - lastSpawnTime;
            // If it's been less than a 'reasonable' amount of time, just skip it.
            if (deltaTime < 20L)
                return;

            lastSpawnTime = System.currentTimeMillis();
        } else {
            lastSpawnLocation = e.getSpawner().getLocation();
        }

        FLocation fLocation = new FLocation(e.getSpawner().getLocation());

        Faction factionAt = Board.getInstance().getFactionAt(fLocation);

        if(factionAt.isWilderness())return;

        int upgrade = factionAt.getUpgrade(UpgradeType.SPAWNER_BOOST);

        if(upgrade <= 0 && FactionsPlugin.getInstance().conf().upgrades().spawnerBoost().getDefaultBoost() == 1.0D)return;

        double boost = FactionsPlugin.getInstance().conf().upgrades().spawnerBoost().getNumber(upgrade);

        CreatureSpawner creatureSpawner = e.getSpawner();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            creatureSpawner.setDelay((int) Math.floor(creatureSpawner.getDelay() * boost));
        }, 1);
    }
}
