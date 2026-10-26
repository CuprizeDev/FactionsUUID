package com.massivecraft.factions.listeners;

import com.massivecraft.factions.Board;
import com.massivecraft.factions.FLocation;
import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.Faction;
import com.massivecraft.factions.FactionsPlugin;
import com.massivecraft.factions.config.file.MainConfig;
import com.massivecraft.factions.event.LandClaimEvent;
import com.massivecraft.factions.util.TL;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class FactionsSpawnClaimListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClaim(LandClaimEvent event) {
        MainConfig.SpawnClaimLimit conf = FactionsPlugin.getInstance().conf().spawnClaimLimit();
        Faction faction = event.getFaction();
        FPlayer fPlayer = event.getfPlayer();
        if (faction == null || !faction.isNormal() || (fPlayer != null && fPlayer.isAdminBypassing())) {
            return;
        }
        FLocation target = event.getLocation();
        if (!conf.contains(target.getWorldName(), target.getX(), target.getZ())) {
            return;
        }
        int count = 0;
        for (FLocation claim : Board.getInstance().getAllClaims(faction)) {
            if (conf.contains(claim.getWorldName(), claim.getX(), claim.getZ())) {
                count++;
            }
        }
        if (count < conf.getMaxClaims()) {
            return;
        }
        event.setCancelled(true);
        if (fPlayer != null) {
            fPlayer.msg(TL.SPAWNCLAIM_LIMIT, conf.getMaxClaims(), conf.getRadius(target.getWorldName()));
        }
    }
}
