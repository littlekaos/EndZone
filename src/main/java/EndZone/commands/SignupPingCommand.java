package EndZone.commands;

import EndZone.EndZone;
import EndZone.utils.PermissionUtils;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

import java.util.List;

public class SignupPingCommand implements Command {

    private final EndZone bot;

    public SignupPingCommand(EndZone bot) {
        this.bot = bot;
    }

    @Override
    public List<CommandData> getCommandDataList() {
        return List.of(
                Commands.slash("signup-ping", "Ping users in a role to sign up.")
                        .setGuildOnly(true)
                        .addOptions(
                                new OptionData(OptionType.ROLE, "role", "The role to ping", true),
                                new OptionData(OptionType.USER, "exclude", "A user to exclude from the ping", false)
                        )
        );
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!PermissionUtils.isAlphaBetaOrHigher(event.getMember(), bot.getConfig())) {
            event.reply("❌ You do not have permission to use this command. Only Alpha Beta+ can use this command.").setEphemeral(true).queue();
            return;
        }

        String roleId = event.getOption("role").getAsRole().getId();
        String roleName = event.getOption("role").getAsRole().getName();
        
        var excludeOption = event.getOption("exclude");
        String excludeId = excludeOption != null ? excludeOption.getAsUser().getId() : "none";
        String excludeName = excludeOption != null ? " (excluding " + excludeOption.getAsUser().getName() + ")" : "";

        event.reply("Are you sure you want to ping all members of **" + roleName + "**" + excludeName + " to signup?")
                .addComponents(ActionRow.of(
                        Button.success("signup_ping_confirm:" + roleId + ":" + excludeId, "Send Signup Ping"),
                        Button.danger("signup_ping_cancel:direct", "Cancel")
                ))
                .setEphemeral(true)
                .queue();
    }
}
