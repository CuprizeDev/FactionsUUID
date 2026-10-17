package com.massivecraft.factions.cmd;

import com.massivecraft.factions.FactionsPlugin;
import com.massivecraft.factions.struct.Permission;
import com.massivecraft.factions.util.SeeChunkUtil;
import com.massivecraft.factions.util.TL;

public class CmdSeeChunk extends FCommand {

    public CmdSeeChunk() {
        super();
        this.aliases.add("seechunk");
        this.aliases.add("sc");

        this.optionalArgs.put("on/off", "toggle");

        this.requirements = new CommandRequirements.Builder(Permission.SEECHUNK)
                .playerOnly()
                .build();
    }

    @Override
    public void perform(CommandContext context) {
        boolean toggle;
        if (context.args.size() == 0) {
            toggle = !context.fPlayer.isSeeingChunk();
        } else {
            toggle = context.argAsBool(0, !context.fPlayer.isSeeingChunk());
        }

        context.fPlayer.setSeeingChunk(toggle);
        context.msg(TL.COMMAND_SEECHUNK_TOGGLE, toggle ? "enabled" : "disabled");
    }

    @Override
    public TL getUsageTranslation() {
        return TL.COMMAND_SEECHUNK_DESCRIPTION;
    }

}
