package com.massivecraft.factions.cmd.claim;

import com.massivecraft.factions.*;
import com.massivecraft.factions.cmd.CommandContext;
import com.massivecraft.factions.cmd.CommandRequirements;
import com.massivecraft.factions.cmd.FCommand;
import com.massivecraft.factions.perms.PermissibleAction;
import com.massivecraft.factions.perms.Relation;
import com.massivecraft.factions.struct.Permission;
import com.massivecraft.factions.util.CornerClaims;
import com.massivecraft.factions.util.TL;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;

public class CmdCorner extends FCommand {
    public CmdCorner(){
        this.aliases.add("claimcorner");
        this.aliases.add("corner");

        this.requirements = new CommandRequirements.Builder(Permission.CLAIMCORNER)
                .withAction(PermissibleAction.CLAIMCORNER)
                .playerOnly()
                .build();
    }

    enum EnumCorner{
        POS_POS,
        NEG_NEG,
        POS_NEG,
        NEG_POS
    }

    @Override
    public void perform(CommandContext context) {
        FPlayer fPlayer = context.fPlayer;
        FactionsPlugin plugin = FactionsPlugin.getInstance();

        if(!plugin.isSOTW() && plugin.conf().corner().onlyAllowInSOTW()){
            fPlayer.msg(TL.COMMAND_CORNER_ISNTSOTW);
            return;
        }

        EnumCorner corner = getCorner(fPlayer);

        if(corner == null){
            fPlayer.msg(TL.COMMAND_CORNER_MUSTBEINCORNER);
            return;
        }

        if(fPlayer.getRelationToLocation() != Relation.MEMBER && !Board.getInstance().getFactionAt(new FLocation(fPlayer)).isWilderness()){
            fPlayer.msg(TL.COMMAND_CORNER_MUSTOWN);
            return;
        }

        Faction faction = fPlayer.getFaction();
        CornerClaims corners = CornerClaims.get();
        corners.prune();

        CornerClaims.Region existing = corners.of(faction.getId());
        if(existing != null){
            if(corners.ownsAny(faction, existing)){
                fPlayer.msg(TL.COMMAND_CORNER_ALREADYHAS);
                return;
            }
            corners.remove(faction.getId());
        }

        int size = Math.max(1, plugin.conf().corner().diameterClaim());
        Location location = fPlayer.getPlayer().getLocation();
        String world = location.getWorld().getName();
        int myCX = location.getBlockX() >> 4;
        int myCZ = location.getBlockZ() >> 4;

        int minX = corner == EnumCorner.NEG_NEG || corner == EnumCorner.NEG_POS ? myCX : myCX - (size - 1);
        int minZ = corner == EnumCorner.NEG_NEG || corner == EnumCorner.POS_NEG ? myCZ : myCZ - (size - 1);
        CornerClaims.Region region = new CornerClaims.Region(world, minX, minZ, minX + size - 1, minZ + size - 1);

        if(corners.overlapsOther(region, faction.getId())){
            fPlayer.msg(TL.COMMAND_CORNER_TAKEN);
            return;
        }

        boolean override = plugin.conf().corner().overrideOtherClaims();
        List<FLocation> toClaim = new ArrayList<>();
        for(int x = minX; x < minX + size; x++){
            for(int z = minZ; z < minZ + size; z++){
                FLocation fl = new FLocation(world, x, z);
                Faction at = Board.getInstance().getFactionAt(fl);
                if(at.getId().equals(faction.getId())) continue;
                if(!at.isWilderness() && (!at.isNormal() || !override)){
                    fPlayer.msg(TL.COMMAND_CORNER_BLOCKED, at.getTag());
                    return;
                }
                toClaim.add(fl);
            }
        }

        int missing = faction.getLandRounded() + toClaim.size() - faction.getPowerRounded();
        if(missing > 0){
            fPlayer.msg(TL.COMMAND_CORNER_NOTENOUGHPOWER, missing);
            return;
        }

        for(FLocation fl : toClaim){
            Board.getInstance().setFactionAt(faction, fl);
        }
        corners.set(faction.getId(), region);

        fPlayer.msg(TL.COMMAND_CORNER_SUCCESS);
    }

    private EnumCorner getCorner(FPlayer fPlayer){
        Location location = fPlayer.getPlayer().getLocation();
        int cx = location.getBlockX() >> 4;
        int cz = location.getBlockZ() >> 4;

        Location center = location.getWorld().getWorldBorder().getCenter();

        double radius = (location.getWorld().getWorldBorder().getSize() / 2D);

        int maxX = (int) Math.floor((center.getX() + radius - 0.1) / 16.0D);
        int maxZ = (int) Math.floor((center.getZ() + radius - 0.1) / 16.0D);
        int minX = (int) Math.floor((center.getX() - radius) / 16.0D);
        int minZ = (int) Math.floor((center.getZ() - radius) / 16.0D);

        if(cx == maxX && cz == maxZ)return EnumCorner.POS_POS;
        if(cx == maxX && cz == minZ)return EnumCorner.POS_NEG;
        if(cx == minX && cz == maxZ)return EnumCorner.NEG_POS;
        if(cx == minX && cz == minZ)return EnumCorner.NEG_NEG;
        return null;
    }

    @Override
    public TL getUsageTranslation() {
        return TL.COMMAND_CORNER_DESCRIPTION;
    }
}
