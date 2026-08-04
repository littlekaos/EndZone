package EndZone.commands;

import EndZone.EndZone;
import EndZone.config.BotConfig;
import EndZone.models.ModAction;
import EndZone.services.DataService;
import EndZone.services.ServiceManager;
import EndZone.utils.EmbedUtils;
import EndZone.utils.PermissionUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.audit.AuditLogEntry;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class ReasonCommand implements Command {
    private static final Logger logger = LoggerFactory.getLogger(ReasonCommand.class);
    private final EndZone bot;
    private final DataService dataService;
    private static final String EZ_PERM_BAN_LIST_CHANNEL_ID = BotConfig.EZ_PERM_BAN_LIST_CHANNEL_ID;
    private static final String EZ_UNBAN_LIST_CHANNEL_ID = BotConfig.EZ_UNBAN_LIST_CHANNEL_ID;
    private static final String COURT_GUILD_ID = BotConfig.COURT_GUILD_ID;
    private static final String MAIN_GUILD_ID = BotConfig.GUILD_ID;

    public ReasonCommand(EndZone bot) {
        this.bot = bot;
        this.dataService = ServiceManager.getDataService();
    }

    @Override
    public List<CommandData> getCommandDataList() {
        return List.of(Commands.slash("reason", "Check the ban/unban history for a user")
                .addOption(OptionType.STRING, "user", "The user ID to check history for", true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String userId = event.getOption("user").getAsString();
        logger.info("[ReasonScan] /reason command called for user: {}", userId);
        
        if (!PermissionUtils.isAlphaBetaOrHigher(event.getMember(), ServiceManager.getConfig())) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("You don't have permission to use this command.")).setEphemeral(true).queue();
            return;
        }

        event.deferReply(true).queue();
        startScan(event, userId);
    }

    private void startScan(SlashCommandInteractionEvent event, String userId) {
        Guild courtGuild = event.getJDA().getGuildById(COURT_GUILD_ID);
        Guild mainGuild = event.getJDA().getGuildById(MAIN_GUILD_ID);
        
        if (courtGuild == null) {
            logger.error("[ReasonScan] CourtZone guild not found!");
            event.getHook().sendMessage("Error: CourtZone guild not found. Please contact an administrator.").queue();
            return;
        }

        List<ModAction> scanResults = new ArrayList<>();
        AtomicInteger pendingScans = new AtomicInteger(5); // 2 channels + 2 audit logs + 1 category scan

        // --- CourtZone Scans ---

        // Scan Ban List (CourtZone)
        TextChannel banChannel = courtGuild.getTextChannelById(EZ_PERM_BAN_LIST_CHANNEL_ID);
        if (banChannel != null) {
            scanChannel(banChannel, userId, ModAction.ActionType.BAN, scanResults, () -> {
                if (pendingScans.decrementAndGet() == 0) finishScan(event, userId, scanResults);
            });
        } else {
            logger.warn("[ReasonScan] Ban list channel not found!");
            if (pendingScans.decrementAndGet() == 0) finishScan(event, userId, scanResults);
        }

        // Scan Unban List (CourtZone)
        TextChannel unbanChannel = courtGuild.getTextChannelById(EZ_UNBAN_LIST_CHANNEL_ID);
        if (unbanChannel != null) {
            scanChannel(unbanChannel, userId, ModAction.ActionType.UNBAN, scanResults, () -> {
                if (pendingScans.decrementAndGet() == 0) finishScan(event, userId, scanResults);
            });
        } else {
            logger.warn("[ReasonScan] Unban list channel not found!");
            if (pendingScans.decrementAndGet() == 0) finishScan(event, userId, scanResults);
        }

        // Scan Audit Logs (CourtZone)
        scanAuditLogs(courtGuild, userId, scanResults, () -> {
            if (pendingScans.decrementAndGet() == 0) finishScan(event, userId, scanResults);
        });

        // --- EndZone Community Scans ---

        if (mainGuild != null) {
            // Scan Audit Logs (EndZone Community)
            scanAuditLogs(mainGuild, userId, scanResults, () -> {
                if (pendingScans.decrementAndGet() == 0) finishScan(event, userId, scanResults);
            });

            // Scan Sentinel Logs Category (EndZone Community)
            scanCategoryLogs(mainGuild, "Sentinel Logs", userId, scanResults, () -> {
                if (pendingScans.decrementAndGet() == 0) finishScan(event, userId, scanResults);
            });
        } else {
            logger.warn("[ReasonScan] Main guild not found for scans!");
            pendingScans.addAndGet(-2); // Skip the 2 scans for main guild
            if (pendingScans.get() <= 0) finishScan(event, userId, scanResults);
        }
    }

    private void scanCategoryLogs(Guild guild, String categoryKeyword, String userId, List<ModAction> results, Runnable onComplete) {
        List<Category> categories = guild.getCategories().stream()
                .filter(c -> c.getName().toLowerCase().contains(categoryKeyword.toLowerCase()))
                .toList();

        if (categories.isEmpty()) {
            logger.warn("[ReasonScan] Category with keyword '{}' not found in guild: {}", categoryKeyword, guild.getName());
            onComplete.run();
            return;
        }

        List<TextChannel> allChannels = new ArrayList<>();
        for (Category category : categories) {
            allChannels.addAll(category.getTextChannels());
        }

        if (allChannels.isEmpty()) {
            onComplete.run();
            return;
        }

        AtomicInteger pendingChannels = new AtomicInteger(allChannels.size());
        for (TextChannel channel : allChannels) {
            // Increased depth for Sentinel Logs channels to 500 messages
            scanChannelRecursive(channel, null, userId, ModAction.ActionType.BAN, results, 500, () -> {
                if (pendingChannels.decrementAndGet() == 0) onComplete.run();
            });
        }
    }

    private void scanAuditLogs(Guild guild, String userId, List<ModAction> results, Runnable onComplete) {
        scanAuditLogsRecursive(guild, null, userId, results, 500, onComplete);
    }

    private void scanAuditLogsRecursive(Guild guild, AuditLogEntry lastEntry, String userId, List<ModAction> results, int remaining, Runnable onComplete) {
        if (remaining <= 0) {
            onComplete.run();
            return;
        }

        int limit = Math.min(remaining, 100);
        var action = guild.retrieveAuditLogs().type(ActionType.BAN).limit(limit);
        
        // AuditLogPaginationAction in JDA usually allows skipTo or simple iteration. 
        // We'll use the skipTo/iteration pattern or simply use the last ID.
        if (lastEntry != null) {
            action.skipTo(lastEntry.getIdLong());
        }

        action.queue(logs -> {
            if (logs.isEmpty()) {
                onComplete.run();
                return;
            }

            for (AuditLogEntry entry : logs) {
                if (entry.getTargetId() != null && entry.getTargetId().equals(userId)) {
                    String reason = entry.getReason() != null ? entry.getReason() : "Breaking server rules";
                    String modName = entry.getUser() != null ? entry.getUser().getName() : "EZ Management";
                    String modId = entry.getUser() != null ? entry.getUser().getId() : "0";
                    long timestamp = entry.getTimeCreated().toInstant().toEpochMilli();
                    
                    synchronized (results) {
                        boolean exists = results.stream().anyMatch(a -> 
                            Math.abs(a.getTimestamp() - timestamp) < 5000);
                        
                        if (!exists) {
                            results.add(new ModAction(0, ModAction.ActionType.BAN, modId, modName, userId, "User", 
                                    reason, guild.getName(), "Audit Log", timestamp, 0, 0));
                        }
                    }
                }
            }

            if (logs.size() < limit) {
                onComplete.run();
            } else {
                scanAuditLogsRecursive(guild, logs.get(logs.size() - 1), userId, results, remaining - logs.size(), onComplete);
            }
        }, error -> {
            logger.error("[ReasonScan] Error scanning audit logs for guild {}: {}", guild.getName(), error.getMessage());
            onComplete.run();
        });
    }

    private void scanChannel(TextChannel channel, String userId, ModAction.ActionType type, List<ModAction> results, Runnable onComplete) {
        channel.getHistory().retrievePast(100).queue(messages -> {
            processMessages(messages, userId, type, channel, results);
            onComplete.run();
        }, error -> {
            logger.error("[ReasonScan] Error scanning channel {}: {}", channel.getName(), error.getMessage());
            onComplete.run();
        });
    }

    private void scanChannelRecursive(TextChannel channel, Message before, String userId, ModAction.ActionType type, List<ModAction> results, int remaining, Runnable onComplete) {
        if (remaining <= 0) {
            onComplete.run();
            return;
        }

        int limit = Math.min(remaining, 100);
        if (before == null) {
            channel.getHistory().retrievePast(limit).queue(messages -> {
                processMessages(messages, userId, type, channel, results);
                if (messages.size() < limit) {
                    onComplete.run();
                } else {
                    scanChannelRecursive(channel, messages.get(messages.size() - 1), userId, type, results, remaining - messages.size(), onComplete);
                }
            }, e -> onComplete.run());
        } else {
            channel.getHistoryBefore(before, limit).queue(history -> {
                List<Message> messages = history.getRetrievedHistory();
                processMessages(messages, userId, type, channel, results);
                if (messages.size() < limit) {
                    onComplete.run();
                } else {
                    scanChannelRecursive(channel, messages.get(messages.size() - 1), userId, type, results, remaining - messages.size(), onComplete);
                }
            }, e -> onComplete.run());
        }
    }

    private void processMessages(List<Message> messages, String userId, ModAction.ActionType type, TextChannel channel, List<ModAction> results) {
        String mentionFormat = "<@" + userId + ">";
        String nicknameMentionFormat = "<@!" + userId + ">";
        
        for (Message message : messages) {
            if (messageMatches(message, userId, mentionFormat, nicknameMentionFormat)) {
                ModAction action = parseMessage(message, userId, type, channel);
                if (action != null) {
                    synchronized (results) {
                        // Deduplicate by timestamp (within 5 seconds) to remove duplicates from multiple log channels
                        boolean exists = results.stream().anyMatch(a -> 
                            Math.abs(a.getTimestamp() - action.getTimestamp()) < 5000);
                        
                        if (!exists) {
                            results.add(action);
                        }
                    }
                }
            }
        }
    }

    private boolean messageMatches(Message message, String userId, String mentionFormat, String nicknameMentionFormat) {
        String content = message.getContentRaw();
        if (content.contains(userId) || content.contains(mentionFormat) || content.contains(nicknameMentionFormat)) {
            return true;
        }
        
        for (MessageEmbed embed : message.getEmbeds()) {
            if (embedContains(embed, userId) || embedContains(embed, mentionFormat) || embedContains(embed, nicknameMentionFormat)) {
                return true;
            }
        }
        return false;
    }

    private boolean embedContains(MessageEmbed embed, String text) {
        if (embed == null) return false;
        if (embed.getDescription() != null && embed.getDescription().contains(text)) return true;
        if (embed.getTitle() != null && embed.getTitle().contains(text)) return true;
        if (embed.getFooter() != null && embed.getFooter().getText() != null && embed.getFooter().getText().contains(text)) return true;
        if (embed.getAuthor() != null && embed.getAuthor().getName() != null && embed.getAuthor().getName().contains(text)) return true;
        for (MessageEmbed.Field field : embed.getFields()) {
            if (field.getName() != null && field.getName().contains(text)) return true;
            if (field.getValue() != null && field.getValue().contains(text)) return true;
        }
        return false;
    }

    private ModAction parseMessage(Message message, String userId, ModAction.ActionType type, TextChannel channel) {
        String content = message.getContentRaw();
        boolean isCourtZoneBanList = channel.getId().equals(EZ_PERM_BAN_LIST_CHANNEL_ID) && channel.getGuild().getId().equals(COURT_GUILD_ID);
        
        String foundReason = type == ModAction.ActionType.UNBAN ? "appealed" : (isCourtZoneBanList ? "No reason provided." : "Breaking server rules");
        String foundMod = message.getAuthor().getName();
        String foundModId = message.getAuthor().getId();
        long timestamp = message.getTimeCreated().toInstant().toEpochMilli();

        // If it's the bot and not CourtZone, we might want to default to EZ Management if no other mod is found
        if (!isCourtZoneBanList && foundModId.equals(message.getJDA().getSelfUser().getId())) {
             foundMod = "EZ Management";
             foundModId = "0";
        }

        // Check embeds for Sentinel style logs
        for (MessageEmbed embed : message.getEmbeds()) {
            if (embedContains(embed, userId)) {
                if (embed.getDescription() != null && embed.getDescription().toLowerCase().contains("reason")) {
                    String desc = embed.getDescription();
                    String lowerDesc = desc.toLowerCase();
                    int reasonIndex = lowerDesc.indexOf("reason:");
                    if (reasonIndex != -1) {
                        foundReason = desc.substring(reasonIndex + 7).split("\n")[0].trim();
                    }
                }
                
                // Try to find moderator in embed fields
                for (MessageEmbed.Field field : embed.getFields()) {
                    if (field.getName() != null && (field.getName().toLowerCase().contains("moderator") || field.getName().toLowerCase().contains("staff"))) {
                        String modVal = field.getValue();
                        // Extract ID if present like "Name (ID)" or "<@ID>"
                        if (modVal != null) {
                            if (modVal.contains("(") && modVal.contains(")")) {
                                int openParen = modVal.lastIndexOf("(");
                                int closeParen = modVal.lastIndexOf(")");
                                foundModId = modVal.substring(openParen + 1, closeParen).trim();
                                foundMod = modVal.substring(0, openParen).trim();
                            } else if (modVal.contains("<@") && modVal.contains(">")) {
                                foundModId = modVal.replaceAll("[^0-9]", "");
                                foundMod = modVal; // Use the mention as the name if we can't parse further
                            } else {
                                foundMod = modVal;
                                foundModId = modVal;
                            }
                        }
                        break;
                    }
                }
            }
        }

        if (type == ModAction.ActionType.BAN && content != null && !content.isEmpty()) {
            // Try parsing multiline format
            String[] lines = content.split("\n");
            boolean foundIdLine = false;
            for (String line : lines) {
                if (line.contains("Discord ID:") && line.contains(userId)) {
                    foundIdLine = true;
                    continue;
                }
                if (foundIdLine && !line.trim().isEmpty()) {
                    foundReason = line.trim();
                    break;
                }
            }

            // Fallback for single line format: reason - moderator (id)
            if ((foundReason.equals("Breaking server rules") || foundReason.equals("No reason provided.")) && content.contains(" - ")) {
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
            }
        }

        return new ModAction(0, type, foundModId, foundMod, userId, "User", foundReason, 
                channel.getGuild().getName(), channel.getName(), timestamp, 0, 0);
    }

    private void finishScan(SlashCommandInteractionEvent event, String userId, List<ModAction> results) {
        // Sort by timestamp descending
        results.sort((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));

        if (results.isEmpty()) {
            EmbedBuilder historyEmbed = new EmbedBuilder()
                    .setTitle("📋 Ban/Unban History")
                    .setDescription("**User:** <@" + userId + "> (`" + userId + "`)\n\n" +
                            "🔨 **BAN**\n" +
                            "└─ **Moderator:** EZ Management (ID: `0`)\n" +
                            "└─ **Reason:** Breaking server rules\n" +
                            "└─ **Source:** EndZone Community / Audit Log\n" +
                            "└─ **Date:** " + DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm:ss").withZone(ZoneId.systemDefault()).format(Instant.now()) + "\n")
                    .setColor(new Color(100, 150, 255))
                    .setTimestamp(Instant.now());

            event.getHook().sendMessageEmbeds(historyEmbed.build()).queue();
            return;
        }

        StringBuilder historyText = new StringBuilder();
        for (ModAction action : results) {
            String actionEmoji = action.getActionType() == ModAction.ActionType.BAN ? "🔨" : "🔓";
            String modName = action.getModeratorName().replace("_", "\\_").replace("*", "\\*");
            
            historyText.append(actionEmoji).append(" **").append(action.getActionType().name()).append("**\n");
            historyText.append("└─ **Moderator:** ").append(modName)
                    .append(" (ID: `").append(action.getModeratorId()).append("`)\n");
            
            String displayReason = action.getReason().replace("_", "\\_").replace("*", "\\*");
            historyText.append("└─ **Reason:** ").append(displayReason).append("\n");
            historyText.append("└─ **Source:** ").append(action.getGuildName()).append(" / ").append(action.getChannelName()).append("\n");
            historyText.append("└─ **Date:** ").append(action.getFormattedDate()).append("\n\n");
        }

        EmbedBuilder historyEmbed = new EmbedBuilder()
                .setTitle("📋 Ban/Unban History")
                .setDescription("**User:** <@" + userId + "> (`" + userId + "`)\n\n" + historyText + "\n")
                .setColor(new Color(100, 150, 255))
                .setTimestamp(Instant.now());

        event.getHook().sendMessageEmbeds(historyEmbed.build()).queue();
    }
}
