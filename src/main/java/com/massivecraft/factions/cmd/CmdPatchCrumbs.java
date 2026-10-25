package com.massivecraft.factions.cmd;

import com.massivecraft.factions.listeners.FactionsPatchCrumbsListener;
import com.massivecraft.factions.struct.Permission;
import com.massivecraft.factions.util.TL;

public class CmdPatchCrumbs extends FCommand {

    public CmdPatchCrumbs() {
        super();
        this.aliases.add("patchcrumbs");
        this.aliases.add("crumbs");
        this.aliases.add("pc");
        this.aliases.add("patch");

        this.optionalArgs.put("debug", "toggle debug mode");

        this.requirements = new CommandRequirements.Builder(Permission.PATCH_CRUMBS)
                .playerOnly()
                .memberOnly()
                .build();
    }

    @Override
    public void perform(CommandContext context) {
        if (context.args.size() > 0 && context.args.get(0).equalsIgnoreCase("debug")) {
            if (!context.player.isOp() && !Permission.DEBUG.has(context.player, false)) {
                context.msg(TL.GENERIC_NOPERMISSION, "factions.debug");
                return;
            }
            FactionsPatchCrumbsListener.DEBUG = !FactionsPatchCrumbsListener.DEBUG;
            context.msg("&ePatch Crumbs debug mode is now %s&e.", FactionsPatchCrumbsListener.DEBUG ? "&aENABLED" : "&cDISABLED");
            return;
        }

        boolean toggle = !context.fPlayer.hasPatchCrumbs();
        context.fPlayer.setPatchCrumbs(toggle);
        if (toggle) {
            context.msg(TL.COMMAND_PATCHCRUMBS_ENABLED);
        } else {
            context.msg(TL.COMMAND_PATCHCRUMBS_DISABLED);
        }
    }

    @Override
    public TL getUsageTranslation() {
        return TL.COMMAND_PATCHCRUMBS_DESCRIPTION;
    }
}
