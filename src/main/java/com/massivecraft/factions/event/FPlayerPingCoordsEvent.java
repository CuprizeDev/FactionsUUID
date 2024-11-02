package com.massivecraft.factions.event;

import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.FPlayers;
import com.massivecraft.factions.Faction;
import com.massivecraft.factions.perms.Role;
import org.bukkit.Location;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Event called when a FPlayer does /f coords
 */
public class FPlayerPingCoordsEvent extends FactionPlayerEvent implements Cancellable
{
    private static final HandlerList handlers = new HandlerList();

    private boolean cancelled = false;

    public FPlayerPingCoordsEvent(Faction fac, FPlayer player)
    {
        super(fac, player);
    }

    @Override
    public boolean isCancelled()
    {
        return cancelled;
    }

    public Location getLocation()
    {
        return getfPlayer().getPlayer().getLocation();
    }

    @Override
    public void setCancelled(boolean cancelled)
    {
        this.cancelled = cancelled;
    }

    @Override
    public HandlerList getHandlers()
    {
        return handlers;
    }

    public static HandlerList getHandlerList()
    {
        return handlers;
    }
}