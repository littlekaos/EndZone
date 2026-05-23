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

public class UntimeoutCommand implements Command {
    private final EndZone bot;
    private final DataService dataService;

    public UntimeoutCommand(EndZone bot) {
        this.bot = bot;
        this.dataService = ServiceManager.getDataService();
    }

    @Override
    public List<CommandData> getCommandDataList() {
        return List.of(Commands.slash("untimeout", "Remove a timeout from a user")
                .addOption(OptionType.USER, "user", "The user to untimeout", true)
                .addOption(OptionType.STRING, "reason", "The reason for removing the timeout", true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!PermissionUtils.isModerator(event.getMember(), ServiceManager.getConfig()) && !PermissionUtils.isSemiMod(event.getMember(), ServiceManager.getConfig())) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("You don't have permission to use this command.")).setEphemeral(true).queue();
            return;
        }

        event.deferReply(true).queue();

        User targetUser = event.getOption("user").getAsUser();
        String reason = event.getOption("reason").getAsString();

        event.getGuild().retrieveMemberById(targetUser.getId()).queue(targetMember -> {
            if (!event.getMember().canInteract(targetMember)) {
                event.getHook().sendMessage("❌ You cannot remove the timeout from this user due to role hierarchy.").queue();
                return;
            }

            if (!event.getGuild().getSelfMember().canInteract(targetMember)) {
                event.getHook().sendMessage("❌ I cannot remove the timeout from this user due to role hierarchy.").queue();
                return;
            }

            if (targetMember.getTimeOutEnd() == null) {
                event.getHook().sendMessage("❌ This user is not currently timed out.").queue();
                return;
            }

            targetMember.removeTimeout().reason(reason).queue(success -> {
                dataService.saveModAction(ModAction.ActionType.UNTIMEOUT, event.getUser().getId(), event.getUser().getName(),
                        targetUser.getId(), targetUser.getName(), reason, event.getGuild().getName(), event.getChannel().getName(), 0, 0);

                EmbedBuilder untimeoutEmbed = new EmbedBuilder()
                        .setTitle("⏰ Timeout Removed")
                        .setDescription(String.format("Timeout has been removed from %s (%s)\n", targetUser.getAsMention(), targetUser.getName()))
                        .addField("User ID", targetUser.getId(), false)
                        .addField("Reason", reason, false)
                        .addField("Moderator", event.getUser().getName(), false)
                        .setColor(Color.GREEN)
                        .setTimestamp(Instant.now())
                        .setThumbnail(targetUser.getEffectiveAvatarUrl());

                ServiceManager.getLoggingService().logAction(event.getGuild(), "moderation-logs", untimeoutEmbed.build());

                event.getHook().sendMessage("✅ User's timeout has been removed successfully.").queue();
            }, error -> {
                event.getHook().sendMessage("❌ Error: Could not remove the timeout. " + error.getMessage()).queue();
            });
        }, error -> event.getHook().sendMessage("❌ Error: Cannot find the user in this server.").queue());
    }
}
