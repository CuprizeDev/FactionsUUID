package com.massivecraft.factions.data;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.massivecraft.factions.Board;
import com.massivecraft.factions.FLocation;
import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.FPlayers;
import com.massivecraft.factions.Faction;
import com.massivecraft.factions.Factions;
import com.massivecraft.factions.FactionsPlugin;
import com.massivecraft.factions.perms.Relation;
import com.massivecraft.factions.util.AsciiCompass;
import com.massivecraft.factions.util.TL;
import mkremins.fanciful.FancyMessage;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


public abstract class MemoryBoard extends Board
{

    public class MemoryBoardMap extends ConcurrentHashMap<FLocation, String>
    {
        private static final long serialVersionUID = -6689617828610585368L;

        Multimap<String, FLocation> factionToLandMap = HashMultimap.create();

        @Override
        public String put(FLocation floc, String factionId)
        {
            String previousValue = super.put(floc, factionId);
            if(previousValue != null)
            {
                factionToLandMap.remove(previousValue, floc);
            }

            factionToLandMap.put(factionId, floc);
            return previousValue;
        }

        @Override
        public String remove(Object key)
        {
            String result = super.remove(key);
            if(result != null)
            {
                FLocation floc = (FLocation) key;
                factionToLandMap.remove(result, floc);
            }

            return result;
        }

        @Override
        public void clear()
        {
            super.clear();
            factionToLandMap.clear();
        }

        public int getOwnedLandCount(String factionId)
        {
            return factionToLandMap.get(factionId).size();
        }

        public void removeFaction(String factionId)
        {
            Collection<FLocation> fLocations = factionToLandMap.removeAll(factionId);
            for(FPlayer fPlayer : FPlayers.getInstance().getOnlinePlayers())
            {
                if(fLocations.contains(fPlayer.getLastStoodAt()))
                {
                    if(FactionsPlugin.getInstance().conf().commands().fly().isEnable() && !fPlayer.isAdminBypassing() && fPlayer.isFlying()
                       && !fPlayer.getPlayer().hasPermission("essentials.fly"))
                    {
                        fPlayer.setFlying(false);
                    }
                    if(fPlayer.isWarmingUp())
                    {
                        fPlayer.clearWarmup();
                        fPlayer.msg(TL.WARMUPS_CANCELLED);
                    }
                }
            }
            for(FLocation floc : fLocations)
            {
                super.remove(floc);
            }
        }
    }

    private final char[] mapKeyChrs = "\\/#$%=&^ABCDEFGHJKLMNOPQRSTUVWXYZ1234567890abcdeghjmnopqrsuvwxyz?".toCharArray();

    public MemoryBoardMap flocationIds = new MemoryBoardMap();

    //----------------------------------------------//
    // Get and Set
    //----------------------------------------------//
    public String getIdAt(FLocation flocation)
    {
        if(!flocationIds.containsKey(flocation))
        {
            return "0";
        }

        return flocationIds.get(flocation);
    }

    public Faction getFactionAt(FLocation flocation)
    {
        return Factions.getInstance().getFactionById(getIdAt(flocation));
    }

    public void setIdAt(String id, FLocation flocation)
    {
        clearOwnershipAt(flocation);

        if(id.equals("0"))
        {
            removeAt(flocation);
        }

        flocationIds.put(flocation, id);
    }

    public void setFactionAt(Faction faction, FLocation flocation)
    {
        setIdAt(faction.getId(), flocation);
    }

    public void removeAt(FLocation flocation)
    {
        removeAt(flocation, false);
    }

