package EndZone.services;

import EndZone.config.BotConfig;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.utils.FileUpload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LoggingService {
    private static final Logger logger = LoggerFactory.getLogger(LoggingService.class);
    private final Map<String, TextChannel> logChannelCache = new ConcurrentHashMap<>();

    // Hardcoded IDs for specific channels as requested
    private static final String MOD_LOG_CHANNEL_ID = BotConfig.MOD_LOG_CHANNEL_ID;
    private static final String ENDZONE_LOG_CHANNEL_ID = BotConfig.ENDZONE_LOG_CHANNEL_ID;
    private static final String VOICE_LOG_CHANNEL_ID = BotConfig.VOICE_LOG_CHANNEL_ID;
    private static final String NAME_LOG_CHANNEL_ID = BotConfig.NAME_LOG_CHANNEL_ID;
    private static final String EVENT_NAME_LOG_ID = BotConfig.EVENT_NAME_LOG_ID;
    private static final String JOIN_LEAVE_LOG_CHANNEL_ID = BotConfig.JOIN_LEAVE_LOG_CHANNEL_ID;
    private static final String MESSAGE_LOG_CHANNEL_ID = BotConfig.MESSAGE_LOG_CHANNEL_ID;
    private static final String STAFF_STRIKE_LOG_ID = BotConfig.STAFF_STRIKE_LOG_CHANNEL_ID;

    public void initializeLogChannels(Guild guild) {
        if (isCourtZone(guild)) {
            TextChannel courtLogs = getLogChannel(guild, "endzone-logs");
            if (courtLogs == null) {
                logger.warn("CourtZone server logs channel {} was not found", BotConfig.COURT_BOT_LOG_CHANNEL_ID);
            } else {
                logger.info("CourtZone server logs -> #{} ({})", courtLogs.getName(), courtLogs.getId());
            }
            return;
        }
        getLogChannel(guild, "endzone-logs");
        getLogChannel(guild, "moderation-logs");
        getLogChannel(guild, "voice-logs");
        getLogChannel(guild, "name-logs");
        getLogChannel(guild, "join-leave-logs");
        getLogChannel(guild, "message-logs");
        getLogChannel(guild, "staff-strikes");
    }

    public TextChannel getLogChannel(Guild guild, String channelName) {
        // All CourtZone logs go to #bot-logs
        if (isCourtZone(guild)) {
            TextChannel courtLogs = findChannel(guild, BotConfig.COURT_BOT_LOG_CHANNEL_ID);
            if (courtLogs != null) {
                return courtLogs;
            }
            logger.warn("CourtZone log channel {} not found for {}", BotConfig.COURT_BOT_LOG_CHANNEL_ID, channelName);
        }

        String cacheKey = guild.getId() + ":" + channelName;
        if (logChannelCache.containsKey(cacheKey)) {
            TextChannel cachedChannel = logChannelCache.get(cacheKey);
            if (cachedChannel != null && guild.getTextChannelById(cachedChannel.getId()) != null) {
                return cachedChannel;
            }
        }

        logger.info("Looking for log channel: {} in guild: {}", channelName, guild.getName());

        // Try to find by ID first for specific channels
        TextChannel logChannel = null;
        switch (channelName) {
            case "endzone-logs":
            case "server-logs": 
                if (ENDZONE_LOG_CHANNEL_ID != null && !ENDZONE_LOG_CHANNEL_ID.isEmpty()) {
                    logChannel = guild.getTextChannelById(ENDZONE_LOG_CHANNEL_ID);
                }
                break;
            case "moderation-logs":
                if (MOD_LOG_CHANNEL_ID != null && !MOD_LOG_CHANNEL_ID.isEmpty()) {
                    logChannel = guild.getTextChannelById(MOD_LOG_CHANNEL_ID);
                }
                break;
            case "voice-logs":
                if (VOICE_LOG_CHANNEL_ID != null && !VOICE_LOG_CHANNEL_ID.isEmpty()) {
                    logChannel = guild.getTextChannelById(VOICE_LOG_CHANNEL_ID);
                }
                break;
            case "name-logs":
                if (NAME_LOG_CHANNEL_ID != null && !NAME_LOG_CHANNEL_ID.isEmpty()) {
                    logChannel = guild.getTextChannelById(NAME_LOG_CHANNEL_ID);
                }
                break;
            case "event-names":
                if (EVENT_NAME_LOG_ID != null && !EVENT_NAME_LOG_ID.isEmpty()) {
                    logChannel = guild.getTextChannelById(EVENT_NAME_LOG_ID);
                }
                break;
            case "join-leave-logs":
                if (JOIN_LEAVE_LOG_CHANNEL_ID != null && !JOIN_LEAVE_LOG_CHANNEL_ID.isEmpty()) {
                    logChannel = guild.getTextChannelById(JOIN_LEAVE_LOG_CHANNEL_ID);
                }
                break;
            case "message-logs":
                if (MESSAGE_LOG_CHANNEL_ID != null && !MESSAGE_LOG_CHANNEL_ID.isEmpty()) {
                    logChannel = guild.getTextChannelById(MESSAGE_LOG_CHANNEL_ID);
                }
                break;
            case "staff-strikes":
                if (STAFF_STRIKE_LOG_ID != null && !STAFF_STRIKE_LOG_ID.isEmpty()) {
                    logChannel = guild.getTextChannelById(STAFF_STRIKE_LOG_ID);
                }
                break;
        }

        // Fallback to finding by name if ID lookup failed or for other channel names
        if (logChannel == null) {
            logChannel = guild.getTextChannelsByName(channelName, true).stream().findFirst().orElse(null);
        }

        if (logChannel != null) {
            logger.info("Found existing log channel: {} (ID: {})", channelName, logChannel.getId());
            logChannelCache.put(cacheKey, logChannel);
            return logChannel;
        }

        if (isCourtZone(guild)) {
            logger.info("Log channel {} not found. Automatic creation is disabled for CourtZone.", channelName);
            return null;
        }

        // Do not create endzone-logs, moderation-logs or server-logs automatically
        if (channelName.equals("endzone-logs") || channelName.equals("moderation-logs") || channelName.equals("server-logs")) {
            logger.info("Log channel {} not found. Automatic creation is disabled for this channel.", channelName);
            return null;
        }

        logger.info("Log channel {} not found. Attempting to create...", channelName);

        // Check for MANAGE_CHANNELS permission
        if (!guild.getSelfMember().hasPermission(net.dv8tion.jda.api.Permission.MANAGE_CHANNEL)) {
            logger.error("Missing MANAGE_CHANNELS permission to create log channel: {}", channelName);
            return null;
        }

        // Create channel if it still doesn't exist
        guild.createTextChannel(channelName).queue(newChannel -> {
            String topic;
            switch (channelName) {
                case "staff-strikes":
                    topic = "This channel logs all staff strike actions.";
                    break;
                case "moderation-logs":
                    topic = "This channel logs all moderation actions (warn, mute, timeout, etc).";
                    break;
                case "server-logs":
                    topic = "This channel logs all server events.";
                    break;
                default:
                    topic = "Server logging channel.";
            }

            newChannel.getManager().setTopic(topic).queue(success -> {
                logChannelCache.put(cacheKey, newChannel);
                logger.info("Created and cached log channel: {} in guild: {}", channelName, guild.getName());
            });
        }, error -> logger.error("Failed to create log channel: {}: {}", channelName, error.getMessage()));

        return null;
    }

    public void logAction(Guild guild, String channelName, MessageEmbed embed) {
        TextChannel logChannel = getLogChannel(guild, channelName);
        if (logChannel != null) {
            logChannel.sendMessageEmbeds(embed).queue(
                success -> {},
                error -> System.err.println("❌ Failed to send simple log (" + channelName + "): " + error.getMessage())
            );
        } else {
            System.err.println("❌ Log channel NOT FOUND for: " + channelName);
            // If channel is being created, log the pending action
            logger.info("Pending log action for {} (channel not ready yet)", channelName);
        }
    }

    public void logActionWithFile(Guild guild, String channelName, MessageEmbed embed,
                                  InputStream fileData, String fileName) {
        TextChannel logChannel = getLogChannel(guild, channelName);
        if (logChannel != null) {
            FileUpload fileUpload = FileUpload.fromData(fileData, fileName);
            logChannel.sendMessageEmbeds(embed).queue(message -> {
                logChannel.sendFiles(fileUpload).queue();
            });
        }
    }

    private static boolean isCourtZone(Guild guild) {
        return guild != null && BotConfig.COURT_GUILD_ID.equals(guild.getId());
    }

    private TextChannel findChannel(Guild guild, String channelId) {
        if (guild == null || channelId == null || channelId.isEmpty()) {
            return null;
        }
        TextChannel channel = guild.getTextChannelById(channelId);
        if (channel != null) {
            return channel;
        }
        channel = guild.getJDA().getTextChannelById(channelId);
        if (channel != null && channel.getGuild().getId().equals(guild.getId())) {
            return channel;
        }
        return null;
    }
}
