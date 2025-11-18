package com.massivecraft.factions.listeners;

import com.golfing8.events.feature.lms.bevent.LMSEndEvent;
import com.massivecraft.factions.FPlayers;
import com.massivecraft.factions.Faction;
import com.massivecraft.factions.FactionsPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class FKoreLMSListener implements Listener {

    @EventHandler
    public void onWinLMS(LMSEndEvent event) {
        Faction faction = FPlayers.getInstance().getByPlayer(event.getWinner()).getFaction();

        if (faction == null) {
            return;
        }

        faction.addPvPPoints(FactionsPlugin.getInstance().conf().commands().pvpTop().getLmsPoints());
    }
}
