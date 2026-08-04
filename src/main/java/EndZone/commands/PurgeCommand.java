package EndZone.commands;

import EndZone.EndZone;
import EndZone.models.ModAction;
import EndZone.services.DataService;
import EndZone.services.ServiceManager;
import EndZone.utils.EmbedUtils;
import EndZone.utils.PermissionUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.awt.Color;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class PurgeCommand implements Command {
    private final EndZone bot;
    private final DataService dataService;

    public PurgeCommand(EndZone bot) {
        this.bot = bot;
        this.dataService = ServiceManager.getDataService();
    }

    @Override
    public List<CommandData> getCommandDataList() {
        return List.of(Commands.slash("purge", "Delete up to 1000 messages")
                .addOption(OptionType.INTEGER, "amount", "The number of messages to delete (1-1000)", true)
                .addOption(OptionType.USER, "user", "Only delete messages from this user", false));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!PermissionUtils.isModerator(event.getMember(), ServiceManager.getConfig())) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("You don't have permission to use this command.")).setEphemeral(true).queue();
            return;
        }

        int amount = event.getOption("amount").getAsInt();
        User targetUser = event.getOption("user") != null ? event.getOption("user").getAsUser() : null;

        if (amount < 1 || amount > 1000) {
            event.reply("❌ Please provide a number between 1 and 1000.").setEphemeral(true).queue();
            return;
        }

        event.deferReply().setEphemeral(true).queue();
        
        if (targetUser != null) {
            event.getGuild().retrieveMemberById(targetUser.getId()).queue(targetMember -> {
                if (!PermissionUtils.canModerate(event.getMember(), targetMember) && !event.getUser().getId().equals(targetUser.getId())) {
                    event.getHook().editOriginal("❌ You cannot purge messages from this user due to role hierarchy.").queue();
                    return;
                }
                doPurgeManual(event, amount, targetUser);
            }, error -> doPurgeManual(event, amount, targetUser));
        } else {
            doPurgeManual(event, amount, null);
        }
    }

    private void doPurgeManual(SlashCommandInteractionEvent event, int amount, User targetUser) {
        TextChannel channel = event.getChannel().asTextChannel();
        if (targetUser != null) {
            List<Message> userMessages = new ArrayList<>();
            searchUserMessagesBatchManual(channel, event, targetUser, amount, userMessages, null);
        } else {
            channel.getHistory().retrievePast(Math.min(amount, 100)).queue(messages -> {
                if (messages.isEmpty()) {
                    event.getHook().editOriginal("❌ No messages found to delete.").setEmbeds().setComponents().queue();
                    return;
                }

                bulkDeleteMessages(channel, messages, () -> {
                    dataService.saveModAction(ModAction.ActionType.PURGE, event.getUser().getId(), event.getUser().getName(),
                            "channel", channel.getName(), "Purged " + messages.size() + " messages", event.getGuild().getName(), event.getChannel().getName(), 0, messages.size());

                    EmbedBuilder purgeEmbed = new EmbedBuilder()
                            .setTitle("🗑️ Messages Purged")
                            .setDescription(String.format("**%d** messages were purged in %s\n", messages.size(), channel.getAsMention()))
                            .addField("Moderator", event.getUser().getName(), false)
                            .setColor(Color.BLUE)
                            .setTimestamp(Instant.now());

                    ServiceManager.getLoggingService().logAction(event.getGuild(), "moderation-logs", purgeEmbed.build());

                    event.getHook().editOriginal("✅ Successfully deleted " + messages.size() + " messages.").setEmbeds().setComponents().queue();
                });
            });
        }
    }

    private void searchUserMessagesBatchManual(TextChannel channel, SlashCommandInteractionEvent event, User targetUser, int amount, List<Message> userMessages, String lastMessageId) {
        if (userMessages.size() >= amount) {
            performFinalPurge(channel, event, userMessages, targetUser);
            return;
        }

        if (lastMessageId == null) {
            channel.getHistory().retrievePast(100).queue(messages -> {
                processMessages(channel, event, targetUser, amount, userMessages, messages);
            }, error -> performFinalPurge(channel, event, userMessages, targetUser));
        } else {
            channel.getHistoryBefore(lastMessageId, 100).queue(history -> {
                List<Message> messages = history.getRetrievedHistory();
                processMessages(channel, event, targetUser, amount, userMessages, messages);
            }, error -> performFinalPurge(channel, event, userMessages, targetUser));
        }
    }

    private void processMessages(TextChannel channel, SlashCommandInteractionEvent event, User targetUser, int amount, List<Message> userMessages, List<Message> messages) {
        if (messages.isEmpty()) {
            performFinalPurge(channel, event, userMessages, targetUser);
            return;
        }

        for (Message m : messages) {
            if (m.getAuthor().getId().equals(targetUser.getId())) {
                userMessages.add(m);
            }
            if (userMessages.size() >= amount) break;
        }

        if (userMessages.size() < amount && messages.size() == 100) {
            searchUserMessagesBatchManual(channel, event, targetUser, amount, userMessages, messages.get(99).getId());
        } else {
            performFinalPurge(channel, event, userMessages, targetUser);
        }
    }

    private void performFinalPurge(TextChannel channel, SlashCommandInteractionEvent event, List<Message> userMessages, User targetUser) {
        if (userMessages.isEmpty()) {
            event.getHook().editOriginal("❌ No messages found from " + targetUser.getName() + " to delete.").setEmbeds().setComponents().queue();
            return;
        }

        bulkDeleteMessages(channel, userMessages, () -> {
            dataService.saveModAction(ModAction.ActionType.PURGE, event.getUser().getId(), event.getUser().getName(),
                    "user", targetUser.getName(), "Purged " + userMessages.size() + " messages from " + targetUser.getName(), event.getGuild().getName(), event.getChannel().getName(), 0, userMessages.size());

            EmbedBuilder purgeEmbed = new EmbedBuilder()
                    .setTitle("🗑️ Messages Purged")
                    .setDescription(String.format("**%d** messages from %s were purged in %s\n", userMessages.size(), targetUser.getAsMention(), channel.getAsMention()))
                    .addField("Moderator", event.getUser().getName(), false)
                    .setColor(Color.BLUE)
                    .setTimestamp(Instant.now());

            ServiceManager.getLoggingService().logAction(event.getGuild(), "moderation-logs", purgeEmbed.build());

            event.getHook().editOriginal("✅ Successfully deleted " + userMessages.size() + " messages from " + targetUser.getName() + ".").setEmbeds().setComponents().queue();
        });
    }

    private void bulkDeleteMessages(TextChannel channel, List<Message> messages, Runnable onComplete) {
        if (messages.isEmpty()) {
            onComplete.run();
            return;
        }

        List<CompletableFuture<Void>> deleteFutures = new ArrayList<>();
        for (int i = 0; i < messages.size(); i += 100) {
            int endIndex = Math.min(i + 100, messages.size());
            List<Message> batch = messages.subList(i, endIndex);
            
            CompletableFuture<Void> future = new CompletableFuture<>();
            deleteFutures.add(future);
            
            if (batch.size() == 1) {
                batch.get(0).delete().queue(s -> future.complete(null), e -> future.complete(null));
            } else {
                channel.deleteMessages(batch).queue(s -> future.complete(null), e -> future.complete(null));
            }
        }
        
        CompletableFuture.allOf(deleteFutures.toArray(new CompletableFuture[0])).thenRun(onComplete);
    }
}
