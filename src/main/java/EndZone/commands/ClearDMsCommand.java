package EndZone.commands;

import EndZone.EndZone;
import EndZone.services.ServiceManager;
import EndZone.utils.EmbedUtils;
import EndZone.utils.PermissionUtils;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ClearDMsCommand implements Command {
    private final EndZone bot;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public ClearDMsCommand(EndZone bot) {
        this.bot = bot;
    }

    @Override
    public List<CommandData> getCommandDataList() {
        return List.of(Commands.slash("cleardms", "Deletes all bot messages in your DMs or with everyone (Alpha Beta+)"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!PermissionUtils.isAlphaBetaOrHigher(event.getMember(), ServiceManager.getConfig())) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "You don't have permission to use this command. Only Alpha Beta+ can use this command."
            )).setEphemeral(true).queue();
            return;
        }

        event.deferReply(true).queue();

        event.getGuild().loadMembers().onSuccess(members -> {
            AtomicInteger totalCleared = new AtomicInteger(0);
            List<Member> nonBotMembers = members.stream()
                    .filter(m -> !m.getUser().isBot())
                    .toList();
            
            int totalMembers = nonBotMembers.size();

            // Initial status message
            event.getHook().sendMessageEmbeds(EmbedUtils.createEmbed(
                    Color.BLUE,
                    "⏳ Clearing DMs...\nProcessed: 0/" + totalMembers
            )).queue(statusMessage -> {
                processNextMember(event, nonBotMembers, 0, totalCleared);
            });
        });
    }

    private void processNextMember(SlashCommandInteractionEvent event, List<Member> members, int index, AtomicInteger totalCleared) {
        if (index >= members.size()) {
            sendFinalUpdate(event, totalCleared.get());
            return;
        }

        Member member = members.get(index);
        member.getUser().openPrivateChannel().queue(channel -> {
            retrieveAndBatchDelete(event, channel, members, index, totalCleared);
        }, error -> {
            System.err.println("Failed to open DM with " + member.getUser().getName() + ": " + error.getMessage());
            updateStatus(event, index + 1, members.size());
            scheduler.schedule(() -> processNextMember(event, members, index + 1, totalCleared), 500, TimeUnit.MILLISECONDS);
        });
    }

    private void retrieveAndBatchDelete(SlashCommandInteractionEvent event, PrivateChannel channel, List<Member> members, int memberIndex, AtomicInteger totalCleared) {
        List<Message> toDelete = new ArrayList<>();
        channel.getIterableHistory().forEachAsync(message -> {
            if (message.getAuthor().getId().equals(event.getJDA().getSelfUser().getId())) {
                toDelete.add(message);
            }
            return true; // continue iteration
        }).thenRun(() -> {
            if (toDelete.isEmpty()) {
                updateStatus(event, memberIndex + 1, members.size());
                scheduler.schedule(() -> processNextMember(event, members, memberIndex + 1, totalCleared), 500, TimeUnit.MILLISECONDS);
                return;
            }
            
            deleteMessagesSequentially(event, toDelete, 0, channel, members, memberIndex, totalCleared);
        }).exceptionally(error -> {
            System.err.println("Error fetching history for " + channel.getUser().getName() + ": " + error.getMessage());
            updateStatus(event, memberIndex + 1, members.size());
            scheduler.schedule(() -> processNextMember(event, members, memberIndex + 1, totalCleared), 500, TimeUnit.MILLISECONDS);
            return null;
        });
    }

    private void deleteMessagesSequentially(SlashCommandInteractionEvent event, List<Message> messages, int messageIndex, PrivateChannel channel, List<Member> members, int memberIndex, AtomicInteger totalCleared) {
        if (messageIndex >= messages.size()) {
            updateStatus(event, memberIndex + 1, members.size());
            // Delay before moving to the next member
            scheduler.schedule(() -> processNextMember(event, members, memberIndex + 1, totalCleared), 10, TimeUnit.SECONDS);
            return;
        }

        messages.get(messageIndex).delete().queue(
            success -> {
                totalCleared.incrementAndGet();
                // Delay between message deletions to avoid spam flags
                scheduler.schedule(() -> deleteMessagesSequentially(event, messages, messageIndex + 1, channel, members, memberIndex, totalCleared), 1500, TimeUnit.MILLISECONDS);
            },
            error -> {
                System.err.println("Failed to delete message in DM with " + channel.getUser().getName() + ": " + error.getMessage());
                // Even on error, continue to next message
                scheduler.schedule(() -> deleteMessagesSequentially(event, messages, messageIndex + 1, channel, members, memberIndex, totalCleared), 1500, TimeUnit.MILLISECONDS);
            }
        );
    }

    private void updateStatus(SlashCommandInteractionEvent event, int processed, int total) {
        double percentage = (double) processed / total * 100;
        int blocks = (int) (percentage / 10);
        StringBuilder bar = new StringBuilder("[");
        for (int i = 0; i < 10; i++) {
            if (i < blocks) bar.append("█");
            else bar.append("░");
        }
        bar.append("] ").append(String.format("%.1f", percentage)).append("%");

        event.getHook().editOriginalEmbeds(EmbedUtils.createEmbed(
                Color.BLUE,
                "⏳ Clearing DMs...\nProcessed: " + processed + "/" + total + "\n```\n" + bar.toString() + "\n```"
        )).queue(null, error -> {});
    }

    private void sendFinalUpdate(SlashCommandInteractionEvent event, int count) {
        event.getHook().editOriginalEmbeds(EmbedUtils.createSuccessEmbed(
                "Successfully completed DM clearing for all members."
        )).queue();
    }
}
