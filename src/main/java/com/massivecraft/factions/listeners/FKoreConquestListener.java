package com.massivecraft.factions.listeners;

import com.golfing8.events.feature.conquest.event.ConquestWinEvent;
import com.massivecraft.factions.Faction;
import com.massivecraft.factions.Factions;
import com.massivecraft.factions.FactionsPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class FKoreConquestListener implements Listener {

    @EventHandler
    public void onWinConquest(ConquestWinEvent event) {

        Faction faction = Factions.getInstance().getFactionById(event.getWinningFactionID());

        if (faction == null) {
            return;
        }

        faction.addPvPPoints(FactionsPlugin.getInstance().conf().commands().pvpTop().getConquestPoints());
    }
}
