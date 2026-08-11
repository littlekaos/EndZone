package EndZone.models;

public class ModmailSession {
    public enum Kind {
        DM, TICKET
    }

    public enum Status {
        OPEN, CLOSED
    }

    private final int id;
    private final String userId;
    private final String guildId;
    private final String channelId;
    private final Kind kind;
    private final Status status;
    private final long createdAt;
    private final Long closedAt;
    private final String category;

    public ModmailSession(int id, String userId, String guildId, String channelId,
                          Kind kind, Status status, long createdAt, Long closedAt) {
        this(id, userId, guildId, channelId, kind, status, createdAt, closedAt, null);
    }

    public ModmailSession(int id, String userId, String guildId, String channelId,
                          Kind kind, Status status, long createdAt, Long closedAt, String category) {
        this.id = id;
        this.userId = userId;
        this.guildId = guildId;
        this.channelId = channelId;
        this.kind = kind;
        this.status = status;
        this.createdAt = createdAt;
        this.closedAt = closedAt;
        this.category = category;
    }

    public int getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getGuildId() {
        return guildId;
    }

    public String getChannelId() {
        return channelId;
    }

    public Kind getKind() {
        return kind;
    }

    public Status getStatus() {
        return status;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public Long getClosedAt() {
        return closedAt;
    }

    public String getCategory() {
        return category;
    }

    public boolean isOpen() {
        return status == Status.OPEN;
    }
}
