package EndZone.commands;

import EndZone.EndZone;
import EndZone.config.BotConfig;
import EndZone.models.VoiceChannelRecord;
import EndZone.services.ServiceManager;
import EndZone.services.VoiceChannelManager;
import EndZone.utils.PermissionUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.awt.Color;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;

public class VoiceCommand implements Command {
    private final EndZone bot;
    private final VoiceChannelManager channelManager;

    public VoiceCommand(EndZone bot) {
        this.bot = bot;
        this.channelManager = ServiceManager.getVoiceChannelManager();
    }

    @Override
    public List<CommandData> getCommandDataList() {
        return List.of(
            Commands.slash("vchelp", "View detailed help about voice commands"),
            Commands.slash("setup", "Set up the Voice Channel Manager for this server"),
            Commands.slash("createvoice", "Create a new voice channel")
                .addOption(OptionType.STRING, "name", "The name of the voice channel", true)
                .addOption(OptionType.INTEGER, "limit", "User limit (0 for no limit)", false),
            Commands.slash("deletevoice", "Delete a voice channel you created")
                .addOption(OptionType.CHANNEL, "channel", "The voice channel to delete", true),
            Commands.slash("vcstats", "View voice channel statistics")
                .addOption(OptionType.STRING, "type", "Type of stats (server/global/user)", true)
                .addOption(OptionType.USER, "user", "User to view stats for (optional)", false),
            Commands.slash("mychannels", "View your recently created voice channels"),
            Commands.slash("activechannels", "View currently active voice channels"),
            Commands.slash("vcdbinfo", "View voice channel database information (Admin only)"),
            Commands.slash("vclock", "Lock your voice channel"),
            Commands.slash("vcunlock", "Unlock your voice channel"),
            Commands.slash("rename", "Rename your voice channel")
                .addOption(OptionType.STRING, "name", "The new name for the voice channel", true),
            Commands.slash("limit", "Change the user limit for your voice channel")
                .addOption(OptionType.INTEGER, "limit", "The new user limit (0 for no limit)", true)
                .addOption(OptionType.USER, "user", "Target user's voice channel (Alpha Beta+ only)", false)
        );
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        switch (event.getName()) {
            case "vchelp":
                handleHelp(event);
                break;
            case "setup":
                // Handled by EventsSetupCommandListener
                break;
            case "createvoice":
                handleCreate(event);
                break;
            case "deletevoice":
                handleDelete(event);
                break;
            case "vcstats":
                handleStats(event);
                break;
            case "mychannels":
                handleMyChannels(event);
                break;
            case "activechannels":
                handleActive(event);
                break;
            case "vcdbinfo":
                handleDbInfo(event);
                break;
            case "vclock":
                handleLock(event);
                break;
            case "vcunlock":
                handleUnlock(event);
                break;
            case "rename":
                handleRename(event);
                break;
            case "limit":
                handleLimit(event);
                break;
        }
    }

