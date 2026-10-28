package com.massivecraft.factions.cmd;

import com.massivecraft.factions.Faction;
import com.massivecraft.factions.struct.Permission;
import com.massivecraft.factions.util.TL;
import com.massivecraft.factions.util.TextUtil;
import mkremins.fanciful.FancyMessage;

public class CmdDiscord extends FCommand {

    public CmdDiscord() {
        super();
        this.aliases.add("discord");
        this.aliases.add("dc");

        this.optionalArgs.put("faction", "yours");

        this.addSubCommand(new CmdDiscordSet());

        this.requirements = new CommandRequirements.Builder(Permission.DISCORD)
                .playerOnly()
                .build();
    }

    @Override
    public void perform(CommandContext context) {
        Faction target;
        if (context.argIsSet(0)) {
            target = context.argAsFaction(0);
            if (target == null) {
                return;
            }
        } else {
            if (!context.fPlayer.hasFaction()) {
                context.msg(TL.GENERIC_MEMBERONLY);
                return;
            }
            target = context.faction;
        }

        String discordLink = target.getDiscord();
        if (discordLink == null || discordLink.isEmpty()) {
            if (target == context.faction) {
                context.msg(TL.COMMAND_DISCORD_NOTSET_SELF);
            } else {
                context.msg(TL.COMMAND_DISCORD_NOTSET_OTHER, target.describeTo(context.fPlayer, true));
            }
            return;
        }

        String formatted = TL.COMMAND_DISCORD_SEE.format(target.describeTo(context.fPlayer, true), discordLink);
        String tooltip = TL.COMMAND_DISCORD_CLICK_TOOLTIP.format(target.getTag());
        new FancyMessage(TextUtil.parseColor(formatted))
                .link(discordLink)
                .tooltip(TextUtil.parseColor(tooltip))
                .send(context.player);
    }

    @Override
    public TL getUsageTranslation() {
        return TL.COMMAND_DISCORD_DESCRIPTION;
    }
}
