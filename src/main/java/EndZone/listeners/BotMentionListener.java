package EndZone.listeners;

import EndZone.config.BotConfig;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Staff ping when someone mentions the bot, matching ModMail's mention log.
 * Always posted to the CourtZone mention-log channel, from any server.
 */
public class BotMentionListener extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(BotMentionListener.class);

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot() || !event.isFromGuild()) {
            return;
        }
        User self = event.getJDA().getSelfUser();
        if (!event.getMessage().getMentions().isMentioned(self, Message.MentionType.USER)) {
            return;
        }

        GuildMessageChannel dest = event.getJDA().getChannelById(
                GuildMessageChannel.class, BotConfig.BOT_MENTION_LOG_CHANNEL_ID);
        if (dest == null || dest.getId().equals(event.getChannel().getId())) {
            logger.warn("[BotMention] CourtZone mention log channel {} not found (ping from {} #{})",
                    BotConfig.BOT_MENTION_LOG_CHANNEL_ID,
                    event.getGuild().getName(), event.getChannel().getName());
            return;
        }

        String quoted = event.getMessage().getContentDisplay().replace("\n", " ").trim();
        if (quoted.length() > 1500) {
            quoted = quoted.substring(0, 1497) + "...";
        }
        String jump = "https://discord.com/channels/"
                + event.getGuild().getId() + "/"
                + event.getChannel().getId() + "/"
                + event.getMessageId();
        String place = event.getGuild().getName() + " > # " + event.getChannel().getName();
        place = place.replace("]", "");

        String body = "@here Bot mentioned in [" + place + "](" + jump + ")"
                + " by **" + userTag(event.getAuthor()) + "** (`" + event.getAuthor().getId() + "`): \""
                + quoted + "\"\n"
                + jump;

        Member botMember = dest.getGuild().getSelfMember();
        boolean canHere = botMember.hasPermission(dest, Permission.MESSAGE_MENTION_EVERYONE);
        dest.sendMessage(new MessageCreateBuilder()
                        .setContent(body)
                        .setAllowedMentions(canHere ? List.of(Message.MentionType.EVERYONE) : List.of())
                        .build())
                .queue(
                        ok -> {},
                        err -> logger.warn("[BotMention] Failed to post mention log: {}", err.getMessage())
                );
    }

    private static String userTag(User user) {
        String discriminator = user.getDiscriminator();
        if (discriminator == null || discriminator.isBlank()
                || "0000".equals(discriminator) || "0".equals(discriminator)) {
            return user.getName() + "#0";
        }
        return user.getName() + "#" + discriminator;
    }
}
