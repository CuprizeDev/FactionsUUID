package com.massivecraft.factions.listeners;

import com.golfing8.kore.event.KothCaptureEvent;
import com.golfing8.kore.event.RaidingOutpostResetEvent;
import com.golfing8.kore.event.roam.PlayerRoamEnterEvent;
import com.golfing8.kore.event.roam.PlayerRoamExitEvent;
import com.golfing8.kore.event.sandbot.SandBotBreakEvent;
import com.golfing8.kore.event.sandbot.SandBotPlaceEvent;
import com.massivecraft.factions.*;
import com.massivecraft.factions.perms.Relation;
import com.massivecraft.factions.util.TL;
import com.massivecraft.factions.util.UpgradeType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class FKoreListener implements Listener {

    private final FactionsPlugin plugin;

    public FKoreListener(FactionsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onKothWin(KothCaptureEvent event) {

        Player player = event.getPlayerCapper();
        Faction faction = FPlayers.getInstance().getByPlayer(player).getFaction();

        if (faction == null) {
            return;
        }

        faction.addPvPPoints(FactionsPlugin.getInstance().conf().commands().pvpTop().getKothPoints());
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

    @EventHandler
    public void onSandBotRemove(RaidingOutpostResetEvent event) {
        if(!FactionsPlugin.getInstance().conf().upgrades().sandbots().isEnabled()) {
            return;
        }

    }

    List<Player> flying = new ArrayList<>();
    HashMap<Player, Location> lastStoodAt = new HashMap<>();

    @EventHandler
    public void onEnterRoam(PlayerRoamEnterEvent event) {

        Player player = event.getPlayer();

        if (player.isFlying() && FPlayers.getInstance().getByPlayer(player).isFlying()) {
            flying.add(player);
            lastStoodAt.put(player, event.getPlayer().getLocation());
        }

    }

    @EventHandler
    public void onExitRoam(PlayerRoamExitEvent event) {
        Player player = event.getPlayer();
        if (flying.contains(player)) {

            FPlayer fPlayer = FPlayers.getInstance().getByPlayer(player);
            fPlayer.setLastStoodAt(new FLocation(lastStoodAt.get(player)));

            if (FPlayers.getInstance().getByPlayer(player).getRelationToLocation() == Relation.ENEMY) {
                return;
            }

            player.setAllowFlight(true);
            FPlayers.getInstance().getByPlayer(player).setFlying(true);
        }
    }

}
