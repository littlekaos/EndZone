package EndZone.commands;

import EndZone.EndZone;
import EndZone.models.ModAction;
import EndZone.services.DataService;
import EndZone.services.ServiceManager;
import EndZone.utils.EmbedUtils;
import EndZone.utils.PermissionUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.awt.Color;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class BanCommand implements Command {
    private final EndZone bot;
    private final DataService dataService;

    public BanCommand(EndZone bot) {
        this.bot = bot;
        this.dataService = ServiceManager.getDataService();
    }

    @Override
    public List<CommandData> getCommandDataList() {
        return List.of(Commands.slash("ban", "Ban a user from the server")
                .addOption(OptionType.USER, "user", "The user to ban", true)
                .addOption(OptionType.STRING, "reason", "The reason for the ban", true)
                .addOption(OptionType.INTEGER, "delete_days", "Number of days of messages to delete (0-7, defaults to 1)", false));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!PermissionUtils.isAdminPlus(event.getMember(), ServiceManager.getConfig()) || PermissionUtils.isCourtModOnly(event.getMember(), ServiceManager.getConfig())) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("You don't have permission to use this command.")).setEphemeral(true).queue();
            return;
        }

        User targetUser = event.getOption("user").getAsUser();
        String reason = event.getOption("reason").getAsString();
        int deleteDays = event.getOption("delete_days") != null ? event.getOption("delete_days").getAsInt() : 1;

        if (deleteDays < 0 || deleteDays > 7) {
            event.reply("❌ Error: delete_days must be between 0 and 7").setEphemeral(true).queue();
            return;
        }

        event.deferReply().setEphemeral(true).queue();
        
        ServiceManager.getJda().retrieveUserById(targetUser.getId()).queue(user -> {
            event.getGuild().retrieveMemberById(targetUser.getId()).queue(targetMember -> {
                if (!PermissionUtils.canModerate(event.getMember(), targetMember)) {
                    event.getHook().editOriginal("❌ You cannot ban this user due to role hierarchy.").queue();
                    return;
                }

                if (!event.getGuild().getSelfMember().canInteract(targetMember)) {
                    event.getHook().editOriginal("❌ I cannot ban this user due to role hierarchy.").queue();
                    return;
                }
                doBanManual(event, user, reason, deleteDays);
            }, error -> doBanManual(event, user, reason, deleteDays));
        }, error -> event.getHook().editOriginal("❌ Error: User not found.").queue());
    }

    private void doBanManual(SlashCommandInteractionEvent event, User targetUser, String reason, int deleteDays) {
        event.getGuild().ban(targetUser, deleteDays, TimeUnit.DAYS).reason(reason).queue(success -> {
            dataService.saveModAction(ModAction.ActionType.BAN, event.getUser().getId(), event.getUser().getName(),
                    targetUser.getId(), targetUser.getName(), reason, event.getGuild().getName(), event.getChannel().getName(), deleteDays, 0);

            EmbedBuilder banEmbed = new EmbedBuilder()
                    .setTitle("🔨 User Banned")
                    .setDescription(String.format("%s (%s) has been banned from the server\n", targetUser.getAsMention(), targetUser.getName()))
                    .addField("User ID", targetUser.getId(), false)
                    .addField("Reason", reason, false)
                    .addField("Message History Deleted", deleteDays + " days", false)
                    .addField("Moderator", event.getUser().getName(), false)
                    .setColor(Color.RED)
                    .setTimestamp(Instant.now())
                    .setThumbnail(targetUser.getEffectiveAvatarUrl());

            ServiceManager.getLoggingService().logAction(event.getGuild(), "moderation-logs", banEmbed.build());

            event.getHook().editOriginal("✅ User has been banned successfully.").setEmbeds().setComponents().queue();
        }, error -> {
            event.getHook().editOriginal("❌ Error: Could not ban the user. " + error.getMessage()).setEmbeds().setComponents().queue();
        });
    }
}
