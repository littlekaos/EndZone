package EndZone.commands;

import EndZone.EndZone;
import EndZone.utils.EmbedUtils;
import EndZone.utils.PermissionUtils;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Icon;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.emoji.CustomEmoji;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.utils.FileUpload;

import java.io.InputStream;
import java.net.URL;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StealCommand implements Command {
    private final EndZone bot;
    private static final Pattern EMOJI_PATTERN = Pattern.compile("<a?:(\\w+):(\\d+)>");

    public StealCommand(EndZone bot) {
        this.bot = bot;
    }

    @Override
    public List<CommandData> getCommandDataList() {
        return List.of(Commands.slash("steal", "Steal an emoji from another server")
                .addOption(OptionType.STRING, "emoji", "The emoji to steal (or emoji ID/link)", true)
                .addOption(OptionType.STRING, "name", "Name for the new emoji", false));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!PermissionUtils.isAlphaBetaOrHigher(event.getMember(), bot.getConfig())) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("You need Alpha Beta+ permissions to use this command.")).setEphemeral(true).queue();
            return;
        }

        if (!event.getMember().hasPermission(Permission.MANAGE_GUILD_EXPRESSIONS)) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("You need Manage Expressions permission to use this command.")).setEphemeral(true).queue();
            return;
        }

        String emojiInput = event.getOption("emoji").getAsString();
        String name = event.getOption("name") != null ? event.getOption("name").getAsString() : null;

        String emojiId = null;
        String emojiName = null;
        boolean animated = false;

        Matcher matcher = EMOJI_PATTERN.matcher(emojiInput);
        if (matcher.find()) {
            emojiName = matcher.group(1);
            emojiId = matcher.group(2);
            animated = emojiInput.contains("<a:");
        } else if (emojiInput.matches("\\d+")) {
            emojiId = emojiInput;
        } else if (emojiInput.startsWith("http")) {
            // Try to extract ID from URL
            String[] parts = emojiInput.split("/");
            String lastPart = parts[parts.length - 1];
            if (lastPart.contains(".")) {
                emojiId = lastPart.split("\\.")[0];
                animated = lastPart.endsWith(".gif");
            } else {
                emojiId = lastPart;
            }
        }

        if (emojiId == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Could not parse emoji from input.")).setEphemeral(true).queue();
            return;
        }

        if (name == null) {
            name = emojiName != null ? emojiName : "stolen_emoji";
        }

        String extension = animated ? "gif" : "png";
        String url = String.format("https://cdn.discordapp.com/emojis/%s.%s", emojiId, extension);

        event.deferReply().queue();

        try {
            InputStream in = new URL(url).openStream();
            Icon icon = Icon.from(in);
            
            event.getGuild().createEmoji(name, icon).queue(
                emoji -> event.getHook().sendMessage("Successfully stole emoji: " + emoji.getAsMention()).queue(),
                error -> event.getHook().sendMessage("Failed to create emoji: " + error.getMessage()).queue()
            );
        } catch (Exception e) {
            event.getHook().sendMessage("Error stealing emoji: " + e.getMessage()).queue();
        }
    }
}
