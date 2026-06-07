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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SignupPingCommand implements Command {

    private final EndZone bot;
    private static final Map<String, SignupPingData> pendingPings = new ConcurrentHashMap<>();

    public record SignupPingData(String roleId, List<String> excludeIds) {}

    public static SignupPingData getPendingPing(String uuid) {
        return pendingPings.remove(uuid);
    }

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
                                new OptionData(OptionType.STRING, "excludes", "User mentions or IDs to exclude (space-separated)", false)
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
        
        List<String> excludeIds = new ArrayList<>();
        var excludesOption = event.getOption("excludes");
        
        if (excludesOption != null) {
            String input = excludesOption.getAsString();
            // Match mentions <@!123> or <@123> or raw IDs 123
            Pattern pattern = Pattern.compile("<@!?(\\d+)>|(\\d{17,20})");
            Matcher matcher = pattern.matcher(input);
            while (matcher.find()) {
                String id = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
                if (!excludeIds.contains(id)) {
                    excludeIds.add(id);
                }
            }
        }

        String excludeString = excludeIds.isEmpty() ? "" : " (excluding " + excludeIds.size() + " users)";
        
        String uuid = UUID.randomUUID().toString();
        pendingPings.put(uuid, new SignupPingData(roleId, excludeIds));

        event.reply("Are you sure you want to ping all members of **" + roleName + "**" + excludeString + " to signup?")
                .addComponents(ActionRow.of(
                        Button.success("signup_ping_confirm:" + uuid, "Send Signup Ping"),
                        Button.danger("signup_ping_cancel:" + uuid, "Cancel")
                ))
                .setEphemeral(true)
                .queue();
    }
}
