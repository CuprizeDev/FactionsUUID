package com.massivecraft.factions.cmd;

import com.massivecraft.factions.*;
import com.massivecraft.factions.struct.Permission;
import com.massivecraft.factions.util.TL;

public class CmdFriendlyFire extends FCommand {

    public CmdFriendlyFire() {
        super();
        this.aliases.add("ff");
        this.aliases.add("friendly");

        this.optionalArgs.put("on/off", "flip");

        this.requirements = new CommandRequirements.Builder(Permission.FRIENDLY_FIRE)
                .memberOnly()
                .build();
    }

    @Override
    public void perform(CommandContext context) {

        FPlayer fPlayer = FPlayers.getInstance().getByPlayer(context.player);

        if (context.args.isEmpty()) {
            fPlayer.setFriendlyFire(!fPlayer.isFriendlyFireOn());
            context.fPlayer.msg(TL.COMMAND_FRIENDLY_FIRE_CHANGE, fPlayer.isFriendlyFireOn() ? TL.GENERIC_ENABLED : TL.GENERIC_DISABLED);
        } else if (context.args.size() == 1) {
            boolean friendlyFire = false;
            if (context.argAsString(0).equalsIgnoreCase("on")) {
                fPlayer.setFriendlyFire(true);
                friendlyFire = true;
            }

            if (context.argAsString(0).equalsIgnoreCase("off")) {
                fPlayer.setFriendlyFire(false);
            }
            context.fPlayer.msg(TL.COMMAND_FRIENDLY_FIRE_CHANGE, friendlyFire ? TL.GENERIC_ENABLED : TL.GENERIC_DISABLED);
        }
    }


    @Override
    public TL getUsageTranslation() {
        return TL.COMMAND_FRIENDLY_FIRE_DESCRIPTION;
    }
}