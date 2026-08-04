package EndZone.commands;

import EndZone.config.BotConfig;
import EndZone.utils.EmbedUtils;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.awt.Color;
import java.util.List;

public class TestCommand implements Command {

    @Override
    public List<CommandData> getCommandDataList() {
        return List.of(Commands.slash("test", "Check if the bot is working")
                .setDefaultPermissions(DefaultMemberPermissions.ENABLED));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        event.replyEmbeds(EmbedUtils.createEmbed(
                Color.BLUE,
                BotConfig.EZ_EMOJI_MENTION + " EndZone bot is currently working!"
        )).setEphemeral(true).queue();
    }
}
