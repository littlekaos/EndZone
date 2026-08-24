package EndZone.events;

import EndZone.EndZone;
import EndZone.config.BotConfig;
import EndZone.services.TicketToolLogImporter;
import EndZone.util.UnbanAppealMessage;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.components.buttons.Button;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TicketEventListener extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(TicketEventListener.class);
    private final EndZone bot;
    private final TicketToolLogImporter ticketLogImporter = new TicketToolLogImporter();

    public TicketEventListener(EndZone bot) {
        this.bot = bot;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        handleMessage(event.getMessage());
    }

    @Override
    public void onMessageUpdate(MessageUpdateEvent event) {
        handleMessage(event.getMessage());
    }

    private void handleMessage(Message message) {
        // Author check
        String authorId = message.getAuthor().getId();
        String authorName = message.getAuthor().getName();
        if (!authorId.equals(BotConfig.TICKET_TOOL_BOT_ID)
                && !authorId.equals(BotConfig.TICKET_TOOL_BOT_ID_ALT)
                && !authorName.toLowerCase().contains("ticket tool")) return;

        if (TicketToolLogImporter.isLogChannel(message.getChannel().getId())) {
            ticketLogImporter.importAsync(message);
            return;
        }

        // Ignore close confirmation messages
        String content = message.getContentRaw().toLowerCase();
        if (content.contains("are you sure") && content.contains("close this ticket")) return;

        if (!(message.getChannel() instanceof TextChannel channel)) return;

        // Category check
        if (!isTicketCategory(channel)) return;

        var buttons = message.getComponentTree().findAll(Button.class);
        if (buttons.isEmpty()) return;

        // Determine if it's an EndZone or ZRE ticket by checking the button label
        boolean tempIsEz = false;
        boolean foundServerButton = false;
        for (Button button : buttons) {
            String label = button.getLabel().toLowerCase();
            if (label.contains("endzone") || label.contains("ez")) {
                tempIsEz = true;
                foundServerButton = true;
                break;
            } else if (label.contains("zre")) {
                tempIsEz = false;
                foundServerButton = true;
                break;
            }
        }

        if (!foundServerButton) return;
        
        final boolean isEzTicket = tempIsEz;

        // Final check: scan history to ensure we haven't already posted the blurb in this channel
        channel.getHistory().retrievePast(50).queue(messages -> {
            boolean alreadySent = messages.stream()
                    .anyMatch(m -> m.getAuthor().getId().equals(message.getJDA().getSelfUser().getId()) && 
                                 m.getContentRaw().contains("This is what you need to fill a ticket"));
            
            if (alreadySent) return;

            // Find the user to ping by scanning history for any non-bot mention
            String mention = findUserToPing(channel, messages);
            
            // If we can't find a mention, we still send but with no specific name (fallback)
            sendAppealRules(channel, mention, isEzTicket);
        });
    }

    private boolean isTicketCategory(TextChannel channel) {
        if (channel.getParentCategoryId() != null && channel.getParentCategoryId().equals(BotConfig.TICKET_ZONE_CATEGORY_ID)) return true;
        if (channel.getParentCategory() != null) {
            String name = channel.getParentCategory().getName().toLowerCase();
            // Check for emoji or name
            return name.contains("ticket zone") || name.contains("ticket-zone") || name.contains("🎫");
        }
        return false;
    }

    private String findUserToPing(TextChannel channel, java.util.List<Message> messages) {
        // 1. Try mentions in messages (content)
        for (Message m : messages) {
            for (net.dv8tion.jda.api.entities.User u : m.getMentions().getUsers()) {
                if (!u.isBot()) return u.getAsMention();
            }
            // Check content string too just in case JDA didn't parse it as a mention
            String contentMention = extractMention(m.getContentRaw());
            if (contentMention != null) return contentMention;
        }

        // 2. Check embeds for mention-like patterns in more fields
        for (Message m : messages) {
            for (MessageEmbed embed : m.getEmbeds()) {
                String mention = extractMention(embed.getDescription());
                if (mention == null) mention = extractMention(embed.getTitle());
                if (mention == null && embed.getAuthor() != null) mention = extractMention(embed.getAuthor().getName());
                if (mention == null && embed.getFooter() != null) mention = extractMention(embed.getFooter().getText());
                
                if (mention == null) {
                    for (MessageEmbed.Field field : embed.getFields()) {
                        mention = extractMention(field.getValue());
                        if (mention != null) break;
                    }
                }
                if (mention != null) return mention;
            }
        }

        // 3. Try permission overrides (the user who opened the ticket usually has an override)
        for (net.dv8tion.jda.api.entities.PermissionOverride override : channel.getPermissionOverrides()) {
            if (override.isMemberOverride()) {
                net.dv8tion.jda.api.entities.Member member = override.getMember();
                if (member != null && !member.getUser().isBot()) {
                    // Ignore the bot itself
                    if (member.getUser().getId().equals(channel.getJDA().getSelfUser().getId())) continue;
                    return member.getAsMention();
                }
            }
        }
        
        return null;
    }

    private String extractMention(String text) {
        if (text == null) return null;
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("<@!?(\\d+)>").matcher(text);
        if (matcher.find()) {
            return matcher.group(0);
        }
        
        // Fallback: look for 17-20 digit ID in the text
        java.util.regex.Matcher idMatcher = java.util.regex.Pattern.compile("\\b(\\d{17,20})\\b").matcher(text);
        if (idMatcher.find()) {
            return "<@" + idMatcher.group(1) + ">";
        }
        
        return null;
    }

    private void sendAppealRules(TextChannel channel, String mention, boolean isEndzone) {
        UnbanAppealMessage.send(channel, mention, isEndzone);
    }
}
