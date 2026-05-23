package EndZone.services;

import EndZone.database.DatabaseService;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class BlacklistService {

    public boolean isBlacklisted(String userId) {
        String query = "SELECT 1 FROM blacklist WHERE user_id = ?";
        try (Connection conn = DatabaseService.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                boolean result = rs.next();
                if (result) {
                    System.out.println("[BLACKLIST] Blocked interaction for blacklisted user: " + userId);
                }
                return result;
            }
        } catch (SQLException e) {
            System.err.println("[ERROR] Failed to check blacklist status: " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }

    public void blacklistUser(String userId, String reason, String moderatorId) {
        System.out.println("[BLACKLIST] Adding user to blacklist: " + userId + " by " + moderatorId + " for reason: " + reason);
        String query = "INSERT OR REPLACE INTO blacklist (user_id, reason, moderator_id, timestamp) VALUES (?, ?, ?, CURRENT_TIMESTAMP)";
        try (Connection conn = DatabaseService.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, userId);
            pstmt.setString(2, reason);
            pstmt.setString(3, moderatorId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[ERROR] Failed to blacklist user: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void unblacklistUser(String userId) {
        System.out.println("[BLACKLIST] Removing user from blacklist: " + userId);
        String query = "DELETE FROM blacklist WHERE user_id = ?";
        try (Connection conn = DatabaseService.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, userId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[ERROR] Failed to unblacklist user: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public List<String> getBlacklistedUsers() {
        List<String> users = new ArrayList<>();
        String query = "SELECT user_id FROM blacklist";
        try (Connection conn = DatabaseService.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            while (rs.next()) {
                users.add(rs.getString("user_id"));
            }
        } catch (SQLException e) {
            System.err.println("[ERROR] Failed to get blacklisted users: " + e.getMessage());
            e.printStackTrace();
        }
        return users;
    }
}
