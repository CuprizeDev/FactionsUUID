package com.massivecraft.factions.gui;

import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.Faction;
import com.massivecraft.factions.FactionsPlugin;
import com.massivecraft.factions.listeners.FKoreRaidListener;
import com.massivecraft.factions.raids.RaidManager;
import com.massivecraft.factions.raids.RaidRecord;
import com.massivecraft.factions.util.TextUtil;
import com.massivecraft.factions.util.material.MaterialDb;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RaidHistoryGUI extends GUI<Integer> {

    private static final int[] RAID_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private static final int[] BORDER_SLOTS = {
            0, 1, 2, 3, 4, 5, 6, 7, 8,
            9, 17, 18, 26, 27, 35, 36, 44,
            45, 46, 47, 48, 49, 50, 51, 52, 53
    };

    private static final int SLOT_RESET_FILTER = 45;
    private static final int SLOT_PREV = 48;
    private static final int SLOT_CLOSE = 49;
    private static final int SLOT_NEXT = 50;
    private static final int SLOT_EMPTY = 22;

    private final int page;
    private final Faction filterFaction;
    private final Map<Integer, RaidRecord> raidBySlot = new HashMap<>();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy HH:mm");

    public RaidHistoryGUI(FPlayer user, int page, Faction filterFaction) {
        super(user, 6);
        this.page = Math.max(1, page);
        this.filterFaction = filterFaction;
    }

    @Override
    protected String getName() {
        if (filterFaction != null) {
            String title = FactionsPlugin.getInstance().conf().commands().raids().getGuiTitleFaction();
            return TextUtil.parseColor(title.replace("{FACTION}", filterFaction.getTag()));
        }
        return TextUtil.parseColor(FactionsPlugin.getInstance().conf().commands().raids().getGuiTitle());
    }

    @Override
    protected String parse(String toParse, Integer slot) {
        return toParse;
    }

    @Override
    protected void onClick(Integer slot, ClickType clickType) {
        if (slot == SLOT_CLOSE) {
            user.getPlayer().closeInventory();
            return;
        }
        if (slot == SLOT_PREV) {
            RaidHistoryGUI prevGui = new RaidHistoryGUI(user, page - 1, filterFaction);
            prevGui.build();
            prevGui.open();
            return;
        }
        if (slot == SLOT_NEXT) {
            RaidHistoryGUI nextGui = new RaidHistoryGUI(user, page + 1, filterFaction);
            nextGui.build();
            nextGui.open();
            return;
        }
        if (slot == SLOT_RESET_FILTER && filterFaction != null) {
            RaidHistoryGUI allGui = new RaidHistoryGUI(user, 1, null);
            allGui.build();
            allGui.open();
        }
    }

    @Override
    protected Map<Integer, Integer> createSlotMap() {
        raidBySlot.clear();
        Map<Integer, Integer> map = new HashMap<>();

        List<RaidRecord> list = filterFaction == null
                ? RaidManager.getInstance().getAllRaids()
                : RaidManager.getInstance().getRaidsForFaction(filterFaction);

        if (list.isEmpty()) {
            map.put(SLOT_EMPTY, SLOT_EMPTY);
        } else {
            int itemsPerPage = RAID_SLOTS.length;
            int startIndex = (page - 1) * itemsPerPage;
            int endIndex = Math.min(startIndex + itemsPerPage, list.size());

            for (int i = startIndex; i < endIndex; i++) {
                int slot = RAID_SLOTS[i - startIndex];
                RaidRecord record = list.get(i);
                raidBySlot.put(slot, record);
                map.put(slot, slot);
            }

            if (page > 1) {
                map.put(SLOT_PREV, SLOT_PREV);
            }
            if (endIndex < list.size()) {
                map.put(SLOT_NEXT, SLOT_NEXT);
            }
        }

        map.put(SLOT_CLOSE, SLOT_CLOSE);

        if (filterFaction != null) {
            map.put(SLOT_RESET_FILTER, SLOT_RESET_FILTER);
        }

        return map;
    }

    @Override
    protected SimpleItem getItem(Integer slot) {
        if (slot == SLOT_PREV) {
            Material arrow = MaterialDb.get("ARROW");
            if (arrow == null || arrow == Material.AIR) {
                arrow = Material.ARROW;
            }
            return SimpleItem.builder()
                    .setMaterial(arrow)
                    .setName("&b&lPREVIOUS PAGE")
                    .setLore(Collections.singletonList("&7Click to go to page " + (page - 1)))
                    .build();
        }

        if (slot == SLOT_CLOSE) {
            Material barrier = MaterialDb.get("BARRIER");
            if (barrier == null || barrier == Material.AIR) {
                barrier = Material.BARRIER;
            }
            return SimpleItem.builder()
                    .setMaterial(barrier)
                    .setName("&c&lCLOSE")
                    .setLore(Collections.emptyList())
                    .build();
        }

        if (slot == SLOT_NEXT) {
            Material arrow = MaterialDb.get("ARROW");
            if (arrow == null || arrow == Material.AIR) {
                arrow = Material.ARROW;
            }
            return SimpleItem.builder()
                    .setMaterial(arrow)
                    .setName("&b&lNEXT PAGE")
                    .setLore(Collections.singletonList("&7Click to go to page " + (page + 1)))
                    .build();
        }

        if (slot == SLOT_RESET_FILTER) {
            Material book = MaterialDb.get("BOOK");
            if (book == null || book == Material.AIR) {
                book = Material.BOOK;
            }
            return SimpleItem.builder()
                    .setMaterial(book)
                    .setName("&e&lVIEW ALL RAIDS")
                    .setLore(Arrays.asList(
                            "&7Filtered to: &b" + filterFaction.getTag(),
                            "",
                            "&eClick to clear filter and view all raids."
                    ))
                    .build();
        }

        if (slot == SLOT_EMPTY) {
            Material paper = MaterialDb.get("PAPER");
            if (paper == null || paper == Material.AIR) {
                paper = Material.PAPER;
            }
            String note = filterFaction == null
                    ? "&7No raids have been recorded yet."
                    : "&7No raids recorded for " + filterFaction.getTag() + ".";
            return SimpleItem.builder()
                    .setMaterial(paper)
                    .setName("&c&lNo Raids Found")
                    .setLore(Collections.singletonList(note))
                    .build();
        }

        RaidRecord raid = raidBySlot.get(slot);
        if (raid != null) {
            String attackerName = raid.getAttackerName();
            String defenderName = raid.getDefenderName();

            if (raid.isActive()) {
                FKoreRaidListener.updateActiveRaidStats(raid);

                Material diamondSword = MaterialDb.get("DIAMOND_SWORD");
                if (diamondSword == null || diamondSword == Material.AIR) {
                    diamondSword = Material.DIAMOND_SWORD;
                }

                List<String> lore = new ArrayList<>();
                lore.add("&7Status: &a&lACTIVE");
                lore.add("");
                lore.add("&7Defender: &c" + defenderName);
                lore.add("&7Attacker: &a" + attackerName);
                lore.add("");
                lore.add("&7Started: &f" + raid.getFormattedStartTime(dateFormat));
                lore.add("&7Duration: &e" + raid.getFormattedDuration());
                lore.add("");
                lore.add("&7Attacker Kills: &a" + raid.getAttackerKills());
                lore.add("&7Defender Kills: &c" + raid.getDefenderKills());
                lore.add("&7Blocks Placed (Def): &b" + raid.getDefenderBlocksPlaced());

                return SimpleItem.builder()
                        .setMaterial(diamondSword)
                        .setName("&eRaid: &a" + attackerName + " &evs &c" + defenderName)
                        .setLore(lore)
                        .setEnchant(true)
                        .build();
            } else {
                Material ironSword = MaterialDb.get("IRON_SWORD");
                if (ironSword == null || ironSword == Material.AIR) {
                    ironSword = Material.IRON_SWORD;
                }

                List<String> lore = new ArrayList<>();
                lore.add("&7Status: &c&lENDED");
                lore.add("");
                lore.add("&7Defender: &c" + defenderName);
                lore.add("&7Attacker: &a" + attackerName);
                lore.add("");
                lore.add("&7Started: &f" + raid.getFormattedStartTime(dateFormat));
                lore.add("&7Ended: &f" + raid.getFormattedEndTime(dateFormat));
                lore.add("&7Duration: &e" + raid.getFormattedDuration());
                lore.add("");
                lore.add("&7Attacker Kills: &a" + raid.getAttackerKills());
                lore.add("&7Defender Kills: &c" + raid.getDefenderKills());
                lore.add("&7Blocks Placed (Def): &b" + raid.getDefenderBlocksPlaced());

                return SimpleItem.builder()
                        .setMaterial(ironSword)
                        .setName("&eRaid: &a" + attackerName + " &evs &c" + defenderName)
                        .setLore(lore)
                        .setEnchant(false)
                        .build();
            }
        }

        return FactionsPlugin.getInstance().conf().upgrades().filler().getItem();
    }

    @Override
    protected Map<Integer, SimpleItem> createDummyItems() {
        Map<Integer, SimpleItem> dummies = new HashMap<>();
        SimpleItem filler = FactionsPlugin.getInstance().conf().upgrades().filler().getItem();
        for (int slot : BORDER_SLOTS) {
            dummies.put(slot, filler);
        }
        return dummies;
    }
}
