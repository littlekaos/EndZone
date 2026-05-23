package EndZone.commands;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

public class CommandManager {
    public static void registerCommands(JDA jda) {
        var guild = jda.getGuildById("790157978647920641");
        if (guild != null) {
            guild.updateCommands()
                .addCommands(
                        Commands.slash("test", "Check if the bot is working"),
                        Commands.slash("lock", "Manually lock a channel")
                                .addOption(OptionType.CHANNEL, "channel", "The channel to lock", true),
                        Commands.slash("unlock", "Manually unlock a channel")
                                .addOption(OptionType.CHANNEL, "channel", "The channel to unlock", true),
                        Commands.slash("resume", "Resume automatic schedule for a channel")
                                .addOption(OptionType.CHANNEL, "channel", "The channel to resume schedule for", true)
                )
                .queue(
                    success -> System.out.println("[COMMANDS] Registered slash commands for guild: " + guild.getName()),
                    error -> System.err.println("[COMMANDS] Failed to register slash commands: " + error.getMessage())
                );
        } else {
            System.err.println("[COMMANDS] Primary guild not found for command registration");
        }
    }
}
