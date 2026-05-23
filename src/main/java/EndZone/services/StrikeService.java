package EndZone.services;

import EndZone.config.BotConfig;
import EndZone.database.StrikeDatabase;
import EndZone.models.Strike;
import EndZone.models.UserData;
import net.dv8tion.jda.api.JDA;

import java.sql.Timestamp;
import java.util.List;

public class StrikeService {
    private final StrikeDatabase database = new StrikeDatabase();
    private final BotConfig config;
    private DemotionService demotionService;
    private RoleRestorationService roleRestorationService;
    private JDA jda;

    public void setDemotionService(DemotionService demotionService) {
        this.demotionService = demotionService;
    }

    public void setRoleRestorationService(RoleRestorationService roleRestorationService) {
        this.roleRestorationService = roleRestorationService;
    }

    public void setJda(JDA jda) {
        this.jda = jda;
    }

    public UserData getUserData(String userId) {
        List<String> roles = database.getUserRoles(userId);
        if (roles.isEmpty()) {
            return null;
        }
        return new UserData(null, null, null, null, userId, roles);
    }

    public void updateUserRoles(String userId, List<String> roleIds) {
        database.updateUserRoles(userId, roleIds);
    }

    public StrikeDatabase getDatabase() {
        return database;
    }

    public StrikeService(BotConfig config) {
        this.config = config;
        initializeStrikesIfNeeded();
    }

    private void initializeStrikesIfNeeded() {
        if (database.hasBeenInitialized()) {
            return;
        }
        database.markAsInitialized();
    }

    public void issueStrike(String userId, String reason, String moderatorId) {
        database.addStrike(userId, reason, moderatorId, new Timestamp(System.currentTimeMillis()));
    }

    public List<Strike> getStrikes(String userId) {
        return database.getStrikes(userId);
    }

    public List<Strike> getAllStrikes(String userId) {
        return database.getAllStrikes(userId);
    }

    public void clearStrikes(String userId) {
        System.out.println("DEBUG: StrikeService.clearStrikes called for " + userId);
        
        // Restore roles before deleting demotion info
        List<String> roleIds = database.getTemporaryDemotionRoles(userId);
        if (roleRestorationService != null && !roleIds.isEmpty()) {
            roleRestorationService.restoreRolesForUser(userId, roleIds, false);
        }

        database.clearStrikes(userId);
        database.clearServedDemotion(userId);
        if (demotionService != null) {
            System.out.println("DEBUG: Removing demotion via DemotionService for " + userId);
            demotionService.removeDemotion(userId, jda);
        } else {
            System.err.println("DEBUG: demotionService is NULL in StrikeService!");
        }
    }

    public boolean removeStrike(String userId, int strikeNumber) {
        boolean removed = database.removeStrike(userId, strikeNumber);
        if (removed) {
            database.clearServedDemotion(userId);
            // If strike count drops below 2, restore roles and remove from demotions
            if (getStrikes(userId).size() < 2 && demotionService != null) {
                List<String> roleIds = database.getTemporaryDemotionRoles(userId);
                if (roleRestorationService != null && !roleIds.isEmpty()) {
                    roleRestorationService.restoreRolesForUser(userId, roleIds, false);
                }
                demotionService.removeDemotion(userId, jda);
            }
        }
        return removed;
    }

    public boolean removeStrike(String userId, Timestamp timestamp) {
        boolean removed = database.removeStrike(userId, timestamp);
        if (removed) {
            database.clearServedDemotion(userId);
            // If strike count drops below 2, restore roles and remove from demotions
            if (getStrikes(userId).size() < 2 && demotionService != null) {
                List<String> roleIds = database.getTemporaryDemotionRoles(userId);
                if (roleRestorationService != null && !roleIds.isEmpty()) {
                    roleRestorationService.restoreRolesForUser(userId, roleIds, false);
                }
                demotionService.removeDemotion(userId, jda);
            }
        }
        return removed;
    }

    public void clearAllStrikes(String userId) {
        // Restore roles before deleting demotion info
        List<String> roleIds = database.getTemporaryDemotionRoles(userId);
        if (roleRestorationService != null && !roleIds.isEmpty()) {
            roleRestorationService.restoreRolesForUser(userId, roleIds, false);
        }

        database.clearStrikes(userId);
        database.clearServedDemotion(userId);
        if (demotionService != null) {
            demotionService.removeDemotion(userId, jda);
        }
    }

    public void editStrike(String userId, int strikeNumber, String newReason) {
        database.editStrike(userId, strikeNumber, newReason);
    }

    public void editStrikeReason(String userId, Timestamp timestamp, String newReason) {
        database.editStrikeReason(userId, timestamp, newReason);
    }

    public List<String> getAllUsersWithStrikes() {
        return database.getAllUsersWithStrikes();
    }
}
