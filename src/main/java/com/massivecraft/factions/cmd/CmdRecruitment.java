package com.massivecraft.factions.cmd;

import com.massivecraft.factions.gui.RecruitmentListGUI;
import com.massivecraft.factions.gui.RecruitmentManageGUI;
import com.massivecraft.factions.struct.Permission;
import com.massivecraft.factions.util.TL;

public class CmdRecruitment extends FCommand {

    public CmdRecruitment() {
        super();
        this.aliases.add("recruitment");
        this.aliases.add("recruitments");
        this.aliases.add("recruit");

        this.optionalArgs.put("manage", "board");

        this.requirements = new CommandRequirements.Builder(Permission.RECRUITMENT)
                .playerOnly()
                .build();
    }

    @Override
    public void perform(CommandContext context) {
        if (context.argIsSet(0)) {
            String sub = context.argAsString(0).toLowerCase();
            if (sub.equals("manage") || sub.equals("edit") || sub.equals("settings") || sub.equals("config")) {
                if (!context.fPlayer.hasFaction()) {
                    context.msg(TL.GENERIC_MEMBERONLY);
                    return;
                }
                if (!RecruitmentListGUI.canManageRecruitment(context.fPlayer)) {
                    context.msg(TL.COMMAND_RECRUITMENT_NOROLE);
                    return;
                }
                RecruitmentManageGUI manageGUI = new RecruitmentManageGUI(context.fPlayer, null);
                manageGUI.build();
                manageGUI.open();
                return;
            }
        }

        RecruitmentListGUI listGUI = new RecruitmentListGUI(context.fPlayer, 1);
        listGUI.build();
        listGUI.open();
    }

    @Override
    public TL getUsageTranslation() {
        return TL.COMMAND_RECRUITMENT_DESCRIPTION;
    }
}
