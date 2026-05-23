package EndZone.listeners;

import EndZone.services.ServiceManager;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.concurrent.TimeUnit;

public class StaffPingListener extends ListenerAdapter {

    private static final String ALPHA_BETAS_ROLE = "1483691965601157201";
    private static final String ALPHAS_ROLE = "1483685546080469033";

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;
        if (!event.isFromGuild()) return;

        // Only trigger in the access help channel
        String channelId = event.getChannel().getId();
        if (!channelId.equals(ServiceManager.getConfig().getAccessHelpChannelId())) return;

        String content = event.getMessage().getContentRaw();
        boolean pingedStaffRole = content.contains("<@&" + ALPHA_BETAS_ROLE + ">") ||
                content.contains("<@&" + ALPHAS_ROLE + ">");

        boolean pingedStaffMember = event.getMessage().getMentions().getMembers()
                .stream()
                .anyMatch(member -> member.getRoles()
                        .stream()
                        .anyMatch(role -> role.getId().equals(ALPHA_BETAS_ROLE) ||
                                role.getId().equals(ALPHAS_ROLE))
                );

        if (pingedStaffRole || pingedStaffMember) {
            String warningMessage = event.getAuthor().getAsMention() +
                    " ⚠️ **Warning:** Do not ping staff roles to get your roles! " +
                    "Please wait and someone will help you. Further violations may result in moderation action.";

            event.getMessage().delete().queue(
                    success -> event.getChannel().sendMessage(warningMessage).queue(
                            warning -> warning.delete().queueAfter(10, TimeUnit.SECONDS)
                    ),
                    error -> System.err.println("[ERROR] Failed to delete message: " + error.getMessage())
            );
        }
    }
}