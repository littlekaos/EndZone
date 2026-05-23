package EndZone.services;

import EndZone.config.BotConfig;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ScoreboardReminderService {
    private static final Logger logger = LoggerFactory.getLogger(ScoreboardReminderService.class);
    private final JDA jda;
    private final ScheduledExecutorService scheduler;
    private static final String ADMIN_PLUS_1_ROLE_ID = BotConfig.BRULPH_ROLE_ID;
    private static final String ADMIN_PLUS_2_ROLE_ID = BotConfig.MASTER_ALPHA_ROLE_ID;
    private static final String STAFF_CHAT_CHANNEL_ID = BotConfig.STAFF_CHAT_CHANNEL_ID;
    private static final ZoneId EST_ZONE = ZoneId.of("America/New_York");

    private ZonedDateTime lastPingTime = null;

    public ScoreboardReminderService(JDA jda) {
        this.jda = jda;
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    public void start() {
        // Send initial ping on startup
        sendPing();
        lastPingTime = ZonedDateTime.now(EST_ZONE);

        // Run every minute to check if it's time for the next scheduled ping
        scheduler.scheduleAtFixedRate(this::checkAndPing, 1, 1, TimeUnit.MINUTES);
        logger.info("Scoreboard Reminder Service started");
    }

    private void checkAndPing() {
        ZonedDateTime now = ZonedDateTime.now(EST_ZONE);
        int hour = now.getHour();

        // Only ping at 11 PM (23:00)
        if (hour == 23) {
            // If we haven't pinged today yet
            if (lastPingTime == null || lastPingTime.toLocalDate().isBefore(now.toLocalDate())) {
                sendPing();
                lastPingTime = now;
            }
        }
    }

    private void sendPing() {
        try {
            TextChannel channel = jda.getTextChannelById(STAFF_CHAT_CHANNEL_ID);
            if (channel == null) {
                logger.warn("Staff chat channel not found for scoreboard reminder: {}", STAFF_CHAT_CHANNEL_ID);
                return;
            }

            Guild guild = channel.getGuild();
            Role adminPlusRole1 = guild.getRoleById(ADMIN_PLUS_1_ROLE_ID);
            Role adminPlusRole2 = guild.getRoleById(ADMIN_PLUS_2_ROLE_ID);

            Set<Member> membersToPing = new LinkedHashSet<>();

            if (adminPlusRole1 != null) {
                try {
                    List<Member> members = guild.findMembers(m -> m.getRoles().contains(adminPlusRole1)).get();
                    membersToPing.addAll(members);
                } catch (Exception e) {
                    logger.error("Error finding Admin+ members: {}", e.getMessage());
                }
            }
            if (adminPlusRole2 != null) {
                try {
                    List<Member> members = guild.findMembers(m -> m.getRoles().contains(adminPlusRole2)).get();
                    membersToPing.addAll(members);
                } catch (Exception e) {
                    logger.error("Error finding Admin+ members: {}", e.getMessage());
                }
            }

            String finalMentions;
            if (membersToPing.isEmpty()) {
                // Fallback to role mentions if no members found (e.g. not cached or empty)
                String mention1 = adminPlusRole1 != null ? adminPlusRole1.getAsMention() : "<@&" + ADMIN_PLUS_1_ROLE_ID + ">";
                String mention2 = adminPlusRole2 != null ? adminPlusRole2.getAsMention() : "<@&" + ADMIN_PLUS_2_ROLE_ID + ">";
                finalMentions = mention1 + " " + mention2;
            } else {
                StringBuilder sb = new StringBuilder();
                for (Member member : membersToPing) {
                    sb.append(member.getAsMention()).append(" ");
                }
                finalMentions = sb.toString().trim();
            }

            channel.sendMessage(finalMentions + " This is your reminder to do the SB's please and thank you!").queue(
                success -> logger.info("Scoreboard reminder sent at {}", ZonedDateTime.now(EST_ZONE)),
                error -> logger.error("Failed to send scoreboard reminder: {}", error.getMessage())
            );
        } catch (Exception e) {
            logger.error("Error in ScoreboardReminderService: {}", e.getMessage());
        }
    }

    public void stop() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info("Scoreboard Reminder Service shut down");
    }
}
