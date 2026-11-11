package com.massivecraft.factions.listeners;

import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.FPlayers;
import com.massivecraft.factions.Faction;
import com.massivecraft.factions.Factions;
import com.massivecraft.factions.FactionsPlugin;
import com.massivecraft.factions.config.file.MainConfig;
import com.massivecraft.factions.perms.Relation;
import com.massivecraft.factions.struct.Permission;
import com.massivecraft.factions.util.TL;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class FactionsFocusListener implements Listener {

    private static final String[] ARROWS = {"\u2191", "\u2197", "\u2192", "\u2198", "\u2193", "\u2199", "\u2190", "\u2196"};

    private static final class Focus {
        final String factionId;
        final UUID initiatorUuid;
        final String initiatorName;
        final UUID targetUuid;
        final String targetName;

        Focus(String factionId, Player initiator, Player target) {
            this.factionId = factionId;
            this.initiatorUuid = initiator.getUniqueId();
            this.initiatorName = initiator.getName();
            this.targetUuid = target.getUniqueId();
            this.targetName = target.getName();
        }
    }

    private final FactionsPlugin plugin;
    private final Map<String, Focus> focuses = new HashMap<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private BukkitTask task;

    private long cooldownMs = 5_000L;
    private long updateTicks = 5L;
    private double maxRange = 100.0;
    private boolean actionBarEnabled = true;
    private boolean glowEnabled = true;

    // 1.20+ Glow Packet reflection cache
    private boolean glowReflectionInitialized = false;
    private boolean glowSupported = false;
    private Method getHandleMethod;
    private Field connectionField;
    private Method sendPacketMethod;
    private Constructor<?> metadataPacketConstructor;
    private Constructor<?> dataValueConstructor;
    private Object byteEntityDataSerializer;

    public FactionsFocusListener(FactionsPlugin plugin) {
        this.plugin = plugin;
        initGlowReflection();
    }

    public void reload() {
        MainConfig.Focus conf = plugin.conf().focus();
        cooldownMs = Math.max(0, conf.getCooldownSeconds()) * 1000L;
        updateTicks = Math.max(1, conf.getUpdateTicks());
        maxRange = conf.getMaxRange();
        actionBarEnabled = conf.isActionBarEnabled();
        glowEnabled = conf.isGlowEnabled();

        if (task != null) {
            stopTask();
            if (!focuses.isEmpty()) {
                startTask();
            }
        }
    }

    public void clear() {
        for (Focus focus : new ArrayList<>(focuses.values())) {
            Faction faction = Factions.getInstance().getFactionById(focus.factionId);
            cleanupVisuals(faction, focus.targetUuid, true);
        }
        focuses.clear();
        cooldowns.clear();
        stopTask();
    }

    public boolean hasActiveFocus(String factionId) {
        return factionId != null && focuses.containsKey(factionId);
    }

    public UUID getFocusedTarget(Faction faction) {
        if (faction == null) {
            return null;
        }
        Focus focus = focuses.get(faction.getId());
        return focus != null ? focus.targetUuid : null;
    }

    public String getFocusedPlayerName(Faction faction) {
        if (faction == null) {
            return "";
        }
        Focus focus = focuses.get(faction.getId());
        return focus != null ? focus.targetName : "";
    }

    public boolean isGlowActive() {
        // Only enable glow on 1.20+ (mcVersion >= 2000) when configured and supported
        return glowEnabled && FactionsPlugin.getMCVersion() >= 2000 && glowSupported;
    }

    public void start(FPlayer fPlayer, FPlayer targetFPlayer) {
        Player player = fPlayer.getPlayer();
        Faction faction = fPlayer.getFaction();
        if (player == null || faction == null || !faction.isNormal() || targetFPlayer == null) {
            return;
        }

        Player target = targetFPlayer.getPlayer();
        if (target == null || !target.isOnline() || !player.canSee(target)) {
            fPlayer.msg(TL.GENERIC_NOPLAYERMATCH, targetFPlayer.getName());
            return;
        }

        Focus existing = focuses.get(faction.getId());
        if (existing != null && existing.targetUuid.equals(target.getUniqueId())) {
            end(fPlayer);
            return;
        }

        if (targetFPlayer.getFaction() != null && targetFPlayer.getFaction().getId().equals(faction.getId())) {
            fPlayer.msg(TL.COMMAND_FOCUS_SAMEFACTION);
            return;
        }

        Relation relation = faction.getRelationTo(targetFPlayer);
        if (relation == Relation.MEMBER) {
            fPlayer.msg(TL.COMMAND_FOCUS_SAMEFACTION);
            return;
        }
        if (relation == Relation.ALLY || relation == Relation.TRUCE) {
            fPlayer.msg(TL.COMMAND_FOCUS_ALLYTRUCE);
            return;
        }

        if (target.isOp() || Permission.FOCUS_IMMUNE.has(target)) {
            fPlayer.msg(TL.COMMAND_FOCUS_IMMUNE);
            return;
        }

        if (maxRange > 0 && !isWithinFactionRange(faction, target)) {
            fPlayer.msg(TL.COMMAND_FOCUS_TOOFAR, target.getName(), Math.round(maxRange));
            return;
        }

        long now = System.currentTimeMillis();
        Long last = cooldowns.get(player.getUniqueId());
        if (last != null && now - last < cooldownMs) {
            fPlayer.msg(TL.COMMAND_FOCUS_COOLDOWN, (cooldownMs - (now - last) + 999) / 1000);
            return;
        }
        cooldowns.put(player.getUniqueId(), now);

        Focus old = focuses.remove(faction.getId());
        if (old != null) {
            cleanupVisuals(faction, old.targetUuid, false);
        }

        Focus focus = new Focus(faction.getId(), player, target);
        focuses.put(faction.getId(), focus);

        String prefix = fPlayer.getRole() == null ? "" : fPlayer.getRole().getPrefix();
        String message = plugin.txt().parse(TL.COMMAND_FOCUS_STARTED.toString(), prefix, player.getName(), target.getName());
        for (Player member : viewers(faction)) {
            member.sendMessage(message);
            if (isGlowActive() && member.getWorld() == target.getWorld()) {
                sendGlowPacket(member, target, true);
            }
            if (actionBarEnabled) {
                member.sendActionBar(bar(member, target));
            }
        }

        plugin.debug("[Focus] " + player.getName() + " focused " + target.getName() + " for " + faction.getTag());
        startTask();
    }

    public void end(FPlayer fPlayer) {
        Player player = fPlayer.getPlayer();
        Faction faction = fPlayer.getFaction();
        Focus focus = faction == null ? null : focuses.remove(faction.getId());
        if (player == null || focus == null) {
            fPlayer.msg(TL.COMMAND_FOCUS_NONE);
            return;
        }
        cleanupVisuals(faction, focus.targetUuid, true);
        announce(faction, TL.COMMAND_FOCUS_ENDED, focus.targetName);
        if (focuses.isEmpty()) {
            stopTask();
        }
    }

    private boolean isWithinFactionRange(Faction faction, Player target) {
        if (maxRange <= 0) {
            return true;
        }
        if (faction == null || target == null || !target.isOnline()) {
            return false;
        }
        Location targetLoc = target.getLocation();
        World targetWorld = targetLoc.getWorld();
        if (targetWorld == null) {
            return false;
        }
        double maxDistSq = maxRange * maxRange;
        for (Player member : faction.getOnlinePlayers()) {
            if (member.getWorld() == targetWorld && member.getLocation().distanceSquared(targetLoc) <= maxDistSq) {
                return true;
            }
        }
        return false;
    }

    private List<Player> viewers(Faction faction) {
        List<Player> viewers = new ArrayList<>();
        if (faction == null) {
            return viewers;
        }
        for (Player member : faction.getOnlinePlayers()) {
            if (Permission.FOCUS.has(member)) {
                viewers.add(member);
            }
        }
        return viewers;
    }

    private void announce(Faction faction, TL tl, String targetName) {
        if (faction == null) {
            return;
        }
        String message = plugin.txt().parse(tl.toString(), targetName);
        for (Player member : viewers(faction)) {
            member.sendMessage(message);
        }
    }

    private void cleanupVisuals(Faction faction, UUID targetUuid, boolean clearActionBar) {
        if (faction == null) {
            return;
        }
        Player target = targetUuid != null ? Bukkit.getPlayer(targetUuid) : null;
        for (Player member : viewers(faction)) {
            if (clearActionBar) {
                member.sendActionBar(Component.empty());
            }
            if (target != null && isGlowActive() && member.getWorld() == target.getWorld()) {
                sendGlowPacket(member, target, false);
            }
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
        Iterator<Map.Entry<String, Focus>> iterator = focuses.entrySet().iterator();
        while (iterator.hasNext()) {
            Focus focus = iterator.next().getValue();
            Faction faction = Factions.getInstance().getFactionById(focus.factionId);
            if (faction == null || !faction.isNormal() || faction.getOnlinePlayers().isEmpty()) {
                iterator.remove();
                cleanupVisuals(faction, focus.targetUuid, true);
                continue;
            }

            Player target = plugin.getServer().getPlayer(focus.targetUuid);
            if (target == null || !target.isOnline()) {
                iterator.remove();
                cleanupVisuals(faction, focus.targetUuid, true);
                announce(faction, TL.COMMAND_FOCUS_QUIT, focus.targetName);
                continue;
            }

            if (target.isDead()) {
                iterator.remove();
                cleanupVisuals(faction, focus.targetUuid, true);
                announce(faction, TL.COMMAND_FOCUS_DIED, focus.targetName);
                continue;
            }

            FPlayer targetFPlayer = FPlayers.getInstance().getByPlayer(target);
            Relation rel = faction.getRelationTo(targetFPlayer);
            if (target.isOp() || Permission.FOCUS_IMMUNE.has(target)
                    || rel == Relation.MEMBER || rel == Relation.ALLY || rel == Relation.TRUCE) {
                iterator.remove();
                cleanupVisuals(faction, focus.targetUuid, true);
                announce(faction, TL.COMMAND_FOCUS_ENDED, focus.targetName);
                continue;
            }

            if (maxRange > 0 && !isWithinFactionRange(faction, target)) {
                iterator.remove();
                cleanupVisuals(faction, focus.targetUuid, true);
                announce(faction, TL.COMMAND_FOCUS_OUTOFRANGE, focus.targetName);
                continue;
            }

            for (Player member : viewers(faction)) {
                if (actionBarEnabled) {
                    member.sendActionBar(bar(member, target));
                }
                if (isGlowActive() && member.getWorld() == target.getWorld()) {
                    sendGlowPacket(member, target, true);
                }
            }
        }

        if (focuses.isEmpty()) {
            stopTask();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player dead = event.getEntity();
        UUID deadId = dead.getUniqueId();
        Iterator<Map.Entry<String, Focus>> iterator = focuses.entrySet().iterator();
        while (iterator.hasNext()) {
            Focus focus = iterator.next().getValue();
            if (focus.targetUuid.equals(deadId)) {
                iterator.remove();
                Faction faction = Factions.getInstance().getFactionById(focus.factionId);
                cleanupVisuals(faction, focus.targetUuid, true);
                announce(faction, TL.COMMAND_FOCUS_DIED, focus.targetName);
            }
        }
        if (focuses.isEmpty()) {
            stopTask();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player quitting = event.getPlayer();
        UUID quitId = quitting.getUniqueId();
        Iterator<Map.Entry<String, Focus>> iterator = focuses.entrySet().iterator();
        while (iterator.hasNext()) {
            Focus focus = iterator.next().getValue();
            if (focus.targetUuid.equals(quitId)) {
                iterator.remove();
                Faction faction = Factions.getInstance().getFactionById(focus.factionId);
                cleanupVisuals(faction, focus.targetUuid, true);
                announce(faction, TL.COMMAND_FOCUS_QUIT, focus.targetName);
            }
        }
        if (focuses.isEmpty()) {
            stopTask();
        }
    }

    private Component bar(Player viewer, Player target) {
        Location me = viewer.getLocation();
        Location targetLoc = target.getLocation();
        String text;
        if (me.getWorld() != targetLoc.getWorld()) {
            text = String.format(TL.COMMAND_FOCUS_BAROTHERWORLD.toString(), target.getName(),
                    targetLoc.getWorld() == null ? "?" : targetLoc.getWorld().getName());
        } else {
            double dx = targetLoc.getX() - me.getX();
            double dy = targetLoc.getY() - me.getY();
            double dz = targetLoc.getZ() - me.getZ();
            long distance = Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz));
            String hearts = String.format(Locale.US, "%.1f", Math.max(0.0, target.getHealth() / 2.0));
            text = String.format(TL.COMMAND_FOCUS_BAR.toString(), target.getName(), hearts, distance, arrow(dx, dz, me.getYaw()));
        }
        return LegacyComponentSerializer.legacySection().deserialize(text);
    }

    private static String arrow(double dx, double dz, float viewerYaw) {
        if (dx * dx + dz * dz < 1.0) {
            return "\u2022";
        }
        double targetYaw = Math.toDegrees(Math.atan2(-dx, dz));
        double relative = targetYaw - viewerYaw;
        relative = ((relative + 180.0) % 360.0 + 360.0) % 360.0 - 180.0;
        int index = (int) Math.round(relative / 45.0);
        return ARROWS[((index % 8) + 8) % 8];
    }

    /**
     * Initializes NMS reflection for per-viewer 1.20+ entity glowing metadata packets.
     */
    private void initGlowReflection() {
        if (glowReflectionInitialized) {
            return;
        }
        glowReflectionInitialized = true;

        if (FactionsPlugin.getMCVersion() < 2000) {
            glowSupported = false;
            return;
        }

        try {
            Class<?> packetClass;
            try {
                packetClass = Class.forName("net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket");
            } catch (ClassNotFoundException e) {
                packetClass = Class.forName("net.minecraft.network.protocol.game.PacketPlayOutEntityMetadata");
            }

            metadataPacketConstructor = packetClass.getConstructor(int.class, List.class);

            Class<?> dataValueClass = null;
            try {
                dataValueClass = Class.forName("net.minecraft.network.syncher.SynchedEntityData$DataValue");
            } catch (ClassNotFoundException ignored) {
                Type genericType = metadataPacketConstructor.getGenericParameterTypes()[1];
                if (genericType instanceof ParameterizedType) {
                    Type actual = ((ParameterizedType) genericType).getActualTypeArguments()[0];
                    if (actual instanceof Class<?>) {
                        dataValueClass = (Class<?>) actual;
                    }
                }
            }

            if (dataValueClass == null) {
                return;
            }

            for (Constructor<?> constructor : dataValueClass.getDeclaredConstructors()) {
                if (constructor.getParameterCount() == 3 && constructor.getParameterTypes()[0] == int.class) {
                    constructor.setAccessible(true);
                    dataValueConstructor = constructor;
                    break;
                }
            }

            if (dataValueConstructor == null) {
                return;
            }

            Class<?> serializerClass = dataValueConstructor.getParameterTypes()[1];
            Class<?> serializersRegistryClass;
            try {
                serializersRegistryClass = Class.forName("net.minecraft.network.syncher.EntityDataSerializers");
            } catch (ClassNotFoundException e) {
                serializersRegistryClass = Class.forName("net.minecraft.network.syncher.DataWatcherRegistry");
            }

            try {
                Field byteField = serializersRegistryClass.getDeclaredField("BYTE");
                byteField.setAccessible(true);
                byteEntityDataSerializer = byteField.get(null);
            } catch (NoSuchFieldException ignored) {
                for (Field field : serializersRegistryClass.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers()) && serializerClass.isAssignableFrom(field.getType())) {
                        field.setAccessible(true);
                        byteEntityDataSerializer = field.get(null);
                        break;
                    }
                }
            }

            glowSupported = byteEntityDataSerializer != null;
        } catch (Throwable t) {
            glowSupported = false;
            plugin.debug("[Focus] 1.20+ glow packet reflection unavailable: " + t.getMessage());
        }
    }

    private void sendGlowPacket(Player viewer, Player target, boolean glowing) {
        if (!isGlowActive() || viewer == null || target == null) {
            return;
        }
        try {
            byte flags = computeEntitySharedFlags(target, glowing);
            Object dataValue = dataValueConstructor.newInstance(0, byteEntityDataSerializer, flags);
            Object packet = metadataPacketConstructor.newInstance(target.getEntityId(), Collections.singletonList(dataValue));

            if (getHandleMethod == null) {
                getHandleMethod = viewer.getClass().getMethod("getHandle");
            }
            Object handle = getHandleMethod.invoke(viewer);
            if (handle == null) {
                return;
            }

            if (connectionField == null) {
                connectionField = findConnectionField(handle.getClass());
            }
            if (connectionField == null) {
                return;
            }
            Object connection = connectionField.get(handle);
            if (connection == null) {
                return;
            }

            if (sendPacketMethod == null) {
                sendPacketMethod = findSendPacketMethod(connection.getClass());
            }
            if (sendPacketMethod != null) {
                sendPacketMethod.invoke(connection, packet);
            }
        } catch (Throwable ignored) {
        }
    }

    private static byte computeEntitySharedFlags(Player target, boolean forceGlow) {
        byte flags = 0;
        if (target.getFireTicks() > 0) {
            flags |= 0x01;
        }
        if (target.isSneaking()) {
            flags |= 0x02;
        }
        if (target.isSprinting()) {
            flags |= 0x08;
        }
        if (target.isSwimming()) {
            flags |= 0x10;
        }
        if (target.isInvisible()) {
            flags |= 0x20;
        }
        if (forceGlow || target.isGlowing()) {
            flags |= 0x40;
        }
        if (target.isGliding()) {
            flags |= (byte) 0x80;
        }
        return flags;
    }

    private static Field findConnectionField(Class<?> handleClass) {
        try {
            Field f = handleClass.getField("connection");
            f.setAccessible(true);
            return f;
        } catch (NoSuchFieldException ignored) {
        }
        Class<?> packetListenerClass = null;
        try {
            packetListenerClass = Class.forName("net.minecraft.network.protocol.game.ServerGamePacketListener");
        } catch (ClassNotFoundException ignored) {
        }
        for (Class<?> c = handleClass; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (packetListenerClass != null && packetListenerClass.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    return field;
                }
                String typeName = field.getType().getSimpleName();
                if (typeName.contains("ServerGamePacketListenerImpl") || typeName.contains("PlayerConnection")) {
                    field.setAccessible(true);
                    return field;
                }
            }
        }
        return null;
    }

    private static Method findSendPacketMethod(Class<?> connectionClass) {
        Class<?> packetInterface;
        try {
            packetInterface = Class.forName("net.minecraft.network.protocol.Packet");
        } catch (ClassNotFoundException e) {
            return null;
        }
        for (String name : new String[]{"send", "sendPacket", "a"}) {
            try {
                Method m = connectionClass.getMethod(name, packetInterface);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) {
            }
        }
        for (Method m : connectionClass.getMethods()) {
            if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == packetInterface && m.getReturnType() == void.class) {
                m.setAccessible(true);
                return m;
            }
        }
        return null;
    }
}
