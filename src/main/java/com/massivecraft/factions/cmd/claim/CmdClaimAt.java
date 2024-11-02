package com.massivecraft.factions.cmd.claim;

import com.massivecraft.factions.Board;
import com.massivecraft.factions.FLocation;
import com.massivecraft.factions.Faction;
import com.massivecraft.factions.FactionsPlugin;
import com.massivecraft.factions.cmd.CommandContext;
import com.massivecraft.factions.cmd.CommandRequirements;
import com.massivecraft.factions.cmd.FCommand;
import com.massivecraft.factions.struct.Permission;
import com.massivecraft.factions.util.TL;
import org.bukkit.Bukkit;

public class CmdClaimAt extends FCommand
{

    public CmdClaimAt()
    {
        super();
        this.aliases.add("claimat");

        this.requiredArgs.add("world");
        this.requiredArgs.add("x");
        this.requiredArgs.add("z");
        this.optionalArgs.put("showmap", "true/false");

        this.requirements = new CommandRequirements.Builder(Permission.CLAIMAT)
                .memberOnly()
                .build();
    }

    @Override
    public void perform(CommandContext context)
    {
        int x = context.argAsInt(1);
        int z = context.argAsInt(2);
        FLocation targetLocation = new FLocation(context.argAsString(0), x, z);
        FLocation playerLocation = context.fPlayer.getLastStoodAt();

        if(FactionsPlugin.getInstance().conf().factions().claims().isLimitClaimAtDistance() && !context.fPlayer.isAdminBypassing())
        {
            if(targetLocation.getWorld().getName().equals(playerLocation.getWorld().getName()))
            {
                long distanceX = Math.abs(targetLocation.getX() - playerLocation.getX());
                long distanceZ = Math.abs(targetLocation.getZ() - playerLocation.getZ());
                long distance = Math.max(distanceX, distanceZ);
                if(distance > FactionsPlugin.getInstance().conf().factions().claims().getMaxClaimAtDistance())
                {
                    context.fPlayer.msg(TL.COMMAND_CLAIMAT_TOOFAR_CHAT, FactionsPlugin.getInstance().conf().factions().claims().getMaxClaimAtDistance());
                    return;
                }

                if(context.fPlayer.attemptClaim(context.faction, targetLocation, true) && context.argAsBool(3, false))
                {
                    context.sendFancyMessage(Board.getInstance().getMap(context.fPlayer, new FLocation(context.fPlayer), context.fPlayer.getPlayer().getLocation().getYaw()));
                    //refresh map after a claim was made.
                }
            }
            else
            {
                context.fPlayer.msg(TL.COMMAND_CLAIMAT_WRONGWORLD.toString());
            }
        }
        else
        {
            if(context.fPlayer.attemptClaim(context.faction, targetLocation, true))
            {
                if(context.argAsBool(3, false))
                    context.sendFancyMessage(Board.getInstance().getMap(context.fPlayer, new FLocation(context.fPlayer), context.fPlayer.getPlayer().getLocation().getYaw()));
            }
        }
    }


    @Override
    public TL getUsageTranslation()
    {
        return null;
    }
}