    public void removeAt(FLocation flocation, boolean light)
    {
        Faction faction = getFactionAt(flocation);
        faction.getWarps().values().removeIf(lazyLocation -> flocation.isInChunk(lazyLocation.getLocation()));
        if(!light)
        {
            if(Bukkit.isPrimaryThread())
            {
                for(Entity entity : flocation.getChunk().getEntities())
                {
                    if(entity instanceof Player)
                    {
                        FPlayer fPlayer = FPlayers.getInstance().getByPlayer((Player) entity);
                        if(!fPlayer.isAdminBypassing() && fPlayer.isFlying() && !fPlayer.getPlayer().hasPermission("essentials.fly"))
                        {
                            fPlayer.setFlying(false);
                        }
                        if(fPlayer.isWarmingUp())
                        {
                            fPlayer.clearWarmup();
                            fPlayer.msg(TL.WARMUPS_CANCELLED);
                        }
                    }
                }
            }
            else
            {
                new BukkitRunnable()
                {
                    @Override
                    public void run()
                    {
                        for(Entity entity : flocation.getChunk().getEntities())
                        {
                            if(entity instanceof Player)
                            {
                                FPlayer fPlayer = FPlayers.getInstance().getByPlayer((Player) entity);
                                if(!fPlayer.isAdminBypassing() && fPlayer.isFlying() && !fPlayer.getPlayer().hasPermission("essentials.fly"))
                                {
                                    fPlayer.setFlying(false);
                                }
                                if(fPlayer.isWarmingUp())
                                {
                                    fPlayer.clearWarmup();
                                    fPlayer.msg(TL.WARMUPS_CANCELLED);
                                }
                            }
                        }
                    }
                }.runTask(FactionsPlugin.getInstance());
            }
        }
        clearOwnershipAt(flocation);
        flocationIds.remove(flocation);
    }

    public Set<FLocation> getAllClaims(String factionId)
    {
        Set<FLocation> locs = new HashSet<>();
        for(Entry<FLocation, String> entry : flocationIds.entrySet())
        {
            if(entry.getValue().equals(factionId))
            {
                locs.add(entry.getKey());
            }
        }
        return locs;
    }

    public Set<FLocation> getAllClaims(Faction faction)
    {
        return getAllClaims(faction.getId());
    }

    // not to be confused with claims, ownership referring to further member-specific ownership of a claim
    public void clearOwnershipAt(FLocation flocation)
    {
        Faction faction = getFactionAt(flocation);
        if(faction != null && faction.isNormal())
        {
            faction.clearClaimOwnership(flocation);
            faction.setSpawnerChunk(flocation, false);
        }
    }

    public void unclaimAll(String factionId)
    {
        Faction faction = Factions.getInstance().getFactionById(factionId);
        if(faction != null && faction.isNormal())
        {
            faction.clearAllClaimOwnership();
            faction.clearWarps();
            faction.clearSpawnerChunks();
        }
        clean(factionId);
    }

    public void unclaimAllInWorld(String factionId, World world)
    {
        for(FLocation loc : getAllClaims(factionId))
        {
            if(loc.getWorldName().equals(world.getName()))
            {
                removeAt(loc, true);
            }
        }
    }

    public void clean(String factionId)
    {

        flocationIds.removeFaction(factionId);
    }

    // Is this coord NOT completely surrounded by coords claimed by the same faction?
    // Simpler: Is there any nearby coord with a faction other than the faction here?
    public boolean isBorderLocation(FLocation flocation)
    {
        Faction faction = getFactionAt(flocation);
        FLocation a = flocation.getRelative(1, 0);
        FLocation b = flocation.getRelative(-1, 0);
        FLocation c = flocation.getRelative(0, 1);
        FLocation d = flocation.getRelative(0, -1);
        return faction != getFactionAt(a) || faction != getFactionAt(b) || faction != getFactionAt(c) || faction != getFactionAt(d);
    }

    // Is this coord connected to any coord claimed by the specified faction?
    public boolean isConnectedLocation(FLocation flocation, Faction faction)
    {
        FLocation a = flocation.getRelative(1, 0);
        FLocation b = flocation.getRelative(-1, 0);
        FLocation c = flocation.getRelative(0, 1);
        FLocation d = flocation.getRelative(0, -1);
        return faction == getFactionAt(a) || faction == getFactionAt(b) || faction == getFactionAt(c) || faction == getFactionAt(d);
    }

    /**
     * Checks if there is another faction within a given radius other than Wilderness. Used for HCF feature that
     * requires a 'buffer' between factions.
     *
     * @param flocation - center location.
     * @param faction   - faction checking for.
     * @param radius    - chunk radius to check.
     * @return true if another Faction is within the radius, otherwise false.
     */
    public boolean hasFactionWithin(FLocation flocation, Faction faction, int radius)
    {
        for(int x = -radius; x <= radius; x++)
        {
            for(int z = -radius; z <= radius; z++)
            {
                if(x == 0 && z == 0)
                {
                    continue;
                }

                FLocation relative = flocation.getRelative(x, z);
                Faction other = getFactionAt(relative);

                if(other.isNormal() && other != faction)
                {
                    return true;
                }
            }
        }
        return false;
    }


    //----------------------------------------------//
    // Cleaner. Remove orphaned foreign keys
    //----------------------------------------------//

