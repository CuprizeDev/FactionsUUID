package com.massivecraft.factions.gui;

import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.Faction;
import com.massivecraft.factions.Factions;
import com.massivecraft.factions.FactionsPlugin;
import com.massivecraft.factions.perms.PermissibleAction;
import com.massivecraft.factions.perms.Role;
import com.massivecraft.factions.util.TL;
import com.massivecraft.factions.util.TextUtil;
import com.massivecraft.factions.util.material.MaterialDb;
import mkremins.fanciful.FancyMessage;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class RecruitmentListGUI extends GUI<Integer> {

    private static final int[] LISTING_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };

    private static final int[] BORDER_SLOTS = {
            0, 1, 2, 3, 4, 5, 6, 7, 8,
            9, 17, 18, 26, 27, 35, 36, 44,
            45, 46, 47, 48, 49, 50, 51, 52, 53
    };

    private static final int SLOT_PREV = 48;
    private static final int SLOT_CLOSE = 49;
    private static final int SLOT_NEXT = 50;
    private static final int SLOT_MANAGE = 53;

    private final int page;
    private final Map<Integer, Faction> factionBySlot = new HashMap<>();

    public RecruitmentListGUI(FPlayer user, int page) {
        super(user, 6);
        this.page = Math.max(1, page);
    }

    public static boolean canManageRecruitment(FPlayer fPlayer) {
        if (fPlayer == null || !fPlayer.hasFaction()) {
            return false;
        }
        if (fPlayer.isAdminBypassing() || fPlayer.getRole().isAtLeast(Role.COLEADER)) {
            return true;
        }
        return fPlayer.getFaction().hasAccess(fPlayer, PermissibleAction.INVITE);
    }

    @Override
    protected String getName() {
        return TextUtil.parseColor(FactionsPlugin.getInstance().conf().discord().recruitment().getBoardTitle());
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
            RecruitmentListGUI prevGui = new RecruitmentListGUI(user, page - 1);
            prevGui.build();
            prevGui.open();
            return;
        }
        if (slot == SLOT_NEXT) {
            RecruitmentListGUI nextGui = new RecruitmentListGUI(user, page + 1);
            nextGui.build();
            nextGui.open();
            return;
        }
        if (slot == SLOT_MANAGE && canManageRecruitment(user)) {
            RecruitmentManageGUI manageGui = new RecruitmentManageGUI(user, this);
            manageGui.build();
            manageGui.open();
            return;
        }

        Faction target = factionBySlot.get(slot);
        if (target != null) {
            user.getPlayer().closeInventory();
            String discordLink = target.getDiscord();
            if (discordLink != null && !discordLink.isEmpty()) {
                String formatted = TL.COMMAND_RECRUITMENT_CLICK_DISCORD.format(target.describeTo(user, true), discordLink);
                String tooltip = TL.COMMAND_DISCORD_CLICK_TOOLTIP.format(target.getTag());
                new FancyMessage(TextUtil.parseColor(formatted))
                        .link(discordLink)
                        .tooltip(TextUtil.parseColor(tooltip))
                        .send(user.getPlayer());
            } else {
                user.msg(TL.COMMAND_DISCORD_NOTSET_OTHER, target.describeTo(user, true));
            }
        }
    }

    @Override
    protected Map<Integer, Integer> createSlotMap() {
        factionBySlot.clear();
        Map<Integer, Integer> map = new HashMap<>();

        List<Faction> recruitingFactions = Factions.getInstance().getAllFactions().stream()
                .filter(f -> f.isNormal() && f.isRecruiting())
                .sorted(Comparator.comparingInt((Faction f) -> f.getFPlayers().size()).reversed())
                .collect(Collectors.toList());

        int itemsPerPage = LISTING_SLOTS.length;
        int startIndex = (page - 1) * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, recruitingFactions.size());

        for (int i = startIndex; i < endIndex; i++) {
            int slot = LISTING_SLOTS[i - startIndex];
            Faction faction = recruitingFactions.get(i);
            factionBySlot.put(slot, faction);
            map.put(slot, slot);
        }

        if (page > 1) {
            map.put(SLOT_PREV, SLOT_PREV);
        }
        map.put(SLOT_CLOSE, SLOT_CLOSE);
        if (endIndex < recruitingFactions.size()) {
            map.put(SLOT_NEXT, SLOT_NEXT);
        }
        if (canManageRecruitment(user)) {
            map.put(SLOT_MANAGE, SLOT_MANAGE);
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
        if (slot == SLOT_MANAGE) {
            Faction ownFaction = user.getFaction();
            Material book = MaterialDb.get("BOOK");
            if (book == null || book == Material.AIR) {
                book = Material.BOOK;
            }
            String status = ownFaction.isRecruiting() ? "&aENABLED" : "&cDISABLED";
            return SimpleItem.builder()
                    .setMaterial(book)
                    .setName("&b&lManage Recruitment")
                    .setLore(Arrays.asList(
                            "&7Status: " + status,
                            "&7Type: &e" + ownFaction.getRecruitmentPlaystyle(),
                            "&7Looking For: &e" + ownFaction.getRecruitmentLookingFor(),
                            "",
                            "&eClick to configure your listing!"
                    ))
                    .build();
        }

        Faction faction = factionBySlot.get(slot);
        if (faction != null) {
            Material beacon = MaterialDb.get("BEACON");
            if (beacon == null || beacon == Material.AIR) {
                beacon = Material.BEACON;
            }
            FPlayer admin = faction.getFPlayerAdmin();
            String leaderName = admin != null ? admin.getName() : "None";
            int memberLimit = FactionsPlugin.getInstance().conf().factions().other().getFactionMemberLimit();
            String maxMembers = memberLimit > 0 ? String.valueOf(memberLimit) : "∞";
            String discordLink = (faction.getDiscord() != null && !faction.getDiscord().isEmpty()) ? faction.getDiscord() : "None";

            return SimpleItem.builder()
                    .setMaterial(beacon)
                    .setName("&a" + faction.getTag())
                    .setLore(Arrays.asList(
                            "&7Leader: &e" + leaderName,
                            "&7Members: &e" + faction.getFPlayers().size() + "/" + maxMembers,
                            "&7Power: &e" + faction.getPowerRounded() + "/" + faction.getPowerMaxRounded(),
                            "",
                            "&7Type: &b" + faction.getRecruitmentPlaystyle(),
                            "&7Looking For: &b" + faction.getRecruitmentLookingFor(),
                            "",
                            "&eDiscord: &n" + discordLink,
                            "",
                            "&eClick to join their discord!"
                    ))
                    .build();
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
