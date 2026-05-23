package EndZone.schedulers;

import EndZone.config.BotConfig;
import EndZone.forms.EndZoneForm;
import EndZone.services.ServiceManager;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SchedulerManager {
    private static ScheduledExecutorService scheduler;

    public static void start(JDA jda) {
        scheduler = Executors.newScheduledThreadPool(2);
        
        // Auto-export scheduler
        startAutoExportScheduler();

        // Perform immediate reset
        performWinnersReset(jda);

        // Weekly Winners Reset (Sunday @ 6 PM EDT)
        startWeeklyWinnersReset(jda);
    }

    private static void startAutoExportScheduler() {
        scheduler.scheduleAtFixedRate(() -> {
            EndZoneForm.exportAllFormStatesToDatabase();
        }, 5, 5, TimeUnit.MINUTES);
        System.out.println("[SCHEDULER] Auto-export scheduler started (every 5 minutes)");
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
