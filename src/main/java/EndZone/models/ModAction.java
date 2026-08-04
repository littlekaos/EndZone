package EndZone.models;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class ModAction {
    public enum ActionType {
        BAN, UNBAN, WARN, MUTE, UNMUTE, TIMEOUT, UNTIMEOUT, KICK, PURGE, RESTRICT, UNRESTRICT
    }

    private final int id;
    private final ActionType actionType;
    private final String moderatorId;
    private final String moderatorName;
    private final String targetId;
    private final String targetName;
    private final String reason;
    private final String guildName;
    private final String channelName;
    private final long timestamp;
    private final int duration;
    private final int count;

    public ModAction(int id, ActionType actionType, String moderatorId, String moderatorName,
                     String targetId, String targetName, String reason,
                     long timestamp, int duration, int count) {
        this(id, actionType, moderatorId, moderatorName, targetId, targetName, reason, "Unknown Server", "Unknown Channel", timestamp, duration, count);
    }

    public ModAction(int id, ActionType actionType, String moderatorId, String moderatorName,
                     String targetId, String targetName, String reason,
                     String guildName, String channelName,
                     long timestamp, int duration, int count) {
        this.id = id;
        this.actionType = actionType;
        this.moderatorId = moderatorId;
        this.moderatorName = moderatorName;
        this.targetId = targetId;
        this.targetName = targetName;
        this.reason = reason;
        this.guildName = guildName;
        this.channelName = channelName;
        this.timestamp = timestamp;
        this.duration = duration;
        this.count = count;
    }

    public int getId() {
        return id;
    }

    public ActionType getActionType() {
        return actionType;
    }

    public String getModeratorId() {
        return moderatorId;
    }

    public String getModeratorName() {
        return moderatorName;
    }

    public String getTargetId() {
        return targetId;
    }

    public String getTargetName() {
        return targetName;
    }

    public String getReason() {
        return reason;
    }

    public String getGuildName() {
        return guildName;
    }

    public String getChannelName() {
        return channelName;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public int getDuration() {
        return duration;
    }

    public int getCount() {
        return count;
    }

    public String getFormattedDate() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm:ss")
                .withZone(ZoneId.systemDefault());
        return formatter.format(Instant.ofEpochMilli(timestamp));
    }
}
