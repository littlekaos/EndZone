package EndZone.commands;

import EndZone.EndZone;
import EndZone.services.ServiceManager;
import EndZone.utils.PermissionUtils;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.util.List;

public class EventCountdownToggleCommand implements Command {

    private final EndZone bot;

    public EventCountdownToggleCommand(EndZone bot) {
        this.bot = bot;
    }

    @Override
    public List<CommandData> getCommandDataList() {
        return List.of(
                Commands.slash("eventcountdowntoggle", "Enable or disable the automatic weekly event countdown ping.")
                        .addOption(OptionType.BOOLEAN, "enabled", "Whether the countdown should be enabled", true)
        );
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!PermissionUtils.isAlphaBetaOrHigher(event.getMember(), bot.getConfig())) {
            event.reply("❌ You do not have permission to use this command.").setEphemeral(true).queue();
            return;
        }

        boolean enabled = event.getOption("enabled").getAsBoolean();
        ServiceManager.getDataService().setEventCountdownEnabled(enabled);

        String status = enabled ? "enabled" : "disabled";
        event.reply("✅ Automatic weekly event countdown ping has been **" + status + "**.").queue();
    }
}
