package com.massivecraft.factions.event;

import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.perms.Role;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Event called when a FPlayer does /f tnt f
 */
public class FPlayerTNTFillEvent extends Event implements Cancellable
{
    private static final HandlerList handlers = new HandlerList();

    private final FPlayer fPlayer;
    private boolean cancelled = false;

    public FPlayerTNTFillEvent(FPlayer fPlayer)
    {
        this.fPlayer = fPlayer;
    }

    public FPlayer getFPlayer()
    {
        return fPlayer;
    }

    public boolean isAlt()
    {
        return getFPlayer().hasFaction() && getFPlayer().getRole() == Role.ALT;
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