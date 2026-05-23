package EndZone.embeds;

import EndZone.config.BotConfig;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

public class EmbedInitializer {
    public static void initializeEmbeds(JDA jda) {
        TextChannel channel = jda.getTextChannelById(BotConfig.APPLICATION_CHANNEL_ID);
        if (channel != null) {
            EndZoneEmbed.sendEndZoneEmbed(channel);
            System.out.println("[EMBEDS] EndZone embed sent to channel: " + channel.getName());
        } else {
            System.err.println("[EMBEDS] Application channel not found for embed initialization");
        }

        TextChannel accessHelpChannel = jda.getTextChannelById(BotConfig.ACCESS_HELP_CHANNEL_ID);
        if (accessHelpChannel != null) {
            AccessHelpEmbed.sendOrUpdateAccessHelpEmbed(accessHelpChannel);
            System.out.println("[EMBEDS] Access Help embed sent to channel: " + accessHelpChannel.getName());
        } else {
            System.err.println("[EMBEDS] Access Help channel not found for embed initialization");
        }
    }
}