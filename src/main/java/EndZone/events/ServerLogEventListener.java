package EndZone.events;

import EndZone.EndZone;
import EndZone.services.MessageCache;
import EndZone.services.UserCache;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.attribute.IPermissionContainer;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.channel.ChannelCreateEvent;
import net.dv8tion.jda.api.events.channel.ChannelDeleteEvent;
import EndZone.config.BotConfig;
import net.dv8tion.jda.api.events.emoji.EmojiAddedEvent;
import net.dv8tion.jda.api.events.emoji.EmojiRemovedEvent;
import net.dv8tion.jda.api.events.guild.GuildBanEvent;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildUnbanEvent;
import net.dv8tion.jda.api.events.guild.invite.GuildInviteCreateEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleRemoveEvent;
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateAvatarEvent;
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateNicknameEvent;
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateTimeOutEvent;
import net.dv8tion.jda.api.events.guild.update.GuildUpdateIconEvent;
import net.dv8tion.jda.api.events.guild.update.GuildUpdateNameEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.events.message.MessageBulkDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionRemoveEvent;
import net.dv8tion.jda.api.events.role.RoleCreateEvent;
import net.dv8tion.jda.api.events.role.RoleDeleteEvent;
import net.dv8tion.jda.api.events.role.update.RoleUpdateColorEvent;
import net.dv8tion.jda.api.events.role.update.RoleUpdateNameEvent;
import net.dv8tion.jda.api.events.role.update.RoleUpdatePermissionsEvent;
import net.dv8tion.jda.api.events.user.update.UserUpdateAvatarEvent;
import net.dv8tion.jda.api.events.user.update.UserUpdateNameEvent;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.awt.Color;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;

public class ServerLogEventListener extends ListenerAdapter {
    private final EndZone bot;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");
    private static final ZoneId EST_ZONE = ZoneId.of("America/New_York");

    public ServerLogEventListener(EndZone bot) {
        this.bot = bot;
    }

    private String getFormattedTime() {
        return ZonedDateTime.now(EST_ZONE).format(FORMATTER);
    }

    private String[] getUserInfo(User user) {
        if (user == null) return new String[]{"Unknown", "Unknown"};
        bot.getUserCache().cacheUser(user);
        return bot.getUserCache().getUserDisplayInfo(user.getId());
    }

    // --- Channel Events ---

    @Override
    public void onChannelCreate(ChannelCreateEvent event) {
        String muteRoleId = bot.getDataService().getMuteRoleId(event.getGuild().getId());
        if (muteRoleId == null) muteRoleId = BotConfig.MUTE_ROLE_ID;
        
        Role muteRole = event.getGuild().getRoleById(muteRoleId);
        if (muteRole != null && event.getChannel() instanceof IPermissionContainer container) {
            var override = container.upsertPermissionOverride(muteRole);
            if (event.getChannel().getId().equals(BotConfig.RULES_CHANNEL_ID)) {
                override.deny(Permission.MESSAGE_SEND,
                                Permission.MESSAGE_SEND_IN_THREADS,
                                Permission.CREATE_PUBLIC_THREADS,
                                Permission.CREATE_PRIVATE_THREADS,
                                Permission.MESSAGE_ADD_REACTION)
                        .clear(Permission.VIEW_CHANNEL)
                        .queue(null, e -> {});
            } else {
                override.deny(Permission.VIEW_CHANNEL,
                                Permission.MESSAGE_SEND,
                                Permission.MESSAGE_SEND_IN_THREADS,
                                Permission.CREATE_PUBLIC_THREADS,
                                Permission.CREATE_PRIVATE_THREADS,
                                Permission.MESSAGE_ADD_REACTION)
                        .queue(null, e -> {});
            }
        }

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Channel Created")
                .setColor(Color.GREEN)
                .setDescription("A new channel has been created in the server.\n")
                .addField("Channel Name", event.getChannel().getName(), false)
                .addField("Channel Mention", event.getChannel().getAsMention(), false)
                .addField("Channel ID", event.getChannel().getId(), false)
                .addField("Type", event.getChannelType().name(), false)
                .setTimestamp(Instant.now());
        bot.getLoggingService().logAction(event.getGuild(), "endzone-logs", embed.build());
    }

