package EndZone.services;

import EndZone.config.BotConfig;
import EndZone.database.DatabaseService;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class StrikeScannerService {
    private static final Logger logger = LoggerFactory.getLogger(StrikeScannerService.class);
    private final StrikeService strikeService;
    private final String channelId = BotConfig.STAFF_STRIKE_LOG_CHANNEL_ID;

    public StrikeScannerService(StrikeService strikeService) {
        this.strikeService = strikeService;
    }

    public void scanStaffStrikes(JDA jda, Consumer<String> onProgress, Consumer<Integer> onComplete) {
        TextChannel channel = jda.getTextChannelById(channelId);
        if (channel == null) {
            onProgress.accept("❌ Staff strike logs channel not found.");
            onComplete.accept(0);
            return;
        }

        onProgress.accept("🔍 Starting scan of " + channel.getName() + "...");
        
        AtomicInteger importedCount = new AtomicInteger(0);
        AtomicInteger scannedCount = new AtomicInteger(0);

        channel.getIterableHistory().forEachAsync(message -> {
            scannedCount.incrementAndGet();
            if (processMessage(message)) {
                importedCount.incrementAndGet();
            }
            return true;
        }).thenRun(() -> {
            onProgress.accept("✅ Scan complete! Scanned " + scannedCount.get() + " messages.");
            onComplete.accept(importedCount.get());
        }).exceptionally(throwable -> {
            logger.error("Error during strike scan: ", throwable);
            onProgress.accept("❌ Error during scan: " + throwable.getMessage());
            onComplete.accept(importedCount.get());
            return null;
        });
    }

    private boolean processMessage(Message message) {
        List<MessageEmbed> embeds = message.getEmbeds();
        if (embeds.isEmpty()) return false;

        MessageEmbed embed = embeds.get(0);
        String title = embed.getTitle();
        if (title == null || (!title.equals("⚠️ STRIKE ISSUED") && !title.equals("⛔ Strike Issued"))) {
            return false;
        }

        try {
            String userId = null;
            String reason = null;
            String moderatorId = null;
            Timestamp date = new Timestamp(message.getTimeCreated().toInstant().toEpochMilli());

            if (title.equals("⚠️ STRIKE ISSUED")) {
                // Detailed Embed parsing
                for (MessageEmbed.Field field : embed.getFields()) {
                    String name = field.getName();
                    String value = field.getValue();
                    if (name == null || value == null) continue;

                    if (name.contains("Target User")) {
                        // Value: Name\nID\nMention
                        String[] lines = value.split("\n");
                        if (lines.length >= 2) userId = lines[1].trim();
                    } else if (name.contains("Moderator")) {
                        // Value: Name\nMention
                        if (value.contains("<@")) {
                            moderatorId = value.substring(value.indexOf("<@") + 2, value.indexOf(">")).replace("!", "");
                        }
                    } else if (name.contains("Reason")) {
                        reason = value;
                    }
                }
            } else {
                // Simple Embed parsing
                for (MessageEmbed.Field field : embed.getFields()) {
                    String name = field.getName();
                    String value = field.getValue();
                    if (name == null || value == null) continue;

                    if (name.equals("User ID")) {
                        userId = value.trim();
                    } else if (name.equals("Reason")) {
                        reason = value;
                    }
                }
                // Moderator ID is harder to get from simple embed description if not mentioned
                // But we can try to extract it from description: "<@ID> (Name) has been striked."
                String desc = embed.getDescription();
                if (moderatorId == null && message.getAuthor() != null && !message.getAuthor().isBot()) {
                    moderatorId = message.getAuthor().getId();
                } else {
                    moderatorId = BotConfig.OWNER_USER_ID; // Fallback
                }
            }

            if (userId != null && reason != null) {
                if (!strikeExists(userId, date)) {
                    strikeService.getDatabase().addStrike(userId, reason, moderatorId != null ? moderatorId : "UNKNOWN", date);
                    return true;
                }
            }
        } catch (Exception e) {
            logger.error("Error parsing strike message {}: {}", message.getId(), e.getMessage());
        }

        return false;
    }

    private boolean strikeExists(String userId, Timestamp date) {
        String sql = "SELECT 1 FROM strikes WHERE user_id = ? AND date = ?";
        try (Connection conn = DatabaseService.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, userId);
            pstmt.setTimestamp(2, date);
            ResultSet rs = pstmt.executeQuery();
            return rs.next();
        } catch (Exception e) {
            return false;
        }
    }
}
