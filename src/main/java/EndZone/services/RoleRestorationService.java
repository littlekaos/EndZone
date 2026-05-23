package EndZone.services;

import EndZone.services.ServiceManager;
import EndZone.models.UserData;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class RoleRestorationService extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(RoleRestorationService.class);
    private final StrikeService strikeService;

    public RoleRestorationService(StrikeService strikeService) {
        this.strikeService = strikeService;
    }

    public void restoreRolesForUser(String userId, List<String> roleIds, boolean silent) {
        net.dv8tion.jda.api.JDA jda = ServiceManager.getJda();
        if (jda == null) return;

        String guildId = ServiceManager.getConfig().getGuildId();
        Guild guild = jda.getGuildById(guildId);
        if (guild == null) return;

        guild.retrieveMemberById(userId).queue(member -> {
            List<Role> rolesToRestore = new ArrayList<>();
            for (String roleId : roleIds) {
                Role role = guild.getRoleById(roleId);
                if (role != null && canAssignRole(guild, role)) {
                    rolesToRestore.add(role);
                }
            }

            if (!rolesToRestore.isEmpty()) {
                for (Role role : rolesToRestore) {
                    guild.addRoleToMember(member, role).queue(
                        success -> {
                            if (!silent) logger.info("Restored role {} for user {}", role.getName(), userId);
                        },
                        error -> logger.error("Failed to restore role {} for user {}: {}", role.getName(), userId, error.getMessage())
                    );
                }
            }
        }, error -> {
            logger.warn("Could not find member {} to restore roles: {}", userId, error.getMessage());
        });
    }

    @Override
    public void onGuildMemberJoin(@NotNull GuildMemberJoinEvent event) {
        restoreRoles(event.getMember());
    }

    public void restoreRoles(Member member) {
        String userId = member.getUser().getId();
        UserData userData = strikeService.getUserData(userId);
        
        if (userData == null || userData.getRoles() == null || userData.getRoles().isEmpty()) {
            return;
        }

        Guild guild = member.getGuild();
        List<Role> rolesToRestore = new ArrayList<>();
        List<String> missingRoles = new ArrayList<>();

        for (String roleId : userData.getRoles()) {
            Role role = guild.getRoleById(roleId);
            if (role != null) {
                if (canAssignRole(guild, role)) {
                    rolesToRestore.add(role);
                } else {
                    logger.warn("Cannot assign role {} to user {} due to hierarchy", role.getName(), userId);
                }
            } else {
                missingRoles.add(roleId);
            }
        }

        if (!rolesToRestore.isEmpty()) {
            guild.modifyMemberRoles(member, rolesToRestore).queue(
                success -> logger.info("Restored {} roles for user {}", rolesToRestore.size(), userId),
                error -> logger.error("Failed to restore roles for user {}: {}", userId, error.getMessage())
            );
        }

        if (!missingRoles.isEmpty()) {
            logger.warn("User {} has {} roles that no longer exist in the guild", userId, missingRoles.size());
        }
    }

    private boolean canAssignRole(Guild guild, Role role) {
        Member selfMember = guild.getSelfMember();
        return selfMember.canInteract(role) && !role.isManaged() && !role.isPublicRole();
    }

    public void saveCurrentRoles(Member member) {
        String userId = member.getUser().getId();
        List<String> roleIds = member.getRoles().stream()
                .filter(role -> !role.isManaged() && !role.isPublicRole())
                .map(Role::getId)
                .collect(Collectors.toList());
        
        strikeService.updateUserRoles(userId, roleIds);
        logger.debug("Saved {} roles for user {}", roleIds.size(), userId);
    }

    public void triggerManualCheck() {
        logger.info("Manual role restoration check triggered");
        ServiceManager.getDemotionSyncService().syncDemotions();
    }

    public boolean isRunning() {
        return true; // Service is always active as a listener
    }
}
