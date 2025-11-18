package com.massivecraft.factions.cmd.pvp;

import com.massivecraft.factions.Faction;
import com.massivecraft.factions.cmd.CommandContext;
import com.massivecraft.factions.cmd.CommandRequirements;
import com.massivecraft.factions.cmd.FCommand;
import com.massivecraft.factions.struct.Permission;
import com.massivecraft.factions.util.TL;

public class CmdPvPTake extends FCommand {
    public CmdPvPTake(){
        super();
        this.aliases.add("take");
        this.aliases.add("remove");

        this.requiredArgs.add("faction");

        this.requiredArgs.add("amount");

        this.requirements = new CommandRequirements.Builder(Permission.PVP_MANAGE).noErrorOnManyArgs().build();
    }

    @Override
    public void perform(CommandContext context) {

        Faction faction = context.argAsFaction(0);

        int index = context.argAsInt(1);

        if (index < 0) {
            return;
        }

        if (faction == null) {
            return;
        }

        if (faction.getPvPPoints()-index < 0) {
            if (context.fPlayer != null) context.fPlayer.msg(TL.COMMAND_PVPTOP_TAKE_FAIL);
        } else {
            faction.removePvPPoints(index);
            if (context.fPlayer != null) {
                context.fPlayer.msg(TL.COMMAND_PVPTOP_TAKE_SUCCESS, faction.describeTo(context.fPlayer));
                faction.removePvPPoints(index);
            }
        }
    }

    @Override
    public TL getUsageTranslation() {
        return TL.COMMAND_PVPTOP_TAKE_USAGE;
    }
}
