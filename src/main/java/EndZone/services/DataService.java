package EndZone.services;

import EndZone.database.DatabaseService;
import EndZone.models.ModAction;
// import EndZone.models.WarnRecord; // Skipped per instructions

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.entities.User;

public class DataService {
    private UserCache userCache;

    public DataService() {
        // Tables are initialized in DatabaseService
    }

    public void setUserCache(UserCache userCache) {
        this.userCache = userCache;
    }

    public void cacheUser(User user) {
        if (userCache != null) {
            userCache.cacheUser(user);
        }
    }

    public User retrieveUser(String userId) {
        if (userCache != null) {
            return userCache.retrieveUser(userId);
        }
        return null;
    }

    public void saveModAction(ModAction.ActionType action, String moderatorId, String moderatorName, String targetId, String targetName, String reason, int duration, int count) {
        saveModAction(action, moderatorId, moderatorName, targetId, targetName, reason, "Unknown Server", "Unknown Channel", duration, count, System.currentTimeMillis());
    }

    public void saveModAction(ModAction.ActionType action, String moderatorId, String moderatorName, String targetId, String targetName, String reason, String guildName, String channelName, int duration, int count) {
        saveModAction(action, moderatorId, moderatorName, targetId, targetName, reason, guildName, channelName, duration, count, System.currentTimeMillis());
    }

    public void saveModAction(ModAction.ActionType action, String moderatorId, String moderatorName, String targetId, String targetName, String reason, int duration, int count, long timestamp) {
        saveModAction(action, moderatorId, moderatorName, targetId, targetName, reason, "Unknown Server", "Unknown Channel", duration, count, timestamp);
    }

    public void saveModAction(ModAction.ActionType action, String moderatorId, String moderatorName, String targetId, String targetName, String reason, String guildName, String channelName, int duration, int count, long timestamp) {
        try (Connection conn = DatabaseService.getConnection()) {
            String sql = "INSERT INTO ez_moderation_analytics (action, moderatorId, moderatorName, targetId, targetName, reason, guildName, channelName, timestamp, duration, count) " +
                       "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, action.name());
                pstmt.setString(2, moderatorId);
                pstmt.setString(3, moderatorName);
                pstmt.setString(4, targetId);
                pstmt.setString(5, targetName);
                pstmt.setString(6, reason);
                pstmt.setString(7, guildName);
                pstmt.setString(8, channelName);
                pstmt.setLong(9, timestamp);
                pstmt.setInt(10, duration);
                pstmt.setInt(11, count);
                pstmt.executeUpdate();
            }
        } catch (Exception e) {
            System.err.println("Error saving moderation action: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void saveBanAction(String moderatorId, String moderatorName, String targetId, String targetName, String reason) {
        saveModAction(ModAction.ActionType.BAN, moderatorId, moderatorName, targetId, targetName, reason, 0, 0);
    }

    public void saveUnbanAction(String moderatorId, String moderatorName, String targetId) {
        saveModAction(ModAction.ActionType.UNBAN, moderatorId, moderatorName, targetId, "Unknown", "", 0, 0);
    }

    /* 
    public void addWarning(WarnRecord record) {
        try (Connection conn = DatabaseService.getConnection()) {
            String sql = "INSERT INTO ez_warnings (userId, moderatorId, reason, timestamp) VALUES (?, ?, ?, ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, record.getUserId());
                pstmt.setString(2, record.getModeratorId());
                pstmt.setString(3, record.getReason());
                pstmt.setLong(4, record.getTimestamp());
                pstmt.executeUpdate();
            }
        } catch (Exception e) {
            System.err.println("Error adding warning: " + e.getMessage());
        }
    }

    public List<WarnRecord> getWarningsForUser(String userId) {
        List<WarnRecord> warnings = new ArrayList<>();
        try (Connection conn = DatabaseService.getConnection()) {
            String sql = "SELECT userId, moderatorId, reason, timestamp FROM ez_warnings WHERE userId = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, userId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        warnings.add(new WarnRecord(
                            rs.getString("userId"),
                            rs.getString("moderatorId"),
                            rs.getString("reason"),
                            rs.getLong("timestamp")
                        ));
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error getting warnings: " + e.getMessage());
        }
        return warnings;
    }
    */

    public void setMuteRoleId(String guildId, String roleId) {
        try (Connection conn = DatabaseService.getConnection()) {
            // Using REPLACE for SQLite
            String sql = "INSERT OR REPLACE INTO ez_mute_config (guildId, muteRoleId) VALUES (?, ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, guildId);
                pstmt.setString(2, roleId);
                pstmt.executeUpdate();
            }
        } catch (Exception e) {
            System.err.println("Error setting mute role: " + e.getMessage());
        }
    }