    public void clean()
    {
        Iterator<Entry<FLocation, String>> iter = flocationIds.entrySet().iterator();
        while(iter.hasNext())
        {
            Entry<FLocation, String> entry = iter.next();
            if(!Factions.getInstance().isValidFactionId(entry.getValue()))
            {
                FactionsPlugin.getInstance().log("Board cleaner removed " + entry.getValue() + " from " + entry.getKey());
                iter.remove();
            }
        }
    }

    //----------------------------------------------//
    // Coord count
    //----------------------------------------------//

    public int getFactionCoordCount(String factionId)
    {
        return flocationIds.getOwnedLandCount(factionId);
    }

    public int getFactionCoordCount(Faction faction)
    {
        return getFactionCoordCount(faction.getId());
    }

    public int getFactionCoordCountInWorld(Faction faction, String worldName)
    {
        String factionId = faction.getId();
        int ret = 0;
        for(Entry<FLocation, String> entry : flocationIds.entrySet())
        {
            if(entry.getValue().equals(factionId) && entry.getKey().getWorldName().equals(worldName))
            {
                ret += 1;
            }
        }
        return ret;
    }

    //----------------------------------------------//
    // Map generation
    //----------------------------------------------//

    /**
     * The map is relative to a coord and a faction north is in the direction of decreasing x east is in the direction
     * of decreasing z
     */
    public ArrayList<FancyMessage> getMap(FPlayer fplayer, FLocation flocation, double inDegrees)
    {
        Faction faction = fplayer.getFaction();
        ArrayList<FancyMessage> ret = new ArrayList<>();
        Faction factionLoc = getFactionAt(flocation);
        ret.add(new FancyMessage(FactionsPlugin.getInstance().txt().titleize("(" + flocation.getCoordString() + ") " + factionLoc.getTag(fplayer))));

        // Get the compass
        ArrayList<String> asciiCompass = AsciiCompass.getAsciiCompass(inDegrees, ChatColor.RED, FactionsPlugin.getInstance().txt().parse("<a>"));

        int halfWidth = FactionsPlugin.getInstance().conf().map().getWidth() / 2;
        // Use player's value for height
        int halfHeight = fplayer.getMapHeight() / 2;
        FLocation topLeft = flocation.getRelative(-halfWidth, -halfHeight);
        int width = halfWidth * 2 + 1;
        int height = halfHeight * 2 + 1;

        if(FactionsPlugin.getInstance().conf().map().isShowFactionKey())
        {
            height--;
        }

        Map<String, Character> fList = new HashMap<>();
        int chrIdx = 0;

        WorldBorder worldBorder = flocation.getWorld().getWorldBorder();

        double radius = (worldBorder.getSize() / 2) - 0.001D;

        double cxMin = Math.floor((worldBorder.getCenter().getX() - radius) / 16.0D);
        double cxMax = Math.floor((worldBorder.getCenter().getX() + radius) / 16.0D);

        double czMin = Math.floor((worldBorder.getCenter().getZ() - radius) / 16.0D);
        double czMax = Math.floor((worldBorder.getCenter().getZ() + radius) / 16.0D);

        // For each row
        for(int dz = 0; dz < height; dz++)
        {
            // Draw and add that row
            FancyMessage row = new FancyMessage("");

            if(dz < 3)
            {
                row.then(asciiCompass.get(dz));
            }
            for(int dx = (dz < 3 ? 6 : 3); dx < width; dx++)
            {

                if(dx == halfWidth && dz == halfHeight)
                {
                    row.then("+").color(ChatColor.AQUA);
                    if(FactionsPlugin.getInstance().conf().map().isShowMapToolTips())
                        row.tooltip(TL.CLAIM_YOUAREHERE.toString());
                }
                else
                {
                    FLocation flocationHere = topLeft.getRelative(dx, dz);
                    long distanceX = Math.abs(flocationHere.getX() - fplayer.getLastStoodAt().getX());
                    long distanceZ = Math.abs(flocationHere.getZ() - fplayer.getLastStoodAt().getZ());
                    long distance = Math.max(distanceX, distanceZ);
                    boolean outOfRange = (distance > FactionsPlugin.getInstance().conf().factions().claims().getMaxClaimAtDistance() &&
                                          !fplayer.isAdminBypassing());
                    Faction factionHere = getFactionAt(flocationHere);
                    Relation relation = fplayer.getRelationTo(factionHere);
                    if(flocationHere.getX() < cxMin || flocationHere.getX() > cxMax || flocationHere.getZ() < czMin || flocationHere.getZ() > czMax)
                    {
                        row.then("-").color(ChatColor.BLACK);
                    }
                    else if(factionHere.isWilderness())
                    {
                        row.then("-").color(FactionsPlugin.getInstance().conf().colors().factions().getWilderness())
                                .color(FactionsPlugin.getInstance().conf().colors().factions().getWilderness());
                        if(FactionsPlugin.getInstance().conf().map().isShowMapToolTips())
                        {
                            if(outOfRange)
                                row.tooltip(TL.COMMAND_CLAIMAT_TOOFAR_TOOLTIP.toString());
                            else
                                row.tooltip(String.format(TL.CLAIM_CLICK_TO_CLAIM.toString(), flocationHere.getX(), flocationHere.getZ()))
                                        .command("/f claimat " + flocationHere.getWorld().getName() + " " + flocationHere.getX() + " " + flocationHere.getZ() +
                                                 " true");
                        }
                    }
                    else if(factionHere.isSafeZone())
                    {
                        row.then("+").color(FactionsPlugin.getInstance().conf().colors().factions().getSafezone());
                    }
                    else if(factionHere.isWarZone())
                    {
                        row.then("+").color(FactionsPlugin.getInstance().conf().colors().factions().getWarzone());
                    }
                    else if(factionHere == faction || factionHere == factionLoc || relation.isAtLeast(Relation.ALLY) ||
                            (FactionsPlugin.getInstance().conf().map().isShowNeutralFactionsOnMap() && relation.equals(Relation.NEUTRAL)) ||
                            (FactionsPlugin.getInstance().conf().map().isShowEnemyFactions() && relation.equals(Relation.ENEMY)) ||
                            FactionsPlugin.getInstance().conf().map().isShowTruceFactions() && relation.equals(Relation.TRUCE))
                    {
                        if(!fList.containsKey(factionHere.getTag()))
                        {
                            fList.put(factionHere.getTag(), this.mapKeyChrs[Math.min(chrIdx++, this.mapKeyChrs.length - 1)]);
                        }
                        char tag = fList.get(factionHere.getTag());
                        row.then(String.valueOf(tag)).color(factionHere.getColorTo(faction));
                        if(factionHere == faction)
                        {
                            if(FactionsPlugin.getInstance().conf().map().isShowMapToolTips())
                            {
                                if(outOfRange)
                                    row.tooltip(TL.COMMAND_UNCLAIMAT_TOOFAR_TOOLTIP.toString());
                                else
                                    row.tooltip(String.format(TL.CLAIM_CLICK_TO_UNCLAIM.toString(), flocationHere.getX(), flocationHere.getZ()))
                                            .command(
                                                    "/f unclaimat " + flocationHere.getWorld().getName() + " " + flocationHere.getX() + " " +
                                                    flocationHere.getZ() +
                                                    " true");
                            }
                        }
                        else
                        {
                            if(fplayer.isAdminBypassing())
                            {
                                row.tooltip(String.format(TL.CLAIMEDTOOLTIP_FACTIONNAME.toString(), relation.getColor().toString() + factionHere.getTag()), " ",
                                                "\u00A74\u00A7oADMIN BYPASS", "Click to \u00A7cunclaim\u00A7f.")
                                        .command(
                                                "/f unclaimat " + flocationHere.getWorld().getName() + " " + flocationHere.getX() + " " + flocationHere.getZ() +
                                                " true");
                            }
                            else
                            {
                                row.tooltip(String.format(TL.CLAIMEDTOOLTIP_FACTIONNAME.toString(), relation.getColor().toString() + factionHere.getTag()))
                                        .command("/f who " + factionHere.getTag());
                            }
                        }
                    }
                    else
                    {
                        row.then("-").color(ChatColor.GRAY);
                    }
                }
            }
            ret.add(row);
        }

        // Add the faction key
        if(FactionsPlugin.getInstance().conf().map().isShowFactionKey())
        {
            FancyMessage fRow = new FancyMessage("");
            for(String key : fList.keySet())
            {
                final Relation relation = fplayer.getRelationTo(Factions.getInstance().getByTag(key));
                fRow.then(String.format("%s: %s ", fList.get(key), key)).color(relation.getColor());
            }
            ret.add(fRow);
        }

        return ret;
    }

    public abstract void convertFrom(MemoryBoard old);
}
