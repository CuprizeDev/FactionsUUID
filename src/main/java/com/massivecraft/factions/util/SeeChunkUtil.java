package com.massivecraft.factions.util;

import com.massivecraft.factions.Board;
import com.massivecraft.factions.FLocation;
import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.FPlayers;
import com.massivecraft.factions.FactionsPlugin;
import com.massivecraft.factions.util.particle.ParticleColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SeeChunkUtil extends BukkitRunnable {

    private final Set<UUID> playersSeeingChunks = new HashSet<>();
    private final Map<UUID, Set<Location>> activeBarriers = new ConcurrentHashMap<>();
    private final boolean useColor;
    private Object effect;

    private static Method sendBlockDataMethod;
    private static Method getBlockDataMethod;

    static {
        try {
            getBlockDataMethod = Block.class.getMethod("getBlockData");
            Class<?> blockDataClass = Class.forName("org.bukkit.block.data.BlockData");
            sendBlockDataMethod = Player.class.getMethod("sendBlockChange", Location.class, blockDataClass);
        } catch (Throwable ignored) {
            // Legacy Bukkit (1.8 - 1.12)
        }
    }

    public SeeChunkUtil() {
        String effectName;
        if (FactionsPlugin.getInstance().conf().commands().seeChunk().isBarriers()) {
            effectName = "BARRIER";
            this.useColor = false;
        } else {
            effectName = FactionsPlugin.getInstance().conf().commands().seeChunk().getParticleName();
            this.useColor = FactionsPlugin.getInstance().conf().commands().seeChunk().isRelationalColor();
        }

        this.effect = FactionsPlugin.getInstance().getParticleProvider().effectFromString(effectName);
        if (this.effect == null) {
            this.effect = FactionsPlugin.getInstance().getParticleProvider().effectFromString("BARRIER");
        }
        if (this.effect == null) {
            this.effect = FactionsPlugin.getInstance().getParticleProvider().effectFromString("FLAME");
        }
        if (this.effect == null) {
            FactionsPlugin.getInstance().getLogger().warning("Could not find any suitable particle effect for /f sc!");
            return;
        }

        FactionsPlugin.getInstance().getLogger().info(FactionsPlugin.getInstance().txt().parse("Using %s as the ParticleEffect for /f sc", FactionsPlugin.getInstance().getParticleProvider().effectName(effect)));
    }

    @Override
    public void run() {
        for (UUID playerId : new HashSet<>(playersSeeingChunks)) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                playersSeeingChunks.remove(playerId);
                continue;
            }
            FPlayer fme = FPlayers.getInstance().getByPlayer(player);
            showPillars(player, fme, this.effect, this.useColor);
        }
    }

    public void updatePlayerInfo(UUID uuid, boolean toggle) {
        Player player = Bukkit.getPlayer(uuid);
        if (toggle) {
            playersSeeingChunks.add(uuid);
            if (player != null && player.isOnline()) {
                // Clear any legacy client-side barrier blocks to eliminate collision
                clearBarriers(player);
                clearChunkBlocks(player, player.getLocation().getChunk());
                FPlayer fme = FPlayers.getInstance().getByPlayer(player);
                showPillars(player, fme, this.effect, this.useColor);
            }
        } else {
            playersSeeingChunks.remove(uuid);
            if (player != null) {
                clearBarriers(player);
                clearChunkBlocks(player, player.getLocation().getChunk());
            } else {
                activeBarriers.remove(uuid);
            }
        }
    }

    public void onChunkChange(Player player, Location to) {
        if (player == null || to == null) {
            return;
        }
        if (playersSeeingChunks.contains(player.getUniqueId())) {
            int chunkX = to.getBlockX() >> 4;
            int chunkZ = to.getBlockZ() >> 4;
            World world = to.getWorld();
            int playerY = to.getBlockY();

            FPlayer fme = FPlayers.getInstance().getByPlayer(player);
            showPillars(player, fme, chunkX, chunkZ, playerY, world, this.effect, this.useColor);
        }
    }

    public void clearBarriers(Player player) {
        if (player == null) {
            return;
        }
        Set<Location> locations = activeBarriers.remove(player.getUniqueId());
        if (locations == null || locations.isEmpty()) {
            return;
        }

        World world = player.getWorld();
        for (Location loc : locations) {
            if (loc.getWorld().equals(world)) {
                restoreBlock(player, loc);
            }
        }
    }

    public static void clearChunkBlocks(Player player, Chunk chunk) {
        if (player == null || chunk == null) {
            return;
        }
        World world = chunk.getWorld();
        int chunkX = chunk.getX();
        int chunkZ = chunk.getZ();
        int minX = chunkX * 16;
        int maxX = minX + 15;
        int minZ = chunkZ * 16;
        int maxZ = minZ + 15;

        int minHeight = getMinWorldHeight(world);
        int maxHeight = getMaxWorldHeight(world);
        int startY = Math.max(minHeight, player.getLocation().getBlockY() - 40);
        int endY = Math.min(maxHeight, player.getLocation().getBlockY() + 40);

        int[][] corners = new int[][] {
            {minX, minZ},
            {minX, maxZ},
            {maxX, minZ},
            {maxX, maxZ}
        };

        for (int[] corner : corners) {
            int x = corner[0];
            int z = corner[1];
            for (int y = startY; y < endY; y++) {
                Block block = world.getBlockAt(x, y, z);
                if (isAir(block.getType())) {
                    restoreBlock(player, new Location(world, x, y, z));
                }
            }
        }
    }

    private static void restoreBlock(Player player, Location loc) {
        Block block = loc.getBlock();
        if (sendBlockDataMethod != null && getBlockDataMethod != null) {
            try {
                sendBlockDataMethod.invoke(player, loc, getBlockDataMethod.invoke(block));
                return;
            } catch (Throwable ignored) {
            }
        }
        player.sendBlockChange(loc, block.getType(), block.getData());
    }

    public void cleanup() {
        try {
            this.cancel();
        } catch (Throwable ignored) {
        }
        for (UUID uuid : new HashSet<>(activeBarriers.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                clearBarriers(player);
                clearChunkBlocks(player, player.getLocation().getChunk());
            }
        }
        activeBarriers.clear();
        playersSeeingChunks.clear();
    }

    public static int getMinWorldHeight(World world) {
        try {
            Method method = World.class.getMethod("getMinHeight");
            return (int) method.invoke(world);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    public static int getMaxWorldHeight(World world) {
        return world.getMaxHeight();
    }

    private static boolean isAir(Material material) {
        return material == Material.AIR || material.name().endsWith("AIR");
    }

    public static void showPillars(Player me, FPlayer fme, Object effect, boolean useColor) {
        if (me == null || !me.isOnline()) {
            return;
        }
        Location loc = me.getLocation();
        showPillars(me, fme, loc.getBlockX() >> 4, loc.getBlockZ() >> 4, loc.getBlockY(), loc.getWorld(), effect, useColor);
    }

    public static void showPillars(Player me, FPlayer fme, int chunkX, int chunkZ, int playerY, World world, Object effect, boolean useColor) {
        if (me == null || world == null) {
            return;
        }

        ParticleColor color = null;
        if (useColor) {
            FLocation flocation = new FLocation(world.getName(), chunkX, chunkZ);
            ChatColor chatColor = Board.getInstance().getFactionAt(flocation).getRelationTo(fme).getColor();
            color = ParticleColor.fromChatColor(chatColor);
        }

        int minX = chunkX * 16;
        int maxX = minX + 15;
        int minZ = chunkZ * 16;
        int maxZ = minZ + 15;

        showPillar(me, world, playerY, minX, minZ, effect, color);
        showPillar(me, world, playerY, maxX, minZ, effect, color);
        showPillar(me, world, playerY, minX, maxZ, effect, color);
        showPillar(me, world, playerY, maxX, maxZ, effect, color);
    }

    public static void showPillar(Player player, World world, int blockX, int blockZ, Object effect, ParticleColor color) {
        showPillar(player, world, player.getLocation().getBlockY(), blockX, blockZ, effect, color);
    }

    public static void showPillar(Player player, World world, int playerY, int blockX, int blockZ, Object effect, ParticleColor color) {
        int minHeight = getMinWorldHeight(world);
        int maxHeight = getMaxWorldHeight(world);

        int startY = Math.max(minHeight, playerY - 40);
        int endY = Math.min(maxHeight, playerY + 40);

        for (int blockY = startY; blockY < endY; blockY++) {
            Block block = world.getBlockAt(blockX, blockY, blockZ);
            if (!isAir(block.getType())) {
                continue;
            }

            Location loc = new Location(world, blockX + 0.5, blockY + 0.5, blockZ + 0.5);

            if (effect != null) {
                if (color == null) {
                    FactionsPlugin.getInstance().getParticleProvider().playerSpawn(player, effect, loc, 1);
                } else {
                    FactionsPlugin.getInstance().getParticleProvider().playerSpawn(player, effect, loc, color);
                }
            }
        }
    }

}
