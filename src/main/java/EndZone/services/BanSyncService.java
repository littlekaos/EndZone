package EndZone.services;

import EndZone.config.BotConfig;
import EndZone.models.ModAction;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class BanSyncService {
    private static final Logger logger = LoggerFactory.getLogger(BanSyncService.class);
    private final DataService dataService;
    private static final String MOD_LOG_CHANNEL_ID = BotConfig.MOD_LOG_CHANNEL_ID;
    private static final String EZ_PERM_BAN_LIST_CHANNEL_ID = BotConfig.EZ_PERM_BAN_LIST_CHANNEL_ID;
    private static final String COURT_GUILD_ID = BotConfig.COURT_GUILD_ID;

    public BanSyncService(DataService dataService) {
        this.dataService = dataService;
    }

    public void syncBans(JDA jda) {
        logger.info("[BanSync] Starting automatic ban synchronization...");
        TextChannel modLogChannel = jda.getTextChannelById(MOD_LOG_CHANNEL_ID);
        
        if (modLogChannel != null) {
            logger.info("[BanSync] Scanning moderation-logs (500 messages)...");
            retrievePastMessages(modLogChannel, null, 0);
        } else {
            logger.warn("[BanSync] Moderation log channel NOT FOUND (ID: {}).", MOD_LOG_CHANNEL_ID);
        }

        syncPermBanList(jda);
    }

    private void syncPermBanList(JDA jda) {
        logger.info("[BanSync] Scanning ez-perm-ban-list...");
        net.dv8tion.jda.api.entities.Guild courtGuild = jda.getGuildById(COURT_GUILD_ID);
        if (courtGuild == null) {
            logger.warn("[BanSync] CourtZone guild not found (ID: {}).", COURT_GUILD_ID);
            return;
        }

        TextChannel permBanChannel = courtGuild.getTextChannelById(EZ_PERM_BAN_LIST_CHANNEL_ID);
        if (permBanChannel == null) {
            logger.warn("[BanSync] ez-perm-ban-list channel not found (ID: {}).", EZ_PERM_BAN_LIST_CHANNEL_ID);
            return;
        }

        permBanChannel.getHistory().retrievePast(100).queue(messages -> {
            int newlySynced = 0;
            for (Message message : messages) {
                String content = message.getContentRaw();
                // Find all 17-20 digit IDs in the message
                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\d{17,20}").matcher(content);
                while (matcher.find()) {
                    String targetId = matcher.group();
                    
                    String foundReason = "No reason provided.";
                    
                    // Default to message author
                    String foundMod = message.getAuthor().getName();
                    String foundModId = message.getAuthor().getId();

                    // Parse multiline format
                    String[] lines = content.split("\n");
                    boolean foundIdLine = false;
                    for (String line : lines) {
                        if (line.contains("Discord ID:") && line.contains(targetId)) {
                            foundIdLine = true;
                            continue;
                        }
                        if (foundIdLine && !line.trim().isEmpty()) {
                            foundReason = line.trim();
                            break;
                        }
                    }

                    // Fallback to single line format
                    if (foundReason.equals("No reason provided.") && content.contains(" - ")) {
                        String[] parts = content.split(" - ");
                        if (parts.length >= 2) {
                            foundReason = parts[0].trim();
                            String modPart = parts[1].trim();
                            if (modPart.contains(" (")) {
                                String parsedMod = modPart.substring(0, modPart.indexOf(" (")).trim();
                                String idPart = modPart.substring(modPart.indexOf(" (") + 2);
                                if (idPart.contains(")")) {
                                    foundMod = parsedMod;
                                    foundModId = idPart.substring(0, idPart.indexOf(")")).trim();
                                }
                            } else {
                                foundMod = modPart;
                            }
                        }
                    } else if (foundReason.equals("No reason provided.")) {
                        foundReason = content;
                    }

                    long timestamp = message.getTimeCreated().toInstant().toEpochMilli();
                    if (!dataService.hasSimilarModAction(ModAction.ActionType.BAN, targetId, foundReason, courtGuild.getName(), permBanChannel.getName())) {
                        dataService.saveModAction(ModAction.ActionType.BAN, foundModId, foundMod, 
                                targetId, "User " + targetId, foundReason, courtGuild.getName(), permBanChannel.getName(), 0, 0, timestamp);
                        newlySynced++;
                    }
                }
            }
            if (newlySynced > 0) {
                logger.info("[BanSync] Synced {} new bans from ez-perm-ban-list.", newlySynced);
            }
        });
    }

    private void retrievePastMessages(TextChannel channel, String lastMessageId, int depth) {
        if (depth >= 5) {
            logger.info("[BanSync] Finished 500-message deep sync.");
            return;
        }

        if (lastMessageId == null) {
            channel.getHistory().retrievePast(100).queue(messages -> {
                if (messages.isEmpty()) {
                    logger.info("[BanSync] No more messages found at depth {}.", depth);
                    return;
                }
                processMessages(channel, messages);
                if (messages.size() == 100) {
                    retrievePastMessages(channel, messages.get(99).getId(), depth + 1);
                }
            }, error -> logger.error("[BanSync] Failed to retrieve moderation logs at depth {}: {}", depth, error.getMessage()));
        } else {
            channel.getHistoryBefore(lastMessageId, 100).queue(historyObj -> {
                List<Message> messages = historyObj.getRetrievedHistory();
                if (messages.isEmpty()) {
                    logger.info("[BanSync] No more messages found at depth {}.", depth);
                    return;
                }
                processMessages(channel, messages);
                if (messages.size() == 100) {
                    retrievePastMessages(channel, messages.get(99).getId(), depth + 1);
                }
            }, error -> logger.error("[BanSync] Failed to retrieve moderation logs at depth {}: {}", depth, error.getMessage()));
        }
    }

    private void processMessages(TextChannel channel, List<Message> messages) {
        int newlySynced = 0;
        int skipped = 0;
        int totalEmbeds = 0;

        for (Message message : messages) {
            for (MessageEmbed embed : message.getEmbeds()) {
                totalEmbeds++;
                if (embed.getTitle() != null && embed.getTitle().contains("User Banned")) {
                    String targetId = null;
                    String targetName = "Unknown";
                    String reason = "No reason provided in log.";
                    String moderatorName = "EZ Management";
                    String moderatorId = "0";
                    long timestamp = message.getTimeCreated().toInstant().toEpochMilli();

                    for (MessageEmbed.Field field : embed.getFields()) {
                        if (field.getName() == null) continue;
                        
                        switch (field.getName()) {
                            case "User ID":
                                targetId = field.getValue();
                                break;
                            case "Reason":
                                reason = field.getValue();
                                break;
                            case "Moderator":
                                moderatorName = field.getValue();
                                if (moderatorName.contains("(ID:")) {
                                    try {
                                        int startIdx = moderatorName.indexOf("(ID:") + 4;
                                        int endIdx = moderatorName.indexOf(")", startIdx);
                                        if (endIdx != -1) {
                                            moderatorId = moderatorName.substring(startIdx, endIdx).trim();
                                            moderatorName = moderatorName.substring(0, moderatorName.indexOf("(ID:")).trim();
                                        }
                                    } catch (Exception ignored) {}
                                }
                                break;
                        }
                    }

                    if (targetId != null) {
                        // Use hasSimilarModAction to check if we already have this user + reason + source
                        // This prevents duplicates where the moderator might be different (EZ Management vs Real)
                        if (!dataService.hasSimilarModAction(ModAction.ActionType.BAN, targetId, reason, channel.getGuild().getName(), channel.getName())) {
                            logger.info("[BanSync] Syncing new ban for user: {} (Reason: {}, Mod: {})", targetId, reason, moderatorName);
                            dataService.saveModAction(ModAction.ActionType.BAN, moderatorId, moderatorName, 
                                    targetId, targetName, reason, channel.getGuild().getName(), channel.getName(), 0, 0, timestamp);
                            newlySynced++;
                        } else {
                            skipped++;
                        }
                    }
                }
            }
        }
        logger.info("[BanSync] Batch scan finished. Found {} ban embeds. Synced: {}, Skipped (already in DB): {}", totalEmbeds, newlySynced, skipped);
    }
}
