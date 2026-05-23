package EndZone.commands;

import EndZone.EndZone;
import EndZone.utils.PermissionUtils;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;

import java.util.List;

public class EventPingCommand implements Command {

    private final EndZone bot;

    public EventPingCommand(EndZone bot) {
        this.bot = bot;
    }

    @Override
    public List<CommandData> getCommandDataList() {
        return List.of(
                Commands.slash("eventping", "Send an event ping with formatting support.")
                        .setGuildOnly(true)
                        .addOptions(new OptionData(OptionType.STRING, "destination", "Where to send the ping", true)
                                .addChoice("Drafting Things", "drafting")
                                .addChoice("Event Countdowns", "countdown")
                                .addChoice("Both Channels", "both"))
        );
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!PermissionUtils.isAlphaBetaOrHigher(event.getMember(), bot.getConfig())) {
            event.reply("❌ You do not have permission to use this command. Only Alpha Beta+ can use this command.").setEphemeral(true).queue();
            return;
        }

        String destination = event.getOption("destination").getAsString();

        TextInput messageInput = TextInput.create("message", "Message", TextInputStyle.PARAGRAPH)
                .setPlaceholder("Enter your event message here...")
                .setRequired(true)
                .build();

        Modal modal = Modal.create("eventping_modal:" + destination, "Event Ping")
                .addComponents(ActionRow.of(messageInput))
                .build();

        event.replyModal(modal).queue();
    }
}
