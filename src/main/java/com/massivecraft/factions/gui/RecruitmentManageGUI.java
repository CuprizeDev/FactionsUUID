package com.massivecraft.factions.gui;

import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.Faction;
import com.massivecraft.factions.FactionsPlugin;
import com.massivecraft.factions.integration.DiscordSRVIntegration;
import com.massivecraft.factions.util.TL;
import com.massivecraft.factions.util.TextUtil;
import com.massivecraft.factions.util.material.MaterialDb;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RecruitmentManageGUI extends GUI<Integer> implements GUI.Backable {

    private static final int SLOT_STATUS = 10;
    private static final int SLOT_PLAYSTYLE = 13;
    private static final int SLOT_LOOKING_FOR = 16;
    private static final int SLOT_BACK_OR_CLOSE = 22;

    private final GUI<?> parent;

    public RecruitmentManageGUI(FPlayer user, GUI<?> parent) {
        super(user, 3);
        this.parent = parent;
        this.back = SLOT_BACK_OR_CLOSE;
    }

    @Override
    protected String getName() {
        String rawTitle = FactionsPlugin.getInstance().conf().discord().recruitment().getManageTitle();
        String tag = user.hasFaction() ? ChatColor.stripColor(user.getFaction().getTag()) : "Faction";
        return TextUtil.parseColor(rawTitle.replace("{FACTION}", tag).replace("{TEAM}", tag));
    }

    @Override
    protected String parse(String toParse, Integer slot) {
        return toParse;
    }

    @Override
    protected void onClick(Integer slot, ClickType clickType) {
        if (!user.hasFaction()) {
            user.getPlayer().closeInventory();
            return;
        }
        Faction faction = user.getFaction();

        if (slot == SLOT_STATUS) {
            boolean nextState = !faction.isRecruiting();
            if (nextState && (faction.getDiscord() == null || faction.getDiscord().isEmpty())) {
                user.getPlayer().closeInventory();
                user.msg(TL.COMMAND_RECRUITMENT_NODISCORD);
                return;
            }
            faction.setRecruiting(nextState);
            if (nextState) {
                user.msg(TL.COMMAND_RECRUITMENT_STATUS_ENABLED);
                DiscordSRVIntegration.sendRecruitmentEmbed(faction);
            } else {
                user.msg(TL.COMMAND_RECRUITMENT_STATUS_DISABLED);
            }
            build();
            open();
        } else if (slot == SLOT_PLAYSTYLE) {
            List<String> options = FactionsPlugin.getInstance().conf().discord().recruitment().getPlaystyles();
            int currentIdx = options.indexOf(faction.getRecruitmentPlaystyle());
            String nextOption = options.get((currentIdx + 1) % options.size());
            faction.setRecruitmentPlaystyle(nextOption);
            build();
            open();
        } else if (slot == SLOT_LOOKING_FOR) {
            List<String> options = FactionsPlugin.getInstance().conf().discord().recruitment().getLookingForOptions();
            int currentIdx = options.indexOf(faction.getRecruitmentLookingFor());
            String nextOption = options.get((currentIdx + 1) % options.size());
            faction.setRecruitmentLookingFor(nextOption);
            build();
            open();
        }
    }

    @Override
    protected Map<Integer, Integer> createSlotMap() {
        Map<Integer, Integer> map = new HashMap<>();
        map.put(SLOT_STATUS, SLOT_STATUS);
        map.put(SLOT_PLAYSTYLE, SLOT_PLAYSTYLE);
        map.put(SLOT_LOOKING_FOR, SLOT_LOOKING_FOR);
        return map;
    }

    @Override
    protected SimpleItem getItem(Integer slot) {
        Faction faction = user.getFaction();
        if (slot == SLOT_STATUS) {
            boolean recruiting = faction.isRecruiting();
            Material mat = recruiting ? MaterialDb.get("EMERALD_BLOCK") : MaterialDb.get("REDSTONE_BLOCK");
            if (mat == null || mat == Material.AIR) {
                mat = Material.EMERALD_BLOCK;
            }
            String statusText = recruiting ? "&aENABLED" : "&cDISABLED";
            return SimpleItem.builder()
                    .setMaterial(mat)
                    .setName("&bRecruitment Status")
                    .setLore(Arrays.asList(
                            "&7Currently: " + statusText,
                            "",
                            "&eClick to toggle!"
                    ))
                    .build();
        } else if (slot == SLOT_PLAYSTYLE) {
            Material mat = MaterialDb.get("DIAMOND_SWORD");
            if (mat == null || mat == Material.AIR) {
                mat = Material.DIAMOND_SWORD;
            }
            return SimpleItem.builder()
                    .setMaterial(mat)
                    .setName("&bFaction Type")
                    .setLore(Arrays.asList(
                            "&7What kind of faction are you?",
                            "&7Currently: &e" + faction.getRecruitmentPlaystyle(),
                            "",
                            "&eClick to cycle options!"
                    ))
                    .build();
        } else if (slot == SLOT_LOOKING_FOR) {
            Material mat = MaterialDb.get("PLAYER_HEAD");
            if (mat == null || mat == Material.AIR) {
                mat = MaterialDb.get("SKULL_ITEM");
            }
            if (mat == null || mat == Material.AIR) {
                mat = Material.BOOK;
            }
            return SimpleItem.builder()
                    .setMaterial(mat)
                    .setName("&bLooking For")
                    .setLore(Arrays.asList(
                            "&7What players do you need?",
                            "&7Currently: &e" + faction.getRecruitmentLookingFor(),
                            "",
                            "&eClick to cycle options!"
                    ))
                    .build();
        }
        return FactionsPlugin.getInstance().conf().upgrades().filler().getItem();
    }

    @Override
    protected Map<Integer, SimpleItem> createDummyItems() {
        Map<Integer, SimpleItem> dummies = new HashMap<>();
        SimpleItem filler = FactionsPlugin.getInstance().conf().upgrades().filler().getItem();
        for (int i = 0; i < size; i++) {
            dummies.put(i, filler);
        }

        if (parent != null) {
            Material arrow = MaterialDb.get("ARROW");
            if (arrow == null || arrow == Material.AIR) {
                arrow = Material.ARROW;
            }
            dummies.put(SLOT_BACK_OR_CLOSE, SimpleItem.builder()
                    .setMaterial(arrow)
                    .setName("&cBack")
                    .setLore(Collections.singletonList("&7Return to Recruitment Board"))
                    .build());
        } else {
            Material barrier = MaterialDb.get("BARRIER");
            if (barrier == null || barrier == Material.AIR) {
                barrier = Material.BARRIER;
            }
            dummies.put(SLOT_BACK_OR_CLOSE, SimpleItem.builder()
                    .setMaterial(barrier)
                    .setName("&cClose")
                    .setLore(Collections.emptyList())
                    .build());
        }
        return dummies;
    }

    @Override
    public void onBack() {
        if (parent != null) {
            parent.build();
            parent.open();
        } else {
            user.getPlayer().closeInventory();
        }
    }
}
