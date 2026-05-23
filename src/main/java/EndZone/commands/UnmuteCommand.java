package EndZone.commands;

import EndZone.EndZone;
import EndZone.config.BotConfig;
import EndZone.models.ModAction;
import EndZone.services.DataService;
import EndZone.services.ServiceManager;
import EndZone.utils.EmbedUtils;
import EndZone.utils.PermissionUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.awt.Color;
import java.time.Instant;
import java.util.List;

public class UnmuteCommand implements Command {
    private final EndZone bot;
    private final DataService dataService;

    public UnmuteCommand(EndZone bot) {
        this.bot = bot;
        this.dataService = ServiceManager.getDataService();
    }

    @Override
    public List<CommandData> getCommandDataList() {
        return List.of(Commands.slash("unmute", "Unmute a user")
                .addOption(OptionType.USER, "user", "The user to unmute", true)
                .addOption(OptionType.STRING, "reason", "The reason for the unmute", true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!PermissionUtils.isModerator(event.getMember(), ServiceManager.getConfig())) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("You don't have permission to use this command.")).setEphemeral(true).queue();
            return;
        }

        User targetUser = event.getOption("user").getAsUser();
        String reason = event.getOption("reason").getAsString();

        Guild guild = event.getGuild();
        String muteRoleId = dataService.getMuteRoleId(guild.getId());

        if (muteRoleId == null || muteRoleId.isEmpty()) {
            muteRoleId = BotConfig.MUTE_ROLE_ID;
        }

        Role muteRole = guild.getRoleById(muteRoleId);
        if (muteRole == null) {
            event.reply("❌ The configured mute role no longer exists.").setEphemeral(true).queue();
            return;
        }

        event.deferReply().setEphemeral(true).queue();
        
        guild.retrieveMemberById(targetUser.getId()).queue(targetMember -> {
            if (!event.getMember().canInteract(targetMember)) {
                event.getHook().editOriginal("❌ You cannot unmute this user due to role hierarchy.").queue();
                return;
            }

            if (!targetMember.getRoles().contains(muteRole)) {
                event.getHook().editOriginal("❌ This user is not currently muted.").queue();
                return;
            }
            
            doUnmuteManual(event, targetUser.getId(), targetUser.getName(), reason, muteRole);
        }, error -> event.getHook().editOriginal("❌ Error: Cannot find the user in this server.").queue());
    }

    private void doUnmuteManual(SlashCommandInteractionEvent event, String targetId, String targetName, String reason, Role muteRole) {
        Guild guild = event.getGuild();
        guild.retrieveMemberById(targetId).queue(targetMember -> {
            guild.removeRoleFromMember(targetMember, muteRole).queue(success -> {
                dataService.removeMute(guild.getId(), targetId);
                dataService.saveModAction(ModAction.ActionType.UNMUTE, event.getUser().getId(), event.getUser().getName(),
                        targetId, targetName, reason, event.getGuild().getName(), event.getChannel().getName(), 0, 0);

                EmbedBuilder unmuteEmbed = new EmbedBuilder()
                        .setTitle("🔊 User Unmuted")
                        .setDescription(String.format("%s (%s) has been unmuted\n", targetMember.getAsMention(), targetName))
                        .addField("User ID", targetId, false)
                        .addField("Reason", reason, false)
                        .addField("Moderator", event.getUser().getName(), false)
                        .setColor(Color.GREEN)
                        .setTimestamp(Instant.now())
                        .setThumbnail(targetMember.getEffectiveAvatarUrl());

                ServiceManager.getLoggingService().logAction(guild, "moderation-logs", unmuteEmbed.build());

                event.getHook().editOriginal("✅ User has been unmuted successfully.").setEmbeds().setComponents().queue();
            }, error -> {
                event.getHook().editOriginal("❌ Error: Could not unmute the user. " + error.getMessage()).setEmbeds().setComponents().queue();
            });
        }, error -> event.getHook().editOriginal("❌ Error: Cannot find the user in this server.").setEmbeds().setComponents().queue());
    }
}
