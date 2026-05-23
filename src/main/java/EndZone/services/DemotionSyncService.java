package EndZone.services;

import EndZone.config.BotConfig;
import EndZone.database.StrikeDatabase;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DemotionSyncService {
    private static final Logger logger = LoggerFactory.getLogger(DemotionSyncService.class);
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    
    private final DemotionService demotionService;
    private final StrikeDatabase database;
    private final BotConfig config;
    private JDA jda;

    public DemotionSyncService(DemotionService demotionService, StrikeDatabase database, BotConfig config) {
        this.demotionService = demotionService;
        this.database = database;
        this.config = config;
    }

    public void initialize(JDA jda) {
        this.jda = jda;
        // Run every 30 minutes
        scheduler.scheduleAtFixedRate(this::syncDemotions, 0, 30, TimeUnit.MINUTES);
        logger.info("Demotion Sync Service initialized - checking every 30 minutes");
    }

    public void syncDemotions() {
        try {
            logger.info("Starting demotion synchronization check...");
            String guildId = BotConfig.GUILD_ID;
            Guild guild = jda.getGuildById(guildId);
            if (guild == null) return;

            checkExpiredDemotions(); // Check this first
            
            List<String> userIds = database.getAllUsersWithStrikes();
            for (String userId : userIds) {
                int strikeCount = database.getStrikes(userId).size();
                if (strikeCount < 2) {
                    // If they have less than 2 strikes but are still in temporary_demotions, restore them
                    if (database.loadTemporaryDemotionsWithRoles().containsKey(userId)) {
                         restoreExpiredUser(userId, database.getTemporaryDemotionRoles(userId));
                    }
                    continue;
                }

                guild.retrieveMemberById(userId).queue(member -> {
                    if (strikeCount == 2) {
                        checkAndApplyTempDemotion(guild, member);
                    } else if (strikeCount >= 3) {
                        checkAndApplyPermDemotion(guild, member);
                    }
                }, error -> {
                    // Member not in guild, still track perm demotion if 3+ strikes
                    if (strikeCount >= 3) {
                        demotionService.addPermanentDemotion(userId);
                    }
                });
            }
            
            demotionService.updateDemotionListMessage(jda);
            checkManualRestorationCleanup(); // Cleanup for users who were removed but didn't get roles
        } catch (Exception e) {
            logger.error("Error during demotion sync: {}", e.getMessage());
        }
    }

    private void checkExpiredDemotions() {
        try {
            Map<String, Map<String, Object>> tempDemotions = database.loadTemporaryDemotionsWithRoles();
            Instant now = Instant.now();
            
            for (Map.Entry<String, Map<String, Object>> entry : tempDemotions.entrySet()) {
                String userId = entry.getKey();
                Map<String, Object> data = entry.getValue();
                Instant restorationDate = (Instant) data.get("restoration_date");
                @SuppressWarnings("unchecked")
                List<String> roleIds = (List<String>) data.get("role_ids");
                
                if (restorationDate != null && now.isAfter(restorationDate)) {
                    logger.info("Temporary demotion expired for user {}. Restoring roles...", userId);
                    restoreExpiredUser(userId, roleIds);
                }
            }
        } catch (Exception e) {
            logger.error("Error checking expired demotions: {}", e.getMessage());
        }
    }

    private void restoreExpiredUser(String userId, List<String> roleIds) {
        RoleRestorationService restorationService = ServiceManager.getRoleRestorationService();
        if (restorationService != null) {
            String guildId = BotConfig.GUILD_ID;
            Guild guild = jda.getGuildById(guildId);
            if (guild == null) return;

            guild.retrieveMemberById(userId).queue(member -> {
                List<Role> rolesToRestore = new ArrayList<>();
                for (String roleId : roleIds) {
                    Role role = guild.getRoleById(roleId);
                    if (role != null) rolesToRestore.add(role);
                }

                if (rolesToRestore.isEmpty()) {
                    logger.warn("No roles to restore for user {}. Cleaning up demotion anyway.", userId);
                    completeRestoration(userId);
                    return;
                }

                // Count successes to ensure we only cleanup when done
                java.util.concurrent.atomic.AtomicInteger count = new java.util.concurrent.atomic.AtomicInteger(0);
                for (Role role : rolesToRestore) {
                    guild.addRoleToMember(member, role).queue(s -> {
                        if (count.incrementAndGet() == rolesToRestore.size()) {
                            completeRestoration(userId);
                        }
                    }, e -> {
                        logger.error("Failed to restore role {} for {}: {}", role.getName(), userId, e.getMessage());
                        if (count.incrementAndGet() == rolesToRestore.size()) {
                            completeRestoration(userId);
                        }
                    });
                }
            }, error -> {
                logger.warn("Could not find member {} to restore roles, but they are in DB. Cleaning up.", userId);
                completeRestoration(userId);
            });
        } else {
            logger.error("RoleRestorationService not found! Cannot restore roles for {}", userId);
        }
    }

    private void completeRestoration(String userId) {
        int strikeCount = database.getStrikes(userId).size();
        if (database.isRestorationNotified(userId, strikeCount)) {
            return; // Avoid double notification
        }

        // Notify staff channel
        TextChannel staffChannel = jda.getTextChannelById(BotConfig.STAFF_NOTIFICATION_CHANNEL_ID);
        if (staffChannel != null) {
            staffChannel.sendMessage("🔄 **Automatic Role Restoration**\n<@" + userId + "> roles have been automatically restored after temporary demotion period.").queue();
            database.markRestorationNotified(userId, strikeCount);
        }

        // Mark as served
        demotionService.markDemotionAsServed(userId, strikeCount);
        
        // Remove from temporary demotions
        demotionService.removeDemotion(userId, jda);
        logger.info("Completed restoration and cleanup for user {}", userId);
    }

    private void checkManualRestorationCleanup() {
        // Find users with 2 strikes who have served their demotion but might not have roles
        List<String> userIds = database.getAllUsersWithStrikes();
        for (String userId : userIds) {
            int strikeCount = database.getStrikes(userId).size();
            if (strikeCount == 2) {
                int servedCount = database.getServedDemotionStrikeCount(userId);
                if (servedCount == 2) {
                    // Even if servedCount is 2, check if we need to notify or restore roles
                    if (database.isRestorationNotified(userId, 2)) {
                        continue;
                    }

                    jda.retrieveUserById(userId).queue(user -> {
                        String guildId = BotConfig.GUILD_ID;
                        Guild guild = jda.getGuildById(guildId);
                        if (guild == null) return;
                        
                        guild.retrieveMember(user).queue(member -> {
                            boolean hasStaffRole = false;
                            for (Role role : member.getRoles()) {
                                if (BotConfig.isStaffOrModRole(role.getId())) {
                                    hasStaffRole = true;
                                    break;
                                }
                            }
                            
                            if (!hasStaffRole) {
                                logger.info("User {} has served demotion but has no staff roles. Restoring from backup...", userId);
                                List<String> roles = database.getUserRoles(userId);
                                if (!roles.isEmpty()) {
                                    restoreExpiredUser(userId, roles);
                                }
                            } else {
                                // They have roles (manual fix), but we haven't notified yet!
                                logger.info("User {} has roles and served demotion. Finalizing notification.", userId);
                                completeRestoration(userId);
                            }
                        }, err -> {});
                    }, err -> {});
                }
            }
        }
    }

    private void checkAndApplyTempDemotion(Guild guild, Member member) {
        String userId = member.getId();
        boolean alreadyTracked = database.loadTemporaryDemotionsWithRoles().containsKey(userId);
        boolean isPerm = database.loadPermanentDemotions().contains(userId);
        
        if (isPerm) return;

        if (!alreadyTracked) {
            // Check if they already served a demotion for their current strike count
            int strikeCount = database.getStrikes(userId).size();
            int servedStrikeCount = database.getServedDemotionStrikeCount(userId);
            
            if (servedStrikeCount >= strikeCount) {
                logger.debug("User {} already served their temporary demotion for {} strikes. Skipping re-application.", member.getUser().getName(), strikeCount);
                return;
            }

            List<String> removedRoleIds = new ArrayList<>();
            for (String roleId : BotConfig.STAFF_ROLE_IDS) {
                Role role = guild.getRoleById(roleId);
                if (role != null && member.getRoles().contains(role)) {
                    removedRoleIds.add(role.getId());
                    guild.removeRoleFromMember(member, role).queue();
                }
            }

            Instant restorationDate = Instant.now().plus(BotConfig.TEMP_DEMOTION_DAYS, ChronoUnit.DAYS);
            demotionService.addTemporaryDemotion(member.getId(), removedRoleIds, restorationDate);
            logger.info("Auto-applied temporary demotion tracking for {} (2 strikes)", member.getUser().getName());
        }
    }

    private void checkAndApplyPermDemotion(Guild guild, Member member) {
        if (database.loadPermanentDemotions().contains(member.getId())) return;

        for (String roleId : BotConfig.STAFF_ROLE_IDS) {
            Role role = guild.getRoleById(roleId);
            if (role != null && member.getRoles().contains(role)) {
                guild.removeRoleFromMember(member, role).queue();
            }
        }

        demotionService.addPermanentDemotion(member.getId());
        logger.info("Auto-applied permanent demotion for {} (3+ strikes)", member.getUser().getName());
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
        logger.info("Demotion Sync Service shut down");
    }
}
