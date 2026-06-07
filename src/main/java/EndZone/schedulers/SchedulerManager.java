package EndZone.schedulers;

import EndZone.config.BotConfig;
import EndZone.forms.EndZoneForm;
import EndZone.services.ServiceManager;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
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

    public static void start(JDA jda) {
        scheduler = Executors.newScheduledThreadPool(4);

        // Auto-export scheduler
        startAutoExportScheduler();

        // Status Heartbeat (Every 10 minutes)
        startStatusHeartbeat();

        // Perform immediate reset
        performWinnersReset(jda);

        // Weekly Winners Reset (Sunday @ 6 PM EDT)
        startWeeklyWinnersReset(jda);

        // Daily Staff Announcements (10 AM EST, skipping Monday)
        startDailyAnnouncements(jda);

        // Weekly Event Countdown (Sunday @ 12 PM EST)
        startEventCountdown(jda);

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

            System.out.println("[SCHEDULER] Winners reset completed successfully.");
        } catch (Exception e) {
            System.err.println("[SCHEDULER] Error during winners reset: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void startWeeklyWinnersReset(JDA jda) {
        ZoneId edtZone = ZoneId.of("America/New_York");
        ZonedDateTime now = ZonedDateTime.now(edtZone);
        ZonedDateTime nextRun = now.with(DayOfWeek.SUNDAY).withHour(18).withMinute(0).withSecond(0).withNano(0);

        if (now.isAfter(nextRun)) {
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

        if (now.isAfter(nextRun)) {
            nextRun = nextRun.plusDays(1);
        }

        long initialDelay = Duration.between(now, nextRun).toSeconds();
        long period = TimeUnit.DAYS.toSeconds(1);

        scheduler.scheduleAtFixedRate(() -> {
            ZonedDateTime runTime = ZonedDateTime.now(edtZone);
            if (runTime.getDayOfWeek() == DayOfWeek.MONDAY) {
                return; // Skip Monday
            }

            try {
                String channelId = BotConfig.STAFF_ANNOUNCEMENTS_CHANNEL_ID;
                TextChannel channel = jda.getTextChannelById(channelId);
                if (channel != null) {
                    ZonedDateTime nextSun = runTime.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
                            .withHour(14).withMinute(0).withSecond(0).withNano(0);
                    String timestamp = "<t:" + nextSun.toEpochSecond() + ":F>";

                    String ordinal = getReminderOrdinal(runTime.getDayOfWeek());
                    channel.sendMessage(buildStaffMessage(timestamp, ordinal)).queue(success -> {
                        lastStaffAnnouncementTime = ZonedDateTime.now(edtZone);
                    });
                }
            } catch (Exception e) {
                System.err.println("[SCHEDULER] Error during daily announcement: " + e.getMessage());
                e.printStackTrace();
            }
        }, initialDelay, period, TimeUnit.SECONDS);

        System.out.println("[SCHEDULER] Daily Staff Announcements scheduled (Next run: " + nextRun + ")");
    }

    private static void startEventCountdown(JDA jda) {
        ZoneId edtZone = ZoneId.of("America/New_York");
        ZonedDateTime now = ZonedDateTime.now(edtZone);
        ZonedDateTime nextRun = now.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
                .withHour(12).withMinute(0).withSecond(0).withNano(0);

        if (now.isAfter(nextRun)) {
            nextRun = nextRun.plusWeeks(1);
        }

        long initialDelay = Duration.between(now, nextRun).toSeconds();
        long period = TimeUnit.DAYS.toSeconds(7);

        scheduler.scheduleAtFixedRate(() -> {
            try {
                if (!ServiceManager.getDataService().isEventCountdownEnabled()) {
                    return;
                }
                String channelId = BotConfig.EVENT_COUNTDOWNS_CHANNEL_ID;
                TextChannel channel = jda.getTextChannelById(channelId);
                if (channel != null) {
                    channel.sendMessage(buildEventMessage()).queue(message -> {
                        lastEventCountdownTime = ZonedDateTime.now(edtZone);
                        Emoji emoji = Emoji.fromCustom(BotConfig.EZ_EMOJI_NAME, Long.parseLong(BotConfig.EZ_EMOJI_ID), false);
                        message.addReaction(emoji).queue();
                    });
                }
            } catch (Exception e) {
                System.err.println("[SCHEDULER] Error during event countdown: " + e.getMessage());
                e.printStackTrace();
            }
        }, initialDelay, period, TimeUnit.SECONDS);

        System.out.println("[SCHEDULER] Weekly Event Countdown scheduled (Next run: " + nextRun + ")");
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