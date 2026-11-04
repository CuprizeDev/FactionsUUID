package com.massivecraft.factions.raids;

import com.massivecraft.factions.Faction;
import com.massivecraft.factions.Factions;
import com.massivecraft.factions.FactionsPlugin;
import com.massivecraft.factions.util.DiscUtil;
import org.bukkit.Location;
import org.bukkit.World;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class RaidManager {

    private static RaidManager instance;

    private final File file;
    private final Map<String, RaidRecord> activeRaids = new ConcurrentHashMap<>();
    private final List<RaidRecord> pastRaids = new CopyOnWriteArrayList<>();
    private volatile boolean dirty = false;

    private RaidManager() {
        this.file = new File(FactionsPlugin.getInstance().getDataFolder(), "data/raids.json");
    }

    public static synchronized RaidManager getInstance() {
        if (instance == null) {
            instance = new RaidManager();
        }
        return instance;
    }

    public static class RaidsContainer {
        public Map<String, RaidRecord> activeRaids = new HashMap<>();
        public List<RaidRecord> pastRaids = new ArrayList<>();
    }

    public synchronized void load() {
        if (!file.exists()) {
            return;
        }

        try {
            String content = DiscUtil.readCatch(file);
            if (content == null || content.trim().isEmpty()) {
                return;
            }

            RaidsContainer container = FactionsPlugin.getInstance().getGson().fromJson(content, RaidsContainer.class);
            if (container != null) {
                activeRaids.clear();
                if (container.activeRaids != null) {
                    activeRaids.putAll(container.activeRaids);
                }
                pastRaids.clear();
                if (container.pastRaids != null) {
                    pastRaids.addAll(container.pastRaids);
                }
                FactionsPlugin.getInstance().getLogger().info("Loaded " + activeRaids.size() + " active raids and " + pastRaids.size() + " past raids.");
            }
        } catch (Exception e) {
            FactionsPlugin.getInstance().getLogger().log(Level.SEVERE, "Failed to load raid data from " + file.getAbsolutePath(), e);
        }
    }

    public synchronized void save(boolean sync) {
        if (!sync && !dirty) {
            return;
        }

        try {
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }

            RaidsContainer container = new RaidsContainer();
            container.activeRaids = new HashMap<>(activeRaids);
            container.pastRaids = new ArrayList<>(pastRaids);

            String json = FactionsPlugin.getInstance().getGson().toJson(container);
            DiscUtil.writeCatch(file, json, sync);
            dirty = false;
        } catch (Exception e) {
            FactionsPlugin.getInstance().getLogger().log(Level.SEVERE, "Failed to save raid data to " + file.getAbsolutePath(), e);
        }
    }

    public void forceSave() {
        save(true);
    }

    public void handleRaidStart(String raiderId, String raidedId, World world, Location location) {
        handleRaidStart(raiderId, raidedId, world, location, true);
    }

    public void handleRaidStart(String raiderId, String raidedId, World world, Location location, boolean notifyDiscord) {
        if (raiderId == null || raidedId == null) {
            return;
        }

        Faction raider = Factions.getInstance().getFactionById(raiderId);
        if (raider == null) {
            raider = Factions.getInstance().getByTag(raiderId);
        }

        Faction raided = Factions.getInstance().getFactionById(raidedId);
        if (raided == null) {
            raided = Factions.getInstance().getByTag(raidedId);
        }

        String actualRaiderId = raider != null ? raider.getId() : raiderId;
        String actualRaiderTag = raider != null ? raider.getTag() : raiderId;
        String actualRaidedId = raided != null ? raided.getId() : raidedId;
        String actualRaidedTag = raided != null ? raided.getTag() : raidedId;

        // If an active raid already exists for this raided faction, reuse it
        RaidRecord existing = activeRaids.get(actualRaidedId);
        if (existing != null) {
            existing.setAttackerFactionId(actualRaiderId);
            existing.setAttackerFactionTag(actualRaiderTag);
            existing.setDefenderFactionTag(actualRaidedTag);
            if (world != null) {
                existing.setWorld(world.getName());
            }
            if (location != null) {
                existing.setX(location.getX());
                existing.setY(location.getY());
                existing.setZ(location.getZ());
            }
            dirty = true;
            return;
        }

        String worldName = world != null ? world.getName() : (location != null && location.getWorld() != null ? location.getWorld().getName() : "");
        double x = location != null ? location.getX() : 0;
        double y = location != null ? location.getY() : 0;
        double z = location != null ? location.getZ() : 0;

        RaidRecord record = new RaidRecord(
                UUID.randomUUID(),
                actualRaidedId,
                actualRaidedTag,
                actualRaiderId,
                actualRaiderTag,
                System.currentTimeMillis(),
                worldName,
                x,
                y,
                z
        );

        activeRaids.put(actualRaidedId, record);
        dirty = true;
        save(false);

        if (notifyDiscord) {
            com.massivecraft.factions.integration.DiscordSRVIntegration.sendRaidStartedAlert(record, raided, raider);
        }
    }

    public void handleRaidEnd(String raiderId, String raidedId, int raiderKills, int defenderKills) {
        if (raidedId == null) {
            return;
        }

        Faction raided = Factions.getInstance().getFactionById(raidedId);
        if (raided == null) {
            raided = Factions.getInstance().getByTag(raidedId);
        }
        String actualRaidedId = raided != null ? raided.getId() : raidedId;

        RaidRecord record = activeRaids.remove(actualRaidedId);

        if (record == null) {
            // Check by matching raider or raided
            for (Map.Entry<String, RaidRecord> entry : activeRaids.entrySet()) {
                RaidRecord candidate = entry.getValue();
                if (candidate.isInvolved(actualRaidedId) || (raiderId != null && candidate.isInvolved(raiderId))) {
                    record = candidate;
                    activeRaids.remove(entry.getKey());
                    break;
                }
            }
        }

        if (record == null) {
            // In case active raid wasn't tracked (e.g. reload), synthesize record
            Faction raider = raiderId != null ? Factions.getInstance().getFactionById(raiderId) : null;
            if (raider == null && raiderId != null) {
                raider = Factions.getInstance().getByTag(raiderId);
            }
            String actualRaiderId = raider != null ? raider.getId() : (raiderId != null ? raiderId : "Unknown");
            String actualRaiderTag = raider != null ? raider.getTag() : (raiderId != null ? raiderId : "Unknown");
            String actualRaidedTag = raided != null ? raided.getTag() : actualRaidedId;

            record = new RaidRecord(
                    UUID.randomUUID(),
                    actualRaidedId,
                    actualRaidedTag,
                    actualRaiderId,
                    actualRaiderTag,
                    System.currentTimeMillis() - 60000L,
                    "",
                    0, 0, 0
            );
        }

        record.setActive(false);
        record.setEndTime(System.currentTimeMillis());
        record.setAttackerKills(Math.max(record.getAttackerKills(), raiderKills));
        record.setDefenderKills(Math.max(record.getDefenderKills(), defenderKills));

        pastRaids.add(0, record);

        int maxHistory = FactionsPlugin.getInstance().conf().commands().raids().getMaxHistory();
        if (maxHistory > 0) {
            while (pastRaids.size() > maxHistory) {
                pastRaids.remove(pastRaids.size() - 1);
            }
        }

        dirty = true;
        save(false);

        Faction raider = record.getAttackerFactionId() != null ? Factions.getInstance().getFactionById(record.getAttackerFactionId()) : null;
        if (raider == null && record.getAttackerFactionTag() != null) {
            raider = Factions.getInstance().getByTag(record.getAttackerFactionTag());
        }
        com.massivecraft.factions.integration.DiscordSRVIntegration.sendRaidEndedAlert(record, raided, raider);
    }

    public RaidRecord getActiveRaid(String defenderFactionId) {
        if (defenderFactionId == null) {
            return null;
        }
        return activeRaids.get(defenderFactionId);
    }

    public void recordKill(Faction killerFaction, Faction victimFaction) {
        if (killerFaction == null || victimFaction == null) {
            return;
        }

        RaidRecord activeRaid = activeRaids.get(victimFaction.getId());
        if (activeRaid != null && activeRaid.isActive()) {
            if (killerFaction.getId().equalsIgnoreCase(activeRaid.getAttackerFactionId())
                    || killerFaction.getTag().equalsIgnoreCase(activeRaid.getAttackerFactionTag())) {
                activeRaid.setAttackerKills(activeRaid.getAttackerKills() + 1);
                dirty = true;
                return;
            }
        }

        RaidRecord defensiveRaid = activeRaids.get(killerFaction.getId());
        if (defensiveRaid != null && defensiveRaid.isActive()) {
            if (victimFaction.getId().equalsIgnoreCase(defensiveRaid.getAttackerFactionId())
                    || victimFaction.getTag().equalsIgnoreCase(defensiveRaid.getAttackerFactionTag())) {
                defensiveRaid.setDefenderKills(defensiveRaid.getDefenderKills() + 1);
                dirty = true;
                return;
            }
        }

        for (RaidRecord record : activeRaids.values()) {
            if (!record.isActive()) {
                continue;
            }
            boolean killerIsAttacker = killerFaction.getId().equalsIgnoreCase(record.getAttackerFactionId())
                    || killerFaction.getTag().equalsIgnoreCase(record.getAttackerFactionTag());
            boolean victimIsDefender = victimFaction.getId().equalsIgnoreCase(record.getDefenderFactionId())
                    || victimFaction.getTag().equalsIgnoreCase(record.getDefenderFactionTag());
            if (killerIsAttacker && victimIsDefender) {
                record.setAttackerKills(record.getAttackerKills() + 1);
                dirty = true;
                return;
            }

            boolean killerIsDefender = killerFaction.getId().equalsIgnoreCase(record.getDefenderFactionId())
                    || killerFaction.getTag().equalsIgnoreCase(record.getDefenderFactionTag());
            boolean victimIsAttacker = victimFaction.getId().equalsIgnoreCase(record.getAttackerFactionId())
                    || victimFaction.getTag().equalsIgnoreCase(record.getAttackerFactionTag());
            if (killerIsDefender && victimIsAttacker) {
                record.setDefenderKills(record.getDefenderKills() + 1);
                dirty = true;
                return;
            }
        }
    }

    public void recordBlockPlaced(Faction defenderFaction, World world) {
        if (defenderFaction == null) {
            return;
        }

        RaidRecord activeRaid = activeRaids.get(defenderFaction.getId());
        if (activeRaid == null) {
            for (RaidRecord record : activeRaids.values()) {
                if (record.isActive() && (defenderFaction.getId().equalsIgnoreCase(record.getDefenderFactionId())
                        || defenderFaction.getTag().equalsIgnoreCase(record.getDefenderFactionTag()))) {
                    activeRaid = record;
                    break;
                }
            }
        }

        if (activeRaid != null && activeRaid.isActive()) {
            if (world != null && activeRaid.getWorld() != null && !activeRaid.getWorld().isEmpty()) {
                if (!world.getName().equalsIgnoreCase(activeRaid.getWorld())) {
                    return;
                }
            }
            activeRaid.setDefenderBlocksPlaced(activeRaid.getDefenderBlocksPlaced() + 1);
            dirty = true;
        }
    }

    public Map<String, RaidRecord> getActiveRaids() {
        return Collections.unmodifiableMap(activeRaids);
    }

    public List<RaidRecord> getPastRaids() {
        return Collections.unmodifiableList(pastRaids);
    }

    public List<RaidRecord> getAllRaids() {
        List<RaidRecord> combined = new ArrayList<>(activeRaids.size() + pastRaids.size());
        combined.addAll(activeRaids.values());
        combined.addAll(pastRaids);
        combined.sort((a, b) -> Long.compare(b.getStartTime(), a.getStartTime()));
        return combined;
    }

    public List<RaidRecord> getRaidsForFaction(Faction faction) {
        if (faction == null) {
            return getAllRaids();
        }
        return getAllRaids().stream()
                .filter(record -> record.isInvolved(faction))
                .collect(Collectors.toList());
    }
}
