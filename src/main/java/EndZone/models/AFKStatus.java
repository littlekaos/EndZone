package EndZone.models;

public class AFKStatus {
    private final String userId;
    private final String reason;
    private final long timestamp;
    private final String originalNickname;

    public AFKStatus(String userId, String reason, long timestamp, String originalNickname) {
        this.userId = userId;
        this.reason = reason;
        this.timestamp = timestamp;
        this.originalNickname = originalNickname;
    }

    public String getUserId() {
        return userId;
    }

    public String getReason() {
        return reason;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getOriginalNickname() {
        return originalNickname;
    }
}
