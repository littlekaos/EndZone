package EndZone.commands;

import EndZone.config.BotConfig;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.awt.Color;
import java.time.Instant;
import java.util.List;

public class HelpCommand implements Command {

    @Override
    public List<CommandData> getCommandDataList() {
        return List.of(Commands.slash("help", "View a detailed list of all bot commands and features")
                .setDefaultPermissions(DefaultMemberPermissions.ENABLED));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        net.dv8tion.jda.api.entities.Guild guild = event.getGuild();
        boolean isCourtZone = false;
        
        if (guild != null) {
            String guildId = guild.getId();
            // Check by ID or if the server has the specific Judge role
            isCourtZone = guildId.equals(BotConfig.COURT_GUILD_ID) || 
                         guild.getRoleById(BotConfig.COURT_THE_JUDGE_EZ_ROLE_ID) != null;
        }

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle(isCourtZone ? "🤖 CourtZone Help - Ban Appeals" : "🤖 EndZone Bot - Command Help")
                .setDescription(isCourtZone 
                    ? "Command guide for the CourtZone [ Ban Appeal ] server\n"
                    : "A comprehensive guide to all available commands and features\n")
                .setColor(new Color(0, 150, 255))
                .setThumbnail("https://cdn.discordapp.com/emojis/" + BotConfig.EZ_EMOJI_ID + ".png");

        if (isCourtZone) {
            addCourtZoneCommands(embed);
        } else {
            addGeneralCommands(embed);
            addEventNameCommands(embed);
            addModerationCommands(embed);
            addStrikeCommands(embed);
            addVoiceCommands(embed);
            addAdminCommands(embed);
        }
        addNeedHelpSection(embed);

        embed.setFooter("Use /[command] to execute. Moderator commands require proper permissions." + (guild != null ? " | Server ID: " + guild.getId() : ""))
                .setTimestamp(Instant.now());

        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }

