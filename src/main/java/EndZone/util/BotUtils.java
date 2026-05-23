package EndZone.util;

import net.dv8tion.jda.api.entities.Activity;

public class BotUtils {
    public static Activity parseActivity(String type, String status, String twitchUrl) {
        return switch (type.toUpperCase()) {
            case "PLAYING" -> Activity.playing(status);
            case "WATCHING" -> Activity.watching(status);
            case "LISTENING" -> Activity.listening(status);
            case "STREAMING" -> Activity.streaming(status, twitchUrl);
            default -> Activity.playing(status);
        };
    }
}
