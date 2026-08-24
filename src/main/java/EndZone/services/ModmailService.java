package EndZone.services;

import EndZone.config.BotConfig;
import EndZone.database.DatabaseService;
import EndZone.models.ModmailLog;
import EndZone.models.ModmailSession;
import EndZone.util.UnbanAppealMessage;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ModmailService {
    private static final Logger logger = LoggerFactory.getLogger(ModmailService.class);
    private static final Pattern LOG_UUID_IN_URL = Pattern.compile(
            "/logs/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})"
    );
    private static final int LOG_LINK_REWRITE_HISTORY = 1500;
    private static final long LOG_LINK_REWRITE_DELAY_MS = 400;
    private static final int DISCORD_MESSAGE_LIMIT = 1900;
    private static final ScheduledExecutorService CLOSE_SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "modmail-close-scheduler");
        t.setDaemon(true);
        return t;
    });

    /** User ID → messages waiting for category selection before a staff channel is created. */
    private final Map<String, List<String>> pendingDms = new ConcurrentHashMap<>();
    /** Channel ID → scheduled close task. */
    private final Map<String, ScheduledFuture<?>> scheduledCloses = new ConcurrentHashMap<>();

    public ModmailSession findOpenByUser(String userId) {
        String sql = "SELECT * FROM ez_modmail_sessions WHERE user_id = ? AND status = 'OPEN' ORDER BY created_at DESC LIMIT 1";
        try (Connection conn = DatabaseService.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        } catch (Exception e) {
            logger.error("[Modmail] Failed to find open session for {}: {}", userId, e.getMessage());
        }
        return null;
    }

    public ModmailSession findOpenDmByUser(String userId) {
        String sql = "SELECT * FROM ez_modmail_sessions WHERE user_id = ? AND kind = 'DM' AND status = 'OPEN' ORDER BY created_at DESC LIMIT 1";
        try (Connection conn = DatabaseService.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        } catch (Exception e) {
            logger.error("[Modmail] Failed to find open DM session for {}: {}", userId, e.getMessage());
        }
        return null;
    }

    public ModmailSession findByChannel(String channelId) {
        String sql = "SELECT * FROM ez_modmail_sessions WHERE channel_id = ? LIMIT 1";
        try (Connection conn = DatabaseService.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, channelId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        } catch (Exception e) {
            logger.error("[Modmail] Failed to find session for channel {}: {}", channelId, e.getMessage());
        }
        return null;
    }

    public boolean isTrackedChannel(String channelId) {
        ModmailSession session = findByChannel(channelId);
        return session != null && session.isOpen();
    }

    public void openDmThread(User user, Message firstMessage, Consumer<ModmailSession> onReady, Consumer<String> onError) {
        ModmailSession existing = findOpenDmByUser(user.getId());
        if (existing != null) {
            MessageChannel channel = resolveChannel(existing);
            if (channel != null) {
                relayUserMessage(channel, user, firstMessage);
                onReady.accept(existing);
                return;
            }
            logger.warn("[Modmail] Clearing stale DM session {} for user {} (channel missing)", existing.getId(), user.getId());
            markSessionClosed(existing);
        }

        // Waiting for category button — queue this message, remind if needed
        if (pendingDms.containsKey(user.getId())) {
            queuePendingMessage(user.getId(), firstMessage);
            user.openPrivateChannel().queue(dm ->
                    dm.sendMessage("Please pick a category with the buttons above so we can open your ticket.").queue(
                            ok -> {}, ignored -> {}
                    )
            );
            onReady.accept(null);
            return;
        }

        // New contact: greet + category buttons; create channel after they choose
        queuePendingMessage(user.getId(), firstMessage);
        sendGreetingAndCategoryButtons(user, onError);
        onReady.accept(null);
    }

    public void handleCategorySelection(User user, String buttonId, Consumer<ModmailSession> onReady, Consumer<String> onError) {
        ModmailCategory categoryType = ModmailCategory.fromButtonId(buttonId);
        if (categoryType == null) {
            onError.accept("Unknown category.");
            return;
        }

        // Gem Event is not ticketed yet
        if (categoryType == ModmailCategory.GEM) {
            onError.accept("COMING_SOON");
            return;
        }

        ModmailSession existing = findOpenDmByUser(user.getId());
        if (existing != null && resolveChannel(existing) != null) {
            onError.accept("You already have an open modmail ticket.");
            return;
        }

        List<String> pending = pendingDms.getOrDefault(user.getId(), new ArrayList<>());
        if (pending.isEmpty()) {
            pending = List.of("*(opened ticket — no prior message)*");
        }

        Guild guild = ServiceManager.getJda().getGuildById(BotConfig.COURT_GUILD_ID);
        if (guild == null) {
            onError.accept("CourtZone guild not found.");
            return;
        }

        String categoryId = BotConfig.TICKET_ZONE_CATEGORY_ID;
        Category category = guild.getCategoryById(categoryId);
        if (category == null) {
            onError.accept("Ticket Zone category not found in CourtZone.");
            return;
        }

        String channelName = sessionChannelNameWithEmoji(categoryType.emoji, user.getName());
        List<String> messagesToRelay = new ArrayList<>(pending);
        pendingDms.remove(user.getId());

        createStaffOnlyChannel(guild, category, channelName, action ->
                action.queue(channel -> {
                    String opener = buildOpener(user, "New modmail — " + categoryType.label);
                    channel.sendMessage(opener).queue(success -> {
                        ModmailSession session = insertSession(
                                user.getId(), guild.getId(), channel.getId(), ModmailSession.Kind.DM, categoryType.label);
                        if (session == null) {
                            onError.accept("Failed to save modmail session.");
                            return;
                        }

                        int previous = countLogsByUserId(user.getId());
                        if (previous > 0) {
                            channel.sendMessage("This user has **" + previous
                                    + "** previous modmail thread"
                                    + (previous == 1 ? "" : "s")
                                    + ". Use `/logs` to see them.").queue();
                        }

                        for (String line : messagesToRelay) {
                            sendChunked(channel, formatUserLine(user, line));
                        }

                        if (categoryType == ModmailCategory.UNBAN) {
                            // User-facing form belongs in DMs only (they can't see the staff channel)
                            String appeal = UnbanAppealMessage.buildForDm(user.getAsMention());
                            user.openPrivateChannel().queue(dm ->
                                    dm.sendMessage(appeal).queue(ok -> {}, ignored -> {}),
                                    ignored -> {}
                            );
                        }

                        onReady.accept(session);
                    }, err -> onError.accept("Failed to post opener: " + err.getMessage()));
                }, err -> onError.accept("Failed to create channel: " + err.getMessage()))
        );
    }

    private void sendGreetingAndCategoryButtons(User user, Consumer<String> onError) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("EndZone Support")
                .setDescription("Thank you for your message! The EndZone management team will reply to you here as soon as possible.\n\n"
                        + "Please select what you need help with below:")
                .setColor(new Color(100, 150, 255))
                .setTimestamp(Instant.now());

        user.openPrivateChannel().queue(dm ->
                dm.sendMessageEmbeds(embed.build())
                        .setComponents(ActionRow.of(
                                Button.danger(BotConfig.MODMAIL_CAT_UNBAN, "Unban Request").withEmoji(Emoji.fromUnicode("🔨")),
                                Button.primary(BotConfig.MODMAIL_CAT_GEM, "Gem Event Questions/Concerns").withEmoji(Emoji.fromUnicode("💎")),
                                Button.secondary(BotConfig.MODMAIL_CAT_GENERAL, "General Concern").withEmoji(Emoji.fromUnicode("❓"))
                        ))
                        .queue(
                                ok -> {},
                                err -> {
                                    pendingDms.remove(user.getId());
                                    onError.accept("Failed to send greeting: " + err.getMessage());
                                }
                        ),
                err -> {
                    pendingDms.remove(user.getId());
                    onError.accept("Could not open your DMs: " + err.getMessage());
                }
        );
    }

    private void queuePendingMessage(String userId, Message message) {
        String content = message.getContentDisplay();
        if (content == null || content.isBlank()) {
            if (!message.getAttachments().isEmpty()) {
                content = "*[attachment]*";
            } else {
                content = "*[empty message]*";
            }
        }
        pendingDms.computeIfAbsent(userId, k -> new ArrayList<>()).add(content);
    }

    public enum ModmailCategory {
        UNBAN("Unban Request", "🔨", BotConfig.MODMAIL_CAT_UNBAN),
        GEM("Gem Event Questions/Concerns", "💎", BotConfig.MODMAIL_CAT_GEM),
        GENERAL("General Concern", "❓", BotConfig.MODMAIL_CAT_GENERAL);

        final String label;
        final String emoji;
        final String buttonId;

        ModmailCategory(String label, String emoji, String buttonId) {
            this.label = label;
            this.emoji = emoji;
            this.buttonId = buttonId;
        }

        static ModmailCategory fromButtonId(String id) {
            for (ModmailCategory c : values()) {
                if (c.buttonId.equals(id)) return c;
            }
            return null;
        }
    }

    public void openTicket(Guild guild, Member opener, Consumer<ModmailSession> onReady, Consumer<String> onError) {
        // Only block on an existing open TICKET (DMs are separate)
        ModmailSession existingTicket = findOpenTicketByUser(opener.getId());
        if (existingTicket != null) {
            MessageChannel existingChannel = resolveChannel(existingTicket);
            if (existingChannel != null) {
                onError.accept("You already have an open ticket: <#" + existingTicket.getChannelId() + ">");
                return;
            }
            logger.warn("[Modmail] Clearing stale ticket session {} for user {}", existingTicket.getId(), opener.getId());
            markSessionClosed(existingTicket);
        }

        boolean isCourt = guild.getId().equals(BotConfig.COURT_GUILD_ID);
        String categoryId = isCourt
                ? BotConfig.TICKET_ZONE_CATEGORY_ID
                : BotConfig.MAIN_TICKET_CATEGORY_ID;

        if (categoryId == null || categoryId.isBlank()) {
            onError.accept("Ticket category is not configured for this server.");
            return;
        }

        Category category = guild.getCategoryById(categoryId);
        if (category == null) {
            onError.accept("Ticket category not found. Check the category ID in BotConfig.");
            return;
        }

        String prefix = isCourt ? "courtzone" : "ez-main";
        String channelName = sessionChannelName(prefix, opener.getUser().getName());
        EnumSet<Permission> allow = staffChannelAllowPerms();
        EnumSet<Permission> denyView = EnumSet.of(Permission.VIEW_CHANNEL);

        var action = guild.createTextChannel(channelName)
                .setParent(category)
                .addPermissionOverride(guild.getPublicRole(), null, denyView)
                .addMemberPermissionOverride(opener.getIdLong(), allow, null)
                .addMemberPermissionOverride(guild.getSelfMember().getIdLong(), allow, null);

        for (String roleId : staffRoleIdsForGuild(guild)) {
            Role role = guild.getRoleById(roleId);
            if (role != null) {
                action = action.addRolePermissionOverride(role.getIdLong(), allow, null);
            }
        }

        action.queue(channel -> {
            String openerText = buildOpener(opener.getUser(), "New ticket");
            channel.sendMessage(openerText).queue(success -> {
                ModmailSession session = insertSession(opener.getId(), guild.getId(), channel.getId(), ModmailSession.Kind.TICKET, "Ticket");
                if (session == null) {
                    onError.accept("Failed to save ticket session.");
                    return;
                }
                opener.getUser().openPrivateChannel().queue(dm ->
                        dm.sendMessage("Your ticket has been created: **" + channel.getAsMention() + "** in **" + guild.getName() + "**.").queue(
                                ok -> {},
                                ignored -> {}
                        )
                );
                onReady.accept(session);
            }, err -> onError.accept("Failed to post ticket opener: " + err.getMessage()));
        }, err -> onError.accept("Failed to create ticket channel: " + err.getMessage()));
    }

    private void createStaffOnlyChannel(Guild guild, Category category, String channelName,
                                        Consumer<net.dv8tion.jda.api.requests.restaction.ChannelAction<TextChannel>> then) {
        EnumSet<Permission> allow = staffChannelAllowPerms();
        EnumSet<Permission> denyView = EnumSet.of(Permission.VIEW_CHANNEL);

        var action = guild.createTextChannel(channelName)
                .setParent(category)
                .addPermissionOverride(guild.getPublicRole(), null, denyView)
                .addMemberPermissionOverride(guild.getSelfMember().getIdLong(), allow, null);

        for (String roleId : staffRoleIdsForGuild(guild)) {
            Role role = guild.getRoleById(roleId);
            if (role != null) {
                action = action.addRolePermissionOverride(role.getIdLong(), allow, null);
            }
        }
        then.accept(action);
    }

    private EnumSet<Permission> staffChannelAllowPerms() {
        return EnumSet.of(
                Permission.VIEW_CHANNEL,
                Permission.MESSAGE_SEND,
                Permission.MESSAGE_HISTORY,
                Permission.MESSAGE_ATTACH_FILES,
                Permission.MESSAGE_EMBED_LINKS,
                Permission.MESSAGE_ADD_REACTION
        );
    }

    public ModmailSession findOpenTicketByUser(String userId) {
        String sql = "SELECT * FROM ez_modmail_sessions WHERE user_id = ? AND kind = 'TICKET' AND status = 'OPEN' ORDER BY created_at DESC LIMIT 1";
        try (Connection conn = DatabaseService.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        } catch (Exception e) {
            logger.error("[Modmail] Failed to find open ticket for {}: {}", userId, e.getMessage());
        }
        return null;
    }

    private void markSessionClosed(ModmailSession session) {
        String sql = "UPDATE ez_modmail_sessions SET status = 'CLOSED', closed_at = ? WHERE id = ?";
        try (Connection conn = DatabaseService.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, System.currentTimeMillis());
            ps.setInt(2, session.getId());
            ps.executeUpdate();
        } catch (Exception e) {
            logger.error("[Modmail] Failed to mark session {} closed: {}", session.getId(), e.getMessage());
        }
    }

    /** Discord channel names: "🔨-bryce_01", "💎-bryce_01", "❓-bryce_01". */
    private String sessionChannelNameWithEmoji(String emoji, String username) {
        String clean = username == null ? "user" : username.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]", "-").replaceAll("-+", "-");
        if (clean.startsWith("-")) clean = clean.substring(1);
        if (clean.endsWith("-")) clean = clean.substring(0, clean.length() - 1);
        if (clean.isBlank()) clean = "user";
        if (clean.length() > 80) clean = clean.substring(0, 80);
        String name = emoji + "-" + clean;
        if (name.length() > 100) {
            name = name.substring(0, 100);
        }
        return name;
    }

    /** Discord channel names: lowercase letters/numbers/hyphens only → "bryce_01-0", "ez-main-bryce_01-0". */
    private String sessionChannelName(String username) {
        return sessionChannelName(null, username);
    }

    private String sessionChannelName(String prefix, String username) {
        String clean = username == null ? "user" : username.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]", "-").replaceAll("-+", "-");
        if (clean.startsWith("-")) clean = clean.substring(1);
        if (clean.endsWith("-")) clean = clean.substring(0, clean.length() - 1);
        if (clean.isBlank()) clean = "user";
        if (clean.length() > 70) clean = clean.substring(0, 70);
        String name = (prefix == null || prefix.isBlank())
                ? clean + "-0"
                : prefix.toLowerCase(Locale.ROOT) + "-" + clean + "-0";
        if (name.length() > 100) {
            name = name.substring(0, 100);
        }
        return name;
    }

    public void handleStaffOrUserGuildMessage(Message message, ModmailSession session) {
        if (!session.isOpen()) return;

        User author = message.getAuthor();
        boolean fromTicketUser = author.getId().equals(session.getUserId());

        if (fromTicketUser) {
            String line = formatUserLine(author, message.getContentDisplay());
            replaceWithRelay(message, line, message.getAttachments());
            return;
        }

        // DM channels: staff free-chat is internal notes only — use /reply to message the user
        if (session.getKind() == ModmailSession.Kind.DM) {
            return;
        }

        // Ticket channels: reformat staff messages as transcript lines (new message each time)
        Member member = message.getMember();
        String roleName = resolveStaffRoleName(member);
        String line = formatStaffLine(roleName, author.getName(), message.getContentDisplay());
        replaceWithRelay(message, line, message.getAttachments());
    }

    /**
     * Staff /reply — DMs the user first, then posts a formatted line in the staff channel.
     */
    public void staffReply(ModmailSession session, Member staff, String content, Runnable onSuccess, Consumer<String> onError) {
        if (session == null || !session.isOpen()) {
            onError.accept("This channel is not an open modmail thread or ticket.");
            return;
        }
        if (content == null || content.isBlank()) {
            onError.accept("Reply content cannot be empty.");
            return;
        }

        MessageChannel channel = resolveChannel(session);
        if (channel == null) {
            onError.accept("Could not find the modmail channel.");
            return;
        }

        String roleName = resolveStaffRoleName(staff);
        String line = formatStaffLine(roleName, staff.getUser().getName(), content);

        ServiceManager.getJda().retrieveUserById(session.getUserId()).queue(user ->
                user.openPrivateChannel().queue(dm ->
                                dm.sendMessage(line).queue(ok -> {
                                    sendChunked(channel, line);
                                    onSuccess.run();
                                }, err -> {
                                    logger.error("[Modmail] Failed to DM reply to {}: {}", session.getUserId(), err.getMessage());
                                    onError.accept("Failed to DM user: " + err.getMessage());
                                }),
                        err -> {
                            logger.error("[Modmail] Could not open DM with {}: {}", session.getUserId(), err.getMessage());
                            onError.accept("Could not open a DM with that user. They may have DMs closed.");
                        }
                ),
                err -> {
                    logger.error("[Modmail] Could not retrieve user {}: {}", session.getUserId(), err.getMessage());
                    onError.accept("Could not find the user to DM.");
                }
        );
    }

    public void notifyUserMessageEdited(ModmailSession session, String oldContent, String newContent) {
        MessageChannel channel = resolveChannel(session);
        if (channel == null) return;

        String notice = "The user has edited their message:\n\n"
                + "`A:` " + oldContent + "\n"
                + "`B:` " + newContent;
        sendChunked(channel, notice);
    }

    public void relayUserMessage(MessageChannel staffChannel, User user, Message source) {
        String line = formatUserLine(user, source.getContentDisplay());
        if (source.getAttachments() == null || source.getAttachments().isEmpty()) {
            sendChunked(staffChannel, line);
        } else {
            sendRelay(staffChannel, line, source.getAttachments());
        }
    }

    public void closeSession(ModmailSession session, String reason) {
        closeSession(session, reason, null);
    }

    /**
     * Schedule an automatic close. Replaces any existing schedule for this channel.
     * @return true if scheduled successfully
     */
    public boolean scheduleClose(ModmailSession session, long delayMillis, String reason, User closedBy, String displayDuration) {
        if (session == null || !session.isOpen() || delayMillis <= 0) {
            return false;
        }

        cancelScheduledClose(session.getChannelId());

        int sessionId = session.getId();
        String channelId = session.getChannelId();
        String closerId = closedBy != null ? closedBy.getId() : null;

        ScheduledFuture<?> future = CLOSE_SCHEDULER.schedule(() -> {
            scheduledCloses.remove(channelId);
            ModmailSession current = findByChannel(channelId);
            if (current == null || !current.isOpen() || current.getId() != sessionId) {
                logger.info("[Modmail] Skipping scheduled close for {} — session no longer open", channelId);
                return;
            }
            User closer = null;
            if (closerId != null) {
                try {
                    closer = ServiceManager.getJda().retrieveUserById(closerId).complete();
                } catch (Exception ignored) {
                }
            }
            String closeReason = reason != null && !reason.isBlank()
                    ? reason
                    : ("closed as scheduled after " + displayDuration);
            MessageChannel channel = resolveChannel(current);
            if (channel != null) {
                channel.sendMessage("Closing ticket now (scheduled).").queue();
            }
            closeSession(current, closeReason, closer);
        }, delayMillis, TimeUnit.MILLISECONDS);

        scheduledCloses.put(channelId, future);
        logger.info("[Modmail] Scheduled close for channel {} in {}ms", channelId, delayMillis);
        return true;
    }

    public boolean cancelScheduledClose(String channelId) {
        ScheduledFuture<?> future = scheduledCloses.remove(channelId);
        if (future == null) {
            return false;
        }
        boolean cancelled = future.cancel(false);
        logger.info("[Modmail] Cancelled scheduled close for channel {} ({})", channelId, cancelled);
        return cancelled;
    }

    public boolean hasScheduledClose(String channelId) {
        ScheduledFuture<?> future = scheduledCloses.get(channelId);
        return future != null && !future.isDone();
    }

    public void closeSession(ModmailSession session, String reason, User closedBy) {
        cancelScheduledClose(session.getChannelId());

        String sql = "UPDATE ez_modmail_sessions SET status = 'CLOSED', closed_at = ? WHERE id = ?";
        try (Connection conn = DatabaseService.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, System.currentTimeMillis());
            ps.setInt(2, session.getId());
            ps.executeUpdate();
        } catch (Exception e) {
            logger.error("[Modmail] Failed to close session {}: {}", session.getId(), e.getMessage());
            return;
        }

        MessageChannel channel = resolveChannel(session);
        Runnable afterLog = () -> {
            if (channel != null) {
                channel.delete().queue(
                        ok -> {},
                        err -> logger.warn("[Modmail] Could not delete channel {}: {}", session.getChannelId(), err.getMessage())
                );
            }
        };

        // Save transcript + post log link, then delete the channel (no DM to the user)
        if (channel != null) {
            collectTranscript(channel, lines -> logClosed(session, closedBy, lines, afterLog));
        } else {
            logClosed(session, closedBy, List.of(), afterLog);
        }
    }

    private void logClosed(ModmailSession session, User closedBy, List<String> transcriptLines, Runnable onDone) {
        String closerId = closedBy != null ? closedBy.getId() : null;
        String closerName = closedBy != null ? closedBy.getName() : "staff";
        String transcript = transcriptLines.isEmpty()
                ? "(No messages in transcript)"
                : String.join("\n", transcriptLines);
        String logUuid = UUID.randomUUID().toString();

        boolean saved = saveLog(logUuid, session, closerId, closerName, transcript);
        if (!saved) {
            logger.error("[Modmail] Failed to persist transcript for session {}", session.getId());
        }

        TextChannel logChannel = getLogChannel();
        if (logChannel == null) {
            onDone.run();
            return;
        }

        String logsUrl = buildWebLogUrl(logUuid);

        ServiceManager.getJda().retrieveUserById(session.getUserId()).queue(user -> {
            String summary = "EndZone thread with " + user.getName() + " (" + session.getUserId()
                    + ") was closed by " + closerName + "\nLogs: " + logsUrl;
            postCloseSummary(logChannel, summary, logUuid, onDone);
        }, err -> {
            String summary = "EndZone thread with unknown (" + session.getUserId()
                    + ") was closed by " + closerName + "\nLogs: " + logsUrl;
            postCloseSummary(logChannel, summary, logUuid, onDone);
        });
    }

    private void postCloseSummary(TextChannel logChannel, String summary, String logUuid, Runnable onDone) {
        var message = new MessageCreateBuilder()
                .setContent(summary)
                .setAllowedMentions(List.of())
                .build();
        logChannel.sendMessage(message).queue(
                ok -> {
                    updateLogDiscordUrl(logUuid, ok.getJumpUrl());
                    onDone.run();
                },
                err -> {
                    logger.warn("[Modmail] Failed to post close log: {}", err.getMessage());
                    onDone.run();
                }
        );
    }

    public ModmailLog findLogByUuid(String uuid) {
        String sql = "SELECT * FROM ez_modmail_logs WHERE log_uuid = ? LIMIT 1";
        try (Connection conn = DatabaseService.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapLogRow(rs);
            }
        } catch (Exception e) {
            logger.error("[Modmail] Failed to find log {}: {}", uuid, e.getMessage());
        }
        return null;
    }

    public List<ModmailLog> findLogsByUserId(String userId) {
        List<ModmailLog> logs = new ArrayList<>();
        String sql = "SELECT * FROM ez_modmail_logs WHERE user_id = ? ORDER BY closed_at DESC";
        try (Connection conn = DatabaseService.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    logs.add(mapLogRow(rs));
                }
            }
        } catch (Exception e) {
            logger.error("[Modmail] Failed to list logs for {}: {}", userId, e.getMessage());
        }
        return logs;
    }

    public int countLogsByUserId(String userId) {
        String sql = "SELECT COUNT(*) FROM ez_modmail_logs WHERE user_id = ?";
        try (Connection conn = DatabaseService.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (Exception e) {
            logger.error("[Modmail] Failed to count logs for {}: {}", userId, e.getMessage());
        }
        return 0;
    }

    public int deleteLogsByUserId(String userId) {
        String sql = "DELETE FROM ez_modmail_logs WHERE user_id = ?";
        try (Connection conn = DatabaseService.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            return ps.executeUpdate();
        } catch (Exception e) {
            logger.error("[Modmail] Failed to delete logs for {}: {}", userId, e.getMessage());
            return 0;
        }
    }

    public String buildLogUrl(ModmailLog log) {
        return buildWebLogUrl(log.getLogUuid());
    }

    public List<String> buildWebLogUrls(String logUuid) {
        if (ServiceManager.getConfig() != null) {
            List<String> urls = ServiceManager.getConfig().buildModmailLogUrls(logUuid);
            if (!urls.isEmpty()) {
                return urls;
            }
        }
        return List.of("http://localhost:" + BotConfig.DEFAULT_MODMAIL_LOGS_PORT + "/logs/" + logUuid);
    }

    public String buildWebLogUrl(String logUuid) {
        return buildWebLogUrls(logUuid).get(0);
    }

    public String buildLogUrlBlock(ModmailLog log) {
        return String.join("\n", buildWebLogUrls(log.getLogUuid()));
    }

    /**
     * After boot, rewrite already-posted close-summary messages so each has
     * a single Logs: URL for this machine (desktop 8080, laptop 8890, mini PC 9090).
     */
    public void rewritePostedLogLinksAsync() {
        TextChannel channel = getLogChannel();
        if (channel == null) {
            logger.warn("[ModmailLogs] Cannot rewrite posted links — log channel not found");
            return;
        }
        String selfId = ServiceManager.getJda().getSelfUser().getId();
        logger.info("[ModmailLogs] Scanning log channel to update ticket links for this host...");
        channel.getIterableHistory().takeAsync(LOG_LINK_REWRITE_HISTORY).thenAccept(messages -> {
            int queued = 0;
            for (Message msg : messages) {
                if (!selfId.equals(msg.getAuthor().getId())) {
                    continue;
                }
                String updated = rewriteCloseSummaryContent(msg.getContentRaw());
                if (updated == null || updated.equals(msg.getContentRaw())) {
                    continue;
                }
                long delay = queued * LOG_LINK_REWRITE_DELAY_MS;
                String jump = msg.getJumpUrl();
                String uuid = extractLogUuid(updated);
                msg.editMessage(updated).queueAfter(delay, TimeUnit.MILLISECONDS,
                        ok -> {
                            if (uuid != null) {
                                updateLogDiscordUrl(uuid, jump);
                            }
                        },
                        err -> logger.warn("[ModmailLogs] Failed to rewrite {}: {}", jump, err.getMessage())
                );
                queued++;
            }
            if (queued == 0) {
                logger.info("[ModmailLogs] Posted ticket links already match this host");
            } else {
                logger.info("[ModmailLogs] Updating {} posted ticket link message(s)", queued);
            }
        }).exceptionally(err -> {
            logger.warn("[ModmailLogs] Failed to scan log channel: {}", err.getMessage());
            return null;
        });
    }

    String rewriteCloseSummaryContent(String content) {
        if (content == null || !content.contains("/logs/")) {
            return null;
        }
        String uuid = extractLogUuid(content);
        if (uuid == null) {
            return null;
        }
        List<String> urls = buildWebLogUrls(uuid);
        if (urls.isEmpty()) {
            return null;
        }
        String url = urls.get(0);
        String newBlock = "Logs: " + url;
        if (countLogUrls(content) == 1 && content.contains(url)) {
            return content;
        }
        int logsIdx = content.indexOf("Logs:");
        if (logsIdx >= 0) {
            return content.substring(0, logsIdx) + newBlock;
        }
        return content;
    }

    private static String extractLogUuid(String content) {
        Matcher m = LOG_UUID_IN_URL.matcher(content);
        return m.find() ? m.group(1) : null;
    }

    private static int countLogUrls(String content) {
        int count = 0;
        Matcher m = LOG_UUID_IN_URL.matcher(content);
        while (m.find()) {
            count++;
        }
        return count;
    }

    private void updateLogDiscordUrl(String logUuid, String discordUrl) {
        if (logUuid == null || discordUrl == null || discordUrl.isBlank()) {
            return;
        }
        String sql = "UPDATE ez_modmail_logs SET discord_url = ? WHERE log_uuid = ?";
        try (Connection conn = DatabaseService.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, discordUrl);
            ps.setString(2, logUuid);
            ps.executeUpdate();
        } catch (Exception e) {
            logger.warn("[Modmail] Failed to save discord_url for {}: {}", logUuid, e.getMessage());
        }
    }

    /**
     * Insert a Ticket Tool (or other) transcript. Returns true if a new row was written,
     * false if {@code logUuid} already exists or the insert failed.
     */
    public boolean insertImportedLog(String logUuid, String userId, String closedById, String closedByName,
                                     String category, String transcript, long createdAt, long closedAt,
                                     String discordUrl) {
        if (logUuid == null || logUuid.isBlank() || userId == null || userId.isBlank()) {
            return false;
        }
        String body = transcript != null && !transcript.isBlank() ? transcript : "(No messages in transcript)";
        ModmailLog existing = findLogByUuid(logUuid);
        if (existing != null) {
            if (existing.getSessionId() != 0) {
                return false;
            }
            String sql = """
                    UPDATE ez_modmail_logs
                    SET user_id = ?, closed_by_id = ?, closed_by_name = ?, category = ?, transcript = ?,
                        created_at = ?, closed_at = ?, discord_url = ?
                    WHERE log_uuid = ?
                    """;
            try (Connection conn = DatabaseService.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, userId);
                ps.setString(2, closedById);
                ps.setString(3, closedByName);
                ps.setString(4, category);
                ps.setString(5, body);
                ps.setLong(6, createdAt > 0 ? createdAt : closedAt);
                ps.setLong(7, closedAt);
                ps.setString(8, discordUrl);
                ps.setString(9, logUuid);
                return ps.executeUpdate() > 0;
            } catch (Exception e) {
                logger.error("[Modmail] Failed to refresh imported log {}: {}", logUuid, e.getMessage());
                return false;
            }
        }
        String sql = """
                INSERT INTO ez_modmail_logs
                (log_uuid, session_id, user_id, closed_by_id, closed_by_name, category, transcript, created_at, closed_at, discord_url)
                VALUES (?, 0, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection conn = DatabaseService.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, logUuid);
            ps.setString(2, userId);
            ps.setString(3, closedById);
            ps.setString(4, closedByName);
            ps.setString(5, category);
            ps.setString(6, body);
            ps.setLong(7, createdAt > 0 ? createdAt : closedAt);
            ps.setLong(8, closedAt);
            ps.setString(9, discordUrl);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            logger.error("[Modmail] Failed to import log {}: {}", logUuid, e.getMessage());
            return false;
        }
    }

    static boolean isWeakImportedTranscript(String transcript) {
        if (transcript == null || transcript.isBlank()) {
            return true;
        }
        String t = transcript.trim();
        return t.startsWith("(Could not")
                || t.startsWith("(No messages")
                || t.length() < 60;
    }

    private boolean saveLog(String logUuid, ModmailSession session, String closedById,
                            String closedByName, String transcript) {
        String sql = """
                INSERT INTO ez_modmail_logs
                (log_uuid, session_id, user_id, closed_by_id, closed_by_name, category, transcript, created_at, closed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        long closedAt = System.currentTimeMillis();
        try (Connection conn = DatabaseService.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, logUuid);
            ps.setInt(2, session.getId());
            ps.setString(3, session.getUserId());
            ps.setString(4, closedById);
            ps.setString(5, closedByName);
            ps.setString(6, session.getCategory());
            ps.setString(7, transcript);
            ps.setLong(8, session.getCreatedAt());
            ps.setLong(9, closedAt);
            ps.executeUpdate();
            return true;
        } catch (Exception e) {
            logger.error("[Modmail] Failed to save log {}: {}", logUuid, e.getMessage());
            return false;
        }
    }

    private ModmailLog mapLogRow(ResultSet rs) throws Exception {
        String discordUrl = null;
        try {
            discordUrl = rs.getString("discord_url");
        } catch (Exception ignored) {
            // older DBs before migration
        }
        return new ModmailLog(
                rs.getInt("id"),
                rs.getString("log_uuid"),
                rs.getInt("session_id"),
                rs.getString("user_id"),
                rs.getString("closed_by_id"),
                rs.getString("closed_by_name"),
                rs.getString("category"),
                rs.getString("transcript"),
                rs.getLong("created_at"),
                rs.getLong("closed_at"),
                discordUrl
        );
    }

    private void collectTranscript(MessageChannel source, Consumer<List<String>> onDone) {
        List<String> collected = new ArrayList<>();
        collectTranscriptRecursive(source, null, 0, collected, () -> {
            java.util.Collections.reverse(collected);
            onDone.accept(collected);
        });
    }

    private void collectTranscriptRecursive(MessageChannel source, Message before, int depth,
                                            List<String> collected, Runnable onDone) {
        if (depth >= 5) {
            onDone.run();
            return;
        }

        int limit = 100;
        if (before == null) {
            source.getHistory().retrievePast(limit).queue(messages -> {
                addTranscriptMessages(messages, collected);
                if (messages.size() < limit) {
                    onDone.run();
                } else {
                    collectTranscriptRecursive(source, messages.get(messages.size() - 1), depth + 1, collected, onDone);
                }
            }, err -> onDone.run());
        } else {
            source.getHistoryBefore(before.getId(), limit).queue(history -> {
                List<Message> messages = history.getRetrievedHistory();
                addTranscriptMessages(messages, collected);
                if (messages.size() < limit) {
                    onDone.run();
                } else {
                    collectTranscriptRecursive(source, messages.get(messages.size() - 1), depth + 1, collected, onDone);
                }
            }, err -> onDone.run());
        }
    }

    private void addTranscriptMessages(List<Message> messages, List<String> collected) {
        for (Message message : messages) {
            String content = message.getContentRaw();
            if (content == null || content.isBlank()) {
                if (!message.getAttachments().isEmpty()) {
                    collected.add("*(attachment)*");
                }
                continue;
            }
            if (content.startsWith("Thread closed")) continue;
            collected.add(content);
        }
    }

    private TextChannel getLogChannel() {
        String id = BotConfig.MODMAIL_LOG_CHANNEL_ID;
        if (id == null || id.isBlank()) return null;
        return ServiceManager.getJda().getTextChannelById(id);
    }

    public String buildOpener(User user, String title) {
        String age = formatAccountAge(user.getTimeCreated());
        return "@here " + title + " (**" + user.getName() + "**)\n"
                + "**ACCOUNT AGE " + age + ", ID " + user.getId() + "**\n"
                + "--------------------";
    }

    public String formatUserLine(User user, String content) {
        String body = content == null || content.isBlank() ? "*[attachment only]*" : content;
        return "**" + user.getName() + "**: " + body;
    }

    public String formatStaffLine(String roleName, String username, String content) {
        String body = content == null || content.isBlank() ? "*[attachment only]*" : content;
        return "**(" + roleName + ") " + username + "**: " + body;
    }

    public String resolveStaffRoleName(Member member) {
        if (member == null) return "Staff";
        Role best = null;
        for (Role role : member.getRoles()) {
            if (!BotConfig.isStaffOrModRole(role.getId())
                    && !BotConfig.ADMIN_ROLES.contains(role.getId())
                    && !BotConfig.SEMI_MOD_ROLES.contains(role.getId())
                    && !isCourtStaffRole(role.getId())) {
                continue;
            }
            if (best == null || role.getPosition() > best.getPosition()) {
                best = role;
            }
        }
        return best != null ? best.getName() : "Staff";
    }

    public String formatAccountAge(OffsetDateTime created) {
        Period period = Period.between(created.toLocalDate(), Instant.now().atOffset(ZoneOffset.UTC).toLocalDate());
        List<String> parts = new ArrayList<>();
        if (period.getYears() > 0) {
            parts.add(period.getYears() + (period.getYears() == 1 ? " year" : " years"));
        }
        if (period.getMonths() > 0) {
            parts.add(period.getMonths() + (period.getMonths() == 1 ? " month" : " months"));
        }
        if (period.getDays() > 0 || parts.isEmpty()) {
            parts.add(period.getDays() + (period.getDays() == 1 ? " day" : " days"));
        }
        // Prefer years+months like ModMail when both present; otherwise weeks for short ages
        if (period.getYears() == 0 && period.getMonths() == 0 && period.getDays() >= 7) {
            int weeks = period.getDays() / 7;
            int days = period.getDays() % 7;
            parts.clear();
            parts.add(weeks + (weeks == 1 ? " week" : " weeks"));
            if (days > 0) {
                parts.add(days + (days == 1 ? " day" : " days"));
            }
        }
        return String.join(", ", parts);
    }

    private boolean isCourtStaffRole(String roleId) {
        return roleId.equals(BotConfig.COURT_THE_JUDGE_EZ_ROLE_ID)
                || roleId.equals(BotConfig.COURT_THE_DISTRICT_ATTORNIES_EZ_ROLE_ID)
                || roleId.equals(BotConfig.COURT_THE_JUDGE_ZRE_ROLE_ID)
                || roleId.equals(BotConfig.COURT_SPECIAL_PEOPLE_ROLE_ID)
                || roleId.equals(BotConfig.COURT_THE_JURY_EZ_ROLE_ID)
                || roleId.equals(BotConfig.COURT_THE_JURY_ZRE_ROLE_ID);
    }

    private List<String> staffRoleIdsForGuild(Guild guild) {
        List<String> ids = new ArrayList<>();
        if (guild.getId().equals(BotConfig.COURT_GUILD_ID)) {
            ids.add(BotConfig.COURT_THE_JUDGE_EZ_ROLE_ID);
            ids.add(BotConfig.COURT_THE_DISTRICT_ATTORNIES_EZ_ROLE_ID);
            ids.add(BotConfig.COURT_THE_JUDGE_ZRE_ROLE_ID);
            ids.add(BotConfig.COURT_SPECIAL_PEOPLE_ROLE_ID);
            ids.add(BotConfig.COURT_THE_JURY_EZ_ROLE_ID);
            ids.add(BotConfig.COURT_THE_JURY_ZRE_ROLE_ID);
        } else {
            // EndZone tickets: Alphas + Alpha Betas only
            ids.add(BotConfig.ALPHAS_ROLE_ID);
            ids.add(BotConfig.ALPHA_BETAS_ROLE_ID);
        }
        return ids.stream().distinct().toList();
    }

    private void sendDmToUser(User user, Message staffMessage, String staffLine, Runnable after) {
        String dmContent = staffMessage.getContentDisplay();
        if (dmContent == null || dmContent.isBlank()) {
            dmContent = "*[Staff sent an attachment]*";
        }
        String finalContent = dmContent;
        user.openPrivateChannel().queue(dm -> {
            if (staffMessage.getAttachments().isEmpty()) {
                dm.sendMessage(finalContent).queue(ok -> after.run(), err -> {
                    logger.warn("[Modmail] Failed to DM user: {}", err.getMessage());
                    after.run();
                });
            } else {
                downloadAttachments(staffMessage).thenAccept(uploads ->
                        dm.sendMessage(finalContent).setFiles(uploads).queue(ok -> after.run(), err -> after.run())
                );
            }
        }, err -> after.run());
    }

    private void replaceWithRelay(Message original, String line, List<Message.Attachment> attachments) {
        MessageChannel channel = original.getChannel();
        List<Message.Attachment> attachmentCopy = attachments == null ? List.of() : new ArrayList<>(attachments);

        if (attachmentCopy.isEmpty()) {
            original.delete().queue(
                    success -> sendChunked(channel, line),
                    error -> sendChunked(channel, line)
            );
            return;
        }

        downloadAttachmentsFromList(attachmentCopy).thenAccept(uploads ->
                original.delete().queue(success -> {
                    if (uploads.isEmpty()) {
                        sendChunked(channel, line);
                    } else {
                        channel.sendMessage(truncate(line)).setFiles(uploads).queue(
                                ok -> {},
                                err -> sendChunked(channel, line + "\n*(Failed to forward attachments)*")
                        );
                    }
                }, error -> {
                    if (uploads.isEmpty()) {
                        sendChunked(channel, line);
                    } else {
                        channel.sendMessage(truncate(line)).setFiles(uploads).queue();
                    }
                })
        );
    }

    private void sendRelay(MessageChannel channel, String line, List<Message.Attachment> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            sendChunked(channel, line);
            return;
        }
        downloadAttachmentsFromList(attachments).thenAccept(uploads -> {
            if (uploads.isEmpty()) {
                sendChunked(channel, line);
            } else {
                channel.sendMessage(truncate(line)).setFiles(uploads).queue(
                        ok -> {},
                        err -> sendChunked(channel, line + "\n*(Failed to forward attachments)*")
                );
            }
        });
    }

    private CompletableFuture<List<FileUpload>> downloadAttachments(Message message) {
        return downloadAttachmentsFromList(message.getAttachments());
    }

    private CompletableFuture<List<FileUpload>> downloadAttachmentsFromList(List<Message.Attachment> attachments) {
        List<CompletableFuture<FileUpload>> futures = new ArrayList<>();
        for (Message.Attachment attachment : attachments) {
            CompletableFuture<FileUpload> future = new CompletableFuture<>();
            futures.add(future);
            attachment.getProxy().download().thenAccept(stream -> {
                try {
                    byte[] data = stream.readAllBytes();
                    future.complete(FileUpload.fromData(data, attachment.getFileName()));
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            }).exceptionally(ex -> {
                future.completeExceptionally(ex);
                return null;
            });
        }
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    List<FileUpload> uploads = new ArrayList<>();
                    for (CompletableFuture<FileUpload> f : futures) {
                        try {
                            FileUpload upload = f.getNow(null);
                            if (upload != null) uploads.add(upload);
                        } catch (Exception ignored) {
                        }
                    }
                    return uploads;
                })
                .exceptionally(ex -> List.of());
    }

    private void sendChunked(MessageChannel channel, String text) {
        if (text.length() <= DISCORD_MESSAGE_LIMIT) {
            channel.sendMessage(text).queue();
            return;
        }
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + DISCORD_MESSAGE_LIMIT, text.length());
            channel.sendMessage(text.substring(start, end)).queue();
            start = end;
        }
    }

    private String truncate(String text) {
        if (text.length() <= DISCORD_MESSAGE_LIMIT) return text;
        return text.substring(0, DISCORD_MESSAGE_LIMIT - 3) + "...";
    }

    private MessageChannel resolveChannel(ModmailSession session) {
        var channel = ServiceManager.getJda().getChannelById(MessageChannel.class, session.getChannelId());
        if (channel != null) return channel;
        ThreadChannel thread = ServiceManager.getJda().getThreadChannelById(session.getChannelId());
        if (thread != null) return thread;
        return ServiceManager.getJda().getTextChannelById(session.getChannelId());
    }

    private ModmailSession insertSession(String userId, String guildId, String channelId,
                                         ModmailSession.Kind kind, String category) {
        String sql = "INSERT INTO ez_modmail_sessions (user_id, guild_id, channel_id, kind, status, created_at, category) VALUES (?, ?, ?, ?, 'OPEN', ?, ?)";
        try (Connection conn = DatabaseService.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            long now = System.currentTimeMillis();
            ps.setString(1, userId);
            ps.setString(2, guildId);
            ps.setString(3, channelId);
            ps.setString(4, kind.name());
            ps.setLong(5, now);
            ps.setString(6, category);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                int id = keys.next() ? keys.getInt(1) : 0;
                return new ModmailSession(id, userId, guildId, channelId, kind, ModmailSession.Status.OPEN, now, null, category);
            }
        } catch (Exception e) {
            logger.error("[Modmail] Failed to insert session: {}", e.getMessage());
            return null;
        }
    }

    private ModmailSession mapRow(ResultSet rs) throws Exception {
        Long closedAt = rs.getObject("closed_at") != null ? rs.getLong("closed_at") : null;
        return new ModmailSession(
                rs.getInt("id"),
                rs.getString("user_id"),
                rs.getString("guild_id"),
                rs.getString("channel_id"),
                ModmailSession.Kind.valueOf(rs.getString("kind")),
                ModmailSession.Status.valueOf(rs.getString("status")),
                rs.getLong("created_at"),
                closedAt,
                rs.getString("category")
        );
    }

    private String sanitizeChannelName(String name) {
        String cleaned = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]", "-").replaceAll("-+", "-");
        if (cleaned.length() > 80) cleaned = cleaned.substring(0, 80);
        if (cleaned.isBlank()) cleaned = "user";
        return cleaned;
    }
}
