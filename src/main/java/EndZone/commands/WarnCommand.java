package EndZone.commands;

import EndZone.EndZone;
import EndZone.models.ModAction;
// import EndZone.models.WarnRecord; // Skipped per user instruction
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

public class WarnCommand implements Command {
    private final EndZone bot;
    private final DataService dataService;

    public WarnCommand(EndZone bot) {
        this.bot = bot;
        this.dataService = ServiceManager.getDataService();
    }

    @Override
    public List<CommandData> getCommandDataList() {
        return List.of(Commands.slash("warn", "Issue a warning to a user")
                .addOption(OptionType.USER, "user", "The user to warn", true)
                .addOption(OptionType.STRING, "reason", "The reason for the warning", true)
                .addOption(OptionType.ATTACHMENT, "evidence", "Optional evidence attachment", false));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!PermissionUtils.isModerator(event.getMember(), ServiceManager.getConfig()) && !PermissionUtils.isSemiMod(event.getMember(), ServiceManager.getConfig())) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("You don't have permission to use this command.")).setEphemeral(true).queue();
            return;
        }

        User targetUser = event.getOption("user").getAsUser();
        String reason = event.getOption("reason").getAsString();
        net.dv8tion.jda.api.entities.Message.Attachment evidence = event.getOption("evidence") != null ?
                event.getOption("evidence").getAsAttachment() : null;

        event.deferReply().setEphemeral(true).queue();
        
        event.getGuild().retrieveMemberById(targetUser.getId()).queue(targetMember -> {
            if (!PermissionUtils.canModerate(event.getMember(), targetMember)) {
                event.getHook().editOriginal("❌ You cannot warn this user due to role hierarchy.").queue();
                return;
            }
            
            doWarnManual(event, targetUser.getId(), targetUser.getName(), reason, 
                    evidence != null ? evidence.getProxyUrl() : null, 
                    evidence != null ? evidence.getFileName() : null);
        }, error -> {
            doWarnManual(event, targetUser.getId(), targetUser.getName(), reason, 
                    evidence != null ? evidence.getProxyUrl() : null, 
                    evidence != null ? evidence.getFileName() : null);
        });
    }

    private void doWarnManual(SlashCommandInteractionEvent event, String targetId, String targetName, String reason, String evidenceUrl, String evidenceFileName) {
        int currentWarnings = 0; // Placeholder since WarnRecord is skipped
        try {
            currentWarnings = dataService.getHistory(targetId, List.of("WARN")).size();
        } catch (Exception ignored) {}

        dataService.saveModAction(ModAction.ActionType.WARN, event.getUser().getId(), event.getUser().getName(),
                targetId, targetName, reason, event.getGuild().getName(), event.getChannel().getName(), 0, currentWarnings);

        ServiceManager.getJda().retrieveUserById(targetId).queue(targetUser -> {
            EmbedBuilder warnEmbed = new EmbedBuilder()
                    .setTitle("⚠️ Warning Issued")
                    .setDescription(String.format("A warning has been issued to %s (%s)\n", targetUser.getAsMention(), targetUser.getName()))
                    .addField("User ID", targetId, false)
                    .addField("Reason", reason, false)
                    .addField("Moderator", event.getUser().getName(), false)
                    .setColor(Color.YELLOW)
                    .setTimestamp(Instant.now())
                    .setThumbnail(targetUser.getEffectiveAvatarUrl());

            targetUser.openPrivateChannel().queue(channel -> {
                EmbedBuilder userWarnEmbed = new EmbedBuilder()
                        .setTitle("⚠️ You've Received a Warning")
                        .setDescription(String.format("You have been warned in **%s**\n", event.getGuild().getName()))
                        .addField("Reason", reason, false)
                        .setColor(Color.YELLOW)
                        .setTimestamp(Instant.now());
                channel.sendMessageEmbeds(userWarnEmbed.build()).queue(s -> {}, e -> {});
            });

            if (evidenceUrl != null) {
                java.util.concurrent.CompletableFuture.runAsync(() -> {
                    try (java.io.InputStream is = java.net.URI.create(evidenceUrl).toURL().openStream()) {
                        byte[] data = is.readAllBytes();
                        ServiceManager.getLoggingService().logActionWithFile(event.getGuild(), "moderation-logs", warnEmbed.build(), new java.io.ByteArrayInputStream(data), evidenceFileName);
                    } catch (java.io.IOException e) {
                        ServiceManager.getLoggingService().logAction(event.getGuild(), "moderation-logs", warnEmbed.build());
                    }
                });
            } else {
                ServiceManager.getLoggingService().logAction(event.getGuild(), "moderation-logs", warnEmbed.build());
            }

            event.getHook().editOriginal("✅ Warning issued successfully.").setEmbeds().setComponents().queue();
        });
    }
}
