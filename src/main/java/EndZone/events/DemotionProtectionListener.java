package EndZone.events;

import EndZone.config.BotConfig;
import EndZone.services.DemotionService;
import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.audit.AuditLogEntry;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DemotionProtectionListener extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(DemotionProtectionListener.class);
    private final DemotionService demotionService;

    public DemotionProtectionListener(DemotionService demotionService) {
        this.demotionService = demotionService;
    }

    @Override
    public void onGuildMemberRoleAdd(@NotNull GuildMemberRoleAddEvent event) {
        String userId = event.getUser().getId();
        Member member = event.getMember();
        
        // 1. Protection for Demoted Users
        if (demotionService.isDemoted(userId)) {
            boolean isPerm = demotionService.isPermanentlyDemoted(userId);
            String type = isPerm ? "permanently" : "temporarily";
            
            for (Role role : event.getRoles()) {
                // Remove staff roles for demoted users
                if (BotConfig.isStaffOrModRole(role.getId())
                        && !role.getId().equals(BotConfig.TRIAL_SENTINELS_ROLE_ID)) {
                    event.getGuild().removeRoleFromMember(member, role).queue(
                        success -> {
                            logger.info("Removed staff role {} from {} demoted user {}", role.getName(), type, userId);
                            
                            // Find who added the role to mention them in the notice
                            event.getGuild().retrieveAuditLogs()
                                .type(ActionType.MEMBER_ROLE_UPDATE)
                                .limit(5)
                                .queue(logs -> {
                                    String moderatorMention = "Unknown Moderator";
                                    for (AuditLogEntry entry : logs) {
                                        if (entry.getTargetId().equals(userId)) {
                                            moderatorMention = entry.getUser().getAsMention();
                                            break;
                                        }
                                    }
                                    sendProtectionNotice(event, role, type + " demoted", moderatorMention);
                                }, failure -> {
                                    sendProtectionNotice(event, role, type + " demoted", "Unknown Moderator");
                                });
                        },
                        failure -> logger.error("Failed to remove staff role from demoted user: {}", failure.getMessage())
                    );
                }
            }
        }
    }

    private void sendProtectionNotice(GuildMemberRoleAddEvent event, Role role, String type, String moderatorMention) {
        var staffChannel = event.getJDA().getTextChannelById(BotConfig.MANAGER_CHAT_CHANNEL_ID);
        if (staffChannel == null) return;

        Role ownerRole = event.getGuild().getRoleById(BotConfig.BRULPH_ROLE_ID);
        Role adminPlusRole = event.getGuild().getRoleById(BotConfig.MASTER_ALPHA_ROLE_ID);
        
        java.util.Set<net.dv8tion.jda.api.entities.Member> membersToPing = new java.util.LinkedHashSet<>();
        
        // Use CompletableFutures to wait for both role lookups
        java.util.concurrent.CompletableFuture<Void> ownersFuture = new java.util.concurrent.CompletableFuture<>();
        java.util.concurrent.CompletableFuture<Void> adminPlusFuture = new java.util.concurrent.CompletableFuture<>();

        if (ownerRole != null) {
            event.getGuild().findMembers(m -> m.getRoles().contains(ownerRole)).onSuccess(members -> {
                membersToPing.addAll(members);
                ownersFuture.complete(null);
            }).onError(e -> ownersFuture.complete(null));
        } else {
            ownersFuture.complete(null);
        }

        if (adminPlusRole != null) {
            event.getGuild().findMembers(m -> m.getRoles().contains(adminPlusRole)).onSuccess(members -> {
                membersToPing.addAll(members);
                adminPlusFuture.complete(null);
            }).onError(e -> adminPlusFuture.complete(null));
        } else {
            adminPlusFuture.complete(null);
        }

        java.util.concurrent.CompletableFuture.allOf(ownersFuture, adminPlusFuture).thenRun(() -> {
            StringBuilder pings = new StringBuilder();
            for (net.dv8tion.jda.api.entities.Member m : membersToPing) {
                pings.append(m.getAsMention()).append(" ");
            }

            // Fallback to role mentions if no members found
            if (pings.length() == 0) {
                if (ownerRole != null) pings.append(ownerRole.getAsMention()).append(" ");
                if (adminPlusRole != null) pings.append(adminPlusRole.getAsMention()).append(" ");
            }

            String message = String.format("%s\n⚠️ **Demotion Protection**\nModerator %s attempted to give staff role `%s` to **%s** demoted user <@%s>. Role has been automatically removed.", 
                    pings.toString().trim(), moderatorMention, role.getName(), type, event.getUser().getId());
            
            staffChannel.sendMessage(message).queue();
        });
    }
}
