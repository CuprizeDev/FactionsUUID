package com.massivecraft.factions.cmd;

import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.FactionsPlugin;
import com.massivecraft.factions.listeners.FactionsFocusListener;
import com.massivecraft.factions.perms.PermissibleAction;
import com.massivecraft.factions.struct.Permission;
import com.massivecraft.factions.util.TL;

public class CmdFocus extends FCommand {

    public CmdFocus() {
        super();
        this.aliases.add("focus");
        this.aliases.add("unfocus");

        this.optionalArgs.put("player", "off");

        this.requirements = new CommandRequirements.Builder(Permission.FOCUS)
                .playerOnly()
                .memberOnly()
                .withAction(PermissibleAction.FOCUS)
                .noDisableOnLock()
                .build();
    }

    @Override
    public void perform(CommandContext context) {
        FactionsFocusListener focus = FactionsPlugin.getInstance().getFocus();
        if (focus == null) {
            context.msg(TL.COMMAND_FOCUS_DISABLED);
            return;
        }

        String arg = context.argAsString(0, "");
        if (arg.isEmpty() || arg.equalsIgnoreCase("off") || arg.equalsIgnoreCase("end")
                || arg.equalsIgnoreCase("cancel") || arg.equalsIgnoreCase("clear") || arg.equalsIgnoreCase("none")) {
            focus.end(context.fPlayer);
            return;
        }

        FPlayer target = context.argAsBestFPlayerMatch(0);
        if (target == null) {
            return;
        }

        focus.start(context.fPlayer, target);
    }

    @Override
    public TL getUsageTranslation() {
        return TL.COMMAND_FOCUS_DESCRIPTION;
    }
}
