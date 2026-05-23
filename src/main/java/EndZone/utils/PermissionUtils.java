package EndZone.utils;

import EndZone.EndZone;
import EndZone.config.BotConfig;
import EndZone.services.ServiceManager;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;

import java.util.List;

public class PermissionUtils {

    public static boolean isBlacklisted(User user) {
        if (user == null) return false;
        return ServiceManager.getBlacklistService().isBlacklisted(user.getId());
    }

    public static boolean isModerator(Member member, BotConfig config) {
        if (member == null) {
            return false;
        }

        List<String> modRoles = config.getModRoles();
        return member.getRoles().stream()
                .anyMatch(role -> modRoles.contains(role.getId()));
    }

    public static boolean isAdmin(Member member, BotConfig config) {
        if (member == null) {
            return false;
        }

        List<String> adminRoles = config.getAdminRoles();
        
        return member.getRoles().stream()
                .anyMatch(role -> adminRoles.contains(role.getId()));
    }

    public static boolean isJury(Member member, BotConfig config) {
        if (member == null) {
            return false;
        }

        List<String> juryRoles = config.getJuryRoles();
        return member.getRoles().stream()
                .anyMatch(role -> juryRoles.contains(role.getId()));
    }

    public static boolean isCourtModOnly(Member member, BotConfig config) {
        if (member == null || config == null) {
            return false;
        }

        List<String> courtModOnlyRoles = config.getCourtModOnlyRoles();
        return member.getRoles().stream()
                .anyMatch(role -> courtModOnlyRoles.contains(role.getId()));
    }

    public static boolean isAdminPlus(Member member, BotConfig config) {
        if (member == null) {
            return false;
        }

        List<String> adminRoles = (config != null) ? config.getAdminRoles() : BotConfig.ADMIN_ROLES;

        return member.getRoles().stream()
                .anyMatch(role -> adminRoles.contains(role.getId()));
    }

    public static boolean isAlphaBetaOrHigher(Member member, BotConfig config) {
        if (member == null) {
            return false;
        }

        List<String> adminRoles = (config != null) ? config.getAdminRoles() : BotConfig.ADMIN_ROLES;
        String alphaBetaRoleId = BotConfig.ALPHA_BETAS_ROLE_ID;
        String daRoleId = BotConfig.COURT_THE_DISTRICT_ATTORNIES_EZ_ROLE_ID;

        return member.getRoles().stream()
                .anyMatch(role -> role.getId().equals(alphaBetaRoleId) || 
                                role.getId().equals(daRoleId) ||
                                adminRoles.contains(role.getId()));
    }

    public static boolean isGfxOrHigher(Member member, BotConfig config) {
        if (member == null) {
            return false;
        }

        List<String> staffRoles = BotConfig.STAFF_ROLE_IDS;
        int gfxIndex = staffRoles.indexOf(BotConfig.GFX_CONTENT_TEAM_ROLE_ID);
        
        if (gfxIndex == -1) {
            return isAlphaBetaOrHigher(member, config);
        }

        // Check if user has any role at or before GFX in the staff hierarchy list
        return member.getRoles().stream()
                .anyMatch(role -> {
                    int roleIndex = staffRoles.indexOf(role.getId());
                    return roleIndex != -1 && roleIndex <= gfxIndex;
                }) || isAlphaBetaOrHigher(member, config);
    }

    public static boolean isSemiMod(Member member, BotConfig config) {
        if (member == null) {
            return false;
        }

        List<String> semiModRoles = (config != null) ? config.getSemiModRoles() : BotConfig.SEMI_MOD_ROLES;
        return member.getRoles().stream()
                .anyMatch(role -> semiModRoles.contains(role.getId()));
    }

    public static boolean requiresApproval(Member member, BotConfig config) {
        return requiresApproval(member, config, null);
    }

    public static boolean requiresApproval(Member member, BotConfig config, EndZone bot) {
        return false;
    }

    public static boolean canModerate(Member moderator, Member target) {
        if (moderator == null || target == null) return false;
        
        // Server owner can always moderate anyone else
        if (moderator.isOwner()) return true;
        
        // Cannot moderate the server owner
        if (target.isOwner()) return false;

        // follows Discord's native role hierarchy order
        return moderator.canInteract(target);
    }
}
