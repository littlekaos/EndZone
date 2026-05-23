package EndZone.commands;

import EndZone.EndZone;
import EndZone.config.BotConfig;
import EndZone.models.ModAction;
import EndZone.services.DataService;
import EndZone.services.ServiceManager;
import EndZone.utils.EmbedUtils;
import EndZone.utils.FormatUtils;
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

public class MuteCommand implements Command {
    private final EndZone bot;
    private final DataService dataService;

    public MuteCommand(EndZone bot) {
        this.bot = bot;
        this.dataService = ServiceManager.getDataService();
    }

    @Override
    public List<CommandData> getCommandDataList() {
        return List.of(Commands.slash("mute", "Mute a user")
                .addOption(OptionType.USER, "user", "The user to mute", true)
                .addOption(OptionType.STRING, "reason", "The reason for the mute", true)
                .addOption(OptionType.STRING, "duration", "Duration (e.g., 1h, 7d, 30m)", false));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!PermissionUtils.isModerator(event.getMember(), ServiceManager.getConfig())) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("You don't have permission to use this command.")).setEphemeral(true).queue();
            return;
        }

        User targetUser = event.getOption("user").getAsUser();
        String reason = event.getOption("reason").getAsString();
        String durationStr = event.getOption("duration") != null ? event.getOption("duration").getAsString() : null;

        Guild guild = event.getGuild();
        String muteRoleId = dataService.getMuteRoleId(guild.getId());

        if (muteRoleId == null || muteRoleId.isEmpty()) {
            muteRoleId = BotConfig.MUTE_ROLE_ID;
        }

        Role muteRole = guild.getRoleById(muteRoleId);
        if (muteRole == null) {
            event.reply("❌ The configured mute role no longer exists. Please use `/setmuterole` to set a new one.").setEphemeral(true).queue();
            return;
        }

        long parsedDuration = FormatUtils.parseDurationToMinutes(durationStr);
        final Integer duration = (durationStr != null && parsedDuration > 0) ? (int) parsedDuration : null;

        event.deferReply().setEphemeral(true).queue();
        
        event.getGuild().retrieveMemberById(targetUser.getId()).queue(targetMember -> {
            if (!PermissionUtils.canModerate(event.getMember(), targetMember)) {
                event.getHook().editOriginal("❌ You cannot mute this user due to role hierarchy.").queue();
                return;
            }
            
            if (!event.getGuild().getSelfMember().canInteract(targetMember)) {
                event.getHook().editOriginal("❌ I cannot mute this user due to role hierarchy.").queue();
                return;
            }
            
            doMuteManual(event, targetUser.getId(), targetUser.getName(), reason, duration, muteRole);
        }, error -> event.getHook().editOriginal("❌ Error: Cannot find the user in this server.").queue());
    }

    private void doMuteManual(SlashCommandInteractionEvent event, String targetId, String targetName, String reason, Integer duration, Role muteRole) {
        Guild guild = event.getGuild();
        guild.retrieveMemberById(targetId).queue(targetMember -> {
            guild.addRoleToMember(targetMember, muteRole).queue(success -> {
                String durationText = duration != null ? duration + " minutes" : "Permanent";
                dataService.saveModAction(ModAction.ActionType.MUTE, event.getUser().getId(), event.getUser().getName(),
                        targetId, targetName, reason, event.getGuild().getName(), event.getChannel().getName(), duration != null ? duration : 0, 0);

                long unmuteTime = (duration != null) ? System.currentTimeMillis() + (duration * 60 * 1000L) : 0;
                dataService.addMute(guild.getId(), targetId, unmuteTime);

                EmbedBuilder muteEmbed = new EmbedBuilder()
                        .setTitle("🔇 User Muted")
                        .setDescription(String.format("%s (%s) has been muted\n", targetMember.getAsMention(), targetName))
                        .addField("User ID", targetId, false)
                        .addField("Reason", reason, false)
                        .addField("Duration", durationText, false)
                        .addField("Moderator", event.getUser().getName(), false)
                        .setColor(new Color(128, 0, 128))
                        .setTimestamp(Instant.now())
                        .setThumbnail(targetMember.getEffectiveAvatarUrl());

                ServiceManager.getLoggingService().logAction(guild, "moderation-logs", muteEmbed.build());

                event.getHook().editOriginal("✅ User has been muted successfully.").setEmbeds().setComponents().queue();
            }, error -> {
                event.getHook().editOriginal("❌ Error: Could not mute the user. " + error.getMessage()).setEmbeds().setComponents().queue();
            });
        }, error -> event.getHook().editOriginal("❌ Error: Cannot find the user in this server.").setEmbeds().setComponents().queue());
    }
}
