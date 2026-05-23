package EndZone.events;

import EndZone.EndZone;
import EndZone.config.BotConfig;
import EndZone.database.DatabaseService;
import EndZone.models.AFKStatus;
import EndZone.embeds.AccessHelpEmbed;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import EndZone.models.ModAction;
import EndZone.services.RestrictionService;
import EndZone.services.ServiceManager;
import EndZone.utils.EmbedUtils;
import EndZone.utils.PermissionUtils;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.Arrays;
import java.util.List;

public class MessageEventListener extends ListenerAdapter {
    private final EndZone bot;
    private String lastPromoMessageId = null;
    private String lastAccessHelpMessageId = null;

    public MessageEventListener(EndZone bot) {
        this.bot = bot;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) {
            return;
        }

        if (PermissionUtils.isBlacklisted(event.getAuthor())) {
            if (event.isFromGuild() && event.getGuild().getSelfMember().hasPermission(net.dv8tion.jda.api.Permission.MESSAGE_MANAGE)) {
                event.getMessage().delete().queue(
                    success -> {},
                    error -> System.err.println("[ERROR] Failed to delete message from blacklisted user: " + error.getMessage())
                );
            }
            return;
        }

        ServiceManager.getDataService().cacheUser(event.getAuthor());
        ServiceManager.getMessageCache().cacheMessage(event.getMessage(), event.getAuthor().getId());

        // AFK Logic: Remove AFK status if sender is AFK
        AFKStatus senderStatus = ServiceManager.getAFKService().getAFKStatus(event.getAuthor().getId());
        if (senderStatus != null) {
            ServiceManager.getAFKService().removeAFK(event.getAuthor().getId());
            
            // Restore nickname if possible
            if (event.isFromGuild() && event.getMember() != null) {
                net.dv8tion.jda.api.entities.Member selfMember = event.getGuild().getSelfMember();
                net.dv8tion.jda.api.entities.Member targetMember = event.getMember();
                if (selfMember.canInteract(targetMember) && selfMember.hasPermission(net.dv8tion.jda.api.Permission.NICKNAME_MANAGE)) {
                    targetMember.modifyNickname(senderStatus.getOriginalNickname()).queue(null, error -> {});
                }
            }

            event.getChannel().sendMessageEmbeds(EmbedUtils.createEmbed(
                    java.awt.Color.GREEN,
                    "Welcome back " + event.getAuthor().getAsMention() + ", I have removed your AFK!"
            )).queue(m -> m.delete().queueAfter(5, java.util.concurrent.TimeUnit.SECONDS));
        }

        // AFK Logic: Check for mentions of AFK users
        for (net.dv8tion.jda.api.entities.User mentionedUser : event.getMessage().getMentions().getUsers()) {
            AFKStatus status = ServiceManager.getAFKService().getAFKStatus(mentionedUser.getId());
            if (status != null) {
                long unixSeconds = status.getTimestamp() / 1000;
                event.getMessage().replyEmbeds(EmbedUtils.createEmbed(
                        java.awt.Color.YELLOW,
                        "**" + mentionedUser.getName() + "** is currently AFK: *" + status.getReason() + "* - <t:" + unixSeconds + ":R>"
                )).queue();
            }
        }

        if (event.isFromGuild()) {
            ServiceManager.getDataService().logMessage(
                    event.getGuild().getId(),
                    event.getChannel().getId(),
                    event.getMessageId(),
                    event.getAuthor().getId(),
                    event.getMessage().getContentRaw(),
                    "RECEIVED"
            );
        }

        Member member = event.getMember();
        boolean isStaff = PermissionUtils.isModerator(member, ServiceManager.getConfig());
        
        if (!isStaff) {
            if (handleRestrictions(event)) return;
        }

        String channelId = event.getChannel().getId();
        List<String> autoReactionChannels = ServiceManager.getConfig().getAutoReactionChannels();

        if (autoReactionChannels.contains(channelId)) {
            addReactionsToMessage(event.getMessage());
        }

        String content = event.getMessage().getContentRaw().toLowerCase();
        String eventNameChannel = ServiceManager.getConfig().getEventNameChannelId();

