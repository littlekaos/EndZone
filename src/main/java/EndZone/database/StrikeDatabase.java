package EndZone.database;

import EndZone.models.Strike;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

public class StrikeDatabase {

    public StrikeDatabase() {
        // Tables are initialized in DatabaseService
    }

    public void addStrike(String userId, String reason, String moderatorId, Timestamp date) {
        String sql = "INSERT INTO strikes (user_id, reason, moderator_id, date) VALUES (?, ?, ?, ?)";
        try (Connection conn = DatabaseService.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, userId);
            pstmt.setString(2, reason);
            pstmt.setString(3, moderatorId);
            pstmt.setTimestamp(4, date);
            pstmt.executeUpdate();
            System.out.println("[DATABASE] ✅ Added strike for " + userId + ". Reason: " + reason);
        } catch (SQLException e) {
            System.err.println("[DATABASE] ❌ Error adding strike: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void clearStrikes(String userId) {
        System.out.println("DEBUG: Clearing strikes for user " + userId);
        try (Connection conn = DatabaseService.getConnection()) {
            conn.setAutoCommit(false);
            
            try {
                String deleteAppealsSQL = "DELETE FROM appeals WHERE strike_id IN (SELECT id FROM strikes WHERE user_id = ?)";
                try (PreparedStatement pstmt = conn.prepareStatement(deleteAppealsSQL)) {
                    pstmt.setString(1, userId);
                    int count = pstmt.executeUpdate();
                    System.out.println("DEBUG: Deleted " + count + " appeals");
                }

                String deleteStrikesSQL = "DELETE FROM strikes WHERE user_id = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(deleteStrikesSQL)) {
                    pstmt.setString(1, userId);
                    int count = pstmt.executeUpdate();
                    System.out.println("DEBUG: Deleted " + count + " strikes");
                }

                // Also remove from demotions
                String deleteTempDemotionSQL = "DELETE FROM temporary_demotions WHERE user_id = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(deleteTempDemotionSQL)) {
                    pstmt.setString(1, userId);
                    int count = pstmt.executeUpdate();
                    System.out.println("DEBUG: Deleted " + count + " temporary demotions from table");
                }

                String deleteMetadataDemotionsSQL = "DELETE FROM bot_metadata WHERE key = ? OR key = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(deleteMetadataDemotionsSQL)) {
                    pstmt.setString(1, "temp_demotion_" + userId);
                    pstmt.setString(2, "perm_demotion_" + userId);
                    int count = pstmt.executeUpdate();
                    System.out.println("DEBUG: Deleted " + count + " demotion metadata entries");
                }
                
                conn.commit();
                System.out.println("DEBUG: Committed clearStrikes transaction for user " + userId);
            } catch (SQLException e) {
                conn.rollback();
                System.err.println("DEBUG: Error in clearStrikes transaction, rolling back: " + e.getMessage());
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            System.err.println("Error clearing strikes for " + userId + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void editStrike(String userId, int strikeNumber, String newReason) {
        List<Strike> strikes = getStrikes(userId);

        if (strikeNumber < 1 || strikeNumber > strikes.size()) {
            throw new IllegalArgumentException("Invalid strike number");
        }

        Strike strikeToEdit = strikes.get(strikeNumber - 1);

        String sql = "UPDATE strikes SET reason = ? WHERE id = ?";

        try (Connection conn = DatabaseService.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, newReason);
            pstmt.setInt(2, strikeToEdit.getId());

            int rowsAffected = pstmt.executeUpdate();

            if (rowsAffected == 0) {
                System.err.println("Warning: No strike was updated for user " + userId);
            }

        } catch (SQLException e) {
            System.err.println("Error editing strike for user " + userId + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    public boolean hasBeenInitialized() {
        String sql = "SELECT value FROM bot_metadata WHERE key = 'strikes_imported'";
        try (Connection conn = DatabaseService.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            ResultSet rs = pstmt.executeQuery();
            return rs.next() && "true".equals(rs.getString("value"));
        } catch (SQLException e) {
            System.err.println("Error checking initialization status: " + e.getMessage());
            return false;
        }
    }

    public void markAsInitialized() {
        String sql = "INSERT INTO bot_metadata (key, value) " +
                "VALUES ('strikes_imported', 'true') " +
                "ON CONFLICT(key) DO UPDATE SET value = excluded.value;";
        try (Connection conn = DatabaseService.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.executeUpdate();
            System.out.println("✅ Database marked as initialized - strikes will not be re-imported");
        } catch (SQLException e) {
            System.err.println("Error marking database as initialized: " + e.getMessage());
        }
    }

    public int getTotalStrikeCount() {
        String sql = "SELECT COUNT(*) as count FROM strikes";
        try (Connection conn = DatabaseService.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("count");
            }
        } catch (SQLException e) {
            System.err.println("Error getting total strike count: " + e.getMessage());
        }
        return 0;
    }

    public List<String> getAllUsersWithStrikes() {
        List<String> userIds = new ArrayList<>();
        String sql = "SELECT DISTINCT user_id FROM strikes";

        try (Connection conn = DatabaseService.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                userIds.add(rs.getString("user_id"));
            }
        } catch (SQLException e) {
            System.err.println("Error getting all users with strikes: " + e.getMessage());
            e.printStackTrace();
        }

        return userIds;
    }

    public void setDemotionListMessageId(String messageId) {
        String sql = "INSERT INTO bot_metadata (key, value) " +
                "VALUES ('demotion_list_message_id', ?) " +
                "ON CONFLICT (key) DO UPDATE SET value = excluded.value";
        try (Connection conn = DatabaseService.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, messageId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error saving demotion list message ID: " + e.getMessage());
        }
    }

    public String getDemotionListMessageId() {
        String sql = "SELECT value FROM bot_metadata WHERE key = 'demotion_list_message_id'";
        try (Connection conn = DatabaseService.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getString("value");
            }
        } catch (SQLException e) {
            System.err.println("Error getting demotion list message ID: " + e.getMessage());
        }
        return null;
    }

    public void addTemporaryDemotion(String userId, java.time.Instant restoreDate) {
        String sql = "INSERT INTO bot_metadata (key, value) VALUES (?, ?) ON CONFLICT (key) DO UPDATE SET value = excluded.value";
        try (Connection conn = DatabaseService.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, "temp_demotion_" + userId);
            pstmt.setString(2, restoreDate.toString());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error adding temporary demotion: " + e.getMessage());
        }
    }

