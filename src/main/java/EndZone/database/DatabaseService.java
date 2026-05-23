package EndZone.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import EndZone.forms.EndZoneForm;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;

public class DatabaseService {
    private static HikariDataSource dataSource;
    private static final Gson gson = new Gson();

    public static void initialize(String databaseUrl) {
        try {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(databaseUrl);
            config.setMaximumPoolSize(10);
            config.setMinimumIdle(2);
            config.setConnectionTimeout(30000);
            config.setIdleTimeout(300000);
            config.setMaxLifetime(600000);
            config.setConnectionTestQuery("SELECT 1");
            config.setLeakDetectionThreshold(60000);

            dataSource = new HikariDataSource(config);
            
            // Log absolute path for persistence verification
            if (databaseUrl.startsWith("jdbc:sqlite:")) {
                String path = databaseUrl.substring("jdbc:sqlite:".length());
                java.io.File dbFile = new java.io.File(path);
                System.out.println("[DATABASE] SQLite file location: " + dbFile.getAbsolutePath());
            }
            
            createTables();
            migrateAfkTable();
            migrateModerationAnalyticsTable();
            System.out.println("[DATABASE] Database connection pool initialized");
        } catch (Exception e) {
            System.err.println("[ERROR] Failed to initialize database: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Database initialization failed", e);
        }
    }

