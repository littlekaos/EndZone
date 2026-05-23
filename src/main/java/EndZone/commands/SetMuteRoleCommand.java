package EndZone.commands;

import EndZone.EndZone;
import EndZone.config.BotConfig;
import EndZone.services.DataService;
import EndZone.utils.EmbedUtils;
import EndZone.utils.PermissionUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.awt.Color;
import java.time.Instant;
import java.util.List;

public class SetMuteRoleCommand implements Command {
    private final EndZone bot;
    private final DataService dataService;

    public SetMuteRoleCommand(EndZone bot) {
        this.bot = bot;
        this.dataService = bot.getDataService();
    }

    @Override
    public List<CommandData> getCommandDataList() {
        return List.of(Commands.slash("setmuterole", "Set the role used for muting users")
                .addOption(OptionType.ROLE, "role", "The role to use for muting", true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!PermissionUtils.isAlphaBetaOrHigher(event.getMember(), bot.getConfig())) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("You need Alpha Beta+ permissions to use this command.")).setEphemeral(true).queue();
            return;
        }

        event.deferReply(true).queue();

        Role muteRole = event.getOption("role").getAsRole();
        if (muteRole.isManaged()) {
            event.getHook().sendMessage("❌ Cannot use managed roles for muting.").queue();
            return;
        }

        Guild guild = event.getGuild();
        dataService.setMuteRoleId(guild.getId(), muteRole.getId());

        for (GuildChannel channel : guild.getChannels()) {
            if (channel instanceof net.dv8tion.jda.api.entities.channel.attribute.IPermissionContainer container) {
                var override = container.upsertPermissionOverride(muteRole);
                
                if (channel.getId().equals(BotConfig.RULES_CHANNEL_ID)) {
                    // For rules channel, allow viewing but deny sending
                    override.deny(Permission.MESSAGE_SEND,
                                    Permission.MESSAGE_SEND_IN_THREADS,
                                    Permission.CREATE_PUBLIC_THREADS,
                                    Permission.CREATE_PRIVATE_THREADS,
                                    Permission.MESSAGE_ADD_REACTION)
                            .clear(Permission.VIEW_CHANNEL)
                            .queue(null, e -> {});
                } else {
                    // For all other channels, deny viewing entirely
                    override.deny(Permission.VIEW_CHANNEL,
                                    Permission.MESSAGE_SEND,
                                    Permission.MESSAGE_SEND_IN_THREADS,
                                    Permission.CREATE_PUBLIC_THREADS,
                                    Permission.CREATE_PRIVATE_THREADS,
                                    Permission.MESSAGE_ADD_REACTION)
                            .queue(null, e -> {});
                }
            }
        }

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🔇 Mute Role Set")
                .setDescription(String.format("Role **%s** will now be used for muting users.", muteRole.getName()))
                .setColor(Color.GREEN)
                .setTimestamp(Instant.now());

        event.getHook().sendMessageEmbeds(embed.build()).queue();
    }
}