    private void addCourtZoneCommands(EmbedBuilder embed) {
        embed.addField(
                "📌 General Commands",
                "**`/help`** - View this help menu\n" +
                "**`/test`** - Check if the bot is operational\n" +
                "**`/afk [reason]`** - Set your AFK status\n" +
                "**`/afk [reason] [user]`** - Set another user's AFK status **<@&" + BotConfig.COURT_THE_DISTRICT_ATTORNIES_EZ_ROLE_ID + "> +**",
                false
        );

        embed.addField(
                "🔨 Moderation Actions",
                "**`/warn`** - Warn a user **<@&" + BotConfig.COURT_THE_DISTRICT_ATTORNIES_EZ_ROLE_ID + "> +**\n" +
                "**`/mute`** - Mute a user **<@&" + BotConfig.COURT_THE_DISTRICT_ATTORNIES_EZ_ROLE_ID + "> +**\n" +
                "**`/unmute`** - Unmute a user **<@&" + BotConfig.COURT_THE_DISTRICT_ATTORNIES_EZ_ROLE_ID + "> +**\n" +
                "**`/timeout`** - Timeout a user **<@&" + BotConfig.COURT_THE_DISTRICT_ATTORNIES_EZ_ROLE_ID + "> +**\n" +
                "**`/untimeout`** - Remove timeout **<@&" + BotConfig.COURT_THE_DISTRICT_ATTORNIES_EZ_ROLE_ID + "> +**\n" +
                "**`/kick`** - Kick a user **<@&" + BotConfig.COURT_THE_DISTRICT_ATTORNIES_EZ_ROLE_ID + "> +**\n" +
                "**`/ban`** - Ban a user **<@&" + BotConfig.COURT_THE_JUDGE_EZ_ROLE_ID + "> +**\n" +
                "**`/unban`** - Unban a user **<@&" + BotConfig.COURT_THE_JUDGE_EZ_ROLE_ID + "> +**\n" +
                "**`/purge`** - Delete messages **<@&" + BotConfig.COURT_THE_DISTRICT_ATTORNIES_EZ_ROLE_ID + "> +**\n" +
                "**`/reason`** - View ban history **<@&" + BotConfig.COURT_THE_DISTRICT_ATTORNIES_EZ_ROLE_ID + "> +**",
                false
        );

        embed.addField(
                "⚖️ Strike & Appeal System",
                "**`/strike`** - Issue a strike **<@&" + BotConfig.COURT_THE_DISTRICT_ATTORNIES_EZ_ROLE_ID + "> +**\n" +
                "**`/strikes`** - View user strikes **<@&" + BotConfig.COURT_THE_DISTRICT_ATTORNIES_EZ_ROLE_ID + "> +**\n" +
                "**`/removestrike`** - Remove a specific strike **<@&" + BotConfig.COURT_THE_DISTRICT_ATTORNIES_EZ_ROLE_ID + "> +**\n" +
                "**`/clearstrikes`** - Clear all strikes **<@&" + BotConfig.COURT_THE_DISTRICT_ATTORNIES_EZ_ROLE_ID + "> +**\n" +
                "**`/editstrike`** - Edit a strike reason **<@&" + BotConfig.COURT_THE_DISTRICT_ATTORNIES_EZ_ROLE_ID + "> +**\n" +
                "**`/appeal`** - Appeal your strikes\n" +
                "**`/myappeals`** - View your appeals\n" +
                "**`/pendingappeals`** - View pending appeals **<@&" + BotConfig.COURT_THE_DISTRICT_ATTORNIES_EZ_ROLE_ID + "> +**\n" +
                "**`/reviewappeal`** - Approve/deny appeal **<@&" + BotConfig.COURT_THE_DISTRICT_ATTORNIES_EZ_ROLE_ID + "> +**\n" +
                "**`/undoappeal`** - Reset a user's appeal **<@&" + BotConfig.COURT_THE_DISTRICT_ATTORNIES_EZ_ROLE_ID + "> +**",
                false
        );

        embed.addField(
                "🎙️ Voice Channel Management",
                "**`/vchelp`** - Detailed help for voice commands\n" +
                "**`/setup`** - Interactive setup for managed voice channels **<@&" + BotConfig.COURT_THE_DISTRICT_ATTORNIES_EZ_ROLE_ID + "> +**\n" +
                "**`/createvoice`** - Create a temporary voice channel **<@&" + BotConfig.COURT_THE_DISTRICT_ATTORNIES_EZ_ROLE_ID + "> +**\n" +
                "**`/deletevoice`** - Delete a channel you created **<@&" + BotConfig.COURT_THE_DISTRICT_ATTORNIES_EZ_ROLE_ID + "> +**\n" +
                "**`/mychannels`** - View your creation history\n" +
                "**`/activechannels`** - View currently active channels **<@&" + BotConfig.COURT_THE_DISTRICT_ATTORNIES_EZ_ROLE_ID + "> +**\n" +
                "**`/vcstats`** - View usage statistics **<@&" + BotConfig.COURT_THE_DISTRICT_ATTORNIES_EZ_ROLE_ID + "> +**",
                false
        );

        embed.addField(
                "⚙️ System & Admin",
                "**`/say`** - Send a message with formatting **<@&" + BotConfig.COURT_THE_DISTRICT_ATTORNIES_EZ_ROLE_ID + "> +**\n" +
                "**`/eventping`** - Send an event ping **<@&" + BotConfig.COURT_THE_DISTRICT_ATTORNIES_EZ_ROLE_ID + "> +**\n" +
                "**`/eventcountdowntoggle`** - Toggle weekly countdown **<@&" + BotConfig.COURT_THE_JUDGE_EZ_ROLE_ID + "> +**\n" +
                "**`/signup-ping`** - Ping a role for signup **<@&" + BotConfig.COURT_THE_DISTRICT_ATTORNIES_EZ_ROLE_ID + "> +**\n" +
                "**`/steal`** - Steal an emoji from another server **<@&" + BotConfig.COURT_THE_DISTRICT_ATTORNIES_EZ_ROLE_ID + "> +**\n" +
                "**`/reactionrole`** - Setup and manage reaction roles **<@&" + BotConfig.COURT_THE_DISTRICT_ATTORNIES_EZ_ROLE_ID + "> +**\n" +
                "**`/dbinfo`** - View strike system statistics **<@&" + BotConfig.COURT_THE_DISTRICT_ATTORNIES_EZ_ROLE_ID + "> +**\n" +
                "**`/backupstrikes`** - Backup strike database **<@&" + BotConfig.COURT_THE_DISTRICT_ATTORNIES_EZ_ROLE_ID + "> +**\n" +
                "**`/rolerestoration`** - Manage role restoration **<@&" + BotConfig.COURT_THE_DISTRICT_ATTORNIES_EZ_ROLE_ID + "> +**\n" +
                "**`/void-checker`** - Analyze reactions **<@&" + BotConfig.COURT_THE_DISTRICT_ATTORNIES_EZ_ROLE_ID + "> +**",
                false
        );
    }

    private void addGeneralCommands(EmbedBuilder embed) {
        embed.addField(
                "📌 General Commands",
                "**`/help`** - View this help menu\n" +
                "**`/test`** - Check if the bot is operational\n" +
                "**`/afk [reason]`** - Set your AFK status\n" +
                "**`/afk [reason] [user]`** - Set another user's AFK status **<@&" + BotConfig.ALPHA_BETAS_ROLE_ID + "> +**",
                false
        );
    }

