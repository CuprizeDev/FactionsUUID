package com.massivecraft.factions.listeners;

import com.massivecraft.factions.Board;
import com.massivecraft.factions.FLocation;
import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.FPlayers;
import com.massivecraft.factions.Faction;
import com.massivecraft.factions.FactionsPlugin;
import com.massivecraft.factions.struct.Permission;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FactionsPatchCrumbsListener implements Listener {

    public static boolean DEBUG = false;

    public static void logDebug(String msg) {
        if (DEBUG) {
            FactionsPlugin.getInstance().getLogger().info("[PatchCrumbs DEBUG] " + msg);
        }
    }

    private static final Set<Material> WALL_MATERIALS = new HashSet<>();
    private static final Map<String, ActiveCrumb> activeCrumbs = new ConcurrentHashMap<>();

    static {
        for (Material mat : Material.values()) {
            if (!mat.isBlock() || mat.isAir()) continue;
            String name = mat.name();
            if (name.contains("COBBLESTONE")
                    || name.contains("STONE")
                    || name.contains("OBSIDIAN")
                    || name.contains("DEEPSLATE")
                    || name.contains("BLACKSTONE")
                    || name.contains("BASALT")
                    || name.contains("NETHERRACK")
                    || name.contains("NETHER_BRICK")
                    || name.contains("SAND")
                    || name.contains("TERRACOTTA")
                    || name.contains("CONCRETE")
                    || name.contains("BRICK")
                    || name.contains("PRISMARINE")
                    || name.contains("PURPUR")
                    || name.contains("QUARTZ")
                    || name.contains("IRON_BLOCK")
                    || name.contains("EMERALD_BLOCK")
                    || name.contains("GOLD_BLOCK")
                    || name.contains("DIAMOND_BLOCK")
                    || name.contains("NETHERITE_BLOCK")
                    || name.contains("ANVIL")
                    || name.contains("SLIME_BLOCK")
                    || name.contains("HONEY_BLOCK")
                    || name.contains("BEDROCK")
                    || name.contains("GLASS")) {
                WALL_MATERIALS.add(mat);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (!(event.getEntity() instanceof TNTPrimed)) {
            return;
        }

        TNTPrimed tnt = (TNTPrimed) event.getEntity();
        Location origin = tnt.getOrigin();
        Location tntLoc = tnt.getLocation();

        if (origin == null || origin.getWorld() == null || !origin.getWorld().equals(tnt.getWorld())) {
            return;
        }

        int dx = tntLoc.getBlockX() - origin.getBlockX();
        int dz = tntLoc.getBlockZ() - origin.getBlockZ();
        int travelDist = Math.max(Math.abs(dx), Math.abs(dz));

        // Stage 1: Require horizontal travel distance of at least 12 blocks to ignore stationary / booster / internal cannon TNT
        if (travelDist < 12) {
            return;
        }

        logDebug(String.format("TNT Explode at [%d, %d, %d] (origin=[%d, %d, %d], dx=%d, dz=%d, blocks=%d)",
                tntLoc.getBlockX(), tntLoc.getBlockY(), tntLoc.getBlockZ(),
                origin.getBlockX(), origin.getBlockY(), origin.getBlockZ(),
                dx, dz, event.blockList().size()));

        int tntY = tntLoc.getBlockY();

        // Determine primary cardinal shot direction
        BlockFace shotDir;
        if (Math.abs(dz) >= Math.abs(dx)) {
            shotDir = dz > 0 ? BlockFace.SOUTH : BlockFace.NORTH;
        } else {
            shotDir = dx > 0 ? BlockFace.EAST : BlockFace.WEST;
        }

        // Check if an active crumb exists along this specific shot line and elevation
        ActiveCrumb current = findActiveCrumb(tntLoc, shotDir);

        // Filter out nuke / trench drops exploding below the active wall breach line
        if (current != null && tntY < current.getBlockLocation().getBlockY() - 2) {
            logDebug(String.format("Explosion at [%d, %d, %d] ignored: nuke/trench drop below active breach (%d).",
                    tntLoc.getBlockX(), tntLoc.getBlockY(), tntLoc.getBlockZ(), current.getBlockLocation().getBlockY()));
            return;
        }

        // Also ignore standalone nuke/trench drops far below cannon barrel height (Y < 180)
        if (current == null && tntY < 180) {
            logDebug(String.format("Explosion at [%d, %d, %d] ignored: standalone trench drop (Y < 180).",
                    tntLoc.getBlockX(), tntLoc.getBlockY(), tntLoc.getBlockZ()));
            return;
        }

        // Stage 2: Isolating the deepest breached wall coordinate along shotDir
        Block deepestWallBlock = null;
        int wallBlocksFound = 0;

        if (!event.blockList().isEmpty()) {
            for (Block block : event.blockList()) {
                if (!WALL_MATERIALS.contains(block.getType())) {
                    continue;
                }
                wallBlocksFound++;
                if (deepestWallBlock == null) {
                    deepestWallBlock = block;
                } else {
                    switch (shotDir) {
                        case NORTH: // -Z
                            if (block.getZ() < deepestWallBlock.getZ()) deepestWallBlock = block;
                            break;
                        case SOUTH: // +Z
                            if (block.getZ() > deepestWallBlock.getZ()) deepestWallBlock = block;
                            break;
                        case WEST: // -X
                            if (block.getX() < deepestWallBlock.getX()) deepestWallBlock = block;
                            break;
                        case EAST: // +X
                            if (block.getX() > deepestWallBlock.getX()) deepestWallBlock = block;
                            break;
                        default:
                            break;
                    }
                }
            }
        }

        // Watered wall / Custom blast resistance fallback: check along shotDir strictly for WALL_MATERIALS
        if (deepestWallBlock == null) {
            for (int step = 0; step <= 5; step++) {
                Block b = tntLoc.getBlock().getRelative(shotDir, step);
                if (WALL_MATERIALS.contains(b.getType())) {
                    deepestWallBlock = b;
                    break;
                }
            }
        }

        // Must hit an actual wall or destroy wall blocks
        if (deepestWallBlock == null) {
            logDebug(String.format("Explosion at [%d, %d, %d] ignored: no wall blocks breached or in front.",
                    tntLoc.getBlockX(), tntLoc.getBlockY(), tntLoc.getBlockZ()));
            return;
        }

        // Calculate the exact patch crumb coordinate aligned with the barrel hit!
        int crumbX;
        int crumbY = (current != null) ? current.getBlockLocation().getBlockY() : tntLoc.getBlockY();
        int crumbZ;

        if (shotDir == BlockFace.NORTH || shotDir == BlockFace.SOUTH) {
            crumbZ = deepestWallBlock.getZ();
            crumbX = tntLoc.getBlockX(); // Exact barrel line along lateral X
        } else {
            crumbX = deepestWallBlock.getX();
            crumbZ = tntLoc.getBlockZ(); // Exact barrel line along lateral Z
        }

        Location crumbLoc = new Location(tntLoc.getWorld(), crumbX, crumbY, crumbZ);
        Faction targetFaction = Board.getInstance().getFactionAt(new FLocation(crumbLoc));
        String tag = (targetFaction != null && targetFaction.isNormal()) ? targetFaction.getTag() : "Wilderness";

        // Stage 3: Collect eligible viewers: any player with /f crumbs ON within 160 blocks (10 chunks)
        List<Player> eligibleViewers = getEligibleViewers(crumbLoc);
        if (eligibleViewers.isEmpty()) {
            logDebug(String.format("Wall breach at [%d, %d, %d] in [%s], but NO players within 160m have /f crumbs ON.",
                    crumbX, crumbY, crumbZ, tag));
            return;
        }

        // Clean up any stray lower trench crumbs in this same shot column
        activeCrumbs.values().removeIf(crumb -> {
            if (crumb == current) return false;
            Location cl = crumb.getBlockLocation();
            if (cl != null && cl.getWorld() != null && cl.getWorld().equals(crumbLoc.getWorld())) {
                boolean sameColumn = (shotDir == BlockFace.EAST || shotDir == BlockFace.WEST)
                        ? (Math.abs(cl.getBlockZ() - crumbLoc.getBlockZ()) <= 4 && Math.abs(cl.getBlockX() - crumbLoc.getBlockX()) <= 64)
                        : (Math.abs(cl.getBlockX() - crumbLoc.getBlockX()) <= 4 && Math.abs(cl.getBlockZ() - crumbLoc.getBlockZ()) <= 64);
                if (sameColumn && cl.getBlockY() < crumbLoc.getBlockY()) {
                    crumb.cleanup();
                    return true;
                }
            }
            return false;
        });

        // Stage 4: Multi-TNT cannon shot deduplication & auto-fire tracking
        long now = System.currentTimeMillis();

        if (current == null) {
            logDebug(String.format("Crumb SPAWNED for [%s] at [%d, %d, %d] (destroyed: %d blocks along %s, viewers: %d)",
                    tag, crumbX, crumbY, crumbZ, wallBlocksFound, shotDir.name(), eligibleViewers.size()));
            ActiveCrumb newCrumb = new ActiveCrumb(crumbLoc, now, shotDir);
            activeCrumbs.put(newCrumb.getId(), newCrumb);
        } else {
            boolean lateralShift = (shotDir == BlockFace.EAST || shotDir == BlockFace.WEST)
                    ? (crumbZ != current.getBlockLocation().getBlockZ())
                    : (crumbX != current.getBlockLocation().getBlockX());

            boolean deeper = isDeeperAlong(crumbX, crumbZ, current.getBlockLocation(), shotDir);
            boolean sameSpot = (crumbX == current.getBlockLocation().getBlockX()
                    && crumbZ == current.getBlockLocation().getBlockZ());

            if (lateralShift) {
                logDebug(String.format("Crumb BARREL ADJUSTED for [%s] to [%d, %d, %d] (shifted lateral line along %s)",
                        tag, crumbX, crumbY, crumbZ, shotDir.name()));
                current.updateLocation(crumbLoc, shotDir);
                current.refreshTimestamp(now);
            } else if (deeper) {
                logDebug(String.format("Crumb UPDATED for [%s] to [%d, %d, %d] (deeper breach along %s)",
                        tag, crumbX, crumbY, crumbZ, shotDir.name()));
                current.updateLocation(crumbLoc, shotDir);
                current.refreshTimestamp(now);
            } else if (sameSpot) {
                current.refreshTimestamp(now);
            } else {
                if (now - current.getLastShotTimestamp() > 2500) {
                    logDebug(String.format("Crumb RELOCATED for [%s] to [%d, %d, %d] (re-breached along %s)",
                            tag, crumbX, crumbY, crumbZ, shotDir.name()));
                    current.updateLocation(crumbLoc, shotDir);
                    current.refreshTimestamp(now);
                } else {
                    current.refreshTimestamp(now);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Location placedLoc = event.getBlock().getLocation();
        activeCrumbs.entrySet().removeIf(entry -> {
            ActiveCrumb crumb = entry.getValue();
            if (crumb.matchesLocation(placedLoc)) {
                logDebug(String.format("Crumb PATCHED at [%d, %d, %d] by %s placing %s",
                        placedLoc.getBlockX(), placedLoc.getBlockY(), placedLoc.getBlockZ(),
                        event.getPlayer().getName(), event.getBlock().getType().name()));
                crumb.cleanup();
                return true;
            }
            return false;
        });
    }

    private static boolean isDeeperAlong(int newX, int newZ, Location currentLoc, BlockFace shotDir) {
        if (currentLoc == null) {
            return true;
        }
        switch (shotDir) {
            case NORTH:
                return newZ < currentLoc.getBlockZ();
            case SOUTH:
                return newZ > currentLoc.getBlockZ();
            case WEST:
                return newX < currentLoc.getBlockX();
            case EAST:
                return newX > currentLoc.getBlockX();
            default:
                return false;
        }
    }

    private static ActiveCrumb findActiveCrumb(Location loc, BlockFace shotDir) {
        if (loc == null || loc.getWorld() == null) {
            return null;
        }
        ActiveCrumb best = null;
        for (ActiveCrumb crumb : activeCrumbs.values()) {
            Location cLoc = crumb.getBlockLocation();
            if (cLoc == null || cLoc.getWorld() == null || !cLoc.getWorld().equals(loc.getWorld())) {
                continue;
            }

            boolean match = false;
            // Directional alignment:
            // For East/West shots: lateral axis is Z (must match closely <= 3), longitudinal axis is X (<= 48)
            // For North/South shots: lateral axis is X (must match closely <= 3), longitudinal axis is Z (<= 48)
            // An explosion belongs to this breach column if it is at or below the barrel breach line (loc.getY() <= cLoc.getY() + 3)
            if (shotDir == BlockFace.EAST || shotDir == BlockFace.WEST) {
                if (Math.abs(cLoc.getBlockZ() - loc.getBlockZ()) <= 4
                        && Math.abs(cLoc.getBlockX() - loc.getBlockX()) <= 64
                        && loc.getBlockY() <= cLoc.getBlockY() + 3) {
                    match = true;
                }
            } else if (shotDir == BlockFace.NORTH || shotDir == BlockFace.SOUTH) {
                if (Math.abs(cLoc.getBlockX() - loc.getBlockX()) <= 4
                        && Math.abs(cLoc.getBlockZ() - loc.getBlockZ()) <= 64
                        && loc.getBlockY() <= cLoc.getBlockY() + 3) {
                    match = true;
                }
            } else {
                if (Math.abs(cLoc.getBlockX() - loc.getBlockX()) <= 16
                        && Math.abs(cLoc.getBlockZ() - loc.getBlockZ()) <= 16
                        && loc.getBlockY() <= cLoc.getBlockY() + 3) {
                    match = true;
                }
            }

            if (match) {
                // Prioritize the highest crumb in the column (the barrel breach line)
                if (best == null || cLoc.getBlockY() > best.getBlockLocation().getBlockY()) {
                    best = crumb;
                }
            }
        }
        return best;
    }

    public static List<Player> getEligibleViewers(Location center) {
        List<Player> viewers = new ArrayList<>();
        if (center == null || center.getWorld() == null) {
            return viewers;
        }
        for (Player p : center.getWorld().getPlayers()) {
            if (p == null || !p.isOnline()) {
                continue;
            }
            if (p.getLocation().distanceSquared(center) <= 160 * 160) {
                FPlayer fPlayer = FPlayers.getInstance().getByPlayer(p);
                if (fPlayer != null && fPlayer.hasPatchCrumbs()) {
                    viewers.add(p);
                }
            }
        }
        return viewers;
    }

    public static void cleanupAll() {
        for (ActiveCrumb crumb : activeCrumbs.values()) {
            crumb.cleanup();
        }
        activeCrumbs.clear();
    }

    public static class ActiveCrumb {
        private final String id;
        private Location blockLocation;
        private BlockFace shotDir;
        private long lastShotTimestamp;
        private BlockDisplay display;
        private BlockDisplay beamDisplay;
        private BukkitRunnable particleTask;
        private boolean cleanedUp = false;

        public ActiveCrumb(Location blockLocation, long shotTimestamp, BlockFace shotDir) {
            this.id = UUID.randomUUID().toString();
            this.blockLocation = blockLocation.getBlock().getLocation();
            this.lastShotTimestamp = shotTimestamp;
            this.shotDir = shotDir;

            spawnDisplay();
            startParticleTask();
        }

        public String getId() {
            return id;
        }

        public long getLastShotTimestamp() {
            return lastShotTimestamp;
        }

        public void refreshTimestamp(long now) {
            this.lastShotTimestamp = now;
        }

        public Location getBlockLocation() {
            return blockLocation;
        }

        public boolean matchesLocation(Location loc) {
            return this.blockLocation != null
                    && loc != null
                    && this.blockLocation.getWorld() != null
                    && this.blockLocation.getWorld().equals(loc.getWorld())
                    && this.blockLocation.getBlockX() == loc.getBlockX()
                    && this.blockLocation.getBlockZ() == loc.getBlockZ()
                    && Math.abs(this.blockLocation.getBlockY() - loc.getBlockY()) <= 2;
        }

        public void updateLocation(Location newLocation, BlockFace newDir) {
            if (this.cleanedUp) {
                return;
            }
            this.blockLocation = newLocation.getBlock().getLocation();
            if (newDir != null) {
                this.shotDir = newDir;
            }
            if (this.display != null && this.display.isValid()) {
                this.display.teleport(this.blockLocation);
            }
            if (this.beamDisplay != null && this.beamDisplay.isValid()) {
                this.beamDisplay.teleport(this.blockLocation);
                this.beamDisplay.setTransformation(createBeamTransformation(this.shotDir));
            }
        }

        private static final float BEAM_OUTWARD_BLOCKS = 80.0f; // 5 chunks toward cannon / incoming direction
        private static final float BEAM_INWARD_BLOCKS = 80.0f;  // 5 chunks past the breached wall into base

        private Transformation createBeamTransformation(BlockFace dir) {
            float transX = 0.35f;
            float transY = 0.35f;
            float transZ = 0.35f;
            float scaleX = 0.3f;
            float scaleY = 0.3f;
            float scaleZ = 0.3f;

            if (dir == null) {
                dir = BlockFace.EAST;
            }

            float totalLength = BEAM_OUTWARD_BLOCKS + BEAM_INWARD_BLOCKS;

            switch (dir) {
                case EAST: // Came from WEST (-X), extend outward along -X, and inward along +X
                    transX = -BEAM_OUTWARD_BLOCKS;
                    scaleX = totalLength;
                    break;
                case WEST: // Came from EAST (+X), extend outward along +X, and inward along -X
                    transX = -BEAM_INWARD_BLOCKS;
                    scaleX = totalLength;
                    break;
                case SOUTH: // Came from NORTH (-Z), extend outward along -Z, and inward along +Z
                    transZ = -BEAM_OUTWARD_BLOCKS;
                    scaleZ = totalLength;
                    break;
                case NORTH: // Came from SOUTH (+Z), extend outward along +Z, and inward along -Z
                    transZ = -BEAM_INWARD_BLOCKS;
                    scaleZ = totalLength;
                    break;
                default:
                    break;
            }

            return new Transformation(
                    new Vector3f(transX, transY, transZ),
                    new AxisAngle4f(),
                    new Vector3f(scaleX, scaleY, scaleZ),
                    new AxisAngle4f()
            );
        }

        private void spawnDisplay() {
            if (this.blockLocation.getWorld() == null) {
                return;
            }
            try {
                // 1. Highlight box at the breach coordinate
                this.display = this.blockLocation.getWorld().spawn(this.blockLocation, BlockDisplay.class, d -> {
                    d.setBlock(Material.RED_STAINED_GLASS.createBlockData());
                    d.setGlowing(true);
                    d.setGlowColorOverride(Color.fromRGB(255, 20, 20));
                    d.setBrightness(new Display.Brightness(15, 15));
                    d.setViewRange(5.0f);
                    d.setPersistent(false);
                    d.setVisibleByDefault(false);
                    d.setTransformation(new Transformation(
                            new Vector3f(0.001f, 0.001f, 0.001f),
                            new AxisAngle4f(),
                            new Vector3f(0.998f, 0.998f, 0.998f),
                            new AxisAngle4f()
                    ));
                });

                // 2. Outward glowing tracer beam extending in the direction the TNT came from & past the wall
                this.beamDisplay = this.blockLocation.getWorld().spawn(this.blockLocation, BlockDisplay.class, d -> {
                    d.setBlock(Material.RED_STAINED_GLASS.createBlockData());
                    d.setGlowing(true);
                    d.setGlowColorOverride(Color.fromRGB(255, 20, 20));
                    d.setBrightness(new Display.Brightness(15, 15));
                    d.setViewRange(5.0f);
                    d.setDisplayWidth(200.0f);
                    d.setDisplayHeight(200.0f);
                    d.setPersistent(false);
                    d.setVisibleByDefault(false);
                    d.setTransformation(createBeamTransformation(this.shotDir));
                });

                for (Player viewer : getEligibleViewers(this.blockLocation)) {
                    viewer.showEntity(FactionsPlugin.getInstance(), this.display);
                    viewer.showEntity(FactionsPlugin.getInstance(), this.beamDisplay);
                }
            } catch (Throwable t) {
                FactionsPlugin.getInstance().getLogger().warning("Could not spawn BlockDisplay for patch crumbs: " + t.getMessage());
                logDebug("ERROR spawning BlockDisplay: " + t.getMessage());
            }
        }

        private void startParticleTask() {
            Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(255, 30, 30), 1.0F);
            Particle.DustOptions lineDust = new Particle.DustOptions(Color.fromRGB(255, 60, 60), 1.2F);

            this.particleTask = new BukkitRunnable() {
                @Override
                public void run() {
                    long elapsedSinceShot = System.currentTimeMillis() - lastShotTimestamp;
                    if (cleanedUp || elapsedSinceShot > 5000) {
                        cleanup();
                        activeCrumbs.remove(id);
                        this.cancel();
                        return;
                    }

                    List<Player> viewers = getEligibleViewers(blockLocation);
                    if (viewers.isEmpty()) {
                        return;
                    }

                    if (display != null && display.isValid()) {
                        for (Player viewer : viewers) {
                            viewer.showEntity(FactionsPlugin.getInstance(), display);
                        }
                    }
                    if (beamDisplay != null && beamDisplay.isValid()) {
                        for (Player viewer : viewers) {
                            viewer.showEntity(FactionsPlugin.getInstance(), beamDisplay);
                        }
                    }

                    renderWireframe(viewers, dust);
                    renderTracerLine(viewers, lineDust);
                }
            };

            this.particleTask.runTaskTimer(FactionsPlugin.getInstance(), 0L, 10L);
        }

        private void renderTracerLine(List<Player> viewers, Particle.DustOptions dust) {
            if (this.blockLocation == null || this.blockLocation.getWorld() == null || this.shotDir == null) {
                return;
            }

            double centerX = this.blockLocation.getBlockX() + 0.5;
            double centerY = this.blockLocation.getBlockY() + 0.5;
            double centerZ = this.blockLocation.getBlockZ() + 0.5;

            BlockFace fromDir = this.shotDir.getOppositeFace();
            double fromX = fromDir.getModX();
            double fromZ = fromDir.getModZ();

            Location lineLoc = new Location(this.blockLocation.getWorld(), 0, centerY, 0);

            // Extend 80 blocks (5 chunks) out in the direction the shot came from (towards the cannon/attacker)
            for (double step = 1.0; step <= 80.0; step += 1.0) {
                lineLoc.setX(centerX + (fromX * step));
                lineLoc.setZ(centerZ + (fromZ * step));
                for (Player viewer : viewers) {
                    viewer.spawnParticle(Particle.DUST, lineLoc, 1, 0, 0, 0, 0, dust);
                }
            }

            // Also extend 80 blocks (5 chunks) forward past the wall (along shotDir) into the base
            double forwardX = this.shotDir.getModX();
            double forwardZ = this.shotDir.getModZ();
            for (double step = 1.0; step <= 80.0; step += 1.0) {
                lineLoc.setX(centerX + (forwardX * step));
                lineLoc.setZ(centerZ + (forwardZ * step));
                for (Player viewer : viewers) {
                    viewer.spawnParticle(Particle.DUST, lineLoc, 1, 0, 0, 0, 0, dust);
                }
            }
        }

        private void renderWireframe(List<Player> viewers, Particle.DustOptions dust) {
            if (this.blockLocation == null || this.blockLocation.getWorld() == null) {
                return;
            }

            double minX = this.blockLocation.getBlockX();
            double minY = this.blockLocation.getBlockY();
            double minZ = this.blockLocation.getBlockZ();
            double maxX = minX + 1.0;
            double maxY = minY + 1.0;
            double maxZ = minZ + 1.0;

            Location particleLoc = new Location(this.blockLocation.getWorld(), 0, 0, 0);

            for (double step = 0.0; step <= 1.0; step += 0.25) {
                // Bottom 4 edges (minY)
                spawnAt(viewers, particleLoc, minX + step, minY, minZ, dust);
                spawnAt(viewers, particleLoc, minX + step, minY, maxZ, dust);
                spawnAt(viewers, particleLoc, minX, minY, minZ + step, dust);
                spawnAt(viewers, particleLoc, maxX, minY, minZ + step, dust);

                // Top 4 edges (maxY)
                spawnAt(viewers, particleLoc, minX + step, maxY, minZ, dust);
                spawnAt(viewers, particleLoc, minX + step, maxY, maxZ, dust);
                spawnAt(viewers, particleLoc, minX, maxY, minZ + step, dust);
                spawnAt(viewers, particleLoc, maxX, maxY, minZ + step, dust);

                // 4 Vertical edges (along Y)
                spawnAt(viewers, particleLoc, minX, minY + step, minZ, dust);
                spawnAt(viewers, particleLoc, maxX, minY + step, minZ, dust);
                spawnAt(viewers, particleLoc, minX, minY + step, maxZ, dust);
                spawnAt(viewers, particleLoc, maxX, minY + step, maxZ, dust);
            }
        }

        private void spawnAt(List<Player> viewers, Location tempLoc, double x, double y, double z, Particle.DustOptions dust) {
            tempLoc.setX(x);
            tempLoc.setY(y);
            tempLoc.setZ(z);
            for (Player viewer : viewers) {
                viewer.spawnParticle(Particle.DUST, tempLoc, 1, 0, 0, 0, 0, dust);
            }
        }

        public void cleanup() {
            if (this.cleanedUp) {
                return;
            }
            this.cleanedUp = true;
            if (this.blockLocation != null) {
                logDebug(String.format("Crumb at [%d, %d, %d] expired / cleaned up.",
                        this.blockLocation.getBlockX(), this.blockLocation.getBlockY(), this.blockLocation.getBlockZ()));
            }
            if (this.particleTask != null) {
                try {
                    this.particleTask.cancel();
                } catch (Throwable ignored) {
                }
                this.particleTask = null;
            }
            if (this.display != null) {
                try {
                    if (this.display.isValid()) {
                        this.display.remove();
                    }
                } catch (Throwable ignored) {
                }
                this.display = null;
            }
            if (this.beamDisplay != null) {
                try {
                    if (this.beamDisplay.isValid()) {
                        this.beamDisplay.remove();
                    }
                } catch (Throwable ignored) {
                }
                this.beamDisplay = null;
            }
        }
    }
}
