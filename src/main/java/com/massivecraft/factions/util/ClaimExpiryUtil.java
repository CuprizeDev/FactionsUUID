package com.massivecraft.factions.util;

import com.massivecraft.factions.Board;
import com.massivecraft.factions.FLocation;
import com.massivecraft.factions.Faction;
import com.massivecraft.factions.Factions;
import com.massivecraft.factions.FactionsPlugin;
import com.google.gson.reflect.TypeToken;
import com.massivecraft.factions.config.file.MainConfig;
import com.massivecraft.factions.data.MemoryBoard;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ClaimExpiryUtil {
    private static final class Stamp {
        final String faction;
        final long at;

        Stamp(String faction, long at) {
            this.faction = faction;
            this.at = at;
        }
    }

    private final FactionsPlugin plugin;
    private final Map<FLocation, Stamp> stamps = new HashMap<>();
    private BukkitTask task;
    private boolean started, dirty;
    private long durationMs = 3 * 3_600_000L;
    private int scanSeconds = 20, maxPerScan = 50, tick;
    private Object shieldFeature;
    private Method shieldData, coreWorld, coreChunks, chunkKey;
    private boolean shieldFailed;

    public ClaimExpiryUtil(FactionsPlugin plugin) {
        this.plugin = plugin;
    }

    private File dataFile() {
        return new File(plugin.getDataFolder(), "data/claim-expiry.json");
    }

    private File legacyFile() {
        return new File(plugin.getDataFolder(), "claim-expiry.txt");
    }

    public void reload() {
        MainConfig.ClaimExpiry c = plugin.conf().claimExpiry();
        long s = Durations.parse(c.getExpireAfter());
        durationMs = (s <= 0 ? 3 * 3600L : s) * 1000L;
        scanSeconds = Math.max(5, c.getScanSeconds());
        maxPerScan = Math.max(1, c.getMaxPerScan());
        shieldFeature = null;
        shieldFailed = false;
        if (started) {
            cancelTask();
            startTask();
        }
    }

    public void start() {
        if (started) return;
        started = true;
        load();
        startTask();
    }

    public void stop() {
        if (!started) return;
        started = false;
        cancelTask();
        save();
        stamps.clear();
    }

    private void startTask() {
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::scan, 100L, scanSeconds * 20L);
    }

    private void cancelTask() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private boolean hookShield() {
        if (shieldFeature != null) return true;
        if (shieldFailed) return false;
        try {
            Plugin kore = Bukkit.getPluginManager().getPlugin("FactionsKore");
            if (kore == null || !kore.isEnabled()) return false;
            ClassLoader cl = kore.getClass().getClassLoader();
            Class<?> main = Class.forName("com.golfing8.kore.FactionsKore", true, cl);
            Class<?> feature = Class.forName("com.golfing8.kore.feature.ShieldFeature", true, cl);
            Object feat = main.getMethod("getFeature", Class.class).invoke(main.getMethod("get").invoke(null), feature);
            if (feat == null) return false;
            Class<?> data = Class.forName("com.golfing8.kore.featureobjs.shield.FactionShieldData", true, cl);
            shieldData = feature.getMethod("getFactionShieldData", String.class);
            coreWorld = data.getMethod("getCoreChunkWorld");
            coreChunks = data.getMethod("getCoreChunkCoordinates");
            chunkKey = Class.forName("com.golfing8.kore.util.LongHash", true, cl).getMethod("toLong", int.class, int.class);
            shieldFeature = feat;
            return true;
        } catch (Throwable t) {
            shieldFailed = true;
            plugin.getLogger().warning("Claim expiry: could not read FactionsKore shield chunks, so no claims will expire (" + t + ")");
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private boolean isBase(Faction f, FLocation loc) {
        if (!hookShield()) return true;
        try {
            Object data = shieldData.invoke(shieldFeature, f.getId());
            if (data == null) return false;
            World world = (World) coreWorld.invoke(data);
            if (world == null || !world.getName().equals(loc.getWorldName())) return false;
            Set<Long> keys = (Set<Long>) coreChunks.invoke(data);
            return keys != null && keys.contains((Long) chunkKey.invoke(null, (int) loc.getX(), (int) loc.getZ()));
        } catch (Throwable t) {
            return true;
        }
    }

    private boolean isSpawn(FLocation loc) {
        return plugin.conf().spawnClaimLimit().contains(loc.getWorldName(), loc.getX(), loc.getZ());
    }

    private void scan() {
        if (!(Board.getInstance() instanceof MemoryBoard)) return;
        MemoryBoard board = (MemoryBoard) Board.getInstance();
        long now = System.currentTimeMillis();
        Set<FLocation> seen = new HashSet<>();
        List<FLocation> due = new ArrayList<>();
        Map<String, Faction> cache = new HashMap<>();
        for (Map.Entry<FLocation, String> e : new ArrayList<>(board.flocationIds.entrySet())) {
            Faction f = cache.computeIfAbsent(e.getValue(), id -> Factions.getInstance().getFactionById(id));
            if (f == null || !f.isNormal()) continue;
            FLocation loc = e.getKey();
            seen.add(loc);
            Stamp s = stamps.get(loc);
            if (s == null || !s.faction.equals(e.getValue())) {
                stamps.put(loc, new Stamp(e.getValue(), now));
                dirty = true;
            } else if (now - s.at >= durationMs && due.size() < maxPerScan && !isSpawn(loc) && !isBase(f, loc)) {
                due.add(loc);
            }
        }
        if (stamps.keySet().removeIf(k -> !seen.contains(k))) dirty = true;
        Map<Faction, List<FLocation>> lost = new HashMap<>();
        for (FLocation loc : due) {
            Stamp s = stamps.remove(loc);
            Board.getInstance().removeAt(loc);
            dirty = true;
            lost.computeIfAbsent(cache.get(s.faction), k -> new ArrayList<>()).add(loc);
        }
        for (Map.Entry<Faction, List<FLocation>> e : lost.entrySet()) {
            List<FLocation> locs = e.getValue();
            FLocation loc = locs.get(0);
            if (locs.size() > 1) {
                e.getKey().msg(TL.CLAIMEXPIRY_UNCLAIMEDMANY, locs.size());
            } else {
                e.getKey().msg(TL.CLAIMEXPIRY_UNCLAIMED, loc.getX(), loc.getZ(), loc.getWorldName());
            }
        }
        if (dirty && ++tick % 3 == 0) save();
    }

    public String[] mapLines(Faction f, FLocation loc) {
        if (!started || f == null || !f.isNormal()) return null;
        long now = System.currentTimeMillis();
        Stamp s = stamps.get(loc);
        if (s == null || !s.faction.equals(f.getId())) {
            s = new Stamp(f.getId(), now);
            stamps.put(loc, s);
            dirty = true;
        }
        String claimed = String.format(TL.CLAIMEXPIRY_CLAIMED.toString(), Durations.ago((now - s.at) / 1000));
        if (isSpawn(loc)) return new String[]{claimed, TL.CLAIMEXPIRY_SPAWN.toString()};
        if (isBase(f, loc)) return new String[]{claimed, TL.CLAIMEXPIRY_BASE.toString()};
        long left = Math.max(0, s.at + durationMs - now) / 1000;
        return new String[]{claimed, String.format(TL.CLAIMEXPIRY_TEMPORARY.toString(), Durations.format(left))};
    }

    private void load() {
        stamps.clear();
        File f = dataFile();
        if (f.exists()) {
            String json = DiscUtil.readCatch(f);
            if (json == null || json.isBlank()) return;
            try {
                Map<String, Map<String, Stamp>> data = plugin.getGson().fromJson(json, new TypeToken<Map<String, Map<String, Stamp>>>() {
                }.getType());
                if (data == null) return;
                for (Map.Entry<String, Map<String, Stamp>> world : data.entrySet()) {
                    for (Map.Entry<String, Stamp> e : world.getValue().entrySet()) {
                        String[] xz = e.getKey().split(",");
                        stamps.put(new FLocation(world.getKey(), Integer.parseInt(xz[0]), Integer.parseInt(xz[1])), e.getValue());
                    }
                }
            } catch (RuntimeException e) {
                plugin.getLogger().warning("claim-expiry: could not read claim-expiry.json: " + e.getMessage());
            }
            return;
        }
        File legacy = legacyFile();
        if (!legacy.exists()) return;
        try {
            for (String line : Files.readAllLines(legacy.toPath(), StandardCharsets.UTF_8)) {
                String[] p = line.split(";");
                if (p.length < 5) continue;
                try {
                    stamps.put(new FLocation(p[0], Integer.parseInt(p[1]), Integer.parseInt(p[2])), new Stamp(p[3], Long.parseLong(p[4])));
                } catch (NumberFormatException ignored) {
                }
            }
            save();
            Files.deleteIfExists(legacy.toPath());
        } catch (IOException e) {
            plugin.getLogger().warning("claim-expiry: could not import claim-expiry.txt: " + e.getMessage());
        }
    }

    public void save() {
        dirty = false;
        Map<String, Map<String, Stamp>> data = new HashMap<>();
        for (Map.Entry<FLocation, Stamp> e : stamps.entrySet()) {
            data.computeIfAbsent(e.getKey().getWorldName(), k -> new HashMap<>()).put(e.getKey().getX() + "," + e.getKey().getZ(), e.getValue());
        }
        DiscUtil.writeCatch(dataFile(), plugin.getGson().toJson(data), true);
    }
}
