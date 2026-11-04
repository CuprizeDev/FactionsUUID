package com.massivecraft.factions.cmd;

import com.massivecraft.factions.Faction;
import com.massivecraft.factions.FactionsPlugin;
import com.massivecraft.factions.gui.RaidHistoryGUI;
import com.massivecraft.factions.struct.Permission;
import com.massivecraft.factions.util.TL;

public class CmdRaids extends FCommand {

    public CmdRaids() {
        super();
        this.aliases.add("raids");
        this.aliases.add("raid");
        this.aliases.add("raidhistory");

        this.optionalArgs.put("faction", "all");

        this.requirements = new CommandRequirements.Builder(Permission.RAIDS)
                .playerOnly()
                .build();
    }

    @Override
    public void perform(CommandContext context) {
        if (!FactionsPlugin.getInstance().conf().commands().raids().isEnabled()) {
            context.msg(TL.COMMAND_RAIDS_DISABLED);
            return;
        }

        Faction targetFaction = null;
        if (context.argIsSet(0)) {
            targetFaction = context.argAsFaction(0);
            if (targetFaction == null) {
                return;
            }
        }

        RaidHistoryGUI gui = new RaidHistoryGUI(context.fPlayer, 1, targetFaction);
        gui.build();
        gui.open();
    }

    @Override
    public TL getUsageTranslation() {
        return TL.COMMAND_RAIDS_DESCRIPTION;
    }
}