    public static Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("DataSource is not initialized");
        }
        return dataSource.getConnection();
    }

    public static HikariDataSource getDataSource() {
        return dataSource;
    }

    private static void migrateAfkTable() {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            try (ResultSet rs = meta.getColumns(null, null, "ez_afk", "original_nickname")) {
                if (!rs.next()) {
                    System.out.println("[DATABASE] Adding missing column 'original_nickname' to 'ez_afk' table");
                    try (Statement stmt = conn.createStatement()) {
                        stmt.execute("ALTER TABLE ez_afk ADD COLUMN original_nickname TEXT");
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("[ERROR] Failed to migrate 'ez_afk' table: " + e.getMessage());
        }
    }

    private static void migrateModerationAnalyticsTable() {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            
            // Add guildName
            try (ResultSet rs = meta.getColumns(null, null, "ez_moderation_analytics", "guildName")) {
                if (!rs.next()) {
                    System.out.println("[DATABASE] Adding missing column 'guildName' to 'ez_moderation_analytics' table");
                    try (Statement stmt = conn.createStatement()) {
                        stmt.execute("ALTER TABLE ez_moderation_analytics ADD COLUMN guildName TEXT");
                    }
                }
            }
            
            // Add channelName
            try (ResultSet rs = meta.getColumns(null, null, "ez_moderation_analytics", "channelName")) {
                if (!rs.next()) {
                    System.out.println("[DATABASE] Adding missing column 'channelName' to 'ez_moderation_analytics' table");
                    try (Statement stmt = conn.createStatement()) {
                        stmt.execute("ALTER TABLE ez_moderation_analytics ADD COLUMN channelName TEXT");
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("[ERROR] Failed to migrate 'ez_moderation_analytics' table: " + e.getMessage());
        }
    }

    private static void createTables() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            
            // endzone_applications
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS endzone_applications (
                    user_id VARCHAR(255) PRIMARY KEY,
                    current_step INT NOT NULL DEFAULT 1,
                    selected_role VARCHAR(255),
                    submitted BOOLEAN NOT NULL DEFAULT FALSE,
                    answers_json TEXT DEFAULT '{}',
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);

            // event_names
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS event_names (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id TEXT NOT NULL UNIQUE,
                    event_name TEXT NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_event_names_user_id ON event_names(user_id)");

            // strikes
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS strikes (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id TEXT NOT NULL,
                    reason TEXT NOT NULL,
                    moderator_id TEXT NOT NULL,
                    date TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);

            // bot_metadata
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS bot_metadata (
                    key TEXT PRIMARY KEY,
                    value TEXT NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);

            // temporary_demotions
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS temporary_demotions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id TEXT NOT NULL UNIQUE,
                    role_ids TEXT NOT NULL,
                    restoration_date TIMESTAMP NOT NULL
                )
            """);

            // appeals
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS appeals (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id TEXT NOT NULL,
                    strike_id INTEGER NOT NULL,
                    reason TEXT NOT NULL,
                    status TEXT DEFAULT 'PENDING',
                    reviewer_id TEXT,
                    review_reason TEXT,
                    date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    review_date TIMESTAMP,
                    FOREIGN KEY (strike_id) REFERENCES strikes(id) ON DELETE CASCADE
                )
            """);

            // ez_moderation_analytics
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS ez_moderation_analytics (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    action VARCHAR(32) NOT NULL,
                    moderatorId VARCHAR(32),
                    moderatorName VARCHAR(255),
                    targetId VARCHAR(32) NOT NULL,
                    targetName VARCHAR(255),
                    reason TEXT,
                    timestamp BIGINT,
                    duration INT,
                    count INT,
                    guildName TEXT,
                    channelName TEXT,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_ez_moderation_targetId ON ez_moderation_analytics(targetId)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_ez_moderation_action ON ez_moderation_analytics(action)");

            // ez_warnings
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS ez_warnings (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    userId VARCHAR(32) NOT NULL,
                    moderatorId VARCHAR(32) NOT NULL,
                    reason TEXT,
                    timestamp BIGINT NOT NULL
                )
            """);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_ez_warnings_userId ON ez_warnings(userId)");

            // ez_mute_config
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS ez_mute_config (
                    guildId VARCHAR(32) PRIMARY KEY,
                    muteRoleId VARCHAR(32) NOT NULL
                )
            """);

            // ez_active_mutes
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS ez_active_mutes (
                    guildId VARCHAR(32),
                    userId VARCHAR(32),
                    unmuteTime BIGINT,
                    PRIMARY KEY (guildId, userId)
                )
            """);

            // ez_channel_restrictions
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS ez_channel_restrictions (
                    channelId VARCHAR(32),
                    restrictionType VARCHAR(100),
                    PRIMARY KEY (channelId, restrictionType)
                )
            """);

            // ez_message_logs
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS ez_message_logs (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    guildId VARCHAR(32),
                    channelId VARCHAR(32),
                    messageId VARCHAR(32),
                    userId VARCHAR(32),
                    content TEXT,
                    action TEXT,
                    timestamp BIGINT
                )
            """);

            // ez_general_logs
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS ez_general_logs (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    guildId VARCHAR(32),
                    userId VARCHAR(32),
                    eventType TEXT,
                    details TEXT,
                    timestamp BIGINT
                )
            """);

            // ez_voice_channels
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS ez_voice_channels (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    channel_id VARCHAR(20) NOT NULL,
                    channel_name VARCHAR(255) NOT NULL,
                    creator_id VARCHAR(20) NOT NULL,
                    creator_name VARCHAR(255) NOT NULL,
                    guild_id VARCHAR(20) NOT NULL,
                    guild_name VARCHAR(255),
                    category_id VARCHAR(20),
                    user_limit INTEGER DEFAULT 0,
                    channel_type VARCHAR(20) DEFAULT 'CUSTOM',
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    deleted_at TIMESTAMP,
                    is_active BOOLEAN DEFAULT 1
                )
            """);

            // ez_voice_activity
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS ez_voice_activity (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    channel_id VARCHAR(20) NOT NULL,
                    user_id VARCHAR(20) NOT NULL,
                    user_name VARCHAR(255) NOT NULL,
                    guild_id VARCHAR(20) NOT NULL,
                    action_type VARCHAR(10) NOT NULL,
                    joined_at TIMESTAMP,
                    left_at TIMESTAMP,
                    duration_seconds INTEGER,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);

            // ez_user_voice_stats
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS ez_user_voice_stats (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id VARCHAR(20) NOT NULL,
                    user_name VARCHAR(255) NOT NULL,
                    guild_id VARCHAR(20) NOT NULL,
                    total_channels_created INTEGER DEFAULT 0,
                    total_voice_time_seconds BIGINT DEFAULT 0,
                    last_channel_created TIMESTAMP,
                    last_voice_activity TIMESTAMP,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE(user_id, guild_id)
                )
            """);

            // ez_voice_server_setup
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS ez_voice_server_setup (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    guild_id VARCHAR(20) NOT NULL UNIQUE,
                    category_id VARCHAR(20) NOT NULL,
                    managed_vc_ids TEXT,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);

            // user_roles
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS user_roles (
                    user_id TEXT PRIMARY KEY,
                    role_ids TEXT NOT NULL,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);

            // reaction_roles
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS reaction_roles (
                    message_id TEXT NOT NULL,
                    emoji TEXT NOT NULL,
                    role_id TEXT NOT NULL,
                    guild_id TEXT NOT NULL,
                    PRIMARY KEY (message_id, emoji)
                )
            """);

            // blacklist
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS blacklist (
                    user_id TEXT PRIMARY KEY,
                    reason TEXT,
                    moderator_id TEXT,
                    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);

            // ez_afk
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS ez_afk (
                    user_id VARCHAR(32) PRIMARY KEY,
                    message TEXT,
                    timestamp BIGINT NOT NULL,
                    original_nickname TEXT
                )
            """);

            System.out.println("[DATABASE] All tables created or verified in unified database");
            
            // Log strike count on startup
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM strikes")) {
                if (rs.next()) {
                    System.out.println("[DATABASE] Current total strikes in database: " + rs.getInt(1));
                }
            }
        } catch (SQLException e) {
            System.err.println("[ERROR] Failed to create tables: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void saveFormState(String userId, EndZoneForm.FormState state) {
        String insertOrUpdate = """
            INSERT INTO endzone_applications (user_id, current_step, selected_role, submitted, answers_json, updated_at)
            VALUES (?, ?, ?, FALSE, ?, CURRENT_TIMESTAMP)
            ON CONFLICT (user_id) DO UPDATE SET
                current_step = EXCLUDED.current_step,
                selected_role = EXCLUDED.selected_role,
                answers_json = EXCLUDED.answers_json,
                updated_at = CURRENT_TIMESTAMP
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(insertOrUpdate)) {
            
            String answersJson = gson.toJson(state.answers);
            
            pstmt.setString(1, userId);
            pstmt.setInt(2, state.currentQuestion);
            pstmt.setString(3, state.selectedRole);
            pstmt.setString(4, answersJson);
            
            pstmt.executeUpdate();
            System.out.println("[DATABASE] Saved form state for " + userId + " (Answers: " + state.answers.size() + ")");
        } catch (SQLException e) {
            System.err.println("[ERROR] Failed to save form state: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static EndZoneForm.FormState loadFormState(String userId, net.dv8tion.jda.api.entities.User user) {
        EndZoneForm.FormState state = new EndZoneForm.FormState(user);

        String select = "SELECT current_step, selected_role, submitted, answers_json FROM endzone_applications WHERE user_id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(select)) {
            pstmt.setString(1, userId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                boolean submitted = rs.getBoolean("submitted");
                if (submitted) return null;

                state.currentQuestion = rs.getInt("current_step");
                state.selectedRole = rs.getString("selected_role");
                
                String json = rs.getString("answers_json");
                if (json != null && !json.isEmpty()) {
                    Map<Integer, String> answers = gson.fromJson(json, new TypeToken<HashMap<Integer, String>>(){}.getType());
                    if (answers != null) {
                        state.answers.putAll(answers);
                    }
                }
                
                if (state.selectedRole == null) {
                    state.selectedRole = inferRoleFromAnswers(state.answers);
                }
                
                return state;
            }
        } catch (SQLException e) {
            System.err.println("[ERROR] Failed to load form state: " + e.getMessage());
            e.printStackTrace();
        }

        return state;
    }

    public static void markApplicationSubmitted(String userId) {
        String update = "UPDATE endzone_applications SET submitted = TRUE, updated_at = CURRENT_TIMESTAMP WHERE user_id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(update)) {
            pstmt.setString(1, userId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[ERROR] Failed to mark as submitted: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void deleteFormState(String userId) {
        String delete = "DELETE FROM endzone_applications WHERE user_id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(delete)) {
            pstmt.setString(1, userId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[ERROR] Failed to delete form state: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void resetApplication(String userId) {
        String reset = "UPDATE endzone_applications SET submitted = FALSE, current_step = 1, selected_role = NULL, answers_json = '{}', updated_at = CURRENT_TIMESTAMP WHERE user_id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(reset)) {
            pstmt.setString(1, userId);
            pstmt.executeUpdate();
            System.out.println("[DATABASE] Reset data for user: " + userId);
        } catch (SQLException e) {
            System.err.println("[ERROR] Failed to reset: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            System.out.println("[DATABASE] Connection pool closed");
        }
    }

    private static String inferRoleFromAnswers(java.util.Map<Integer, String> answers) {
        boolean hasHostStreamerQuestions = answers.containsKey(6) || answers.containsKey(7) || answers.containsKey(8);
        boolean hasModeratorQuestions = answers.containsKey(3) || answers.containsKey(4) || answers.containsKey(5);
        
        if (hasHostStreamerQuestions) {
            return "Host/Streamer";
        } else if (hasModeratorQuestions) {
            return "Trial Moderator";
        }
        
        return null;
    }
}
