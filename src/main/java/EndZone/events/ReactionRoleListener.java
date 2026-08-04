package EndZone.events;

import EndZone.database.DatabaseService;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class ReactionRoleListener extends ListenerAdapter {

    @Override
    public void onMessageReactionAdd(MessageReactionAddEvent event) {
        if (event.getUser() == null || event.getUser().isBot()) return;

        String messageId = event.getMessageId();
        String emoji = event.getEmoji().getFormatted();

        try (Connection conn = DatabaseService.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                     "SELECT role_id FROM reaction_roles WHERE message_id = ? AND emoji = ?")) {
            pstmt.setString(1, messageId);
            pstmt.setString(2, emoji);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                String roleId = rs.getString("role_id");
                Role role = event.getGuild().getRoleById(roleId);
                if (role != null) {
                    event.getGuild().addRoleToMember(event.getMember(), role).queue();
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onMessageReactionRemove(MessageReactionRemoveEvent event) {
        if (event.getUser() == null || event.getUser().isBot()) return;

        String messageId = event.getMessageId();
        String emoji = event.getEmoji().getFormatted();

        try (Connection conn = DatabaseService.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                     "SELECT role_id FROM reaction_roles WHERE message_id = ? AND emoji = ?")) {
            pstmt.setString(1, messageId);
            pstmt.setString(2, emoji);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                String roleId = rs.getString("role_id");
                Role role = event.getGuild().getRoleById(roleId);
                if (role != null) {
                    event.getGuild().retrieveMemberById(event.getUserId()).queue(member -> {
                        event.getGuild().removeRoleFromMember(member, role).queue();
                    });
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
