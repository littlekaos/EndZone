package EndZone.commands;

import EndZone.EndZone;
import EndZone.config.BotConfig;
import EndZone.services.BlacklistService;
import EndZone.services.ServiceManager;
import EndZone.utils.PermissionUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

import java.awt.Color;
import java.time.Instant;
import java.util.List;

public class BlacklistCommand implements Command {
    private final EndZone bot;
    private final BlacklistService blacklistService;

    public BlacklistCommand(EndZone bot) {
        this.bot = bot;
        this.blacklistService = ServiceManager.getBlacklistService();
    }

    @Override
    public List<CommandData> getCommandDataList() {
        return List.of(Commands.slash("blacklist", "Manage the global user blacklist")
                .addSubcommands(
                        new SubcommandData("add", "Add a user to the blacklist")
                                .addOption(OptionType.STRING, "user", "The User ID or mention to blacklist", true)
                                .addOption(OptionType.STRING, "reason", "The reason for blacklisting", true),
                        new SubcommandData("remove", "Remove a user from the blacklist")
                                .addOption(OptionType.STRING, "user_id", "The ID of the user to remove", true)
                ));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!PermissionUtils.isAlphaBetaOrHigher(event.getMember(), ServiceManager.getConfig()) &&
            !event.getUser().getId().equals(BotConfig.OWNER_USER_ID)) {
            event.reply("❌ You do not have permission to use this command. Only Alpha Beta+ can use this command.").setEphemeral(true).queue();
            return;
        }

        event.deferReply(true).queue();
        String subcommand = event.getSubcommandName();

        if ("add".equals(subcommand)) {
            String userInput = event.getOption("user").getAsString();
            String reason = event.getOption("reason").getAsString();

            String userId = userInput.replaceAll("[^0-9]", "");

            if (userId.isEmpty()) {
                event.getHook().sendMessage("❌ Invalid user or ID provided.").setEphemeral(true).queue();
                return;
            }

            if (blacklistService.isBlacklisted(userId)) {
                event.getHook().sendMessage("❌ That user is already blacklisted.").setEphemeral(true).queue();
                return;
            }

            if (userId.equals(event.getJDA().getSelfUser().getId())) {
                event.getHook().sendMessage("❌ You cannot blacklist the bot itself.").setEphemeral(true).queue();
                return;
            }

            blacklistService.blacklistUser(userId, reason, event.getUser().getId());

            event.getJDA().retrieveUserById(userId).queue(user -> {
                EmbedBuilder embed = new EmbedBuilder()
                        .setTitle("🚫 User Blacklisted")
                        .setDescription(String.format("**%s** (%s) has been added to the global blacklist.", user.getAsMention(), user.getId()))
                        .addField("Reason", reason, false)
                        .addField("Moderator", event.getUser().getAsMention(), false)
                        .setColor(Color.RED)
                        .setTimestamp(Instant.now());

                event.getHook().sendMessageEmbeds(embed.build()).queue();

                // Log to moderation logs
                ServiceManager.getLoggingService().logAction(event.getGuild(), "moderation-logs", embed.build());
            }, error -> {
                EmbedBuilder embed = new EmbedBuilder()
                        .setTitle("🚫 User Blacklisted")
                        .setDescription(String.format("User ID `%s` has been added to the global blacklist.", userId))
                        .addField("Reason", reason, false)
                        .addField("Moderator", event.getUser().getAsMention(), false)
                        .setColor(Color.RED)
                        .setTimestamp(Instant.now());

                event.getHook().sendMessageEmbeds(embed.build()).queue();

                // Log to moderation logs
                ServiceManager.getLoggingService().logAction(event.getGuild(), "moderation-logs", embed.build());
            });
            
        } else if ("remove".equals(subcommand)) {
            String userId = event.getOption("user_id").getAsString();

            if (!blacklistService.isBlacklisted(userId)) {
                event.getHook().sendMessage("❌ That user is not blacklisted.").setEphemeral(true).queue();
                return;
            }

            blacklistService.unblacklistUser(userId);

            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("✅ User Unblacklisted")
                    .setDescription(String.format("User ID `%s` has been removed from the global blacklist.", userId))
                    .setColor(Color.GREEN)
                    .setTimestamp(Instant.now());

            event.getHook().sendMessageEmbeds(embed.build()).setEphemeral(true).queue();
            
            // Log to moderation logs
            ServiceManager.getLoggingService().logAction(event.getGuild(), "moderation-logs", embed.build());
        }
    }
}