    public void addPermanentDemotion(String userId) {
        System.out.println("DEBUG: addPermanentDemotion for user: " + userId);
        String sql = "INSERT INTO bot_metadata (key, value) VALUES (?, 'permanent') ON CONFLICT (key) DO UPDATE SET value = excluded.value";
        try (Connection conn = DatabaseService.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, "perm_demotion_" + userId);
            pstmt.executeUpdate();
            System.out.println("DEBUG: Successfully saved perm_demotion metadata for " + userId);
        } catch (SQLException e) {
            System.err.println("Error adding permanent demotion: " + e.getMessage());
        }
    }

    public void removeFromDemotions(String userId) {
        String sql1 = "DELETE FROM bot_metadata WHERE key = ? OR key = ?";
        String sql2 = "DELETE FROM temporary_demotions WHERE user_id = ?";
        try (Connection conn = DatabaseService.getConnection()) {
            try (PreparedStatement pstmt = conn.prepareStatement(sql1)) {
                pstmt.setString(1, "temp_demotion_" + userId);
                pstmt.setString(2, "perm_demotion_" + userId);
                pstmt.executeUpdate();
            }
            try (PreparedStatement pstmt = conn.prepareStatement(sql2)) {
                pstmt.setString(1, userId);
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("Error removing from demotions: " + e.getMessage());
        }
    }

    public Map<String, java.time.Instant> loadTemporaryDemotions() {
        Map<String, java.time.Instant> tempDemotions = new HashMap<>();
        String sql = "SELECT user_id, restoration_date FROM temporary_demotions";
        try (Connection conn = DatabaseService.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                String userId = rs.getString("user_id");
                Timestamp ts = rs.getTimestamp("restoration_date");
                tempDemotions.put(userId, ts.toInstant());
            }
        } catch (SQLException e) {
            System.err.println("Error loading temporary demotions: " + e.getMessage());
        }
        return tempDemotions;
    }

    public Set<String> loadPermanentDemotions() {
        Set<String> permDemotions = new HashSet<>();
        String sql = "SELECT key FROM bot_metadata WHERE key LIKE 'perm_demotion_%'";
        try (Connection conn = DatabaseService.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                String key = rs.getString("key");
                String userId = key.replace("perm_demotion_", "");
                permDemotions.add(userId);
            }
        } catch (SQLException e) {
            System.err.println("Error loading permanent demotions: " + e.getMessage());
        }
        return permDemotions;
    }

    public void removeTemporaryDemotion(String userId) {
        String sql = "DELETE FROM bot_metadata WHERE key = ?";
        try (Connection conn = DatabaseService.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, "temp_demotion_" + userId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error removing temporary demotion: " + e.getMessage());
        }
    }

    public void saveTemporaryDemotion(String userId, List<String> roleIds, java.time.Instant restorationDate) {
        System.out.println("DEBUG: saveTemporaryDemotion for user: " + userId + ", roles: " + roleIds.size());
        String roleIdsStr = String.join(",", roleIds);
        String sql = "INSERT INTO temporary_demotions (user_id, role_ids, restoration_date) VALUES (?, ?, ?) " +
                "ON CONFLICT (user_id) DO UPDATE SET role_ids = excluded.role_ids, restoration_date = excluded.restoration_date";
        try (Connection conn = DatabaseService.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, userId);
            pstmt.setString(2, roleIdsStr);
            pstmt.setTimestamp(3, java.sql.Timestamp.from(restorationDate));
            pstmt.executeUpdate();
            System.out.println("DEBUG: Successfully saved temporary_demotion table entry for " + userId);
        } catch (SQLException e) {
            System.err.println("Error saving temporary demotion: " + e.getMessage());
        }
    }

    public List<String> getTemporaryDemotionRoles(String userId) {
        String sql = "SELECT role_ids FROM temporary_demotions WHERE user_id = ?";
        try (Connection conn = DatabaseService.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, userId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                String roleIdsStr = rs.getString("role_ids");
                if (roleIdsStr != null && !roleIdsStr.isEmpty()) {
                    return Arrays.asList(roleIdsStr.split(","));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting temporary demotion roles: " + e.getMessage());
        }
        return new ArrayList<>();
    }

    public Map<String, Map<String, Object>> loadTemporaryDemotionsWithRoles() {
        Map<String, Map<String, Object>> demotions = new HashMap<>();
        String sql = "SELECT user_id, role_ids, restoration_date FROM temporary_demotions";
        try (Connection conn = DatabaseService.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                String userId = rs.getString("user_id");
                String roleIdsStr = rs.getString("role_ids");
                Timestamp ts = rs.getTimestamp("restoration_date");
                java.time.Instant restorationDate = ts.toInstant();
                
                Map<String, Object> data = new HashMap<>();
                if (roleIdsStr != null && !roleIdsStr.isEmpty()) {
                    data.put("role_ids", Arrays.asList(roleIdsStr.split(",")));
                } else {
                    data.put("role_ids", new ArrayList<String>());
                }
                data.put("restoration_date", restorationDate);
                
                demotions.put(userId, data);
            }
        } catch (SQLException e) {
            System.err.println("Error loading temporary demotions with roles: " + e.getMessage());
        }
        return demotions;
    }

    public void updateUserRoles(String userId, List<String> roleIds) {
        String roleIdsStr = String.join(",", roleIds);
        String sql = "INSERT INTO user_roles (user_id, role_ids, updated_at) VALUES (?, ?, CURRENT_TIMESTAMP) " +
                "ON CONFLICT (user_id) DO UPDATE SET role_ids = excluded.role_ids, updated_at = CURRENT_TIMESTAMP";
        try (Connection conn = DatabaseService.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, userId);
            pstmt.setString(2, roleIdsStr);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error updating user roles: " + e.getMessage());
        }
    }

    public List<String> getUserRoles(String userId) {
        String sql = "SELECT role_ids FROM user_roles WHERE user_id = ?";
        try (Connection conn = DatabaseService.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, userId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                String roleIdsStr = rs.getString("role_ids");
                if (roleIdsStr != null && !roleIdsStr.isEmpty()) {
                    return Arrays.asList(roleIdsStr.split(","));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting user roles: " + e.getMessage());
        }
        return new ArrayList<>();
    }

    public void deleteTemporaryDemotion(String userId) {
        String sql = "DELETE FROM temporary_demotions WHERE user_id = ?";
        try (Connection conn = DatabaseService.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, userId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error deleting temporary demotion: " + e.getMessage());
        }
    }

    public void markDemotionAsServed(String userId, int strikeCount) {
        String sql = "INSERT INTO bot_metadata (key, value) VALUES (?, ?) ON CONFLICT (key) DO UPDATE SET value = excluded.value";
        try (Connection conn = DatabaseService.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, "served_temp_demotion_" + userId);
            pstmt.setString(2, String.valueOf(strikeCount));
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error marking demotion as served: " + e.getMessage());
        }
    }

    public int getServedDemotionStrikeCount(String userId) {
        String sql = "SELECT value FROM bot_metadata WHERE key = ?";
        try (Connection conn = DatabaseService.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, "served_temp_demotion_" + userId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return Integer.parseInt(rs.getString("value"));
            }
        } catch (SQLException e) {
            System.err.println("Error getting served demotion strike count: " + e.getMessage());
        }
        return -1;
    }

    public void clearServedDemotion(String userId) {
        String sql = "DELETE FROM bot_metadata WHERE key = ?";
        try (Connection conn = DatabaseService.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, "served_temp_demotion_" + userId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error clearing served demotion: " + e.getMessage());
        }
    }

    public boolean isRestorationNotified(String userId, int strikeCount) {
        String sql = "SELECT value FROM bot_metadata WHERE key = ?";
        try (Connection conn = DatabaseService.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, "restoration_notified_" + userId + "_" + strikeCount);
            ResultSet rs = pstmt.executeQuery();
            return rs.next() && "true".equals(rs.getString("value"));
        } catch (SQLException e) {
            return false;
        }
    }

    public void markRestorationNotified(String userId, int strikeCount) {
        String sql = "INSERT INTO bot_metadata (key, value) VALUES (?, 'true') ON CONFLICT (key) DO UPDATE SET value = excluded.value";
        try (Connection conn = DatabaseService.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, "restoration_notified_" + userId + "_" + strikeCount);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error marking restoration as notified: " + e.getMessage());
        }
    }

    public List<Strike> getStrikes(String userId) {
        List<Strike> strikes = new ArrayList<>();
        String sql = """
            SELECT s.id, s.user_id, s.reason, s.moderator_id, s.date
            FROM strikes s
            WHERE s.user_id = ?
            AND s.id NOT IN (
                SELECT a.strike_id
                FROM appeals a
                WHERE a.status = 'APPROVED'
            )
            ORDER BY s.date ASC;
            """;
        try (Connection conn = DatabaseService.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, userId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                strikes.add(new Strike(
                        rs.getInt("id"),
                        rs.getString("user_id"),
                        rs.getString("reason"),
                        rs.getString("moderator_id"),
                        rs.getTimestamp("date")
                ));
            }
            System.out.println("[DATABASE] Found " + strikes.size() + " active strikes for " + userId);
        } catch (SQLException e) {
            System.err.println("[DATABASE] ❌ Error getting strikes: " + e.getMessage());
            e.printStackTrace();
        }
        return strikes;
    }

    public List<Strike> getAllStrikes(String userId) {
        List<Strike> strikes = new ArrayList<>();
        String sql = "SELECT id, user_id, reason, moderator_id, date FROM strikes WHERE user_id = ? ORDER BY date ASC";
        try (Connection conn = DatabaseService.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, userId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                strikes.add(new Strike(
                        rs.getInt("id"),
                        rs.getString("user_id"),
                        rs.getString("reason"),
                        rs.getString("moderator_id"),
                        rs.getTimestamp("date")
                ));
            }
        } catch (SQLException e) {
            System.err.println("Error getting all strikes: " + e.getMessage());
        }
        return strikes;
    }

    public Strike getStrikeById(int strikeId) {
        String sql = "SELECT id, user_id, reason, moderator_id, date FROM strikes WHERE id = ?";
        try (Connection conn = DatabaseService.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, strikeId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return new Strike(
                        rs.getInt("id"),
                        rs.getString("user_id"),
                        rs.getString("reason"),
                        rs.getString("moderator_id"),
                        rs.getTimestamp("date")
                );
            }
        } catch (SQLException e) {
            System.err.println("Error getting strike by ID: " + e.getMessage());
        }
        return null;
    }

    public boolean removeStrike(String userId, Timestamp date) {
        String sql = "DELETE FROM strikes WHERE user_id = ? AND date = ?";
        try (Connection conn = DatabaseService.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, userId);
            pstmt.setTimestamp(2, date);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error removing strike by timestamp: " + e.getMessage());
            return false;
        }
    }

    public boolean editStrikeReason(String userId, Timestamp date, String newReason) {
        String sql = "UPDATE strikes SET reason = ? WHERE user_id = ? AND date = ?";
        try (Connection conn = DatabaseService.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, newReason);
            pstmt.setString(2, userId);
            pstmt.setTimestamp(3, date);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error editing strike reason by timestamp: " + e.getMessage());
            return false;
        }
    }

    public boolean removeStrike(String userId, int strikeNumber) {
        List<Strike> strikes = getStrikes(userId);

        if (strikeNumber < 1 || strikeNumber > strikes.size()) {
            System.err.println("Invalid strike number: " + strikeNumber + " (user has " + strikes.size() + " strikes)");
            return false;
        }

        Strike strikeToRemove = strikes.get(strikeNumber - 1);
        int strikeId = strikeToRemove.getId();

        try (Connection conn = DatabaseService.getConnection()) {
            String checkAppealsSQL = "SELECT " +
                    "COUNT(*) as count, "+
                    "SUM(status = 'PENDING') AS pending_count "+
                    "FROM appeals " +
                    "WHERE strike_id = ?;";
            try (PreparedStatement pstmt = conn.prepareStatement(checkAppealsSQL)) {
                pstmt.setInt(1, strikeId);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    int pendingAppeals = rs.getInt("pending_count");

                    if (pendingAppeals > 0) {
                        System.err.println("❌ Cannot manually remove strike ID " + strikeId + " - it has " + pendingAppeals + " pending appeal(s)");
                        return false;
                    }
                }
            }

            conn.setAutoCommit(false);

            try {
                String deleteAppealsSQL = "DELETE FROM appeals WHERE strike_id = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(deleteAppealsSQL)) {
                    pstmt.setInt(1, strikeId);
                    pstmt.executeUpdate();
                }

                String deleteStrikeSQL = "DELETE FROM strikes WHERE id = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(deleteStrikeSQL)) {
                    pstmt.setInt(1, strikeId);
                    int rowsAffected = pstmt.executeUpdate();

                    if (rowsAffected > 0) {
                        conn.commit();
                        return true;
                    } else {
                        conn.rollback();
                        return false;
                    }
                }

            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }

        } catch (SQLException e) {
            System.err.println("Error removing strike: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public void bulkUpdateTemporaryDemotions(int daysToAdd) {
        String sql = "UPDATE temporary_demotions SET restoration_date = datetime(restoration_date, '+' || ? || ' days')";
        try (Connection conn = DatabaseService.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, daysToAdd);
            int rows = pstmt.executeUpdate();
            System.out.println("✅ Bulk updated " + rows + " temporary demotions (Added " + daysToAdd + " days)");
        } catch (SQLException e) {
            System.err.println("Error bulk updating temporary demotions: " + e.getMessage());
        }
    }
}
