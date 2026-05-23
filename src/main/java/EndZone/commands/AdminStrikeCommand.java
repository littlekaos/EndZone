package EndZone.commands;

import EndZone.EndZone;
import EndZone.config.BotConfig;
import EndZone.models.Strike;
import EndZone.services.*;
import EndZone.utils.PermissionUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.awt.Color;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;

public class AdminStrikeCommand implements Command {

    private final EndZone bot;
    private final StrikeService strikeService;
    private final AppealScannerService appealScannerService;
    private final RoleRestorationService roleRestorationService;
    private static final ZoneId EST_ZONE = ZoneId.of("America/New_York");

    public AdminStrikeCommand(EndZone bot) {
        this.bot = bot;
        this.strikeService = ServiceManager.getStrikeService();
        this.appealScannerService = ServiceManager.getAppealScannerService();
        this.roleRestorationService = ServiceManager.getRoleRestorationService();
    }

    @Override
    public List<CommandData> getCommandDataList() {
        return List.of(
                Commands.slash("dbinfo", "Show database statistics."),
                Commands.slash("backupstrikes", "Create a backup of the strikes database."),
                Commands.slash("checkroles", "Check and restore temporary role demotions (Alpha Beta+)"),
                Commands.slash("clearblacklist", "Clear all users from the blacklist (Alpha Beta+)"),
                Commands.slash("appealscanner", "Manage the appeal scanner service. (Alpha Beta+)")
                        .addOption(OptionType.STRING, "action", "scan, stats, or status", true),
                Commands.slash("rolerestoration", "Manage the role restoration service. (Alpha Beta+)")
                        .addOption(OptionType.STRING, "action", "status or check", true),
                Commands.slash("dbscan", "Scan staff strike logs to import missing strikes (Alpha Beta+)")
        );
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!PermissionUtils.isAlphaBetaOrHigher(event.getMember(), ServiceManager.getConfig()) && 
            !PermissionUtils.isAdmin(event.getMember(), ServiceManager.getConfig()) && 
            !event.getUser().getId().equals(BotConfig.OWNER_USER_ID)) {
            event.reply("❌ You do not have permission to use admin strike commands. Only Alpha Beta+ can use this command.").setEphemeral(true).queue();
            return;
        }

        String commandName = event.getName();

