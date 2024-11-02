package com.massivecraft.factions.event;

import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.Faction;
import org.bukkit.Location;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

/**
 * Event called when a FPlayer does /f warp (0 args)
 */
public class FPlayerOpenWarpGUIEvent extends FactionPlayerEvent implements Cancellable
{
    private static final HandlerList handlers = new HandlerList();

    private boolean cancelled = false;

    public FPlayerOpenWarpGUIEvent(Faction fac, FPlayer player)
    {
        super(fac, player);
    }

    @Override
    public boolean isCancelled()
    {
        return cancelled;
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