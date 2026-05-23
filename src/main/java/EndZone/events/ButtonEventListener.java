package EndZone.events;

import EndZone.EndZone;
import EndZone.commands.SayCommand;
import EndZone.config.BotConfig;
import EndZone.services.ServiceManager;
import EndZone.utils.PermissionUtils;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.ActionRow;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ButtonEventListener extends ListenerAdapter {

    private final EndZone bot;
    private final Map<String, Integer> demotionListPageState = new ConcurrentHashMap<>();

    public ButtonEventListener(EndZone bot) {
        this.bot = bot;
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        if (PermissionUtils.isBlacklisted(event.getUser())) {
            event.reply("❌ You are blacklisted from using this bot.").setEphemeral(true).queue();
            return;
        }

        String buttonId = event.getComponentId();

        if (buttonId.startsWith("demotion_")) {
            handleDemotionListNavigation(event);
        } else if (buttonId.startsWith("say_confirm:")) {
            handleSayConfirm(event);
        } else if (buttonId.startsWith("say_cancel:")) {
            handleSayCancel(event);
        } else if (buttonId.startsWith("eventping_confirm:")) {
            handleEventPingConfirm(event);
        } else if (buttonId.startsWith("eventping_cancel:")) {
            handleEventPingCancel(event);
        }
    }

    private void handleEventPingConfirm(ButtonInteractionEvent event) {
        String[] parts = event.getComponentId().split(":");
        String destination = parts[1];
        String uuid = parts[2];
        String message = SayCommand.getPendingMessage(uuid);

        if (message == null) {
            event.reply("❌ This preview has expired or already been sent.").setEphemeral(true).queue();
            return;
        }

        String draftingThingsId = BotConfig.DRAFTING_THINGS_CHANNEL_ID;
        String eventCountdownsId = BotConfig.EVENT_COUNTDOWNS_CHANNEL_ID;
        String ezEmojiId = BotConfig.EZ_EMOJI_ID;

        boolean sendToDrafting = destination.equals("drafting") || destination.equals("both");
        boolean sendToCountdown = destination.equals("countdown") || destination.equals("both");

        if (sendToDrafting) {
            TextChannel draftingThings = event.getGuild().getTextChannelById(draftingThingsId);
            if (draftingThings != null) {
                draftingThings.sendMessage(message).queue(msg -> {
                    msg.addReaction(Emoji.fromCustom(BotConfig.EZ_EMOJI_NAME, Long.parseLong(ezEmojiId), false)).queue();
                });
            }
        }

        if (sendToCountdown) {
            TextChannel eventCountdowns = event.getGuild().getTextChannelById(eventCountdownsId);
            if (eventCountdowns != null) {
                eventCountdowns.sendMessage(message).queue(msg -> {
                    msg.addReaction(Emoji.fromCustom(BotConfig.EZ_EMOJI_NAME, Long.parseLong(ezEmojiId), false)).queue();
                });
            }
        }

        event.deferEdit().queue();
        String successMessage = switch (destination) {
            case "drafting" -> "✅ Event Ping sent to Drafting Things!";
            case "countdown" -> "✅ Event Ping sent to Event Countdowns!";
            default -> "✅ Event Ping sent to both channels!";
        };
        event.getHook().editOriginal(successMessage).setComponents().queue();
    }

    private void handleEventPingCancel(ButtonInteractionEvent event) {
        String uuid = event.getComponentId().split(":")[1];
        SayCommand.getPendingMessage(uuid); // Clear from memory

        event.deferEdit().queue();
        event.getHook().deleteOriginal().queue();
    }

    private void handleSayConfirm(ButtonInteractionEvent event) {
        String uuid = event.getComponentId().split(":")[1];
        String message = SayCommand.getPendingMessage(uuid);

        if (message == null) {
            event.reply("❌ This preview has expired or already been sent.").setEphemeral(true).queue();
            return;
        }

        event.getChannel().sendMessage(message).queue();
        event.deferEdit().queue();
        event.getHook().deleteOriginal().queue();
    }

    private void handleSayCancel(ButtonInteractionEvent event) {
        String uuid = event.getComponentId().split(":")[1];
        SayCommand.getPendingMessage(uuid); // Clear from memory

        event.deferEdit().queue();
        event.getHook().deleteOriginal().queue();
    }

    private void handleDemotionListNavigation(ButtonInteractionEvent event) {
        event.deferEdit().queue();

        List<MessageEmbed> pages = ServiceManager.getDemotionService().buildPages();

        if (pages.isEmpty()) {
            event.getHook().editOriginal("❌ Demotion list is empty.").queue();
            return;
        }

        String buttonId = event.getComponentId();
        String userId = event.getUser().getId();
        int currentPage = demotionListPageState.getOrDefault(userId, 0);
        int totalPages = pages.size();

        int newPage = switch (buttonId) {
            case "demotion_prev" -> Math.max(0, currentPage - 1);
            case "demotion_next" -> Math.min(totalPages - 1, currentPage + 1);
            case "demotion_first" -> 0;
            case "demotion_last" -> totalPages - 1;
            default -> currentPage;
        };

        if (newPage >= totalPages) {
            newPage = totalPages - 1;
        }

        demotionListPageState.put(userId, newPage);

        MessageEmbed embed = pages.get(newPage);
        ActionRow buttons = ServiceManager.getDemotionService().buildActionRow(newPage + 1, totalPages);

        event.getHook().editOriginalEmbeds(embed).setComponents(buttons).queue();
    }
}
