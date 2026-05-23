package EndZone.events;

import EndZone.EndZone;
import EndZone.commands.SayCommand;
import EndZone.config.BotConfig;
import EndZone.repositories.SQLiteEventNameRepository;
import EndZone.services.ServiceManager;
import EndZone.utils.EmbedUtils;
import EndZone.utils.FormatUtils;
import EndZone.utils.PermissionUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

import java.awt.Color;
import java.time.Instant;
import java.util.UUID;

public class ModalEventListener extends ListenerAdapter {
    private final EndZone bot;

    public ModalEventListener(EndZone bot) {
        this.bot = bot;
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        if (PermissionUtils.isBlacklisted(event.getUser())) {
            event.reply("❌ You are blacklisted from using this bot.").setEphemeral(true).queue();
            return;
        }

        String modalId = event.getModalId();
        if (modalId.equals("eventNameModal")) {
            handleEventNameModal(event);
        } else if (modalId.equals("say_modal")) {
            handleSayModal(event);
        } else if (modalId.startsWith("eventping_modal")) {
            handleEventPingModal(event);
        }
    }

    private void handleSayModal(ModalInteractionEvent event) {
        String message = event.getValue("message").getAsString();
        String resolvedMessage = FormatUtils.resolveMentions(message, event.getGuild());
        
        String uuid = UUID.randomUUID().toString();
        SayCommand.addPendingMessage(uuid, resolvedMessage);

        event.reply("**Preview of your message:**\n\n" + resolvedMessage)
                .addComponents(ActionRow.of(
                        Button.success("say_confirm:" + uuid, "Send Message"),
                        Button.danger("say_cancel:" + uuid, "Cancel")
                ))
                .setEphemeral(true)
                .queue();
    }

    private void handleEventPingModal(ModalInteractionEvent event) {
        String modalId = event.getModalId();
        String destination = "both"; // Default
        if (modalId.contains(":")) {
            destination = modalId.split(":")[1];
        }

        String message = event.getValue("message").getAsString();
        String resolvedMessage = FormatUtils.resolveMentions(message, event.getGuild());
        
        String uuid = UUID.randomUUID().toString();
        SayCommand.addPendingMessage(uuid, resolvedMessage);

        String destinationDisplay = switch (destination) {
            case "drafting" -> "Drafting Things";
            case "countdown" -> "Event Countdowns";
            default -> "Both Channels";
        };

        event.reply("**Preview of your Event Ping message (" + destinationDisplay + "):**\n\n" + resolvedMessage)
                .addComponents(ActionRow.of(
                        Button.success("eventping_confirm:" + destination + ":" + uuid, "Send Ping"),
                        Button.danger("eventping_cancel:" + uuid, "Cancel")
                ))
                .setEphemeral(true)
                .queue();
    }

    private void handleEventNameModal(ModalInteractionEvent event) {
        String eventName = event.getValue("name").getAsString();
        String userId = event.getUser().getId();
        String username = event.getUser().getName();

        event.deferReply(true).queue();

        try {
            SQLiteEventNameRepository repository = ServiceManager.getEventNameRepository();
            repository.saveEventName(userId, eventName);

            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle(eventName)
                    .setColor(Color.BLUE)
                    .setFooter("Nickname of " + username + " (" + userId + ")")
                    .setTimestamp(Instant.now());

            bot.getLoggingService().logAction(event.getGuild(), "event-names", embed.build());

            event.getHook().sendMessageEmbeds(EmbedUtils.createEmbed(
                    Color.BLUE,
                    BotConfig.EZ_EMOJI_MENTION + " Your eventname has been recorded. " +
                            "You can play under the name \"" + eventName + "\" for all future " +
                            event.getGuild().getName() + " events. To change your name, do /eventname again"
            )).queue();

        } catch (Exception e) {
            System.err.println("Error saving event name for user " + userId + ": " + e.getMessage());
            e.printStackTrace();

            event.getHook().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                    "An error occurred while saving your event name. Please try again later."
            )).queue();
        }
    }
}
