package EndZone.models;

import java.sql.Timestamp;

public class Strike {
    private int id;
    private String userId;
    private String reason;
    private String moderatorId;
    private Timestamp date;

    public Strike(int id, String userId, String reason, String moderatorId, Timestamp date) {
        this.id = id;
        this.userId = userId;
        this.reason = reason;
        this.moderatorId = moderatorId;
        this.date = date;
    }

    public int getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getReason() {
        return reason;
    }

    public String getModeratorId() {
        return moderatorId;
    }

    public Timestamp getDate() {
        return date;
    }

    public Timestamp getTimestamp() {
        return date;
    }
}
