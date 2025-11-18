package com.massivecraft.factions.cmd.pvp;

import com.massivecraft.factions.Faction;
import com.massivecraft.factions.cmd.CommandContext;
import com.massivecraft.factions.cmd.CommandRequirements;
import com.massivecraft.factions.cmd.FCmdRoot;
import com.massivecraft.factions.cmd.FCommand;
import com.massivecraft.factions.struct.Permission;
import com.massivecraft.factions.util.TL;

public class CmdPvPHelp extends FCommand {
    public CmdPvPHelp() {
        super();
        this.aliases.add("help");

        this.requirements = new CommandRequirements.Builder(Permission.PVP).build();
    }

    @Override
    public void perform(CommandContext context) {
        FCmdRoot.getInstance().cmdAutoHelp.execute(context);
    }

    @Override
    public TL getUsageTranslation() {
        return TL.COMMAND_PVPTOP_HELP_DESCRIPTION;
    }
}
