package com.massivecraft.factions.cmd.pvp;

import com.massivecraft.factions.cmd.CommandContext;
import com.massivecraft.factions.cmd.CommandRequirements;
import com.massivecraft.factions.cmd.FCommand;
import com.massivecraft.factions.struct.Permission;
import com.massivecraft.factions.util.TL;

public class CmdPvPTop extends FCommand {

    @Override
    public void perform(CommandContext context) {

        this.aliases.add("top");

        this.requirements = new CommandRequirements.Builder(Permission.PVP).build();
    }

    @Override
    public TL getUsageTranslation() {
        return TL.COMMAND_PVPTOP_DESCRIPTION;
    }
}
