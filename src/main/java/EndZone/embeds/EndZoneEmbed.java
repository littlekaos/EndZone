package EndZone.embeds;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

public class EndZoneEmbed {
    private static final String EMBED_TITLE = "EndZone Staff Application";
    private static final String EMBED_DESCRIPTION = """
            \u200B
            If you're interested in helping manage and grow our community, please apply down below!
           \s
            **__TO PROCEED YOU WILL NEED YOUR DISCORD ID.__**
           \s
           Settings > Advanced Mode > Enable Developer Mode. Good luck!
           \s""";

    private static final int EMBED_COLOR = 0x1f47cf;
    private static final String BUTTON_ID = "endzone_button";
    private static final String BUTTON_LABEL = "Get Started!";

    public static void sendEndZoneEmbed(TextChannel channel) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle(EMBED_TITLE)
                .setDescription(EMBED_DESCRIPTION)
                .setColor(EMBED_COLOR);

        channel.sendMessageEmbeds(embed.build())
                .setActionRow(Button.primary(BUTTON_ID, BUTTON_LABEL))
                .queue(
                    null,
                    error -> System.err.println("[ERROR] Failed to send application embed: " + error.getMessage())
                );
    }
}
