package EndZone.repositories;

import EndZone.database.DatabaseService;
import EndZone.models.EventNameData;

import java.sql.*;
import java.util.*;

public class SQLiteEventNameRepository {

    public SQLiteEventNameRepository() {
        // Tables are initialized in DatabaseService
    }

    public void saveEventName(String userId, String eventName) {
        String sql = """
            INSERT INTO event_names (user_id, event_name, updated_at)
            VALUES (?, ?, CURRENT_TIMESTAMP)
            ON CONFLICT(user_id) DO UPDATE SET
                event_name = excluded.event_name,
                updated_at = CURRENT_TIMESTAMP;
            """;

        try (Connection conn = DatabaseService.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, userId);
            stmt.setString(2, eventName.toLowerCase());
            stmt.executeUpdate();

        } catch (SQLException e) {
            System.err.println("Error saving event name for user " + userId + ": " + e.getMessage());
        }
    }

    public EventNameData getEventNameByUser(String userId) {
        String sql = """
            SELECT 
                user_id,
                event_name,
                (strftime('%s', created_at) * 1000) AS timestamp
            FROM event_names
            WHERE user_id = ?;
            """;

        try (Connection conn = DatabaseService.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, userId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new EventNameData(
                            rs.getString("user_id"),
                            rs.getString("event_name"),
                            rs.getLong("timestamp")
                    );
                }
            }
        } catch (SQLException e) {
            System.err.println("Error retrieving event name for user " + userId + ": " + e.getMessage());
        }

        return null;
    }

    public List<EventNameData> searchEventNameByName(String name) {
        String sql = """
            SELECT 
                user_id,
                event_name,
                (strftime('%s', created_at) * 1000) AS timestamp
            FROM event_names
            WHERE LOWER(event_name) LIKE ?;
            """;

        List<EventNameData> results = new ArrayList<>();

        try (Connection conn = DatabaseService.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, "%" + name.toLowerCase() + "%");

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    results.add(new EventNameData(
                            rs.getString("user_id"),
                            rs.getString("event_name"),
                            rs.getLong("timestamp")
                    ));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error searching event names: " + e.getMessage());
        }

        return results;
    }

    public EventNameData getEventNameByUserAndName(String userId, String name) {
        String sql = """
            SELECT 
                user_id,
                event_name,
                (strftime('%s', created_at) * 1000) AS timestamp
            FROM event_names
            WHERE user_id = ?
              AND LOWER(event_name) = ?;
            """;

        try (Connection conn = DatabaseService.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, userId);
            stmt.setString(2, name.toLowerCase());

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new EventNameData(
                            rs.getString("user_id"),
                            rs.getString("event_name"),
                            rs.getLong("timestamp")
                    );
                }
            }
        } catch (SQLException e) {
            System.err.println("Error retrieving event name: " + e.getMessage());
        }

        return null;
    }

    public Map<String, EventNameData> getAllEventNames() {
        String sql = """
            SELECT 
                user_id,
                event_name,
                (strftime('%s', created_at) * 1000) AS timestamp
            FROM event_names;
            """;

        Map<String, EventNameData> results = new HashMap<>();

        try (Connection conn = DatabaseService.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                String userId = rs.getString("user_id");

                results.put(userId, new EventNameData(
                        userId,
                        rs.getString("event_name"),
                        rs.getLong("timestamp")
                ));
            }
        } catch (SQLException e) {
            System.err.println("Error retrieving all event names: " + e.getMessage());
        }

        return results;
    }
}
