package com.massivecraft.factions.listeners;

import com.google.gson.reflect.TypeToken;
import com.massivecraft.factions.util.DiscUtil;
import com.massivecraft.factions.util.Durations;
import com.massivecraft.factions.util.TL;
import com.vitaldev.vitallibs.util.ChatUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.FPlayers;
import com.massivecraft.factions.Faction;
import com.massivecraft.factions.Factions;
import com.massivecraft.factions.FactionsPlugin;
import com.massivecraft.factions.config.file.MainConfig;
import com.massivecraft.factions.perms.PermissibleAction;
import com.massivecraft.factions.struct.Permission;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FactionsChunkLoaderListener implements Listener {
    static final int SLOT_UPKEEP = 2, SLOT_INFO = 4, SLOT_TIME = 6;

    static final class Loader {
        final String id;
        final String world;
        final int x, y, z;
        final String faction;
        final UUID owner;
        final String ownerName;
        long remainingMs;
        UUID crystal;
        transient boolean active;
        transient int missing;
        transient String shownName = "";

        Loader(String id, String world, int x, int y, int z, String faction, UUID owner, String ownerName, long remainingMs, UUID crystal) {
            this.id = id;
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.faction = faction;
            this.owner = owner;
            this.ownerName = ownerName;
            this.remainingMs = remainingMs;
            this.crystal = crystal;
        }

        int chunkX() {
            return x >> 4;
        }

        int chunkZ() {
            return z >> 4;
        }
    }

    private static final class View implements InventoryHolder {
        final String loaderId;
        Inventory inv;

        View(String loaderId) {
            this.loaderId = loaderId;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private static final class CK {
        final String world;
        final int cx, cz;

        CK(String world, int cx, int cz) {
            this.world = world;
            this.cx = cx;
            this.cz = cz;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof CK)) return false;
            CK other = (CK) o;
            return cx == other.cx && cz == other.cz && world.equals(other.world);
        }

        @Override
        public int hashCode() {
            return Objects.hash(world, cx, cz);
        }
    }

    private final FactionsPlugin plugin;
    private final Map<String, Loader> loaders = new LinkedHashMap<>();

    private final Map<CK, Integer> refs = new HashMap<>();
    private final Map<CK, Chunk> held = new HashMap<>();
    private boolean restorePending;
    private final Set<CK> pendingChunks = new HashSet<>();
    private final Map<String, Integer> touched = new LinkedHashMap<>();
    private final Map<UUID, View> views = new HashMap<>();
    private final Map<UUID, Long> stackHandled = new ConcurrentHashMap<>();
    private final Listener hookListener = new Listener() { };
    private final Random random = new Random();

    private NamespacedKey keyLoader, keyTime, keyUpkeep, keyEntity;
    private BukkitTask task;
    private boolean started, stackHooked;
    private long lastTick;
    private int tickCount;
    private boolean dirty;
    private int radius = 2, maxPerFaction = 3;
    private Set<String> allowedWorlds = new HashSet<>();
    private boolean requireFaction = true, requireBuild = true, pickupSneak = true, enemiesBreak = true, dropOnBreak = true, showTimer = true;
    private long msPerItem = 30 * 60_000L, maxMs = 48 * 3_600_000L;
    private boolean keepMobs = true;
    private int spawnerRange = 32767, scanEvery = 10;
    private String dropEntity = "ENDERMITE";
    private double dropChance = 0.02;
    private int dropMin = 1, dropMax = 1;
    private boolean dropPlayerOnly = true;

    public FactionsChunkLoaderListener(FactionsPlugin plugin) {
        this.plugin = plugin;
    }

    private MainConfig.ChunkLoaders c() {
        return plugin.conf().chunkLoaders();
    }

    private String text(TL translation, Object... args) {
        return plugin.txt().parse(translation.toString(), args);
    }

    private void msg(Player player, TL translation, Object... args) {
        player.sendMessage(text(translation, args));
    }

    private String factionId(Player player) {
        FPlayer fPlayer = FPlayers.getInstance().getByPlayer(player);
        return fPlayer == null || !fPlayer.hasFaction() ? null : fPlayer.getFactionId();
    }

    private String tag(String factionId) {
        Faction faction = Factions.getInstance().getFactionById(factionId);
        return faction == null ? factionId : faction.getTag();
    }

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder().character('\u00A7').hexColors().useUnusualXRepeatedCharacterHexFormat().build();

    private String color(String s) {
        return ChatUtil.color(s == null ? "" : s);
    }

    private Component component(String s) {
        String text = s == null ? "" : s;
        Component component = text.indexOf('<') >= 0 ? MiniMessage.miniMessage().deserialize(text) : LEGACY.deserialize(color(text));
        return component.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    private static String fill(String s, String... pairs) {
        for (int i = 0; i + 1 < pairs.length; i += 2) s = s.replace(pairs[i], pairs[i + 1]);
        return s;
    }

    private void debug(String s) {
        plugin.debug("[ChunkLoader] " + s);
    }

    public void reload() {
        MainConfig.ChunkLoaders c = c();
        radius = Math.max(0, Math.min(6, c.getRadius()));
        maxPerFaction = Math.max(1, c.getMaxPerFaction());
        allowedWorlds = new HashSet<>(c.getAllowedWorlds());
        requireFaction = c.isRequireFaction();
        requireBuild = c.isRequireBuildPermission();
        pickupSneak = c.isPickupWithSneak();
        enemiesBreak = c.isEnemiesCanBreak();
        dropOnBreak = c.isDropOnBreak();
        showTimer = c.isShowTimer();
        msPerItem = Math.max(1, c.getUpkeep().getMinutesPerItem()) * 60_000L;
        maxMs = Math.max(1, c.getUpkeep().getMaxHours()) * 3_600_000L;
        spawnerRange = Math.max(1, Math.min(32767, c.getWhileUnattended().getSpawnerPlayerRange()));
        keepMobs = c.getWhileUnattended().isKeepSpawnerMobs();
        scanEvery = Math.max(1, c.getWhileUnattended().getScanSeconds());
        dropEntity = c.getDrop().getEntity().toUpperCase(Locale.ROOT);
        dropChance = Math.max(0, Math.min(1, c.getDrop().getChance()));
        dropMin = Math.max(1, c.getDrop().getMin());
        dropMax = Math.max(dropMin, c.getDrop().getMax());
        dropPlayerOnly = c.getDrop().isOnlyPlayerKills();
    }

    public void start() {
        if (started) return;
        started = true;

        keyLoader = NamespacedKey.fromString("debugcore:chunkloader");
        keyTime = NamespacedKey.fromString("debugcore:chunkloader_time");
        keyUpkeep = NamespacedKey.fromString("debugcore:chunkloader_upkeep");
        keyEntity = NamespacedKey.fromString("debugcore:chunkloader_id");
        load();
        restorePending = !touched.isEmpty();
        pendingChunks.clear();
        for (String key : touched.keySet()) {
            String[] k = key.split(";");
            pendingChunks.add(chunkKey(k[0], Integer.parseInt(k[1]) >> 4, Integer.parseInt(k[3]) >> 4));
        }
        lastTick = 0;
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> tick(System.currentTimeMillis()), 20L, 20L);
        hookStack();
        for (Loader l : loaders.values()) {
            if (l.remainingMs > 0) activate(l, false);
        }
    }

    public void stop() {
        if (!started) return;
        started = false;
        if (task != null) {
            task.cancel();
            task = null;
        }
        HandlerList.unregisterAll(hookListener);
        stackHooked = false;
        for (View v : new ArrayList<>(views.values())) {
            if (v.inv != null) v.inv.getViewers().forEach(h -> h.closeInventory());
        }
        views.clear();
        save();
        for (Loader l : loaders.values()) l.active = false;
        for (Chunk held1 : held.values()) held1.removePluginChunkTicket(plugin);
        held.clear();
        refs.clear();
        restoreSpawners();
        saveSpawners();
        loaders.clear();
    }

    void tick(long now) {
        long elapsed = lastTick == 0 ? 0 : Math.max(0, Math.min(now - lastTick, 60_000L));
        lastTick = now;
        tickCount++;
        for (Loader l : new ArrayList<>(loaders.values())) {
            if (l.remainingMs > 0 && elapsed > 0) {
                l.remainingMs = Math.max(0, l.remainingMs - elapsed);
                dirty = true;
            }
            if (l.remainingMs <= 0 && l.active) {
                deactivate(l);
                notifyFaction(l, TL.CHUNKLOADER_RANOUT);
            } else if (l.remainingMs > 0 && !l.active) {
                activate(l, true);
            }
            if (!verify(l)) continue;
            updateName(l);
        }
        if (tickCount % scanEvery == 0) scanSpawners();
        refreshViews();
        if (dirty && tickCount % 30 == 0) {
            save();
            saveSpawners();
        }
        long cutoff = now - 10_000L;
        stackHandled.values().removeIf(t -> t < cutoff);
    }

    private boolean verify(Loader l) {
        World w = plugin.getServer().getWorld(l.world);
        if (w == null || l.crystal == null || !w.isChunkLoaded(l.chunkX(), l.chunkZ())) return true;
        Entity e = w.getEntity(l.crystal);
        if (e != null && e.isValid()) {
            l.missing = 0;
            return true;
        }
        if (++l.missing < 3) return true;
        l.missing = 0;
        respawn(l, "not found");
        return true;
    }

    private void respawn(Loader l, String why) {
        World w = plugin.getServer().getWorld(l.world);
        if (w == null || !loaders.containsKey(l.id) || !w.isChunkLoaded(l.chunkX(), l.chunkZ())) return;
        Entity old = l.crystal == null ? null : w.getEntity(l.crystal);
        if (old != null && old.isValid()) return;
        EnderCrystal c = spawnCrystal(w, new Location(w, l.x + 0.5, l.y, l.z + 0.5), l.id);
        l.crystal = c.getUniqueId();
        l.shownName = null;
        dirty = true;
        updateName(l);
        plugin.getLogger().info("chunkloader: the crystal at " + l.world + " " + l.x + "," + l.y + "," + l.z + " was removed (" + why + ") - put it back");
    }

    private EnderCrystal spawnCrystal(World w, Location at, String id) {
        return w.spawn(at, EnderCrystal.class, cr -> {
            cr.setShowingBottom(false);
            cr.getPersistentDataContainer().set(keyEntity, PersistentDataType.STRING, id);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCrystalRemoved(EntityRemoveEvent e) {
        if (!(e.getEntity() instanceof EnderCrystal)) return;
        String cause = e.getCause().name();
        if (!cause.equals("PLUGIN") && !cause.equals("DESPAWN")) return;
        Loader l = byCrystal(e.getEntity().getUniqueId());
        if (l == null) return;
        plugin.getServer().getScheduler().runTask(plugin, () -> respawn(l, cause.toLowerCase(java.util.Locale.ROOT)));
    }

    private void updateName(Loader l) {
        if (!showTimer || l.crystal == null) return;
        World w = plugin.getServer().getWorld(l.world);
        if (w == null || !w.isChunkLoaded(l.chunkX(), l.chunkZ())) return;
        Entity e = w.getEntity(l.crystal);
        if (e == null) return;
        String name = l.remainingMs > 0 ? color("&d&lChunk Loader &7(" + formatMinutes(l.remainingMs) + ")") : color("&c&lChunk Loader &7(off)");
        if (!name.equals(l.shownName)) {
            e.setCustomName(name);
            e.setCustomNameVisible(true);
            l.shownName = name;
        }
    }

    private static CK chunkKey(String world, int cx, int cz) {
        return new CK(world, cx, cz);
    }

    private void activate(Loader l, boolean announce) {
        if (l.active) return;
        l.active = true;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) ref(l.world, l.chunkX() + dx, l.chunkZ() + dz);
        }
        if (announce) notifyFaction(l, TL.CHUNKLOADER_RUNNING);
        debug("chunkloader: " + l.id + " on (" + l.world + " " + l.x + "," + l.z + ")");
    }

    private void deactivate(Loader l) {
        if (!l.active) return;
        l.active = false;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) unref(l.world, l.chunkX() + dx, l.chunkZ() + dz);
        }
        restoreSpawners();
        debug("chunkloader: " + l.id + " off");
    }

    private void ref(String world, int cx, int cz) {
        CK key = chunkKey(world, cx, cz);
        int now = refs.merge(key, 1, Integer::sum);
        if (now != 1 || held.containsKey(key)) return;
        World w = plugin.getServer().getWorld(world);
        if (w == null) return;
        w.getChunkAtAsync(cx, cz, true).thenAccept(chunk -> {
            if (chunk == null) return;
            if (refs.containsKey(key) && !held.containsKey(key)) {
                chunk.addPluginChunkTicket(plugin);
                held.put(key, chunk);
            }
        });
    }

    private void unref(String world, int cx, int cz) {
        CK key = chunkKey(world, cx, cz);
        Integer n = refs.get(key);
        if (n == null) return;
        if (n > 1) {
            refs.put(key, n - 1);
            return;
        }
        refs.remove(key);
        Chunk chunk = held.remove(key);
        if (chunk != null) chunk.removePluginChunkTicket(plugin);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent e) {
        if (!keepMobs || refs.isEmpty() || e.getSpawnReason() != CreatureSpawnEvent.SpawnReason.SPAWNER) return;
        LivingEntity mob = e.getEntity();
        Location at = mob.getLocation();
        World w = at.getWorld();
        if (w != null && refs.containsKey(chunkKey(w.getName(), at.getBlockX() >> 4, at.getBlockZ() >> 4))) mob.setRemoveWhenFarAway(false);
    }

    void scanSpawners() {
        for (CK key : refs.keySet()) {
            World w = plugin.getServer().getWorld(key.world);
            if (w == null) continue;
            int cx = key.cx, cz = key.cz;
            if (!w.isChunkLoaded(cx, cz)) continue;
            for (BlockState st : w.getChunkAt(cx, cz).getTileEntities(b -> b.getType() == Material.SPAWNER, false)) {
                if (!(st instanceof CreatureSpawner)) continue;
                CreatureSpawner sp = (CreatureSpawner) st;
                int current = sp.getRequiredPlayerRange();
                if (current >= spawnerRange) continue;
                touched.putIfAbsent(key.world + ";" + st.getX() + ";" + st.getY() + ";" + st.getZ(), current);
                sp.setRequiredPlayerRange(spawnerRange);
                sp.update();
                dirty = true;
            }
        }
    }

    void restoreSpawners() {
        boolean changed = false;
        restorePending = false;
        pendingChunks.clear();
        for (Map.Entry<String, Integer> en : new ArrayList<>(touched.entrySet())) {
            String[] k = en.getKey().split(";");
            int x = Integer.parseInt(k[1]), y = Integer.parseInt(k[2]), z = Integer.parseInt(k[3]);
            if (refs.containsKey(chunkKey(k[0], x >> 4, z >> 4))) continue;
            World w = plugin.getServer().getWorld(k[0]);
            if (w == null || !w.isChunkLoaded(x >> 4, z >> 4)) { restorePending = true; pendingChunks.add(chunkKey(k[0], x >> 4, z >> 4)); continue; }
            BlockState st = w.getBlockAt(x, y, z).getState();
            if (st instanceof CreatureSpawner) {
                CreatureSpawner sp = (CreatureSpawner) st;
                sp.setRequiredPlayerRange(en.getValue());
                sp.update();
            }
            touched.remove(en.getKey());
            changed = true;
        }
        if (changed) dirty = true;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent e) {
        if (restorePending && pendingChunks.contains(chunkKey(e.getChunk().getWorld().getName(), e.getChunk().getX(), e.getChunk().getZ()))) restoreSpawners();
    }

    public static int rollKills(int kills, double chance, Random rnd) {
        if (kills <= 0 || chance <= 0) return 0;
        if (chance >= 1) return kills;
        if (kills <= 2000) {
            int n = 0;
            for (int i = 0; i < kills; i++) {
                if (rnd.nextDouble() < chance) n++;
            }
            return n;
        }
        double mean = kills * chance, sd = Math.sqrt(kills * chance * (1 - chance));
        return (int) Math.max(0, Math.min(kills, Math.round(mean + rnd.nextGaussian() * sd)));
    }

    int upkeepFor(int kills) {
        int hits = rollKills(kills, dropChance, random);
        long total = 0;
        for (int i = 0; i < hits && i < 100_000; i++) total += dropMin + random.nextInt(dropMax - dropMin + 1);
        return (int) Math.min(total, 100_000);
    }

    @SuppressWarnings("unchecked")
    private void hookStack() {
        if (stackHooked) return;
        Plugin kore = plugin.getServer().getPluginManager().getPlugin("FactionsKore");
        if (kore == null) return;
        try {
            ClassLoader cl = kore.getClass().getClassLoader();
            Class<? extends Event> evt = (Class<? extends Event>) Class.forName("com.golfing8.kore.event.StackedEntityDeathEvent", true, cl);
            Method getStacked = evt.getMethod("getStackedEntity");
            Method getKilled = evt.getMethod("getAmountKilled");
            Method getKiller = evt.getMethod("getKiller");
            Method getDrops = evt.getMethod("getStackedDrops");
            Method getDeath = evt.getMethod("getEntityDeathEvent");
            Method getType = Class.forName("com.golfing8.kore.object.StackedEntity", true, cl).getMethod("getEntityType");
            Method addDrop = Class.forName("com.golfing8.kore.object.StackedDrops", true, cl).getMethod("addDrop", ItemStack.class);
            plugin.getServer().getPluginManager().registerEvent(evt, hookListener, EventPriority.NORMAL, (l, ev) -> {
                if (!evt.isInstance(ev)) return;
                try {
                    Object type = getType.invoke(getStacked.invoke(ev));
                    if (type == null || !type.toString().equalsIgnoreCase(dropEntity)) return;
                    Object death = getDeath.invoke(ev);
                    if (death instanceof EntityDeathEvent) stackHandled.put(((EntityDeathEvent) death).getEntity().getUniqueId(), System.currentTimeMillis());
                    if (dropPlayerOnly && getKiller.invoke(ev) == null) return;
                    int amount = upkeepFor((int) getKilled.invoke(ev));
                    if (amount <= 0) return;
                    Object drops = getDrops.invoke(ev);
                    ItemStack proto = upkeepItem(1);
                    int per = Math.max(1, proto.getMaxStackSize());
                    while (amount > 0) {
                        int n = Math.min(per, amount);
                        addDrop.invoke(drops, upkeepItem(n));
                        amount -= n;
                    }
                } catch (Exception ex) {
                    throw new org.bukkit.event.EventException(ex);
                }
            }, plugin);
            stackHooked = true;
        } catch (Throwable t) {
            debug("chunkloader: FactionsKore mob stacking event not available (" + t + ") - plain deaths only");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent e) {
        LivingEntity dead = e.getEntity();
        if (!dead.getType().name().equals(dropEntity)) return;
        UUID id = dead.getUniqueId();
        Location at = dead.getLocation();
        boolean byPlayer = dead.getKiller() != null;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (stackHandled.remove(id) != null) return;
            if (dropPlayerOnly && !byPlayer) return;
            int amount = upkeepFor(1);
            if (amount > 0 && at.getWorld() != null) {
                at.getWorld().dropItemNaturally(at, upkeepItem(amount));
            }
        });
    }

    private Material material(String name, Material fallback) {
        Material mat = name == null ? null : Material.matchMaterial(name);
        return mat == null ? fallback : mat;
    }

    private List<Component> lore(List<String> lines, String... pairs) {
        List<Component> out = new ArrayList<>();
        for (String line : lines) out.add(component(fill(line, pairs)));
        return out;
    }

    private int size() {
        return radius * 2 + 1;
    }

    public ItemStack loaderItem(long remainingMs) {
        MainConfig.ChunkLoaderItem cfg = c().getItems().getLoader();
        ItemStack it = new ItemStack(material(cfg.getType(), Material.END_CRYSTAL));
        ItemMeta meta = it.getItemMeta();
        String[] p = {"{time}", remainingMs > 0 ? format(remainingMs) : "none", "{size}", String.valueOf(size()), "{radius}", String.valueOf(radius)};
        meta.displayName(component(fill(cfg.getName(), p)));
        meta.lore(lore(cfg.getLore(), p));
        if (cfg.isGlow()) meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(keyLoader, PersistentDataType.STRING, "1");
        if (remainingMs > 0) meta.getPersistentDataContainer().set(keyTime, PersistentDataType.LONG, remainingMs);
        it.setItemMeta(meta);
        return it;
    }

    public ItemStack upkeepItem(int amount) {
        MainConfig.ChunkLoaderItem cfg = c().getItems().getShards();
        ItemStack it = new ItemStack(material(cfg.getType(), Material.AMETHYST_SHARD), Math.max(1, amount));
        ItemMeta meta = it.getItemMeta();
        String[] p = {"{minutes}", String.valueOf(msPerItem / 60_000L), "{length}", Durations.format(msPerItem / 1000L)};
        meta.displayName(component(fill(cfg.getName(), p)));
        meta.lore(lore(cfg.getLore(), p));
        if (cfg.isGlow()) meta.setEnchantmentGlintOverride(true);
        int model = cfg.getCustomModelData();
        if (model > 0) {
            try {
                ItemMeta.class.getMethod("setCustomModelData", Integer.class).invoke(meta, model);
            } catch (Throwable t) {
                debug("chunkloader: custom-model-data not supported here: " + t);
            }
        }
        meta.getPersistentDataContainer().set(keyUpkeep, PersistentDataType.STRING, "1");
        it.setItemMeta(meta);
        return it;
    }

    public boolean isLoaderItem(ItemStack it) {
        return it != null && it.getType() != Material.AIR && it.hasItemMeta()
                && it.getItemMeta().getPersistentDataContainer().has(keyLoader, PersistentDataType.STRING);
    }

    public boolean isUpkeepItem(ItemStack it) {
        return it != null && it.getType() != Material.AIR && it.hasItemMeta()
                && it.getItemMeta().getPersistentDataContainer().has(keyUpkeep, PersistentDataType.STRING);
    }

    private long storedTime(ItemStack it) {
        Long t = it.getItemMeta().getPersistentDataContainer().get(keyTime, PersistentDataType.LONG);
        return t == null ? 0 : t;
    }

    public static String formatMinutes(long ms) {
        long m = (Math.max(0, ms) + 59_999L) / 60_000L;
        return m >= 60 ? (m / 60) + "h " + (m % 60) + "m" : m + "m";
    }

    public static String format(long ms) {
        long s = Math.max(0, ms) / 1000;
        long h = s / 3600, mi = (s % 3600) / 60, se = s % 60;
        if (h > 0) return h + "h " + mi + "m";
        if (mi > 0) return mi + "m " + se + "s";
        return se + "s";
    }

    private void give(Player to, ItemStack item) {
        for (ItemStack left : to.getInventory().addItem(item).values()) to.getWorld().dropItemNaturally(to.getLocation(), left);
    }

    private Loader byCrystal(UUID id) {
        for (Loader l : loaders.values()) {
            if (id.equals(l.crystal)) return l;
        }
        return null;
    }

    private int countFor(String faction) {
        int n = 0;
        for (Loader l : loaders.values()) {
            if (l.faction.equals(faction)) n++;
        }
        return n;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND) return;
        ItemStack item = e.getItem();
        if (!isLoaderItem(item)) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        Block clicked = e.getClickedBlock();
        if (clicked == null) return;
        if (!Permission.CHUNKLOADER.has(p, true)) {
            return;
        }
        String fid = factionId(p);
        if (fid == null && requireFaction) {
            msg(p, TL.GENERIC_MEMBERONLY);
            return;
        }
        String faction = fid == null ? "none" : fid;
        World w = clicked.getWorld();
        if (!allowedWorlds.isEmpty() && !allowedWorlds.contains(w.getName())) {
            msg(p, TL.CHUNKLOADER_WRONGWORLD);
            return;
        }
        Block spot = clicked.getRelative(e.getBlockFace());
        if (!spot.isEmpty() || !spot.getRelative(BlockFace.UP).isEmpty()) {
            msg(p, TL.CHUNKLOADER_NOSPACE);
            return;
        }
        Location at = spot.getLocation().add(0.5, 0, 0.5);
        if (requireBuild && !FactionsBlockListener.playerCanBuildDestroyBlock(p, at, PermissibleAction.BUILD, true)) {
            msg(p, TL.CHUNKLOADER_NOTALLOWED);
            return;
        }
        if (countFor(faction) >= maxPerFaction) {
            msg(p, TL.CHUNKLOADER_LIMIT, maxPerFaction);
            return;
        }
        String id = UUID.randomUUID().toString().substring(0, 8);
        EnderCrystal crystal = spawnCrystal(w, at, id);
        Loader l = new Loader(id, w.getName(), spot.getX(), spot.getY(), spot.getZ(), faction, p.getUniqueId(), p.getName(), storedTime(item), crystal.getUniqueId());
        loaders.put(id, l);
        if (p.getGameMode() != org.bukkit.GameMode.CREATIVE) item.setAmount(item.getAmount() - 1);
        msg(p, TL.CHUNKLOADER_PLACED);
        if (l.remainingMs > 0) activate(l, false);
        updateName(l);
        save();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onUse(PlayerInteractEntityEvent e) {
        if (e instanceof PlayerInteractAtEntityEvent) return;
        if (e.getHand() != EquipmentSlot.HAND || !(e.getRightClicked() instanceof EnderCrystal)) return;
        Loader l = byCrystal(e.getRightClicked().getUniqueId());
        if (l == null) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        if (!canManage(p, l)) {
            msg(p, TL.CHUNKLOADER_NOTYOURS, tag(l.faction));
            return;
        }
        if (p.isSneaking() && pickupSneak) {
            pickUp(p, l);
            return;
        }
        open(p, l);
    }

    private boolean canManage(Player p, Loader l) {
        if (Permission.CHUNKLOADER_ADMIN.has(p)) return true;
        if (!Permission.CHUNKLOADER.has(p)) return false;
        String fid = factionId(p);
        return fid != null ? fid.equals(l.faction) : l.faction.equals("none") && p.getUniqueId().equals(l.owner);
    }

    private void pickUp(Player p, Loader l) {
        ItemStack item = loaderItem(l.remainingMs);
        remove(l, false);
        give(p, item);
        msg(p, TL.CHUNKLOADER_PICKEDUP);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onHit(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof EnderCrystal)) return;
        Loader l = byCrystal(e.getEntity().getUniqueId());
        if (l == null) return;
        e.setCancelled(true);
        if (!(e instanceof EntityDamageByEntityEvent) || !(((EntityDamageByEntityEvent) e).getDamager() instanceof Player)) return;
        Player p = (Player) ((EntityDamageByEntityEvent) e).getDamager();
        if (canManage(p, l)) {
            if (pickupSneak) msg(p, TL.CHUNKLOADER_SNEAKHINT);
            return;
        }
        if (!enemiesBreak) return;
        ItemStack drop = dropOnBreak ? loaderItem(l.remainingMs) : null;
        Location at = e.getEntity().getLocation();
        notifyFaction(l, TL.CHUNKLOADER_DESTROYED);
        remove(l, false);
        if (drop != null && at.getWorld() != null) at.getWorld().dropItemNaturally(at, drop);
        msg(p, TL.CHUNKLOADER_ENEMYDESTROYED);
    }

    private void remove(Loader l, boolean keepCrystal) {
        World w = plugin.getServer().getWorld(l.world);
        if (!keepCrystal && w != null && l.crystal != null) {
            Entity e = w.getEntity(l.crystal);
            if (e != null) e.remove();
        }
        forget(l);
    }

    private void forget(Loader l) {
        deactivate(l);
        loaders.remove(l.id);
        for (View v : new ArrayList<>(views.values())) {
            if (v.loaderId.equals(l.id) && v.inv != null) v.inv.getViewers().forEach(h -> h.closeInventory());
        }
        save();
        saveSpawners();
    }

    private void notifyFaction(Loader l, TL translation) {
        Faction faction = Factions.getInstance().getFactionById(l.faction);
        if (faction != null) faction.msg(translation, l.x, l.y, l.z);
    }

    private void open(Player p, Loader l) {
        View v = new View(l.id);
        v.inv = Bukkit.createInventory(v, 9, color(c().getGui().getTitle()));
        fill(v, l);
        views.put(p.getUniqueId(), v);
        p.openInventory(v.inv);
    }

    private ItemStack button(MainConfig.ChunkLoaderGuiSlot slot, Material fallback, String... pairs) {
        ItemStack it = new ItemStack(material(slot.getMaterial(), fallback));
        ItemMeta meta = it.getItemMeta();
        meta.displayName(component(fill(slot.getName(), pairs)));
        meta.lore(lore(slot.getLore(), pairs));
        it.setItemMeta(meta);
        return it;
    }

    private void fill(View v, Loader l) {
        String time = l.remainingMs > 0 ? format(l.remainingMs) : "0s";
        String status = l.remainingMs > 0 ? text(TL.CHUNKLOADER_STATUSON, time) : text(TL.CHUNKLOADER_STATUSOFF);
        String[] p = {"{time}", time, "{status}", status, "{size}", String.valueOf(size()), "{minutes}", String.valueOf(msPerItem / 60_000L),
                "{max}", format(maxMs), "{x}", String.valueOf(l.x), "{y}", String.valueOf(l.y), "{z}", String.valueOf(l.z)};
        String filler = c().getGui().getFiller();
        Material fm = filler == null || filler.isBlank() ? null : Material.matchMaterial(filler);
        for (int i = 0; i < 9; i++) {
            if (fm == null) {
                v.inv.setItem(i, null);
                continue;
            }
            ItemStack pane = new ItemStack(fm);
            ItemMeta meta = pane.getItemMeta();
            meta.setDisplayName(" ");
            pane.setItemMeta(meta);
            v.inv.setItem(i, pane);
        }
        v.inv.setItem(SLOT_UPKEEP, button(c().getGui().getShards(), Material.HOPPER, p));
        v.inv.setItem(SLOT_INFO, button(c().getGui().getInfo(), Material.BOOK, p));
        v.inv.setItem(SLOT_TIME, l.remainingMs > 0 ? button(c().getGui().getTime(), Material.CLOCK, p) : button(c().getGui().getTimeEmpty(), Material.BARRIER, p));
    }

    private void refreshViews() {
        for (View v : new ArrayList<>(views.values())) {
            Loader l = loaders.get(v.loaderId);
            if (l == null || v.inv == null) continue;
            fill(v, l);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof View)) return;
        View v = (View) e.getView().getTopInventory().getHolder();
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();
        if (e.getRawSlot() != SLOT_UPKEEP) return;
        Loader l = loaders.get(v.loaderId);
        if (l == null) {
            p.closeInventory();
            return;
        }
        ItemStack cursor = e.getCursor();
        if (isUpkeepItem(cursor)) {
            int used = addUpkeep(l, e.isRightClick() ? 1 : cursor.getAmount());
            if (used < 0) {
                msg(p, TL.CHUNKLOADER_SHARDSFULL);
                return;
            }
            cursor.setAmount(cursor.getAmount() - used);
            p.setItemOnCursor(cursor.getAmount() <= 0 ? null : cursor);
            added(p, l, used);
            return;
        }
        int wanted = e.isShiftClick() ? Integer.MAX_VALUE : 1;
        int taken = 0;
        ItemStack[] contents = p.getInventory().getStorageContents();
        for (int i = 0; i < contents.length && taken < wanted; i++) {
            ItemStack it = contents[i];
            if (!isUpkeepItem(it)) continue;
            int want = (int) Math.min(it.getAmount(), (long) wanted - taken);
            int used = addUpkeep(l, want);
            if (used < 0) {
                if (taken == 0) msg(p, TL.CHUNKLOADER_SHARDSFULL);
                return;
            }
            it.setAmount(it.getAmount() - used);
            p.getInventory().setItem(i, it.getAmount() <= 0 ? null : it);
            taken += used;
            if (used < want) break;
        }
        if (taken == 0) {
            msg(p, TL.CHUNKLOADER_SHARDSNONE);
            return;
        }
        added(p, l, taken);
    }

    private void added(Player p, Loader l, int amount) {
        msg(p, TL.CHUNKLOADER_SHARDSADDED, amount, format(l.remainingMs));
        p.updateInventory();
        View v = views.get(p.getUniqueId());
        if (v != null) fill(v, l);
    }

    int addUpkeep(Loader l, int count) {
        long room = maxMs - l.remainingMs;
        long fit = room / msPerItem;
        if (fit <= 0) return -1;
        int use = (int) Math.min(count, fit);
        l.remainingMs += use * msPerItem;
        dirty = true;
        if (!l.active) activate(l, true);
        updateName(l);
        return use;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof View) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof View) views.remove(e.getPlayer().getUniqueId());
    }

    public void give(Player target, boolean upkeep, int amount) {
        if (upkeep) {
            int left = amount;
            while (left > 0) {
                int n = Math.min(64, left);
                give(target, upkeepItem(n));
                left -= n;
            }
        } else {
            for (int i = 0; i < Math.min(amount, 64); i++) give(target, loaderItem(0));
        }
    }

    public void list(FPlayer fPlayer) {
        String fid = fPlayer.getFactionId();
        List<Loader> mine = new ArrayList<>();
        for (Loader l : loaders.values()) {
            if (l.faction.equals(fid)) mine.add(l);
        }
        if (mine.isEmpty()) {
            fPlayer.msg(TL.COMMAND_CHUNKLOADER_LISTNONE);
            return;
        }
        fPlayer.msg(TL.COMMAND_CHUNKLOADER_LISTHEADER, fPlayer.getFaction().getTag());
        for (Loader l : mine) {
            String status = l.remainingMs > 0 ? text(TL.CHUNKLOADER_STATUSON, format(l.remainingMs)) : text(TL.CHUNKLOADER_STATUSOFF);
            fPlayer.msg(TL.COMMAND_CHUNKLOADER_LISTLINE, l.x, l.y, l.z, l.world, status);
        }
    }

    private File dataFile() {
        return new File(plugin.getDataFolder(), "data/chunkloaders.json");
    }

    private File spawnerFile() {
        return new File(plugin.getDataFolder(), "data/chunkloader-spawners.json");
    }

    public void save() {
        DiscUtil.writeCatch(dataFile(), plugin.getGson().toJson(new ArrayList<>(loaders.values())), true);
        dirty = false;
    }

    void saveSpawners() {
        DiscUtil.writeCatch(spawnerFile(), plugin.getGson().toJson(touched), true);
    }

    private void load() {
        loaders.clear();
        touched.clear();
        if (dataFile().isFile() || spawnerFile().isFile()) {
            try {
                String json = dataFile().isFile() ? DiscUtil.readCatch(dataFile()) : null;
                List<Loader> list = json == null || json.isBlank() ? null : plugin.getGson().fromJson(json, new TypeToken<List<Loader>>() {
                }.getType());
                if (list != null) {
                    for (Loader l : list) {
                        if (l == null || l.id == null || l.world == null || l.faction == null) continue;
                        l.shownName = "";
                        loaders.put(l.id, l);
                    }
                }
                String spawners = spawnerFile().isFile() ? DiscUtil.readCatch(spawnerFile()) : null;
                Map<String, Integer> saved = spawners == null || spawners.isBlank() ? null : plugin.getGson().fromJson(spawners, new TypeToken<Map<String, Integer>>() {
                }.getType());
                if (saved != null) touched.putAll(saved);
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("chunkloader: could not read saved chunk loaders: " + ex.getMessage());
            }
            return;
        }
        File ignited = new File(plugin.getDataFolder().getParentFile(), "IgnitedCore");
        for (File folder : new File[]{plugin.getDataFolder(), ignited}) {
            File old = new File(folder, "chunkloaders.txt");
            if (!old.isFile()) continue;
            File oldSpawners = new File(folder, "chunkloader-spawners.txt");
            importLegacy(old, oldSpawners);
            save();
            saveSpawners();
            plugin.getLogger().info("chunkloader: imported " + loaders.size() + " chunk loader(s) from " + old.getPath());
            if (folder.equals(plugin.getDataFolder())) {
                old.delete();
                oldSpawners.delete();
            }
            return;
        }
    }

    private void importLegacy(File loaderFile, File spawners) {
        try {
            for (String line : Files.readAllLines(loaderFile.toPath(), StandardCharsets.UTF_8)) {
                String[] f = line.split("\t");
                if (f.length < 10) continue;
                try {
                    UUID crystal = f[9].equals("null") ? null : UUID.fromString(f[9]);
                    loaders.put(f[0], new Loader(f[0], f[1], Integer.parseInt(f[2]), Integer.parseInt(f[3]), Integer.parseInt(f[4]), f[5],
                            UUID.fromString(f[6]), f[7], Long.parseLong(f[8]), crystal));
                } catch (IllegalArgumentException bad) {
                    plugin.getLogger().warning("chunkloader: skipped a broken line in " + loaderFile.getName());
                }
            }
            if (spawners.isFile()) {
                for (String line : Files.readAllLines(spawners.toPath(), StandardCharsets.UTF_8)) {
                    String[] f = line.split("\t");
                    if (f.length < 5) continue;
                    try {
                        touched.put(f[0] + ";" + f[1] + ";" + f[2] + ";" + f[3], Integer.parseInt(f[4]));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        } catch (IOException ex) {
            plugin.getLogger().warning("chunkloader: could not import " + loaderFile.getPath() + ": " + ex.getMessage());
        }
    }
}
