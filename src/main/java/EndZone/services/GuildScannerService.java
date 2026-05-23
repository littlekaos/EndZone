package EndZone.services;

import EndZone.config.BotConfig;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class GuildScannerService {
    private static final Logger logger = LoggerFactory.getLogger(GuildScannerService.class);
    private final JDA jda;
    private final BotConfig config;

    public GuildScannerService(JDA jda, BotConfig config) {
        this.jda = jda;
        this.config = config;
    }

    public void scanAndVerify() {
        String guildId = config.getGuildId();
        Guild guild = jda.getGuildById(guildId);

        if (guild == null) {
            logger.error("CRITICAL: Guild with ID {} not found! The bot might not be in the server or the ID is incorrect.", guildId);
            return;
        }

        logger.info("Starting scan for guild: {} ({})", guild.getName(), guild.getId());
        verifyFields(guild);
        logger.info("Guild scan and verification complete.");
    }

    private void verifyFields(Guild guild) {
        Field[] fields = BotConfig.class.getDeclaredFields();
        for (Field field : fields) {
            try {
                if (field.getName().endsWith("_ID") && field.getType() == String.class) {
                    field.setAccessible(true);
                    String id = (String) field.get(null);
                    verifyId(guild, field.getName(), id);
                }
            } catch (IllegalAccessException e) {
                logger.error("Failed to access field {}: {}", field.getName(), e.getMessage());
            }
        }
    }

    private void verifyId(Guild guild, String fieldName, String id) {
        if (id == null || id.isEmpty() || id.equals("0")) {
            logger.warn("Field {} has no ID configured. Trying to find by name...", fieldName);
            tryFallback(guild, fieldName);
            return;
        }

        if (fieldName.endsWith("_ROLE_ID")) {
            Role role = guild.getRoleById(id);
            if (role == null) {
                logger.warn("Field {}: Role with ID {} not found! Trying fallback...", fieldName, id);
                tryFallback(guild, fieldName);
            } else {
                logger.info("Field {}: Role '{}' found.", fieldName, role.getName());
            }
        } else if (fieldName.endsWith("_CHANNEL_ID")) {
            TextChannel channel = guild.getTextChannelById(id);
            if (channel == null) {
                logger.warn("Field {}: TextChannel with ID {} not found! Trying fallback...", fieldName, id);
                tryFallback(guild, fieldName);
            } else {
                logger.info("Field {}: Channel '#{}' found.", fieldName, channel.getName());
            }
        } else if (fieldName.endsWith("_MESSAGE_ID")) {
            TextChannel channel = findChannelForMessage(guild, fieldName);
            if (channel != null) {
                channel.retrieveMessageById(id).queue(
                    msg -> logger.info("Field {}: Message found in #{}", fieldName, channel.getName()),
                    error -> {
                        logger.warn("Field {}: Message ID {} not found in #{}. Attempting to discover recent bot messages...", fieldName, id, channel.getName());
                        discoverMessage(channel, fieldName);
                    }
                );
            } else {
                logger.warn("Field {}: Could not determine which channel to search for message ID {}.", fieldName, id);
            }
        }
    }

    private TextChannel findChannelForMessage(Guild guild, String fieldName) {
        if (fieldName.equals("STAFF_VERIFY_MESSAGE_ID")) {
            return guild.getTextChannelById(BotConfig.STAFF_VERIFY_CHANNEL_ID);
        } else if (fieldName.equals("BLACKLIST_MESSAGE_ID")) {
            return guild.getTextChannelById(BotConfig.BLACKLIST_CHANNEL_ID);
        }
        return null;
    }

    private void discoverMessage(TextChannel channel, String fieldName) {
        channel.getHistory().retrievePast(50).queue(messages -> {
            for (net.dv8tion.jda.api.entities.Message message : messages) {
                if (message.getAuthor().equals(jda.getSelfUser())) {
                    // Simple heuristic: TRIAL_SENTINELS usually has roles, BLACKLIST has "permanently demoted"
                    String content = message.getContentRaw();
                    if (fieldName.equals("STAFF_VERIFY_MESSAGE_ID") && content.toLowerCase().contains("react")) {
                         logger.info("Discovery Success: Found potential Trial Sentinels message (ID: {}). Content starts with: {}", message.getId(), content.substring(0, Math.min(20, content.length())));
                         return;
                    }
                    if (fieldName.equals("BLACKLIST_MESSAGE_ID") && content.toLowerCase().contains("demoted")) {
                         logger.info("Discovery Success: Found potential Blacklist message (ID: {}).", message.getId());
                         return;
                    }
                }
            }
            logger.error("Discovery Failed: Could not find a suitable replacement for {} in #{}", fieldName, channel.getName());
        });
    }

    private void tryFallback(Guild guild, String fieldName) {
        String nameToSearch = fieldName.replace("_ROLE_ID", "")
                                    .replace("_CHANNEL_ID", "")
                                    .replace("_ID", "")
                                    .replace("_", "-")
                                    .toLowerCase();

        if (fieldName.endsWith("_ROLE_ID")) {
            List<Role> roles = guild.getRolesByName(nameToSearch, true);
            if (!roles.isEmpty()) {
                logger.info("Fallback Success: Found Role '{}' by name search (ID: {}). Update BotConfig to fix permanent error.", roles.get(0).getName(), roles.get(0).getId());
            } else {
                logger.error("Fallback Failed: No role found for '{}'", nameToSearch);
            }
        } else if (fieldName.endsWith("_CHANNEL_ID")) {
            List<TextChannel> channels = guild.getTextChannelsByName(nameToSearch, true);
            if (!channels.isEmpty()) {
                logger.info("Fallback Success: Found Channel '#{}' by name search (ID: {}). Update BotConfig to fix permanent error.", channels.get(0).getName(), channels.get(0).getId());
            } else {
                logger.error("Fallback Failed: No channel found for '{}'", nameToSearch);
            }
        }
    }
}
