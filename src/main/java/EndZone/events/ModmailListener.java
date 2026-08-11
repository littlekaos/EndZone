package EndZone.events;

import EndZone.config.BotConfig;
import EndZone.models.ModmailSession;
import EndZone.services.ModmailService;
import EndZone.services.ServiceManager;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ModmailListener extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(ModmailListener.class);

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot() || event.isWebhookMessage()) {
            return;
        }

        ModmailService modmail = ServiceManager.getModmailService();
        if (modmail == null) return;

        // Incoming user DMs → CourtZone Ticket Zone channel
        if (!event.isFromGuild() && event.getChannelType() == ChannelType.PRIVATE) {
            if (EndZone.forms.EndZoneForm.hasActiveForm(event.getAuthor().getId())) {
                return;
            }
            handleIncomingDm(event, modmail);
            return;
        }

        if (!event.isFromGuild()) return;

        ModmailSession session = modmail.findByChannel(event.getChannel().getId());
        if (session == null || !session.isOpen()) {
            return;
        }

        modmail.handleStaffOrUserGuildMessage(event.getMessage(), session);
    }

    @Override
    public void onMessageUpdate(MessageUpdateEvent event) {
        if (event.getAuthor().isBot()) {
            return;
        }
        if (event.isFromGuild() || event.getChannelType() != ChannelType.PRIVATE) {
            return;
        }

        ModmailService modmail = ServiceManager.getModmailService();
        if (modmail == null) return;

        ModmailSession session = modmail.findOpenDmByUser(event.getAuthor().getId());
        if (session == null || !session.isOpen()) {
            return;
        }

        String oldContent = ServiceManager.getMessageCache().getMessageContent(event.getMessageId());
        String newContent = event.getMessage().getContentDisplay();
        if (oldContent == null || oldContent.equals("Unknown content")) {
            oldContent = "*unknown*";
        }
        if (newContent == null) {
            newContent = "";
        }
        if (oldContent.equals(newContent)) {
            ServiceManager.getMessageCache().cacheMessage(event.getMessage(), event.getAuthor().getId());
            return;
        }

        modmail.notifyUserMessageEdited(session, oldContent, newContent);
        ServiceManager.getMessageCache().cacheMessage(event.getMessage(), event.getAuthor().getId());
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String id = event.getComponentId();
        if (!id.equals(BotConfig.MODMAIL_CAT_UNBAN)
                && !id.equals(BotConfig.MODMAIL_CAT_GEM)
                && !id.equals(BotConfig.MODMAIL_CAT_GENERAL)) {
            return;
        }

        ModmailService modmail = ServiceManager.getModmailService();
        if (modmail == null) {
            event.reply("Modmail is not ready yet.").setEphemeral(true).queue();
            return;
        }

        event.deferReply(true).queue();
        modmail.handleCategorySelection(event.getUser(), id,
                session -> {
                    event.getHook().editOriginal("Category selected — your ticket is open.").queue();
                    if (event.getMessage() != null) {
                        event.getMessage().editMessageComponents().queue(ok -> {}, ignored -> {});
                    }
                    if (session != null) {
                        logger.info("[Modmail] Category {} selected by {} → channel {}", id, event.getUser().getId(), session.getChannelId());
                    }
                },
                error -> {
                    if ("COMING_SOON".equals(error)) {
                        event.getHook().editOriginal("Coming soon!").queue();
                        return;
                    }
                    event.getHook().editOriginal("❌ " + error).queue();
                }
        );
    }

    private void handleIncomingDm(MessageReceivedEvent event, ModmailService modmail) {
        modmail.openDmThread(event.getAuthor(), event.getMessage(),
                session -> {
                    if (session != null) {
                        logger.info("[Modmail] DM from {} routed to channel {}", event.getAuthor().getId(), session.getChannelId());
                    }
                },
                error -> {
                    logger.error("[Modmail] Failed to open DM channel: {}", error);
                    event.getChannel().sendMessage("Sorry, staff modmail isn't available right now. Please try again later.").queue();
                }
        );
    }
}
