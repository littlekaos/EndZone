package EndZone.events;

import EndZone.EndZone;
import EndZone.config.BotConfig;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TicketEventListener extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(TicketEventListener.class);
    private final EndZone bot;

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
        if (!authorId.equals("557628353928036352") && !authorName.toLowerCase().contains("ticket tool")) return;

        // Ignore close confirmation messages
        String content = message.getContentRaw().toLowerCase();
        if (content.contains("are you sure") && content.contains("close this ticket")) return;

        if (!(message.getChannel() instanceof TextChannel channel)) return;

        // Category check
        if (!isTicketCategory(channel)) return;

        // Initial ticket messages from Ticket Tool ALWAYS have buttons (ActionRows)
        if (message.getButtons().isEmpty()) return;

        // Determine if it's an EndZone or ZRE ticket by checking the button label
        boolean tempIsEz = false;
        boolean foundServerButton = false;
        for (Button button : message.getButtons()) {
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
        // Match <@123> or <@!123> or even just 18 digit ID
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
        String judgeRoleId;
        String juryRoleId;
        String extraRoleId = null;
        
        if (isEndzone) {
            judgeRoleId = BotConfig.COURT_THE_JUDGE_EZ_ROLE_ID;
            juryRoleId = BotConfig.COURT_THE_DISTRICT_ATTORNIES_EZ_ROLE_ID;
            extraRoleId = BotConfig.COURT_THE_JURY_EZ_ROLE_ID;
        } else {
            judgeRoleId = BotConfig.COURT_THE_JUDGE_ZRE_ROLE_ID;
            juryRoleId = BotConfig.COURT_THE_JURY_ZRE_ROLE_ID;
        }
        
        StringBuilder greeting = new StringBuilder("Hello ");
        if (mention != null) {
            greeting.append(mention);
        } else {
            greeting.append("there");
        }
        greeting.append("! A <@&").append(judgeRoleId).append("> or a <@&").append(juryRoleId).append(">");
        
        if (extraRoleId != null) {
            greeting.append(" or a <@&").append(extraRoleId).append(">");
        }
        
        greeting.append(" will be with you soon!\n\n");
        
        String message = greeting.toString() +
                "## **__This is what you need to fill a ticket to get unbanned, anything else may end up in a deny.__**\n\n" +
                "➜ **__Discord Account ID:__** e.g. 435303327450791936\n\n" +
                "**How to get your Discord Account ID on Computer or mobile:**\n" +
                "**PC:** Settings (Gear Icon) > Scroll to Advanced > Enable Developer Mode > Right click on your profile picture and go down and click Copy ID.\n" +
                "**Mobile:** Click your profile in the lower right hand side > Settings (Gear Icon) > Scroll down to Advanced > Enable Developer Mode > Click your profile picture > 3 dots in the right hand side > Click Copy User ID\n\n" +
                "➜ **__Reason For Ban:__**\n\n" +
                "➜ **__Reason For Unban:__**\n\n" +
                "## **__Short answers about your reason for ban and unban WILL be an auto deny.__**";
        
        channel.sendMessage(message).queue();
    }
}
