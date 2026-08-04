package EndZone.services;

import EndZone.models.ModAction;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MuteService {
    private static final Logger logger = LoggerFactory.getLogger(MuteService.class);
    private JDA jda;
    private final DataService dataService;
    private final ScheduledExecutorService scheduler;

    public MuteService(DataService dataService) {
        this.dataService = dataService;
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    public void setJda(JDA jda) {
        this.jda = jda;
    }

    public void start() {
        scheduler.scheduleAtFixedRate(this::checkMutes, 1, 1, TimeUnit.MINUTES);
        logger.info("Mute Service started");
    }

    private void checkMutes() {
        try {
            List<DataService.MuteEntry> expiredMutes = dataService.getExpiredMutes(System.currentTimeMillis());
            
            for (DataService.MuteEntry entry : expiredMutes) {
                unmuteUser(entry.guildId(), entry.userId());
            }
        } catch (Exception e) {
            logger.error("Error checking expired mutes: {}", e.getMessage());
        }
    }

    private void unmuteUser(String guildId, String userId) {
        Guild guild = jda.getGuildById(guildId);
        if (guild == null) return;

        String muteRoleId = dataService.getMuteRoleId(guildId);
        if (muteRoleId == null) {
            dataService.removeMute(guildId, userId);
            return;
        }

        Role muteRole = guild.getRoleById(muteRoleId);
        if (muteRole == null) {
            dataService.removeMute(guildId, userId);
            return;
        }

        guild.retrieveMemberById(userId).queue(member -> {
            guild.removeRoleFromMember(member, muteRole).queue(success -> {
                dataService.removeMute(guildId, userId);
                dataService.saveModAction(ModAction.ActionType.UNMUTE, jda.getSelfUser().getId(), jda.getSelfUser().getName(),
                        userId, member.getUser().getName(), "Auto-unmute: Duration expired", guild.getName(), "System", 0, 0);
                logger.info("Auto-unmuted {} in guild {}", member.getUser().getName(), guild.getName());
            }, error -> {
                logger.error("Failed to auto-unmute {}: {}", member.getUser().getName(), error.getMessage());
            });
        }, error -> {
            // User left the server?
            dataService.removeMute(guildId, userId);
            logger.info("Removed expired mute for user {} who is no longer in guild {}", userId, guild.getName());
        });
    }

    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info("Mute Service shut down");
    }
}