    private void handleHelp(SlashCommandInteractionEvent event) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🎙️ Voice Channel Manager Help")
                .setColor(new Color(0, 200, 100))
                .setDescription("Manage your own voice channels and track activity.\n")
                .addField("Commands",
                        "**`/setup`** - Interactive setup for managed VCs **Alpha Beta+**\n" +
                        "**`/createvoice`** - Create a temporary voice channel **Alpha Beta+**\n" +
                        "**`/deletevoice`** - Delete a channel you created **Alpha Beta+**\n" +
                        "**`/mychannels`** - View your creation history\n" +
                        "**`/activechannels`** - View currently active channels **Alpha Beta+**\n" +
                        "**`/vcstats`** - View usage statistics **Alpha Beta+**\n" +
                        "**`/vclock`** - Lock your voice channel\n" +
                        "**`/vcunlock`** - Unlock your voice channel\n" +
                        "**`/rename`** - Rename your voice channel\n" +
                        "**`/limit`** - Change your voice channel limit\n" +
                        "**`/vcdbinfo`** - View database stats **Alpha Beta+**", false)
                .setTimestamp(Instant.now());
        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }

    private boolean hasVoiceAdminPermissions(Member member) {
        if (member == null) return false;
        return PermissionUtils.isAdmin(member, ServiceManager.getConfig());
    }

    private boolean canManageVoiceChannel(Member member, VoiceChannel channel) {
        if (member == null || channel == null) return false;

        // 1. Check if creator (for auto-created temporary channels)
        String creatorId = channelManager.getVoiceService().getCreatorId(channel.getId());
        if (member.getId().equals(creatorId)) return true;

        // 2. Check Admin roles
        if (PermissionUtils.isAdmin(member, ServiceManager.getConfig())) return true;

        // 3. Check Alpha and Alpha Betas roles
        if (member.getRoles().stream()
                .anyMatch(role -> role.getId().equals(BotConfig.ALPHAS_ROLE_ID) ||
                                 role.getId().equals(BotConfig.ALPHA_BETAS_ROLE_ID))) {
            return true;
        }

        // 4. Special handling for Winners VCs (and other managed categories)
        // If it's a managed channel and the user is CURRENTLY IN IT, allow management
        // (This covers static VCs like Winners Zones where no specific creator_id exists in DB)
        if (channelManager.isManagedVoiceChannel(channel)) {
            if (member.getVoiceState() != null && channel.equals(member.getVoiceState().getChannel())) {
                return true;
            }
        }

        return false;
    }

    private void handleLock(SlashCommandInteractionEvent event) {
        if (event.getMember() == null || event.getMember().getVoiceState() == null || !event.getMember().getVoiceState().inAudioChannel()) {
            event.reply("You must be in a voice channel to use this command.").setEphemeral(true).queue();
            return;
        }

        VoiceChannel channel = (VoiceChannel) event.getMember().getVoiceState().getChannel();
        if (channel == null || !channelManager.isManagedVoiceChannel(channel)) {
            event.reply("This is not a managed voice channel.").setEphemeral(true).queue();
            return;
        }

        if (!canManageVoiceChannel(event.getMember(), channel)) {
            event.reply("You don't have permission to lock this channel.").setEphemeral(true).queue();
            return;
        }

        event.deferReply(true).queue();

        // To lock: remove CONNECT permission for @everyone and Member role
        net.dv8tion.jda.api.entities.Role memberRole = event.getGuild().getRoleById(BotConfig.MEMBER_ROLE_ID);
        var manager = channel.getManager()
                .putPermissionOverride(event.getGuild().getPublicRole(), null, EnumSet.of(Permission.VOICE_CONNECT));
        
        if (memberRole != null) {
            manager = manager.putPermissionOverride(memberRole, null, EnumSet.of(Permission.VOICE_CONNECT));
        }

        manager.queue(
            v -> event.getHook().sendMessage("✅ Channel **" + channel.getName() + "** has been locked.").queue(),
            error -> event.getHook().sendMessage("❌ Failed to lock channel: " + error.getMessage()).queue()
        );
    }

    private void handleUnlock(SlashCommandInteractionEvent event) {
        if (event.getMember() == null || event.getMember().getVoiceState() == null || !event.getMember().getVoiceState().inAudioChannel()) {
            event.reply("You must be in a voice channel to use this command.").setEphemeral(true).queue();
            return;
        }

        VoiceChannel channel = (VoiceChannel) event.getMember().getVoiceState().getChannel();
        if (channel == null || !channelManager.isManagedVoiceChannel(channel)) {
            event.reply("This is not a managed voice channel.").setEphemeral(true).queue();
            return;
        }

        if (!canManageVoiceChannel(event.getMember(), channel)) {
            event.reply("You don't have permission to unlock this channel.").setEphemeral(true).queue();
            return;
        }

        event.deferReply(true).queue();

        // To unlock: grant CONNECT permission for @everyone and Member role
        net.dv8tion.jda.api.entities.Role memberRole = event.getGuild().getRoleById(BotConfig.MEMBER_ROLE_ID);
        var manager = channel.getManager()
                .putPermissionOverride(event.getGuild().getPublicRole(), EnumSet.of(Permission.VOICE_CONNECT), null);

        if (memberRole != null) {
            manager = manager.putPermissionOverride(memberRole, EnumSet.of(Permission.VOICE_CONNECT), null);
        }

        manager.queue(
            v -> event.getHook().sendMessage("✅ Channel **" + channel.getName() + "** has been unlocked.").queue(),
            error -> event.getHook().sendMessage("❌ Failed to unlock channel: " + error.getMessage()).queue()
        );
    }

    private void handleRename(SlashCommandInteractionEvent event) {
        if (event.getMember() == null || event.getMember().getVoiceState() == null || !event.getMember().getVoiceState().inAudioChannel()) {
            event.reply("You must be in a voice channel to use this command.").setEphemeral(true).queue();
            return;
        }

        VoiceChannel channel = (VoiceChannel) event.getMember().getVoiceState().getChannel();
        if (channel == null || !channelManager.isManagedVoiceChannel(channel)) {
            event.reply("This is not a managed voice channel.").setEphemeral(true).queue();
            return;
        }

        if (!canManageVoiceChannel(event.getMember(), channel)) {
            event.reply("You don't have permission to rename this channel.").setEphemeral(true).queue();
            return;
        }

        String newName = event.getOption("name").getAsString();
        event.deferReply(true).queue();

        channel.getManager().setName(newName).queue(
            v -> event.getHook().sendMessage("✅ Channel renamed to **" + newName + "**.").queue(),
            error -> event.getHook().sendMessage("❌ Failed to rename channel: " + error.getMessage()).queue()
        );
    }

    private void handleLimit(SlashCommandInteractionEvent event) {
        User targetUser = event.getOption("user") != null ? event.getOption("user").getAsUser() : null;
        VoiceChannel channel = null;

        if (targetUser != null) {
            // Check if user is Alpha Beta+ to modify others' channels
            if (!PermissionUtils.isAlphaBetaOrHigher(event.getMember(), ServiceManager.getConfig())) {
                event.reply("You don't have permission to modify other users' voice channels.").setEphemeral(true).queue();
                return;
            }

            // Find target user's active channel
            List<VoiceChannelRecord> records = channelManager.getVoiceService().getActiveChannels(event.getGuild().getId());
            for (VoiceChannelRecord record : records) {
                if (record.getCreatorId().equals(targetUser.getId())) {
                    channel = event.getGuild().getVoiceChannelById(record.getChannelId());
                    break;
                }
            }

            if (channel == null) {
                event.reply("Could not find an active managed voice channel for user **" + targetUser.getName() + "**.").setEphemeral(true).queue();
                return;
            }
        } else {
            // Use current channel
            if (event.getMember() == null || event.getMember().getVoiceState() == null || !event.getMember().getVoiceState().inAudioChannel()) {
                event.reply("You must be in a voice channel to use this command, or specify a user (Alpha Beta+ only).").setEphemeral(true).queue();
                return;
            }

            channel = (VoiceChannel) event.getMember().getVoiceState().getChannel();
            if (channel == null || !channelManager.isManagedVoiceChannel(channel)) {
                event.reply("This is not a managed voice channel.").setEphemeral(true).queue();
                return;
            }

            if (!canManageVoiceChannel(event.getMember(), channel)) {
                event.reply("You don't have permission to modify the limit of this channel.").setEphemeral(true).queue();
                return;
            }
        }

        int limit = event.getOption("limit").getAsInt();
        if (limit < 0 || limit > 99) {
            event.reply("Limit must be between 0 and 99.").setEphemeral(true).queue();
            return;
        }

        // Restriction for normal users: they can only add 1 more person
        if (!PermissionUtils.isAlphaBetaOrHigher(event.getMember(), ServiceManager.getConfig())) {
            int currentLimit = channel.getUserLimit();
            
            // Get original limit from database record if available
            int initialLimit = -1;
            VoiceChannelRecord record = channelManager.getVoiceService().getChannelRecord(channel.getId());
            if (record != null) {
                initialLimit = record.getUserLimit();
            }

            // Determine absolute max limit based on channel type
            int maxLimit = 99;
            String catName = channel.getParentCategory() != null ? channel.getParentCategory().getName().toLowerCase() : "";
            String chanName = channel.getName().toLowerCase();
            
            // Winners Duo/Squad zones are often static but let's check name pattern too
            if (catName.contains("duo") || chanName.contains("duo") || chanName.contains(" d")) {
                maxLimit = 3;
            } else if (catName.contains("squad") || chanName.contains("squad") || chanName.contains(" s")) {
                maxLimit = 5;
            }

            // If we have an initial limit (like from auto-created channels), cap at initialLimit + 1
            if (initialLimit != -1) {
                maxLimit = Math.min(maxLimit, initialLimit + 1);
            }

            if (limit > maxLimit) {
                event.reply("You cannot set the limit higher than " + maxLimit + " for this channel type.").setEphemeral(true).queue();
                return;
            }
            
            // Still allow only +1 at a time compared to current
            if (limit > currentLimit + 1) {
                event.reply("You can only increase your voice channel limit by 1 at a time.").setEphemeral(true).queue();
                return;
            }
        }

        event.deferReply(true).queue();

        final VoiceChannel finalChannel = channel;
        final int finalLimit = limit;
        channel.getManager().setUserLimit(limit).queue(
            v -> event.getHook().sendMessage("✅ Channel limit for **" + finalChannel.getName() + "** set to **" + (finalLimit == 0 ? "No limit" : finalLimit) + "**.").queue(),
            error -> event.getHook().sendMessage("❌ Failed to change limit: " + error.getMessage()).queue()
        );
    }

    private void handleCreate(SlashCommandInteractionEvent event) {
        if (!hasVoiceAdminPermissions(event.getMember())) {
            event.reply("You don't have permission to create voice channels. Only Alpha Beta+ can use this command.")
                    .setEphemeral(true).queue();
            return;
        }

        String name = event.getOption("name").getAsString();
        int limit = event.getOption("limit") != null ? event.getOption("limit").getAsInt() : 0;

        if (limit < 0 || limit > 99) {
            event.reply("Limit must be between 0 and 99.").setEphemeral(true).queue();
            return;
        }

        event.deferReply(true).queue();

        channelManager.createCustomVoiceChannel(event.getGuild(), event.getUser(), name, limit, 
            () -> event.getHook().sendMessage("✅ Voice channel **" + name + "** created!").queue(),
            error -> event.getHook().sendMessage("❌ Failed to create channel: " + error.getMessage()).queue()
        );
    }

    private void handleDelete(SlashCommandInteractionEvent event) {
        if (!hasVoiceAdminPermissions(event.getMember())) {
            event.reply("You don't have permission to delete voice channels. Only Alpha Beta+ can use this command.")
                    .setEphemeral(true).queue();
            return;
        }

        VoiceChannel channel = event.getOption("channel").getAsChannel().asVoiceChannel();
        
        event.deferReply(true).queue();
        channel.delete().queue(v -> {
            channelManager.deleteUserVoiceChannel(event.getUser(), channel);
            event.getHook().sendMessage("✅ Channel deleted.").queue();
        }, error -> {
            event.getHook().sendMessage("❌ Failed to delete channel: " + error.getMessage()).queue();
        });
    }

    private void handleStats(SlashCommandInteractionEvent event) {
        if (!hasVoiceAdminPermissions(event.getMember())) {
            event.reply("You don't have permission to view voice stats. Only Alpha Beta+ can use this command.")
                    .setEphemeral(true).queue();
            return;
        }

        String type = event.getOption("type").getAsString().toLowerCase();
        User user = event.getOption("user") != null ? event.getOption("user").getAsUser() : event.getUser();

        EmbedBuilder embed = new EmbedBuilder().setTimestamp(Instant.now());

        if (type.equals("user")) {
            List<VoiceChannelRecord> channels = channelManager.getVoiceService().getUserCreatedChannels(user.getId(), event.getGuild().getId());
            embed.setTitle("📊 User Stats: " + user.getName())
                 .setColor(Color.BLUE)
                 .setDescription("Total channels created: " + channels.size());
            if (!channels.isEmpty()) {
                StringBuilder sb = new StringBuilder("**Recent Channels:**\n");
                for (int i = 0; i < Math.min(channels.size(), 5); i++) {
                    sb.append("• ").append(channels.get(i).getChannelName()).append("\n");
                }
                embed.addField("", sb.toString(), false);
            }
        } else if (type.equals("server")) {
            int activeCount = channelManager.getVoiceService().getActiveChannelCount(event.getGuild().getId());
            embed.setTitle("📊 Server Stats")
                 .setColor(Color.GREEN)
                 .addField("Active Channels", String.valueOf(activeCount), false);
        } else {
            int total = channelManager.getVoiceService().getTotalChannelsCreated();
            embed.setTitle("🌍 Global Stats")
                 .setColor(Color.ORANGE)
                 .addField("Total Channels Created", String.valueOf(total), false);
        }

        event.replyEmbeds(embed.build()).queue();
    }

    private void handleMyChannels(SlashCommandInteractionEvent event) {
        List<VoiceChannelRecord> channels = channelManager.getVoiceService().getUserCreatedChannels(event.getUser().getId(), event.getGuild().getId());
        if (channels.isEmpty()) {
            event.reply("You haven't created any channels yet.").setEphemeral(true).queue();
            return;
        }

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("📋 Your Voice Channels")
                .setColor(Color.CYAN);
        
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(channels.size(), 10); i++) {
            VoiceChannelRecord r = channels.get(i);
            sb.append("**").append(r.getChannelName()).append("** - ").append(r.isActive() ? "🟢 Active" : "🔴 Deleted").append("\n");
        }
        embed.setDescription(sb.toString());
        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }

    private void handleActive(SlashCommandInteractionEvent event) {
        if (!hasVoiceAdminPermissions(event.getMember())) {
            event.reply("You don't have permission to view active voice channels. Only Alpha Beta+ can use this command.")
                    .setEphemeral(true).queue();
            return;
        }

        List<VoiceChannelRecord> channels = channelManager.getVoiceService().getActiveChannels(event.getGuild().getId());
        if (channels.isEmpty()) {
            event.reply("No active managed channels.").queue();
            return;
        }

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🔊 Active Channels")
                .setColor(Color.GREEN);
        
        StringBuilder sb = new StringBuilder();
        for (VoiceChannelRecord r : channels) {
            sb.append("• **").append(r.getChannelName()).append("** (Created by ").append(r.getCreatorName()).append(")\n");
        }
        embed.setDescription(sb.toString());
        event.replyEmbeds(embed.build()).queue();
    }

    private void handleDbInfo(SlashCommandInteractionEvent event) {
        if (!hasVoiceAdminPermissions(event.getMember())) {
            event.reply("You do not have permission to view database info.").setEphemeral(true).queue();
            return;
        }

        int totalChannels = channelManager.getVoiceService().getTotalChannelsCreated();
        int activeChannels = channelManager.getVoiceService().getActiveChannelCount(event.getGuild().getId());

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("📊 Voice Database Information")
                .setColor(Color.CYAN)
                .addField("Total Channels Tracked", String.valueOf(totalChannels), false)
                .addField("Active Channels (This Server)", String.valueOf(activeChannels), false)
                .setFooter("Voice Channel Alpha Beta+ System")
                .setTimestamp(Instant.now());

        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }
}
