package EndZone.schedulers;

import EndZone.config.BotConfig;
import EndZone.forms.EndZoneForm;
import EndZone.services.DataService;
import EndZone.services.ServiceManager;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SchedulerManager {
    private static ScheduledExecutorService scheduler;
    private static ZonedDateTime lastStaffAnnouncementTime;
    private static ZonedDateTime lastEventCountdownTime;
    private static ZonedDateTime lastWeeklyAnnouncementDraftTime;

    public static void start(JDA jda) {
        scheduler = Executors.newScheduledThreadPool(4);

        // Auto-export scheduler
        startAutoExportScheduler();

        // Status Heartbeat (Every 10 minutes)
        startStatusHeartbeat();

        // Weekly Winners Reset (Sunday @ 6 PM EDT)
        startWeeklyWinnersReset(jda);

        // Daily Staff Announcements (10 AM EST, skipping Monday)
        startDailyAnnouncements(jda);

        // Weekly Event Countdown (Sunday @ 12 PM EST)
        startEventCountdown(jda);

        // Weekly Announcement (Sunday @ 11 AM EST)
        startWeeklyAnnouncement(jda);
        
        // Access Help Automatic Role (30 minutes)
        startAccessHelpRoleScheduler(jda);
    }

    private static String getReminderOrdinal(DayOfWeek day) {
        switch (day) {
            case TUESDAY: return "first";
            case WEDNESDAY: return "second";
            case THURSDAY: return "third";
            case FRIDAY: return "fourth";
            case SATURDAY: return "fifth";
            case SUNDAY: return "sixth";
            default: return "first";
        }
    }

    private static String buildStaffMessage(String timestamp, String ordinal) {
        return "This is your " + ordinal + " reminder to signup for the sheet for the event " + timestamp + "! || @everyone ||\n" +
                "\n" +
                "** **\n" +
                "## MAKE SURE YOU FILL UP ALL SPOTS OR I WILL START STRIKING!\n" +
                "** **\n" +
                "\n" +
                "## There is __ 4 __ <@&1478880351584522302> spots, __ 4 __ <@&1478880395696013343> spots, __ 4 __ <@&1478880444131840172> spots, __ 4 __ <@&1478880498423169124> spots, and __ 4 __ <@&1478880563229233173> spots!\n" +
                "** **\n" +
                "\n" +
                "**__PLEASE CLAIM ALL SPOTS OR USE <#1479282088095121479> IF YOU'RE NOT GOING TO BE HERE!__**\n" +
                "\n" +
                "<@&1478880351584522302> - You post custom codes in <#1478566788177330349>.\n" +
                "<@&1478880395696013343> - Moderating Game A and report back in <#1478568674091860069> what's going on + have access to <#1478566788177330349>.\n" +
                "<@&1478880444131840172> - Moderating Game B and report back in <#1478568674091860069> what's going on + have access to <#1478566788177330349>.\n" +
                "<@&1478880498423169124> - Mostly moderating <#1478556185933381722>, but helping <#1478566857584808147> so their rosters are correct.\n" +
                "<@&1478880563229233173> - Moderating VC's to make sure there is a correct amount of people in them.\n" +
                "\n" +
                "Link: https://docs.google.com/spreadsheets/d/1CFLGG-f_-75yw4AgX6lWQ5lkgSthoObhzzHCAtn2Uow/edit?usp=sharing";
    }

    private static String buildEventMessage() {
        return "<:EZ_new:1478805339011809350> **Main Event**\n" +
                "\n" +
                "|| @everyone ||" +
                "\n" +
                "\n" +
                "**100 reacts to start/ 110 reacts for game B!**\n" +
                "\n" +
                "`2` <:DuosEZ:1465846857103048766> / `8` <:SquadsEZ:1478805345471041637> Games!\n" +
                "\n" +
                "`2` <:USEEZ:1457446258170925118> / `2` <:EUEZ:1478805353679425780> __Starting with EU!__\n" +
                "\n" +
                "<#790162355404144670> apply.\n" +
                "\n" +
                "<#1478574358741127268> for the rest of the rules, **__they are important__**!\n" +
                "\n" +
                "## DON'T FORGET TO SEND YOUR CLAN LOGOS IN <#1478566982931582997>\n" +
                "\n" +
                "** **\n" +
                "**__SUBMIT YOUR ROSTERS IN <#1478566857584808147>!__**";
    }

    private static String buildWinnerMessage() {
        return "If you’re representing the clan that won last week's event, get the <@&" + BotConfig.WINNER_ROLE_ID + "> role here!";
    }

    private static void startAutoExportScheduler() {
        scheduler.scheduleAtFixedRate(() -> {
            EndZoneForm.exportAllFormStatesToDatabase();
        }, 5, 5, TimeUnit.MINUTES);
        System.out.println("[SCHEDULER] Auto-export scheduler started (every 5 minutes)");
    }

    private static void startStatusHeartbeat() {
        scheduler.scheduleAtFixedRate(() -> {
            System.out.println("--- [SCHEDULER HEALTH CHECK] ---");
            System.out.println("Status: Active and Scanning");
            System.out.println("Last Staff Announcement: " + (lastStaffAnnouncementTime != null ? lastStaffAnnouncementTime : "None Yet"));
            System.out.println("Last Event Countdown: " + (lastEventCountdownTime != null ? lastEventCountdownTime : "None Yet"));
            System.out.println("--------------------------------");
        }, 1, 10, TimeUnit.MINUTES);
    }

    private static void performWinnersReset(JDA jda) {
        try {
            ZoneId edtZone = ZoneId.of("America/New_York");
            ZonedDateTime now = ZonedDateTime.now(edtZone);
            String todayDate = now.toLocalDate().toString();
            String lastRun = ServiceManager.getDataService().getMetadata("last_winners_reset_run", "");

            if (todayDate.equals(lastRun)) {
                System.out.println("[SCHEDULER] Winners reset already performed today (" + todayDate + "), skipping.");
                return;
            }

            String guildId = BotConfig.GUILD_ID;
            Guild guild = jda.getGuildById(guildId);
            if (guild == null) return;

            System.out.println("[SCHEDULER] Running Winners VC and Role reset...");

            // 1. Reset Winners VCs (Duo zones to 2, Squad zones to 4)
            for (VoiceChannel vc : guild.getVoiceChannels()) {
                if (vc.getParentCategory() != null) {
                    String catName = vc.getParentCategory().getName().toLowerCase();
                    if (catName.contains("winners")) {
                        int targetLimit = 0;
                        String chanName = vc.getName().toLowerCase();
                        if (chanName.contains("d") || chanName.contains("duo")) targetLimit = 2;
                        else if (chanName.contains("s") || chanName.contains("squad")) targetLimit = 4;

                        if (targetLimit > 0 && vc.getUserLimit() != targetLimit) {
                            vc.getManager().setUserLimit(targetLimit).queue();
                        }
                    }
                }
            }

            // 2. Clear Roles
            List<String> rolesToClear = Arrays.asList(
                    "1478880351584522302",
                    "1478880395696013343",
                    "1478880444131840172",
                    "1478880498423169124",
                    "1478880563229233173",
                    "1479306922447339712"
            );

            for (String roleId : rolesToClear) {
                Role role = guild.getRoleById(roleId);
                if (role != null) {
                    guild.findMembersWithRoles(role).onSuccess(members -> {
                        for (net.dv8tion.jda.api.entities.Member m : members) {
                            guild.removeRoleFromMember(m, role).queue();
                        }
                    });
                }
            }

            // 3. Delete old winner claim messages
            List<DataService.WinnerMessageEntry> messages = ServiceManager.getDataService().getAllWinnerMessages();
            for (DataService.WinnerMessageEntry entry : messages) {
                GuildMessageChannel channel = guild.getChannelById(GuildMessageChannel.class, entry.channelId());
                if (channel != null) {
                    channel.deleteMessageById(entry.messageId()).queue(
                            null,
                            error -> System.err.println("[SCHEDULER] Failed to delete old winner message: " + error.getMessage())
                    );
                }
            }
            ServiceManager.getDataService().clearWinnerMessages();
            ServiceManager.getDataService().setMetadata("last_winners_reset_run", todayDate);

            System.out.println("[SCHEDULER] Winners reset completed successfully.");
        } catch (Exception e) {
            System.err.println("[SCHEDULER] Error during winners reset: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void performWeeklyAnnouncement(JDA jda) {
        try {
            ZoneId estZone = ZoneId.of("America/New_York");
            ZonedDateTime now = ZonedDateTime.now(estZone);
            String todayDate = now.toLocalDate().toString();
            String lastRun = ServiceManager.getDataService().getMetadata("last_weekly_announcement_run", "");

            if (todayDate.equals(lastRun)) {
                System.out.println("[SCHEDULER] Weekly announcement already sent today (" + todayDate + "), skipping.");
                return;
            }

            System.out.println("[SCHEDULER] Attempting to send weekly announcement...");
            ZonedDateTime eventTime = now.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
                    .withHour(14).withMinute(0).withSecond(0).withNano(0);

            String messageContent = buildWeeklyAnnouncementMessage(eventTime.toEpochSecond());

            // Send to Announcements
            String channelId = BotConfig.ANNOUNCEMENTS_CHANNEL_ID;
            GuildMessageChannel announcementsChannel = jda.getChannelById(GuildMessageChannel.class, channelId);
            if (announcementsChannel != null) {
                announcementsChannel.sendMessage(messageContent).queue(
                        success -> {
                            System.out.println("[SCHEDULER] Weekly announcement sent successfully to " + announcementsChannel.getName());
                            ServiceManager.getDataService().setMetadata("last_weekly_announcement_run", todayDate);
                        },
                        error -> System.err.println("[SCHEDULER] Failed to send weekly announcement message: " + error.getMessage())
                );
            } else {
                System.err.println("[SCHEDULER] Could not find announcements channel with ID: " + channelId);
            }

            lastWeeklyAnnouncementDraftTime = now;
        } catch (Exception e) {
            System.err.println("[SCHEDULER] Error during weekly announcement: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void startWeeklyWinnersReset(JDA jda) {
        ZoneId edtZone = ZoneId.of("America/New_York");
        ZonedDateTime now = ZonedDateTime.now(edtZone);
        ZonedDateTime nextRun = now.with(DayOfWeek.SUNDAY).withHour(18).withMinute(0).withSecond(0).withNano(0);

        String todayDate = now.toLocalDate().toString();
        String lastRun = ServiceManager.getDataService().getMetadata("last_winners_reset_run", "");

        if (now.isAfter(nextRun) || todayDate.equals(lastRun)) {
            nextRun = nextRun.plusWeeks(1);
        }

        long initialDelay = Duration.between(now, nextRun).toSeconds();
        long period = TimeUnit.DAYS.toSeconds(7);

        scheduler.scheduleAtFixedRate(() -> performWinnersReset(jda), initialDelay, period, TimeUnit.SECONDS);

        System.out.println("[SCHEDULER] Weekly Winners reset scheduled (Next run: " + nextRun + ")");
    }

    private static void startDailyAnnouncements(JDA jda) {
        ZoneId edtZone = ZoneId.of("America/New_York");
        ZonedDateTime now = ZonedDateTime.now(edtZone);
        ZonedDateTime nextRun = now.withHour(10).withMinute(0).withSecond(0).withNano(0);

        String todayDate = now.toLocalDate().toString();
        String lastRun = ServiceManager.getDataService().getMetadata("last_staff_announcement_run", "");

        if (now.isAfter(nextRun) || todayDate.equals(lastRun)) {
            nextRun = nextRun.plusDays(1);
        }

        long initialDelay = Duration.between(now, nextRun).toSeconds();
        long period = TimeUnit.DAYS.toSeconds(1);

        scheduler.scheduleAtFixedRate(() -> performDailyAnnouncement(jda), initialDelay, period, TimeUnit.SECONDS);

        System.out.println("[SCHEDULER] Daily Staff Announcements scheduled (Next run: " + nextRun + ")");
    }

    private static void performDailyAnnouncement(JDA jda) {
        ZoneId edtZone = ZoneId.of("America/New_York");
        ZonedDateTime runTime = ZonedDateTime.now(edtZone);
        if (runTime.getDayOfWeek() == DayOfWeek.MONDAY) {
            return; // Skip Monday
        }

        try {
            String todayDate = runTime.toLocalDate().toString();
            String lastRun = ServiceManager.getDataService().getMetadata("last_staff_announcement_run", "");

            if (todayDate.equals(lastRun)) {
                return;
            }

            String channelId = BotConfig.STAFF_ANNOUNCEMENTS_CHANNEL_ID;
            GuildMessageChannel channel = jda.getChannelById(GuildMessageChannel.class, channelId);
            if (channel != null) {
                ZonedDateTime nextSun = runTime.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
                        .withHour(14).withMinute(0).withSecond(0).withNano(0);
                String timestamp = "<t:" + nextSun.toEpochSecond() + ":F>";

                String ordinal = getReminderOrdinal(runTime.getDayOfWeek());
                channel.sendMessage(buildStaffMessage(timestamp, ordinal)).queue(success -> {
                    lastStaffAnnouncementTime = ZonedDateTime.now(edtZone);
                    ServiceManager.getDataService().setMetadata("last_staff_announcement_run", todayDate);
                });
            }
        } catch (Exception e) {
            System.err.println("[SCHEDULER] Error during daily announcement: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void startEventCountdown(JDA jda) {
        ZoneId edtZone = ZoneId.of("America/New_York");
        ZonedDateTime now = ZonedDateTime.now(edtZone);
        ZonedDateTime nextRun = now.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
                .withHour(12).withMinute(0).withSecond(0).withNano(0);

        String todayDate = now.toLocalDate().toString();
        String lastRun = ServiceManager.getDataService().getMetadata("last_event_countdown_run", "");

        if (now.isAfter(nextRun.plusHours(2)) || todayDate.equals(lastRun)) {
            nextRun = nextRun.plusWeeks(1);
        }

        long initialDelay = Math.max(0, Duration.between(now, nextRun).toSeconds());
        long period = TimeUnit.DAYS.toSeconds(7);

        scheduler.scheduleAtFixedRate(() -> performEventCountdown(jda), initialDelay, period, TimeUnit.SECONDS);

        System.out.println("[SCHEDULER] Weekly Event Countdown scheduled (Next run: " + nextRun + ")");
    }

    private static void performEventCountdown(JDA jda) {
        try {
            ZoneId edtZone = ZoneId.of("America/New_York");
            ZonedDateTime now = ZonedDateTime.now(edtZone);
            String todayDate = now.toLocalDate().toString();
            String lastRun = ServiceManager.getDataService().getMetadata("last_event_countdown_run", "");

            if (todayDate.equals(lastRun)) {
                System.out.println("[SCHEDULER] Event countdown already sent today (" + todayDate + "), skipping.");
                return;
            }

            if (!ServiceManager.getDataService().isEventCountdownEnabled()) {
                return;
            }

            String channelId = BotConfig.EVENT_COUNTDOWNS_CHANNEL_ID;
            GuildMessageChannel channel = jda.getChannelById(GuildMessageChannel.class, channelId);
            if (channel != null) {
                channel.sendMessage(buildEventMessage()).queue(message -> {
                    lastEventCountdownTime = ZonedDateTime.now(edtZone);
                    Emoji emoji = Emoji.fromCustom(BotConfig.EZ_EMOJI_NAME, Long.parseLong(BotConfig.EZ_EMOJI_ID), false);
                    message.addReaction(emoji).queue();

                    // Send winner message to event countdowns
                    GuildMessageChannel countdownChannel = jda.getChannelById(GuildMessageChannel.class, BotConfig.EVENT_COUNTDOWNS_CHANNEL_ID);
                    if (countdownChannel != null) {
                        countdownChannel.sendMessage(buildWinnerMessage()).queue(winnerMsg -> {
                            Emoji winnerEmoji = Emoji.fromCustom(BotConfig.WINNER_CLAIM_EMOJI_NAME, Long.parseLong(BotConfig.WINNER_CLAIM_EMOJI_ID), false);
                            winnerMsg.addReaction(winnerEmoji).queue();
                            ServiceManager.getDataService().addWinnerMessage(winnerMsg.getId(), BotConfig.EVENT_COUNTDOWNS_CHANNEL_ID, BotConfig.GUILD_ID);
                        });
                    }
                    ServiceManager.getDataService().setMetadata("last_event_countdown_run", todayDate);
                });
            }
        } catch (Exception e) {
            System.err.println("[SCHEDULER] Error during event countdown: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void startWeeklyAnnouncement(JDA jda) {
        ZoneId estZone = ZoneId.of("America/New_York");
        ZonedDateTime now = ZonedDateTime.now(estZone);
        ZonedDateTime nextRun = now.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
                .withHour(11).withMinute(0).withSecond(0).withNano(0);

        if (now.isAfter(nextRun)) {
            nextRun = nextRun.plusWeeks(1);
        }

        long initialDelay = Duration.between(now, nextRun).toSeconds();
        long period = TimeUnit.DAYS.toSeconds(7);

        scheduler.scheduleAtFixedRate(() -> performWeeklyAnnouncement(jda), initialDelay, period, TimeUnit.SECONDS);

        System.out.println("[SCHEDULER] Weekly Announcement scheduled (Next run: " + nextRun + ")");
    }

    private static String buildWeeklyAnnouncementMessage(long eventTimestamp) {
        return "<:EZ_new:1478805339011809350> **ENDZONE EVENT IS STARTING <t:" + eventTimestamp + ":R> !** <:EZ_new:1478805339011809350>\n" +
                "\n" +
                "@everyone\n" +
                "\n" +
                "<:DuoEZ:1465846857103048766> 2 duo games, one EU game and one USE game; THEN\n" +
                "<:SquadsEZ:1478805345471041637> 8 squad games, alternating every 2 games between EU and USE, starting with EU.\n" +
                "\n" +
                "<a:ah:1478805260372934676> 6 duos limit.\n" +
                "\n" +
                "<a:ah:1478805260372934676> 3 squads limit.\n" +
                "\n" +
                "<a:Attention:1160427579216511066> Cosmetics are prohibited.\n" +
                "\n" +
                "<a:Attention:1160427579216511066> Stormhealing is no longer allowed.\n" +
                "\n" +
                "    Stormhealing is as follows:\n" +
                "    -Going into the storm to use more meds than you currently hold.\n" +
                "\n" +
                "    You may dip in to use current meds, but you should not stay in storm to gather and heal more and win by that.\n" +
                "    You must come out before the zone closes. This is a form of griefing.\n" +
                "\n" +
                "<a:POLICE:1479582008014541018> Make sure you are using the following rules:\n" +
                "\n" +
                "📇 Name Rule\n" +
                "All participants must have the same nickname within the discord server as in game, or you can use `/eventname submit`. No vertical names allowed in game. If the streamer and staff cannot view your proper name, **__it will be voided.__**\n" +
                "\n" +
                "🎶 Clan Voice Channels\n" +
                "All clans participating **__MUST__** have their duos/squads join voice chat channels within the EndZone discord server. All voice chats must be locked and limited in order for win to count. To learn more on how to do so please read <#1478631200892518400>.\n" +
                "\n" +
                "Make sure you are familiar with these rules and are abiding by them!\n" +
                "\n" +
                "🏆 Who will take the trophy in the EndZone? Let's find out!\n" +
                "\n" +
                " <a:HYPE:1513154798231224393> Good Luck everyone! <a:HYPE:1513154798231224393>\n" ;
    }

    private static void startAccessHelpRoleScheduler(JDA jda) {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                long cutOffTime = System.currentTimeMillis() - (20 * 60 * 1000); // 20 minutes ago
                List<String> eligibleUsers = ServiceManager.getDataService().getEligibleAccessHelpUsers(cutOffTime);

                if (eligibleUsers.isEmpty()) return;

                String guildId = BotConfig.GUILD_ID;
                Guild guild = jda.getGuildById(guildId);
                if (guild == null) return;

                String memberRoleId = ServiceManager.getConfig().getMemberRoleId();
                Role role = guild.getRoleById(memberRoleId);
                if (role == null) return;

                for (String userId : eligibleUsers) {
                    guild.retrieveMemberById(userId).queue(member -> {
                        if (!member.getRoles().contains(role)) {
                            guild.addRoleToMember(member, role).queue(
                                success -> {
                                    System.out.println("[SCHEDULER] Automatically added Community Member role to " + member.getUser().getName() + " after 20 mins in access-help");
                                    ServiceManager.getDataService().removeAccessHelpTracking(userId);
                                },
                                error -> System.err.println("[SCHEDULER] Failed to add role to " + member.getUser().getName() + ": " + error.getMessage())
                            );
                        } else {
                            // User already has the role, just remove from tracking
                            ServiceManager.getDataService().removeAccessHelpTracking(userId);
                        }
                    }, error -> {
                        // User might have left the guild or other error
                        ServiceManager.getDataService().removeAccessHelpTracking(userId);
                    });
                }
            } catch (Exception e) {
                System.err.println("[SCHEDULER] Error in Access Help role scheduler: " + e.getMessage());
                e.printStackTrace();
            }
        }, 1, 1, TimeUnit.MINUTES);
    }

    public static void shutdown() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
            }
        }
    }
}