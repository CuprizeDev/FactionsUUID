package com.massivecraft.factions.cmd;

import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.event.FPlayerPingCoordsEvent;
import com.massivecraft.factions.struct.Permission;
import com.massivecraft.factions.util.TL;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;

public class CmdCoords extends FCommand {

    public CmdCoords() {
        super();
        this.aliases.add("coords");
        this.aliases.add("coord");
        this.aliases.add("location");
        this.aliases.add("tl");
        this.aliases.add("loc");

        this.requirements = new CommandRequirements.Builder(Permission.COORDS)
                .memberOnly()
                .build();
    }

    @Override
    public void perform(CommandContext context) {
        FPlayerPingCoordsEvent event = new FPlayerPingCoordsEvent(context.faction, context.fPlayer);
        Bukkit.getPluginManager().callEvent(event);
        if(event.isCancelled())
            return;
        Location location = context.player.getLocation();
        String message = TL.COMMAND_COORDS_MESSAGE.format(context.player.getDisplayName(), location.getBlockX(), location.getBlockY(), location.getBlockZ(), location.getWorld().getName());
        for (FPlayer fPlayer : context.faction.getFPlayers()) {
            TextComponent messageClick = new TextComponent(message);
            messageClick.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpa " + context.player.getDisplayName()));
            messageClick.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new net.md_5.bungee.api.chat.ComponentBuilder(TL.COMMAND_COORDS_CLICKABLE.format(context.player.getDisplayName())).create()));
            fPlayer.getPlayer().spigot().sendMessage(messageClick);
        }
    }

    @Override
    public TL getUsageTranslation() {
        return TL.COMMAND_COORDS_DESCRIPTION;
    }

}
