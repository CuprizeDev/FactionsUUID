package com.massivecraft.factions.cmd;

import com.massivecraft.factions.FactionsPlugin;
import com.massivecraft.factions.listeners.FactionsRallyListener;
import com.massivecraft.factions.struct.Permission;
import com.massivecraft.factions.util.TL;

public class CmdRally extends FCommand {

    public CmdRally() {
        super();
        this.aliases.add("rally");

        this.optionalArgs.put("off", "end");

        this.requirements = new CommandRequirements.Builder(Permission.RALLY)
                .playerOnly()
                .memberOnly()
                .noDisableOnLock()
                .build();
    }

    @Override
    public void perform(CommandContext context) {
        FactionsRallyListener rally = FactionsPlugin.getInstance().getRally();
        if (rally == null) {
            context.msg(TL.COMMAND_RALLY_DISABLED);
            return;
        }

        String arg = context.argAsString(0, "");
        if (arg.equalsIgnoreCase("off") || arg.equalsIgnoreCase("end") || arg.equalsIgnoreCase("cancel") || arg.equalsIgnoreCase("clear")) {
            rally.end(context.fPlayer);
            return;
        }

        rally.start(context.fPlayer);
    }

    @Override
    public TL getUsageTranslation() {
        return TL.COMMAND_RALLY_DESCRIPTION;
    }
}
