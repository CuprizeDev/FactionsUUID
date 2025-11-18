package com.massivecraft.factions.listeners;

import com.massivecraft.factions.FPlayers;
import com.massivecraft.factions.Faction;
import com.massivecraft.factions.FactionsPlugin;
import com.vitaldev.events.events.EventType;
import com.vitaldev.events.events.VitalWinEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class VitalEventListener implements Listener {

    @EventHandler
    public void onEventWin(VitalWinEvent event) {

        Player player = event.getWinner();
        Faction faction = FPlayers.getInstance().getByPlayer(player).getFaction();

        if (faction == null) {
            return;
        }

        switch (event.getEventType()) {
            case DTC:
                faction.addPvPPoints(FactionsPlugin.getInstance().conf().commands().pvpTop().getDtcPoints());
                break;
            case LTS:
                faction.addPvPPoints(FactionsPlugin.getInstance().conf().commands().pvpTop().getLtsPoints());
                break;
            case LMS:
                faction.addPvPPoints(FactionsPlugin.getInstance().conf().commands().pvpTop().getLmsPoints());
                break;
            default:
                break;
        }


    }
}
