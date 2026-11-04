package com.massivecraft.factions.raids;

import com.massivecraft.factions.Faction;
import com.massivecraft.factions.Factions;

import java.text.DateFormat;
import java.util.Date;
import java.util.UUID;

public class RaidRecord {

    private UUID id;
    private String defenderFactionId;
    private String defenderFactionTag;
    private String attackerFactionId;
    private String attackerFactionTag;
    private long startTime;
    private long endTime;
    private boolean active;
    private int attackerKills;
    private int defenderKills;
    private int defenderBlocksPlaced;
    private String world;
    private double x;
    private double y;
    private double z;

    public RaidRecord() {
    }

    public RaidRecord(UUID id, String defenderFactionId, String defenderFactionTag,
                      String attackerFactionId, String attackerFactionTag,
                      long startTime, String world, double x, double y, double z) {
        this.id = id != null ? id : UUID.randomUUID();
        this.defenderFactionId = defenderFactionId;
        this.defenderFactionTag = defenderFactionTag;
        this.attackerFactionId = attackerFactionId;
        this.attackerFactionTag = attackerFactionTag;
        this.startTime = startTime;
        this.endTime = 0L;
        this.active = true;
        this.attackerKills = 0;
        this.defenderKills = 0;
        this.defenderBlocksPlaced = 0;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public UUID getId() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getDefenderFactionId() {
        return defenderFactionId;
    }

    public void setDefenderFactionId(String defenderFactionId) {
        this.defenderFactionId = defenderFactionId;
    }

    public String getDefenderFactionTag() {
        return defenderFactionTag;
    }

    public void setDefenderFactionTag(String defenderFactionTag) {
        this.defenderFactionTag = defenderFactionTag;
    }

    public String getAttackerFactionId() {
        return attackerFactionId;
    }

    public void setAttackerFactionId(String attackerFactionId) {
        this.attackerFactionId = attackerFactionId;
    }

    public String getAttackerFactionTag() {
        return attackerFactionTag;
    }

    public void setAttackerFactionTag(String attackerFactionTag) {
        this.attackerFactionTag = attackerFactionTag;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public int getAttackerKills() {
        return attackerKills;
    }

    public void setAttackerKills(int attackerKills) {
        this.attackerKills = attackerKills;
    }

    public int getDefenderKills() {
        return defenderKills;
    }

    public void setDefenderKills(int defenderKills) {
        this.defenderKills = defenderKills;
    }

    public int getDefenderBlocksPlaced() {
        return defenderBlocksPlaced;
    }

    public void setDefenderBlocksPlaced(int defenderBlocksPlaced) {
        this.defenderBlocksPlaced = defenderBlocksPlaced;
    }

    public String getWorld() {
        return world;
    }

    public void setWorld(String world) {
        this.world = world;
    }

    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
    }

    public double getZ() {
        return z;
    }

    public void setZ(double z) {
        this.z = z;
    }

    public String getDefenderName() {
        if (defenderFactionId != null) {
            Faction f = Factions.getInstance().getFactionById(defenderFactionId);
            if (f != null && f.getTag() != null && !f.getTag().isEmpty()) {
                return f.getTag();
            }
        }
        return defenderFactionTag != null && !defenderFactionTag.isEmpty() ? defenderFactionTag : "Unknown";
    }

    public String getAttackerName() {
        if (attackerFactionId != null) {
            Faction f = Factions.getInstance().getFactionById(attackerFactionId);
            if (f != null && f.getTag() != null && !f.getTag().isEmpty()) {
                return f.getTag();
            }
        }
        return attackerFactionTag != null && !attackerFactionTag.isEmpty() ? attackerFactionTag : "Unknown";
    }

    public boolean isInvolved(Faction faction) {
        if (faction == null) {
            return false;
        }
        String fId = faction.getId();
        String fTag = faction.getTag();
        if (fId != null) {
            if (fId.equalsIgnoreCase(defenderFactionId) || fId.equalsIgnoreCase(attackerFactionId)) {
                return true;
            }
        }
        if (fTag != null) {
            if (fTag.equalsIgnoreCase(defenderFactionTag) || fTag.equalsIgnoreCase(attackerFactionTag)) {
                return true;
            }
        }
        return false;
    }

    public boolean isInvolved(String factionIdOrTag) {
        if (factionIdOrTag == null || factionIdOrTag.isEmpty()) {
            return false;
        }
        return factionIdOrTag.equalsIgnoreCase(defenderFactionId)
                || factionIdOrTag.equalsIgnoreCase(defenderFactionTag)
                || factionIdOrTag.equalsIgnoreCase(attackerFactionId)
                || factionIdOrTag.equalsIgnoreCase(attackerFactionTag);
    }

    public String getFormattedDuration() {
        long elapsed = (active || endTime <= 0) ? (System.currentTimeMillis() - startTime) : (endTime - startTime);
        if (elapsed < 0) {
            elapsed = 0;
        }
        long totalSeconds = elapsed / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes, seconds);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds);
        } else {
            return String.format("%ds", seconds);
        }
    }

    public String getFormattedStartTime(DateFormat format) {
        if (startTime <= 0) {
            return "N/A";
        }
        return format.format(new Date(startTime));
    }

    public String getFormattedEndTime(DateFormat format) {
        if (endTime <= 0) {
            return "N/A";
        }
        return format.format(new Date(endTime));
    }
}
