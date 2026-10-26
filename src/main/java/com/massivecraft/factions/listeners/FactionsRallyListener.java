package com.massivecraft.factions.listeners;

import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.Faction;
import com.massivecraft.factions.Factions;
import com.massivecraft.factions.FactionsPlugin;
import com.massivecraft.factions.config.file.MainConfig;
import com.massivecraft.factions.struct.Permission;
import com.massivecraft.factions.util.TL;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class FactionsRallyListener implements Listener {

    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

    private static final class Rally {
        final String factionId;
        final UUID owner;
        final String ownerName;
        final long until;
        final boolean follow;
        final World world;
        final double x, y, z;

        Rally(String factionId, Player player, long until, boolean follow) {
            this.factionId = factionId;
            this.owner = player.getUniqueId();
            this.ownerName = player.getName();
            this.until = until;
            this.follow = follow;
            Location location = player.getLocation();
            this.world = location.getWorld();
            this.x = location.getX();
            this.y = location.getY();
            this.z = location.getZ();
        }
    }

    private final FactionsPlugin plugin;
    private final Map<String, Rally> rallies = new HashMap<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private BukkitTask task;
    private long durationMs = 60_000L;
    private long cooldownMs = 15_000L;
    private boolean follow = true;
    private long updateTicks = 5L;
    private boolean beamOn = true;
    private Particle beamParticle = Particle.END_ROD;
    private int beamHeight = 60;
    private double beamSpacing = 2;
    private long beamEvery = 20L;
    private double beamRange = 200;
    private long tickCount;

    public FactionsRallyListener(FactionsPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        MainConfig.Rally conf = plugin.conf().rally();
        durationMs = Math.max(5, conf.getDurationSeconds()) * 1000L;
        cooldownMs = Math.max(0, conf.getCooldownSeconds()) * 1000L;
        follow = conf.isFollowPlayer();
        updateTicks = Math.max(1, conf.getUpdateTicks());
        beamOn = conf.getBeam().isEnabled();
        try {
            beamParticle = Particle.valueOf(conf.getBeam().getParticle().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            beamParticle = Particle.END_ROD;
            plugin.getLogger().warning("Rally: unknown beam particle '" + conf.getBeam().getParticle() + "', using END_ROD");
        }
        beamHeight = Math.max(1, Math.min(256, conf.getBeam().getHeight()));
        beamSpacing = Math.max(0.5, conf.getBeam().getSpacing());
        beamEvery = Math.max(1, conf.getBeam().getIntervalTicks());
        beamRange = Math.max(8, conf.getBeam().getMaxDistance());
        if (task != null) {
            stopTask();
            if (!rallies.isEmpty()) {
                startTask();
            }
        }
    }

    public void clear() {
        rallies.clear();
        cooldowns.clear();
        stopTask();
    }

    public void start(FPlayer fPlayer) {
        Player player = fPlayer.getPlayer();
        Faction faction = fPlayer.getFaction();
        if (player == null || faction == null) {
            return;
        }

        long now = System.currentTimeMillis();
        Long last = cooldowns.get(player.getUniqueId());
        if (last != null && now - last < cooldownMs) {
            fPlayer.msg(TL.COMMAND_RALLY_COOLDOWN, (cooldownMs - (now - last) + 999) / 1000);
            return;
        }
        cooldowns.put(player.getUniqueId(), now);

        Rally old = rallies.remove(faction.getId());
        if (old != null && !old.owner.equals(player.getUniqueId())) {
            announceEnd(old);
        }
        Rally rally = new Rally(faction.getId(), player, now + durationMs, follow);
        rallies.put(faction.getId(), rally);

        Location location = player.getLocation();
        String prefix = fPlayer.getRole() == null ? "" : fPlayer.getRole().getPrefix();
        String message = plugin.txt().parse(TL.COMMAND_RALLY_STARTED.toString(), prefix, player.getName(),
                location.getBlockX(), location.getBlockY(), location.getBlockZ(), rally.world == null ? "?" : rally.world.getName());
        for (Player member : viewers(faction)) {
            member.sendMessage(message);
        }
        plugin.debug("[Rally] " + player.getName() + " started a rally for " + faction.getTag());
        startTask();
    }

    public void end(FPlayer fPlayer) {
        Player player = fPlayer.getPlayer();
        Faction faction = fPlayer.getFaction();
        Rally rally = faction == null ? null : rallies.get(faction.getId());
        if (player == null || rally == null) {
            fPlayer.msg(TL.COMMAND_RALLY_NONE);
            return;
        }
        if (!rally.owner.equals(player.getUniqueId()) && !Permission.RALLY_ADMIN.has(player)) {
            fPlayer.msg(TL.COMMAND_RALLY_OWNERONLY, rally.ownerName);
            return;
        }
        rallies.remove(faction.getId());
        announceEnd(rally);
        if (rallies.isEmpty()) {
            stopTask();
        }
    }

    private List<Player> viewers(Faction faction) {
        List<Player> viewers = new ArrayList<>();
        if (faction == null) {
            return viewers;
        }
        for (Player member : faction.getOnlinePlayers()) {
            if (Permission.RALLY.has(member)) {
                viewers.add(member);
            }
        }
        return viewers;
    }

    private void announceEnd(Rally rally) {
        String message = plugin.txt().parse(TL.COMMAND_RALLY_ENDED.toString(), rally.ownerName);
        for (Player member : viewers(Factions.getInstance().getFactionById(rally.factionId))) {
            member.sendMessage(message);
            member.sendActionBar(Component.empty());
        }
    }

    private void startTask() {
        if (task == null) {
            task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, updateTicks);
        }
    }

    private void stopTask() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        tickCount++;
        long now = System.currentTimeMillis();
        List<Rally> expired = new ArrayList<>();
        Iterator<Map.Entry<String, Rally>> iterator = rallies.entrySet().iterator();
        while (iterator.hasNext()) {
            Rally rally = iterator.next().getValue();
            Faction faction = Factions.getInstance().getFactionById(rally.factionId);
            Player owner = plugin.getServer().getPlayer(rally.owner);
            if (faction == null || now >= rally.until || (rally.follow && (owner == null || !owner.isOnline()))) {
                iterator.remove();
                expired.add(rally);
                continue;
            }
            Location target = rally.follow && owner != null ? owner.getLocation() : new Location(rally.world, rally.x, rally.y, rally.z);
            boolean drawBeam = beamOn && target.getWorld() != null && tickCount % Math.max(1, beamEvery / updateTicks) == 0;
            for (Player member : viewers(faction)) {
                if (drawBeam) {
                    beam(member, target);
                }
                if (!member.getUniqueId().equals(rally.owner)) {
                    member.sendActionBar(bar(member, rally.ownerName, target));
                }
            }
        }
        for (Rally rally : expired) {
            announceEnd(rally);
        }
        if (rallies.isEmpty()) {
            stopTask();
        }
    }

    private void beam(Player viewer, Location target) {
        Location me = viewer.getLocation();
        if (me.getWorld() != target.getWorld()) {
            return;
        }
        double dx = target.getX() - me.getX();
        double dz = target.getZ() - me.getZ();
        if (dx * dx + dz * dz > beamRange * beamRange) {
            return;
        }
        double base = target.getY() - 2;
        for (double h = 0; h <= beamHeight && h <= 64 * beamSpacing; h += beamSpacing) {
            viewer.spawnParticle(beamParticle, target.getX(), base + h, target.getZ(), 1, 0, 0, 0, 0);
        }
    }

    private Component bar(Player viewer, String name, Location target) {
        Location me = viewer.getLocation();
        String text;
        if (me.getWorld() != target.getWorld()) {
            text = String.format(TL.COMMAND_RALLY_BAROTHERWORLD.toString(), name, target.getWorld() == null ? "?" : target.getWorld().getName());
        } else {
            double dx = target.getX() - me.getX();
            double dy = target.getY() - me.getY();
            double dz = target.getZ() - me.getZ();
            long distance = Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz));
            text = String.format(TL.COMMAND_RALLY_BAR.toString(), name, distance, arrow(dx, dz, me.getYaw()));
        }
        return LegacyComponentSerializer.legacySection().deserialize(text);
    }

    private static String arrow(double dx, double dz, float viewerYaw) {
        if (dx * dx + dz * dz < 1.0) {
            return "•";
        }
        double targetYaw = Math.toDegrees(Math.atan2(-dx, dz));
        double relative = targetYaw - viewerYaw;
        relative = ((relative + 180.0) % 360.0 + 360.0) % 360.0 - 180.0;
        int index = (int) Math.round(relative / 45.0);
        return ARROWS[((index % 8) + 8) % 8];
    }
}
