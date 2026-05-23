package EndZone.models;

import java.sql.Timestamp;
import java.util.Objects;

public class VoiceChannelRecord {
    private final int id;
    private final String channelId;
    private final String channelName;
    private final String creatorId;
    private final String creatorName;
    private final String guildId;
    private final String guildName;
    private final String categoryId;
    private final int userLimit;
    private final String channelType;
    private final Timestamp createdAt;
    private final Timestamp deletedAt;
    private final boolean isActive;

    public VoiceChannelRecord(int id, String channelId, String channelName, String creatorId, String creatorName, String guildId, String guildName, String categoryId, int userLimit, String channelType, Timestamp createdAt, Timestamp deletedAt, boolean isActive) {
        this.id = id;
        this.channelId = channelId;
        this.channelName = channelName;
        this.creatorId = creatorId;
        this.creatorName = creatorName;
        this.guildId = guildId;
        this.guildName = guildName;
        this.categoryId = categoryId;
        this.userLimit = userLimit;
        this.channelType = channelType;
        this.createdAt = createdAt;
        this.deletedAt = deletedAt;
        this.isActive = isActive;
    }

    public int getId() {
        return id;
    }

    public String getChannelId() {
        return channelId;
    }

    public String getChannelName() {
        return channelName;
    }

    public String getCreatorId() {
        return creatorId;
    }

    public String getCreatorName() {
        return creatorName;
    }

    public String getGuildId() {
        return guildId;
    }

    public String getGuildName() {
        return guildName;
    }

    public String getCategoryId() {
        return categoryId;
    }

    public int getUserLimit() {
        return userLimit;
    }

    public String getChannelType() {
        return channelType;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public Timestamp getDeletedAt() {
        return deletedAt;
    }

    public boolean isActive() {
        return isActive;
    }

    public boolean isPermanent() {
        return "PERMANENT".equalsIgnoreCase(channelType);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VoiceChannelRecord that = (VoiceChannelRecord) o;
        return channelId.equals(that.channelId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(channelId);
    }

    @Override
    public String toString() {
        return "VoiceChannelRecord{" +
                "id=" + id +
                ", channelId='" + channelId + '\'' +
                ", channelName='" + channelName + '\'' +
                ", creatorId='" + creatorId + '\'' +
                ", creatorName='" + creatorName + '\'' +
                ", guildId='" + guildId + '\'' +
                ", guildName='" + guildName + '\'' +
                ", categoryId='" + categoryId + '\'' +
                ", userLimit=" + userLimit +
                ", channelType='" + channelType + '\'' +
                ", createdAt=" + createdAt +
                ", deletedAt=" + deletedAt +
                ", isActive=" + isActive +
                '}';
    }
}
