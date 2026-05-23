package EndZone.commands;

import EndZone.EndZone;
import EndZone.services.ServiceManager;
import EndZone.utils.EmbedUtils;
import EndZone.utils.PermissionUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;

import java.awt.Color;
import java.time.Instant;
import java.util.List;

public class RestrictSetupCommand implements Command {
    private final EndZone bot;

    public RestrictSetupCommand(EndZone bot) {
        this.bot = bot;
    }

    @Override
    public List<CommandData> getCommandDataList() {
        return List.of(Commands.slash("restrict-setup", "Interactive setup for channel restrictions"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!PermissionUtils.isAlphaBetaOrHigher(event.getMember(), ServiceManager.getConfig())) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("You need Alpha Beta+ permissions to use this command.")).setEphemeral(true).queue();
            return;
        }

        StringSelectMenu menu = StringSelectMenu.create("restriction_type_select")
                .setPlaceholder("Select a restriction type")
                .addOption("Media With Text", "MEDIA_WITH_TEXT", "Require media/links, text allowed")
                .addOption("Media Only", "MEDIA_ONLY", "Only media/links allowed")
                .addOption("Screenshot Only", "SCREENSHOT_ONLY", "Only images allowed, no text")
                .addOption("Text Only", "TEXT_ONLY", "Only text allowed, no media/links")
                .addOption("No Media", "NO_MEDIA", "No media/links allowed")
                .addOption("No Content", "NO_CONTENT", "No messages allowed at all")
                .addOption("No Message", "NO_MESSAGE", "No messages allowed")
                .build();

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🛡️ Channel Restriction Setup")
                .setDescription("Select the type of restriction you want to apply to one or more channels.\n")
                .setColor(Color.BLUE)
                .setTimestamp(Instant.now());

        event.replyEmbeds(embed.build())
                .addComponents(ActionRow.of(menu))
                .setEphemeral(true)
                .queue();
    }
}
