package EndZone.services;

import EndZone.database.DatabaseService;
import EndZone.models.AFKStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AFKService {
    private static final Logger logger = LoggerFactory.getLogger(AFKService.class);
    private final Map<String, AFKStatus> afkCache = new ConcurrentHashMap<>();

    public AFKService() {
        loadAFKStatus();
    }

    private void loadAFKStatus() {
        String select = "SELECT user_id, message, timestamp, original_nickname FROM ez_afk";
        try (Connection conn = DatabaseService.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(select)) {
            
            while (rs.next()) {
                String userId = rs.getString("user_id");
                String reason = rs.getString("message");
                long timestamp = rs.getLong("timestamp");
                String originalNickname = rs.getString("original_nickname");
                afkCache.put(userId, new AFKStatus(userId, reason, timestamp, originalNickname));
            }
            logger.info("Loaded {} AFK statuses from database", afkCache.size());
        } catch (SQLException e) {
            logger.error("Error loading AFK status: {}", e.getMessage());
        }
    }

    public void setAFK(String userId, String reason, String originalNickname) {
        long timestamp = System.currentTimeMillis();
        AFKStatus status = new AFKStatus(userId, reason, timestamp, originalNickname);
        afkCache.put(userId, status);

        String insert = "INSERT INTO ez_afk (user_id, message, timestamp, original_nickname) VALUES (?, ?, ?, ?) ON CONFLICT(user_id) DO UPDATE SET message = EXCLUDED.message, timestamp = EXCLUDED.timestamp, original_nickname = EXCLUDED.original_nickname";
        try (Connection conn = DatabaseService.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(insert)) {
            pstmt.setString(1, userId);
            pstmt.setString(2, reason);
            pstmt.setLong(3, timestamp);
            pstmt.setString(4, originalNickname);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Error saving AFK status for {}: {}", userId, e.getMessage());
        }
    }

    public void removeAFK(String userId) {
        afkCache.remove(userId);
        String delete = "DELETE FROM ez_afk WHERE user_id = ?";
        try (Connection conn = DatabaseService.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(delete)) {
            pstmt.setString(1, userId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Error removing AFK status for {}: {}", userId, e.getMessage());
        }
    }

    public AFKStatus getAFKStatus(String userId) {
        return afkCache.get(userId);
    }

    public boolean isAFK(String userId) {
        return afkCache.containsKey(userId);
    }
}
