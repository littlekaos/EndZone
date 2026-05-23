package EndZone.util;

import EndZone.services.ServiceManager;
import EndZone.config.BotConfig;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

public class ErrorService {
    public static void sendErrorNotification(String message) {
        if (ServiceManager.getJda() == null) {
            System.err.println("[ERROR] " + message);
            return;
        }
        
        TextChannel channel = ServiceManager.getJda().getTextChannelById("790177733207785472");
        if (channel != null) {
            channel.sendMessage("<@" + BotConfig.OWNER_USER_ID + "> 🚨 **Bot Error Notification**\n\n" + message).queue(
                null,
                err -> System.err.println("[ERROR] Failed to send error notification: " + err.getMessage())
            );
        } else {
            System.err.println("[ERROR] Headquarters channel not found: " + message);
        }
    }
}