        if (channelId.equals(eventNameChannel) &&
                (content.contains("eventname") || content.contains("-eventname"))) {
            event.getMessage().reply("⚠️ **THAT IS NOT HOW YOU SUBMIT EVENT NAMES!** " +
                            "TO SUBMIT NAMES: use the `/eventname submit` SLASH COMMAND within this channel. " +
                            "If you don't correct this, your wins will be voided.")
                    .queue();
        }

        if (channelId.equals(ServiceManager.getConfig().getGeneralChatChannelId()) && !event.getAuthor().isBot()) {
            handlePromoMessage(event);
        }

        if (channelId.equals(ServiceManager.getConfig().getAccessHelpChannelId()) && !event.getAuthor().isBot()) {
            handleAccessHelpMessage(event);
        }
    }

    private void handlePromoMessage(MessageReceivedEvent event) {
        String promoText = ServiceManager.getConfig().getPromoMessage();
        
        if (lastPromoMessageId != null) {
            event.getChannel().deleteMessageById(lastPromoMessageId).queue(
                success -> sendNewPromo(event, promoText),
                error -> {
                    // If deletion fails (e.g. message already deleted), still try to find and send
                    findAndDeleteOldPromo(event, promoText);
                }
            );
        } else {
            findAndDeleteOldPromo(event, promoText);
        }
    }

    private void findAndDeleteOldPromo(MessageReceivedEvent event, String promoText) {
        event.getChannel().getHistory().retrievePast(20).queue(messages -> {
            for (Message m : messages) {
                if (m.getAuthor().getId().equals(event.getJDA().getSelfUser().getId()) && 
                    m.getContentRaw().equals(promoText)) {
                    m.delete().queue(v -> sendNewPromo(event, promoText), e -> sendNewPromo(event, promoText));
                    return;
                }
            }
            sendNewPromo(event, promoText);
        });
    }

    private void sendNewPromo(MessageReceivedEvent event, String promoText) {
        event.getChannel().sendMessage(promoText).queue(m -> {
            lastPromoMessageId = m.getId();
        });
    }

    private void handleAccessHelpMessage(MessageReceivedEvent event) {
        String promoText = ServiceManager.getConfig().getAccessHelpPromo();
        
        if (lastAccessHelpMessageId != null) {
            event.getChannel().deleteMessageById(lastAccessHelpMessageId).queue(
                success -> sendNewAccessHelp(event, promoText),
                error -> {
                    findAndDeleteOldAccessHelp(event, promoText);
                }
            );
        } else {
            findAndDeleteOldAccessHelp(event, promoText);
        }
    }

    private void findAndDeleteOldAccessHelp(MessageReceivedEvent event, String promoText) {
        event.getChannel().getHistory().retrievePast(20).queue(messages -> {
            for (Message m : messages) {
                if (m.getAuthor().getId().equals(event.getJDA().getSelfUser().getId()) &&
                        !m.getEmbeds().isEmpty()) {
                    m.delete().queue(v -> sendNewAccessHelp(event, promoText), e -> sendNewAccessHelp(event, promoText));
                    return;
                }
            }
            sendNewAccessHelp(event, promoText);
        });
    }

    private void sendNewAccessHelp(MessageReceivedEvent event, String promoText) {
        TextChannel channel = (TextChannel) event.getChannel();
        AccessHelpEmbed.sendOrUpdateAccessHelpEmbed(channel);
        // Note: lastAccessHelpMessageId won't be tracked here since AccessHelpEmbed
        // handles its own sending — that's fine, findAndDeleteOldAccessHelp covers it
    }

    private boolean handleRestrictions(MessageReceivedEvent event) {
        RestrictionService service = ServiceManager.getRestrictionService();
        if (service == null) return false;

        String channelId = event.getChannel().getId();
        Message message = event.getMessage();

        if (service.getMediaWithTextChannels().contains(channelId)) {
            if (!hasMedia(message)) {
                deleteAndWarn(event, "Images and links are required in this channel.");
                return true;
            }
        }

        if (service.getMediaOnlyChannels().contains(channelId)) {
            if (!hasMedia(message) || !hasOnlyMedia(message)) {
                deleteAndWarn(event, "Only images and links are allowed in this channel (no regular chat messages).");
                return true;
            }
        }

        if (service.getScreenshotOnlyChannels().contains(channelId)) {
            if (message.getAttachments().isEmpty() || !message.getContentRaw().isEmpty() ||
                message.getAttachments().stream().anyMatch(a -> !a.isImage())) {
                deleteAndWarn(event, "Only images/screenshots are allowed in this channel (no text).");
                return true;
            }
        }

        if (service.getNoMessageChannels().contains(channelId)) {
            deleteAndWarn(event, "Messages are not allowed in this channel.");
            return true;
        }

        if (service.getTextOnlyChannels().contains(channelId)) {
            if (hasMedia(message)) {
                deleteAndWarn(event, "Only text messages are allowed in this channel (no media, links, or attachments).");
                return true;
            }
        }

        if (service.getNoMediaChannels().contains(channelId)) {
            if (hasMedia(message)) {
                deleteAndWarn(event, "Media and links are not allowed in this channel.");
                return true;
            }
        }

        if (service.getNoContentChannels().contains(channelId)) {
            deleteAndWarn(event, "No content is allowed in this channel.");
            return true;
        }

        return false;
    }

    private boolean hasMedia(Message message) {
        return !message.getAttachments().isEmpty() || containsLinks(message.getContentRaw());
    }

    private boolean hasOnlyMedia(Message message) {
        String content = message.getContentRaw().trim();
        return content.isEmpty() || containsOnlyLinks(content);
    }

    private boolean containsLinks(String content) {
        return content.matches("(?s).*https?://\\S+.*");
    }

    private boolean containsOnlyLinks(String content) {
        String[] words = content.split("\\s+");
        return Arrays.stream(words).filter(w -> !w.isEmpty()).allMatch(w -> w.matches("https?://\\S+.*"));
    }

    private void deleteAndWarn(MessageReceivedEvent event, String reason) {
        event.getMessage().delete().queue();
        event.getChannel().sendMessageEmbeds(EmbedUtils.createWarningEmbed(reason))
                .queue(m -> m.delete().queueAfter(5, java.util.concurrent.TimeUnit.SECONDS));

        ServiceManager.getDataService().logMessage(event.getGuild().getId(), event.getChannel().getId(), 
                event.getMessageId(), event.getAuthor().getId(), event.getMessage().getContentRaw(), 
                "MODERATION_DELETE: " + reason);
        
        ServiceManager.getDataService().saveModAction(ModAction.ActionType.RESTRICT, ServiceManager.getJda().getSelfUser().getId(), 
                ServiceManager.getJda().getSelfUser().getName(), event.getAuthor().getId(), event.getAuthor().getName(), 
                "Auto-delete: " + reason, event.getGuild().getName(), event.getChannel().getName(), 0, 0);

        net.dv8tion.jda.api.EmbedBuilder logEmbed = new net.dv8tion.jda.api.EmbedBuilder()
                .setTitle("🛡️ Channel Restriction Triggered")
                .setColor(java.awt.Color.YELLOW)
                .setDescription(String.format("Restriction triggered by %s (%s)\n", event.getAuthor().getAsMention(), event.getAuthor().getName()))
                .addField("User ID", event.getAuthor().getId(), false)
                .addField("Channel", event.getChannel().getAsMention(), false)
                .addField("Reason", reason, false)
                .setTimestamp(java.time.Instant.now());
        ServiceManager.getLoggingService().logAction(event.getGuild(), "moderation-logs", logEmbed.build());
    }

    private void addReactionsToMessage(Message message) {
        try {
            String channelId = message.getChannel().getId();
            BotConfig.EmojiConfig emojiConfig = ServiceManager.getConfig().getChannelEmojiConfig(channelId);

            if (emojiConfig == null) {
                return;
            }

            Emoji emoji = null;

            if (emojiConfig.unicodeEmoji != null) {
                emoji = Emoji.fromUnicode(emojiConfig.unicodeEmoji);
            } else if (emojiConfig.customEmojiName != null && emojiConfig.customEmojiId != null) {
                emoji = Emoji.fromCustom(emojiConfig.customEmojiName, Long.parseLong(emojiConfig.customEmojiId), false);
            }

            if (emoji == null) {
                return;
            }

            message.addReaction(emoji).queue();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
