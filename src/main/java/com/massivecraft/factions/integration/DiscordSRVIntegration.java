package com.massivecraft.factions.integration;

import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.Faction;
import com.massivecraft.factions.Factions;
import com.massivecraft.factions.FactionsPlugin;
import com.massivecraft.factions.config.file.MainConfig;
import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.dependencies.jda.api.EmbedBuilder;
import github.scarsz.discordsrv.dependencies.jda.api.entities.TextChannel;
import com.massivecraft.factions.raids.RaidRecord;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.awt.Color;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class DiscordSRVIntegration {

    private static boolean enabled = false;
    private static BukkitTask recruitmentTask = null;
    private static final SimpleDateFormat raidDateFormat = new SimpleDateFormat("MMM dd, yyyy HH:mm");

    public static boolean init(FactionsPlugin plugin) {
        if (!Bukkit.getPluginManager().isPluginEnabled("DiscordSRV")) {
            enabled = false;
            return false;
        }
        enabled = true;
        plugin.getLogger().info("Successfully hooked into DiscordSRV for faction recruitment!");
        startTask(plugin);
        return true;
    }

    public static void startTask(FactionsPlugin plugin) {
        shutdown();
        if (!enabled) {
            return;
        }
        MainConfig.Discord.Recruitment conf = plugin.conf().discord().recruitment();
        if (!conf.isEnabled()) {
            return;
        }
        long intervalMinutes = conf.getUpdateIntervalMinutes();
        if (intervalMinutes <= 0) {
            return;
        }
        long ticks = intervalMinutes * 60L * 20L;
        recruitmentTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (Faction faction : Factions.getInstance().getAllFactions()) {
                    if (faction.isNormal() && faction.isRecruiting() && faction.getDiscord() != null && !faction.getDiscord().isEmpty()) {
                        sendRecruitmentEmbed(faction);
                    }
                }
            }
        }.runTaskTimerAsynchronously(plugin, ticks, ticks);
    }

    public static void shutdown() {
        if (recruitmentTask != null) {
            recruitmentTask.cancel();
            recruitmentTask = null;
        }
    }

    public static void sendRecruitmentEmbed(Faction faction) {
        if (!enabled || !Bukkit.getPluginManager().isPluginEnabled("DiscordSRV")) {
            return;
        }
        if (faction == null || !faction.isNormal() || !faction.isRecruiting()) {
            return;
        }
        String discordLink = faction.getDiscord();
        if (discordLink == null || discordLink.isEmpty()) {
            return;
        }

        MainConfig.Discord.Recruitment conf = FactionsPlugin.getInstance().conf().discord().recruitment();
        if (!conf.isEnabled()) {
            return;
        }

        String channelName = conf.getChannelName();
        String factionTag = ChatColor.stripColor(faction.getTag());
        FPlayer admin = faction.getFPlayerAdmin();
        String leaderName = admin != null ? admin.getName() : "None";
        String members = String.valueOf(faction.getFPlayers().size());
        int memberLimit = FactionsPlugin.getInstance().conf().factions().other().getFactionMemberLimit();
        String maxMembers = memberLimit > 0 ? String.valueOf(memberLimit) : "∞";
        String power = String.valueOf(faction.getPowerRounded());
        String maxPower = String.valueOf(faction.getPowerMaxRounded());
        String playstyle = faction.getRecruitmentPlaystyle();
        String lookingFor = faction.getRecruitmentLookingFor();

        String title = replacePlaceholders(conf.getEmbedTitle(), factionTag, leaderName, members, maxMembers, power, maxPower, playstyle, lookingFor, discordLink);
        List<String> descLines = conf.getEmbedDescription().stream()
                .map(line -> replacePlaceholders(line, factionTag, leaderName, members, maxMembers, power, maxPower, playstyle, lookingFor, discordLink))
                .collect(Collectors.toList());
        String description = String.join("\n", descLines);
        String colorHex = conf.getEmbedColor();

        sendEmbed(channelName, title, description, colorHex, "recruitment");
    }

    public static void sendStrikeGivenEmbed(Faction faction, String reason, String staff) {
        MainConfig.Discord.Strikes conf = FactionsPlugin.getInstance().conf().discord().strikes();
        int total = faction == null ? 0 : faction.getStrikes().size();
        sendStrikeEmbed(faction, reason, staff, total, total, conf.getGiveTitle(), conf.getGiveDescription(), conf.getGiveColor());
    }

    public static void sendStrikeRemovedEmbed(Faction faction, String reason, String staff, int strikeNumber) {
        MainConfig.Discord.Strikes conf = FactionsPlugin.getInstance().conf().discord().strikes();
        if (!conf.isAnnounceRemovals()) {
            return;
        }
        int total = faction == null ? 0 : faction.getStrikes().size();
        sendStrikeEmbed(faction, reason, staff, strikeNumber, total, conf.getTakeTitle(), conf.getTakeDescription(), conf.getTakeColor());
    }

    private static void sendStrikeEmbed(Faction faction, String reason, String staff, int strikeNumber, int totalStrikes, String rawTitle, List<String> rawDescription, String colorHex) {
        if (!enabled || !Bukkit.getPluginManager().isPluginEnabled("DiscordSRV")) {
            return;
        }
        if (faction == null) {
            return;
        }
        MainConfig.Discord.Strikes conf = FactionsPlugin.getInstance().conf().discord().strikes();
        if (!conf.isEnabled()) {
            return;
        }

        String factionTag = ChatColor.stripColor(faction.getTag());
        FPlayer admin = faction.getFPlayerAdmin();
        String leaderName = admin != null ? admin.getName() : "None";
        String safeReason = reason == null || reason.isEmpty() ? "No reason specified" : ChatColor.stripColor(reason);
        String safeStaff = staff == null || staff.isEmpty() ? "Console" : staff;

        String title = replaceStrikePlaceholders(rawTitle, factionTag, leaderName, safeReason, safeStaff, strikeNumber, totalStrikes);
        String description = rawDescription.stream()
                .map(line -> replaceStrikePlaceholders(line, factionTag, leaderName, safeReason, safeStaff, strikeNumber, totalStrikes))
                .collect(Collectors.joining("\n"));

        sendEmbed(conf.getChannelName(), title, description, colorHex, "strike");
    }

    private static String replaceStrikePlaceholders(String input, String faction, String leader, String reason, String staff, int strikeNumber, int totalStrikes) {
        if (input == null) {
            return "";
        }
        return input
                .replace("{FACTION}", faction)
                .replace("{LEADER}", leader)
                .replace("{REASON}", reason)
                .replace("{STAFF}", staff)
                .replace("{STRIKE_NUMBER}", String.valueOf(strikeNumber))
                .replace("{TOTAL_STRIKES}", String.valueOf(totalStrikes));
    }

    public static void sendRaidStartedAlert(RaidRecord record, Faction defender, Faction attacker) {
        if (!enabled || !Bukkit.getPluginManager().isPluginEnabled("DiscordSRV")) {
            return;
        }
        if (record == null) {
            return;
        }

        MainConfig.Discord.Raids conf = FactionsPlugin.getInstance().conf().discord().raids();
        if (!conf.isEnabled()) {
            return;
        }

        String defenderTag = defender != null ? defender.getTag() : record.getDefenderName();
        String attackerTag = attacker != null ? attacker.getTag() : record.getAttackerName();
        String world = record.getWorld() != null && !record.getWorld().isEmpty() ? record.getWorld() : "Unknown";
        String x = String.valueOf((int) record.getX());
        String y = String.valueOf((int) record.getY());
        String z = String.valueOf((int) record.getZ());
        String startTime = record.getFormattedStartTime(raidDateFormat);

        String channelName = conf.getChannelName();
        if (channelName != null && !channelName.isEmpty()) {
            String title = replaceRaidPlaceholders(conf.getStartTitle(), defenderTag, attackerTag, world, x, y, z, startTime, "", "", "0", "0", "0");
            String description = conf.getStartDescription().stream()
                    .map(line -> replaceRaidPlaceholders(line, defenderTag, attackerTag, world, x, y, z, startTime, "", "", "0", "0", "0"))
                    .collect(Collectors.joining("\n"));

            sendEmbed(channelName, title, description, conf.getStartColor(), "raid_start");
        }

        if (conf.isPlayerAlerts() && defender != null) {
            String pmTitle = replaceRaidPlaceholders(conf.getPlayerAlertTitle(), defenderTag, attackerTag, world, x, y, z, startTime, "", "", "0", "0", "0");
            String pmDescription = conf.getPlayerAlertDescription().stream()
                    .map(line -> replaceRaidPlaceholders(line, defenderTag, attackerTag, world, x, y, z, startTime, "", "", "0", "0", "0"))
                    .collect(Collectors.joining("\n"));

            sendPrivateEmbedsToFaction(defender, pmTitle, pmDescription, conf.getPlayerAlertColor());
        }
    }

    public static void sendRaidEndedAlert(RaidRecord record, Faction defender, Faction attacker) {
        if (!enabled || !Bukkit.getPluginManager().isPluginEnabled("DiscordSRV")) {
            return;
        }
        if (record == null) {
            return;
        }

        MainConfig.Discord.Raids conf = FactionsPlugin.getInstance().conf().discord().raids();
        if (!conf.isEnabled() || !conf.isAnnounceEnd()) {
            return;
        }

        String defenderTag = defender != null ? defender.getTag() : record.getDefenderName();
        String attackerTag = attacker != null ? attacker.getTag() : record.getAttackerName();
        String world = record.getWorld() != null && !record.getWorld().isEmpty() ? record.getWorld() : "Unknown";
        String x = String.valueOf((int) record.getX());
        String y = String.valueOf((int) record.getY());
        String z = String.valueOf((int) record.getZ());
        String startTime = record.getFormattedStartTime(raidDateFormat);
        String endTime = record.getFormattedEndTime(raidDateFormat);
        String duration = record.getFormattedDuration();
        String attackerKills = String.valueOf(record.getAttackerKills());
        String defenderKills = String.valueOf(record.getDefenderKills());
        String blocksPlaced = String.valueOf(record.getDefenderBlocksPlaced());

        String channelName = conf.getChannelName();
        if (channelName != null && !channelName.isEmpty()) {
            String title = replaceRaidPlaceholders(conf.getEndTitle(), defenderTag, attackerTag, world, x, y, z, startTime, endTime, duration, attackerKills, defenderKills, blocksPlaced);
            String description = conf.getEndDescription().stream()
                    .map(line -> replaceRaidPlaceholders(line, defenderTag, attackerTag, world, x, y, z, startTime, endTime, duration, attackerKills, defenderKills, blocksPlaced))
                    .collect(Collectors.joining("\n"));

            sendEmbed(channelName, title, description, conf.getEndColor(), "raid_end");
        }
    }

    public static void sendPrivateEmbedsToFaction(Faction faction, String title, String description, String colorHex) {
        if (!enabled || !Bukkit.getPluginManager().isPluginEnabled("DiscordSRV")) {
            return;
        }
        if (faction == null) {
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(FactionsPlugin.getInstance(), () -> {
            try {
                if (DiscordSRV.getPlugin() == null || DiscordSRV.getPlugin().getJda() == null || DiscordSRV.getPlugin().getAccountLinkManager() == null) {
                    return;
                }

                Color parsedColor;
                try {
                    parsedColor = Color.decode(colorHex);
                } catch (Exception e) {
                    parsedColor = Color.RED;
                }
                final Color color = parsedColor;

                for (FPlayer fPlayer : faction.getFPlayers()) {
                    if (fPlayer == null) {
                        continue;
                    }
                    UUID uuid = null;
                    try {
                        uuid = UUID.fromString(fPlayer.getId());
                    } catch (Exception ex) {
                        org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(fPlayer.getName());
                        if (op != null) {
                            uuid = op.getUniqueId();
                        }
                    }
                    if (uuid == null) {
                        continue;
                    }

                    String discordId = DiscordSRV.getPlugin().getAccountLinkManager().getDiscordId(uuid);
                    if (discordId == null || discordId.isEmpty()) {
                        continue;
                    }

                    DiscordSRV.getPlugin().getJda().retrieveUserById(discordId).queue(user -> {
                        if (user != null) {
                            user.openPrivateChannel().queue(channel -> {
                                EmbedBuilder embed = new EmbedBuilder();
                                embed.setTitle(title);
                                embed.setDescription(description);
                                embed.setColor(color);
                                embed.setTimestamp(Instant.now());
                                channel.sendMessageEmbeds(embed.build()).queue(null, t -> {});
                            }, t -> {});
                        }
                    }, t -> {});
                }
            } catch (Throwable t) {
                FactionsPlugin.getInstance().getLogger().log(Level.WARNING, "Failed to send DiscordSRV raid DMs", t);
            }
        });
    }

    private static String replaceRaidPlaceholders(String input, String defender, String attacker, String world, String x, String y, String z, String startTime, String endTime, String duration, String attackerKills, String defenderKills, String blocksPlaced) {
        if (input == null) {
            return "";
        }
        return input
                .replace("{DEFENDER}", defender != null ? defender : "Unknown")
                .replace("{ATTACKER}", attacker != null ? attacker : "Unknown")
                .replace("{RAIDER}", attacker != null ? attacker : "Unknown")
                .replace("{WORLD}", world != null ? world : "Unknown")
                .replace("{X}", x != null ? x : "0")
                .replace("{Y}", y != null ? y : "0")
                .replace("{Z}", z != null ? z : "0")
                .replace("{START_TIME}", startTime != null ? startTime : "N/A")
                .replace("{END_TIME}", endTime != null ? endTime : "N/A")
                .replace("{DURATION}", duration != null ? duration : "N/A")
                .replace("{ATTACKER_KILLS}", attackerKills != null ? attackerKills : "0")
                .replace("{DEFENDER_KILLS}", defenderKills != null ? defenderKills : "0")
                .replace("{BLOCKS_PLACED}", blocksPlaced != null ? blocksPlaced : "0")
                .replace("{DEFENDER_BLOCKS_PLACED}", blocksPlaced != null ? blocksPlaced : "0");
    }

    private static void sendEmbed(String channelName, String title, String description, String colorHex, String type) {
        Bukkit.getScheduler().runTaskAsynchronously(FactionsPlugin.getInstance(), () -> {
            try {
                if (DiscordSRV.getPlugin() == null || DiscordSRV.getPlugin().getJda() == null) {
                    return;
                }
                TextChannel textChannel = null;
                if (channelName != null && channelName.matches("\\d+")) {
                    textChannel = DiscordSRV.getPlugin().getJda().getTextChannelById(channelName);
                }
                if (textChannel == null && channelName != null) {
                    textChannel = DiscordSRV.getPlugin().getDestinationTextChannelForGameChannelName(channelName);
                }
                if (textChannel == null) {
                    return;
                }

                EmbedBuilder embed = new EmbedBuilder();
                embed.setTitle(title);
                embed.setDescription(description);
                try {
                    embed.setColor(Color.decode(colorHex));
                } catch (Exception e) {
                    embed.setColor(Color.CYAN);
                }
                embed.setTimestamp(Instant.now());

                textChannel.sendMessageEmbeds(embed.build()).queue();
            } catch (Throwable t) {
                FactionsPlugin.getInstance().getLogger().log(Level.WARNING, "Failed to send DiscordSRV " + type + " embed", t);
            }
        });
    }

    private static String replacePlaceholders(String input, String faction, String leader, String members, String maxMembers, String power, String maxPower, String type, String lookingFor, String discordLink) {
        if (input == null) {
            return "";
        }
        return input
                .replace("{FACTION}", faction)
                .replace("{TEAM}", faction)
                .replace("{LEADER}", leader)
                .replace("{MEMBERS}", members)
                .replace("{MAX_MEMBERS}", maxMembers)
                .replace("{POWER}", power)
                .replace("{MAX_POWER}", maxPower)
                .replace("{TYPE}", type)
                .replace("{LOOKING_FOR}", lookingFor)
                .replace("{DISCORD_LINK}", discordLink);
    }
}
