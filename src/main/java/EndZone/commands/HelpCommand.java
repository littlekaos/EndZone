package EndZone.commands;

import EndZone.config.BotConfig;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;

import java.awt.Color;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class HelpCommand implements Command {

    @Override
    public List<CommandData> getCommandDataList() {
        return List.of(Commands.slash("help", "View a detailed list of all bot commands and features")
                .setDefaultPermissions(DefaultMemberPermissions.ENABLED));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        sendHelpMenu(event);
    }

    public static void sendHelpMenu(SlashCommandInteractionEvent event) {
        boolean isCourtZone = isCourtZone(event.getGuild());
        
        EmbedBuilder embed = createBaseEmbed(isCourtZone)
                .setDescription(isCourtZone 
                    ? "Welcome to the CourtZone Help Menu. Select a category below to view specific commands."
                    : "Welcome to the EndZone Help Menu. Select a category below to view specific commands.");

        List<Button> buttons = new ArrayList<>();
        List<Button> row2 = new ArrayList<>();
        if (isCourtZone) {
            buttons.add(Button.primary("help_court_general", "📌 General"));
            buttons.add(Button.primary("help_court_mod", "🔨 Moderation"));
            buttons.add(Button.primary("help_court_strike", "⚖️ Strike/Appeal"));
            buttons.add(Button.primary("help_court_voice", "🎙️ Voice"));
            buttons.add(Button.primary("help_court_admin", "⚙️ Admin"));
            row2.add(Button.primary("help_court_modmail", "📬 Modmail"));
        } else {
            buttons.add(Button.primary("help_gen_general", "📌 General"));
            buttons.add(Button.primary("help_gen_mod", "🔨 Moderation"));
            buttons.add(Button.primary("help_gen_strike", "⛔ Strike/Appeal"));
            buttons.add(Button.primary("help_gen_voice", "🎙️ Voice"));
            buttons.add(Button.primary("help_gen_admin", "⚙️ Admin"));
            row2.add(Button.primary("help_gen_modmail", "📬 Modmail"));
        }

        event.replyEmbeds(embed.build())
                .addComponents(ActionRow.of(buttons), ActionRow.of(row2))
                .setEphemeral(true)
                .queue();
    }

    public static EmbedBuilder createBaseEmbed(boolean isCourtZone) {
        return new EmbedBuilder()
                .setTitle(isCourtZone ? "🤖 CourtZone Help" : "🤖 EndZone Bot Help")
                .setColor(new Color(0, 150, 255))
                .setThumbnail("https://cdn.discordapp.com/emojis/" + BotConfig.EZ_EMOJI_ID + ".png")
                .setTimestamp(Instant.now());
    }

    public static boolean isCourtZone(net.dv8tion.jda.api.entities.Guild guild) {
        if (guild == null) return false;
        String guildId = guild.getId();
        return guildId.equals(BotConfig.COURT_GUILD_ID) || 
               guild.getRoleById(BotConfig.COURT_THE_JUDGE_EZ_ROLE_ID) != null;
    }

    public static void addCourtZoneGeneral(EmbedBuilder embed) {
        embed.addField(
                "📌 General Commands",
                "**`/help`** - View this help menu\n" +
                "**`/test`** - Check if the bot is operational\n" +
                "**`/afk [reason]`** - Set your AFK status\n" +
                "**`/afk [reason] [user]`** - Set another user's AFK status **<@&" + BotConfig.COURT_THE_DISTRICT_ATTORNIES_EZ_ROLE_ID + "> +**",
                false
        );
    }

    public static void addCourtZoneMod(EmbedBuilder embed) {
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
    }

    public static void addCourtZoneStrike(EmbedBuilder embed) {
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
    }

    public static void addCourtZoneVoice(EmbedBuilder embed) {
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
    }

    public static void addCourtZoneAdmin(EmbedBuilder embed) {
        embed.addField(
                "⚙️ System Tools",
                "**`/say`** - Send a message with formatting **<@&" + BotConfig.COURT_THE_DISTRICT_ATTORNIES_EZ_ROLE_ID + "> +**\n" +
                "**`/eventping`** - Send an event ping **<@&" + BotConfig.COURT_THE_DISTRICT_ATTORNIES_EZ_ROLE_ID + "> +**\n" +
                "**`/scheduler`** - Toggle scheduled events **<@&" + BotConfig.COURT_THE_JUDGE_EZ_ROLE_ID + "> +**\n" +
                "**`/lockdown begin/end`** - Manage channel lockdown **<@&" + BotConfig.COURT_THE_JUDGE_EZ_ROLE_ID + "> +**\n" +
                "**`/signup-ping`** - Ping a role for signup **<@&" + BotConfig.COURT_THE_DISTRICT_ATTORNIES_EZ_ROLE_ID + "> +**\n" +
                "**`/steal`** - Steal an emoji from another server **<@&" + BotConfig.COURT_THE_DISTRICT_ATTORNIES_EZ_ROLE_ID + "> +**\n" +
                "**`/reactionrole`** - Setup and manage reaction roles **<@&" + BotConfig.COURT_THE_DISTRICT_ATTORNIES_EZ_ROLE_ID + "> +**",
                false
        );

        embed.addField(
                "🛠️ Admin Utilities",
                "**`/dbinfo`** - View strike system statistics **<@&" + BotConfig.COURT_THE_DISTRICT_ATTORNIES_EZ_ROLE_ID + "> +**\n" +
                "**`/backupstrikes`** - Backup strike database **<@&" + BotConfig.COURT_THE_DISTRICT_ATTORNIES_EZ_ROLE_ID + "> +**\n" +
                "**`/rolerestoration`** - Manage role restoration **<@&" + BotConfig.COURT_THE_DISTRICT_ATTORNIES_EZ_ROLE_ID + "> +**\n" +
                "**`/void-checker`** - Analyze reactions **<@&" + BotConfig.COURT_THE_DISTRICT_ATTORNIES_EZ_ROLE_ID + "> +**",
                false
        );
    }

    public static void addCourtZoneModmail(EmbedBuilder embed) {
        embed.addField(
                "📬 How modmail works",
                "DM the bot to open a ticket, then pick a category (**Unban Request** or **General Concern**).\n" +
                "Staff handle tickets in CourtZone Ticket Zone. Gem Event is coming soon.",
                false
        );
        embed.addField(
                "📬 Modmail Commands",
                "**`/reply message:`** - Send a staff reply to the user in an open ticket **Staff**\n" +
                "**`/close [time] [cancel]`** - Close now, schedule a timed close, or cancel a timed close\n" +
                "**`/logs user:`** - View past ticket history with web log links **Staff**\n" +
                "**`/clear-logs [user]`** - Delete stored ticket history **Staff**\n" +
                "**`/cleardms`** - Delete the bot's messages in your DM with it",
                false
        );
    }

    public static void addGeneralGeneral(EmbedBuilder embed) {
        embed.addField(
                "📌 General Commands",
                "**`/help`** - View this help menu\n" +
                "**`/test`** - Check if the bot is operational\n" +
                "**`/afk [reason]`** - Set your AFK status\n" +
                "**`/afk [reason] [user]`** - Set another user's AFK status **<@&" + BotConfig.ALPHA_BETAS_ROLE_ID + "> +**",
                false
        );
        embed.addField(
                "📝 Event Name Commands",
                "**`/eventname submit`** - Register your in-game name for events\n" +
                "**`/eventname check`** - Look up a user's event name **<@&" + BotConfig.SENIOR_SENTINELS_ROLE_ID + "> +**",
                false
        );
    }

    public static void addGeneralMod(EmbedBuilder embed) {
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

    public static void addGeneralStrike(EmbedBuilder embed) {
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

    public static void addGeneralVoice(EmbedBuilder embed) {
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

    public static void addGeneralAdmin(EmbedBuilder embed) {
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
                "📢 Demotion Management",
                "**`/initdemotionlist`** - Initialize demotion list message **<@&" + BotConfig.ALPHA_BETAS_ROLE_ID + "> +**\n" +
                "**`/updatedemotionlist`** - Force update demotion list **<@&" + BotConfig.ALPHA_BETAS_ROLE_ID + "> +**\n" +
                "**`/adddemotion`** - Manually add to demotion list **<@&" + BotConfig.ALPHA_BETAS_ROLE_ID + "> +**\n" +
                "**`/bulkadddemotions`** - Bulk add to demotion list **<@&" + BotConfig.ALPHA_BETAS_ROLE_ID + "> +**\n" +
                "**`/removedemotion`** - Remove from demotion list **<@&" + BotConfig.ALPHA_BETAS_ROLE_ID + "> +**\n" +
                "**`/bulkremovedemotion`** - Bulk remove from demotion list **<@&" + BotConfig.ALPHA_BETAS_ROLE_ID + "> +**",
                false
        );
        embed.addField(
                "📢 Communication & Server Tools",
                "**`/say`** - Send a message with formatting **<@&" + BotConfig.ALPHA_BETAS_ROLE_ID + "> +**\n" +
                "**`/eventping`** - Send an event ping **<@&" + BotConfig.ALPHA_BETAS_ROLE_ID + "> +**\n" +
                "**`/scheduler`** - Toggle scheduled events **<@&" + BotConfig.ALPHA_BETAS_ROLE_ID + "> +**\n" +
                "**`/lockdown begin/end`** - Manage channel lockdown **<@&" + BotConfig.ALPHA_BETAS_ROLE_ID + "> +**\n" +
                "**`/signup-ping`** - Ping a role for signup **<@&" + BotConfig.ALPHA_BETAS_ROLE_ID + "> +**\n" +
                "**`/steal`** - Steal an emoji from another server **<@&" + BotConfig.ALPHA_BETAS_ROLE_ID + "> +**\n" +
                "**`/reactionrole`** - Setup and manage reaction roles **<@&" + BotConfig.ALPHA_BETAS_ROLE_ID + "> +**",
                false
        );
    }

    public static void addGeneralModmail(EmbedBuilder embed) {
        embed.addField(
                "📬 How modmail works",
                "DM the bot to open a ticket, then pick a category (**Unban Request** or **General Concern**).\n" +
                "Tickets are created in CourtZone Ticket Zone. Gem Event is coming soon.",
                false
        );
        embed.addField(
                "📬 Modmail Commands",
                "**`/reply message:`** - Send a staff reply to the user in an open ticket **Staff**\n" +
                "**`/close [time] [cancel]`** - Close now, schedule a timed close, or cancel a timed close\n" +
                "**`/logs user:`** - View past ticket history with web log links **Staff**\n" +
                "**`/clear-logs [user]`** - Delete stored ticket history **Staff**\n" +
                "**`/cleardms`** - Delete the bot's messages in your DM with it",
                false
        );
    }

    public static void addNeedHelpSection(EmbedBuilder embed) {
        embed.addField(
                "📞 Need Help?",
                "Reach out to <@" + BotConfig.OWNER_USER_ID + "> if it's an immediate emergency.",
                false
        );
    }
}
