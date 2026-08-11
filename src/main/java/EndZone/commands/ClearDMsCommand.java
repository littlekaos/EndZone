package EndZone.commands;

import EndZone.services.ServiceManager;
import EndZone.utils.EmbedUtils;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class ClearDMsCommand implements Command {

    @Override
    public List<CommandData> getCommandDataList() {
        return List.of(Commands.slash("cleardms", "Delete the bot's messages in your DM with it"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();

        event.getUser().openPrivateChannel().queue(
                channel -> clearBotMessages(event, channel),
                err -> event.getHook().editOriginalEmbeds(
                        EmbedUtils.createErrorEmbed("Couldn't open your DM with the bot. Open a DM first, then try again.")
                ).queue()
        );
    }

    private void clearBotMessages(SlashCommandInteractionEvent event, PrivateChannel channel) {
        String botId = event.getJDA().getSelfUser().getId();
        List<Message> toDelete = new ArrayList<>();

        channel.getIterableHistory().forEachAsync(message -> {
            if (message.getAuthor().getId().equals(botId)) {
                toDelete.add(message);
            }
            return true;
        }).thenAccept(v -> {
            if (toDelete.isEmpty()) {
                event.getHook().editOriginalEmbeds(
                        EmbedUtils.createInfoEmbed("No bot messages found in your DMs.")
                ).queue();
                return;
            }

            AtomicInteger deleted = new AtomicInteger(0);
            AtomicInteger failed = new AtomicInteger(0);
            deleteNext(event, toDelete, 0, deleted, failed);
        }).exceptionally(ex -> {
            event.getHook().editOriginalEmbeds(
                    EmbedUtils.createErrorEmbed("Failed to read DM history: " + ex.getMessage())
            ).queue();
            return null;
        });
    }

    private void deleteNext(SlashCommandInteractionEvent event, List<Message> messages, int index,
                            AtomicInteger deleted, AtomicInteger failed) {
        if (index >= messages.size()) {
            event.getHook().editOriginalEmbeds(EmbedUtils.createSuccessEmbed(
                    "Cleared **" + deleted.get() + "** bot message"
                            + (deleted.get() == 1 ? "" : "s")
                            + " from your DMs"
                            + (failed.get() > 0 ? " (" + failed.get() + " failed)." : ".")
            )).queue();
            return;
        }

        messages.get(index).delete().queue(
                ok -> {
                    deleted.incrementAndGet();
                    deleteNext(event, messages, index + 1, deleted, failed);
                },
                err -> {
                    failed.incrementAndGet();
                    deleteNext(event, messages, index + 1, deleted, failed);
                }
        );
    }
}
