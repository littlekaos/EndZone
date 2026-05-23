package EndZone.services;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class BanService {
    private static final Logger logger = LoggerFactory.getLogger(BanService.class);

    public void banUser(
            Guild guild,
            User targetUser,
            String reason,
            TextChannel modLogChannel,
            User moderatorUser,
            Consumer<Void> onSuccess,
            Consumer<Throwable> onError) {
        try {
            guild.ban(targetUser, 0, TimeUnit.DAYS)
                    .reason(reason)
                    .queue(
                            success -> {
                                logBanAction(modLogChannel, moderatorUser, targetUser, reason);
                                onSuccess.accept(success);
                                logger.info("User {} banned by {} for: {}", targetUser.getId(), moderatorUser.getId(), reason);
                            },
                            error -> {
                                onError.accept(error);
                                logger.error("Failed to ban user {}: {}", targetUser.getId(), error.getMessage());
                            }
                    );
        } catch (Exception e) {
            onError.accept(e);
            logger.error("Error during ban process: {}", e.getMessage());
        }
    }

    public void unbanUser(Guild guild, String userIdToUnban, TextChannel modLogChannel,
                          User moderatorUser, Consumer<Void> onSuccess,
                          Consumer<Throwable> onError, Runnable onUserNotBanned) {
        try {
            guild.retrieveBan(UserSnowflake.fromId(userIdToUnban)).queue(
                    ban -> {
                        guild.unban(UserSnowflake.fromId(userIdToUnban)).queue(
                                success -> {
                                    logUnbanAction(modLogChannel, moderatorUser, userIdToUnban);
                                    onSuccess.accept(success);
                                    logger.info("User {} unbanned by {}", userIdToUnban, moderatorUser.getId());
                                },
                                error -> {
                                    onError.accept(error);
                                    logger.error("Failed to unban user {}: {}", userIdToUnban, error.getMessage());
                                }
                        );
                    },
                    error -> {
                        onUserNotBanned.run();
                        logger.info("User {} not found in ban list", userIdToUnban);
                    }
            );
        } catch (Exception e) {
            onError.accept(e);
            logger.error("Error during unban process: {}", e.getMessage());
        }
    }

    public CompletableFuture<Boolean> isUserBanned(Guild guild, String userId) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        guild.retrieveBan(UserSnowflake.fromId(userId)).queue(
                ban -> future.complete(true),
                error -> future.complete(false)
        );

        return future;
    }

    private void logBanAction(TextChannel modLogChannel, User moderator, User target, String reason) {
        if (modLogChannel != null) {
            modLogChannel.sendMessage(moderator.getAsMention() + " (`" + moderator.getId() +
                    "`) **banned a user**:\n> User: " + target.getName() + " (`" + target.getId() +
                    "`)\n> Reason: " + reason).queue();
        }
    }

    private void logUnbanAction(TextChannel modLogChannel, User moderator, String targetId) {
        if (modLogChannel != null) {
            modLogChannel.sendMessage(moderator.getAsMention() + " (`" + moderator.getId() +
                    "`) **unbanned a user**:\n> User: (`" + targetId + "`)").queue();
        }
    }
}
