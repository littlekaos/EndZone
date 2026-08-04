package EndZone.embeds;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;

import java.util.ArrayList;
import java.util.List;

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

    public static void sendOrUpdateEndZoneEmbed(TextChannel channel) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle(EMBED_TITLE)
                .setDescription(EMBED_DESCRIPTION)
                .setColor(EMBED_COLOR);

        channel.getHistory().retrievePast(100).queue(messages -> {
            List<Message> existing = new ArrayList<>();
            for (Message message : messages) {
                if (isApplicationEmbed(message, channel)) {
                    existing.add(message);
                }
            }

            System.out.println("[EMBEDS] Found " + existing.size() + " EndZone application embed(s) in #" + channel.getName());

            if (existing.isEmpty()) {
                channel.sendMessageEmbeds(embed.build())
                        .addComponents(ActionRow.of(Button.primary(BUTTON_ID, BUTTON_LABEL)))
                        .queue(
                                success -> System.out.println("[EMBEDS] Sent new EndZone application embed (id=" + success.getId() + ")"),
                                error -> System.err.println("[EMBEDS] Failed to send application embed: " + error.getMessage())
                        );
                return;
            }

            // Keep the newest one, delete the rest
            Message keep = existing.get(0);
            System.out.println("[EMBEDS] Keeping application embed id=" + keep.getId());
            keep.editMessageEmbeds(embed.build())
                    .setComponents(ActionRow.of(Button.primary(BUTTON_ID, BUTTON_LABEL)))
                    .queue(
                            success -> System.out.println("[EMBEDS] Updated kept application embed id=" + keep.getId()),
                            error -> System.err.println("[EMBEDS] Failed to update application embed: " + error.getMessage())
                    );

            int duplicates = existing.size() - 1;
            if (duplicates == 0) {
                System.out.println("[EMBEDS] No duplicate application embeds to delete.");
                return;
            }

            System.out.println("[EMBEDS] Deleting " + duplicates + " duplicate application embed(s)...");
            for (int i = 1; i < existing.size(); i++) {
                Message duplicate = existing.get(i);
                String duplicateId = duplicate.getId();
                duplicate.delete().queue(
                        success -> System.out.println("[EMBEDS] Deleted duplicate application embed id=" + duplicateId),
                        error -> System.err.println("[EMBEDS] Failed to delete duplicate id=" + duplicateId + ": " + error.getMessage())
                );
            }
        }, error -> System.err.println("[EMBEDS] Failed to load channel history for application embed: " + error.getMessage()));
    }

    private static boolean isApplicationEmbed(Message message, TextChannel channel) {
        if (!message.getAuthor().equals(channel.getJDA().getSelfUser())) {
            return false;
        }

        boolean hasButton = message.getComponentTree().findAll(Button.class).stream()
                .anyMatch(button -> BUTTON_ID.equals(button.getCustomId()));
        if (hasButton) {
            return true;
        }

        return message.getEmbeds().stream()
                .map(MessageEmbed::getTitle)
                .anyMatch(EMBED_TITLE::equals);
    }
}
