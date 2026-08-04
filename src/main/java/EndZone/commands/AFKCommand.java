package EndZone.commands;

import EndZone.EndZone;
import EndZone.config.BotConfig;
import EndZone.services.ServiceManager;
import EndZone.utils.EmbedUtils;
import EndZone.utils.PermissionUtils;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.awt.Color;
import java.util.Collections;
import java.util.List;

public class AFKCommand implements Command {
    private final EndZone bot;

    public AFKCommand(EndZone bot) {
        this.bot = bot;
    }

    @Override
    public List<CommandData> getCommandDataList() {
        return Collections.singletonList(
                Commands.slash("afk", "Set AFK status")
                        .addOption(OptionType.STRING, "reason", "The reason to display when pinged", false)
                        .addOption(OptionType.USER, "user", "The user to set AFK (Staff only)", false)
        );
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        OptionMapping reasonOption = event.getOption("reason");
        OptionMapping userOption = event.getOption("user");
        
        String reason = reasonOption != null ? reasonOption.getAsString() : "I'm currently AFK.";
        
        net.dv8tion.jda.api.entities.Member targetMember = event.getMember();
        if (userOption != null) {
            if (!PermissionUtils.isAlphaBetaOrHigher(event.getMember(), ServiceManager.getConfig())) {
                event.replyEmbeds(EmbedUtils.createErrorEmbed("You do not have permission to set other users' AFK status. Only Alpha Beta+ can do this."))
                        .setEphemeral(true).queue();
                return;
            }
            targetMember = userOption.getAsMember();
            if (targetMember == null) {
                event.replyEmbeds(EmbedUtils.createErrorEmbed("That user is not in this server."))
                        .setEphemeral(true).queue();
                return;
            }
        }

        if (targetMember == null) return;

        String userId = targetMember.getId();
        String originalNickname = targetMember.getEffectiveName();

        ServiceManager.getAFKService().setAFK(userId, reason, originalNickname);

        // Change nickname if possible
        net.dv8tion.jda.api.entities.Member selfMember = event.getGuild().getSelfMember();
        if (selfMember.canInteract(targetMember) && selfMember.hasPermission(net.dv8tion.jda.api.Permission.NICKNAME_MANAGE)) {
            String newNick = "[AFK] " + originalNickname;
            if (newNick.length() > 32) {
                newNick = newNick.substring(0, 32);
            }
            targetMember.modifyNickname(newNick).queue(null, error -> {});
        }

        String confirmation = userOption != null ? 
                "I have set " + targetMember.getAsMention() + "'s AFK: " + reason :
                "I have set your AFK: " + reason;

        event.replyEmbeds(EmbedUtils.createEmbed(Color.GREEN, confirmation)).setEphemeral(true).queue();
    }
}
