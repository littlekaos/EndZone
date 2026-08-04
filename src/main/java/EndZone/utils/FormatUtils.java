package EndZone.utils;

public class FormatUtils {
    public static long parseDurationToMinutes(String duration) {
        if (duration == null || duration.isEmpty()) {
            return -1;
        }

        String input = duration.toLowerCase().trim();
        long multiplier = 1;

        if (input.endsWith("w")) {
            multiplier = 10080;
            input = input.substring(0, input.length() - 1);
        } else if (input.endsWith("d")) {
            multiplier = 1440;
            input = input.substring(0, input.length() - 1);
        } else if (input.endsWith("h")) {
            multiplier = 60;
            input = input.substring(0, input.length() - 1);
        } else if (input.endsWith("m")) {
            multiplier = 1;
            input = input.substring(0, input.length() - 1);
        } else if (input.endsWith("s")) {
            try {
                return (long) Math.ceil(Long.parseLong(input.substring(0, input.length() - 1)) / 60.0);
            } catch (NumberFormatException e) {
                return -1;
            }
        }

        try {
            long value = Long.parseLong(input);
            long totalMinutes = value * multiplier;
            
            if (totalMinutes > 40320) { // Max 28 days for timeouts
                return 40320;
            }
            if (totalMinutes < 1) {
                return -1;
            }
            return totalMinutes;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public static String resolveMentions(String message, net.dv8tion.jda.api.entities.Guild guild) {
        if (message == null || guild == null) return message;
        
        String resolved = message;
        
        // Resolve Channels: #channel-name -> <#id>
        java.util.regex.Pattern channelPattern = java.util.regex.Pattern.compile("#([a-zA-Z0-9_-]+)");
        java.util.regex.Matcher channelMatcher = channelPattern.matcher(resolved);
        StringBuilder sbChannel = new StringBuilder();
        int lastMatchEndChannel = 0;
        
        while (channelMatcher.find()) {
            String name = channelMatcher.group(1);
            net.dv8tion.jda.api.entities.channel.concrete.TextChannel channel = guild.getTextChannelsByName(name, true).stream().findFirst().orElse(null);
            
            sbChannel.append(resolved, lastMatchEndChannel, channelMatcher.start());
            if (channel != null) {
                sbChannel.append(channel.getAsMention());
            } else {
                sbChannel.append("#").append(name);
            }
            lastMatchEndChannel = channelMatcher.end();
        }
        sbChannel.append(resolved.substring(lastMatchEndChannel));
        resolved = sbChannel.toString();
        
        // Resolve Emojis: :emoji-name: -> <:name:id> (avoiding already resolved emojis like <:name:id>)
        java.util.regex.Pattern emojiPattern = java.util.regex.Pattern.compile("(?<!<):([a-zA-Z0-9_]+):");
        java.util.regex.Matcher emojiMatcher = emojiPattern.matcher(resolved);
        StringBuilder sbEmoji = new StringBuilder();
        int lastMatchEndEmoji = 0;
        
        while (emojiMatcher.find()) {
            String name = emojiMatcher.group(1);
            net.dv8tion.jda.api.entities.emoji.RichCustomEmoji emoji = guild.getEmojisByName(name, true).stream().findFirst().orElse(null);
            
            sbEmoji.append(resolved, lastMatchEndEmoji, emojiMatcher.start());
            if (emoji != null) {
                sbEmoji.append(emoji.getAsMention());
            } else {
                sbEmoji.append(":").append(name).append(":");
            }
            lastMatchEndEmoji = emojiMatcher.end();
        }
        sbEmoji.append(resolved.substring(lastMatchEndEmoji));
        resolved = sbEmoji.toString();
        
        return resolved;
    }

    public static String formatTimeAgo(long durationMs) {
        long seconds = durationMs / 1000;
        if (seconds < 60) return seconds + "s";
        long minutes = seconds / 60;
        if (minutes < 60) return minutes + "m";
        long hours = minutes / 60;
        if (hours < 24) return hours + "h";
        long days = hours / 24;
        return days + "d";
    }
}
