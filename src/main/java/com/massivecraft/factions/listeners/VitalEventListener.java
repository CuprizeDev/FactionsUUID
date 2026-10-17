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
            case KOTH:
                faction.addPvPPoints(FactionsPlugin.getInstance().conf().commands().pvpTop().getKothPoints());
                break;
            case CONQUEST:
                faction.addPvPPoints(FactionsPlugin.getInstance().conf().commands().pvpTop().getConquestPoints());
                break;
            case CITADEL:
                faction.addPvPPoints(FactionsPlugin.getInstance().conf().commands().pvpTop().getCitadelPoints());
                break;
            case DTC:
                faction.addPvPPoints(FactionsPlugin.getInstance().conf().commands().pvpTop().getDtcPoints());
                break;
            case LTS:
                faction.addPvPPoints(FactionsPlugin.getInstance().conf().commands().pvpTop().getLtsPoints());
                break;
            case LMS:
                faction.addPvPPoints(FactionsPlugin.getInstance().conf().commands().pvpTop().getLmsPoints());
                break;
            case ENVOY:
                faction.addPvPPoints(FactionsPlugin.getInstance().conf().commands().pvpTop().getEnvoyPoints());
                break;
            case METEORS:
                faction.addPvPPoints(FactionsPlugin.getInstance().conf().commands().pvpTop().getMeteorsPoints());
                break;
            case MOB:
                faction.addPvPPoints(FactionsPlugin.getInstance().conf().commands().pvpTop().getMobPoints());
                break;
            case POI:
                faction.addPvPPoints(FactionsPlugin.getInstance().conf().commands().pvpTop().getPoiPoints());
                break;
            case SPLEEF:
                faction.addPvPPoints(FactionsPlugin.getInstance().conf().commands().pvpTop().getSpleefPoints());
                break;
            default:
                break;
        }


    }
}
