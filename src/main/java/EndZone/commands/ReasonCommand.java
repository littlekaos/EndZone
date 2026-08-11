package EndZone.commands;

import EndZone.EndZone;
import EndZone.config.BotConfig;
import EndZone.utils.EmbedUtils;
import EndZone.utils.PermissionUtils;
import EndZone.services.ServiceManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class ReasonCommand implements Command {
    private static final Logger logger = LoggerFactory.getLogger(ReasonCommand.class);

    private static final String COURT_GUILD_ID = BotConfig.COURT_GUILD_ID;
    private static final String MAIN_GUILD_ID = BotConfig.GUILD_ID;

    public ReasonCommand(EndZone bot) {
    }

    @Override
    public List<CommandData> getCommandDataList() {
        return List.of(Commands.slash("reason", "Check the ban reason for a user")
                .addOption(OptionType.STRING, "user", "The user ID to check", true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String userId = event.getOption("user").getAsString().trim();
        logger.info("[Reason] /reason called for user: {}", userId);

        if (!PermissionUtils.isAlphaBetaOrHigher(event.getMember(), ServiceManager.getConfig())) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("You don't have permission to use this command.")).setEphemeral(true).queue();
            return;
        }

        if (!userId.matches("\\d{17,20}")) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Invalid user ID. Please provide a Discord snowflake ID.")).setEphemeral(true).queue();
            return;
        }

        event.deferReply(true).queue();

        Guild mainGuild = event.getJDA().getGuildById(MAIN_GUILD_ID);
        Guild courtGuild = event.getJDA().getGuildById(COURT_GUILD_ID);

        List<BanInfo> bans = new ArrayList<>();
        AtomicInteger pending = new AtomicInteger(0);

        if (mainGuild != null) pending.incrementAndGet();
        if (courtGuild != null) pending.incrementAndGet();

        if (pending.get() == 0) {
            event.getHook().sendMessage("Error: Could not find EndZone or CourtZone guilds.").queue();
            return;
        }

        Runnable maybeFinish = () -> {
            if (pending.decrementAndGet() == 0) {
                sendResult(event, userId, bans);
            }
        };

        if (mainGuild != null) {
            lookupBan(mainGuild, userId, bans, maybeFinish);
        }
        if (courtGuild != null) {
            lookupBan(courtGuild, userId, bans, maybeFinish);
        }
    }

    private void lookupBan(Guild guild, String userId, List<BanInfo> bans, Runnable onDone) {
        guild.retrieveBan(UserSnowflake.fromId(userId)).queue(ban -> {
            String reason = ban.getReason();
            if (reason == null || reason.isBlank()) {
                reason = "No reason provided.";
            }
            synchronized (bans) {
                bans.add(new BanInfo(guild.getName(), reason));
            }
            onDone.run();
        }, error -> {
            logger.info("[Reason] No ban found for {} in {} ({})", userId, guild.getName(), error.getMessage());
            onDone.run();
        });
    }

    private void sendResult(SlashCommandInteractionEvent event, String userId, List<BanInfo> bans) {
        if (bans.isEmpty()) {
            EmbedBuilder empty = new EmbedBuilder()
                    .setTitle("Ban Reason")
                    .setDescription("**User:** <@" + userId + "> (`" + userId + "`)\n\n"
                            + "This user is not currently banned in EndZone or CourtZone.")
                    .setColor(new Color(100, 150, 255))
                    .setTimestamp(Instant.now());
            event.getHook().sendMessageEmbeds(empty.build()).queue();
            return;
        }

        StringBuilder body = new StringBuilder();
        body.append("**User:** <@").append(userId).append("> (`").append(userId).append("`)\n\n");

        for (BanInfo ban : bans) {
            body.append("🔨 **").append(ban.guildName).append("**\n");
            body.append("└─ **Reason:** ").append(escape(ban.reason)).append("\n\n");
        }

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Ban Reason")
                .setDescription(body.toString())
                .setColor(new Color(100, 150, 255))
                .setTimestamp(Instant.now());

        event.getHook().sendMessageEmbeds(embed.build()).queue();
    }

    private String escape(String text) {
        if (text == null) return "Unknown";
        return text.replace("_", "\\_").replace("*", "\\*");
    }

    private static class BanInfo {
        final String guildName;
        final String reason;

        BanInfo(String guildName, String reason) {
            this.guildName = guildName;
            this.reason = reason;
        }
    }
}
