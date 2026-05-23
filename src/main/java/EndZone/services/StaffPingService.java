package EndZone.services;

import EndZone.config.BotConfig;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class StaffPingService {
    private static final Logger logger = LoggerFactory.getLogger(StaffPingService.class);
    private JDA jda;
    private final ScheduledExecutorService scheduler;
    private final Random random;
    private final Set<Long> reactedStaffMembers;
    private long lastPingMessageId;

    private static final long GUILD_ID = Long.parseLong(BotConfig.GUILD_ID);
    private static final long GENERAL_CHANNEL_ID = Long.parseLong(BotConfig.GENERAL_CHAT_CHANNEL_ID);
    private static final long TRIAL_SENTINELS_ROLE_ID = Long.parseLong(BotConfig.TRIAL_SENTINELS_ROLE_ID);
    private static final String STAFF_VERIFY_CHANNEL_ID = BotConfig.STAFF_VERIFY_CHANNEL_ID;
    private static final String STREAMER_HOSTS_ROLE_ID = BotConfig.STREAMER_HOSTS_ROLE_ID;

    public StaffPingService(BotConfig config) {
        this.scheduler = Executors.newScheduledThreadPool(2);
        this.random = new Random();
        this.reactedStaffMembers = new HashSet<>();
        this.lastPingMessageId = 0;
    }

    public void startScheduledPing(JDA jda) {
        this.jda = jda;
        pingTrialSentinels();
        scheduleNextPing();
        scheduleReactionScanner();
        logger.info("Staff Ping Service started");
    }

    private void scheduleReactionScanner() {
        scheduler.scheduleAtFixedRate(this::scanForReactions, 1, 5, TimeUnit.MINUTES);
    }

    public void manualScan(Runnable onComplete) {
        scanForReactions(onComplete);
    }

    private void scanForReactions() {
        scanForReactions(null);
    }

    private void scanForReactions(Runnable onComplete) {
        if (jda == null) return;
        
        if (lastPingMessageId == 0) {
            if (onComplete != null) onComplete.run();
            return;
        }

        Guild guild = jda.getGuildById(GUILD_ID);
        if (guild == null) {
            if (onComplete != null) onComplete.run();
            return;
        }

        TextChannel generalChannel = guild.getTextChannelById(GENERAL_CHANNEL_ID);
        if (generalChannel == null) {
            if (onComplete != null) onComplete.run();
            return;
        }

        generalChannel.retrieveMessageById(lastPingMessageId).queue(
            message -> {
                List<java.util.concurrent.CompletableFuture<Void>> allFutures = new ArrayList<>();
                
                for (net.dv8tion.jda.api.entities.MessageReaction reaction : message.getReactions()) {
                    java.util.concurrent.CompletableFuture<Void> reactionFuture = new java.util.concurrent.CompletableFuture<>();
                    
                    reaction.retrieveUsers().forEachAsync(user -> {
                        Member member = guild.getMemberById(user.getIdLong());
                        if (member != null) {
                            reactedStaffMembers.add(member.getIdLong());
                        }
                        return true;
                    }).whenComplete((v, error) -> {
                        if (error != null) {
                            logger.error("Error processing reaction users: {}", error.getMessage());
                        }
                        reactionFuture.complete(null);
                    });
                    
                    allFutures.add(reactionFuture);
                }
                
                java.util.concurrent.CompletableFuture.allOf(allFutures.toArray(new java.util.concurrent.CompletableFuture[0]))
                    .whenComplete((v, error) -> {
                        if (onComplete != null) onComplete.run();
                    });
            },
            error -> {
                if (onComplete != null) onComplete.run();
            }
        );
    }

    private void scheduleNextPing() {
        scheduler.schedule(this::executePing, 3, TimeUnit.HOURS);
    }

    private void executePing() {
        pingTrialSentinels();
        scheduleNextPing();
    }

    private void pingTrialSentinels() {
        if (jda == null) {
            logger.error("JDA is not initialized!");
            return;
        }

        Guild guild = jda.getGuildById(GUILD_ID);
        if (guild == null) {
            logger.error("Guild not found!");
            return;
        }

        TextChannel generalChannel = guild.getTextChannelById(GENERAL_CHANNEL_ID);
        if (generalChannel == null) {
            logger.error("General channel not found in guild: {}", guild.getName());
            return;
        }

        if (lastPingMessageId != 0) {
            generalChannel.deleteMessageById(lastPingMessageId).queue(
                success -> logger.info("Previous ping message deleted!"),
                error -> {}
            );
        }

        net.dv8tion.jda.api.entities.Role trialRole = guild.getRoleById(TRIAL_SENTINELS_ROLE_ID);

        if (trialRole == null) {
            logger.error("Trial Sentinels role not found!");
            return;
        }

        guild.findMembers((member) -> member.getRoles().contains(trialRole)).onSuccess(staffMembers -> {
            if (staffMembers.isEmpty()) {
                logger.warn("No Trial Sentinels found with the role!");
                return;
            }

            List<Member> unreactedMembers = staffMembers.stream()
                .filter(member -> !reactedStaffMembers.contains(member.getIdLong()))
                .toList();

            if (unreactedMembers.isEmpty()) {
                reactedStaffMembers.clear();
                unreactedMembers = staffMembers;
            }

            List<Member> finalUnreactedMembers = unreactedMembers;
            String mentionsNewline = String.join("\n", finalUnreactedMembers.stream()
                .map(Member::getAsMention)
                .toList());
            
            String mentionsSpace = String.join(" ", finalUnreactedMembers.stream()
                .map(Member::getAsMention)
                .toList());
            
            EmbedBuilder embed = new EmbedBuilder()
                .setTitle("People Who Need To React For Streamer Host")
                .setColor(new Color(0, 200, 255))
                .setDescription(mentionsNewline + "\n\nPlease react in <#" + STAFF_VERIFY_CHANNEL_ID + "> for the <@&" + STREAMER_HOSTS_ROLE_ID + "> role.\n");
            
            generalChannel.sendMessageEmbeds(embed.build()).queue(
                success -> {
                    generalChannel.sendMessage(mentionsSpace).queue(
                        pingMessage -> {
                            lastPingMessageId = pingMessage.getIdLong();
                            scheduler.schedule(() -> scanForReactions(), 8, TimeUnit.SECONDS);
                            scheduler.schedule(() -> pingMessage.delete().queue(
                                deleteSuccess -> {
                                    lastPingMessageId = 0;
                                },
                                deleteError -> logger.error("Failed to delete ping message: {}", deleteError.getMessage())
                            ), 10, TimeUnit.SECONDS);
                        },
                        pingError -> logger.error("Failed to send auto-delete ping message: {}", pingError.getMessage())
                    );
                },
                error -> logger.error("Failed to send staff ping message: {}", error.getMessage())
            );
        });
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
        logger.info("Staff Ping Service shut down");
    }
}
