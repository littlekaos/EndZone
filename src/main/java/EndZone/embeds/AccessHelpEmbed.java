package EndZone.embeds;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

public class AccessHelpEmbed {

    private static final String MESSAGE_CONTENT =
            "## This is for help if you cannot verify in <#1478541170048241724>.\n\u200B\n" +
                    "## Please do not spam ping <@&1483691965601157201> or <@&1483685546080469033> to give you roles!\n\u200B\n" +
                    "## It won't go any faster, and you can be warned, muted, or timed out if you keep pinging " +
                    "<@&1483691965601157201> & <@&1483685546080469033>. \n\u200B\n";

    public static void sendOrUpdateAccessHelpEmbed(TextChannel channel) {
        EmbedBuilder embed = new EmbedBuilder()
                .setDescription(MESSAGE_CONTENT)
                .setColor(0xFF0000);
        // No dynamic timestamp — avoids unnecessary "edits" every restart

        // Check if the bot already posted this embed in the channel
        channel.getHistory().retrievePast(20).queue(messages -> {
            Message existing = messages.stream()
                    .filter(m -> m.getAuthor().equals(channel.getJDA().getSelfUser()))
                    .filter(m -> !m.getEmbeds().isEmpty())
                    .findFirst()
                    .orElse(null);

            if (existing != null) {
                // Edit the existing message instead of posting a new one
                existing.editMessageEmbeds(embed.build()).queue(
                        success -> System.out.println("[EMBEDS] Access Help embed updated."),
                        error   -> System.err.println("[EMBEDS] Failed to edit embed: " + error.getMessage())
                );
            } else {
                // No existing message found, send fresh
                channel.sendMessageEmbeds(embed.build()).queue(
                        success -> System.out.println("[EMBEDS] Access Help embed sent."),
                        error   -> System.err.println("[EMBEDS] Failed to send embed: " + error.getMessage())
                );
            }
        });
    }
}