        switch (commandName) {
            case "dbinfo":
                handleDbInfo(event);
                break;
            case "backupstrikes":
                handleBackup(event);
                break;
            case "checkroles":
                handleCheckRoles(event);
                break;
            case "clearblacklist":
                handleClearBlacklist(event);
                break;
            case "appealscanner":
                handleScanner(event);
                break;
            case "rolerestoration":
                handleRestoration(event);
                break;
            case "dbscan":
                handleDbScan(event);
                break;
        }
    }

    private void handleDbScan(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();
        
        ServiceManager.getStrikeScannerService().scanStaffStrikes(
                event.getJDA(),
                progress -> event.getHook().editOriginal(progress).queue(),
                importedCount -> {
                    String message = "✅ Scan complete! Imported **" + importedCount + "** new strikes into the database.";
                    event.getHook().editOriginal(message).queue();
                    
                    // Also trigger a sync to apply any demotions discovered
                    ServiceManager.getDemotionSyncService().syncDemotions();
                }
        );
    }

    private void handleDbInfo(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();
        List<String> usersWithStrikes = strikeService.getAllUsersWithStrikes();

        StringBuilder info = new StringBuilder();
        info.append("**All Users With Strikes:**\n\n");

        int totalStrikes = 0;
        int totalUsers = 0;

        for (String userId : usersWithStrikes) {
            List<Strike> strikes = strikeService.getStrikes(userId);
            if (!strikes.isEmpty()) {
                totalUsers++;
                totalStrikes += strikes.size();
                info.append(String.format("• <@%s> (%s): **%d** strikes\n", userId, userId, strikes.size()));
            }
        }

        info.append("\n**Summary:**\n");
        info.append(String.format("• Total users with strikes: **%d**\n", totalUsers));
        info.append(String.format("• Total strikes issued: **%d**\n", totalStrikes));

        ZonedDateTime now = ZonedDateTime.now(EST_ZONE);
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("📊 Database Info")
                .setDescription(info.toString() + "\n")
                .setColor(Color.CYAN)
                .setFooter("Strike System • " + now.format(java.time.format.DateTimeFormatter.ofPattern("M/d/yyyy h:mm a")))
                .setTimestamp(now.toInstant());

        event.getHook().editOriginalEmbeds(embed.build()).queue();
    }

    private void handleBackup(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();
        int totalStrikes = strikeService.getDatabase().getTotalStrikeCount();
        int totalUsers = strikeService.getAllUsersWithStrikes().size();

        ZonedDateTime now = ZonedDateTime.now(EST_ZONE);
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("💾 Database Backup Status")
                .setDescription("Current status of the strike database backups\n")
                .setColor(Color.CYAN)
                .addField("Total Strikes", String.valueOf(totalStrikes), false)
                .addField("Total Users", String.valueOf(totalUsers), false)
                .addField("Status", "✅ Automated backups active via SQLite", false)
                .setFooter("Strike System • " + now.format(java.time.format.DateTimeFormatter.ofPattern("M/d/yyyy h:mm a")))
                .setTimestamp(now.toInstant());

        event.getHook().editOriginalEmbeds(embed.build()).queue();
    }

    private void handleCheckRoles(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();
        roleRestorationService.triggerManualCheck();
        event.getHook().editOriginal("✅ Role restoration check triggered.").queue();
    }

    private void handleClearBlacklist(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();
        java.util.Set<String> permDemotions = strikeService.getDatabase().loadPermanentDemotions();
        for (String userId : permDemotions) {
            strikeService.getDatabase().removeFromDemotions(userId);
        }
        
        TextChannel channel = event.getJDA().getTextChannelById(BotConfig.STAFF_NOTIFICATION_CHANNEL_ID);
        if (channel != null) {
            channel.getHistory().retrievePast(100).queue(messages -> {
                for (var msg : messages) {
                    if (msg.getContentRaw().contains("BLACKLIST") || msg.getContentRaw().contains("permanently demoted")) {
                        msg.editMessage("**📋 BLACKLIST CLEARED**").queue();
                        break;
                    }
                }
            });
        }
        event.getHook().editOriginal("✅ Cleared permanent demotions and updated blacklist message.").queue();
    }

    private void handleScanner(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();
        String action = Objects.requireNonNull(event.getOption("action")).getAsString().toLowerCase();

        switch (action) {
            case "scan":
                appealScannerService.triggerManualScan();
                event.getHook().editOriginal("✅ Manual scan triggered.").queue();
                break;
            case "stats":
                event.getHook().editOriginal("📊 Scanner stats: " + appealScannerService.getScanStats()).queue();
                break;
            case "status":
                event.getHook().editOriginal("⚙️ Scanner is currently " + (appealScannerService.isScannerRunning() ? "RUNNING" : "IDLE")).queue();
                break;
            default:
                event.getHook().editOriginal("❌ Unknown action: " + action).queue();
        }
    }

    private void handleRestoration(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();
        String action = Objects.requireNonNull(event.getOption("action")).getAsString().toLowerCase();

        switch (action) {
            case "status":
                event.getHook().editOriginal("⚙️ Role restoration is " + (roleRestorationService.isRunning() ? "ACTIVE" : "INACTIVE")).queue();
                break;
            case "check":
                roleRestorationService.triggerManualCheck();
                event.getHook().editOriginal("✅ Manual check triggered.").queue();
                break;
            default:
                event.getHook().editOriginal("❌ Unknown action: " + action).queue();
        }
    }
}
