package com.massivecraft.factions.cmd;

import com.massivecraft.factions.struct.Permission;
import com.massivecraft.factions.util.TL;

public class CmdSetDiscord extends FCommand {

    public CmdSetDiscord() {
        super();
        this.aliases.add("setdiscord");

        this.requiredArgs.add("discord link");

        this.requirements = new CommandRequirements.Builder(Permission.SETDISCORD)
                .memberOnly()
                .build();
    }

    @Override
    public void perform(CommandContext context) {
        CmdDiscordSet.handleSetDiscord(context);
    }

    @Override
    public TL getUsageTranslation() {
        return TL.COMMAND_DISCORD_SET_DESCRIPTION;
    }
}
