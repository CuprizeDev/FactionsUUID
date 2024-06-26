package com.massivecraft.factions.event;

import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.perms.Role;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Event called when a FPlayer toggles f stealth
 */
public class FPlayerToggleStealthEvent extends Event implements Cancellable
{

    private static final HandlerList handlers = new HandlerList();

    private final FPlayer fPlayer;
    private boolean cancelled = false;
    private Type type;

    public FPlayerToggleStealthEvent(FPlayer fPlayer, Type type)
    {
        this.fPlayer = fPlayer;
    }

    public FPlayer getfPlayer()
    {
        return fPlayer;
    }

    public Type getType()
    {
        return type;
    }

    public boolean isAlt()
    {
        return getfPlayer().hasFaction() && getfPlayer().getRole() == Role.ALT;
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

    public enum Type
    {
        COMMAND, METHOD;
    }
}