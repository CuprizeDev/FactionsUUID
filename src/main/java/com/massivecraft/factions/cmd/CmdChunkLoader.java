package com.massivecraft.factions.cmd;

import com.massivecraft.factions.FactionsPlugin;
import com.massivecraft.factions.listeners.FactionsChunkLoaderListener;
import com.massivecraft.factions.struct.Permission;
import com.massivecraft.factions.util.TL;
import org.bukkit.entity.Player;

public class CmdChunkLoader extends FCommand {

    public CmdChunkLoader() {
        super();
        this.aliases.add("chunkloader");
        this.aliases.add("chunkloaders");

        this.optionalArgs.put("give|shards", "player [amount]");

        this.requirements = new CommandRequirements.Builder(Permission.CHUNKLOADER)
                .playerOnly()
                .noErrorOnManyArgs()
                .noDisableOnLock()
                .build();
    }

    @Override
    public void perform(CommandContext context) {
        FactionsChunkLoaderListener loaders = FactionsPlugin.getInstance().getChunkLoaders();
        if (loaders == null) {
            context.msg(TL.COMMAND_CHUNKLOADER_DISABLED);
            return;
        }

        String sub = context.argAsString(0, "");
        if (sub.isEmpty()) {
            if (!context.fPlayer.hasFaction()) {
                context.msg(TL.GENERIC_MEMBERONLY);
                return;
            }
            loaders.list(context.fPlayer);
            return;
        }

        boolean shards = sub.equalsIgnoreCase("shards") || sub.equalsIgnoreCase("upkeep");
        if (!shards && !sub.equalsIgnoreCase("give")) {
            context.msg(TL.COMMAND_CHUNKLOADER_USAGE);
            return;
        }
        if (!Permission.CHUNKLOADER_ADMIN.has(context.sender, true)) {
            return;
        }
        if (!context.argIsSet(1)) {
            context.msg(TL.COMMAND_CHUNKLOADER_USAGE);
            return;
        }

        Player target = context.argAsPlayer(1);
        if (target == null) {
            return;
        }

        int amount = Math.max(1, Math.min(64 * 36, context.argAsInt(2, 1)));

        loaders.give(target, shards, amount);
        context.msg(TL.COMMAND_CHUNKLOADER_GIVEN, amount, shards ? "Chunkloader Shards" : "chunk loader", target.getName());
    }

    @Override
    public TL getUsageTranslation() {
        return TL.COMMAND_CHUNKLOADER_DESCRIPTION;
    }
}