    public String getMuteRoleId(String guildId) {
        try (Connection conn = DatabaseService.getConnection()) {
            String sql = "SELECT muteRoleId FROM ez_mute_config WHERE guildId = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, guildId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("muteRoleId");
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error getting mute role: " + e.getMessage());
        }
        return null;
    }

    public void addMute(String guildId, String userId, long unmuteTime) {
        try (Connection conn = DatabaseService.getConnection()) {
            // Using REPLACE for SQLite
            String sql = "INSERT OR REPLACE INTO ez_active_mutes (guildId, userId, unmuteTime) VALUES (?, ?, ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, guildId);
                pstmt.setString(2, userId);
                pstmt.setLong(3, unmuteTime);
                pstmt.executeUpdate();
            }
        } catch (Exception e) {
            System.err.println("Error adding mute: " + e.getMessage());
        }
    }

    public void removeMute(String guildId, String userId) {
        try (Connection conn = DatabaseService.getConnection()) {
            String sql = "DELETE FROM ez_active_mutes WHERE guildId = ? AND userId = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, guildId);
                pstmt.setString(2, userId);
                pstmt.executeUpdate();
            }
        } catch (Exception e) {
            System.err.println("Error removing mute: " + e.getMessage());
        }
    }

    public boolean isMuted(String guildId, String userId) {
        try (Connection conn = DatabaseService.getConnection()) {
            String sql = "SELECT 1 FROM ez_active_mutes WHERE guildId = ? AND userId = ? AND (unmuteTime = 0 OR unmuteTime > ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, guildId);
                pstmt.setString(2, userId);
                pstmt.setLong(3, System.currentTimeMillis());
                try (ResultSet rs = pstmt.executeQuery()) {
                    return rs.next();
                }
            }
        } catch (Exception e) {
            System.err.println("Error checking mute status: " + e.getMessage());
        }
        return false;
    }

    public boolean hasModAction(ModAction.ActionType action, String targetId, String moderatorId, String reason) {
        try (Connection conn = DatabaseService.getConnection()) {
            String sql = "SELECT 1 FROM ez_moderation_analytics WHERE action = ? AND targetId = ? AND moderatorId = ? AND reason = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, action.name());
                pstmt.setString(2, targetId);
                pstmt.setString(3, moderatorId);
                pstmt.setString(4, reason);
                try (ResultSet rs = pstmt.executeQuery()) {
                    return rs.next();
                }
            }
        } catch (Exception e) {
            System.err.println("Error checking mod action existence: " + e.getMessage());
        }
        return false;
    }

    public boolean hasSimilarModAction(ModAction.ActionType action, String targetId, String reason, String guildName, String channelName) {
        try (Connection conn = DatabaseService.getConnection()) {
            String sql = "SELECT 1 FROM ez_moderation_analytics WHERE action = ? AND targetId = ? AND reason = ? AND guildName = ? AND channelName = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, action.name());
                pstmt.setString(2, targetId);
                pstmt.setString(3, reason);
                pstmt.setString(4, guildName);
                pstmt.setString(5, channelName);
                try (ResultSet rs = pstmt.executeQuery()) {
                    return rs.next();
                }
            }
        } catch (Exception e) {
            System.err.println("Error checking similar mod action existence: " + e.getMessage());
        }
        return false;
    }

    public List<MuteEntry> getExpiredMutes(long currentTime) {
        List<MuteEntry> expired = new ArrayList<>();
        try (Connection conn = DatabaseService.getConnection()) {
            String sql = "SELECT guildId, userId FROM ez_active_mutes WHERE unmuteTime > 0 AND unmuteTime <= ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setLong(1, currentTime);
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        expired.add(new MuteEntry(rs.getString("guildId"), rs.getString("userId")));
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error getting expired mutes: " + e.getMessage());
        }
        return expired;
    }

    public static record MuteEntry(String guildId, String userId) {}

    public void addChannelRestriction(String channelId, String type) {
        try (Connection conn = DatabaseService.getConnection()) {
            // Using IGNORE for SQLite
            String sql = "INSERT OR IGNORE INTO ez_channel_restrictions (channelId, restrictionType) VALUES (?, ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, channelId);
                pstmt.setString(2, type);
                pstmt.executeUpdate();
            }
        } catch (Exception e) {
            System.err.println("Error adding channel restriction: " + e.getMessage());
        }
    }

    public void removeChannelRestriction(String channelId, String type) {
        try (Connection conn = DatabaseService.getConnection()) {
            String sql = "DELETE FROM ez_channel_restrictions WHERE channelId = ? AND restrictionType = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, channelId);
                pstmt.setString(2, type);
                pstmt.executeUpdate();
            }
        } catch (Exception e) {
            System.err.println("Error removing channel restriction: " + e.getMessage());
        }
    }

    public List<RestrictionEntry> getAllChannelRestrictions() {
        List<RestrictionEntry> restrictions = new ArrayList<>();
        try (Connection conn = DatabaseService.getConnection()) {
            String sql = "SELECT channelId, restrictionType FROM ez_channel_restrictions";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        restrictions.add(new RestrictionEntry(
                            rs.getString("channelId"),
                            rs.getString("restrictionType")
                        ));
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error getting all channel restrictions: " + e.getMessage());
        }
        return restrictions;
    }

    public void logMessage(String guildId, String channelId, String messageId, String userId, String content, String action) {
        try (Connection conn = DatabaseService.getConnection()) {
            String sql = "INSERT INTO ez_message_logs (guildId, channelId, messageId, userId, content, action, timestamp) " +
                       "VALUES (?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, guildId);
                pstmt.setString(2, channelId);
                pstmt.setString(3, messageId);
                pstmt.setString(4, userId);
                pstmt.setString(5, content);
                pstmt.setString(6, action);
                pstmt.setLong(7, System.currentTimeMillis());
                pstmt.executeUpdate();
            }
        } catch (Exception e) {
            System.err.println("Failed to log message to DB: " + e.getMessage());
        }
    }

    public static record MessageLogEntry(String userId, String content) {}

    public MessageLogEntry getMessageFromLog(String messageId) {
        try (Connection conn = DatabaseService.getConnection()) {
            String sql = "SELECT userId, content FROM ez_message_logs WHERE messageId = ? AND action IN ('RECEIVED', 'UPDATED') ORDER BY timestamp DESC LIMIT 1";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, messageId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return new MessageLogEntry(rs.getString("userId"), rs.getString("content"));
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to retrieve message from DB: " + e.getMessage());
        }
        return null;
    }

    public void logGeneral(String guildId, String userId, String eventType, String details) {
        try (Connection conn = DatabaseService.getConnection()) {
            String sql = "INSERT INTO ez_general_logs (guildId, userId, eventType, details, timestamp) " +
                       "VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, guildId);
                pstmt.setString(2, userId);
                pstmt.setString(3, eventType);
                pstmt.setString(4, details);
                pstmt.setLong(5, System.currentTimeMillis());
                pstmt.executeUpdate();
            }
        } catch (Exception e) {
            System.err.println("Failed to log general event to DB: " + e.getMessage());
        }
    }

    public static record RestrictionEntry(String channelId, String type) {}

    public List<ModAction> getBanUnbanHistory(String targetUserId) {
        return getHistory(targetUserId, List.of("BAN", "UNBAN"));
    }

    public List<ModAction> getHistory(String targetUserId, List<String> actions) {
        try (Connection conn = DatabaseService.getConnection()) {
            List<ModAction> history = new ArrayList<>();
            StringBuilder sql = new StringBuilder("SELECT id, action, moderatorId, moderatorName, targetId, targetName, reason, guildName, channelName, timestamp, duration, count FROM ez_moderation_analytics WHERE targetId = ?");
            
            if (!actions.isEmpty()) {
                sql.append(" AND action IN (");
                for (int i = 0; i < actions.size(); i++) {
                    sql.append("?");
                    if (i < actions.size() - 1) sql.append(", ");
                }
                sql.append(")");
            }
            sql.append(" ORDER BY timestamp DESC");

            try (PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {
                pstmt.setString(1, targetUserId);
                for (int i = 0; i < actions.size(); i++) {
                    pstmt.setString(i + 2, actions.get(i));
                }
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        history.add(new ModAction(
                                rs.getInt("id"),
                                ModAction.ActionType.valueOf(rs.getString("action")),
                                rs.getString("moderatorId"),
                                rs.getString("moderatorName"),
                                rs.getString("targetId"),
                                rs.getString("targetName"),
                                rs.getString("reason"),
                                rs.getString("guildName"),
                                rs.getString("channelName"),
                                rs.getLong("timestamp"),
                                rs.getInt("duration"),
                                rs.getInt("count")
                        ));
                    }
                }
            }
            return history;
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public void updateModAction(int id, String newModeratorId, String newModeratorName, String guildName, String channelName, long newTimestamp) {
        try (Connection conn = DatabaseService.getConnection()) {
            String sql = "UPDATE ez_moderation_analytics SET moderatorId = ?, moderatorName = ?, guildName = ?, channelName = ?, timestamp = ? WHERE id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, newModeratorId);
                pstmt.setString(2, newModeratorName);
                pstmt.setString(3, guildName);
                pstmt.setString(4, channelName);
                pstmt.setLong(5, newTimestamp);
                pstmt.setInt(6, id);
                pstmt.executeUpdate();
            }
        } catch (Exception e) {
            System.err.println("Error updating moderation action: " + e.getMessage());
        }
    }

    public void deleteModAction(ModAction.ActionType action, String targetId, String moderatorId, long timestamp) {
        try (Connection conn = DatabaseService.getConnection()) {
            String sql = "DELETE FROM ez_moderation_analytics WHERE action = ? AND targetId = ? AND moderatorId = ? AND timestamp = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql) ) {
                pstmt.setString(1, action.name());
                pstmt.setString(2, targetId);
                pstmt.setString(3, moderatorId);
                pstmt.setLong(4, timestamp);
                pstmt.executeUpdate();
            }
        } catch (Exception e) {
            System.err.println("Error deleting moderation action: " + e.getMessage());
        }
    }

    public void updateModActionSource(int id, String guildName, String channelName) {
        try (Connection conn = DatabaseService.getConnection()) {
            String sql = "UPDATE ez_moderation_analytics SET guildName = ?, channelName = ? WHERE id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, guildName);
                pstmt.setString(2, channelName);
                pstmt.setInt(3, id);
                pstmt.executeUpdate();
            }
        } catch (Exception e) {
            System.err.println("Error updating moderation action source: " + e.getMessage());
        }
    }

    public void deleteModActionById(int id) {
        try (Connection conn = DatabaseService.getConnection()) {
            String sql = "DELETE FROM ez_moderation_analytics WHERE id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, id);
                pstmt.executeUpdate();
            }
        } catch (Exception e) {
            System.err.println("Error deleting moderation action by ID: " + e.getMessage());
        }
    }

    public void cleanDuplicateBans(String targetId) {
        List<ModAction> history = getHistory(targetId, List.of("BAN"));
        if (history.size() <= 1) return;

        List<ModAction> toKeep = new ArrayList<>();
        
        for (ModAction current : history) {
            ModAction duplicateInToKeep = null;
            for (ModAction kept : toKeep) {
                // Same target, same action type (BAN) and within 10 minutes
                // MUST ALSO BE THE SAME GUILD and CHANNEL to be a duplicate, BUT allow merging Unknown Server/Channel
                boolean sameGuild = (current.getGuildName() != null && current.getGuildName().equals(kept.getGuildName()))
                        || (current.getGuildName() == null && kept.getGuildName() == null)
                        || (current.getGuildName() != null && current.getGuildName().equals("Unknown Server"))
                        || (kept.getGuildName() != null && kept.getGuildName().equals("Unknown Server"));
                
                boolean sameChannel = (current.getChannelName() != null && current.getChannelName().equals(kept.getChannelName()))
                        || (current.getChannelName() == null && kept.getChannelName() == null)
                        || (current.getChannelName() != null && current.getChannelName().equals("Unknown Channel"))
                        || (kept.getChannelName() != null && kept.getChannelName().equals("Unknown Channel"));

                if (sameGuild && sameChannel && Math.abs(current.getTimestamp() - kept.getTimestamp()) < 600000) {
                    duplicateInToKeep = kept;
                    break;
                }
            }
            
            if (duplicateInToKeep == null) {
                toKeep.add(current);
            } else {
                // Merge logic: pick the better moderator and better reason
                boolean currentIsBetter = false;
                
                // If current has a real moderator and kept has "EZ Management"
                boolean keptIsEz = duplicateInToKeep.getModeratorName().equals("EZ Management") || duplicateInToKeep.getModeratorId().equals("0");
                boolean curIsEz = current.getModeratorName().equals("EZ Management") || current.getModeratorId().equals("0");
                
                if (keptIsEz && !curIsEz) {
                    currentIsBetter = true;
                } else if (!keptIsEz && !curIsEz) {
                    // Both have real moderators. 
                    // Priority 1: Has real Guild Name (not Unknown Server)
                    boolean keptHasGuild = duplicateInToKeep.getGuildName() != null && !duplicateInToKeep.getGuildName().equals("Unknown Server");
                    boolean curHasGuild = current.getGuildName() != null && !current.getGuildName().equals("Unknown Server");
                    
                    if (!keptHasGuild && curHasGuild) {
                        currentIsBetter = true;
                    } else if (keptHasGuild == curHasGuild) {
                        // Priority 2: Detailed reason
                        String curReason = current.getReason() != null ? current.getReason() : "";
                        String keptReason = duplicateInToKeep.getReason() != null ? duplicateInToKeep.getReason() : "";
                        
                        if (keptReason.contains("Discord ID:") && !curReason.contains("Discord ID:")) {
                            currentIsBetter = true;
                        } else if (curReason.length() > keptReason.length() && !curReason.contains("Account Name:")) {
                            currentIsBetter = true;
                        }
                    }
                }
                
                if (currentIsBetter) {
                    deleteModActionById(duplicateInToKeep.getId());
                    toKeep.remove(duplicateInToKeep);
                    toKeep.add(current);
                } else {
                    deleteModActionById(current.getId());
                }
            }
        }
    }

    public void addAccessHelpTracking(String userId, long timestamp) {
        try (Connection conn = DatabaseService.getConnection()) {
            String sql = "INSERT OR IGNORE INTO ez_access_help_tracking (user_id, timestamp) VALUES (?, ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, userId);
                pstmt.setLong(2, timestamp);
                pstmt.executeUpdate();
            }
        } catch (Exception e) {
            System.err.println("Error adding access help tracking: " + e.getMessage());
        }
    }

    public Long getAccessHelpTimestamp(String userId) {
        try (Connection conn = DatabaseService.getConnection()) {
            String sql = "SELECT timestamp FROM ez_access_help_tracking WHERE user_id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, userId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getLong("timestamp");
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error getting access help timestamp: " + e.getMessage());
        }
        return null;
    }

    public void removeAccessHelpTracking(String userId) {
        try (Connection conn = DatabaseService.getConnection()) {
            String sql = "DELETE FROM ez_access_help_tracking WHERE user_id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, userId);
                pstmt.executeUpdate();
            }
        } catch (Exception e) {
            System.err.println("Error removing access help tracking: " + e.getMessage());
        }
    }

    public List<String> getEligibleAccessHelpUsers(long cutOffTime) {
        List<String> userIds = new ArrayList<>();
        try (Connection conn = DatabaseService.getConnection()) {
            String sql = "SELECT user_id FROM ez_access_help_tracking WHERE timestamp <= ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setLong(1, cutOffTime);
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        userIds.add(rs.getString("user_id"));
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error getting eligible access help users: " + e.getMessage());
        }
        return userIds;
    }

    public void setMetadata(String key, String value) {
        try (Connection conn = DatabaseService.getConnection()) {
            String sql = "INSERT OR REPLACE INTO bot_metadata (key, value) VALUES (?, ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, key);
                pstmt.setString(2, value);
                pstmt.executeUpdate();
            }
        } catch (Exception e) {
            System.err.println("Error setting metadata: " + e.getMessage());
        }
    }

    public String getMetadata(String key, String defaultValue) {
        try (Connection conn = DatabaseService.getConnection()) {
            String sql = "SELECT value FROM bot_metadata WHERE key = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, key);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("value");
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error getting metadata: " + e.getMessage());
        }
        return defaultValue;
    }

    public boolean isEventCountdownEnabled() {
        return Boolean.parseBoolean(getMetadata("event_countdown_enabled", "true"));
    }

    public void setEventCountdownEnabled(boolean enabled) {
        setMetadata("event_countdown_enabled", String.valueOf(enabled));
    }

    public void addWinnerMessage(String messageId, String channelId, String guildId) {
        try (Connection conn = DatabaseService.getConnection()) {
            String sql = "INSERT INTO winner_messages (message_id, channel_id, guild_id) VALUES (?, ?, ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, messageId);
                pstmt.setString(2, channelId);
                pstmt.setString(3, guildId);
                pstmt.executeUpdate();
            }
        } catch (Exception e) {
            System.err.println("Error adding winner message: " + e.getMessage());
        }
    }

    public List<WinnerMessageEntry> getAllWinnerMessages() {
        List<WinnerMessageEntry> entries = new ArrayList<>();
        try (Connection conn = DatabaseService.getConnection()) {
            String sql = "SELECT message_id, channel_id, guild_id FROM winner_messages";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        entries.add(new WinnerMessageEntry(
                            rs.getString("message_id"),
                            rs.getString("channel_id"),
                            rs.getString("guild_id")
                        ));
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error getting winner messages: " + e.getMessage());
        }
        return entries;
    }

    public void clearWinnerMessages() {
        try (Connection conn = DatabaseService.getConnection()) {
            String sql = "DELETE FROM winner_messages";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.executeUpdate();
            }
        } catch (Exception e) {
            System.err.println("Error clearing winner messages: " + e.getMessage());
        }
    }

    public static record WinnerMessageEntry(String messageId, String channelId, String guildId) {}
}
