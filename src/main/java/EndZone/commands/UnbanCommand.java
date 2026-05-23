package EndZone.commands;

import EndZone.EndZone;
import EndZone.models.ModAction;
import EndZone.services.DataService;
import EndZone.services.ServiceManager;
import EndZone.utils.EmbedUtils;
import EndZone.utils.PermissionUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.awt.Color;
import java.time.Instant;
import java.util.List;

public class UnbanCommand implements Command {
    private final EndZone bot;
    private final DataService dataService;

    public UnbanCommand(EndZone bot) {
        this.bot = bot;
        this.dataService = ServiceManager.getDataService();
    }

    @Override
    public List<CommandData> getCommandDataList() {
        return List.of(Commands.slash("unban", "Unban a user via their Discord ID")
                .addOption(OptionType.STRING, "user_id", "The ID of the user to unban", true)
                .addOption(OptionType.STRING, "reason", "The reason for the unban", true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!PermissionUtils.isAdminPlus(event.getMember(), ServiceManager.getConfig())) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("You don't have permission to use this command.")).setEphemeral(true).queue();
            return;
        }

        String userId = event.getOption("user_id").getAsString();
        String reason = event.getOption("reason").getAsString();

        if (!userId.matches("\\d{17,19}")) {
            event.reply("❌ Invalid user ID format.").setEphemeral(true).queue();
            return;
        }

        event.deferReply().setEphemeral(true).queue();
        doUnbanManual(event, userId, reason);
    }

    private void doUnbanManual(SlashCommandInteractionEvent event, String userId, String reason) {
        Guild guild = event.getGuild();

        guild.retrieveBanList().queue(banList -> {
            Guild.Ban ban = banList.stream()
                    .filter(b -> b.getUser().getId().equals(userId))
                    .findFirst()
                    .orElse(null);

            if (ban == null) {
                event.getHook().editOriginal("❌ User with ID `" + userId + "` is not banned from this server.").setEmbeds().setComponents().queue();
                return;
            }

            User bannedUser = ban.getUser();
            guild.unban(bannedUser).reason(reason).queue(success -> {
                dataService.saveModAction(ModAction.ActionType.UNBAN, event.getUser().getId(), event.getUser().getName(),
                        userId, bannedUser.getName(), reason, event.getGuild().getName(), event.getChannel().getName(), 0, 0);

                EmbedBuilder unbanEmbed = new EmbedBuilder()
                        .setTitle("🔓 User Unbanned")
                        .setDescription(String.format("%s (%s) has been unbanned\n", bannedUser.getAsMention(), bannedUser.getName()))
                        .addField("User ID", userId, false)
                        .addField("Reason", reason, false)
                        .addField("Moderator", event.getUser().getName(), false)
                        .setColor(Color.GREEN)
                        .setTimestamp(Instant.now())
                        .setThumbnail(bannedUser.getEffectiveAvatarUrl());

                ServiceManager.getLoggingService().logAction(guild, "moderation-logs", unbanEmbed.build());

                event.getHook().editOriginal("✅ User **" + bannedUser.getName() + "** has been unbanned successfully.").setEmbeds().setComponents().queue();
            }, error -> {
                event.getHook().editOriginal("❌ Error: Could not unban the user. " + error.getMessage()).setEmbeds().setComponents().queue();
            });
        }, error -> {
            event.getHook().editOriginal("❌ Error: Could not retrieve ban list. " + error.getMessage()).setEmbeds().setComponents().queue();
        });
    }
}
