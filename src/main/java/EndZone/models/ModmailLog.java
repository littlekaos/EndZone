package EndZone.models;

public class ModmailLog {
    private final int id;
    private final String logUuid;
    private final int sessionId;
    private final String userId;
    private final String closedById;
    private final String closedByName;
    private final String category;
    private final String transcript;
    private final long createdAt;
    private final long closedAt;
    private final String discordUrl;

    public ModmailLog(int id, String logUuid, int sessionId, String userId,
                      String closedById, String closedByName, String category,
                      String transcript, long createdAt, long closedAt) {
        this(id, logUuid, sessionId, userId, closedById, closedByName, category,
                transcript, createdAt, closedAt, null);
    }

    public ModmailLog(int id, String logUuid, int sessionId, String userId,
                      String closedById, String closedByName, String category,
                      String transcript, long createdAt, long closedAt, String discordUrl) {
        this.id = id;
        this.logUuid = logUuid;
        this.sessionId = sessionId;
        this.userId = userId;
        this.closedById = closedById;
        this.closedByName = closedByName;
        this.category = category;
        this.transcript = transcript;
        this.createdAt = createdAt;
        this.closedAt = closedAt;
        this.discordUrl = discordUrl;
    }

    public int getId() {
        return id;
    }

    public String getLogUuid() {
        return logUuid;
    }

    public int getSessionId() {
        return sessionId;
    }

    public String getUserId() {
        return userId;
    }

    public String getClosedById() {
        return closedById;
    }

    public String getClosedByName() {
        return closedByName;
    }

    public String getCategory() {
        return category;
    }

    public String getTranscript() {
        return transcript;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getClosedAt() {
        return closedAt;
    }

    /** HTTPS Discord CDN / jump URL for the transcript file, when available. */
    public String getDiscordUrl() {
        return discordUrl;
    }
}
