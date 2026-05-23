package EndZone.commands;

import EndZone.EndZone;
import EndZone.database.DatabaseService;
import EndZone.utils.EmbedUtils;
import EndZone.utils.PermissionUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

import java.awt.Color;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class ReactionRoleCommand implements Command {
    private final EndZone bot;

    public ReactionRoleCommand(EndZone bot) {
        this.bot = bot;
    }

    @Override
    public List<CommandData> getCommandDataList() {
        return List.of(Commands.slash("reactionrole", "Manage reaction roles")
                .addSubcommands(
                        new SubcommandData("add", "Add a reaction role")
                                .addOption(OptionType.STRING, "message_id", "The ID of the message to add the reaction to", true)
                                .addOption(OptionType.STRING, "emoji", "The emoji to react with", true)
                                .addOption(OptionType.ROLE, "role", "The role to give when reacting", true),
                        new SubcommandData("remove", "Remove a reaction role")
                                .addOption(OptionType.STRING, "message_id", "The ID of the message", true)
                                .addOption(OptionType.STRING, "emoji", "The emoji to remove", true),
                        new SubcommandData("list", "List all reaction roles")
                ));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!PermissionUtils.isAlphaBetaOrHigher(event.getMember(), bot.getConfig())) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("You need Alpha Beta+ permissions to use this command.")).setEphemeral(true).queue();
            return;
        }

        if (!event.getMember().hasPermission(Permission.MANAGE_ROLES)) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("You need Manage Roles permission to use this command.")).setEphemeral(true).queue();
            return;
        }

        String subcommand = event.getSubcommandName();

        switch (subcommand) {
            case "add" -> handleAdd(event);
            case "remove" -> handleRemove(event);
            case "list" -> handleList(event);
        }
    }

    private void handleAdd(SlashCommandInteractionEvent event) {
        String messageId = event.getOption("message_id").getAsString();
        String emojiInput = event.getOption("emoji").getAsString();
        Role role = event.getOption("role").getAsRole();
        String guildId = event.getGuild().getId();

        Emoji emoji;
        try {
            emoji = Emoji.fromFormatted(emojiInput);
        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Invalid emoji provided.")).setEphemeral(true).queue();
            return;
        }

        String emojiString = emoji.getFormatted();

        try (Connection conn = DatabaseService.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                     "INSERT OR REPLACE INTO reaction_roles (message_id, emoji, role_id, guild_id) VALUES (?, ?, ?, ?)")) {
            pstmt.setString(1, messageId);
            pstmt.setString(2, emojiString);
            pstmt.setString(3, role.getId());
            pstmt.setString(4, guildId);
            pstmt.executeUpdate();

            event.getChannel().retrieveMessageById(messageId).queue(message -> {
                message.addReaction(emoji).queue();
                event.reply("Successfully added reaction role!").setEphemeral(true).queue();
            }, error -> {
                event.reply("Added to database, but failed to add reaction to message: " + error.getMessage()).setEphemeral(true).queue();
            });

        } catch (SQLException e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Database error: " + e.getMessage())).setEphemeral(true).queue();
        }
    }

    private void handleRemove(SlashCommandInteractionEvent event) {
        String messageId = event.getOption("message_id").getAsString();
        String emojiInput = event.getOption("emoji").getAsString();

        Emoji emoji;
        try {
            emoji = Emoji.fromFormatted(emojiInput);
        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Invalid emoji provided.")).setEphemeral(true).queue();
            return;
        }

        String emojiString = emoji.getFormatted();

        try (Connection conn = DatabaseService.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                     "DELETE FROM reaction_roles WHERE message_id = ? AND emoji = ?")) {
            pstmt.setString(1, messageId);
            pstmt.setString(2, emojiString);
            int affected = pstmt.executeUpdate();

            if (affected > 0) {
                event.reply("Successfully removed reaction role.").setEphemeral(true).queue();
            } else {
                event.reply("No reaction role found for that message and emoji.").setEphemeral(true).queue();
            }
        } catch (SQLException e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Database error: " + e.getMessage())).setEphemeral(true).queue();
        }
    }

    private void handleList(SlashCommandInteractionEvent event) {
        try (Connection conn = DatabaseService.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                     "SELECT message_id, emoji, role_id FROM reaction_roles WHERE guild_id = ?")) {
            pstmt.setString(1, event.getGuild().getId());
            ResultSet rs = pstmt.executeQuery();

            EmbedBuilder eb = new EmbedBuilder()
                    .setTitle("Reaction Roles")
                    .setColor(Color.BLUE);

            boolean found = false;
            while (rs.next()) {
                found = true;
                String mid = rs.getString("message_id");
                String emoji = rs.getString("emoji");
                String rid = rs.getString("role_id");
                eb.addField("Message: " + mid, "Emoji: " + emoji + " -> Role: <@&" + rid + ">", false);
            }

            if (!found) {
                eb.setDescription("No reaction roles configured.");
            }

            event.replyEmbeds(eb.build()).setEphemeral(true).queue();

        } catch (SQLException e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Database error: " + e.getMessage())).setEphemeral(true).queue();
        }
    }
}
