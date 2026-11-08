package com.massivecraft.factions.util;

import com.google.gson.reflect.TypeToken;
import com.massivecraft.factions.Board;
import com.massivecraft.factions.FLocation;
import com.massivecraft.factions.Faction;
import com.massivecraft.factions.Factions;
import com.massivecraft.factions.FactionsPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public final class CornerClaims {
    public static final class Region {
        String world;
        int minX, minZ, maxX, maxZ;

        public Region(String world, int minX, int minZ, int maxX, int maxZ) {
            this.world = world;
            this.minX = minX;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxZ = maxZ;
        }

        public boolean contains(String world, long x, long z) {
            return this.world.equals(world) && x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        }

        public boolean overlaps(Region o) {
            return world.equals(o.world) && minX <= o.maxX && maxX >= o.minX && minZ <= o.maxZ && maxZ >= o.minZ;
        }
    }

    private static CornerClaims instance;
    private final Map<String, Region> byFaction = new HashMap<>();

    private CornerClaims() {
    }

    public static synchronized CornerClaims get() {
        if (instance == null) {
            instance = new CornerClaims();
            instance.load();
        }
        return instance;
    }

    private File file() {
        return new File(FactionsPlugin.getInstance().getDataFolder(), "data/claim-corners.json");
    }

    private void load() {
        byFaction.clear();
        File f = file();
        if (!f.exists()) return;
        String json = DiscUtil.readCatch(f);
        if (json == null || json.isBlank()) return;
        try {
            Map<String, Region> data = FactionsPlugin.getInstance().getGson().fromJson(json, new TypeToken<Map<String, Region>>() {
            }.getType());
            if (data != null) byFaction.putAll(data);
        } catch (RuntimeException e) {
            FactionsPlugin.getInstance().getLogger().warning("claim-corners: could not read claim-corners.json: " + e.getMessage());
        }
    }

    private void save() {
        DiscUtil.writeCatch(file(), FactionsPlugin.getInstance().getGson().toJson(byFaction), true);
    }

    public Region of(String factionId) {
        return byFaction.get(factionId);
    }

    public void set(String factionId, Region region) {
        byFaction.put(factionId, region);
        save();
    }

    public void remove(String factionId) {
        if (byFaction.remove(factionId) != null) save();
    }

    public void prune() {
        if (byFaction.keySet().removeIf(id -> Factions.getInstance().getFactionById(id) == null)) save();
    }

    public boolean overlapsOther(Region region, String factionId) {
        for (Map.Entry<String, Region> e : byFaction.entrySet()) {
            if (!e.getKey().equals(factionId) && e.getValue().overlaps(region)) return true;
        }
        return false;
    }

    public boolean ownsAny(Faction faction, Region region) {
        for (int x = region.minX; x <= region.maxX; x++) {
            for (int z = region.minZ; z <= region.maxZ; z++) {
                Faction at = Board.getInstance().getFactionAt(new FLocation(region.world, x, z));
                if (at != null && at.getId().equals(faction.getId())) return true;
            }
        }
        return false;
    }

    public boolean isBase(Faction faction, FLocation loc) {
        Region r = byFaction.get(faction.getId());
        return r != null && r.contains(loc.getWorldName(), loc.getX(), loc.getZ());
    }
}
