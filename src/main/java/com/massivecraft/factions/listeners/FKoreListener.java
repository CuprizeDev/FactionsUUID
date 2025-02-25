package com.massivecraft.factions.listeners;

import com.golfing8.kore.event.StackedEntityDeathEvent;
import com.golfing8.kore.event.sandbot.SandBotBreakEvent;
import com.golfing8.kore.event.sandbot.SandBotPlaceEvent;
import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.FPlayers;
import com.massivecraft.factions.Faction;
import com.massivecraft.factions.FactionsPlugin;
import com.massivecraft.factions.util.TL;
import com.massivecraft.factions.util.UpgradeType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class FKoreListener implements Listener {

    private final FactionsPlugin plugin;

    public FKoreListener(FactionsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onSandBotPlace(SandBotPlaceEvent event) {
        if(!FactionsPlugin.getInstance().conf().upgrades().sandbots().isEnabled()) {
            return;
        }

        FPlayer fPlayer = FPlayers.getInstance().getByPlayer(event.getPlayer());
        Faction faction = fPlayer.getFaction();

        if (faction.getSandBotCount() >=
                FactionsPlugin.getInstance().conf().upgrades().sandbots().getNumber(faction.getUpgrade(UpgradeType.SANDBOTS))) {
            fPlayer.msg(TL.GENERIC_SANDBOT_LIMIT);
            event.setCancelled(true);
            return;
        }
        faction.addSandBotLocation(event.getBlockPlaced().getLocation());
    }

    @EventHandler
    public void onSandBotBreak(SandBotBreakEvent event) {
        if(!FactionsPlugin.getInstance().conf().upgrades().sandbots().isEnabled()) {
            return;
        }

        Faction faction = FPlayers.getInstance().getByPlayer(event.getPlayer()).getFaction();
        faction.removeSandBotLocation(event.getSandBot().getLocation());
    }

}
