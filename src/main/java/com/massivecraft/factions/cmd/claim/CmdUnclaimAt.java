package com.massivecraft.factions.cmd.claim;

import com.massivecraft.factions.Board;
import com.massivecraft.factions.FLocation;
import com.massivecraft.factions.Faction;
import com.massivecraft.factions.FactionsPlugin;
import com.massivecraft.factions.cmd.CommandContext;
import com.massivecraft.factions.cmd.CommandRequirements;
import com.massivecraft.factions.cmd.FCommand;
import com.massivecraft.factions.event.LandUnclaimEvent;
import com.massivecraft.factions.integration.Econ;
import com.massivecraft.factions.perms.PermissibleAction;
import com.massivecraft.factions.struct.Permission;
import com.massivecraft.factions.util.SpiralTask;
import com.massivecraft.factions.util.TL;
import org.bukkit.Bukkit;

public class CmdUnclaimAt extends FCommand
{

    public CmdUnclaimAt()
    {
        super();
        this.aliases.add("unclaimat");

        this.requiredArgs.add("world");
        this.requiredArgs.add("x");
        this.requiredArgs.add("z");
        this.optionalArgs.put("showmap", "true/false");
        this.optionalArgs.put("faction", "you");

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
                    context.fPlayer.msg(TL.COMMAND_UNCLAIMAT_TOOFAR_CHAT, FactionsPlugin.getInstance().conf().factions().claims().getMaxClaimAtDistance());
                    return;
                }

                if(unClaim(targetLocation, context, context.faction, null) && context.argAsBool(3, false))
                {
                    context.sendFancyMessage(Board.getInstance().getMap(context.fPlayer, new FLocation(context.fPlayer), context.fPlayer.getPlayer().getLocation().getYaw()));
                    //refresh map after a unclaim was made.
                }
            }
            else
            {
                context.fPlayer.msg(TL.COMMAND_CLAIMAT_WRONGWORLD.toString());
            }
        }
        else
        {
            if(unClaim(targetLocation, context, context.faction, null))
            {
                if(context.argAsBool(3, false))
                    context.sendFancyMessage(Board.getInstance().getMap(context.fPlayer, new FLocation(context.fPlayer), context.fPlayer.getPlayer().getLocation().getYaw()));
            }
        }
    }


    private boolean unClaim(FLocation target, CommandContext context, Faction faction, SpiralTask spiralTask) {
        Faction targetFaction = Board.getInstance().getFactionAt(target);

        if (targetFaction.isWilderness()) { // Just ignore wilderness...
            return true;
        }

        if (!targetFaction.equals(faction) && !context.fPlayer.isAdminBypassing()) {
            context.msg(TL.COMMAND_UNCLAIM_WRONGFACTIONOTHER);
            return false;
        }

        if (targetFaction.isSafeZone()) {
            if (Permission.MANAGE_SAFE_ZONE.has(context.sender)) {
                Board.getInstance().removeAt(target);
                if(spiralTask == null){
                    context.msg(TL.COMMAND_UNCLAIM_SAFEZONE_SUCCESS);
                }

                if (FactionsPlugin.getInstance().conf().logging().isLandUnclaims()) {
                    FactionsPlugin.getInstance().log(TL.COMMAND_UNCLAIM_LOG.format(context.fPlayer.getName(), target.getCoordString(), targetFaction.getTag()));
                }
                return true;
            } else {
                context.msg(TL.COMMAND_UNCLAIM_SAFEZONE_NOPERM);
                return false;
            }
        } else if (targetFaction.isWarZone()) {
            if (Permission.MANAGE_WAR_ZONE.has(context.sender)) {
                Board.getInstance().removeAt(target);
                if(spiralTask == null){
                    context.msg(TL.COMMAND_UNCLAIM_WARZONE_SUCCESS);
                }

                if (FactionsPlugin.getInstance().conf().logging().isLandUnclaims()) {
                    FactionsPlugin.getInstance().log(TL.COMMAND_UNCLAIM_LOG.format(context.fPlayer.getName(), target.getCoordString(), targetFaction.getTag()));
                }
                return true;
            } else {
                context.msg(TL.COMMAND_UNCLAIM_WARZONE_NOPERM);
                return false;
            }
        }

        if (context.fPlayer.isAdminBypassing()) {
            LandUnclaimEvent unclaimEvent = new LandUnclaimEvent(target, targetFaction, context.fPlayer);
            Bukkit.getServer().getPluginManager().callEvent(unclaimEvent);
            if (unclaimEvent.isCancelled()) {
                return false;
            }

            Board.getInstance().removeAt(target);

            if(spiralTask == null){
                targetFaction.msg(TL.COMMAND_UNCLAIM_UNCLAIMED, context.fPlayer.describeTo(targetFaction, true));
                context.msg(TL.COMMAND_UNCLAIM_UNCLAIMS);
            }

            if (FactionsPlugin.getInstance().conf().logging().isLandUnclaims()) {
                FactionsPlugin.getInstance().log(TL.COMMAND_UNCLAIM_LOG.format(context.fPlayer.getName(), target.getCoordString(), targetFaction.getTag()));
            }

            return true;
        }

        if (!context.assertHasFaction()) {
            return false;
        }

        if (!targetFaction.hasAccess(context.fPlayer, PermissibleAction.TERRITORY)) {
            context.msg(TL.CLAIM_CANTCLAIM, targetFaction.describeTo(context.fPlayer));
            return false;
        }

        if (context.faction != targetFaction) {
            context.msg(TL.COMMAND_UNCLAIM_WRONGFACTION);
            return false;
        }

        LandUnclaimEvent unclaimEvent = new LandUnclaimEvent(target, targetFaction, context.fPlayer);
        Bukkit.getServer().getPluginManager().callEvent(unclaimEvent);
        if (unclaimEvent.isCancelled()) {
            return false;
        }

        if (Econ.shouldBeUsed()) {
            double refund = Econ.calculateClaimRefund(context.faction.getLandRounded());

            if (FactionsPlugin.getInstance().conf().economy().isBankEnabled() && FactionsPlugin.getInstance().conf().economy().isBankFactionPaysLandCosts()) {
                if (!Econ.modifyMoney(context.faction, refund, TL.COMMAND_UNCLAIM_TOUNCLAIM.toString(), TL.COMMAND_UNCLAIM_FORUNCLAIM.toString())) {
                    return false;
                }
            } else {
                if (!Econ.modifyMoney(context.fPlayer, refund, TL.COMMAND_UNCLAIM_TOUNCLAIM.toString(), TL.COMMAND_UNCLAIM_FORUNCLAIM.toString())) {
                    return false;
                }
            }
        }

        Board.getInstance().removeAt(target);
        if(spiralTask == null){
            context.faction.msg(TL.COMMAND_UNCLAIM_FACTIONUNCLAIMED, context.fPlayer.describeTo(context.faction, true));
        }

        if (FactionsPlugin.getInstance().conf().logging().isLandUnclaims()) {
            FactionsPlugin.getInstance().log(TL.COMMAND_UNCLAIM_LOG.format(context.fPlayer.getName(), target.getCoordString(), targetFaction.getTag()));
        }

        return true;
    }

    @Override
    public TL getUsageTranslation()
    {
        return null;
    }
}
