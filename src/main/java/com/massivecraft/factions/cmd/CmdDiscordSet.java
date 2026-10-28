package com.massivecraft.factions.cmd;

import com.massivecraft.factions.FactionsPlugin;
import com.massivecraft.factions.perms.Role;
import com.massivecraft.factions.struct.Permission;
import com.massivecraft.factions.util.TL;
import com.massivecraft.factions.util.TextUtil;
import mkremins.fanciful.FancyMessage;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CmdDiscordSet extends FCommand {

    private static final Pattern DISCORD_INVITE_PATTERN = Pattern.compile(
            "^(?:https?://)?(?:www\\.)?(?:discord\\.gg|discord(?:app)?\\.com/invite)/([a-zA-Z0-9-]{2,32})/?$",
            Pattern.CASE_INSENSITIVE
    );

    public CmdDiscordSet() {
        super();
        this.aliases.add("set");

        this.requiredArgs.add("discord link");

        this.requirements = new CommandRequirements.Builder(Permission.SETDISCORD)
                .memberOnly()
                .build();
    }

    @Override
    public void perform(CommandContext context) {
        handleSetDiscord(context);
    }

    public static void handleSetDiscord(CommandContext context) {
        if (!context.fPlayer.getRole().isAtLeast(Role.COLEADER) && !context.fPlayer.isAdminBypassing()) {
            context.fPlayer.msg(TL.COMMAND_DISCORD_SET_NOROLE);
            return;
        }

        String rawLink = context.argAsString(0);
        if (rawLink == null || rawLink.trim().isEmpty()) {
            context.fPlayer.msg(TL.COMMAND_DISCORD_SET_INVALID, "null");
            return;
        }
        rawLink = rawLink.trim();

        Matcher matcher = DISCORD_INVITE_PATTERN.matcher(rawLink);
        if (!matcher.matches()) {
            context.fPlayer.msg(TL.COMMAND_DISCORD_SET_INVALID, rawLink);
            return;
        }

        String inviteCode = matcher.group(1);
        if (!isAppropriate(inviteCode)) {
            context.fPlayer.msg(TL.COMMAND_DISCORD_SET_INAPPROPRIATE);
            return;
        }

        String normalizedLink = "https://discord.gg/" + inviteCode;
        context.faction.setDiscord(normalizedLink);

        String msg = TL.COMMAND_DISCORD_SET_SUCCESS.format(normalizedLink);
        String tooltip = TL.COMMAND_DISCORD_CLICK_TOOLTIP.format(context.faction.getTag());
        new FancyMessage(TextUtil.parseColor(msg))
                .link(normalizedLink)
                .tooltip(TextUtil.parseColor(tooltip))
                .send(context.player);
    }

    public static boolean isAppropriate(String inviteCode) {
        if (inviteCode == null || inviteCode.isEmpty()) {
            return false;
        }
        String lower = inviteCode.toLowerCase();
        String normalizedLeet = lower
                .replace('0', 'o')
                .replace('1', 'i')
                .replace('3', 'e')
                .replace('4', 'a')
                .replace('5', 's')
                .replace("-", "");

        List<String> blockedWords = FactionsPlugin.getInstance().conf().discord().getInappropriateWords();
        for (String word : blockedWords) {
            if (word == null || word.isEmpty()) {
                continue;
            }
            String w = word.toLowerCase();
            if (lower.contains(w) || normalizedLeet.contains(w)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public TL getUsageTranslation() {
        return TL.COMMAND_DISCORD_SET_DESCRIPTION;
    }
}
