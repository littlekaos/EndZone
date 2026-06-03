package EndZone.events;

import EndZone.EndZone;
import EndZone.commands.*;
import EndZone.config.BotConfig;
import EndZone.utils.EmbedUtils;
import EndZone.utils.PermissionUtils;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CommandEventListener extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(CommandEventListener.class);
    private final EndZone bot;
    private final Map<String, Command> commands = new HashMap<>();

    public CommandEventListener(EndZone bot) {
        this.bot = bot;
        registerCommandHandlers();
    }

    private void registerCommandHandlers() {
        registerCommand(new TestCommand());
        registerCommand(new HelpCommand());
        registerCommand(new EventNameCommand(bot));
        registerCommand(new BanCommand(bot));
        registerCommand(new VoidCheckerCommand(bot));
        registerCommand(new RoleCommand(bot));
        registerCommand(new ReasonCommand(bot));
        registerCommand(new StrikeCommand(bot));
        registerCommand(new AppealCommand(bot));
        registerCommand(new DemotionCommand(bot));
        registerCommand(new AdminStrikeCommand(bot));
        registerCommand(new WarnCommand(bot));
        registerCommand(new MuteCommand(bot));
        registerCommand(new UnmuteCommand(bot));
        registerCommand(new SetMuteRoleCommand(bot));
        registerCommand(new TimeoutCommand(bot));
        registerCommand(new UntimeoutCommand(bot));
        registerCommand(new UnbanCommand(bot));
        registerCommand(new KickCommand(bot));
        registerCommand(new PurgeCommand(bot));
        registerCommand(new RestrictCommand(bot));
        registerCommand(new UnrestrictCommand(bot));
        registerCommand(new RestrictSetupCommand(bot));
        registerCommand(new VoiceCommand(bot));
        registerCommand(new ClearDMsCommand(bot));
        registerCommand(new SayCommand(bot));
        registerCommand(new EventPingCommand(bot));
        registerCommand(new StealCommand(bot));
        registerCommand(new ReactionRoleCommand(bot));
        registerCommand(new BlacklistCommand(bot));
        registerCommand(new AFKCommand(bot));
        registerCommand(new SignupPingCommand(bot));
    }

    private void registerCommand(Command command) {
        for (CommandData data : command.getCommandDataList()) {
            commands.put(data.getName(), command);
        }
    }

    public static void registerCommands(JDA jda, BotConfig config, EndZone bot) {
        try {
            List<CommandData> allCommands = new ArrayList<>();
            allCommands.addAll(new TestCommand().getCommandDataList());
            allCommands.addAll(new HelpCommand().getCommandDataList());
            allCommands.addAll(new EventNameCommand(bot).getCommandDataList());
            allCommands.addAll(new BanCommand(bot).getCommandDataList());
            allCommands.addAll(new VoidCheckerCommand(bot).getCommandDataList());
            allCommands.addAll(new RoleCommand(bot).getCommandDataList());
            allCommands.addAll(new ReasonCommand(bot).getCommandDataList());
            allCommands.addAll(new StrikeCommand(bot).getCommandDataList());
            allCommands.addAll(new AppealCommand(bot).getCommandDataList());
            allCommands.addAll(new DemotionCommand(bot).getCommandDataList());
            allCommands.addAll(new AdminStrikeCommand(bot).getCommandDataList());
            allCommands.addAll(new WarnCommand(bot).getCommandDataList());
            allCommands.addAll(new MuteCommand(bot).getCommandDataList());
            allCommands.addAll(new UnmuteCommand(bot).getCommandDataList());
            allCommands.addAll(new SetMuteRoleCommand(bot).getCommandDataList());
            allCommands.addAll(new TimeoutCommand(bot).getCommandDataList());
            allCommands.addAll(new UntimeoutCommand(bot).getCommandDataList());
            allCommands.addAll(new UnbanCommand(bot).getCommandDataList());
            allCommands.addAll(new KickCommand(bot).getCommandDataList());
            allCommands.addAll(new PurgeCommand(bot).getCommandDataList());
            allCommands.addAll(new RestrictCommand(bot).getCommandDataList());
            allCommands.addAll(new UnrestrictCommand(bot).getCommandDataList());
            allCommands.addAll(new RestrictSetupCommand(bot).getCommandDataList());
            allCommands.addAll(new VoiceCommand(bot).getCommandDataList());
            allCommands.addAll(new ClearDMsCommand(bot).getCommandDataList());
            allCommands.addAll(new SayCommand(bot).getCommandDataList());
            allCommands.addAll(new EventPingCommand(bot).getCommandDataList());
            allCommands.addAll(new StealCommand(bot).getCommandDataList());
            allCommands.addAll(new ReactionRoleCommand(bot).getCommandDataList());
            allCommands.addAll(new BlacklistCommand(bot).getCommandDataList());
            allCommands.addAll(new AFKCommand(bot).getCommandDataList());
            allCommands.addAll(new SignupPingCommand(bot).getCommandDataList());
            
            // Clear global commands to avoid duplicates
            jda.updateCommands().queue();

            List<net.dv8tion.jda.api.entities.Guild> guilds = jda.getGuilds();
            if (!guilds.isEmpty()) {
                for (net.dv8tion.jda.api.entities.Guild guild : guilds) {
                    guild.updateCommands()
                            .addCommands(allCommands)
                            .queue(commands -> logger.info("Successfully registered {} guild slash commands for guild: {}", commands.size(), guild.getName()));
                }
            } else {
                logger.error("CRITICAL: Bot is not in any guilds. Commands NOT registered!");
            }
        } catch (Exception e) {
            logger.error("Error registering commands: {}", e.getMessage());
        }
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        // Global Blacklist Check
        if (PermissionUtils.isBlacklisted(event.getUser())) {
            event.reply("❌ You are blacklisted from using this bot.").setEphemeral(true).queue();
            return;
        }

        String commandName = event.getName();
        Command command = commands.get(commandName);

        if (command != null) {
            try {
                command.execute(event);
            } catch (Exception e) {
                logger.error("Error executing command {}: {}", commandName, e.getMessage());
                if (!event.isAcknowledged()) {
                    event.replyEmbeds(EmbedUtils.createErrorEmbed(
                            "An error occurred while executing this command: " + e.getMessage()
                    )).setEphemeral(true).queue();
                }
            }
        } else {
            if (!event.isAcknowledged()) {
                event.reply("Unknown command: " + commandName).setEphemeral(true).queue();
            }
        }
    }
}
