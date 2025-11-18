package com.massivecraft.factions.cmd.pvp;

import com.massivecraft.factions.cmd.CommandContext;
import com.massivecraft.factions.cmd.CommandRequirements;
import com.massivecraft.factions.cmd.FCommand;
import com.massivecraft.factions.struct.Permission;
import com.massivecraft.factions.util.TL;

public class CmdPvPInfo extends FCommand {
    public CmdPvPInfo() {
        super();
        this.aliases.add("info");
        this.aliases.add("status");
        this.aliases.add("b");

        this.requirements = new CommandRequirements.Builder(Permission.TNT_INFO).memberOnly().build();
    }

    @Override
    public void perform(CommandContext context) {
        context.msg(TL.COMMAND_PVPTOP_INFO_MESSAGE, context.faction.getPvPPoints());
    }

    @Override
    public TL getUsageTranslation() {
        return TL.COMMAND_PVPTOP_INFO_DESCRIPTION;
    }
}
