package EndZone.services;

import EndZone.config.BotConfig;
import EndZone.database.StrikeDatabase;
import EndZone.models.Strike;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DemotionService {
    private static final Logger logger = LoggerFactory.getLogger(DemotionService.class);
    private static final int ENTRIES_PER_PAGE = 20;

    private final StrikeDatabase database;
    private final BotConfig config;
    private RoleRestorationService roleRestorationService;

    public DemotionService(StrikeService strikeService, BotConfig config) {
        this.database = strikeService.getDatabase();
        this.config = config;
    }

    public void setRoleRestorationService(RoleRestorationService roleRestorationService) {
        this.roleRestorationService = roleRestorationService;
    }

    public StrikeDatabase getDatabase() {
        return database;
    }

    public void addPermanentDemotion(String userId) {
        database.addPermanentDemotion(userId);
    }

    public void addTemporaryDemotion(String userId, List<String> roleIds, java.time.Instant restorationDate) {
        database.saveTemporaryDemotion(userId, roleIds, restorationDate);
    }

    public void markDemotionAsServed(String userId, int strikeCount) {
        database.markDemotionAsServed(userId, strikeCount);
    }

    public int getServedDemotionStrikeCount(String userId) {
        return database.getServedDemotionStrikeCount(userId);
    }

    public void clearServedDemotion(String userId) {
        database.clearServedDemotion(userId);
    }

    public void removeDemotion(String userId, JDA jda) {
        database.removeFromDemotions(userId);
        database.deleteTemporaryDemotion(userId);
        if (jda != null) {
            updateDemotionListMessage(jda);
        }
    }

    public void updateExistingDemotions(int daysToAdd) {
        database.bulkUpdateTemporaryDemotions(daysToAdd);
    }

    public Map<String, Map<String, Object>> getTemporaryDemotions() {
        return database.loadTemporaryDemotionsWithRoles();
    }

    public Set<String> getPermanentDemotions() {
        return database.loadPermanentDemotions();
    }

    public boolean isPermanentlyDemoted(String userId) {
        return database.loadPermanentDemotions().contains(userId);
    }

    public boolean isTemporarilyDemoted(String userId) {
        Map<String, Map<String, Object>> demotions = database.loadTemporaryDemotionsWithRoles();
        if (!demotions.containsKey(userId)) {
            return false;
        }
        
        java.time.Instant restorationDate = (java.time.Instant) demotions.get(userId).get("restoration_date");
        return restorationDate != null && java.time.Instant.now().isBefore(restorationDate);
    }

    public boolean isDemoted(String userId) {
        return isPermanentlyDemoted(userId) || isTemporarilyDemoted(userId);
    }

    public void updateDemotionListMessage(JDA jda) {
        String messageId = database.getDemotionListMessageId();
        if (messageId == null) {
            System.err.println("DEBUG: Cannot update demotion list message - Message ID is NULL. Have you run /initdemotionlist?");
            return;
        }

        TextChannel channel = jda.getTextChannelById(BotConfig.STAFF_NOTIFICATION_CHANNEL_ID);
        if (channel == null) {
            System.err.println("DEBUG: Staff notification channel NOT FOUND: " + BotConfig.STAFF_NOTIFICATION_CHANNEL_ID);
            return;
        }

        List<MessageEmbed> pages = buildPages();
        if (pages.isEmpty()) {
            System.err.println("DEBUG: buildPages() returned 0 pages.");
            return;
        }

        MessageEmbed embed = pages.get(0);
        ActionRow actionRow = buildActionRow(1, pages.size());

        if (actionRow != null) {
            channel.editMessageEmbedsById(messageId, embed)
                    .setComponents(actionRow)
                    .queue(
                        success -> System.out.println("DEBUG: Successfully updated demotion list message: " + messageId),
                        error -> System.err.println("DEBUG: Failed to update demotion list message: " + error.getMessage())
                    );
        } else {
            channel.editMessageEmbedsById(messageId, embed)
                    .setComponents(new ArrayList<>())
                    .queue(
                        success -> System.out.println("DEBUG: Successfully updated demotion list message (no buttons): " + messageId),
                        error -> System.err.println("DEBUG: Failed to update demotion list message: " + error.getMessage())
                    );
        }
    }

    public List<MessageEmbed> buildPages() {
        Map<String, Map<String, Object>> tempDemotions = database.loadTemporaryDemotionsWithRoles();
        Set<String> permDemotions = database.loadPermanentDemotions();
        List<String> allUsersWithStrikes = database.getAllUsersWithStrikes();
        logger.info("Building demotion pages. Found {} users with strikes in DB.", allUsersWithStrikes.size());

        List<String> tempLines = new ArrayList<>();
        List<String> permLines = new ArrayList<>();

        // Add explicit permanent demotions
        for (String userId : permDemotions) {
            permLines.add(String.format("<@%s> (%s) 3 strikes, permanent", userId, userId));
        }

        // Add explicit temporary demotions
        for (Map.Entry<String, Map<String, Object>> entry : tempDemotions.entrySet()) {
            if (permDemotions.contains(entry.getKey())) {
                continue;
            }
            java.time.Instant date = (java.time.Instant) entry.getValue().get("restoration_date");
            long unix = date.getEpochSecond();
            tempLines.add(String.format("<@%s> (%s) 2 strikes, back <t:%d:F>", entry.getKey(), entry.getKey(), unix));
        }

        // Add users from strikes table who might have been missed
        for (String userId : allUsersWithStrikes) {
            if (permDemotions.contains(userId) || tempDemotions.containsKey(userId)) {
                continue;
            }

            int strikeCount = database.getStrikes(userId).size();
            if (strikeCount >= 3) {
                permLines.add(String.format("<@%s> (%s) %d strikes, permanent", userId, userId, strikeCount));
            }
        }

        List<MessageEmbed> pages = new ArrayList<>();
        addPages(pages, permLines, "🔴 PERMANENT DEMOTIONS", Color.RED);
        addPages(pages, tempLines, "🟡 TEMPORARY DEMOTIONS", Color.YELLOW);

        return pages;
    }

    private void addPages(List<MessageEmbed> pages, List<String> lines, String title, Color color) {
        if (lines.isEmpty()) {
            pages.add(new EmbedBuilder()
                    .setTitle(title)
                    .setDescription("None\n")
                    .setColor(color)
                    .setFooter("Page " + (pages.size() + 1))
                    .build());
            return;
        }

        for (int i = 0; i < lines.size(); i += ENTRIES_PER_PAGE) {
            int end = Math.min(i + ENTRIES_PER_PAGE, lines.size());
            StringBuilder sb = new StringBuilder();
            for (int j = i; j < end; j++) {
                sb.append(lines.get(j)).append("\n");
            }
            pages.add(new EmbedBuilder()
                    .setTitle(title)
                    .setDescription((sb.toString().isEmpty() ? "None" : sb.toString()) + "\n")
                    .setColor(color)
                    .setFooter("Page " + (pages.size() + 1))
                    .build());
        }
    }

    public ActionRow buildActionRow(int currentPage, int totalPages) {
        return ActionRow.of(
                Button.primary("demotion_first", "⏮️ First").withDisabled(totalPages <= 1),
                Button.primary("demotion_prev", "◀ Prev").withDisabled(totalPages <= 1),
                Button.secondary("demotion_page", String.format("Page %d/%d", currentPage, totalPages)).asDisabled(),
                Button.primary("demotion_next", "Next ▶").withDisabled(totalPages <= 1),
                Button.primary("demotion_last", "Last ⏭️").withDisabled(totalPages <= 1)
        );
    }
}
