package EndZone.events;

import EndZone.EndZone;
import EndZone.config.BotConfig;
import EndZone.services.DataService;
import EndZone.services.ServiceManager;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ReactionEventListener extends ListenerAdapter {
    private final EndZone bot;
    private final ScheduledExecutorService scheduler;
    private final String reactionRoleId;

    public ReactionEventListener(EndZone bot) {
        this.bot = bot;
        this.reactionRoleId = ServiceManager.getConfig().getStaffStrikesRoleId();
        this.scheduler = Executors.newScheduledThreadPool(1);
    }

    @Override
    public void onMessageReactionAdd(MessageReactionAddEvent event) {
        if (event.getUser() == null || event.getUser().isBot()) {
            return;
        }

        // Handle any reaction in staff verify channel
        if (event.getChannel().getId().equals(BotConfig.STAFF_VERIFY_CHANNEL_ID)) {
            event.getGuild().retrieveMemberById(event.getUserId()).queue(member -> {
                addStreamerHostRole(member, () -> {
                    scheduler.schedule(
                        () -> {
                            // Re-retrieve member to ensure we have the most up-to-date role list before removal
                            event.getGuild().retrieveMemberById(event.getUserId()).queue(
                                    updatedMember -> removeTrialSentinelRole(updatedMember),
                                    error -> System.err.println("Failed to retrieve member for trial sentinel removal: " + error.getMessage())
                            );
                        },
                        10,
                        TimeUnit.SECONDS
                    );
                });
            }, error -> System.err.println("Failed to retrieve member for streamer host assignment: " + error.getMessage()));
            return;
        }

        // Check for multiple reactions on the verification-reaction-roles message
        if (event.getMessageId().equals(BotConfig.VERIFICATION_REACTION_ROLES_MESSAGE_ID)) {
            checkMultiReaction(event);
        }

        // Handle winner role claim via reaction
        handleWinnerRoleClaim(event);
    }

    private void handleWinnerRoleClaim(MessageReactionAddEvent event) {
        // Check if the reaction is the EZ emoji
        if (!event.getReaction().getEmoji().getName().equals(BotConfig.EZ_EMOJI_NAME)) {
            return;
        }

        // Check if message is a winner claim message
        List<DataService.WinnerMessageEntry> entries = ServiceManager.getDataService().getAllWinnerMessages();
        boolean isWinnerMessage = entries.stream().anyMatch(e -> e.messageId().equals(event.getMessageId()));

        if (isWinnerMessage) {
            Role winnerRole = event.getGuild().getRoleById(BotConfig.WINNER_ROLE_ID);
            if (winnerRole != null) {
                event.getGuild().addRoleToMember(event.getMember(), winnerRole).queue(
                        success -> {
                            // Optionally notify user or just keep it silent
                        },
                        error -> System.err.println("Failed to give winner role via reaction: " + error.getMessage())
                );
            }
        }
    }

    private void checkMultiReaction(MessageReactionAddEvent event) {
        event.getChannel().retrieveMessageById(event.getMessageId()).queue(message -> {
            List<MessageReaction> reactions = message.getReactions();
            if (reactions.size() <= 1) return;

            AtomicInteger count = new AtomicInteger(0);
            List<CompletableFuture<?>> futures = new ArrayList<>();
            
            for (MessageReaction reaction : reactions) {
                CompletableFuture<?> future = reaction.retrieveUsers().forEachAsync(user -> {
                    if (user.getId().equals(event.getUserId())) {
                        count.incrementAndGet();
                        return false; // Found the user, stop iterating this reaction
                    }
                    return true; // Continue searching
                });
                futures.add(future);
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .thenRun(() -> {
                        int finalCount = count.get();
                        if (finalCount > 1) {
                            // Remove the NEW reaction (the one that triggered this event)
                            event.getReaction().removeReaction(event.getUser()).queue();
                            notifyStaffOfMultiReaction(event, finalCount);
                        }
                    });
        });
    }

    private void notifyStaffOfMultiReaction(MessageReactionAddEvent event, int count) {
        String notificationChannelId = BotConfig.STAFF_NOTIFICATION_CHANNEL_ID;
        TextChannel notificationChannel = event.getJDA().getTextChannelById(notificationChannelId);
        if (notificationChannel == null) {
            System.err.println("Staff notification channel with ID " + notificationChannelId + " not found!");
            return;
        }

        String adminPlusPing1 = "<@&" + BotConfig.BRULPH_ROLE_ID + ">";
        String adminPlusPing2 = "<@&" + BotConfig.MASTER_ALPHA_ROLE_ID + ">";

        User user = event.getUser();
        String userMention = user != null ? user.getAsMention() : "Unknown User";
        String userId = event.getUserId();
        String channelId = event.getChannel().getId();
        String guildId = event.getGuild().getId();
        String messageId = event.getMessageId();

        String alertMessage = String.format("%s %s\n" +
                "⚠️ **Multi-Reaction Alert**\n" +
                "User: %s (`%s`)\n" +
                "Channel: <#%s>\n" +
                "Message: [Jump to Message](https://discord.com/channels/%s/%s/%s)\n" +
                "Status: **Automatic Removal Processed**\n" +
                "Action: User attempted to grab a second role. The bot has removed their **new** reaction and kept their original one. They should be blacklisted if they continue.",
                adminPlusPing1, adminPlusPing2,
                userMention, userId,
                channelId,
                guildId, channelId, messageId);

        notificationChannel.sendMessage(alertMessage).queue();
    }

    @Override
    public void onMessageReactionRemove(MessageReactionRemoveEvent event) {
        if (event.getChannel().getId().equals(BotConfig.STAFF_VERIFY_CHANNEL_ID)) {
            event.getGuild().retrieveMemberById(event.getUserId()).queue(
                    member -> removeStreamerHostRole(member),
                    error -> System.err.println("Failed to retrieve member for trial sentinel reaction removal: " + error.getMessage())
            );
        }
    }

    private void addStreamerHostRole(Member member, Runnable onSuccess) {
        if (member == null) return;

        // Check if user is demoted
        if (ServiceManager.getDemotionService().isDemoted(member.getId())) {
            System.out.println("User " + member.getUser().getName() + " is demoted, skipping streamer host role assignment.");
            return;
        }

        Role role = member.getGuild().getRoleById(BotConfig.STREAMER_HOSTS_ROLE_ID);
        if (role == null) {
            System.err.println("Streamer host role with ID " + BotConfig.STREAMER_HOSTS_ROLE_ID + " not found!");
            return;
        }

        if (member.getRoles().contains(role)) {
            if (onSuccess != null) onSuccess.run();
            return;
        }

        member.getGuild().addRoleToMember(member, role).queue(
                success -> {
                    System.out.println("Successfully added streamer host role to " + member.getUser().getName());
                    if (onSuccess != null) onSuccess.run();
                },
                error -> System.err.println("Failed to add streamer host role to " + member.getUser().getName() + ": " + error.getMessage())
        );
    }

    private void removeTrialSentinelRole(Member member) {
        if (member == null) return;

        Role role = member.getGuild().getRoleById(BotConfig.TRIAL_SENTINELS_ROLE_ID);
        if (role == null) {
            System.err.println("Trial sentinel role with ID " + BotConfig.TRIAL_SENTINELS_ROLE_ID + " not found!");
            return;
        }

        if (!member.getRoles().contains(role)) return;

        member.getGuild().removeRoleFromMember(member, role).queue(
                success -> System.out.println("Successfully removed trial sentinel role from " + member.getUser().getName()),
                error -> System.err.println("Failed to remove trial sentinel role from " + member.getUser().getName() + ": " + error.getMessage())
        );
    }

    private void removeStreamerHostRole(Member member) {
        if (member == null) return;

        Role role = member.getGuild().getRoleById(BotConfig.STREAMER_HOSTS_ROLE_ID);
        if (role == null) {
            System.err.println("Streamer host role with ID " + BotConfig.STREAMER_HOSTS_ROLE_ID + " not found!");
            return;
        }

        if (!member.getRoles().contains(role)) return;

        member.getGuild().removeRoleFromMember(member, role).queue(
                success -> System.out.println("Successfully removed streamer host role from " + member.getUser().getName()),
                error -> System.err.println("Failed to remove streamer host role from " + member.getUser().getName() + ": " + error.getMessage())
        );
    }
}
