package EndZone.commands;

import EndZone.EndZone;
import EndZone.utils.FormatUtils;
import EndZone.utils.PermissionUtils;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SayCommand implements Command {

    private final EndZone bot;
    private static final Map<String, String> pendingMessages = new ConcurrentHashMap<>();

    public SayCommand(EndZone bot) {
        this.bot = bot;
    }

    public static void addPendingMessage(String uuid, String message) {
        pendingMessages.put(uuid, message);
    }

    public static String getPendingMessage(String uuid) {
        return pendingMessages.remove(uuid);
    }

    @Override
    public List<CommandData> getCommandDataList() {
        return List.of(
                Commands.slash("say", "Send a message with formatting support.")
                        .setGuildOnly(true)
                        .addOption(OptionType.STRING, "message", "Enter your message here...", true)
        );
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!PermissionUtils.isAlphaBetaOrHigher(event.getMember(), bot.getConfig())) {
            event.reply("❌ You do not have permission to use this command. Only Alpha Beta+ can use this command.").setEphemeral(true).queue();
            return;
        }

        String message = event.getOption("message").getAsString();
        String resolvedMessage = FormatUtils.resolveMentions(message, event.getGuild());

        event.getChannel().sendMessage(resolvedMessage).queue();
        event.reply("✅ Message sent!").setEphemeral(true).queue();
    }
}