    @Override
    public void onChannelDelete(ChannelDeleteEvent event) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Channel Deleted")
                .setColor(Color.RED)
                .setDescription("A channel has been deleted from the server.\n")
                .addField("Channel Name", event.getChannel().getName(), false)
                .addField("Channel ID", event.getChannel().getId(), false)
                .addField("Type", event.getChannelType().name(), false)
                .setTimestamp(Instant.now());
        bot.getLoggingService().logAction(event.getGuild(), "endzone-logs", embed.build());
    }

    // --- Emoji Events ---

    @Override
    public void onEmojiAdded(EmojiAddedEvent event) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Emoji Added")
                .setColor(Color.GREEN)
                .setDescription("A new custom emoji has been added to the server.\n")
                .addField("Emoji Name", event.getEmoji().getName(), false)
                .addField("Emoji Preview", event.getEmoji().getFormatted(), false)
                .addField("Emoji ID", event.getEmoji().getId(), false)
                .setTimestamp(Instant.now());
        bot.getLoggingService().logAction(event.getGuild(), "endzone-logs", embed.build());
    }

    @Override
    public void onEmojiRemoved(EmojiRemovedEvent event) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Emoji Removed")
                .setColor(Color.RED)
                .setDescription("A custom emoji has been removed from the server.\n")
                .addField("Emoji Name", event.getEmoji().getName(), false)
                .addField("Emoji Preview", event.getEmoji().getFormatted(), false)
                .addField("Emoji ID", event.getEmoji().getId(), false)
                .setTimestamp(Instant.now());
        bot.getLoggingService().logAction(event.getGuild(), "endzone-logs", embed.build());
    }

    // --- Guild Events ---

    @Override
    public void onGuildJoin(GuildJoinEvent event) {
        Guild guild = event.getGuild();
        bot.getDataService().logGeneral(guild.getId(), null, "BOT_GUILD_JOIN", "Name: " + guild.getName());
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Bot Joined Server")
                .setColor(Color.BLUE)
                .setDescription("The bot has been added to a new server.\n")
                .addField("Server Name", guild.getName(), false)
                .addField("Server ID", guild.getId(), false)
                .addField("Member Count", String.valueOf(guild.getMemberCount()), false)
                .addField("Owner", guild.getOwner() != null ? guild.getOwner().getUser().getAsMention() : "Unknown", false)
                .setTimestamp(Instant.now());
        if (guild.getIconUrl() != null) embed.setThumbnail(guild.getIconUrl());
        bot.getLoggingService().logAction(guild, "endzone-logs", embed.build());
        
        guild.loadMembers().onSuccess(members -> {
            members.forEach(member -> bot.getUserCache().cacheUser(member.getUser()));
        });
    }

    @Override
    public void onGuildUpdateName(GuildUpdateNameEvent event) {
        bot.getDataService().logGeneral(event.getGuild().getId(), null, "GUILD_NAME_UPDATE", "Old: " + event.getOldName() + ", New: " + event.getNewName());
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Server Name Updated")
                .setColor(Color.ORANGE)
                .setDescription("The server name has been changed.\n")
                .addField("Old Name", event.getOldName(), false)
                .addField("New Name", event.getNewName(), false)
                .setTimestamp(Instant.now());
        bot.getLoggingService().logAction(event.getGuild(), "endzone-logs", embed.build());
    }

    @Override
    public void onGuildUpdateIcon(GuildUpdateIconEvent event) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Server Icon Updated")
                .setColor(Color.ORANGE)
                .setDescription("The server icon has been updated.\n")
                .addField("Old Icon", (event.getOldIconUrl() != null ? "[Link](" + event.getOldIconUrl() + ")" : "None"), false)
                .addField("New Icon", (event.getNewIconUrl() != null ? "[Link](" + event.getNewIconUrl() + ")" : "None"), false)
                .setTimestamp(Instant.now());
        if (event.getNewIconUrl() != null) embed.setThumbnail(event.getNewIconUrl());
        bot.getLoggingService().logAction(event.getGuild(), "endzone-logs", embed.build());
    }

    @Override
    public void onGuildInviteCreate(GuildInviteCreateEvent event) {
        User inviter = event.getInvite().getInviter();
        if (inviter != null && inviter.isBot()) return;
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Invite Created")
                .setColor(Color.GREEN)
                .setDescription(String.format("A new invite has been created for %s.\n", event.getChannel().getAsMention()))
                .addField("Inviter", inviter != null ? inviter.getAsMention() : "Unknown", false)
                .addField("User ID", inviter != null ? inviter.getId() : "Unknown", false)
                .addField("Invite Code", event.getCode(), false)
                .addField("Max Uses", event.getInvite().getMaxUses() == 0 ? "Unlimited" : String.valueOf(event.getInvite().getMaxUses()), false)
                .addField("Duration", event.getInvite().getMaxAge() == 0 ? "Never Expires" : (event.getInvite().getMaxAge() / 60) + " minutes", false)
                .setTimestamp(Instant.now());
        bot.getLoggingService().logAction(event.getGuild(), "endzone-logs", embed.build());
    }

    @Override
    public void onGuildBan(GuildBanEvent event) {
        User user = event.getUser();
        if (user.isBot()) return;
        bot.getDataService().logGeneral(event.getGuild().getId(), user.getId(), "GUILD_BAN", "Username: " + user.getName());
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("User Banned")
                .setColor(Color.RED)
                .setDescription("A user has been banned from the server.\n")
                .addField("User", user.getAsMention(), false)
                .addField("Username", user.getName(), false)
                .addField("User ID", user.getId(), false)
                .addField("Account Created", user.getTimeCreated().atZoneSameInstant(EST_ZONE).format(FORMATTER), false)
                .setTimestamp(Instant.now());
        if (user.getAvatarUrl() != null) embed.setThumbnail(user.getAvatarUrl());
        bot.getLoggingService().logAction(event.getGuild(), "endzone-logs", embed.build());
    }

    @Override
    public void onGuildUnban(GuildUnbanEvent event) {
        User user = event.getUser();
        if (user.isBot()) return;
        bot.getDataService().logGeneral(event.getGuild().getId(), user.getId(), "GUILD_UNBAN", "Username: " + user.getName());
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("User Unbanned")
                .setColor(Color.GREEN)
                .setDescription("A user has been unbanned from the server.\n")
                .addField("User", user.getAsMention(), false)
                .addField("Username", user.getName(), false)
                .addField("User ID", user.getId(), false)
                .setTimestamp(Instant.now());
        if (user.getAvatarUrl() != null) embed.setThumbnail(user.getAvatarUrl());
        bot.getLoggingService().logAction(event.getGuild(), "endzone-logs", embed.build());
    }

    // --- Member Events ---

    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        User user = event.getUser();
        if (user.isBot()) return;
        String accountCreationTime = user.getTimeCreated().atZoneSameInstant(EST_ZONE).format(FORMATTER);
        bot.getDataService().logGeneral(event.getGuild().getId(), user.getId(), "MEMBER_JOIN", "Username: " + user.getName() + ", Account Created: " + accountCreationTime);
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Member Joined")
                .setColor(Color.GREEN)
                .setDescription("A new member has joined the server.\n")
                .addField("User", user.getAsMention(), false)
                .addField("Username", user.getName(), false)
                .addField("User ID", user.getId(), false)
                .addField("Account Created", accountCreationTime, false)
                .setTimestamp(event.getMember().getTimeJoined());
        if (user.getAvatarUrl() != null) embed.setThumbnail(user.getAvatarUrl());
        bot.getLoggingService().logAction(event.getGuild(), "join-leave-logs", embed.build());
    }

    @Override
    public void onGuildMemberRemove(GuildMemberRemoveEvent event) {
        User user = event.getUser();
        if (user.isBot()) return;
        bot.getDataService().logGeneral(event.getGuild().getId(), user.getId(), "MEMBER_LEAVE", "Username: " + user.getName());
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Member Left")
                .setColor(Color.RED)
                .setDescription("A member has left the server.\n")
                .addField("User", user.getAsMention(), false)
                .addField("Username", user.getName(), false)
                .addField("User ID", user.getId(), false)
                .setTimestamp(Instant.now());
        if (user.getAvatarUrl() != null) embed.setThumbnail(user.getAvatarUrl());
        bot.getLoggingService().logAction(event.getGuild(), "join-leave-logs", embed.build());
    }

    @Override
    public void onGuildMemberUpdateNickname(GuildMemberUpdateNicknameEvent event) {
        User user = event.getUser();
        if (user.isBot()) return;
        bot.getDataService().logGeneral(event.getGuild().getId(), user.getId(), "NICKNAME_UPDATE", "Old: " + event.getOldNickname() + ", New: " + event.getNewNickname());
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Nickname Updated")
                .setColor(Color.ORANGE)
                .setDescription("A member has updated their nickname.\n")
                .addField("User", user.getAsMention(), false)
                .addField("Username", user.getName(), false)
                .addField("User ID", user.getId(), false)
                .addField("Old Nickname", (event.getOldNickname() != null ? event.getOldNickname() : "None"), false)
                .addField("New Nickname", (event.getNewNickname() != null ? event.getNewNickname() : "None"), false)
                .setTimestamp(Instant.now());
        if (user.getAvatarUrl() != null) embed.setThumbnail(user.getAvatarUrl());
        bot.getLoggingService().logAction(event.getGuild(), "name-logs", embed.build());
    }

    @Override
    public void onGuildMemberUpdateTimeOut(GuildMemberUpdateTimeOutEvent event) {
        User user = event.getUser();
        if (user.isBot()) return;
        bot.getDataService().logGeneral(event.getGuild().getId(), user.getId(), "TIMEOUT_UPDATE",
                "New Timeout End: " + (event.getNewTimeOutEnd() != null ? event.getNewTimeOutEnd().toString() : "None"));
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Member Timeout Updated")
                .setColor(Color.ORANGE)
                .setDescription("A member's timeout status has been updated.\n")
                .addField("User", user.getAsMention(), false)
                .addField("Username", user.getName(), false)
                .addField("User ID", user.getId(), false)
                .addField("New Timeout", (event.getNewTimeOutEnd() != null ? event.getNewTimeOutEnd().toString() : "None"), false)
                .setTimestamp(Instant.now());
        if (user.getAvatarUrl() != null) embed.setThumbnail(user.getAvatarUrl());
        bot.getLoggingService().logAction(event.getGuild(), "endzone-logs", embed.build());
    }

    @Override
    public void onGuildMemberUpdateAvatar(GuildMemberUpdateAvatarEvent event) {
        User user = event.getUser();
        if (user.isBot()) return;
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Member Avatar Updated")
                .setColor(Color.ORANGE)
                .setDescription("A member has updated their server avatar.\n")
                .addField("User", user.getAsMention(), false)
                .addField("Username", user.getName(), false)
                .addField("User ID", user.getId(), false)
                .setTimestamp(Instant.now());
        if (event.getNewAvatarUrl() != null) embed.setThumbnail(event.getNewAvatarUrl());
        bot.getLoggingService().logAction(event.getGuild(), "endzone-logs", embed.build());
    }

    // --- Message Events ---

    @Override
    public void onMessageUpdate(MessageUpdateEvent event) {
        if (event.getChannel().getId().equals("790177733207785472")) return;
        User author = event.getAuthor();
        if (author.isBot()) return;
        
        bot.getUserCache().cacheUser(author);
        String oldContent = bot.getMessageCache().getMessageContent(event.getMessageId());
        String newContent = event.getMessage().getContentDisplay();
        
        bot.getDataService().logMessage(event.getGuild().getId(), event.getChannel().getId(), event.getMessageId(), author.getId(), newContent, "UPDATED");
        
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Message Updated")
                .setColor(Color.ORANGE)
                .setDescription("A message has been edited.\n")
                .addField("User", author.getAsMention(), false)
                .addField("Channel", event.getChannel().getAsMention(), false)
                .addField("Message ID", event.getMessageId(), false)
                .addField("Old Content", oldContent, false)
                .addField("New Content", newContent, false)
                .setTimestamp(Instant.now());
        bot.getLoggingService().logAction(event.getGuild(), "message-logs", embed.build());
        bot.getMessageCache().cacheMessage(event.getMessage(), author.getId());
    }

    @Override
    public void onMessageDelete(MessageDeleteEvent event) {
        if (event.getChannel().getId().equals("790177733207785472")) return;
        String messageId = event.getMessageId();
        String content = bot.getMessageCache().getMessageContent(messageId);
        String userId = bot.getMessageCache().getMessageAuthorId(messageId);
        
        if (content == null || userId == null) {
            var dbEntry = bot.getDataService().getMessageFromLog(messageId);
            if (dbEntry != null) {
                if (content == null) content = dbEntry.content();
                if (userId == null) userId = dbEntry.userId();
            }
        }
        
        if (userId == null) return;
        
        User author = bot.getUserCache().retrieveUser(userId);
        if (author != null && author.isBot()) return;
        
        bot.getDataService().logMessage(event.getGuild().getId(), event.getChannel().getId(), messageId, userId, content, "DELETED");
        
        String[] info = bot.getUserCache().getUserDisplayInfo(userId);

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Message Deleted")
                .setColor(Color.RED)
                .setDescription("A message has been deleted.\n")
                .addField("Username", info[0], false)
                .addField("User ID", userId != null ? userId : "Unknown", false)
                .addField("Channel", event.getChannel().getAsMention(), false)
                .addField("Message ID", messageId, false)
                .addField("Content", content != null ? content : "Unknown content", false)
                .setTimestamp(Instant.now());
        bot.getLoggingService().logAction(event.getGuild(), "message-logs", embed.build());
        bot.getMessageCache().removeMessage(messageId);
    }

    @Override
    public void onMessageBulkDelete(MessageBulkDeleteEvent event) {
        if (event.getChannel().getId().equals("790177733207785472")) return;
        int loggedCount = 0;
        for (String messageId : event.getMessageIds()) {
            String content = bot.getMessageCache().getMessageContent(messageId);
            String userId = bot.getMessageCache().getMessageAuthorId(messageId);
            
            if (content == null || userId == null) {
                var dbEntry = bot.getDataService().getMessageFromLog(messageId);
                if (dbEntry != null) {
                    if (content == null) content = dbEntry.content();
                    if (userId == null) userId = dbEntry.userId();
                }
            }
            
            if (userId == null) continue;
            User author = bot.getUserCache().retrieveUser(userId);
            if (author != null && author.isBot()) continue;
            
            bot.getDataService().logMessage(event.getGuild().getId(), event.getChannel().getId(), messageId, userId, content, "BULK_DELETED");
            loggedCount++;
        }

        if (loggedCount == 0) {
            bot.getMessageCache().removeMessages(event.getMessageIds());
            return;
        }

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Bulk Message Delete")
                .setColor(Color.RED)
                .setDescription("Multiple messages have been deleted in bulk.\n")
                .addField("Channel", event.getChannel().getAsMention(), false)
                .addField("Messages Logged", String.valueOf(loggedCount), false)
                .setTimestamp(Instant.now());
        bot.getLoggingService().logAction(event.getGuild(), "endzone-logs", embed.build());
        bot.getMessageCache().removeMessages(event.getMessageIds());
    }

    @Override
    public void onMessageReactionAdd(MessageReactionAddEvent event) {
        if (event.getChannel().getId().equals("790177733207785472")) return;
        if (event.getUser() != null && event.getUser().isBot()) return;
        if (event.getUser() != null) bot.getUserCache().cacheUser(event.getUser());
        String emojiDisplay = event.getReaction().getEmoji().getAsReactionCode();
        String[] info = bot.getUserCache().getUserDisplayInfo(event.getUserId());
        User user = event.getUser();

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Reaction Added")
                .setColor(Color.GREEN)
                .setDescription("A reaction has been added to a message.\n")
                .addField("User", user != null ? user.getAsMention() : "Unknown", false)
                .addField("Username", user != null ? user.getName() : info[0], false)
                .addField("User ID", event.getUserId(), false)
                .addField("Channel", event.getChannel().getAsMention(), false)
                .addField("Message ID", event.getMessageId(), false)
                .addField("Reaction", emojiDisplay, false)
                .setTimestamp(Instant.now());
        bot.getLoggingService().logAction(event.getGuild(), "endzone-logs", embed.build());
    }

    @Override
    public void onMessageReactionRemove(MessageReactionRemoveEvent event) {
        if (event.getChannel().getId().equals("790177733207785472")) return;
        if (event.getUser() != null && event.getUser().isBot()) return;
        if (event.getUser() != null) bot.getUserCache().cacheUser(event.getUser());
        String emojiDisplay = event.getReaction().getEmoji().getAsReactionCode();
        String[] info = bot.getUserCache().getUserDisplayInfo(event.getUserId());
        User user = event.getUser();

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Reaction Removed")
                .setColor(Color.RED)
                .setDescription("A reaction has been removed from a message.\n")
                .addField("User", user != null ? user.getAsMention() : "Unknown", false)
                .addField("Username", user != null ? user.getName() : info[0], false)
                .addField("User ID", event.getUserId(), false)
                .addField("Channel", event.getChannel().getAsMention(), false)
                .addField("Message ID", event.getMessageId(), false)
                .addField("Reaction", emojiDisplay, false)
                .setTimestamp(Instant.now());
        bot.getLoggingService().logAction(event.getGuild(), "endzone-logs", embed.build());
    }

    // --- Role Events ---

    @Override
    public void onRoleCreate(RoleCreateEvent event) {
        bot.getDataService().logGeneral(event.getGuild().getId(), null, "ROLE_CREATE", "Role: " + event.getRole().getName() + " (" + event.getRole().getId() + ")");
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Role Created")
                .setColor(Color.GREEN)
                .setDescription("A new role has been created.\n")
                .addField("Role", event.getRole().getAsMention(), false)
                .addField("Role Name", event.getRole().getName(), false)
                .addField("Role ID", event.getRole().getId(), false)
                .setTimestamp(Instant.now());
        bot.getLoggingService().logAction(event.getGuild(), "endzone-logs", embed.build());
    }

    @Override
    public void onRoleDelete(RoleDeleteEvent event) {
        bot.getDataService().logGeneral(event.getGuild().getId(), null, "ROLE_DELETE", "Role: " + event.getRole().getName() + " (" + event.getRole().getId() + ")");
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Role Deleted")
                .setColor(Color.RED)
                .setDescription("A role has been deleted.\n")
                .addField("Role Name", event.getRole().getName(), false)
                .addField("Role ID", event.getRole().getId(), false)
                .setTimestamp(Instant.now());
        bot.getLoggingService().logAction(event.getGuild(), "endzone-logs", embed.build());
    }

    @Override
    public void onRoleUpdateName(RoleUpdateNameEvent event) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Role Name Updated")
                .setColor(Color.ORANGE)
                .setDescription("A role name has been updated.\n")
                .addField("Role", event.getRole().getAsMention(), false)
                .addField("Role ID", event.getRole().getId(), false)
                .addField("Old Name", event.getOldName(), false)
                .addField("New Name", event.getNewName(), false)
                .setTimestamp(Instant.now());
        bot.getLoggingService().logAction(event.getGuild(), "endzone-logs", embed.build());
    }

    @Override
    public void onRoleUpdateColor(RoleUpdateColorEvent event) {
        String oldHex = event.getOldColor() != null ? String.format("#%06X", (0xFFFFFF & event.getOldColor().getRGB())) : "None";
        String newHex = event.getNewColor() != null ? String.format("#%06X", (0xFFFFFF & event.getNewColor().getRGB())) : "None";
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Role Color Updated")
                .setColor(Color.ORANGE)
                .setDescription("A role color has been updated.\n")
                .addField("Role", event.getRole().getAsMention(), false)
                .addField("Role ID", event.getRole().getId(), false)
                .addField("Old Color", oldHex, false)
                .addField("New Color", newHex, false)
                .setTimestamp(Instant.now());
        bot.getLoggingService().logAction(event.getGuild(), "endzone-logs", embed.build());
    }

    @Override
    public void onRoleUpdatePermissions(RoleUpdatePermissionsEvent event) {
        EnumSet<Permission> oldPerms = event.getOldPermissions();
        EnumSet<Permission> newPerms = event.getNewPermissions();

        List<String> added = newPerms.stream()
                .filter(p -> !oldPerms.contains(p))
                .map(Permission::getName)
                .collect(Collectors.toList());

        List<String> removed = oldPerms.stream()
                .filter(p -> !newPerms.contains(p))
                .map(Permission::getName)
                .collect(Collectors.toList());

        StringBuilder changes = new StringBuilder();
        if (!added.isEmpty()) {
            changes.append("**Added:** ").append(String.join(", ", added)).append("\n");
        }
        if (!removed.isEmpty()) {
            changes.append("**Removed:** ").append(String.join(", ", removed)).append("\n");
        }
        if (changes.length() == 0) {
            changes.append("No significant changes.");
        }

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Role Permissions Updated")
                .setColor(Color.ORANGE)
                .setDescription("Role permissions have been updated.\n")
                .addField("Role", event.getRole().getName(), false)
                .addField("Role ID", event.getRole().getId(), false)
                .addField("Changes", changes.toString(), false)
                .setTimestamp(Instant.now());
        bot.getLoggingService().logAction(event.getGuild(), "endzone-logs", embed.build());
    }

    // --- User Events ---

    @Override
    public void onUserUpdateName(UserUpdateNameEvent event) {
        if (event.getUser().isBot()) return;
        bot.getUserCache().cacheUser(event.getUser());
        if (event.getUser().getMutualGuilds().isEmpty()) return;
        User user = event.getUser();

        for (Guild guild : user.getMutualGuilds()) {
            bot.getDataService().logGeneral(guild.getId(), user.getId(), "USER_NAME_UPDATE", "Old: " + event.getOldName() + ", New: " + event.getNewName());
        }

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("User Name Updated")
                .setColor(Color.ORANGE)
                .setDescription("A user has updated their global username.\n")
                .addField("User", user.getAsMention(), false)
                .addField("User ID", user.getId(), false)
                .addField("Old Name", event.getOldName(), false)
                .addField("New Name", event.getNewName(), false)
                .setTimestamp(Instant.now());
        if (user.getAvatarUrl() != null) embed.setThumbnail(user.getAvatarUrl());
        bot.getLoggingService().logAction(user.getMutualGuilds().get(0), "name-logs", embed.build());
    }

    @Override
    public void onUserUpdateAvatar(UserUpdateAvatarEvent event) {
        if (event.getUser().isBot()) return;
        bot.getUserCache().cacheUser(event.getUser());
        if (event.getUser().getMutualGuilds().isEmpty()) return;
        User user = event.getUser();

        for (Guild guild : user.getMutualGuilds()) {
            bot.getDataService().logGeneral(guild.getId(), user.getId(), "USER_AVATAR_UPDATE", "Avatar URL updated");
        }

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("User Avatar Updated")
                .setColor(Color.ORANGE)
                .setDescription("A user has updated their global avatar.\n")
                .addField("User", user.getAsMention(), false)
                .addField("Username", user.getName(), false)
                .addField("User ID", user.getId(), false)
                .addField("Old Avatar", (event.getOldAvatarUrl() != null ? "[Link](" + event.getOldAvatarUrl() + ")" : "None"), false)
                .addField("New Avatar", (event.getNewAvatarUrl() != null ? "[Link](" + event.getNewAvatarUrl() + ")" : "None"), false)
                .setTimestamp(Instant.now());
        if (event.getNewAvatarUrl() != null) embed.setThumbnail(event.getNewAvatarUrl());
        bot.getLoggingService().logAction(user.getMutualGuilds().get(0), "endzone-logs", embed.build());
    }

    // --- Voice Events ---

    @Override
    public void onGuildVoiceUpdate(GuildVoiceUpdateEvent event) {
        if (event.getMember().getUser().isBot()) return;
        bot.getUserCache().cacheUser(event.getMember().getUser());
        User user = event.getMember().getUser();

        if (event.getChannelJoined() != null && event.getChannelLeft() == null) {
            // Join
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("Voice Channel Joined")
                    .setColor(Color.GREEN)
                    .setDescription("A member has joined a voice channel.\n")
                    .addField("User", user.getAsMention(), false)
                    .addField("Username", user.getName(), false)
                    .addField("User ID", user.getId(), false)
                    .addField("Channel", event.getChannelJoined().getAsMention(), false)
                    .setTimestamp(Instant.now());
            bot.getLoggingService().logAction(event.getGuild(), "voice-logs", embed.build());
        } else if (event.getChannelLeft() != null && event.getChannelJoined() == null) {
            // Leave
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("Voice Channel Left")
                    .setColor(Color.RED)
                    .setDescription("A member has left a voice channel.\n")
                    .addField("User", user.getAsMention(), false)
                    .addField("Username", user.getName(), false)
                    .addField("User ID", user.getId(), false)
                    .addField("Channel", event.getChannelLeft().getAsMention(), false)
                    .setTimestamp(Instant.now());
            bot.getLoggingService().logAction(event.getGuild(), "voice-logs", embed.build());
        } else if (event.getChannelLeft() != null && event.getChannelJoined() != null) {
            // Move
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("Voice Channel Moved")
                    .setColor(Color.BLUE)
                    .setDescription("A member has moved between voice channels.\n")
                    .addField("User", user.getAsMention(), false)
                    .addField("Username", user.getName(), false)
                    .addField("User ID", user.getId(), false)
                    .addField("Old Channel", event.getChannelLeft().getAsMention(), false)
                    .addField("New Channel", event.getChannelJoined().getAsMention(), false)
                    .setTimestamp(Instant.now());
            bot.getLoggingService().logAction(event.getGuild(), "voice-logs", embed.build());
        }
    }
}
