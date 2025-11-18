package com.massivecraft.factions.cmd.pvp;

import com.massivecraft.factions.Faction;
import com.massivecraft.factions.Factions;
import com.massivecraft.factions.cmd.CommandContext;
import com.massivecraft.factions.cmd.CommandRequirements;
import com.massivecraft.factions.cmd.FCmdRoot;
import com.massivecraft.factions.cmd.FCommand;
import com.massivecraft.factions.struct.Permission;
import com.massivecraft.factions.util.TL;
import org.bukkit.entity.Player;

import java.util.ArrayList;

public class CmdPvP extends FCommand {

    public CmdPvP() {
        super();
        this.aliases.add("pvptop");
        this.aliases.add("pvp");

        this.addSubCommand(new CmdPvPGive());
        this.addSubCommand(new CmdPvPTake());
        this.addSubCommand(new CmdPvPSet());
        this.addSubCommand(new CmdPvPTop());
        this.addSubCommand(new CmdPvPInfo());
        this.addSubCommand(new CmdPvPHelp());

        this.requirements = new CommandRequirements.Builder(Permission.PVP).memberOnly().build();
    }

    @Override
    public void perform(CommandContext context) {
        context.commandChain.add(this);

        ArrayList<Faction> factionList = Factions.getInstance().getAllFactions();
        factionList.remove(Factions.getInstance().getWilderness());
        factionList.remove(Factions.getInstance().getSafeZone());
        factionList.remove(Factions.getInstance().getWarZone());
        factionList.remove(Factions.getInstance().getByTag("RaidOutpost"));


        factionList.sort((f1, f2) -> {
            int f1Size = f1.getPvPPoints();
            int f2Size = f2.getPvPPoints();
            if (f1Size < f2Size) {
                return 1;
            } else if (f1Size > f2Size) {
                return -1;
            }
            return 0;
        });

        ArrayList<String> lines = new ArrayList<>();

        final int pageheight = 9;
        int pagenumber = context.argAsInt(1, 1);
        int pagecount = (factionList.size() / pageheight) + 1;
        if (pagenumber > pagecount) {
            pagenumber = pagecount;
        } else if (pagenumber < 1) {
            pagenumber = 1;
        }
        int start = (pagenumber - 1) * pageheight;
        int end = start + pageheight;
        if (end > factionList.size()) {
            end = factionList.size();
        }

        lines.add(TL.COMMAND_PVPTOP_TOP.format(pagenumber, pagecount));

        int rank = 1;
        for (Faction faction : factionList.subList(start, end)) {
            String fac = context.sender instanceof Player ? faction.getRelationTo(context.fPlayer).getColor() + faction.getTag() : faction.getTag();
            lines.add(TL.COMMAND_PVPTOP_LINE.format(rank, fac, faction.getPvPPoints()));
            rank++;
        }

        context.sendMessage(lines);
    }

    @Override
    public TL getUsageTranslation() {
        return TL.COMMAND_PVPTOP_DESCRIPTION;
    }
}
