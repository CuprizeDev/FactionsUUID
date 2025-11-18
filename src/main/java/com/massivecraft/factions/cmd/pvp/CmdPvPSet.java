package com.massivecraft.factions.cmd.pvp;

import com.massivecraft.factions.Faction;
import com.massivecraft.factions.cmd.CommandContext;
import com.massivecraft.factions.cmd.CommandRequirements;
import com.massivecraft.factions.cmd.FCommand;
import com.massivecraft.factions.struct.Permission;
import com.massivecraft.factions.util.TL;

public class CmdPvPSet extends FCommand {
    public CmdPvPSet() {
        super();
        this.aliases.add("set");
        this.aliases.add("modify");

        this.requiredArgs.add("faction");

        this.requiredArgs.add("amount");

        this.requirements = new CommandRequirements.Builder(Permission.PVP_MANAGE).noErrorOnManyArgs().build();
    }

    @Override
    public void perform(CommandContext context) {

        Faction faction = context.argAsFaction(0);
        if(faction == null)return;
        int amount = context.argAsInt(1);
        if(amount < 0)return;

        faction.setPvPPoints(amount);
        context.msg(TL.COMMAND_PVPTOP_SET_SUCCESS, amount);
    }

    @Override
    public TL getUsageTranslation() {
        return TL.COMMAND_PVPTOP_SET_USAGE;
    }
}
