package com.massivecraft.factions.listeners;

import com.massivecraft.factions.FPlayers;
import com.massivecraft.factions.Faction;
import com.massivecraft.factions.FactionsPlugin;
import com.massivecraft.factions.util.UpgradeType;
import com.vitaldev.core.events.VitalHarvestEvent;
import com.vitaldev.core.events.VitalMobKillEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.Random;

public class VitalCoreListener implements Listener {

    public final FactionsPlugin plugin;
    private final Random rand = new Random();

    public VitalCoreListener(FactionsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onExpUpgradeMobKill(VitalMobKillEvent event) {
        Player player = event.getPlayer();
        Faction faction = FPlayers.getInstance().getByPlayer(player).getFaction();
        player.giveExp((int) (event.getExpAmount()*
                FactionsPlugin.getInstance().conf().upgrades().exp().getNumber(faction.getUpgrade(UpgradeType.EXP))));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMobKill(VitalMobKillEvent event) {

        if(!FactionsPlugin.getInstance().conf().upgrades().mobcoins().isEnabled()) {
            return;
        }
        Faction faction = FPlayers.getInstance().getByPlayer(event.getPlayer()).getFaction();

        if (rand.nextInt(100) > toPercentageValue(FactionsPlugin.getInstance().conf().upgrades().mobcoins().getNumber(faction.getUpgrade(UpgradeType.MOBCOINS))))
            return;

        event.setMobcoinAmount(event.getMobcoinAmount()*2);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHarvest(VitalHarvestEvent event) {

        if(!FactionsPlugin.getInstance().conf().upgrades().tokens().isEnabled()) {
            return;
        }
        Faction faction = FPlayers.getInstance().getByPlayer(event.getPlayer()).getFaction();

        if (rand.nextInt(100) > toPercentageValue(FactionsPlugin.getInstance().conf().upgrades().tokens().getNumber(faction.getUpgrade(UpgradeType.TOKENS))))
            return;

        event.setTokenAmount(event.getTokenAmount()*2);

    }

    public double toPercentageValue(double value) {
        return value * 100.0;
    }

}