    private void addEventNameCommands(EmbedBuilder embed) {
        embed.addField(
                "📝 Event Name Commands",
                "**`/eventname submit`** - Register your in-game name for events\n" +
                "**`/eventname check`** - Look up a user's event name **<@&" + BotConfig.SENIOR_SENTINELS_ROLE_ID + "> +**",
                false
        );
    }

    private void addModerationCommands(EmbedBuilder embed) {
        embed.addField(
                "🔨 Moderation Actions",
                "**`/warn`** - Warn a user **<@&" + BotConfig.TRIAL_SENTINELS_ROLE_ID + "> +**\n" +
                "**`/mute`** - Mute a user **<@&" + BotConfig.SENIOR_SENTINELS_ROLE_ID + "> +**\n" +
                "**`/unmute`** - Unmute a user **<@&" + BotConfig.SENIOR_SENTINELS_ROLE_ID + "> +**\n" +
                "**`/timeout`** - Timeout a user **<@&" + BotConfig.SENIOR_SENTINELS_ROLE_ID + "> +**\n" +
                "**`/untimeout`** - Remove timeout **<@&" + BotConfig.SENIOR_SENTINELS_ROLE_ID + "> +**\n" +
                "**`/kick`** - Kick a user **<@&" + BotConfig.SENIOR_SENTINELS_ROLE_ID + "> +**\n" +
                "**`/ban`** - Ban a user **<@&" + BotConfig.ALPHA_BETAS_ROLE_ID + "> +**\n" +
                "**`/unban`** - Unban a user **<@&" + BotConfig.ALPHA_BETAS_ROLE_ID + "> +**\n" +
                "**`/purge`** - Delete messages **<@&" + BotConfig.SENIOR_SENTINELS_ROLE_ID + "> +**\n" +
                "**`/reason`** - View ban history **<@&" + BotConfig.ALPHA_BETAS_ROLE_ID + "> +**",
                false
        );
        embed.addField(
                "🔧 Role & Channel Management",
                "**`/role add/remove`** - Manage user roles **<@&" + BotConfig.ALPHA_BETAS_ROLE_ID + "> +**\n" +
                "**`/setmuterole`** - Set the mute role **<@&" + BotConfig.ALPHA_BETAS_ROLE_ID + "> +**\n" +
                "**`/restrict`** - Add channel restriction **<@&" + BotConfig.ALPHA_BETAS_ROLE_ID + "> +**\n" +
                "**`/unrestrict`** - Remove channel restriction **<@&" + BotConfig.ALPHA_BETAS_ROLE_ID + "> +**\n" +
                "**`/restrict-setup`** - Setup restrictions **<@&" + BotConfig.ALPHA_BETAS_ROLE_ID + "> +**",
                false
        );
    }

    private void addStrikeCommands(EmbedBuilder embed) {
        embed.addField(
                "⛔ Strike & Appeal System",
                "**`/strike`** - Issue a strike **<@&" + BotConfig.ALPHA_BETAS_ROLE_ID + "> +**\n" +
                "**`/strikes`** - View user strikes **<@&" + BotConfig.GFX_CONTENT_TEAM_ROLE_ID + "> +**\n" +
                "**`/removestrike`** - Remove a specific strike **<@&" + BotConfig.ALPHA_BETAS_ROLE_ID + "> +**\n" +
                "**`/clearstrikes`** - Clear all strikes **<@&" + BotConfig.ALPHA_BETAS_ROLE_ID + "> +**\n" +
                "**`/editstrike`** - Edit a strike reason **<@&" + BotConfig.ALPHA_BETAS_ROLE_ID + "> +**\n" +
                "**`/appeal`** - Appeal your strikes\n" +
                "**`/myappeals`** - View your appeals\n" +
                "**`/pendingappeals`** - View pending appeals **<@&" + BotConfig.ALPHA_BETAS_ROLE_ID + "> +**\n" +
                "**`/reviewappeal`** - Approve/deny appeal **<@&" + BotConfig.ALPHA_BETAS_ROLE_ID + "> +**\n" +
                "**`/undoappeal`** - Reset a user's appeal **<@&" + BotConfig.ALPHA_BETAS_ROLE_ID + "> +**",
                false
        );
    }

