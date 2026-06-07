package EndZone.commands;

import EndZone.config.BotConfig;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.emoji.EmojiUnion;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.util.Collections;
import java.util.List;

public class SyncWinnersCommand implements Command {

    @Override
    public List<CommandData> getCommandDataList() {
        return Collections.singletonList(
                Commands.slash("syncwinners", "Retroactively give Clan Winners role to those who reacted to a message")
                        .addOption(OptionType.STRING, "message_url", "The URL of the message to sync reactions from", true)
        );
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
            event.reply("You do not have permission to use this command.").setEphemeral(true).queue();
            return;
        }

        String messageUrl = event.getOption("message_url").getAsString();
        String[] parts = messageUrl.split("/");
        if (parts.length < 3) {
            event.reply("Invalid message URL.").setEphemeral(true).queue();
            return;
        }

        String channelId = parts[parts.length - 2];
        String messageId = parts[parts.length - 1];

        GuildMessageChannel channel = event.getGuild().getChannelById(GuildMessageChannel.class, channelId);
        if (channel == null) {
            event.reply("Channel not found.").setEphemeral(true).queue();
            return;
        }

        event.deferReply(true).queue();

        channel.retrieveMessageById(messageId).queue(message -> {
            Role winnerRole = event.getGuild().getRoleById(BotConfig.WINNER_ROLE_ID);
            if (winnerRole == null) {
                event.getHook().sendMessage("Winner role not found.").queue();
                return;
            }

            message.getReactions().stream()
                    .filter(reaction -> {
                        EmojiUnion emoji = reaction.getEmoji();
                        if (emoji.getType() == Emoji.Type.CUSTOM) {
                            String id = emoji.asCustom().getId();
                            return id.equals(BotConfig.WINNER_CLAIM_EMOJI_ID) || id.equals(BotConfig.EZ_EMOJI_ID);
                        }
                        String name = emoji.getName();
                        return name.equals(BotConfig.WINNER_CLAIM_EMOJI_NAME) || name.equals(BotConfig.EZ_EMOJI_NAME);
                    })
                    .findFirst()
                    .ifPresentOrElse(reaction -> {
                        reaction.retrieveUsers().forEachAsync(user -> {
                            if (user.isBot()) return true;
                            event.getGuild().retrieveMember(user).queue(member -> {
                                event.getGuild().addRoleToMember(member, winnerRole).queue();
                            });
                            return true;
                        }).thenRun(() -> {
                            // Fetch final count for confirmation
                            reaction.retrieveUsers().submit().thenAccept(users -> {
                                long count = users.stream().filter(u -> !u.isBot()).count();
                                event.getHook().sendMessage("✅ Successfully synced winner roles for " + count + " reactors!").queue();
                            });
                        });
                    }, () -> {
                        event.getHook().sendMessage("No reactions found with the EZ or 1st emoji on that message.").queue();
                    });
        }, error -> {
            event.getHook().sendMessage("Failed to retrieve message: " + error.getMessage()).queue();
        });
    }
}