    private void addVoiceCommands(EmbedBuilder embed) {
        embed.addField(
                "🎙️ Voice Channel Management",
                "**`/vchelp`** - Detailed help for voice commands\n" +
                "**`/setup`** - Interactive setup for managed voice channels **<@&" + BotConfig.ALPHA_BETAS_ROLE_ID + "> +**\n" +
                "**`/createvoice`** - Create a temporary voice channel **<@&" + BotConfig.ALPHA_BETAS_ROLE_ID + "> +**\n" +
                "**`/deletevoice`** - Delete a channel you created **<@&" + BotConfig.ALPHA_BETAS_ROLE_ID + "> +**\n" +
                "**`/mychannels`** - View your creation history\n" +
                "**`/activechannels`** - View currently active channels **<@&" + BotConfig.ALPHA_BETAS_ROLE_ID + "> +**\n" +
                "**`/vcstats`** - View usage statistics **<@&" + BotConfig.ALPHA_BETAS_ROLE_ID + "> +**\n" +
                "**`/vcdbinfo`** - View voice database stats **<@&" + BotConfig.ALPHA_BETAS_ROLE_ID + "> +**",
                false
        );
    }

    private void addAdminCommands(EmbedBuilder embed) {
        embed.addField(
                "⚙️ System Tools",
                "**`/dbinfo`** - View strike system statistics **<@&" + BotConfig.ALPHA_BETAS_ROLE_ID + "> +**\n" +
                "**`/backupstrikes`** - Backup strike database **<@&" + BotConfig.ALPHA_BETAS_ROLE_ID + "> +**\n" +
                "**`/checkroles`** - Trigger manual role restoration check **<@&" + BotConfig.ALPHA_BETAS_ROLE_ID + "> +**\n" +
                "**`/clearblacklist`** - Clear the permanent demotion list **<@&" + BotConfig.ALPHA_BETAS_ROLE_ID + "> +**\n" +
                "**`/appealscanner`** - Manage the automated appeal scanner **<@&" + BotConfig.ALPHA_BETAS_ROLE_ID + "> +**\n" +
                "**`/rolerestoration`** - Manage the role restoration service **<@&" + BotConfig.ALPHA_BETAS_ROLE_ID + "> +**\n" +
                "**`/void-checker`** - Analyze message reactions **<@&" + BotConfig.ALPHA_BETAS_ROLE_ID + "> +**\n" +
                "**`/scan`** - Trigger manual reaction scan **<@&" + BotConfig.ALPHA_BETAS_ROLE_ID + "> +**",
                false
        );
        embed.addField(
                "📢 Communication & Demotions",
                "**`/initdemotionlist`** - Initialize demotion list message **<@&" + BotConfig.ALPHA_BETAS_ROLE_ID + "> +**\n" +
                "**`/updatedemotionlist`** - Force update demotion list **<@&" + BotConfig.ALPHA_BETAS_ROLE_ID + "> +**\n" +
                "**`/adddemotion`** - Manually add to demotion list **<@&" + BotConfig.ALPHA_BETAS_ROLE_ID + "> +**\n" +
                "**`/bulkadddemotions`** - Bulk add to demotion list **<@&" + BotConfig.ALPHA_BETAS_ROLE_ID + "> +**\n" +
                "**`/removedemotion`** - Remove from demotion list **<@&" + BotConfig.ALPHA_BETAS_ROLE_ID + "> +**\n" +
                "**`/bulkremovedemotion`** - Bulk remove from demotion list **<@&" + BotConfig.ALPHA_BETAS_ROLE_ID + "> +**\n" +
                "**`/say`** - Send a message with formatting **<@&" + BotConfig.ALPHA_BETAS_ROLE_ID + "> +**\n" +
                "**`/eventping`** - Send an event ping **<@&" + BotConfig.ALPHA_BETAS_ROLE_ID + "> +**\n" +
                "**`/eventcountdowntoggle`** - Toggle weekly countdown **<@&" + BotConfig.ALPHA_BETAS_ROLE_ID + "> +**\n" +
                "**`/signup-ping`** - Ping a role for signup **<@&" + BotConfig.ALPHA_BETAS_ROLE_ID + "> +**\n" +
                "**`/steal`** - Steal an emoji from another server **<@&" + BotConfig.ALPHA_BETAS_ROLE_ID + "> +**\n" +
                "**`/reactionrole`** - Setup and manage reaction roles **<@&" + BotConfig.ALPHA_BETAS_ROLE_ID + "> +**",
                false
        );
    }

    private void addNeedHelpSection(EmbedBuilder embed) {
        embed.addField(
                "📞 Need Help?",
                "Reach out to <@" + BotConfig.OWNER_USER_ID + "> if it's an immediate emergency.",
                false
        );
    }
}
